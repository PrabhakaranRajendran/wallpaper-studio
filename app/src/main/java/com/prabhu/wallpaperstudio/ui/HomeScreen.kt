package com.prabhu.wallpaperstudio.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prabhu.wallpaperstudio.Tab
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(onOpen: (Tab) -> Unit) {
    val context = LocalContext.current
    Store.tick.intValue
    resumeTick()
    val brand = LocalBrand.current
    val home = screenStatus(context, lock = false)
    val lock = screenStatus(context, lock = true)
    val anyActive = home != null || lock != null
    val paused = Store.paused()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Wordmark()

        // Hero card in the theme's bold colour, with white stripes.
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(brand.color)
                .drawBehind {
                    val w = 14.dp.toPx()
                    val gap = 12.dp.toPx()
                    var x = size.width - 18.dp.toPx() - 6 * w - 5 * gap
                    repeat(6) {
                        drawRect(Color.White.copy(alpha = 0.22f), Offset(x, 0f), Size(w, size.height))
                        x += w + gap
                    }
                }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOW SHOWING",
                    style = MaterialTheme.typography.labelLarge,
                    color = brand.onColor,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        !anyActive -> "Not set"
                        paused -> "Paused"
                        else -> "Running"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = brand.color,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(brand.onColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            ) {
                ScreenPreview("Home screen", home, "home", brand)
                ScreenPreview("Lock screen", lock, "lock", brand)
            }
            Text(
                statusLine(home, lock),
                style = MaterialTheme.typography.bodyMedium,
                color = brand.onColor,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (anyActive) {
                    if (home == "photos" || lock == "photos") {
                        Button(
                            onClick = { Store.edit { putLong(Keys.CHANGE_NOW, System.currentTimeMillis()) } },
                            colors = ButtonDefaults.buttonColors(containerColor = brand.onColor, contentColor = brand.color),
                        ) { Text("Change now") }
                    }
                    OutlinedButton(
                        onClick = { Store.edit { putBoolean(Keys.PAUSED, !paused) } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = brand.onColor),
                        border = androidx.compose.foundation.BorderStroke(2.dp, brand.onColor),
                    ) { Text(if (paused) "Resume" else "Pause") }
                } else {
                    Button(
                        onClick = { onOpen(Tab.Photos) },
                        colors = ButtonDefaults.buttonColors(containerColor = brand.onColor, contentColor = brand.color),
                    ) { Text("Set up photos") }
                    OutlinedButton(
                        onClick = { onOpen(Tab.Video) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = brand.onColor),
                        border = androidx.compose.foundation.BorderStroke(2.dp, brand.onColor),
                    ) { Text("Use a video") }
                }
            }
        }

        Row(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Battery friendly · pauses when the screen is off",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            Modifier
                .padding(top = 12.dp, bottom = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickTile(Icons.Rounded.Videocam, "Video", Modifier.weight(1f)) { onOpen(Tab.Video) }
            QuickTile(Icons.Rounded.PhotoLibrary, "Photos", Modifier.weight(1f)) { onOpen(Tab.Photos) }
            QuickTile(Icons.Rounded.ViewInAr, "3D gyro", Modifier.weight(1f)) { onOpen(Tab.ThreeD) }
        }
    }
}

@Composable
fun Wordmark() {
    Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            "Wallpaper Studio",
            fontFamily = FontFamily.Cursive,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "LIVE · WALLPAPER · CHANGER",
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun statusLine(home: String?, lock: String?): String {
    fun describe(kind: String?, target: String): String = when (kind) {
        "video" -> "video" + if (Store.bool(Keys.VIDEO_SOUND, false)) ", sound on" else ", muted"
        "photos" -> {
            val n = Store.photosFor(target).size
            val gyro = if (Store.gyroOn()) ", 3D on" else ""
            "$n photos, every ${intervalLabel(Store.intervalMin())}$gyro"
        }
        else -> "not set"
    }
    if (home == null && lock == null) {
        return "Choose photos or a video, then tap Set wallpaper. Android asks for Home screen, Lock screen or both."
    }
    return "Home: ${describe(home, "home")}\nLock: ${describe(lock, "lock")}"
}

@Composable
private fun ScreenPreview(label: String, kind: String?, target: String, brand: Brand) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(width = 88.dp, height = 180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1D2A3A))
                .border(3.dp, brand.onColor, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                "photos" -> {
                    val uri = Store.currentPhoto(target)
                    if (uri != null) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                "video" -> {
                    val path = Store.prefs.getString(Keys.VIDEO_PATH, null)
                    var frame by remember(path) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(path) {
                        frame = if (path == null) null else withContext(Dispatchers.IO) { videoFrame(path) }
                    }
                    val f = frame
                    if (f != null) {
                        Image(
                            f.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Icon(Icons.Rounded.Videocam, null, tint = Color.White)
                }
                else -> Icon(Icons.Rounded.Wallpaper, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = brand.onColor)
        Text(
            when (kind) {
                "photos" -> if (Store.gyroOn()) "Photos · 3D" else "Photos"
                "video" -> if (Store.bool(Keys.VIDEO_SOUND, false)) "Video · sound" else "Video · muted"
                else -> "Not set"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF0D1B24),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun videoFrame(path: String): Bitmap? {
    if (!File(path).exists()) return null
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(path)
        r.getFrameAtTime(0)
    } catch (e: Exception) {
        null
    } finally {
        try {
            r.release()
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(88.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
