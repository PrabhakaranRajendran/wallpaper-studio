package com.prabhu.wallpaperstudio.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.prabhu.wallpaperstudio.MainActivity
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.data.Widgets
import com.prabhu.wallpaperstudio.render.LockChanger
import com.prabhu.wallpaperstudio.render.PhotoMath
import com.prabhu.wallpaperstudio.update.Updater
import com.prabhu.wallpaperstudio.wallpaper.Battery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberSaver()
    val snack = LocalSnack.current
    Store.tick.intValue

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Settings")

        UpdateCard()

        SectionLabel("What's active")
        ActiveSummary()

        SectionLabel("Wallpaper check")
        WallpaperCheck()

        SectionLabel("Battery")
        SettingCard {
            SwitchRow("Pause 3D in Battery Saver", "The gyro effect stops while Battery Saver is on", Store.bool(Keys.BAT_3D_SAVER, true)) { v ->
                save(if (v) "3D pauses in Battery Saver" else "3D keeps running in Battery Saver") { Store.edit { putBoolean(Keys.BAT_3D_SAVER, v) } }
            }
            SwitchRow("Pause 3D below 20% battery", "Unless the phone is charging", Store.bool(Keys.BAT_3D_LOW, true)) { v ->
                save(if (v) "3D pauses below 20%" else "3D keeps running below 20%") { Store.edit { putBoolean(Keys.BAT_3D_LOW, v) } }
            }
            SwitchRow("Freeze video in Battery Saver", "Shows a still frame instead of playing", Store.bool(Keys.BAT_VIDEO_SAVER, true)) { v ->
                save(if (v) "Video freezes in Battery Saver" else "Video keeps playing in Battery Saver") { Store.edit { putBoolean(Keys.BAT_VIDEO_SAVER, v) } }
            }
            Text(
                "Everything stops when the screen is off or an app covers the wallpaper.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        SectionLabel("App updates")
        SettingCard {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check for updates", style = MaterialTheme.typography.bodyLarge)
                    val last = Store.long(Keys.LAST_UPDATE_CHECK, 0L)
                    Text(
                        "Version ${Updater.currentVersion}" + if (last > 0) {
                            " · checked " + DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PressButton(
                    if (Updater.checking) "Checking…" else "Check now",
                    onClick = {
                        Updater.dialogDismissed = true
                        scope.launch {
                            Updater.check(quiet = false)
                            Updater.message?.let { snack(it) }
                        }
                    },
                    enabled = !Updater.checking,
                    style = BtnStyle.Outline,
                    height = 42,
                    modifier = Modifier.size(width = 130.dp, height = 47.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow("Check automatically", "Tell me when a new version is on GitHub", Store.bool(Keys.AUTO_UPDATE, true)) { v ->
                save(if (v) "Automatic update check on" else "Automatic update check off") { Store.edit { putBoolean(Keys.AUTO_UPDATE, v) } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.repoUrl + "/releases"))) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Update source", style = MaterialTheme.typography.bodyLarge)
                    Text(Updater.repoUrl.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
            }
        }

        SectionLabel("App icon")
        val shortcutPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { makeShortcut(context, uri) }
        }
        SettingCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { shortcutPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Shortcut with your own picture", style = MaterialTheme.typography.bodyLarge)
                    Text("Pick any photo; it's added to your home screen as an icon that opens this app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null)
            }
        }

        SectionLabel("About")
        Text(
            "Wallpaper Studio ${Updater.currentVersion}\nNo ads, no tracking. Internet is only used for the update check and, if you turn it on, the weather.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 32.dp),
        )
    }
}

@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rel = Updater.available ?: return
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column {
                    Text("New version available", style = MaterialTheme.typography.titleMedium)
                    Text("Version ${rel.version} on GitHub · you have ${Updater.currentVersion}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (rel.notes.isNotBlank()) Text(rel.notes.take(500), style = MaterialTheme.typography.bodyMedium)
            val p = Updater.downloadProgress
            if (p != null) {
                Text("Downloading… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { Updater.available = null }) { Text("Later") }
                PressButton(
                    "Update now",
                    onClick = { scope.launch { Updater.downloadAndInstall(context, rel) } },
                    enabled = p == null,
                    height = 44,
                    modifier = Modifier.size(width = 150.dp, height = 49.dp),
                )
            }
        }
    }
}

/** One clear summary of everything that's set up. */
@Composable
private fun ActiveSummary() {
    val context = LocalContext.current
    resumeTick()
    val home = screenStatus(context, lock = false)
    val lock = screenStatus(context, lock = true)
    val homeCount = remember(Store.tick.intValue) { PhotoLists.get(context, Target.HOME).size }
    val lockCount = remember(Store.tick.intValue) { PhotoLists.get(context, Target.LOCK).size }
    SettingCard {
        Text("Home screen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        SummaryLine("Showing", when (home) {
            "photos" -> "Photos ($homeCount)"
            "video" -> "Video"
            else -> "Not set"
        })
        if (home == "photos") SummaryLine("Changes", "every ${intervalLabel(Store.interval(Target.HOME))}")
        SummaryLine("3D effect", if (Store.gyroOn()) "On" else "Off")
        SummaryLine("Widgets", Widgets.load().filter { it.on }.joinToString { Widgets.label(it.type) }.ifEmpty { "None" })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
        Text("Lock screen", style = MaterialTheme.typography.titleSmall)
        SummaryLine("Showing", when (lock) {
            "slideshow" -> "Slideshow ($lockCount photos)"
            "photos", "video" -> "Same as Home"
            else -> if (Store.lockMode() == "slideshow") "Slideshow (not started)" else "Not set"
        })
        if (Store.lockMode() == "slideshow") {
            SummaryLine("Changes", if (Store.interval(Target.LOCK) <= 0) "every lock" else "every ${intervalLabel(Store.interval(Target.LOCK))}")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
        SummaryLine("Theme", (Palettes.firstOrNull { it.id == Store.palette() }?.name ?: "Material You") + " · " + when (Store.string(Keys.THEME, "system")) {
            "light" -> "Light"
            "dark" -> "Dark"
            else -> "Same as phone"
        })
        SummaryLine("Paused", if (Store.paused()) "Yes" else "No")
        Box(Modifier.size(6.dp))
    }
}

/** Shows what the wallpaper is doing, to spot problems quickly. */
@Composable
private fun WallpaperCheck() {
    val context = LocalContext.current
    val snack = LocalSnack.current
    val now = System.currentTimeMillis()
    val last = Store.long(Keys.last(Target.HOME), 0L)
    val interval = Store.interval(Target.HOME)
    val nextMin = ((last + interval * 60_000L - now) / 60_000L).coerceAtLeast(0L)
    SettingCard {
        SummaryLine("Home last change", if (last == 0L) "never" else "${(now - last) / 60_000L} min ago")
        SummaryLine("Home next change", if (Store.paused()) "paused" else if (nextMin == 0L) "when you next see it" else "in about $nextMin min")
        val lockLast = Store.long(Keys.last(Target.LOCK), 0L)
        SummaryLine("Lock last change", if (lockLast == 0L) "never" else "${(now - lockLast) / 60_000L} min ago")
        SummaryLine("Battery Saver", if (Battery.saverOn(context)) "On" else "Off")
        Text("Recent activity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Text(
            Store.string(Keys.ENGINE_LOG, "").ifBlank { "Nothing yet. Apply a wallpaper and lock/unlock once." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            PressButton("Next Home", onClick = {
                Store.edit { putLong(Keys.change(Target.HOME), System.currentTimeMillis()) }
                snack("Asked the Home screen to change")
            }, icon = Icons.Rounded.Refresh, style = BtnStyle.Tonal, height = 42, modifier = Modifier.weight(1f))
            PressButton("Next Lock", onClick = {
                LockChanger.changeAsync(context, "Wallpaper check")
                snack("Changing the lock screen")
            }, icon = Icons.Rounded.Refresh, style = BtnStyle.Tonal, height = 42, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = {
            Store.edit { putString(Keys.ENGINE_LOG, "") }
            snack("Log cleared")
        }) { Text("Clear log") }
    }
}

/** Adds a home-screen shortcut whose icon is the chosen picture. */
private suspend fun makeShortcut(context: Context, uri: Uri) {
    val bmp = withContext(Dispatchers.IO) {
        try {
            val src = PhotoMath.decode(context, uri.toString(), 1024)
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            Bitmap.createScaledBitmap(square, 432, 432, true)
        } catch (e: Exception) {
            null
        }
    }
    if (bmp == null) {
        Toast.makeText(context, "Couldn't open that picture", Toast.LENGTH_SHORT).show()
        return
    }
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        Toast.makeText(context, "Your launcher doesn't support shortcuts", Toast.LENGTH_SHORT).show()
        return
    }
    val info = ShortcutInfoCompat.Builder(context, "custom_icon_${System.currentTimeMillis()}")
        .setShortLabel("Wallpaper Studio")
        .setIcon(IconCompat.createWithAdaptiveBitmap(bmp))
        .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, info, null)
}
