package com.prabhu.wallpaperstudio.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store

object Battery {
    fun saverOn(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    fun lowAndNotCharging(context: Context): Boolean {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return false
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 0..19 && !bm.isCharging
    }

    /** Whether the 3D effect should stay off right now to save battery. */
    fun blocks3d(context: Context): Boolean =
        (Store.bool(Keys.BAT_3D_SAVER, true) && saverOn(context)) ||
            (Store.bool(Keys.BAT_3D_LOW, true) && lowAndNotCharging(context))

    fun freezesVideo(context: Context): Boolean =
        Store.bool(Keys.BAT_VIDEO_SAVER, true) && saverOn(context)
}

/** Which screens an engine draws on: home (FLAG_SYSTEM) and/or lock (FLAG_LOCK). */
fun WallpaperService.Engine.screenFlags(): Int {
    if (Build.VERSION.SDK_INT >= 34) {
        val f = wallpaperFlags
        if (f > 0) return f
    }
    return WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
}

/** Records which screens this kind of wallpaper is on, for the app's Home tab. */
fun reportScreens(video: Boolean, flags: Int, running: Boolean) {
    val home = flags and WallpaperManager.FLAG_SYSTEM != 0
    val lock = flags and WallpaperManager.FLAG_LOCK != 0
    val homeKey = if (video) Keys.ACTIVE_VIDEO_HOME else Keys.ACTIVE_PHOTO_HOME
    val lockKey = if (video) Keys.ACTIVE_VIDEO_LOCK else Keys.ACTIVE_PHOTO_LOCK
    Store.edit {
        if (home) putBoolean(homeKey, running)
        if (lock) putBoolean(lockKey, running)
    }
}
