package com.prabhu.wallpaperstudio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

/** The two places a picture can go. */
object Target {
    const val HOME = "home"
    const val LOCK = "lock"
}

object Keys {
    const val PAUSED = "paused"
    const val THEME = "theme"                 // system | light | dark
    const val PALETTE = "palette"             // see Palettes, or "you"
    const val HAPTICS = "haptics"
    const val PRESS_3D = "press_3d"
    const val AUTO_UPDATE = "auto_update"
    const val LAST_UPDATE_CHECK = "last_update_check"
    const val MIGRATED_V2 = "migrated_v2"

    // Per target (prefix "home_" or "lock_")
    fun folders(t: String) = "${t}_folders"   // linked phone folders (JSON list of tree URIs)
    fun hidden(t: String) = "${t}_hidden"     // photos left out of the rotation (JSON list)
    fun interval(t: String) = "${t}_interval" // minutes; lock: 0 = every time the screen turns off
    fun order(t: String) = "${t}_order"       // shuffle | sequence
    fun fit(t: String) = "${t}_fit"           // fill | fit | blur
    fun index(t: String) = "${t}_index"
    fun last(t: String) = "${t}_last"         // when it last changed
    fun change(t: String) = "${t}_change_now"

    const val HOME_SOURCE = "home_source"     // photos | video
    const val HOME_TRANSITION = "home_transition" // fade | slide | zoom | none
    const val HOME_ON_UNLOCK = "home_on_unlock"
    const val LOCK_MODE = "lock_mode"         // slideshow | same | off

    const val CROPS = "crops"                 // JSON object: photo -> Crop ("video" for the video)

    const val GYRO = "gyro_enabled"
    const val GYRO_INTENSITY = "gyro_intensity"
    const val GYRO_SMOOTH = "gyro_smooth"
    const val GYRO_STYLE = "gyro_style"       // tilt | shift | zoom
    const val RECENTER = "recenter"

    const val VIDEO_PATH = "video_path"
    const val VIDEO_SOUND = "video_sound"
    const val VIDEO_FIT = "video_fit"         // fill | fit

    const val WIDGETS = "widgets"             // JSON list of WidgetItem
    const val WIDGET_SHADOW = "widget_shadow"
    const val WEATHER_CITY = "weather_city"
    const val WEATHER_LAT = "weather_lat"
    const val WEATHER_LON = "weather_lon"
    const val WEATHER_TEXT = "weather_text"
    const val WEATHER_TIME = "weather_time"

    const val BAT_3D_SAVER = "bat_3d_saver"
    const val BAT_3D_LOW = "bat_3d_low"
    const val BAT_VIDEO_SAVER = "bat_video_saver"

    // Written by the wallpapers themselves while they run.
    const val ACTIVE_PHOTO_HOME = "active_photo_home"
    const val ACTIVE_PHOTO_LOCK = "active_photo_lock"
    const val ACTIVE_VIDEO_HOME = "active_video_home"
    const val ACTIVE_VIDEO_LOCK = "active_video_lock"
    const val LOCK_LAST_PHOTO = "lock_last_photo"
    const val ENGINE_LOG = "engine_log"

    // Old (1.x) keys, read once for migration
    const val OLD_FOLDERS = "folders"
    const val OLD_TARGETS = "folder_targets"
    const val OLD_INTERVAL = "interval_min"
    const val OLD_ORDER = "order"
    const val OLD_TRANSITION = "transition"
    const val OLD_FIT = "fit"
    const val OLD_ON_UNLOCK = "change_on_unlock"
}

/** Per-photo crop. x / y are offsets as a fraction of the screen size. */
data class Crop(
    val zoom: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Int = 0,
    val flip: Boolean = false,
    val brightness: Float = 0f,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("z", zoom.toDouble())
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("r", rotation)
        .put("f", flip)
        .put("b", brightness.toDouble())

    companion object {
        fun fromJson(o: JSONObject) = Crop(
            zoom = o.optDouble("z", 1.0).toFloat(),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            rotation = o.optInt("r", 0),
            flip = o.optBoolean("f", false),
            brightness = o.optDouble("b", 0.0).toFloat(),
        )
    }
}

/**
 * All settings live in one SharedPreferences file, shared by the app screens
 * and the wallpaper services (same process). [tick] changes whenever any
 * setting changes, so Compose screens that read it recompose.
 */
