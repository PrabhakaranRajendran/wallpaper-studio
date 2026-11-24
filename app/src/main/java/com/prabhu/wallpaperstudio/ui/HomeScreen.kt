package com.prabhu.wallpaperstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prabhu.wallpaperstudio.Tab
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store

@Composable
fun HomeScreen(onOpen: (Tab) -> Unit) {
    val context = LocalContext.current
    Store.tick.intValue
    resumeTick()
    val active = activeMode(context)
    val selected = Store.stringList(Keys.SELECTED)
    val paused = Store.paused()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Wallpaper Studio")

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val previewUri = if (active == "photos" && selected.isNotEmpty()) {
                    selected[Store.int(Keys.PHOTO_INDEX, 0).mod(selected.size)]
                } else {
                    null
                }
                Box(
                    Modifier
                        .size(width = 88.dp, height = 176.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF34506B))
                        .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewUri != null) {
                        AsyncImage(
                            model = previewUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            if (active == "video") Icons.Rounded.Videocam else Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (active == null) "Not active yet" else if (paused) "Paused" else "Now active",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    when (active) {
                        "photos" -> {
                            Text("Photo changer", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${selected.size} photos · every ${intervalLabel(Store.intervalMin())}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (Store.gyroOn()) "3D effect on" else "3D effect off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!paused && selected.size > 1) {
                                val next = Store.long(Keys.LAST_CHANGE, 0L) +
                                    Store.intervalMin() * 60_000L - System.currentTimeMillis()
                                val mins = (next / 60_000L).coerceAtLeast(0L) + 1
                                Text("Next change in about ${intervalLabel(mins.toInt())}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        "video" -> {
                            Text("Video wallpaper", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Plays your optimized video. Stops when the screen is off.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            Text("Pick a mode", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Choose a video or some photos below, then tap Set as wallpaper.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (active != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (active == "photos") {
                                Button(onClick = {
                                    Store.edit { putLong(Keys.CHANGE_NOW, System.currentTimeMillis()) }
                                }) { Text("Change now") }
                            }
                            OutlinedButton(onClick = {
                                Store.edit { putBoolean(Keys.PAUSED, !paused) }
                            }) { Text(if (paused) "Resume" else "Pause") }
                        }
                    }
                }
            }
        }

        Text(
            "Modes",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeCard(Icons.Rounded.Videocam, "Video wallpaper", "Upload a video, optimized for your screen", false) {
                onOpen(Tab.Video)
            }
            ModeCard(Icons.Rounded.PhotoLibrary, "Photo changer", "Choose photos, rotate them on a timer", false) {
                onOpen(Tab.Photos)
            }
            ModeCard(Icons.Rounded.ViewInAr, "3D gyro effect", "Tilt your phone, the wallpaper moves", true) {
                onOpen(Tab.ThreeD)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, subtitle: String, tertiary: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val bg = if (tertiary) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
            val fg = if (tertiary) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
