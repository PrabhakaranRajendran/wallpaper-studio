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
