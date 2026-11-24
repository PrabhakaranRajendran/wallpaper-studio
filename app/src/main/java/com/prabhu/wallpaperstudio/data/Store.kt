package com.prabhu.wallpaperstudio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

object Keys {
    const val PAUSED = "paused"
    const val THEME = "theme"                 // system | light | dark
    const val DYNAMIC = "dynamic_color"
    const val AUTO_UPDATE = "auto_update"
    const val LAST_UPDATE_CHECK = "last_update_check"

    const val FOLDERS = "folders"             // JSON array of folder (tree) URIs
    const val CURRENT_FOLDER = "current_folder"
    const val SELECTED = "selected_photos"    // JSON array of photo URIs
    const val INTERVAL_MIN = "interval_min"
    const val ON_UNLOCK = "change_on_unlock"
    const val ORDER = "order"                 // shuffle | sequence
    const val TRANSITION = "transition"       // fade | slide | zoom | none
    const val FIT = "fit"                     // fill | fit | blur
    const val CROPS = "crops"                 // JSON object: photo URI -> Crop
    const val PHOTO_INDEX = "photo_index"
    const val LAST_CHANGE = "last_change"
    const val CHANGE_NOW = "change_now"

    const val GYRO = "gyro_enabled"
    const val GYRO_INTENSITY = "gyro_intensity" // 0..1
    const val GYRO_SMOOTH = "gyro_smooth"       // 0..1
    const val GYRO_STYLE = "gyro_style"         // tilt | shift | zoom
    const val RECENTER = "recenter"

    const val VIDEO_PATH = "video_path"
    const val VIDEO_SOUND = "video_sound"

    const val PALETTE = "palette"             // aqua | ocean | mint | sunset | mono | you
    const val PHOTO_INDEX_LOCK = "photo_index_lock"
    const val FOLDER_TARGETS = "folder_targets" // JSON object: folder URI -> home | lock | both

    const val BAT_3D_SAVER = "bat_3d_saver"
    const val BAT_3D_LOW = "bat_3d_low"
    const val BAT_VIDEO_SAVER = "bat_video_saver"

    // Written by the wallpapers themselves while they run.
    const val ACTIVE_PHOTO_HOME = "active_photo_home"
    const val ACTIVE_PHOTO_LOCK = "active_photo_lock"
    const val ACTIVE_VIDEO_HOME = "active_video_home"
    const val ACTIVE_VIDEO_LOCK = "active_video_lock"
    const val ENGINE_LOG = "engine_log"       // last few things the wallpaper did
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

    // Defaults in one place.
    fun intervalMin() = int(Keys.INTERVAL_MIN, 30)
    fun transition() = string(Keys.TRANSITION, "fade")
    fun fit() = string(Keys.FIT, "fill")
    fun order() = string(Keys.ORDER, "shuffle")
    fun gyroOn() = bool(Keys.GYRO, true)
    fun gyroIntensity() = float(Keys.GYRO_INTENSITY, 0.55f)
    fun gyroSmooth() = float(Keys.GYRO_SMOOTH, 0.75f)
    fun gyroStyle() = string(Keys.GYRO_STYLE, "tilt")
    fun paused() = bool(Keys.PAUSED, false)
    fun palette() = string(Keys.PALETTE, "aqua")

    fun folderTargets(): Map<String, String> {
        val raw = prefs.getString(Keys.FOLDER_TARGETS, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = HashMap<String, String>()
            o.keys().forEach { k -> out[k] = o.getString(k) }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setFolderTarget(folder: String, target: String) {
        val o = JSONObject()
        folderTargets().forEach { (k, v) -> o.put(k, v) }
        o.put(folder, target)
        edit { putString(Keys.FOLDER_TARGETS, o.toString()) }
    }

    fun targetOf(folder: String): String = folderTargets()[folder] ?: "both"

    /** The folder a selected photo came from, if it is still in the list. */
    fun folderOf(photo: String, folders: List<String> = stringList(Keys.FOLDERS)): String? =
        folders.firstOrNull { photo.startsWith("$it/document/") }
            ?: folders.firstOrNull { PhotoRepo.sameFolder(it, photo) }

    /** Selected photos for "home" or "lock", based on each folder's setting. */
    fun photosFor(target: String): List<String> {
        val selected = stringList(Keys.SELECTED)
        val folders = stringList(Keys.FOLDERS)
        val targets = folderTargets()
        // Only photos from folders that are still added (others can't be opened).
        val valid = selected.mapNotNull { uri -> folderOf(uri, folders)?.let { uri to it } }
        val out = valid.filter { (_, f) ->
            val t = targets[f] ?: "both"
            t == "both" || t == target
        }.map { it.first }
        return out.ifEmpty { valid.map { it.first } }
    }

    /** Adds a line to the wallpaper's activity log (shown in Settings > Wallpaper check). */
    fun log(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val lines = string(Keys.ENGINE_LOG, "").lines().filter { it.isNotBlank() } + "$time  $message"
        edit { putString(Keys.ENGINE_LOG, lines.takeLast(6).joinToString("\n")) }
    }

    fun indexKey(target: String) = if (target == "lock") Keys.PHOTO_INDEX_LOCK else Keys.PHOTO_INDEX

    fun currentPhoto(target: String): String? {
        val list = photosFor(target)
        if (list.isEmpty()) return null
        return list[int(indexKey(target), 0).mod(list.size)]
    }
}
