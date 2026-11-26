package com.prabhu.wallpaperstudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.data.Widgets
import com.prabhu.wallpaperstudio.wallpaper.GyroTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything about the Home screen wallpaper. */
/** Everything about the Home screen wallpaper. */
@Composable
fun WallpaperScreen(subTab: Int, onSubTab: (Int) -> Unit, onCrop: (key: String, source: String) -> Unit) {
    val context = LocalContext.current
    Store.tick.intValue
    resumeTick()
    val snack = LocalSnack.current
    var confirmApply by remember { mutableStateOf(false) }
    val applied = screenStatus(context, lock = false) == "photos"
    val count = remember(Store.tick.intValue) { PhotoLists.get(context, Target.HOME).size }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Home screen", "The wallpaper behind your apps")

        StatusBanner(
            ok = applied,
            title = if (applied) "Wallpaper Studio is on your Home screen" else "Not on your Home screen yet",
            detail = if (applied) "Everything below applies straight away." else "Press Apply once. After that, changes show straight away.",
            action = if (applied) null else "Apply",
            onAction = {
                if (count == 0) {
                    snack("Add photos first")
                    onSubTab(0)
                } else {
                    confirmApply = true
                }
            },
        )

        SettingCard(Modifier.padding(top = 10.dp)) {
            SummaryLine("Photos", "$count")
            SummaryLine("Changes", "every ${intervalLabel(Store.interval(Target.HOME))}" + if (Store.bool(Keys.HOME_ON_UNLOCK, false)) " + unlock" else "")
            SummaryLine("3D effect", if (Store.gyroOn()) "On" else "Off")
            val on = Widgets.load().filter { it.on }.joinToString { Widgets.label(it.type) }
            SummaryLine("Clock & widgets", on.ifEmpty { "None" })
        }

        TabRow(
            selectedTabIndex = subTab.coerceIn(0, 2),
            containerColor = Color.Transparent,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            listOf("Photos", "3D effect", "Widgets").forEachIndexed { i, t ->
                Tab(selected = subTab == i, onClick = { onSubTab(i) }, text = { Text(t, maxLines = 1) })
            }
        }
        Box(Modifier.padding(top = 14.dp, bottom = 32.dp)) {
            when (subTab) {
                0 -> HomePhotos(onCrop = { onCrop(it, it) })
                1 -> ThreeDSection()
                else -> WidgetsEditor(Target.HOME)
            }
        }
    }

    if (confirmApply) {
        ConfirmDialog(
            title = "Apply to Home screen",
            message = "Android will show a preview of your wallpaper.\n\nTap \"Set wallpaper\" (or \"Apply to\"), then choose \"Home screen\". Your lock screen is set separately, in the Lock tab.",
            confirm = "Continue",
            onConfirm = {
                confirmApply = false
                if (!applyWallpaper(context)) snack("Already on your Home screen")
            },
            onDismiss = { confirmApply = false },
        )
    }
}


