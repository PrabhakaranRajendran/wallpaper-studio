package com.prabhu.wallpaperstudio.debug

import android.app.Activity
import android.app.WallpaperManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.wallpaper.PhotoRenderer
import com.prabhu.wallpaperstudio.wallpaper.VideoRenderer

/**
 * Test builds only. Runs the same renderer as the live wallpaper inside a
 * normal screen, so automatic tests can take screenshots of it.
 *   adb shell am start -n com.prabhu.wallpaperstudio/.debug.RenderTestActivity --es kind photo|video
 */
class RenderTestActivity : Activity(), SurfaceHolder.Callback {

    private var photo: PhotoRenderer? = null
    private var video: VideoRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = SurfaceView(this)
        setContentView(view)
        val flags = { WallpaperManager.FLAG_SYSTEM }
        if (intent.getStringExtra("kind") == "video") {
            video = VideoRenderer(this, view.holder, live = false, flags = flags).also { it.create() }
        } else {
            photo = PhotoRenderer(this, view.holder, live = false, flags = flags).also { it.create() }
        }
        view.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        photo?.surfaceCreated()
        video?.surfaceCreated()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        photo?.surfaceChanged(width, height)
        video?.surfaceChanged(width, height)
        photo?.visibilityChanged(true)
        video?.visibilityChanged(true)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        photo?.surfaceDestroyed()
        video?.surfaceDestroyed()
    }

    override fun onPause() {
        super.onPause()
        photo?.visibilityChanged(false)
        video?.visibilityChanged(false)
    }

    override fun onDestroy() {
        photo?.destroy()
        video?.destroy()
        super.onDestroy()
    }
}
