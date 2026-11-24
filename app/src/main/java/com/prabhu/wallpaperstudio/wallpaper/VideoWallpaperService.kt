package com.prabhu.wallpaperstudio.wallpaper

import android.content.SharedPreferences
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import java.io.File

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private var player: ExoPlayer? = null
        private var loadedPath: String? = null
        private var visible = false
        private var hasSurface = false
        private var reportedFlags = 0

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            Store.prefs.registerOnSharedPreferenceChangeListener(this)
            if (!isPreview) report()
        }

        private fun report() {
            val f = screenFlags()
            if (f == reportedFlags) return
            if (reportedFlags != 0) reportScreens(video = true, flags = reportedFlags, running = false)
            reportScreens(video = true, flags = f, running = true)
            reportedFlags = f
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            if (!isPreview) report()
        }

        private fun shouldPlay() = visible && !Store.paused() && !Battery.freezesVideo(this@VideoWallpaperService)

        private fun volume() = if (Store.bool(Keys.VIDEO_SOUND, false)) 1f else 0f

        override fun onDestroy() {
            Store.prefs.unregisterOnSharedPreferenceChangeListener(this)
            if (!isPreview && reportedFlags != 0) reportScreens(video = true, flags = reportedFlags, running = false)
            release()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            hasSurface = true
            start()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            hasSurface = true
            if (player == null) start()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            hasSurface = false
            release()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible && !isPreview) report()
            if (isVisible && player == null) start()
            // Pauses when the screen is off or an app covers the wallpaper.
            player?.playWhenReady = shouldPlay()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            when (key) {
                Keys.VIDEO_PATH -> {
                    release()
                    start()
                }
                Keys.PAUSED, Keys.BAT_VIDEO_SAVER -> player?.playWhenReady = shouldPlay()
                Keys.VIDEO_SOUND -> player?.volume = volume()
            }
        }

        @OptIn(UnstableApi::class)
        private fun start() {
            if (!hasSurface) return
            val path = Store.prefs.getString(Keys.VIDEO_PATH, null) ?: return
            val file = File(path)
            if (!file.exists()) return
            if (player != null && loadedPath == path) return
            release()
            val p = ExoPlayer.Builder(this@VideoWallpaperService).build()
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = volume()
            p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            p.setVideoSurface(surfaceHolder.surface)
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            p.prepare()
            p.playWhenReady = shouldPlay()
            player = p
            loadedPath = path
        }

        private fun release() {
            player?.let {
                it.clearVideoSurface()
                it.release()
            }
            player = null
            loadedPath = null
        }
    }
}
