package com.prabhu.wallpaperstudio.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

data class PhotoItem(val uri: String, val name: String, val modified: Long)

object PhotoRepo {

    fun folderName(treeUri: String): String = try {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
        docId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Phone storage" }
    } catch (e: Exception) {
        "Folder"
    }

    /** Images directly inside the chosen folder, newest first. */
    fun listImages(context: Context, treeUri: String): List<PhotoItem> {
        val tree = Uri.parse(treeUri)
        val out = ArrayList<PhotoItem>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(2) ?: continue
                    if (!mime.startsWith("image/")) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    out += PhotoItem(uri.toString(), c.getString(1) ?: "", c.getLong(3))
                }
            }
        } catch (e: Exception) {
            // Folder removed or access revoked: show it as empty.
        }
        return out.sortedByDescending { it.modified }
    }

    /** True when the app may create files in this folder. */
    fun canWrite(context: Context, folder: String): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri.toString() == folder && it.isWritePermission }

    /**
     * Copies photos picked from Gallery / Google Photos into the folder.
     * Returns the new photos' URIs.
     */
    fun copyInto(context: Context, folder: String, sources: List<Uri>, onProgress: (Int) -> Unit): List<String> {
        val cr = context.contentResolver
        val tree = Uri.parse(folder)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<String>()
        sources.forEachIndexed { i, src ->
            try {
                var name = "wallpaper_${System.currentTimeMillis()}_$i.jpg"
                cr.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { if (it.isNotBlank()) name = it }
                }
                val mime = cr.getType(src) ?: "image/jpeg"
                val dest = DocumentsContract.createDocument(cr, parent, mime, name)
                if (dest != null) {
                    cr.openInputStream(src)?.use { input ->
                        cr.openOutputStream(dest)?.use { output -> input.copyTo(output) }
                    }
                    out += dest.toString()
                }
            } catch (e: Exception) {
                // Skip a photo that can't be read or written.
            }
            onProgress(i + 1)
        }
        return out
    }
}
