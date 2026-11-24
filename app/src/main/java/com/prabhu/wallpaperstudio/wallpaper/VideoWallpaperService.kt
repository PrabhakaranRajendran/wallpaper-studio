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

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            Store.prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            Store.prefs.unregisterOnSharedPreferenceChangeListener(this)
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
            if (isVisible && player == null) start()
            // Pauses when the screen is off or an app covers the wallpaper.
            player?.playWhenReady = isVisible && !Store.paused()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            when (key) {
                Keys.VIDEO_PATH -> {
                    release()
                    start()
                }
                Keys.PAUSED -> player?.playWhenReady = visible && !Store.paused()
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
            p.volume = 0f
            p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            p.setVideoSurface(surfaceHolder.surface)
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            p.prepare()
            p.playWhenReady = visible && !Store.paused()
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