object Store {
    lateinit var prefs: SharedPreferences
        private set

    val tick = mutableIntStateOf(0)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        tick.intValue = tick.intValue + 1
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("studio", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        migrate()
    }

    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    fun string(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun int(key: String, default: Int) = prefs.getInt(key, default)
    fun float(key: String, default: Float) = prefs.getFloat(key, default)
    fun long(key: String, default: Long) = prefs.getLong(key, default)

    fun stringList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun putStringList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        edit { putString(key, arr.toString()) }
    }

    fun crops(): Map<String, Crop> {
        val raw = prefs.getString(Keys.CROPS, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = HashMap<String, Crop>()
            o.keys().forEach { k -> out[k] = Crop.fromJson(o.getJSONObject(k)) }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun putCrops(map: Map<String, Crop>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v.toJson()) }
        edit { putString(Keys.CROPS, o.toString()) }
    }

    fun putCrop(key: String, crop: Crop?) {
        val m = crops().toMutableMap()
        if (crop == null) m.remove(key) else m[key] = crop
        putCrops(m)
    }

    // ---- defaults in one place ----
    fun interval(t: String) = int(Keys.interval(t), if (t == Target.LOCK) 0 else 30)
    fun order(t: String) = string(Keys.order(t), "shuffle")
    fun fit(t: String) = string(Keys.fit(t), "fill")
    fun homeSource() = string(Keys.HOME_SOURCE, "photos")
    fun transition() = string(Keys.HOME_TRANSITION, "fade")
    fun lockMode() = string(Keys.LOCK_MODE, "off")
    fun gyroOn() = bool(Keys.GYRO, true)
    fun gyroIntensity() = float(Keys.GYRO_INTENSITY, 0.55f)
    fun gyroSmooth() = float(Keys.GYRO_SMOOTH, 0.75f)
    fun gyroStyle() = string(Keys.GYRO_STYLE, "tilt")
    fun paused() = bool(Keys.PAUSED, false)
    fun palette() = string(Keys.PALETTE, "aqua")
    fun haptics() = bool(Keys.HAPTICS, true)
    fun press3d() = bool(Keys.PRESS_3D, true)

    /** Adds a line to the wallpaper's activity log (Settings > Wallpaper check). */
    fun log(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val lines = string(Keys.ENGINE_LOG, "").lines().filter { it.isNotBlank() } + "$time  $message"
        edit { putString(Keys.ENGINE_LOG, lines.takeLast(8).joinToString("\n")) }
    }

    /** Moves 1.x settings to the 2.0 layout, once. */
    private fun migrate() {
        if (prefs.getBoolean(Keys.MIGRATED_V2, false)) return
        val e = prefs.edit()
        val oldFolders = stringList(Keys.OLD_FOLDERS)
        if (oldFolders.isNotEmpty()) {
            val targets = try {
                JSONObject(prefs.getString(Keys.OLD_TARGETS, null) ?: "{}")
            } catch (ex: Exception) {
                JSONObject()
            }
            val home = JSONArray()
            val lock = JSONArray()
            oldFolders.forEach { f ->
                when (targets.optString(f, "both")) {
                    "home" -> home.put(f)
                    "lock" -> lock.put(f)
                    else -> {
                        home.put(f)
                        lock.put(f)
                    }
                }
            }
            e.putString(Keys.folders(Target.HOME), home.toString())
            e.putString(Keys.folders(Target.LOCK), lock.toString())
        }
        if (prefs.contains(Keys.OLD_INTERVAL)) e.putInt(Keys.interval(Target.HOME), prefs.getInt(Keys.OLD_INTERVAL, 30))
        prefs.getString(Keys.OLD_ORDER, null)?.let { e.putString(Keys.order(Target.HOME), it) }
        prefs.getString(Keys.OLD_TRANSITION, null)?.let { e.putString(Keys.HOME_TRANSITION, it) }
        prefs.getString(Keys.OLD_FIT, null)?.let { e.putString(Keys.fit(Target.HOME), it) }
        if (prefs.contains(Keys.OLD_ON_UNLOCK)) e.putBoolean(Keys.HOME_ON_UNLOCK, prefs.getBoolean(Keys.OLD_ON_UNLOCK, false))
        e.putBoolean(Keys.MIGRATED_V2, true)
        e.apply()
    }
}
