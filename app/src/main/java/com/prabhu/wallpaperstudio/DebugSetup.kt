package com.prabhu.wallpaperstudio

import android.content.Context
import android.content.Intent
import com.prabhu.wallpaperstudio.data.Albums
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Widgets
import com.prabhu.wallpaperstudio.render.LockChanger

/**
 * Test builds only (called when BuildConfig.DEBUG): lets automatic tests set
 * the app up from the command line, e.g.
 *   adb shell am start -n com.prabhu.wallpaperstudio/.MainActivity --ez demo true --es tab wallpaper
 */
object DebugSetup {
    fun apply(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("gen_photos", false)) generatePhotos(context)
        if (intent.hasExtra("demo") || intent.hasExtra("tab")) {
            // Keep the update pop-up out of test screenshots.
            com.prabhu.wallpaperstudio.update.Updater.dialogDismissed = true
            Store.edit { putLong(Keys.LAST_UPDATE_CHECK, System.currentTimeMillis()) }
        }
        if (intent.getBooleanExtra("demo", false)) {
            Store.edit {
                putString(Keys.HOME_SOURCE, "photos")
                putInt(Keys.interval(Target.HOME), 1)
                putString(Keys.LOCK_MODE, "slideshow")
                putInt(Keys.interval(Target.LOCK), 0)
                putString(Keys.WEATHER_TEXT, "28° Partly cloudy · Chennai")
            }
            Widgets.save(
                Widgets.defaults().map {
                    when (it.type) {
                        "clock", "date", "battery", "weather" -> it.copy(on = true)
                        "text" -> it.copy(on = true, text = "Wallpaper Studio")
                        else -> it
                    }
                }
            )
            PhotoLists.invalidate()
        }
        intent.getStringExtra("video")?.let { path ->
            Store.edit {
                putString(Keys.VIDEO_PATH, path)
                putString(Keys.HOME_SOURCE, "video")
            }
        }
        intent.getStringExtra("palette")?.let { p -> Store.edit { putString(Keys.PALETTE, p) } }
        intent.getStringExtra("mode")?.let { m -> Store.edit { putString(Keys.THEME, m) } }
        intent.getStringExtra("gyro")?.let { g -> Store.edit { putBoolean(Keys.GYRO, g == "on") } }
        intent.getStringExtra("only")?.let { part ->
            // Show only the Home photos whose name contains [part] ("" shows all).
            Albums.allPhotos(context, Target.HOME).forEach { ph ->
                Albums.setHidden(Target.HOME, ph, part.isNotEmpty() && !ph.contains(part))
            }
            PhotoLists.invalidate()
        }
        if (intent.getBooleanExtra("lock_now", false)) LockChanger.changeAsync(context, "test")
    }

    /** Draws simple test pictures into both albums (labelled, so screenshots show which is which). */
    private fun generatePhotos(context: Context) {
        fun make(target: String, name: String, w: Int, h: Int, top: Int, bottom: Int) {
            val file = java.io.File(com.prabhu.wallpaperstudio.data.Albums.dir(context, target), "$name.jpg")
            if (file.exists()) return
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
            p.color = android.graphics.Color.WHITE
            p.alpha = 70
            for (i in 1..5) c.drawCircle(w * i / 6f, h * 0.7f, w / 10f, p)
            p.alpha = 255
            p.textSize = w / 9f
            p.textAlign = android.graphics.Paint.Align.CENTER
            c.drawText(name, w / 2f, h / 2f, p)
            p.textSize = w / 22f
            c.drawText("TOP", w / 2f, h * 0.06f, p)
            c.drawText("BOTTOM", w / 2f, h * 0.97f, p)
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }
        make(Target.HOME, "Home 1", 1080, 2400, 0xFF1E88E5.toInt(), 0xFF0D1B24.toInt())
        make(Target.HOME, "Home 2", 1080, 2400, 0xFFE53935.toInt(), 0xFF2A1410.toInt())
        make(Target.HOME, "Home 3", 1080, 2400, 0xFF43A047.toInt(), 0xFF0F2A25.toInt())
        make(Target.HOME, "Home 4 wide", 2400, 1080, 0xFF8E24AA.toInt(), 0xFF120D1F.toInt())
        make(Target.LOCK, "Lock 1", 1080, 2400, 0xFFFFB300.toInt(), 0xFF2A1E05.toInt())
        make(Target.LOCK, "Lock 2", 1080, 2400, 0xFF00ACC1.toInt(), 0xFF08131B.toInt())
        make(Target.LOCK, "Lock 3", 1080, 2400, 0xFFD81B60.toInt(), 0xFF1C0D12.toInt())
        PhotoLists.invalidate()
    }
}
