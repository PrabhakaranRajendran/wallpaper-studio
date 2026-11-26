package com.prabhu.wallpaperstudio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prabhu.wallpaperstudio.AppTab
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.data.Widgets
import com.prabhu.wallpaperstudio.render.LockChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Overview(
    val homePhoto: String?,
    val lockPhoto: String?,
    val homeCount: Int,
    val lockCount: Int,
    val loaded: Boolean = true,
)

@Composable
fun HomeScreen(onOpen: (AppTab) -> Unit) {
    val context = LocalContext.current
    val tick = Store.tick.intValue
    val resumed = resumeTick()
    val brand = LocalBrand.current
    val snack = LocalSnack.current
    val home = screenStatus(context, lock = false)
    val lock = screenStatus(context, lock = true)
    val paused = Store.paused()
    var overview by remember { mutableStateOf(Overview(null, null, 0, 0, loaded = false)) }
    LaunchedEffect(tick, resumed) {
        overview = withContext(Dispatchers.IO) {
            Overview(
                homePhoto = PhotoLists.current(context, Target.HOME),
                lockPhoto = PhotoLists.current(context, Target.LOCK),
                homeCount = PhotoLists.get(context, Target.HOME).size,
                lockCount = PhotoLists.get(context, Target.LOCK).size,
            )
        }
    }

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
                Text("NOW SHOWING", style = MaterialTheme.typography.labelLarge, color = brand.onColor, modifier = Modifier.weight(1f))
                Text(
                    when {
                        home == null && lock == null -> "Not set"
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
                MiniPhone(
                    label = "Home screen",
                    chip = when (home) {
                        "photos" -> if (Store.gyroOn()) "Photos · 3D" else "Photos"
                        "video" -> if (Store.bool(Keys.VIDEO_SOUND, false)) "Video · sound" else "Video · muted"
                        else -> "Not set"
                    },
                    photo = if (home == "photos") overview.homePhoto else null,
                    videoPath = if (home == "video") Store.prefs.getString(Keys.VIDEO_PATH, null) else null,
                    active = home != null,
                    brand = brand,
                )
                MiniPhone(
                    label = "Lock screen",
                    chip = when (lock) {
                        "slideshow" -> "Slideshow"
                        "photos" -> "Same as Home"
                        "video" -> "Same as Home"
                        else -> "Not set"
                    },
                    photo = when (lock) {
                        "slideshow" -> overview.lockPhoto
                        "photos" -> overview.homePhoto
                        else -> null
                    },
                    videoPath = if (lock == "video") Store.prefs.getString(Keys.VIDEO_PATH, null) else null,
                    active = lock != null,
                    brand = brand,
                )
            }
            Text(
                homeLine(home, overview.homeCount) + "\n" + lockLine(lock, overview.lockCount),
                style = MaterialTheme.typography.bodyMedium,
                color = brand.onColor,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressButton(
                    if (paused) "Resume" else "Pause",
                    onClick = {
                        Store.edit { putBoolean(Keys.PAUSED, !paused) }
                        snack(if (paused) "Changing photos again" else "Paused: photos won't change")
                    },
                    icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    style = BtnStyle.Outline,
                    height = 46,
                    modifier = Modifier.weight(1f),
                )
                if (home == "photos") {
                    PressButton(
                        "Next photo",
                        onClick = {
                            Store.edit { putLong(Keys.change(Target.HOME), System.currentTimeMillis()) }
                            snack("Changing your Home screen photo")
                        },
                        icon = Icons.Rounded.Refresh,
                        style = BtnStyle.Primary,
                        height = 46,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (Store.lockMode() == "slideshow") {
                PressButton(
                    "Next lock screen photo",
                    onClick = {
                        LockChanger.changeAsync(context, "Next photo")
                        snack("Changing your lock screen photo")
                    },
                    icon = Icons.Rounded.Lock,
                    style = BtnStyle.Outline,
                    height = 46,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }

        // Things that need attention, each with a way to fix it.
        val tips = buildList {
            if (home == null) add(Triple("Your Home screen isn't using Wallpaper Studio yet.", "Set up Home screen", AppTab.Wallpaper))
            if (overview.loaded && Store.homeSource() == "photos" && overview.homeCount < 2) {
                add(Triple("The Home screen has ${overview.homeCount} photo${if (overview.homeCount == 1) "" else "s"}. Add at least 2 so it can change.", "Add photos", AppTab.Wallpaper))
            }
            if (Store.lockMode() == "off") add(Triple("The lock screen slideshow is off.", "Set up Lock screen", AppTab.Lock))
            if (overview.loaded && Store.lockMode() == "slideshow" && overview.lockCount == 0) {
                add(Triple("The Lock screen album is empty.", "Add photos", AppTab.Lock))
            }
        }
        tips.forEach { (text, action, tab) ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    )
                    TextButton(onClick = { onOpen(tab) }) { Text(action) }
                }
            }
        }

        Row(
            Modifier
                .padding(top = 14.dp, bottom = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickTile(Icons.Rounded.Wallpaper, "Wallpaper", Modifier.weight(1f)) { onOpen(AppTab.Wallpaper) }
            QuickTile(Icons.Rounded.Lock, "Lock screen", Modifier.weight(1f)) { onOpen(AppTab.Lock) }
            QuickTile(Icons.Rounded.Palette, "Themes", Modifier.weight(1f)) { onOpen(AppTab.Themes) }
        }
    }
}

private fun homeLine(kind: String?, count: Int): String = when (kind) {
    "photos" -> {
        val extras = listOfNotNull(
            if (Store.gyroOn()) "3D" else null,
            if (Widgets.anyOn()) "clock" else null,
        ).joinToString(", ")
        "Home: $count photos, every ${intervalLabel(Store.interval(Target.HOME))}" + if (extras.isNotEmpty()) " · $extras" else ""
    }
    "video" -> "Home: video" + if (Store.bool(Keys.VIDEO_SOUND, false)) " with sound" else ", muted"
    else -> "Home: not set"
}

private fun lockLine(kind: String?, count: Int): String = when (kind) {
    "slideshow" -> {
        val every = Store.interval(Target.LOCK)
        "Lock: $count photos, " + if (every <= 0) "new photo every lock" else "every ${intervalLabel(every)}"
    }
    "photos", "video" -> "Lock: same as Home"
    else -> "Lock: not set"
}

@Composable
private fun MiniPhone(label: String, chip: String, photo: String?, videoPath: String?, active: Boolean, brand: Brand) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(width = 88.dp, height = 180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1D2A3A))
                .border(3.dp, brand.onColor, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null -> AsyncImage(
                    model = photoModel(photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                videoPath != null -> {
                    var frame by remember(videoPath) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(videoPath) { frame = withContext(Dispatchers.IO) { videoFrame(videoPath) } }
                    frame?.let {
                        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Icon(Icons.Rounded.Videocam, null, tint = Color.White)
                }
                else -> Icon(Icons.Rounded.Wallpaper, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = brand.onColor)
        Text(
            chip,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF0D1B24),
            modifier = Modifier
                .alpha(if (active) 1f else 0.85f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val tap = rememberTap()
    Card(
        onClick = {
            tap()
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(88.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
