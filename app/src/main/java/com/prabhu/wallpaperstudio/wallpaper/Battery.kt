package com.prabhu.wallpaperstudio.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store

object Battery {
    fun saverOn(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    /** Battery percent, or -1 when unknown. */
    fun level(context: Context): Int {
        val s = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val l = s.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = s.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l >= 0 && sc > 0) l * 100 / sc else -1
    }

    fun charging(context: Context): Boolean {
        val s = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return (s.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) != 0
    }

    /** True when the battery is at or below the limit the person chose (and not charging). */
    fun lowStop(context: Context): Boolean {
        if (!Store.bool(Keys.BAT_STOP_ON, true)) return false
        val limit = Store.int(Keys.BAT_STOP_LEVEL, 20)
        val l = level(context)
        return l in 0..limit && !charging(context)
    }

    /** Why everything is stopped right now, or null when it is running. */
    fun stopReason(context: Context): String? = when {
        Store.paused() -> "Paused by you"
        lowStop(context) -> "Battery is ${level(context)}% (limit ${Store.int(Keys.BAT_STOP_LEVEL, 20)}%)"
        Store.bool(Keys.BAT_SAVER_STOP, true) && saverOn(context) -> "Battery Saver is on"
        else -> null
    }

    /** Photos stop changing and the 3D effect stops. */
    fun stopAll(context: Context): Boolean = stopReason(context) != null

    /** Whether the 3D effect should stay off right now to save battery. */
    fun blocks3d(context: Context): Boolean = stopAll(context)
}

/** Which screens an engine draws on: home (FLAG_SYSTEM) and/or lock (FLAG_LOCK). */
fun WallpaperService.Engine.screenFlags(): Int {
    if (Build.VERSION.SDK_INT >= 34) {
        val f = wallpaperFlags
        if (f > 0) return f
    }
    return WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
}

/** Records which screens this wallpaper is on, for the app's Home tab. */
fun reportScreens(flags: Int, running: Boolean) {
    val home = flags and WallpaperManager.FLAG_SYSTEM != 0
    val lock = flags and WallpaperManager.FLAG_LOCK != 0
    Store.edit {
        if (home) putBoolean(Keys.ACTIVE_PHOTO_HOME, running)
        if (lock) putBoolean(Keys.ACTIVE_PHOTO_LOCK, running)
    }
}
