package com.prabhu.wallpaperstudio.wallpaper

import android.app.KeyguardManager
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.prabhu.wallpaperstudio.data.Crop
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.render.PhotoMath
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PhotoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PhotoEngine()

    private class Slide(
        val uri: String,
        val bitmap: Bitmap,
        val blur: Bitmap,
        val crop: Crop?,
        val filter: ColorFilter?,
    )

    inner class PhotoEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener,
        SensorEventListener,
        Choreographer.FrameCallback {

        private val handler = Handler(Looper.getMainLooper())
        private val loader: ExecutorService = Executors.newSingleThreadExecutor()
        private var visible = false
        private var hasSurface = false
        private var sw = 0
        private var sh = 0
        private var hwCanvasOk = true
        private var locked = false
        private var reportedFlags = 0
        private var batteryBlocked = false
        private var lastDrawNanos = 0L

        private var current: Slide? = null
        private var previous: Slide? = null
        private var transitionStart = 0L
        private var loadingUri: String? = null
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

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        private val matrix = Matrix()
        private val camMatrix = Matrix()
        private val camera = Camera()

        // Gyro
        private var sensorManager: SensorManager? = null
        private var gyroRegistered = false
        private var tiltX = 0f
        private var tiltY = 0f
        private var smoothX = 0f
        private var smoothY = 0f
        private var lastSensorTs = 0L
        private var lastDrawnX = 99f
        private var lastDrawnY = 99f
        private var frameScheduled = false

        private val changeRunnable = Runnable { onTimer() }
        private var receiverRegistered = false
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> setLocked(true)
                    Intent.ACTION_USER_PRESENT -> {
                        setLocked(false)
                        if (Store.bool(Keys.ON_UNLOCK, false) && !paused) advance()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            sensorManager = getSystemService(SensorManager::class.java)
            readSettings()
            Store.prefs.registerOnSharedPreferenceChangeListener(this)
            locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            if (!isPreview) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                ContextCompat.registerReceiver(
                    this@PhotoWallpaperService,
                    screenReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED,
                )
                receiverRegistered = true
                report()
            }
        }

        override fun onDestroy() {
            Store.prefs.unregisterOnSharedPreferenceChangeListener(this)
            if (!isPreview && reportedFlags != 0) reportScreens(video = false, flags = reportedFlags, running = false)
            if (receiverRegistered) {
                try {
                    unregisterReceiver(screenReceiver)
                } catch (_: Exception) {
                }
            }
            handler.removeCallbacksAndMessages(null)
            Choreographer.getInstance().removeFrameCallback(this)
            unregisterGyro()
            loader.shutdownNow()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            hasSurface = true
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            hasSurface = true
            val sizeChanged = width != sw || height != sh
            sw = width
            sh = height
            textPaint.textSize = width / 26f
            if (sizeChanged) loadCurrent(force = true) else drawFrame()
            scheduleTimer()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            hasSurface = false
            Choreographer.getInstance().removeFrameCallback(this)
            frameScheduled = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                batteryBlocked = Battery.blocks3d(this@PhotoWallpaperService)
                if (!isPreview) report()
                val nowLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                if (nowLocked != locked) setLocked(nowLocked)
                // Handler timers stop while the phone sleeps, so catch up here.
                if (!isPreview && !paused && dueForChange()) advance()
                scheduleTimer()
                if (current == null) loadCurrent(force = false)
                drawFrame()
            }
            updateGyro()
        }

        // ---------- settings ----------

        private fun readSettings() {
            transition = Store.transition()
            fit = Store.fit()
            gyroOn = Store.gyroOn()
            intensity = Store.gyroIntensity()
            smooth = Store.gyroSmooth()
            style = Store.gyroStyle()
            paused = Store.paused()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            readSettings()
            when (key) {
                Keys.PHOTO_INDEX, Keys.PHOTO_INDEX_LOCK, Keys.SELECTED,
                Keys.FOLDERS, Keys.FOLDER_TARGETS -> loadCurrent(force = false)
                Keys.BAT_3D_SAVER, Keys.BAT_3D_LOW -> {
                    batteryBlocked = Battery.blocks3d(this@PhotoWallpaperService)
                    updateGyro()
                }
                Keys.CROPS -> refreshCrop()
                Keys.FIT -> drawFrame()
                Keys.INTERVAL_MIN, Keys.LAST_CHANGE, Keys.PAUSED -> scheduleTimer()
                Keys.CHANGE_NOW -> if (!isPreview) advance()
                Keys.GYRO, Keys.GYRO_INTENSITY, Keys.GYRO_SMOOTH, Keys.GYRO_STYLE -> {
                    updateGyro()
                    lastDrawnX = 99f
                    requestFrames()
                    drawFrame()
                }
                Keys.RECENTER -> {
                    tiltX = 0f
                    tiltY = 0f
                    requestFrames()
                }
            }
        }

        // ---------- rotation timer ----------

        private fun intervalMs() = Store.intervalMin().coerceAtLeast(1) * 60_000L

        private fun dueForChange() =
            System.currentTimeMillis() - Store.long(Keys.LAST_CHANGE, 0L) >= intervalMs()

        private fun scheduleTimer() {
            handler.removeCallbacks(changeRunnable)
            if (isPreview || paused) return
            val last = Store.long(Keys.LAST_CHANGE, 0L)
            val delay = (last + intervalMs() - System.currentTimeMillis()).coerceIn(1_000L, intervalMs())
            handler.postDelayed(changeRunnable, delay)
        }

        private fun onTimer() {
            if (!paused) advance() else scheduleTimer()
        }

        private fun advance() {
            val now = System.currentTimeMillis()
            // Two wallpaper copies (home + lock) may both ask at once: change only once.
            if (now - Store.long(Keys.LAST_CHANGE, 0L) < 1_500L) return
            val shuffle = Store.order() == "shuffle"
            Store.edit {
                for (t in listOf("home", "lock")) {
                    val size = Store.photosFor(t).size
                    if (size < 2) continue
                    val cur = Store.int(Store.indexKey(t), 0).mod(size)
                    val next = if (shuffle) {
                        var n: Int
                        do {
                            n = Random.nextInt(size)
                        } while (n == cur)
                        n
                    } else {
                        (cur + 1) % size
                    }
                    putInt(Store.indexKey(t), next)
                }
                putLong(Keys.LAST_CHANGE, now)
            }
        }

        // ---------- home / lock ----------

        /** "home" or "lock": which set of photos this engine shows right now. */
        private fun target(): String {
            if (isPreview) return "home"
            val f = screenFlags()
            val home = f and android.app.WallpaperManager.FLAG_SYSTEM != 0
            val lock = f and android.app.WallpaperManager.FLAG_LOCK != 0
            return when {
                home && lock -> if (locked) "lock" else "home"
                lock -> "lock"
                else -> "home"
            }
        }

        private fun setLocked(value: Boolean) {
            if (locked == value) return
            locked = value
            loadCurrent(force = false)
        }

        private fun report() {
            val f = screenFlags()
            if (f == reportedFlags) return
            if (reportedFlags != 0) reportScreens(video = false, flags = reportedFlags, running = false)
            reportScreens(video = false, flags = f, running = true)
            reportedFlags = f
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            if (!isPreview) report()
            loadCurrent(force = false)
        }

        // ---------- loading ----------

        private fun currentUri(): String? = Store.currentPhoto(target())

        private fun refreshCrop() {
            val cur = current ?: return
            val crop = Store.crops()[cur.uri]
            current = Slide(cur.uri, cur.bitmap, cur.blur, crop, PhotoMath.brightnessFilter(crop?.brightness ?: 0f))
            drawFrame()
        }

        private fun loadCurrent(force: Boolean) {
            val uri = currentUri()
            if (uri == null) {
                current = null
                previous = null
                drawFrame()
                return
            }
            if (!force && (current?.uri == uri || loadingUri == uri)) return
            if (sw == 0 || sh == 0) return
            loadingUri = uri
            val gen = ++loadGeneration
            val longSide = (max(sw, sh) * 1.25f).toInt()
            val crop = Store.crops()[uri]
            loader.execute {
                var reason: String? = null
                val slide = try {
                    val bmp = PhotoMath.decode(this@PhotoWallpaperService, uri, longSide)
                    val blur = Bitmap.createScaledBitmap(
                        bmp, max(1, bmp.width / 24), max(1, bmp.height / 24), true
                    )
                    Slide(uri, bmp, blur, crop, PhotoMath.brightnessFilter(crop?.brightness ?: 0f))
                } catch (t: Throwable) {
                    reason = if (t is SecurityException) "no permission" else (t.message ?: t.javaClass.simpleName)
                    null
                }
                handler.post { onLoaded(gen, uri, slide, reason) }
            }
        }

        private fun onLoaded(gen: Int, uri: String, slide: Slide?, reason: String? = null) {
            if (gen != loadGeneration) return
            loadingUri = null
            val t = target()
            val list = Store.photosFor(t)
            if (slide == null) {
                // Photo deleted or access lost: skip it (without waiting for the timer).
                failedInARow++
                if (!isPreview) {
                    Store.log("Couldn't open a $t photo ($reason), skipped")
                    if (failedInARow < list.size) {
                        Store.edit { putInt(Store.indexKey(t), Store.int(Store.indexKey(t), 0) + 1) }
                    }
                }
                return
            }
            failedInARow = 0
            if (!isPreview && current?.uri != uri) {
                val i = list.indexOf(uri)
                Store.log("$t: showing photo ${i + 1} of ${list.size}")
            }
            val old = current
            val animate = old != null && old.uri != uri && transition != "none" && visible
            previous = if (animate) old else null
            current = slide
            transitionStart = SystemClock.uptimeMillis()
            if (animate) requestFrames() else drawFrame()
        }

        // ---------- gyro ----------

        private fun gyroWanted() = visible && gyroOn && intensity > 0f && !batteryBlocked

        private fun updateGyro() {
            val sm = sensorManager ?: return
            if (gyroWanted() && !gyroRegistered) {
                val sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
                lastSensorTs = 0L
                gyroRegistered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
                requestFrames()
            } else if (!gyroWanted() && gyroRegistered) {
                unregisterGyro()
                tiltX = 0f
                tiltY = 0f
                smoothX = 0f
                smoothY = 0f
                drawFrame()
            }
        }

        private fun unregisterGyro() {
            if (gyroRegistered) sensorManager?.unregisterListener(this)
            gyroRegistered = false
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (lastSensorTs != 0L) {
                val dt = (event.timestamp - lastSensorTs) / 1_000_000_000f
                if (dt > 0f && dt < 0.2f) {
                    // Turning the phone left/right moves the picture sideways,
                    // tipping it forward/back moves it up/down.
                    tiltX += event.values[1] * dt
                    tiltY += event.values[0] * dt
                    // Drift slowly back to centre so the picture never gets stuck.
                    val decay = exp(-dt / 1.6f)
                    tiltX = (tiltX * decay).coerceIn(-LIMIT, LIMIT)
                    tiltY = (tiltY * decay).coerceIn(-LIMIT, LIMIT)
                }
            }
            lastSensorTs = event.timestamp
            requestFrames()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

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
            val inTransition = previous != null &&
                SystemClock.uptimeMillis() - transitionStart < TRANSITION_MS
            // Gyro motion is drawn at most 30 times a second to save battery.
            if (!inTransition && frameTimeNanos - lastDrawNanos < 30_000_000L) {
                requestFrames()
                return
            }
            lastDrawNanos = frameTimeNanos
            val alpha = (0.35f - 0.3f * smooth) * 1.6f
            val targetX = if (gyroRegistered) tiltX / LIMIT else 0f
            val targetY = if (gyroRegistered) tiltY / LIMIT else 0f
            smoothX += (targetX - smoothX) * alpha
            smoothY += (targetY - smoothY) * alpha

            val transitioning = previous != null &&
                SystemClock.uptimeMillis() - transitionStart < TRANSITION_MS
            val moved = abs(smoothX - lastDrawnX) > 0.002f || abs(smoothY - lastDrawnY) > 0.002f
            if (transitioning || moved || previous != null) drawFrame()
            if (!transitioning) previous = null

            val settling = abs(targetX - smoothX) > 0.002f || abs(targetY - smoothY) > 0.002f
            if (transitioning || settling) requestFrames()
        }

        private fun lockCanvas(holder: SurfaceHolder): Canvas? {
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

        private fun drawFrame() {
            if (!hasSurface || sw == 0) return
            val holder = surfaceHolder
            val canvas = lockCanvas(holder) ?: return
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
                lastDrawnX = smoothX
                lastDrawnY = smoothY
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }

        private fun drawEmpty(canvas: Canvas) {
            canvas.drawColor(Color.rgb(0x1D, 0x20, 0x24))
            canvas.drawText("Open Wallpaper Studio", sw / 2f, sh / 2f - textPaint.textSize, textPaint)
            canvas.drawText("and choose your photos", sw / 2f, sh / 2f + textPaint.textSize * 0.4f, textPaint)
        }

        private fun drawSlide(canvas: Canvas, s: Slide, slideX: Float, extraScale: Float, alpha: Int) {
            val w = sw.toFloat()
            val h = sh.toFloat()
            val inten = if (gyroRegistered) intensity else 0f
            val nx = smoothX
            val ny = smoothY
            val over = 1f + 0.14f * inten
            var scale = over * extraScale
            if (style == "zoom") scale *= 1f + 0.06f * inten * min(1f, hypot(nx, ny))
            val px = nx * 0.05f * inten * w + slideX
            val py = ny * 0.05f * inten * h

            paint.alpha = alpha
            paint.colorFilter = s.filter

            val effectiveFit = if (s.crop != null) "fill" else fit
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
    }

    companion object {
        private const val TRANSITION_MS = 900L
        private const val LIMIT = 0.45f
    }
}
