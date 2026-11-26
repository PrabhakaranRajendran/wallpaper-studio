package com.prabhu.wallpaperstudio.ui

import android.media.MediaMetadataRetriever
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
import androidx.compose.material3.ScrollableTabRow
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
import com.prabhu.wallpaperstudio.video.VideoOptimizer
import com.prabhu.wallpaperstudio.wallpaper.GyroTracker
import com.prabhu.wallpaperstudio.wallpaper.VideoRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything about the Home screen wallpaper. */
@Composable
fun WallpaperScreen(subTab: Int, onSubTab: (Int) -> Unit, onCrop: (key: String, source: String, video: Boolean) -> Unit) {
    val context = LocalContext.current
    Store.tick.intValue
    resumeTick()
    val save = rememberSaver()
    val snack = LocalSnack.current
    var confirmApply by remember { mutableStateOf(false) }
    val source = Store.homeSource()
    val status = screenStatus(context, lock = false)
    val applied = status == source

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Home screen wallpaper", "What shows behind your apps")

        // What you've chosen, at a glance.
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Icon(
                    if (applied) Icons.Rounded.CheckCircle else Icons.Rounded.Wallpaper,
                    null,
                    tint = if (applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (applied) "On your Home screen now" else "Not on your Home screen yet",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            ChoiceRow("Show", listOf("photos" to "Photos", "video" to "Video"), source) { v ->
                save(if (v == "video") "Home screen will show your video" else "Home screen will show photos") {
                    Store.edit { putString(Keys.HOME_SOURCE, v) }
                }
                onSubTab(if (v == "video") 1 else 0)
            }
            val count = remember(Store.tick.intValue) { PhotoLists.get(context, Target.HOME).size }
            if (source == "photos") {
                SummaryLine("Photos", "$count")
                SummaryLine("Changes", "every ${intervalLabel(Store.interval(Target.HOME))}" + if (Store.bool(Keys.HOME_ON_UNLOCK, false)) " + unlock" else "")
                SummaryLine("Order · transition", "${if (Store.order(Target.HOME) == "shuffle") "Shuffle" else "In order"} · ${Store.transition().replaceFirstChar { it.uppercase() }}")
            } else {
                val path = Store.prefs.getString(Keys.VIDEO_PATH, null)
                SummaryLine("Video", if (path != null && File(path).exists()) "Ready" else "Not chosen yet")
                SummaryLine("Sound · fit", "${if (Store.bool(Keys.VIDEO_SOUND, false)) "On" else "Muted"} · ${if (Store.string(Keys.VIDEO_FIT, "fill") == "fit") "Fit" else "Fill"}")
            }
            SummaryLine("3D effect", if (Store.gyroOn()) "On" else "Off")
            val on = Widgets.load().filter { it.on }.joinToString { Widgets.label(it.type) }
            SummaryLine("Widgets", on.ifEmpty { "None" })
            PressButton(
                if (applied) "Applied ✓  Apply again" else "Apply to Home screen",
                onClick = {
                    if (source == "video" && Store.prefs.getString(Keys.VIDEO_PATH, null) == null) {
                        snack("Choose a video first (Video tab)")
                        onSubTab(1)
                    } else if (source == "photos" && count == 0) {
                        snack("Add photos first")
                        onSubTab(0)
                    } else {
                        confirmApply = true
                    }
                },
                style = BtnStyle.Brand,
                icon = Icons.Rounded.Wallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
            )
            Text(
                "Changes you make below are used straight away. Apply is only needed once, or to switch between photos and video.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        ScrollableTabRow(
            selectedTabIndex = subTab.coerceIn(0, 3),
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            listOf("Photos", "Video", "3D effect", "Clock & widgets").forEachIndexed { i, t ->
                Tab(selected = subTab == i, onClick = { onSubTab(i) }, text = { Text(t) })
            }
        }
        Box(Modifier.padding(top = 14.dp, bottom = 32.dp)) {
            when (subTab) {
                0 -> HomePhotos(onCrop = { onCrop(it, it, false) })
                1 -> VideoSection(onCrop = { path -> onCrop(VideoRenderer.VIDEO_CROP_KEY, path, true) })
                2 -> ThreeDSection()
                else -> WidgetsEditor()
            }
        }
    }

    if (confirmApply) {
        val both = Store.lockMode() == "same"
        ConfirmDialog(
            title = "Apply to Home screen",
            message = "Android will now show a preview of your " + (if (source == "video") "video" else "photo") +
                " wallpaper.\n\nTap \"Apply to\" (or \"Set wallpaper\"), then choose \"" +
                (if (both) "Home and lock screens" else "Home screen") + "\".",
            confirm = "Continue",
            onConfirm = {
                confirmApply = false
                if (!applyWallpaper(context, video = source == "video")) {
                    snack("Already on your Home screen. Changes show straight away.")
                }
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
            ChoiceRow("Fit", listOf("fill" to "Fill", "fit" to "Fit", "blur" to "Blur fill"), Store.fit(Target.HOME)) { v ->
                save("Fit: ${if (v == "blur") "blur fill" else v}") { Store.edit { putString(Keys.fit(Target.HOME), v) } }
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

// ---------------- Video ----------------

private data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

@Composable
private fun VideoSection(onCrop: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberSaver()
    val snack = LocalSnack.current
    val confirmTap = rememberConfirmTap()
    val current = Store.prefs.getString(Keys.VIDEO_PATH, null)?.takeIf { File(it).exists() }
    var newUri by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var start by rememberSaveable { mutableFloatStateOf(0f) }
    var end by rememberSaveable { mutableFloatStateOf(0f) }
    var quality by rememberSaveable { mutableIntStateOf(1080) }
    var fps by rememberSaveable { mutableIntStateOf(30) }
    var progress by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newUri = uri.toString()
            start = 0f
            end = 0f
        }
    }
    LaunchedEffect(newUri) {
        val u = newUri ?: return@LaunchedEffect
        info = withContext(Dispatchers.IO) { readInfo(context, Uri.parse(u)) }
        val d = info?.durationMs ?: 0L
        if (end <= 0f && d > 0) end = minOf(d, 30_000L).toFloat()
    }

    Column {
        val nu = newUri
        if (nu == null) {
            if (current != null) {
                Text("Your video", style = MaterialTheme.typography.titleMedium)
                VideoPreview(current, 0L, Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PressButton("Crop & zoom", onClick = { onCrop(current) }, icon = Icons.Rounded.Crop, style = BtnStyle.Tonal, height = 46, modifier = Modifier.weight(1f))
                    PressButton("New video", onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }, icon = Icons.Rounded.FileUpload, style = BtnStyle.Outline, height = 46, modifier = Modifier.weight(1f))
                }
                SettingCard(Modifier.padding(top = 12.dp)) {
                    SwitchRow(
                        if (Store.bool(Keys.VIDEO_SOUND, false)) "Sound on" else "Sound off",
                        "Plays whenever your Home screen shows. Videos optimized without sound stay silent.",
                        Store.bool(Keys.VIDEO_SOUND, false),
                    ) { v -> save(if (v) "Video sound on" else "Video muted") { Store.edit { putBoolean(Keys.VIDEO_SOUND, v) } } }
                    ChoiceRow("Fit", listOf("fill" to "Fill screen", "fit" to "Show whole video"), Store.string(Keys.VIDEO_FIT, "fill")) { v ->
                        save(if (v == "fit") "Video: show the whole video" else "Video: fill the screen") { Store.edit { putString(Keys.VIDEO_FIT, v) } }
                    }
                    Text(
                        "Portrait and landscape videos are both turned upright automatically. Use Crop & zoom to pick exactly which part shows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            } else {
                Text(
                    "Pick any video. It's trimmed and resized for your screen so it plays smoothly and saves battery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PressButton(
                    "Choose a video",
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    icon = Icons.Rounded.FileUpload,
                    style = BtnStyle.Brand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
            return@Column
        }

        Text("New video", style = MaterialTheme.typography.titleMedium)
        VideoPreview(nu, start.toLong(), Modifier.padding(top = 8.dp))
        val i = info
        if (i != null) {
            Text(
                "${i.width} × ${i.height} · ${formatMs(i.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (i.durationMs > 1000) {
                SummaryLine("Trim loop", "${formatMs(start.toLong())} – ${formatMs(end.toLong())} · ${((end - start) / 1000).toInt()} s")
                RangeSlider(
                    value = start..end.coerceAtLeast(start),
                    onValueChange = { r ->
                        start = r.start
                        end = r.endInclusive.coerceAtLeast(r.start + 1000f).coerceAtMost(i.durationMs.toFloat())
                    },
                    valueRange = 0f..i.durationMs.toFloat(),
                )
            }
        }
        SettingCard(Modifier.padding(top = 8.dp)) {
            ChoiceRow("Quality", listOf("0" to "Screen", "1080" to "1080p", "720" to "720p"), quality.toString()) { quality = it.toInt() }
            ChoiceRow("Frame rate", listOf("24" to "24", "30" to "30", "60" to "60", "0" to "Original"), fps.toString()) { fps = it.toInt() }
            Text(
                "1080p at 30 frames a second is the battery-friendly choice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        val p = progress
        if (p != null) {
            Text("Optimizing… $p%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressButton("Cancel", onClick = { newUri = null }, style = BtnStyle.Outline, enabled = p == null, modifier = Modifier.weight(1f))
            PressButton(
                "Optimize & use",
                enabled = p == null && i != null,
                style = BtnStyle.Brand,
                modifier = Modifier.weight(1.6f),
                onClick = {
                    val vi = i ?: return@PressButton
                    progress = 0
                    scope.launch {
                        try {
                            val out = VideoOptimizer.optimize(
                                context,
                                VideoOptimizer.Options(
                                    uri = Uri.parse(nu),
                                    startMs = start.toLong(),
                                    endMs = (if (end > start) end else vi.durationMs.toFloat()).toLong(),
                                    shortSide = quality,
                                    fps = fps,
                                    fill = true,
                                    sound = Store.bool(Keys.VIDEO_SOUND, false),
                                ),
                            ) { progress = it }
                            val old = Store.prefs.getString(Keys.VIDEO_PATH, null)
                            Store.edit {
                                putString(Keys.VIDEO_PATH, out.absolutePath)
                                putString(Keys.HOME_SOURCE, "video")
                            }
                            Store.putCrop(VideoRenderer.VIDEO_CROP_KEY, null)
                            if (old != null && old != out.absolutePath) File(old).delete()
                            newUri = null
                            confirmTap()
                            snack("Video ready. Tap Apply to Home screen above if it isn't showing yet.")
                        } catch (e: Exception) {
                            snack("Couldn't optimize this video: ${e.message}")
                        } finally {
                            progress = null
                        }
                    }
                },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreview(uri: String, seekMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(if (uri.startsWith("/")) MediaItem.fromUri(Uri.fromFile(File(uri))) else MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(seekMs) { player.seekTo(seekMs) }
    Box(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun readInfo(context: android.content.Context, uri: Uri): VideoInfo? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(context, uri)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot % 180 != 0) {
            val t = w; w = h; h = t
        }
        VideoInfo(d, w, h)
    } catch (e: Exception) {
        null
    } finally {
        try {
            r.release()
        } catch (_: Exception) {
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
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
            SwitchRow("3D gyro effect", "Works with photos and video. Stops when the screen is off.", on) { v ->
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
