package com.prabhu.wallpaperstudio.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.prabhu.wallpaperstudio.data.CalendarSource
import com.prabhu.wallpaperstudio.data.Crop
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Weather
import com.prabhu.wallpaperstudio.data.WidgetDataSource
import com.prabhu.wallpaperstudio.data.WidgetItem
import com.prabhu.wallpaperstudio.data.Widgets
import com.prabhu.wallpaperstudio.render.LockChanger
import com.prabhu.wallpaperstudio.render.WidgetRenderer
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the Home screen video wallpaper with OpenGL: upright for any video,
 * fill / fit / custom crop, 3D gyro effect and widgets on top.
 */
@OptIn(UnstableApi::class)
class VideoRenderer(
    private val context: Context,
    private val holder: SurfaceHolder,
    private val live: Boolean,
    private val flags: () -> Int,
) : SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var gl: GlVideo? = null
    private var player: ExoPlayer? = null
    private var visible = false
    private var hasSurface = false
    private var sw = 0
    private var sh = 0
    private var videoW = 0
    private var videoH = 0
    private var unappliedRotation = 0
    private var locked = false
    private var reportedFlags = 0
    private var batteryBlocked = false
    private var renderPending = false
    private var frameScheduled = false
    private var lastDrawNanos = 0L

    private var gyroOn = true
    private var intensity = 0.55f
    private var smooth = 0.75f
    private var style = "tilt"
    private var fit = "fill"
    private var crop: Crop? = null
    private var widgets: List<WidgetItem> = emptyList()
    private var widgetsOn = false
    private var shadow = true
    private var overlay: Bitmap? = null

    private val gyro = GyroTracker(context) { requestFrames() }
    private val renderRunnable = Runnable {
        renderPending = false
        render()
    }
    private val minuteRunnable = object : Runnable {
        override fun run() {
            updateOverlay()
            scheduleMinute()
        }
    }

    private var receiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    locked = true
                    if (Store.lockMode() == "slideshow" && !Store.paused() && LockChanger.isDue()) {
                        LockChanger.changeAsync(context, "screen off")
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    locked = false
                    requestRender()
                }
            }
        }
    }

    fun create() {
        readSettings()
        Store.prefs.registerOnSharedPreferenceChangeListener(this)
        locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (live) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
            report()
        }
    }

    fun destroy() {
        Store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (live && reportedFlags != 0) reportScreens(video = true, flags = reportedFlags, running = false)
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
        }
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(this)
        gyro.stop()
        teardown()
        io.shutdownNow()
    }

    fun surfaceCreated() {
        hasSurface = true
    }

    fun surfaceChanged(width: Int, height: Int) {
        hasSurface = true
        val changed = width != sw || height != sh
        sw = width
        sh = height
        if (gl == null) setup()
        if (changed) updateOverlay()
        requestRender()
    }

    fun surfaceDestroyed() {
        hasSurface = false
        teardown()
    }

    fun visibilityChanged(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) {
            batteryBlocked = Battery.blocks3d(context)
            if (live) report()
            locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            if (gl == null) setup()
            refreshWidgetData()
            updateOverlay()
            scheduleMinute()
        } else {
            handler.removeCallbacks(minuteRunnable)
        }
        player?.playWhenReady = shouldPlay()
        updateGyro()
        requestRender()
    }

    fun flagsChanged() {
        if (live) report()
        requestRender()
    }

    // ---------- settings ----------

    private fun readSettings() {
        gyroOn = Store.gyroOn()
        intensity = Store.gyroIntensity()
        smooth = Store.gyroSmooth()
        style = Store.gyroStyle()
        fit = Store.string(Keys.VIDEO_FIT, "fill")
        crop = Store.crops()[VIDEO_CROP_KEY]
        widgets = Widgets.load()
        widgetsOn = widgets.any { it.on }
        shadow = Store.bool(Keys.WIDGET_SHADOW, true)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == Keys.ENGINE_LOG) return
        readSettings()
        when (key) {
            Keys.VIDEO_PATH -> {
                teardown()
                setup()
            }
            Keys.VIDEO_SOUND -> player?.volume = volume()
            Keys.PAUSED, Keys.BAT_VIDEO_SAVER -> player?.playWhenReady = shouldPlay()
            Keys.WIDGETS, Keys.WIDGET_SHADOW, Keys.WEATHER_TEXT -> {
                refreshWidgetData()
                updateOverlay()
                scheduleMinute()
            }
            Keys.GYRO, Keys.GYRO_INTENSITY, Keys.GYRO_SMOOTH, Keys.GYRO_STYLE, Keys.BAT_3D_SAVER, Keys.BAT_3D_LOW -> {
                batteryBlocked = Battery.blocks3d(context)
                updateGyro()
            }
            Keys.RECENTER -> gyro.recenter()
        }
        requestRender()
    }

    private fun volume() = if (Store.bool(Keys.VIDEO_SOUND, false)) 1f else 0f

    private fun shouldPlay() = visible && !Store.paused() && !Battery.freezesVideo(context)

    // ---------- player + OpenGL ----------

    private fun setup() {
        if (!hasSurface || gl != null) return
        val path = Store.prefs.getString(Keys.VIDEO_PATH, null)
        val g = GlVideo()
        if (!g.init(holder.surface, handler) { requestRender() }) {
            g.release()
            if (live) Store.log("Video: this phone refused OpenGL")
            return
        }
        gl = g
        if (path == null || !File(path).exists()) {
            if (live) Store.log("Video: no video chosen yet")
            return
        }
        val p = ExoPlayer.Builder(context).build()
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoW = videoSize.width
                videoH = videoSize.height
                unappliedRotation = videoSize.unappliedRotationDegrees
                requestRender()
            }
        })
        p.setVideoSurface(g.videoSurface)
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.volume = volume()
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        p.prepare()
        p.playWhenReady = shouldPlay()
        player = p
    }

    private fun teardown() {
        player?.let {
            it.clearVideoSurface()
            it.release()
        }
        player = null
        gl?.release()
        gl = null
        videoW = 0
        videoH = 0
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
                handler.post { updateOverlay() }
            }
        }
    }

    private fun updateOverlay() {
        val g = gl ?: return
        if (!widgetsOn || sw == 0) {
            g.setOverlay(null)
            return
        }
        val bmp = overlay?.takeIf { it.width == sw && it.height == sh }
            ?: Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { overlay = it }
        bmp.eraseColor(Color.TRANSPARENT)
        WidgetRenderer.draw(Canvas(bmp), sw, sh, widgets, WidgetDataSource.read(context), shadow)
        g.setOverlay(bmp)
        requestRender()
    }

    private fun scheduleMinute() {
        handler.removeCallbacks(minuteRunnable)
        if (!visible || !widgetsOn) return
        val now = System.currentTimeMillis()
        handler.postDelayed(minuteRunnable, 60_000L - now % 60_000L + 200L)
    }

    private fun showWidgets(): Boolean {
        if (!widgetsOn) return false
        val onLock = flags() and WallpaperManager.FLAG_LOCK != 0
        return !(onLock && locked)
    }

    // ---------- gyro ----------

    private fun updateGyro() {
        val wanted = visible && gyroOn && intensity > 0f && !batteryBlocked
        if (wanted && !gyro.running) {
            gyro.start()
            requestFrames()
        } else if (!wanted && gyro.running) {
            gyro.stop()
            requestRender()
        }
    }

    private fun requestFrames() {
        if (!frameScheduled && visible && hasSurface) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (!visible) return
        if (frameTimeNanos - lastDrawNanos < 30_000_000L) {
            requestFrames()
            return
        }
        lastDrawNanos = frameTimeNanos
        val settling = gyro.step(smooth)
        // When the video plays, its own frames trigger drawing; otherwise draw here.
        if (player?.isPlaying != true) render()
        if (settling) requestFrames()
    }

    // ---------- drawing ----------

    private fun requestRender() {
        if (renderPending) return
        renderPending = true
        handler.post(renderRunnable)
    }

    private fun render() {
        val g = gl ?: return
        if (sw == 0 || sh == 0) return
        val c = crop
        val rot = unappliedRotation + (c?.rotation ?: 0)
        val dw = if (rot % 180 == 0) videoW else videoH
        val dh = if (rot % 180 == 0) videoH else videoW
        var scale = 1f
        if (dw > 0 && dh > 0) {
            val fillS = max(sw / dw.toFloat(), sh / dh.toFloat())
            val fitS = min(sw / dw.toFloat(), sh / dh.toFloat())
            scale = if (c != null) fillS * c.zoom else if (fit == "fit") fitS else fillS
        }
        val inten = if (gyro.running) intensity else 0f
        val nx = gyro.x
        val ny = gyro.y
        scale *= 1f + 0.14f * inten
        if (style == "zoom") scale *= 1f + 0.06f * inten * min(1f, hypot(nx, ny))
        val dx = (c?.x ?: 0f) * sw + nx * 0.05f * inten * sw
        val dy = (c?.y ?: 0f) * sh + ny * 0.05f * inten * sh
        val tilt = style == "tilt" && inten > 0f
        g.draw(
            sw, sh, videoW, videoH, rot, c?.flip == true, scale, dx, dy,
            if (tilt) nx * 9f * inten else 0f,
            if (tilt) -ny * 9f * inten else 0f,
            1f, showWidgets(),
        )
    }

    private fun report() {
        val f = flags()
        if (f == reportedFlags) return
        if (reportedFlags != 0) reportScreens(video = true, flags = reportedFlags, running = false)
        reportScreens(video = true, flags = f, running = true)
        reportedFlags = f
    }

    @Suppress("unused")
    private fun isMoving() = abs(gyro.x) > 0.001f || abs(gyro.y) > 0.001f

    companion object {
        const val VIDEO_CROP_KEY = "video"
    }
}
