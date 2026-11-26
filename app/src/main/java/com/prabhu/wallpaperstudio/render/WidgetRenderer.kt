package com.prabhu.wallpaperstudio.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.prabhu.wallpaperstudio.data.WidgetData
import com.prabhu.wallpaperstudio.data.WidgetItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws the clock, date and other widgets on top of the wallpaper.
 * The same code draws the live wallpaper and the preview in the app,
 * so what you see in the editor is exactly what you get.
 */
object WidgetRenderer {

    private val typefaces = HashMap<String, Typeface>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    fun typeface(font: String): Typeface = typefaces.getOrPut(font) {
        when (font) {
            "thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            "script" -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    /** Lines of text a widget shows right now, or null to skip it. */
    fun textFor(item: WidgetItem, data: WidgetData, preview: Boolean): List<String>? {
        val now = Date(data.now)
        return when (item.type) {
            "clock" -> {
                val t = SimpleDateFormat(if (item.h24) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
                if (item.style == "stacked") t.split(":") else listOf(t)
            }
            "date" -> {
                val pattern = when (item.style) {
                    "long" -> "EEEE, d MMMM"
                    "numeric" -> "dd/MM/yyyy"
                    else -> "EEE, d MMM"
                }
                listOf(SimpleDateFormat(pattern, Locale.getDefault()).format(now))
            }
            "battery" -> if (data.battery < 0) null else listOf("Battery ${data.battery}%" + if (data.charging) " · charging" else "")
            "weather" -> data.weather?.let { listOf(it) } ?: if (preview) listOf("Set your city for weather") else null
            "text" -> item.text.lines().filter { it.isNotBlank() }.ifEmpty { null }
            "event" -> data.event?.let { listOf(it) } ?: if (preview) listOf("Your next event shows here") else null
            else -> null
        }
    }

    private fun baseSize(item: WidgetItem, w: Int): Float = w * item.size * when (item.type) {
        "clock" -> if (item.style == "stacked") 0.26f else 0.21f
        "date" -> 0.052f
        "text" -> 0.06f
        else -> 0.042f
    }

    fun draw(canvas: Canvas, w: Int, h: Int, items: List<WidgetItem>, data: WidgetData, shadow: Boolean, preview: Boolean = false) {
        for (item in items) {
            if (!item.on) continue
            val lines = textFor(item, data, preview) ?: continue
            val size = baseSize(item, w)
            paint.reset()
            paint.isAntiAlias = true
            paint.typeface = typeface(item.font)
            paint.textSize = size
            paint.color = item.color
            if (shadow) {
                val light = luminance(item.color) > 0.5f
                paint.setShadowLayer(size * 0.06f + 2f, 0f, size * 0.02f, if (light) 0x88000000.toInt() else 0x88FFFFFF.toInt())
            }
            val margin = w * 0.08f
            val x = when (item.align) {
                "left" -> {
                    paint.textAlign = Paint.Align.LEFT
                    margin
                }
                "right" -> {
                    paint.textAlign = Paint.Align.RIGHT
                    w - margin
                }
                else -> {
                    paint.textAlign = Paint.Align.CENTER
                    w / 2f
                }
            }
            val lineGap = size * if (item.type == "clock") 0.92f else 1.25f
            var y = item.y * h + size * 0.35f - (lines.size - 1) * lineGap / 2f
            for (line in lines) {
                canvas.drawText(line, x, y, paint)
                y += lineGap
            }
        }
    }

    private fun luminance(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
}
