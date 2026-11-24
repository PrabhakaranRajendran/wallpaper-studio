package com.prabhu.wallpaperstudio.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { onTabChange(0) }, text = { Text("Choose photos") })
            Tab(selected = selectedTab == 1, onClick = { onTabChange(1) }, text = { Text("Rotation") })
        }
        if (selectedTab == 0) {
            ChoosePhotos(onDone = { onTabChange(1) }, onCrop = onCrop)
        } else {
            RotationSettings(onChoose = { onTabChange(0) }, onCrop = onCrop)
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

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val s = uri.toString()
            if (s !in folders) Store.putStringList(Keys.FOLDERS, folders + s)
            Store.edit { putString(Keys.CURRENT_FOLDER, s) }
        }
    }

    LaunchedEffect(current) {
        items = null
        items = if (current == null) emptyList() else withContext(Dispatchers.IO) {
            PhotoRepo.listImages(context, current)
        }
    }

    if (folders.isEmpty()) {
        Column(Modifier.padding(16.dp)) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a folder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick the folder that holds your wallpaper photos, then tick the ones you want. " +
                            "You can add more folders later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                        Text("Choose folder", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        trailingIcon = if (f == current) {
                            {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove folder",
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize)
                                        .combinedClickable(onClick = { removeFolder(context, f) }),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                item {
                    AssistChip(
                        onClick = { folderPicker.launch(null) },
                        label = { Text("Folder") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                    )
                }
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
                    "No photos in this folder.",
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

private fun removeFolder(context: android.content.Context, folder: String) {
    val folders = Store.stringList(Keys.FOLDERS) - folder
    Store.putStringList(Keys.FOLDERS, folders)
    Store.edit { putString(Keys.CURRENT_FOLDER, folders.firstOrNull()) }
    try {
        context.contentResolver.releasePersistableUriPermission(Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Exception) {
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
