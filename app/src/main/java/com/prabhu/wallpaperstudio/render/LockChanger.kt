package com.prabhu.wallpaperstudio.render

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.random.Random

/**
 * Lock screen slideshow. Sets a normal still picture on the lock screen,
 * which works on every phone (OnePlus lock screens don't run live wallpapers).
 */
object LockChanger {

    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    fun isDue(): Boolean {
        val interval = Store.interval(Target.LOCK)
        if (interval <= 0) return true // every time the screen turns off
        return System.currentTimeMillis() - Store.long(Keys.last(Target.LOCK), 0L) >= interval * 60_000L - 30_000L
    }

    /** Changes the lock screen in the background. */
    fun changeAsync(context: Context, reason: String) {
        if (!busy.compareAndSet(false, true)) return
        val app = context.applicationContext
        executor.execute {
            try {
                change(app, reason)
            } finally {
                busy.set(false)
            }
        }
    }

    /** Picks the next photo and sets it. Returns false if there was nothing to set. Runs on the calling thread. */
    fun change(context: Context, reason: String): Boolean {
        val list = PhotoLists.get(context, Target.LOCK, maxAgeMs = 0L)
        if (list.isEmpty()) {
            Store.log("Lock screen: no photos to show")
            return false
        }
        val cur = Store.int(Keys.index(Target.LOCK), -1)
        val next = when {
            list.size == 1 -> 0
            Store.order(Target.LOCK) == "shuffle" -> {
                var n: Int
                do {
                    n = Random.nextInt(list.size)
                } while (n == cur.mod(list.size))
                n
            }
            else -> (cur + 1).mod(list.size)
        }
        // Try photos in turn until one opens.
        for (attempt in list.indices) {
            val i = (next + attempt) % list.size
            val photo = list[i]
            try {
                setPhoto(context, photo)
                Store.edit {
                    putInt(Keys.index(Target.LOCK), i)
                    putLong(Keys.last(Target.LOCK), System.currentTimeMillis())
                    putString(Keys.LOCK_LAST_PHOTO, photo)
                }
                Store.log("Lock screen: photo ${i + 1} of ${list.size} ($reason)")
                return true
            } catch (e: Throwable) {
                Store.log("Lock screen: couldn't use a photo (${e.message ?: e.javaClass.simpleName})")
            }
        }
        return false
    }

    /** Draws the photo at screen size (with its crop and the fit setting) and sets it on the lock screen. */
    fun setPhoto(context: Context, photo: String) {
        val screen = PhotoMath.screenSize(context)
        val w = screen.x
        val h = screen.y
        val src = PhotoMath.decode(context, photo, max(w, h))
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val crop = Store.crops()[photo]
        val fit = if (crop != null) "fill" else PhotoMath.resolveFit(Store.fit(Target.LOCK), src.width, src.height, w.toFloat(), h.toFloat())
        val m = Matrix()
        if (fit == "blur") {
            val small = PhotoMath.softBlur(src)
            PhotoMath.place(m, small.width, small.height, w.toFloat(), h.toFloat(), null, "fill", 1.05f)
            canvas.drawBitmap(small, m, paint)
            small.recycle()
        }
        paint.colorFilter = PhotoMath.brightnessFilter(crop?.brightness ?: 0f)
        PhotoMath.place(m, src.width, src.height, w.toFloat(), h.toFloat(), crop, fit)
        canvas.drawBitmap(src, m, paint)
        src.recycle()
        WallpaperManager.getInstance(context).setBitmap(out, null, true, WallpaperManager.FLAG_LOCK)
        out.recycle()
    }
}
