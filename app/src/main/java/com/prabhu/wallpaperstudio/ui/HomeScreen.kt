package com.prabhu.wallpaperstudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
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
import com.prabhu.wallpaperstudio.wallpaper.Battery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Overview(
    val homePhoto: String?,
    val lockPhoto: String?,
    val homeCount: Int,
    val lockCount: Int,
    val loaded: Boolean = true,
)

/**
 * The first screen: one big "is it running" line, then one card for the Home screen
 * and one for the lock screen. Each card says what is set and what to do next.
 */
@Composable
fun HomeScreen(onOpen: (AppTab) -> Unit) {
    val context = LocalContext.current
    val tick = Store.tick.intValue
    val resumed = resumeTick()
    val snack = LocalSnack.current
    val homeOn = screenStatus(context, lock = false) == "photos"
    val lockOn = screenStatus(context, lock = true) == "slideshow"
    val reason = Battery.stopReason(context)
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

        // 1. Is anything running, and the one button that stops everything.
        StatusBanner(
            ok = reason == null && (homeOn || lockOn),
            title = when {
                reason != null -> "Stopped: $reason"
                homeOn || lockOn -> "Running"
                else -> "Nothing is set up yet"
            },
            detail = when {
                paused -> "Photos and the 3D effect are not changing."
                reason != null -> "Photos and the 3D effect wait until this is over."
                homeOn || lockOn -> "Photos change by themselves and use very little battery."
                else -> "Use the two cards below to set up your Home and lock screens."
            },
        )
        PressButton(
            if (paused) "Resume everything" else "Pause everything",
            onClick = {
                Store.edit { putBoolean(Keys.PAUSED, !paused) }
                snack(if (paused) "Running again" else "Paused: photos and 3D stopped")
            },
            icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            style = if (paused) BtnStyle.Brand else BtnStyle.Tonal,
            height = 48,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )

        // 2. Home screen card
        ScreenCard(
            title = "Home screen",
            icon = Icons.Rounded.Wallpaper,
            photo = overview.homePhoto,
            on = homeOn,
            onLabel = "On",
            offLabel = "Not set",
            lines = listOf(
                "${overview.homeCount} photos · changes every ${intervalLabel(Store.interval(Target.HOME))}",
                listOfNotNull(
                    if (Store.gyroOn()) "3D tilt" else null,
                    if (Widgets.anyOn()) "clock & widgets" else null,
                ).joinToString(" · ").ifEmpty { "No 3D, no widgets" },
            ),
            primary = if (homeOn) "Next photo" else "Set up",
            onPrimary = {
                if (homeOn) {
                    Store.edit { putLong(Keys.change(Target.HOME), System.currentTimeMillis()) }
                    snack("Changing your Home screen photo")
                } else {
                    onOpen(AppTab.Wallpaper)
                }
            },
            onOpen = { onOpen(AppTab.Wallpaper) },
            modifier = Modifier.padding(top = 16.dp),
        )

        // 3. Lock screen card
        ScreenCard(
            title = "Lock screen",
            icon = Icons.Rounded.Lock,
            photo = overview.lockPhoto,
            on = lockOn,
            onLabel = "On",
            offLabel = if (Store.lockMode() == "slideshow") "Waiting for first photo" else "Off",
            lines = listOf(
                "${overview.lockCount} photos · " + intervalLockLine(),
                listOfNotNull(
                    if (Widgets.anyOn(Target.LOCK)) "text & date" else null,
                ).joinToString(" · ").ifEmpty { "No text on the picture" },
            ),
            primary = if (lockOn) "Next photo" else "Set up",
            onPrimary = {
                if (lockOn) {
                    LockChanger.changeAsync(context, "Next photo")
                    snack("Changing your lock screen photo")
                } else {
                    onOpen(AppTab.Lock)
                }
            },
            onOpen = { onOpen(AppTab.Lock) },
            modifier = Modifier.padding(top = 12.dp),
        )

        // 4. What to fix, if anything
        val tips = buildList {
            if (overview.loaded && overview.homeCount < 2) add("Home screen has ${overview.homeCount} photo${if (overview.homeCount == 1) "" else "s"}. Add at least 2 so it can change." to AppTab.Wallpaper)
            if (overview.loaded && Store.lockMode() == "slideshow" && overview.lockCount < 2) add("Lock screen has ${overview.lockCount} photo${if (overview.lockCount == 1) "" else "s"}. Add at least 2 so it can change." to AppTab.Lock)
        }
        tips.forEach { (text, tab) ->
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { onOpen(tab) }) { Text("Add") }
            }
        }
        Box(Modifier.padding(bottom = 24.dp))
    }
}

private fun intervalLockLine(): String {
    if (Store.lockMode() != "slideshow") return "slideshow is off"
    val every = Store.interval(Target.LOCK)
    return if (every <= 0) "new photo every lock" else "changes every ${intervalLabel(every)}"
}

@Composable
private fun ScreenCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    photo: String?,
    on: Boolean,
    onLabel: String,
    offLabel: String,
    lines: List<String>,
    primary: String,
    onPrimary: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(width = 104.dp, height = 224.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1D2A3A))
                    .border(3.dp, cs.outline, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photoModel(photo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(icon, null, tint = Color.White.copy(alpha = 0.7f))
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Text(
                    if (on) onLabel else offLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) cs.onPrimary else cs.onTertiaryContainer,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) cs.primary else cs.tertiaryContainer)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
                lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                PressButton(
                    primary,
                    onClick = onPrimary,
                    icon = if (on) Icons.Rounded.Refresh else null,
                    style = BtnStyle.Brand,
                    height = 44,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                PressButton(
                    "Change settings",
                    onClick = onOpen,
                    style = BtnStyle.Outline,
                    height = 44,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}
