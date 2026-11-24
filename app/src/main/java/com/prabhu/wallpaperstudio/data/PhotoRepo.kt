package com.prabhu.wallpaperstudio.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

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
}
