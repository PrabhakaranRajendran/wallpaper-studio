package com.prabhu.wallpaperstudio.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * The app's own photo albums, one for the Home screen and one for the Lock screen.
 * They live in Android/media/com.prabhu.wallpaperstudio/ so they show up in Files
 * and Gallery, and need no permission to read or write.
 *
 * A photo is identified by a string: an absolute file path for album photos,
 * or a content:// URI for photos in a linked phone folder.
 */
object Albums {

    private const val HOME_DIR = "Wallpaper"
    private const val LOCK_DIR = "Lock screen"

    @Suppress("DEPRECATION")
    fun dir(context: Context, target: String): File {
        val base = context.externalMediaDirs.firstOrNull { it != null } ?: context.filesDir
        val d = File(base, if (target == Target.LOCK) LOCK_DIR else HOME_DIR)
        d.mkdirs()
        return d
    }

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

    fun albumPhotos(context: Context, target: String): List<String> =
        (dir(context, target).listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in imageExt && it.length() > 0 }
            .sortedBy { it.name }
            .map { it.absolutePath }

    /** Every photo for a target: album + linked folders, minus hidden ones. */
    fun allPhotos(context: Context, target: String): List<String> {
        val linked = Store.stringList(Keys.folders(target)).flatMap { f ->
            PhotoRepo.listImages(context, f).map { it.uri }
        }
        return albumPhotos(context, target) + linked
    }

    fun activePhotos(context: Context, target: String): List<String> {
        val hidden = Store.stringList(Keys.hidden(target)).toHashSet()
        return allPhotos(context, target).filter { it !in hidden }
    }

    /** Copies picked photos (Gallery / Google Photos) into the album. Returns how many were copied. */
    fun addPhotos(context: Context, target: String, sources: List<Uri>, onProgress: (Int) -> Unit): Int {
        val dir = dir(context, target)
        val cr = context.contentResolver
        val copied = ArrayList<String>()
        sources.forEachIndexed { i, src ->
            try {
                var name: String? = null
                cr.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0)
                }
                val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in imageExt } ?: "jpg"
                // Time-stamped names keep the album in the order photos were added.
                val file = File(dir, "${System.currentTimeMillis()}_$i.$ext")
                cr.openInputStream(src)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                if (file.length() > 0) copied += file.absolutePath else file.delete()
            } catch (e: Exception) {
                // Skip a photo that can't be read.
            }
            onProgress(i + 1)
        }
        if (copied.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, copied.toTypedArray(), null, null)
        }
        PhotoLists.invalidate()
        return copied.size
    }

    /** Deletes an album photo, or hides a linked-folder photo. */
    fun remove(context: Context, target: String, photo: String) {
        if (photo.startsWith("/")) {
            File(photo).delete()
            MediaScannerConnection.scanFile(context, arrayOf(photo), null, null)
        } else {
            Store.putStringList(Keys.hidden(target), (Store.stringList(Keys.hidden(target)) + photo).distinct())
        }
        Store.putCrop(photo, null)
        PhotoLists.invalidate()
    }

    fun setHidden(target: String, photo: String, hidden: Boolean) {
        val list = Store.stringList(Keys.hidden(target))
        Store.putStringList(Keys.hidden(target), if (hidden) (list + photo).distinct() else list - photo)
        PhotoLists.invalidate()
    }

    /** Hides or shows many photos at once. */
    fun setHiddenMany(target: String, photos: Collection<String>, hidden: Boolean) {
        val list = Store.stringList(Keys.hidden(target))
        Store.putStringList(Keys.hidden(target), if (hidden) (list + photos).distinct() else list - photos.toSet())
        PhotoLists.invalidate()
    }

    fun removeMany(context: Context, target: String, photos: Collection<String>) {
        photos.forEach { remove(context, target, it) }
    }

    /** Folder name to show for a photo's source. */
    fun albumName(target: String) = if (target == Target.LOCK) LOCK_DIR else HOME_DIR
}

/** Short-lived cache of the photo lists, so the wallpaper doesn't re-scan folders all the time. */
object PhotoLists {
    private class Entry(val time: Long, val dirStamp: Long, val list: List<String>)
    private val cache = HashMap<String, Entry>()

    @Synchronized
    fun get(context: Context, target: String, maxAgeMs: Long = 60_000L): List<String> {
        val now = System.currentTimeMillis()
        val dir = Albums.dir(context, target)
        // The folder's time stamp changes whenever a photo is added or removed.
        val stamp = dir.lastModified() + (dir.list()?.size ?: 0)
        cache[target]?.let { e -> if (now - e.time < maxAgeMs && e.dirStamp == stamp) return e.list }
        val list = Albums.activePhotos(context, target)
        cache[target] = Entry(now, stamp, list)
        return list
    }

    @Synchronized
    fun invalidate() {
        cache.clear()
    }

    /** The photo a target is currently showing (by its saved index). */
    fun current(context: Context, target: String): String? {
        if (target == Target.LOCK) {
            Store.prefs.getString(Keys.LOCK_LAST_PHOTO, null)?.let { return it }
        }
        val list = get(context, target)
        if (list.isEmpty()) return null
        return list[Store.int(Keys.index(target), 0).mod(list.size)]
    }
}
