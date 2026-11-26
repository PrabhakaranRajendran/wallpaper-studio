package com.prabhu.wallpaperstudio.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/** The Home screen photo wallpaper. All the work is done by [PhotoRenderer]. */
class PhotoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PhotoEngine()

    inner class PhotoEngine : Engine() {
        private lateinit var renderer: PhotoRenderer

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            renderer = PhotoRenderer(this@PhotoWallpaperService, surfaceHolder, live = !isPreview) { screenFlags() }
            renderer.create()
        }

        override fun onDestroy() {
            renderer.destroy()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer.surfaceCreated()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.surfaceChanged(width, height)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            renderer.redrawNeeded()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer.surfaceDestroyed()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            renderer.visibilityChanged(visible)
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            renderer.flagsChanged()
        }
    }
}
