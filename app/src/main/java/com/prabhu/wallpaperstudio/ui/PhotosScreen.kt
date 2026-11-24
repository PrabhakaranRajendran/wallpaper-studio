package com.prabhu.wallpaperstudio.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoItem
import com.prabhu.wallpaperstudio.data.PhotoRepo
import com.prabhu.wallpaperstudio.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotosScreen(selectedTab: Int, onTabChange: (Int) -> Unit, onCrop: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Photos", Modifier.padding(horizontal = 16.dp))
        TabRow(selectedTabIndex = selectedTab.coerceIn(0, 2)) {
            Tab(selected = selectedTab == 0, onClick = { onTabChange(0) }, text = { Text("Folders") })
            Tab(selected = selectedTab == 1, onClick = { onTabChange(1) }, text = { Text("Choose photos") })
            Tab(selected = selectedTab == 2, onClick = { onTabChange(2) }, text = { Text("Rotation") })
        }
        when (selectedTab) {
            0 -> FoldersTab(onOpenFolder = { onTabChange(1) })
            1 -> ChoosePhotos(onDone = { onTabChange(2) }, onCrop = onCrop)
            else -> RotationSettings(onChoose = { onTabChange(1) }, onCrop = onCrop)
        }
    }
}

/** Opens Android's folder chooser and remembers the folder (with permission to read and add photos). */
@Composable
private fun rememberFolderPicker(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
            }
            val s = uri.toString()
            val folders = Store.stringList(Keys.FOLDERS)
            val old = folders.firstOrNull { PhotoRepo.sameFolder(it, s) }
            when {
                old == null -> Store.putStringList(Keys.FOLDERS, folders + s)
                old != s -> {
                    // Same folder picked again: keep its place and its Home / Lock setting.
                    Store.putStringList(Keys.FOLDERS, folders.map { if (it == old) s else it })
                    Store.setFolderTarget(s, Store.targetOf(old))
                }
            }
            Store.edit { putString(Keys.CURRENT_FOLDER, s) }
        }
    }
    return { launcher.launch(null) }
}

// ---------------- Folders ----------------

@Composable
private fun FoldersTab(onOpenFolder: () -> Unit) {
    val context = LocalContext.current
    Store.tick.intValue
    val folders = Store.stringList(Keys.FOLDERS)
    val pickFolder = rememberFolderPicker()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Choose where each folder's photos appear.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 10.dp),
        )
        if (folders.isEmpty()) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add your first folder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick a folder for your wallpaper photos. You can make one for the Home screen, " +
                            "one for the Lock screen, or use one folder for both.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            folders.forEach { f ->
                FolderCard(
                    folder = f,
                    onOpen = {
                        Store.edit { putString(Keys.CURRENT_FOLDER, f) }
                        onOpenFolder()
                    },
                    onRemove = { removeFolder(context, f) },
                )
            }
        }
        OutlinedButton(
            onClick = pickFolder,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.Rounded.CreateNewFolder, null)
            Text("Add folder", Modifier.padding(start = 8.dp))
        }
        if (folders.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 12.dp, bottom = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryTile("HOME SCREEN", Store.photosFor("home").size, Modifier.weight(1f))
                SummaryTile("LOCK SCREEN", Store.photosFor("lock").size, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, count: Int, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            "$count photos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun FolderCard(folder: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var items by remember(folder) { mutableStateOf<List<PhotoItem>?>(null) }
    LaunchedEffect(folder) {
        items = withContext(Dispatchers.IO) { PhotoRepo.listImages(context, folder) }
    }
    var menu by remember { mutableStateOf(false) }
    val target = Store.targetOf(folder)

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    (items ?: emptyList()).take(3).forEachIndexed { i, it ->
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(it.uri).size(160).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(start = if (i == 0) 0.dp else 0.dp)
                                .offset(x = (-12 * i).dp)
                                .size(width = 36.dp, height = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                    if (items.isNullOrEmpty()) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(PhotoRepo.folderName(folder), style = MaterialTheme.typography.titleMedium)
                    Text(
                        items?.let { "${it.size} photos" } ?: "Loading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Folder options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Choose photos") }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Remove folder") }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf("home" to "Home", "lock" to "Lock", "both" to "Both")
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = target == value,
                        onClick = { Store.setFolderTarget(folder, value) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    ) { Text(label) }
                }
            }
        }
    }
}

// ---------------- Choose photos ----------------

