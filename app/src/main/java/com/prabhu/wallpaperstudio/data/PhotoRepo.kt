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

    data class CopyResult(val added: List<String>, val error: String?)

    /** Same folder, even if its address is written slightly differently. */
    fun sameFolder(a: String, b: String): Boolean {
        if (a == b) return true
        return try {
            val ua = Uri.parse(a)
            val ub = Uri.parse(b)
            ua.authority == ub.authority &&
                DocumentsContract.getTreeDocumentId(ua) == DocumentsContract.getTreeDocumentId(ub)
        } catch (e: Exception) {
            false
        }
    }

    /** True when the app may create files in this folder. */
    fun canWrite(context: Context, folder: String): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.isWritePermission && sameFolder(it.uri.toString(), folder) }

    /**
     * Copies photos picked from Gallery / Google Photos into the folder.
     * Returns the new photos' URIs.
     */
    fun copyInto(context: Context, folder: String, sources: List<Uri>, onProgress: (Int) -> Unit): CopyResult {
        val cr = context.contentResolver
        val tree = Uri.parse(folder)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<String>()
        var firstError: String? = null
        sources.forEachIndexed { i, src ->
            try {
                var name = "wallpaper_${System.currentTimeMillis()}_$i.jpg"
                cr.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { if (it.isNotBlank()) name = it }
                }
                val mime = cr.getType(src) ?: "image/jpeg"
                val dest = DocumentsContract.createDocument(cr, parent, mime, name)
                if (dest == null) {
                    if (firstError == null) firstError = "The folder didn't accept a new file"
                } else {
                    val input = cr.openInputStream(src) ?: throw IllegalStateException("Couldn't read the picked photo")
                    input.use { inp ->
                        val output = cr.openOutputStream(dest) ?: throw IllegalStateException("Couldn't write to the folder")
                        output.use { inp.copyTo(it) }
                    }
                    out += dest.toString()
                }
            } catch (e: Exception) {
                if (firstError == null) {
                    firstError = if (e is SecurityException) "No permission to write here" else (e.message ?: e.javaClass.simpleName)
                }
            }
            onProgress(i + 1)
        }
        return CopyResult(out, firstError)
    }
}
