package com.prabhu.wallpaperstudio.render

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Point
import android.net.Uri
import android.view.WindowManager
import com.prabhu.wallpaperstudio.data.Crop
import kotlin.math.max
import kotlin.math.min

object PhotoMath {
    /**
     * "smart": photos shaped like the screen fill it; photos much wider than the
     * screen are shown whole on a blurred copy of themselves, so nothing important is cut off.
     */
    /** A small, smooth, slightly dimmed copy of a photo, used behind "Blur fill". */
    fun softBlur(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val k = 48f / maxOf(src.width, src.height)
        val w = maxOf(2, (src.width * k).toInt())
        val h = maxOf(2, (src.height * k).toInt())
        val soft = if (src.config == android.graphics.Bitmap.Config.HARDWARE) src.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else src
        val small = android.graphics.Bitmap.createScaledBitmap(soft, w, h, true)
        if (soft !== src && soft !== small) soft.recycle()
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxPass(px, tmp, w, h, 2, horizontal = true)
            boxPass(tmp, px, w, h, 2, horizontal = false)
        }
        for (i in px.indices) {
            val c = px[i]
            val r = ((c shr 16) and 255) * 4 / 5
            val g = ((c shr 8) and 255) * 4 / 5
            val b = (c and 255) * 4 / 5
            px[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        return out
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var sr = 0; var sg = 0; var sb = 0; var n = 0
                for (d in -r..r) {
                    val j = (i + d).coerceIn(0, len - 1)
                    val c = if (horizontal) src[line * w + j] else src[j * w + line]
                    sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255; n++
                }
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = (255 shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
    }

    fun resolveFit(fit: String, bw: Int, bh: Int, sw: Float, sh: Float): String {
        if (fit != "smart") return fit
        val photo = bw.toFloat() / bh.coerceAtLeast(1)
        val screen = sw / sh.coerceAtLeast(1f)
        return if (photo > screen * 1.6f) "blur" else "fill"
    }


    /**
     * Places a bitmap on a screen of size sw x sh.
     * fit: "fill" covers the screen, anything else fits inside it.
     * A custom crop is always relative to "fill".
     */
    fun place(
        out: Matrix,
        bw: Int, bh: Int,
        sw: Float, sh: Float,
        crop: Crop?,
        fit: String,
        extraScale: Float = 1f,
        dx: Float = 0f,
        dy: Float = 0f,
    ) {
        val rot = crop?.rotation ?: 0
        val ew = if (rot % 180 == 0) bw else bh
        val eh = if (rot % 180 == 0) bh else bw
        val fill = max(sw / ew, sh / eh)
        val fitScale = min(sw / ew, sh / eh)
        val base = if (crop != null) fill * crop.zoom else if (fit == "fill") fill else fitScale
        val s = base * extraScale

        out.reset()
        out.postTranslate(-bw / 2f, -bh / 2f)
        if (crop?.flip == true) out.postScale(-1f, 1f)
        if (rot != 0) out.postRotate(rot.toFloat())
        out.postScale(s, s)
        out.postTranslate(
            sw / 2f + (crop?.x ?: 0f) * sw + dx,
            sh / 2f + (crop?.y ?: 0f) * sh + dy,
        )
    }

    /** Largest offset (fraction of screen) that still keeps the screen covered. */
    fun maxOffset(bw: Int, bh: Int, sw: Float, sh: Float, crop: Crop): Pair<Float, Float> {
        val ew = if (crop.rotation % 180 == 0) bw else bh
        val eh = if (crop.rotation % 180 == 0) bh else bw
        val s = max(sw / ew, sh / eh) * crop.zoom
        val mx = ((ew * s - sw) / 2f / sw).coerceAtLeast(0f)
        val my = ((eh * s - sh) / 2f / sh).coerceAtLeast(0f)
        return mx to my
    }

    fun brightnessFilter(b: Float): ColorMatrixColorFilter? {
        if (b == 0f) return null
        val k = 1f + b
        return ColorMatrixColorFilter(ColorMatrix().apply { setScale(k, k, k, 1f) })
    }

    /** Real screen size in portrait (width < height). */
    @Suppress("DEPRECATION")
    fun screenSize(context: Context): Point {
        val p = Point()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(p)
        return Point(min(p.x, p.y), max(p.x, p.y))
    }

    /** Decodes a photo just big enough for a screen whose long side is [longSide]. */
    fun decode(context: Context, uri: String, longSide: Int) = ImageDecoder.decodeBitmap(
        if (uri.startsWith("/")) ImageDecoder.createSource(java.io.File(uri))
        else ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
    ) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val imgLong = max(info.size.width, info.size.height)
        var sample = 1
        while (imgLong / (sample * 2) >= longSide) sample *= 2
        decoder.setTargetSampleSize(sample)
    }
}
