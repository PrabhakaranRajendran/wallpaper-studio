package com.prabhu.wallpaperstudio.ui

import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.combinedClickable
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prabhu.wallpaperstudio.data.Albums
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.PhotoRepo
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos for the Home screen or the Lock screen: the app's own album
 * (filled from Gallery / Google Photos) plus any linked phone folders.
 */
@Composable
fun AlbumSection(target: String, onCrop: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val confirmTap = rememberConfirmTap()
    Store.tick.intValue
    var reload by remember { mutableIntStateOf(0) }
    var photos by remember { mutableStateOf<List<String>?>(null) }
    var copying by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedPhoto by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmRemoveMany by remember { mutableStateOf(false) }
    val folders = Store.stringList(Keys.folders(target))
    val hidden = Store.stringList(Keys.hidden(target)).toHashSet()
    val other = if (target == Target.HOME) Target.LOCK else Target.HOME
    val albumLabel = if (target == Target.HOME) "Home screen" else "Lock screen"

    LaunchedEffect(target, reload, folders.size) {
        photos = withContext(Dispatchers.IO) {
            PhotoLists.invalidate()
            Albums.allPhotos(context, target)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            copying = 0 to uris.size
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    Albums.addPhotos(context, target, uris) { done -> scope.launch { copying = done to uris.size } }
                }
                copying = null
                reload++
                confirmTap()
                snack(if (n == uris.size) "Added $n photos to $albumLabel" else "Added $n of ${uris.size} photos")
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val s = uri.toString()
            if (folders.none { PhotoRepo.sameFolder(it, s) }) {
                Store.putStringList(Keys.folders(target), folders + s)
                snack("Linked folder ${PhotoRepo.folderName(s)}")
            }
            reload++
        }
    }

    if (confirmRemoveMany) {
        ConfirmDialog(
            title = "Remove ${selected.size} photo${if (selected.size == 1) "" else "s"}?",
            message = "They are deleted from the Wallpaper Studio album. Originals in your Gallery or Google Photos aren't touched. To just stop showing them, use Hide instead.",
            confirm = "Remove",
            onConfirm = {
                confirmRemoveMany = false
                val n = selected.size
                Albums.removeMany(context, target, selected)
                selected = emptySet()
                reload++
                snack("$n photo${if (n == 1) "" else "s"} removed")
            },
            onDismiss = { confirmRemoveMany = false },
        )
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                val list = photos
                val active = list?.count { it !in hidden } ?: 0
                Text(
                    if (list == null) "Loading…" else "$active photos in rotation",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (list != null && list.size != active) {
                    Text("${list.size - active} hidden", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if ((photos ?: emptyList()).isNotEmpty()) {
                TextButton(onClick = {
                    selecting = !selecting
                    selected = emptySet()
                }) { Text(if (selecting) "Done" else "Select") }
            }
        }

        PressButton(
            "Add photos from Gallery / Google Photos",
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = Icons.Rounded.AddPhotoAlternate,
            style = BtnStyle.Brand,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )

        if (selecting) {
            val all = photos ?: emptyList()
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selected.isEmpty()) "Tap photos to select them" else "${selected.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { selected = all.toSet() }) { Text("All") }
                    TextButton(onClick = { selected = emptySet() }) { Text("None") }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PressButton("Hide", onClick = {
                        if (selected.isEmpty()) {
                            snack("Select photos first")
                        } else {
                            Albums.setHiddenMany(target, selected, true)
                            snack("${selected.size} photo${if (selected.size == 1) "" else "s"} hidden")
                            selected = emptySet()
                        }
                    }, icon = Icons.Rounded.VisibilityOff, style = BtnStyle.Tonal, height = 44, modifier = Modifier.weight(1f))
                    PressButton("Show", onClick = {
                        if (selected.isEmpty()) {
                            snack("Select photos first")
                        } else {
                            Albums.setHiddenMany(target, selected, false)
                            snack("${selected.size} photo${if (selected.size == 1) "" else "s"} back in the rotation")
                            selected = emptySet()
                        }
                    }, icon = Icons.Rounded.Visibility, style = BtnStyle.Tonal, height = 44, modifier = Modifier.weight(1f))
                    PressButton("Remove", onClick = {
                        if (selected.isEmpty()) snack("Select photos first") else confirmRemoveMany = true
                    }, icon = Icons.Rounded.Delete, style = BtnStyle.Outline, height = 44, modifier = Modifier.weight(1f))
                }
            }
        }

        val c = copying
        if (c != null) {
            Text("Copying ${c.first} of ${c.second}…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            LinearProgressIndicator(progress = { if (c.second == 0) 0f else c.first / c.second.toFloat() }, modifier = Modifier.fillMaxWidth())
        }

        val list = photos ?: emptyList()
        if (photos != null && list.isEmpty()) {
            Text(
                "No photos yet. Tap the button above to pick some. They're copied into the \"${Albums.albumName(target)}\" album, " +
                    "so you never have to copy files by hand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (list.size == 1 && target == Target.HOME) {
            Text(
                "Add at least 2 photos so the wallpaper has something to change to.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // Photo grid (3 across). Tap a photo for options.
        val shown = if (showAll) list else list.take(30)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { p ->
                        val isHidden = p in hidden
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .combinedClickable(
                                    onClick = {
                                        if (selecting) {
                                            selected = if (p in selected) selected - p else selected + p
                                        } else {
                                            selectedPhoto = p
                                        }
                                    },
                                    onLongClick = {
                                        selecting = true
                                        selected = selected + p
                                    },
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(photoModel(p)).size(360).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(if (isHidden) 0.35f else 1f),
                            )
                            if (isHidden) {
                                Text(
                                    "Hidden",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xAA000000))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            if (selecting) {
                                val on = p in selected
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(26.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (on) MaterialTheme.colorScheme.primary else Color(0x66000000))
                                        .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (Store.crops().containsKey(p)) {
                                Icon(
                                    Icons.Rounded.Crop, contentDescription = "Cropped", tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp),
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (!showAll && list.size > 30) {
            TextButton(onClick = { showAll = true }) { Text("Show all ${list.size} photos") }
        }
        if (list.isNotEmpty()) {
            Text(
                "Tap a photo for options, or press Select (or hold a photo) to hide or remove several at once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Linked phone folders
        Text(
            "Phone folders",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            "Every photo in a linked folder is used, and new photos you put there are picked up automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            folders.forEach { f ->
                InputChip(
                    selected = false,
                    onClick = {
                        Store.putStringList(Keys.folders(target), folders - f)
                        try {
                            context.contentResolver.releasePersistableUriPermission(Uri.parse(f), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {
                        }
                        reload++
                        snack("Unlinked ${PhotoRepo.folderName(f)}")
                    },
                    label = { Text(PhotoRepo.folderName(f)) },
                    leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Rounded.Close, "Unlink folder", Modifier.size(18.dp)) },
                )
            }
        }
        PressButton(
            "Link a phone folder",
            onClick = { folderPicker.launch(null) },
            icon = Icons.Rounded.FolderOpen,
            style = BtnStyle.Outline,
            height = 46,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Text(
            "Album location: Files › Android › media › ${context.packageName} › ${Albums.albumName(target)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    val sel = selectedPhoto
    if (sel != null) {
        PhotoOptionsSheet(
            photo = sel,
            hidden = sel in hidden,
            isAlbumPhoto = sel.startsWith("/"),
            otherLabel = if (other == Target.HOME) "Home screen" else "Lock screen",
            onDismiss = { selectedPhoto = null },
            onCrop = {
                selectedPhoto = null
                onCrop(sel)
            },
            onToggleHidden = {
                Albums.setHidden(target, sel, sel !in hidden)
                snack(if (sel in hidden) "Photo is back in the rotation" else "Photo hidden from the rotation")
                selectedPhoto = null
            },
            onCopyToOther = {
                selectedPhoto = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            val src = File(sel)
                            if (src.exists()) {
                                src.copyTo(File(Albums.dir(context, other), "${System.currentTimeMillis()}_${src.name.substringAfter('_')}"))
                                PhotoLists.invalidate()
                                true
                            } else {
                                Albums.addPhotos(context, other, listOf(Uri.parse(sel))) {} > 0
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    snack(if (ok) "Copied to the ${if (other == Target.HOME) "Home screen" else "Lock screen"} album" else "Couldn't copy that photo")
                }
            },
            onRemove = {
                Albums.remove(context, target, sel)
                selectedPhoto = null
                reload++
                snack(if (sel.startsWith("/")) "Photo removed from the album" else "Photo hidden")
            },
        )
    }
}

@Composable
private fun PhotoOptionsSheet(
    photo: String,
    hidden: Boolean,
    isAlbumPhoto: Boolean,
    otherLabel: String,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onToggleHidden: () -> Unit,
    onCopyToOther: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val tap = rememberTap()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            AsyncImage(
                model = photoModel(photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(
                headlineContent = { Text("Crop & zoom") },
                supportingContent = { Text("Choose exactly which part shows") },
                leadingContent = { Icon(Icons.Rounded.Crop, null) },
                colors = colors,
                modifier = Modifier.clickable { tap(); onCrop() },
            )
            ListItem(
                headlineContent = { Text(if (hidden) "Show in rotation" else "Hide from rotation") },
                supportingContent = { Text(if (hidden) "Use this photo again" else "Keep it, but don't show it") },
                leadingContent = { Icon(if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null) },
                colors = colors,
                modifier = Modifier.clickable { tap(); onToggleHidden() },
            )
            ListItem(
                headlineContent = { Text("Copy to $otherLabel album") },
                leadingContent = { Icon(Icons.Rounded.ContentCopy, null) },
                colors = colors,
                modifier = Modifier.clickable { tap(); onCopyToOther() },
            )
            if (isAlbumPhoto) {
                ListItem(
                    headlineContent = { Text("Remove from album", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Deletes this copy. The original in your Gallery stays.") },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    colors = colors,
                    modifier = Modifier.clickable { tap(); confirmRemove = true },
                )
            }
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove this photo?",
            message = "It's deleted from the Wallpaper Studio album. The original in your Gallery or Google Photos isn't touched.",
            confirm = "Remove",
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}
