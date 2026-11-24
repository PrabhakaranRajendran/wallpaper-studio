package com.prabhu.wallpaperstudio.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.wallpaper.PhotoWallpaperService
import com.prabhu.wallpaperstudio.wallpaper.VideoWallpaperService

/**
 * What one screen is showing: "photos", "video", or null (not ours).
 * The wallpapers report this themselves while they run, which is more
 * reliable than asking the system on some phones.
 */
fun screenStatus(context: Context, lock: Boolean): String? {
    val video = Store.bool(if (lock) Keys.ACTIVE_VIDEO_LOCK else Keys.ACTIVE_VIDEO_HOME, false)
    val photos = Store.bool(if (lock) Keys.ACTIVE_PHOTO_LOCK else Keys.ACTIVE_PHOTO_HOME, false)
    if (!video && !photos) return null
    // If the system clearly reports someone else's live wallpaper, trust that.
    val info = try {
        val wm = WallpaperManager.getInstance(context)
        if (lock && Build.VERSION.SDK_INT >= 34) wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK) else if (!lock) wm.wallpaperInfo else null
    } catch (e: Exception) {
        null
    }
    if (info != null && info.packageName != context.packageName) return null
    if (info != null) {
        return if (info.serviceName == VideoWallpaperService::class.java.name) "video" else "photos"
    }
    return if (video) "video" else "photos"
}

/** Opens Android's "Set wallpaper" screen for our photo or video wallpaper. */
fun applyWallpaper(context: Context, video: Boolean) {
    val service = if (video) VideoWallpaperService::class.java else PhotoWallpaperService::class.java
    val want = if (video) "video" else "photos"
    if (screenStatus(context, lock = false) == want || screenStatus(context, lock = true) == want) {
        Toast.makeText(context, "Wallpaper updated", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, service))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e2: Exception) {
            Toast.makeText(context, "Open Wallpaper settings and choose Wallpaper Studio", Toast.LENGTH_LONG).show()
        }
    }
}

/** Increments every time the app comes back to the foreground. */
@Composable
fun resumeTick(): Int {
    var n by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        n++
        onPauseOrDispose { }
    }
    return n
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A label above a full-width single-choice segmented button row. */
@Composable
fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) {
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SliderLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.width(4.dp)) {}
    }
}

fun intervalLabel(min: Int): String = when {
    min < 60 -> "$min min"
    min % 1440 == 0 -> if (min == 1440) "Daily" else "${min / 1440} days"
    min % 60 == 0 -> "${min / 60} hr"
    else -> "${min / 60} hr ${min % 60} min"
}