@Composable
private fun HomePhotos(onCrop: (String) -> Unit) {
    val save = rememberSaver()
    var custom by remember { mutableStateOf(false) }
    Column {
        AlbumSection(Target.HOME, onCrop = onCrop)
        SectionLabel("Changing")
        SettingCard {
            Text("Change photo every", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            IntervalChips(
                current = Store.interval(Target.HOME),
                presets = listOf(1, 5, 15, 30, 60, 360, 1440),
                onPick = { m -> save("Changes every ${intervalLabel(m)}") { Store.edit { putInt(Keys.interval(Target.HOME), m) } } },
                onCustom = { custom = true },
            )
            SwitchRow("Also change when I unlock", "A new photo each time you unlock the phone", Store.bool(Keys.HOME_ON_UNLOCK, false)) { v ->
                save(if (v) "New photo on every unlock" else "Unlock no longer changes the photo") { Store.edit { putBoolean(Keys.HOME_ON_UNLOCK, v) } }
            }
            ChoiceRow("Order", listOf("shuffle" to "Shuffle", "sequence" to "In order"), Store.order(Target.HOME)) { v ->
                save("Order: ${if (v == "shuffle") "shuffle" else "in order"}") { Store.edit { putString(Keys.order(Target.HOME), v) } }
            }
            ChoiceRow("Transition", listOf("fade" to "Fade", "slide" to "Slide", "zoom" to "Zoom", "none" to "None"), Store.transition()) { v ->
                save("Transition: $v") { Store.edit { putString(Keys.HOME_TRANSITION, v) } }
            }
            ChoiceRow("Fit", listOf("smart" to "Smart", "fill" to "Fill", "fit" to "Fit", "blur" to "Blur"), Store.fit(Target.HOME)) { v ->
                save(fitMessage(v)) { Store.edit { putString(Keys.fit(Target.HOME), v) } }
            }
        }
    }
    if (custom) {
        CustomIntervalDialog(Store.interval(Target.HOME), allowZero = false, onSave = { m ->
            custom = false
            save("Changes every ${intervalLabel(m)}") { Store.edit { putInt(Keys.interval(Target.HOME), m) } }
        }, onDismiss = { custom = false })
    }
}

@Composable
fun CustomIntervalDialog(current: Int, allowZero: Boolean, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom time") },
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
                if (m != null && (m > 0 || allowZero)) onSave(m) else onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


// ---------------- 3D ----------------

@Composable
private fun ThreeDSection() {
    val context = LocalContext.current
    val save = rememberSaver()
    Store.tick.intValue
    val on = Store.gyroOn()
    val intensity = Store.gyroIntensity()
    val smooth = Store.gyroSmooth()
    val style = Store.gyroStyle()
    var preview by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { preview = withContext(Dispatchers.IO) { PhotoLists.current(context, Target.HOME) } }

    // Live preview using the same motion as the wallpaper.
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    val tracker = remember { GyroTracker(context) {} }
    DisposableEffect(on) {
        if (on) tracker.start()
        onDispose { tracker.stop() }
    }
    LaunchedEffect(on) {
        while (on) {
            withFrameNanos { }
            tracker.step(Store.gyroSmooth())
            tx = tracker.x
            ty = tracker.y
        }
    }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            Box(
                Modifier
                    .size(width = 104.dp, height = 210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .background(Color(0xFF34506B))
            ) {
                val p = preview
                if (p != null) {
                    val inten = if (on) intensity else 0f
                    AsyncImage(
                        model = photoModel(p),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                var s = 1f + 0.14f * inten
                                if (style == "zoom") s *= 1f + 0.06f * inten * minOf(1f, kotlin.math.hypot(tx, ty))
                                scaleX = s
                                scaleY = s
                                translationX = tx * 0.05f * inten * size.width
                                translationY = ty * 0.05f * inten * size.height
                                if (style == "tilt") {
                                    rotationY = -tx * 9f * inten
                                    rotationX = ty * 9f * inten
                                    cameraDistance = 14f * density.density
                                }
                            },
                    )
                }
            }
            Text(
                "Tilt your phone to try it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
            )
        }
        SettingCard(Modifier.padding(top = 12.dp)) {
            SwitchRow("3D gyro effect", "Stops when the screen is off or when you pause everything.", on) { v ->
                save(if (v) "3D effect on" else "3D effect off") { Store.edit { putBoolean(Keys.GYRO, v) } }
            }
            SliderRow("Depth", levelLabel(intensity), intensity, 0f..1f, onChange = { v ->
                Store.edit { putFloat(Keys.GYRO_INTENSITY, v) }
            }, onDone = { save("3D depth: ${levelLabel(Store.gyroIntensity()).lowercase()}") {} }, enabled = on)
            SliderRow("Smoothness", levelLabel(smooth), smooth, 0f..1f, onChange = { v ->
                Store.edit { putFloat(Keys.GYRO_SMOOTH, v) }
            }, onDone = { save("Smoothness: ${levelLabel(Store.gyroSmooth()).lowercase()}") {} }, enabled = on)
            ChoiceRow("Style", listOf("tilt" to "3D tilt", "shift" to "Shift", "zoom" to "Zoom"), style) { v ->
                save("3D style: ${if (v == "tilt") "3D tilt" else v}") { Store.edit { putString(Keys.GYRO_STYLE, v) } }
            }
        }
        PressButton(
            "Re-center",
            onClick = { save("Re-centered") { Store.edit { putLong(Keys.RECENTER, System.currentTimeMillis()) } } },
            icon = Icons.Rounded.CenterFocusStrong,
            style = BtnStyle.Outline,
            height = 46,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

private fun levelLabel(v: Float) = when {
    v < 0.34f -> "Low"
    v < 0.67f -> "Medium"
    else -> "High"
}
