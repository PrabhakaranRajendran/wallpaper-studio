package com.prabhu.wallpaperstudio.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.prabhu.wallpaperstudio.data.Crop
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.data.Weather
import com.prabhu.wallpaperstudio.data.WidgetDataSource
import com.prabhu.wallpaperstudio.data.WidgetItem
import com.prabhu.wallpaperstudio.data.Widgets
import com.prabhu.wallpaperstudio.data.CalendarSource
import com.prabhu.wallpaperstudio.render.LockChanger
import com.prabhu.wallpaperstudio.render.PhotoMath
import com.prabhu.wallpaperstudio.render.WidgetRenderer
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Draws the Home screen photo slideshow: timer, transitions, crop, 3D gyro and widgets.
 * Used by the live wallpaper and (in test builds) by an ordinary screen.
 *
 * @param live true for the real wallpaper (it changes photos, reports itself and
 *             drives the lock screen slideshow); false for previews.
 */
class PhotoRenderer(
    private val context: Context,
    private val holder: SurfaceHolder,
    private val live: Boolean,
    private val flags: () -> Int,
) : SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {

    private class Slide(val uri: String, val bitmap: Bitmap, val blur: Bitmap, val crop: Crop?, val filter: ColorFilter?)

    private val handler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private val io = Executors.newSingleThreadExecutor()
    private var visible = false
    private var hasSurface = false
    private var sw = 0
    private var sh = 0
    private var hwCanvasOk = true
    private var locked = false
    private var reportedFlags = 0
    private var batteryBlocked = false
    private var lastDrawNanos = 0L
    private var frameScheduled = false
    private var lastDrawnX = 99f
    private var lastDrawnY = 99f

    private var current: Slide? = null
    @Volatile private var shownUri: String? = null
    private var previous: Slide? = null
    private var transitionStart = 0L
    private var loadGeneration = 0
    private var failedInARow = 0

    // Cached settings
    private var transition = "fade"
    private var fit = "fill"
    private var gyroOn = true
    private var intensity = 0.55f
    private var smooth = 0.75f
    private var style = "tilt"
    private var paused = false
    private var widgets: List<WidgetItem> = emptyList()
    private var widgetsOn = false
    private var shadow = true

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val matrix = Matrix()
    private val camMatrix = Matrix()
    private val camera = Camera()
    private val gyro = GyroTracker(context) { requestFrames() }

    private val changeRunnable = Runnable {
        paused = Battery.stopAll(context)
        if (!paused) advance("timer") else scheduleTimer()
    }
    private val minuteRunnable = object : Runnable {
        override fun run() {
            drawFrame()
            scheduleMinute()
        }
    }

    private var receiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED, android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val stop = Battery.stopAll(context)
                    if (stop != paused) {
                        paused = stop
                        batteryBlocked = stop
                        scheduleTimer()
                        updateGyro()
                        lastDrawnX = 99f
                        drawFrame()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    locked = true
                    if (Store.lockMode() == "slideshow" && !Battery.stopAll(context) && LockChanger.isDue()) {
                        LockChanger.changeAsync(context, "screen off")
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    locked = false
                    if (Store.bool(Keys.HOME_ON_UNLOCK, false) && !paused) advance("unlock")
                    drawFrame()
                }
            }
        }
    }

    // ---------- lifecycle ----------

    fun create() {
        readSettings()
        Store.prefs.registerOnSharedPreferenceChangeListener(this)
        locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (live) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
            report()
        }
    }

    fun destroy() {
        Store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (live && reportedFlags != 0) reportScreens(flags = reportedFlags, running = false)
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
        }
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(this)
        gyro.stop()
        loader.shutdownNow()
        io.shutdownNow()
    }

    fun surfaceCreated() {
        hasSurface = true
    }

    fun surfaceChanged(width: Int, height: Int) {
        hasSurface = true
        val sizeChanged = width != sw || height != sh
        sw = width
        sh = height
        textPaint.textSize = width / 26f
        if (sizeChanged) loadCurrent(force = true) else drawFrame()
        scheduleTimer()
    }

    fun redrawNeeded() = drawFrame()

    fun surfaceDestroyed() {
        hasSurface = false
        Choreographer.getInstance().removeFrameCallback(this)
        frameScheduled = false
    }

    fun visibilityChanged(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) {
            paused = Battery.stopAll(context)
            batteryBlocked = Battery.blocks3d(context)
            if (live) report()
            locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            // Timers stop while the phone sleeps, so catch up here.
            if (live && !paused && dueForChange()) advance("catch up")
            scheduleTimer()
            if (current == null) loadCurrent(force = false)
            refreshWidgetData()
            drawFrame()
            scheduleMinute()
        } else {
            // Nothing is on screen: stop every timer so the phone can sleep.
            handler.removeCallbacks(minuteRunnable)
            handler.removeCallbacks(changeRunnable)
        }
        updateGyro()
    }

    fun flagsChanged() {
        if (live) report()
        drawFrame()
    }

    // ---------- settings ----------

    private fun readSettings() {
        transition = Store.transition()
        fit = Store.fit(Target.HOME)
        gyroOn = Store.gyroOn()
        intensity = Store.gyroIntensity()
        smooth = Store.gyroSmooth()
        style = Store.gyroStyle()
        paused = Battery.stopAll(context)
        widgets = Widgets.load()
        widgetsOn = widgets.any { it.on }
        shadow = Store.bool(Keys.WIDGET_SHADOW, true)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == Keys.ENGINE_LOG) return
        readSettings()
        when (key) {
            Keys.index(Target.HOME), Keys.folders(Target.HOME), Keys.hidden(Target.HOME) -> {
                if (key != Keys.index(Target.HOME)) PhotoLists.invalidate()
                loadCurrent(force = false)
            }
            Keys.CROPS -> refreshCrop()
            Keys.fit(Target.HOME), Keys.WIDGET_SHADOW, Keys.WEATHER_TEXT -> drawFrame()
            Keys.widgets(Target.HOME) -> {
                refreshWidgetData()
                drawFrame()
                scheduleMinute()
            }
            Keys.interval(Target.HOME), Keys.last(Target.HOME) -> scheduleTimer()
            Keys.change(Target.HOME) -> if (live) advance("Change now")
            Keys.GYRO, Keys.GYRO_INTENSITY, Keys.GYRO_SMOOTH, Keys.GYRO_STYLE,
            Keys.PAUSED, Keys.BAT_STOP_ON, Keys.BAT_STOP_LEVEL, Keys.BAT_SAVER_STOP -> {
                scheduleTimer()
                batteryBlocked = Battery.blocks3d(context)
                updateGyro()
                lastDrawnX = 99f
                requestFrames()
                drawFrame()
            }
            Keys.RECENTER -> {
                gyro.recenter()
                requestFrames()
            }
        }
    }

    /** Re-reads the photo list (after photos were added or removed in the app). */
    fun reload() {
        PhotoLists.invalidate()
        loadCurrent(force = false)
    }

    // ---------- rotation timer ----------

    private fun intervalMs() = Store.interval(Target.HOME).coerceAtLeast(1) * 60_000L

    private fun dueForChange() =
        System.currentTimeMillis() - Store.long(Keys.last(Target.HOME), 0L) >= intervalMs()

    private fun scheduleTimer() {
        handler.removeCallbacks(changeRunnable)
        if (!live || paused || !visible) return
        val last = Store.long(Keys.last(Target.HOME), 0L)
        val delay = (last + intervalMs() - System.currentTimeMillis()).coerceIn(1_000L, intervalMs())
        handler.postDelayed(changeRunnable, delay)
    }

    private fun advance(reason: String) {
        val now = System.currentTimeMillis()
        // Avoid double changes when two copies of the wallpaper react at once.
        if (now - Store.long(Keys.last(Target.HOME), 0L) < 1_500L) return
        val shuffle = Store.order(Target.HOME) == "shuffle"
        loader.execute {
            val size = PhotoLists.get(context, Target.HOME).size
            Store.edit {
                if (size >= 2) {
                    val cur = Store.int(Keys.index(Target.HOME), 0).mod(size)
                    val next = if (shuffle) {
                        var n: Int
                        do {
                            n = Random.nextInt(size)
                        } while (n == cur)
                        n
                    } else {
                        (cur + 1) % size
                    }
                    putInt(Keys.index(Target.HOME), next)
                }
                putLong(Keys.last(Target.HOME), now)
            }
            if (size < 2) Store.log("Home: only $size photo, nothing to change to")
        }
        if (reason != "timer") scheduleTimer()
    }

    // ---------- loading ----------

    private fun refreshCrop() {
        val cur = current ?: return
        val crop = Store.crops()[cur.uri]
        current = Slide(cur.uri, cur.bitmap, cur.blur, crop, PhotoMath.brightnessFilter(crop?.brightness ?: 0f))
        drawFrame()
    }

    private fun loadCurrent(force: Boolean) {
        if (sw == 0 || sh == 0) return
        val gen = ++loadGeneration
        val longSide = (max(sw, sh) * 1.25f).toInt()
        loader.execute {
            val list = PhotoLists.get(context, Target.HOME)
            if (list.isEmpty()) {
                handler.post {
                    if (gen != loadGeneration) return@post
                    current = null
                    shownUri = null
                    previous = null
                    drawFrame()
                }
                return@execute
            }
            val index = Store.int(Keys.index(Target.HOME), 0).mod(list.size)
            val uri = list[index]
            if (!force && uri == shownUri) return@execute
            val crop = Store.crops()[uri]
            var reason: String? = null
            val slide = try {
                val bmp = PhotoMath.decode(context, uri, longSide)
                val blur = PhotoMath.softBlur(bmp)
                Slide(uri, bmp, blur, crop, PhotoMath.brightnessFilter(crop?.brightness ?: 0f))
            } catch (t: Throwable) {
                reason = if (t is SecurityException) "no permission" else (t.message ?: t.javaClass.simpleName)
                null
            }
            handler.post { onLoaded(gen, uri, slide, reason, index, list.size) }
        }
    }

    private fun onLoaded(gen: Int, uri: String, slide: Slide?, reason: String?, index: Int, size: Int) {
        if (gen != loadGeneration) return
        if (slide == null) {
            failedInARow++
            if (live) {
                Store.log("Home: couldn't open a photo ($reason), skipped")
                if (failedInARow < size) Store.edit { putInt(Keys.index(Target.HOME), index + 1) }
            }
            return
        }
        failedInARow = 0
        if (live && shownUri != uri) Store.log("Home: photo ${index + 1} of $size")
        val old = current
        val animate = old != null && old.uri != uri && transition != "none" && visible
        previous = if (animate) old else null
        current = slide
        shownUri = uri
        transitionStart = SystemClock.uptimeMillis()
        if (animate) requestFrames() else drawFrame()
    }

    // ---------- widgets ----------

    private fun refreshWidgetData() {
        if (!widgetsOn) return
        val needWeather = widgets.any { it.on && it.type == "weather" }
        val needEvent = widgets.any { it.on && it.type == "event" }
        if (needWeather || needEvent) {
            io.execute {
                if (needWeather && Weather.isStale()) Weather.refresh()
                if (needEvent) CalendarSource.refresh(context)
                handler.post { drawFrame() }
            }
        }
    }

    private fun scheduleMinute() {
        handler.removeCallbacks(minuteRunnable)
        if (!visible || !widgetsOn) return
        val now = System.currentTimeMillis()
        handler.postDelayed(minuteRunnable, 60_000L - now % 60_000L + 200L)
    }

    /** Widgets are for the Home screen only, so hide them while this wallpaper shows on the lock screen. */
    private fun showWidgets(): Boolean {
        if (!widgetsOn) return false
        val onLock = flags() and WallpaperManager.FLAG_LOCK != 0
        return !(onLock && locked)
    }

    // ---------- gyro ----------

    private fun gyroWanted() = visible && gyroOn && intensity > 0f && !batteryBlocked

    private fun updateGyro() {
        if (gyroWanted()) {
            if (!gyro.running) {
                gyro.start()
                requestFrames()
            }
        } else if (gyro.running) {
            gyro.stop()
            drawFrame()
        }
    }

    // ---------- drawing ----------

    private fun requestFrames() {
        if (!frameScheduled && visible && hasSurface) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (!visible || !hasSurface) return
        val inTransition = previous != null && SystemClock.uptimeMillis() - transitionStart < TRANSITION_MS
        // 3D motion is drawn at most 30 times a second to save battery.
        if (!inTransition && frameTimeNanos - lastDrawNanos < 30_000_000L) {
            requestFrames()
            return
        }
        lastDrawNanos = frameTimeNanos
        val settling = gyro.step(smooth)
        val moved = kotlin.math.abs(gyro.x - lastDrawnX) > 0.002f || kotlin.math.abs(gyro.y - lastDrawnY) > 0.002f
        if (inTransition || moved || previous != null) drawFrame()
        if (!inTransition) previous = null
        if (inTransition || settling) requestFrames()
    }

    private fun lockCanvas(): Canvas? {
        if (hwCanvasOk) {
            try {
                return holder.lockHardwareCanvas()
            } catch (e: Exception) {
                hwCanvasOk = false
            }
        }
        return try {
            holder.lockCanvas()
        } catch (e: Exception) {
            null
        }
    }

    fun drawFrame() {
        if (!hasSurface || sw == 0) return
        val canvas = lockCanvas() ?: return
        try {
            canvas.drawColor(Color.BLACK)
            val cur = current
            if (cur == null) {
                drawEmpty(canvas)
            } else {
                val prev = previous
                val t = if (prev == null) 1f else
                    ((SystemClock.uptimeMillis() - transitionStart) / TRANSITION_MS.toFloat()).coerceIn(0f, 1f)
                val e = t * t * (3f - 2f * t)
                if (prev != null && t < 1f) {
                    when (transition) {
                        "slide" -> {
                            drawSlide(canvas, prev, -e * sw, 1f, 255)
                            drawSlide(canvas, cur, (1f - e) * sw, 1f, 255)
                        }
                        "zoom" -> {
                            drawSlide(canvas, prev, 0f, 1f, 255)
                            drawSlide(canvas, cur, 0f, 1f + 0.12f * (1f - e), (e * 255).toInt())
                        }
                        else -> {
                            drawSlide(canvas, prev, 0f, 1f, 255)
                            drawSlide(canvas, cur, 0f, 1f, (e * 255).toInt())
                        }
                    }
                } else {
                    drawSlide(canvas, cur, 0f, 1f, 255)
                }
            }
            if (showWidgets()) {
                WidgetRenderer.draw(canvas, sw, sh, widgets, WidgetDataSource.read(context), shadow)
            }
            lastDrawnX = gyro.x
            lastDrawnY = gyro.y
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    private fun drawEmpty(canvas: Canvas) {
        canvas.drawColor(Color.rgb(0x10, 0x20, 0x2B))
        canvas.drawText("Open Wallpaper Studio", sw / 2f, sh / 2f - textPaint.textSize, textPaint)
        canvas.drawText("and add photos for your wallpaper", sw / 2f, sh / 2f + textPaint.textSize * 0.4f, textPaint)
    }

    private fun drawSlide(canvas: Canvas, s: Slide, slideX: Float, extraScale: Float, alpha: Int) {
        val w = sw.toFloat()
        val h = sh.toFloat()
        val inten = if (gyro.running) intensity else 0f
        val nx = gyro.x
        val ny = gyro.y
        var scale = (1f + 0.14f * inten) * extraScale
        if (style == "zoom") scale *= 1f + 0.06f * inten * min(1f, hypot(nx, ny))
        val px = nx * 0.05f * inten * w + slideX
        val py = ny * 0.05f * inten * h

        paint.alpha = alpha
        paint.colorFilter = s.filter
        val effectiveFit = if (s.crop != null) "fill" else PhotoMath.resolveFit(fit, s.bitmap.width, s.bitmap.height, w, h)
        if (effectiveFit == "blur") {
            PhotoMath.place(matrix, s.blur.width, s.blur.height, w, h, null, "fill", scale * 1.05f, px * 0.6f, py * 0.6f)
            applyTilt(nx, ny, inten, w, h)
            canvas.drawBitmap(s.blur, matrix, paint)
        }
        PhotoMath.place(matrix, s.bitmap.width, s.bitmap.height, w, h, s.crop, effectiveFit, scale, px, py)
        applyTilt(nx, ny, inten, w, h)
        canvas.drawBitmap(s.bitmap, matrix, paint)
    }

    private fun applyTilt(nx: Float, ny: Float, inten: Float, w: Float, h: Float) {
        if (style != "tilt" || inten <= 0f) return
        camera.save()
        camera.setLocation(0f, 0f, -max(w, h) / 72f * 1.2f)
        camera.rotateY(-nx * 9f * inten)
        camera.rotateX(ny * 9f * inten)
        camera.getMatrix(camMatrix)
        camera.restore()
        camMatrix.preTranslate(-w / 2f, -h / 2f)
        camMatrix.postTranslate(w / 2f, h / 2f)
        matrix.postConcat(camMatrix)
    }

    // ---------- status ----------

    private fun report() {
        val f = flags()
        if (f == reportedFlags) return
        if (reportedFlags != 0) reportScreens(flags = reportedFlags, running = false)
        reportScreens(flags = f, running = true)
        reportedFlags = f
    }

    companion object {
        private const val TRANSITION_MS = 900L
    }
}
