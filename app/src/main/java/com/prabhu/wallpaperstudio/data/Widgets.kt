package com.prabhu.wallpaperstudio.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One item drawn on the Home screen wallpaper. */
data class WidgetItem(
    val type: String,          // clock | date | battery | weather | text | event
    val on: Boolean,
    val font: String = "light",
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 1f,      // 0.5 .. 2
    val y: Float = 0.2f,       // 0 = top, 1 = bottom
    val align: String = "center", // left | center | right
    val style: String = "",    // clock: big | stacked; date: short | long | numeric
    val h24: Boolean = false,
    val text: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type).put("on", on).put("font", font).put("color", color)
        .put("size", size.toDouble()).put("y", y.toDouble()).put("align", align)
        .put("style", style).put("h24", h24).put("text", text)

    companion object {
        fun fromJson(o: JSONObject) = WidgetItem(
            type = o.optString("type"),
            on = o.optBoolean("on", false),
            font = o.optString("font", "light"),
            color = o.optInt("color", 0xFFFFFFFF.toInt()),
            size = o.optDouble("size", 1.0).toFloat(),
            y = o.optDouble("y", 0.2).toFloat(),
            align = o.optString("align", "center"),
            style = o.optString("style", ""),
            h24 = o.optBoolean("h24", false),
            text = o.optString("text", ""),
        )
    }
}

object Widgets {
    val TYPES = listOf("clock", "date", "battery", "weather", "text", "event")

    fun label(type: String) = when (type) {
        "clock" -> "Clock"
        "date" -> "Date"
        "battery" -> "Battery"
        "weather" -> "Weather"
        "text" -> "Your text"
        "event" -> "Next calendar event"
        else -> type
    }

    fun defaults(): List<WidgetItem> = listOf(
        WidgetItem("clock", on = false, font = "thin", size = 1f, y = 0.16f, style = "big"),
        WidgetItem("date", on = false, font = "regular", size = 1f, y = 0.27f, style = "short"),
        WidgetItem("battery", on = false, font = "medium", size = 1f, y = 0.92f),
        WidgetItem("weather", on = false, font = "regular", size = 1f, y = 0.31f),
        WidgetItem("text", on = false, font = "script", size = 1.2f, y = 0.80f, text = "Have a great day"),
        WidgetItem("event", on = false, font = "regular", size = 1f, y = 0.35f),
    )

    fun load(): List<WidgetItem> {
        val raw = Store.prefs.getString(Keys.WIDGETS, null) ?: return defaults()
        return try {
            val arr = JSONArray(raw)
            val saved = List(arr.length()) { WidgetItem.fromJson(arr.getJSONObject(it)) }.associateBy { it.type }
            defaults().map { saved[it.type] ?: it }
        } catch (e: Exception) {
            defaults()
        }
    }

    fun save(items: List<WidgetItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        Store.edit { putString(Keys.WIDGETS, arr.toString()) }
    }

    fun update(item: WidgetItem) = save(load().map { if (it.type == item.type) item else it })

    fun anyOn() = load().any { it.on }

    val FONTS = listOf(
        "thin" to "Thin", "light" to "Light", "regular" to "Regular", "medium" to "Medium",
        "black" to "Heavy", "condensed" to "Narrow", "serif" to "Serif", "mono" to "Mono", "script" to "Script",
    )

    val COLORS = listOf(
        0xFFFFFFFF.toInt(), 0xFF0D1B24.toInt(), 0xFF2AAAE0.toInt(), 0xFFFFB454.toInt(),
        0xFFF2784B.toInt(), 0xFF2BB89A.toInt(), 0xFFE0789A.toInt(), 0xFFB79CFF.toInt(),
    )
}

/** Live values the widgets show. */
data class WidgetData(
    val now: Long,
    val battery: Int,
    val charging: Boolean,
    val weather: String?,
    val event: String?,
)

object WidgetDataSource {
    fun read(context: Context): WidgetData {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return WidgetData(
            now = System.currentTimeMillis(),
            battery = pct,
            charging = plugged,
            weather = Store.prefs.getString(Keys.WEATHER_TEXT, null),
            event = CalendarSource.cached,
        )
    }
}

/** Weather from Open-Meteo (free, no account). Only the city name is sent. */
object Weather {
    fun isStale() = System.currentTimeMillis() - Store.long(Keys.WEATHER_TIME, 0L) > 60 * 60_000L

    /** Looks up a city; returns a short error message, or null when it worked. Call off the main thread. */
    fun setCity(name: String): String? {
        return try {
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name.trim(), "UTF-8"))
            val json = JSONObject(get(url))
            val r = json.optJSONArray("results")?.optJSONObject(0) ?: return "City not found"
            Store.edit {
                putString(Keys.WEATHER_CITY, r.optString("name", name))
                putFloat(Keys.WEATHER_LAT, r.optDouble("latitude").toFloat())
                putFloat(Keys.WEATHER_LON, r.optDouble("longitude").toFloat())
                putLong(Keys.WEATHER_TIME, 0L)
            }
            refresh()
            null
        } catch (e: Exception) {
            "Couldn't reach the weather service"
        }
    }

    /** Fetches the current weather. Call off the main thread. */
    fun refresh() {
        val city = Store.prefs.getString(Keys.WEATHER_CITY, null) ?: return
        val lat = Store.float(Keys.WEATHER_LAT, 0f)
        val lon = Store.float(Keys.WEATHER_LON, 0f)
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
            val cur = JSONObject(get(url)).getJSONObject("current")
            val temp = Math.round(cur.optDouble("temperature_2m"))
            val text = "$temp° ${describe(cur.optInt("weather_code"))} · $city"
            Store.edit {
                putString(Keys.WEATHER_TEXT, text)
                putLong(Keys.WEATHER_TIME, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            // Keep the last known weather.
        }
    }

    private fun get(url: URL): String {
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> ""
    }
}

/** The next event in the next 24 hours from the phone's calendar. */
object CalendarSource {
    @Volatile
    var cached: String? = null
        private set
    private var lastRead = 0L

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Call off the main thread. */
    fun refresh(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRead < 5 * 60_000L) return
        lastRead = now
        if (!hasPermission(context)) {
            cached = null
            return
        }
        cached = try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString()).appendPath((now + 24 * 3_600_000L).toString()).build()
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                var found: String? = null
                while (c.moveToNext() && found == null) {
                    val title = c.getString(0) ?: continue
                    val begin = c.getLong(1)
                    val allDay = c.getInt(2) == 1
                    if (!allDay && begin < now) continue
                    val time = if (allDay) "Today" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(begin))
                    found = "$title · $time"
                }
                found
            }
        } catch (e: Exception) {
            null
        }
    }
}