@Composable
private fun ChoosePhotos(onDone: () -> Unit, onCrop: (String) -> Unit) {
    val context = LocalContext.current
    Store.tick.intValue
    val folders = Store.stringList(Keys.FOLDERS)
    val current = Store.prefs.getString(Keys.CURRENT_FOLDER, null)?.takeIf { it in folders }
        ?: folders.firstOrNull()
    val selected = Store.stringList(Keys.SELECTED)
    val selectedSet = remember(selected) { selected.toHashSet() }
    var items by remember { mutableStateOf<List<PhotoItem>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val pickFolder = rememberFolderPicker()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) picked = uris
    }

    LaunchedEffect(current, reload) {
        items = null
        items = if (current == null) emptyList() else withContext(Dispatchers.IO) {
            PhotoRepo.listImages(context, current)
        }
    }

    if (picked.isNotEmpty()) {
        AddPhotosSheet(
            picked = picked,
            folders = folders,
            initialFolder = current,
            onPickAgain = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAddFolder = pickFolder,
            onDismiss = { picked = emptyList() },
            onCopied = { dest ->
                picked = emptyList()
                Store.edit { putString(Keys.CURRENT_FOLDER, dest) }
                reload++
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(folders) { f ->
                        FilterChip(
                            selected = f == current,
                            onClick = { Store.edit { putString(Keys.CURRENT_FOLDER, f) } },
                            label = { Text(PhotoRepo.folderName(f)) },
                            leadingIcon = if (f == current) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                    }
                    item {
                        AssistChip(
                            onClick = pickFolder,
                            label = { Text("Folder") },
                            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            Button(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, null)
                Text("Add photos from Gallery / Google Photos", Modifier.padding(start = 8.dp))
            }

            if (folders.isEmpty()) {
                Text(
                    "Add a folder first (tap + Folder). New photos are copied into it.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val list = items
            val inFolder = list?.count { it.uri in selectedSet } ?: 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${selected.size} selected" + if (list != null) " · $inFolder of ${list.size} here" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (list != null) Store.putStringList(Keys.SELECTED, (selected + list.map { it.uri }).distinct())
                }) { Text("Select all") }
                TextButton(onClick = {
                    if (list != null) {
                        val here = list.map { it.uri }.toHashSet()
                        Store.putStringList(Keys.SELECTED, selected.filter { it !in here })
                    }
                }) { Text("Clear") }
            }

            when {
                list == null -> Text(
                    "Loading photos…",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                list.isEmpty() -> Text(
                    "No photos in this folder yet. Tap Add photos to copy some in.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(list, key = { it.uri }) { item ->
                        val isSel = item.uri in selectedSet
                        PhotoTile(
                            uri = item.uri,
                            selected = isSel,
                            onClick = {
                                val newList = if (isSel) selected - item.uri else selected + item.uri
                                Store.putStringList(Keys.SELECTED, newList)
                            },
                            onLongClick = { onCrop(item.uri) },
                        )
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onDone,
                icon = { Icon(Icons.Rounded.Check, null) },
                text = { Text("Use ${selected.size} photos") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
        Text(
            "Long-press a photo to crop it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 34.dp),
        )
    }
}

@Composable
private fun AddPhotosSheet(
    picked: List<Uri>,
    folders: List<String>,
    initialFolder: String?,
    onPickAgain: () -> Unit,
    onAddFolder: () -> Unit,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dest by remember { mutableStateOf(initialFolder ?: folders.firstOrNull()) }
    var tick by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // After "Choose folder again", switch to the folder just picked.
    Store.tick.intValue
    val current = Store.prefs.getString(Keys.CURRENT_FOLDER, null)
    LaunchedEffect(current) {
        if (current != null && current in folders) {
            dest = current
            error = null
        }
    }

    ModalBottomSheet(onDismissRequest = { if (progress == null) onDismiss() }) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("Add photos", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${picked.size} picked from Gallery / Google Photos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(picked) { uri ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(uri).size(200).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 58.dp, height = 76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }

            if (folders.isEmpty()) {
                Text("First add a folder to copy them into.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAddFolder) { Text("Add folder") }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Copy into", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(
                            onClick = { menu = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Icon(Icons.Rounded.Folder, null)
                            Text(
                                dest?.let { PhotoRepo.folderName(it) } ?: "Choose a folder",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            )
                            dest?.let {
                                Text(
                                    targetLabel(Store.targetOf(it)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            folders.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text("${PhotoRepo.folderName(f)} · ${targetLabel(Store.targetOf(f))}") },
                                    onClick = { dest = f; menu = false },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ Add another folder") },
                                onClick = { menu = false; onAddFolder() },
                            )
                        }
                    }
                }
                SwitchRow("Tick them for rotation", null, tick) { tick = it }

                val e = error
                if (e != null) {
                    Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onAddFolder) {
                        Icon(Icons.Rounded.CreateNewFolder, null)
                        Text("Choose folder again", Modifier.padding(start = 8.dp))
                    }
                }
                val p = progress
                if (p != null) {
                    LinearProgressIndicator(progress = { p / picked.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPickAgain,
                        enabled = p == null,
                        modifier = Modifier.height(52.dp),
                    ) { Text("Pick again") }
                    Button(
                        enabled = p == null && dest != null,
                        onClick = {
                            val folder = dest ?: return@Button
                            error = null
                            progress = 0
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    PhotoRepo.copyInto(context, folder, picked) { n ->
                                        scope.launch { progress = n }
                                    }
                                }
                                val added = result.added
                                if (tick && added.isNotEmpty()) {
                                    Store.putStringList(Keys.SELECTED, (Store.stringList(Keys.SELECTED) + added).distinct())
                                }
                                progress = null
                                if (added.isEmpty()) {
                                    error = "Couldn't copy into ${PhotoRepo.folderName(folder)}. Tap Choose folder again, " +
                                        "open the same folder and tap Use this folder, then Allow.\n(Reason: ${result.error ?: "unknown"})"
                                } else {
                                    val skipped = picked.size - added.size
                                    Toast.makeText(
                                        context,
                                        if (skipped == 0) "Copied ${added.size} photos" else "Copied ${added.size}, skipped $skipped",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    onCopied(folder)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) { Text(if (p == null) "Copy ${picked.size} " + (if (picked.size == 1) "photo" else "photos") else "Copying $p of ${picked.size}…") }
                }
            }
        }
    }
}

private fun targetLabel(t: String) = when (t) {
    "home" -> "Home"
    "lock" -> "Lock"
    else -> "Both"
}

private fun removeFolder(context: android.content.Context, folder: String) {
    val all = Store.stringList(Keys.FOLDERS)
    Store.putStringList(Keys.SELECTED, Store.stringList(Keys.SELECTED).filter { Store.folderOf(it, all) != folder })
    val folders = all - folder
    Store.putStringList(Keys.FOLDERS, folders)
    Store.edit { putString(Keys.CURRENT_FOLDER, folders.firstOrNull()) }
    try {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(folder),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    } catch (_: Exception) {
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun PhotoTile(uri: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(uri).size(360).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selected) 8.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 14.dp else 0.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        if (selected) {
            Box(
                Modifier
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(
                Modifier
                    .padding(8.dp)
                    .size(20.dp)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

// ---------------- Rotation settings ----------------

@Composable
private fun RotationSettings(onChoose: () -> Unit, onCrop: (String) -> Unit) {
    val context = LocalContext.current
    Store.tick.intValue
    val selected = Store.stringList(Keys.SELECTED)
    val interval = Store.intervalMin()
    var customOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Change wallpaper every",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        val presets = listOf(1, 15, 30, 60, 360, 1440)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { m ->
                FilterChip(
                    selected = interval == m,
                    onClick = { Store.edit { putInt(Keys.INTERVAL_MIN, m) } },
                    label = { Text(intervalLabel(m)) },
                    leadingIcon = if (interval == m) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
            val custom = interval !in presets
            FilterChip(
                selected = custom,
                onClick = { customOpen = true },
                label = { Text(if (custom) intervalLabel(interval) else "Custom…") },
                leadingIcon = if (custom) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                LinkRow(
                    title = "${selected.size} photos selected",
                    subtitle = "Tap to change your selection",
                    onClick = onChoose,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    "Also change on unlock",
                    "A new photo every time you unlock",
                    Store.bool(Keys.ON_UNLOCK, false),
                ) { v -> Store.edit { putBoolean(Keys.ON_UNLOCK, v) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ChoiceRow(
                    "Order",
                    listOf("shuffle" to "Shuffle", "sequence" to "In order"),
                    Store.order(),
                ) { v -> Store.edit { putString(Keys.ORDER, v) } }
                ChoiceRow(
                    "Transition",
                    listOf("fade" to "Fade", "slide" to "Slide", "zoom" to "Zoom", "none" to "None"),
                    Store.transition(),
                ) { v -> Store.edit { putString(Keys.TRANSITION, v) } }
                ChoiceRow(
                    "Default fit",
                    listOf("fill" to "Fill", "fit" to "Fit", "blur" to "Blur fill"),
                    Store.fit(),
                ) { v -> Store.edit { putString(Keys.FIT, v) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 6.dp))
                LinkRow(
                    title = "Crop & zoom each photo",
                    subtitle = "Or long-press a photo in Choose photos",
                    onClick = {
                        if (selected.isNotEmpty()) {
                            val idx = Store.int(Keys.PHOTO_INDEX, 0).mod(selected.size)
                            onCrop(selected[idx])
                        } else {
                            onChoose()
                        }
                    },
                )
            }
        }

        Button(
            enabled = selected.isNotEmpty(),
            onClick = {
                Store.edit {
                    putLong(Keys.LAST_CHANGE, System.currentTimeMillis())
                    putBoolean(Keys.PAUSED, false)
                }
                applyWallpaper(context, video = false)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(52.dp),
        ) { Text(if (selected.isEmpty()) "Choose photos first" else "Start rotation") }
        Text(
            "Android then asks whether to use it on the home screen, lock screen or both.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
    }

    if (customOpen) {
        var text by remember { mutableStateOf(interval.toString()) }
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text("Custom interval") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(5) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = text.toIntOrNull()
                    if (m != null && m > 0) Store.edit { putInt(Keys.INTERVAL_MIN, m) }
                    customOpen = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}
