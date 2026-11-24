package com.prabhu.wallpaperstudio.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.ui.graphics.Color
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.prabhu.wallpaperstudio.MainActivity
import com.prabhu.wallpaperstudio.render.PhotoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.update.Updater
import kotlinx.coroutines.launch

@Composable
private fun ThemeGrid() {
    val current = Store.palette()
    val options = Palettes.map { Triple(it.id, it.name, it.swatch) }.toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        options += Triple(MATERIAL_YOU, "Material You", listOf(Color(0xFF8AB4F8), Color(0xFFF6AEA9), Color(0xFFA8DAB5)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, name, swatch) ->
                    val selected = id == current
                    Card(
                        onClick = { Store.edit { putString(Keys.PALETTE, id) } },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) LocalBrand.current.color else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp),
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row {
                                swatch.forEachIndexed { i, c ->
                                    Box(
                                        Modifier
                                            .offset(x = (-6 * i).dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                }
                            }
                            Text(
                                (if (selected) "✓ " else "") + name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Store.tick.intValue

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Settings")

        val rel = Updater.available
        if (rel != null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Column {
                            Text("New version available", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Version ${rel.version} on GitHub · you have ${Updater.currentVersion}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (rel.notes.isNotBlank()) {
                        Text(rel.notes.take(500), style = MaterialTheme.typography.bodyMedium)
                    }
                    val p = Updater.downloadProgress
                    if (p != null) {
                        Text("Downloading… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    }
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { Updater.available = null }) { Text("Later") }
                        Button(
                            enabled = p == null,
                            onClick = { scope.launch { Updater.downloadAndInstall(context, rel) } },
                        ) { Text("Update now") }
                    }
                }
            }
        }

        SectionLabel("Theme")
        ThemeGrid()
        ChoiceRow(
            "Mode",
            listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
            Store.string(Keys.THEME, "system"),
        ) { v -> Store.edit { putString(Keys.THEME, v) } }

        SectionLabel("Battery")
        SwitchRow(
            "Pause 3D in Battery Saver",
            "The gyro effect stops while Battery Saver is on",
            Store.bool(Keys.BAT_3D_SAVER, true),
        ) { v -> Store.edit { putBoolean(Keys.BAT_3D_SAVER, v) } }
        SwitchRow(
            "Pause 3D below 20% battery",
            "Unless the phone is charging",
            Store.bool(Keys.BAT_3D_LOW, true),
        ) { v -> Store.edit { putBoolean(Keys.BAT_3D_LOW, v) } }
        SwitchRow(
            "Freeze video in Battery Saver",
            "Shows a still frame instead of playing",
            Store.bool(Keys.BAT_VIDEO_SAVER, true),
        ) { v -> Store.edit { putBoolean(Keys.BAT_VIDEO_SAVER, v) } }

        SectionLabel("App icon")
        val shortcutPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { makeShortcut(context, uri) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable {
                    shortcutPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Shortcut with your own picture", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Pick any photo; it's added to your home screen as an icon that opens this app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null)
        }

        SectionLabel("App updates")
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            OutlinedButton(
                enabled = !Updater.checking,
                onClick = {
                    Updater.dialogDismissed = true
                    scope.launch { Updater.check(quiet = false) }
                },
            ) { Text(if (Updater.checking) "Checking…" else "Check now") }
        }
        Updater.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(
            "Check automatically",
            "Tell me when a new version is on GitHub",
            Store.bool(Keys.AUTO_UPDATE, true),
        ) { v -> Store.edit { putBoolean(Keys.AUTO_UPDATE, v) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.repoUrl + "/releases")))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Update source", style = MaterialTheme.typography.bodyLarge)
                Text(
                    Updater.repoUrl.removePrefix("https://"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.OpenInNew, contentDescription = null)
        }

        SectionLabel("About")
        Text(
            "Wallpaper Studio ${Updater.currentVersion}\nNo ads, no tracking. The only internet use is the update check.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 32.dp),
        )
    }
}
