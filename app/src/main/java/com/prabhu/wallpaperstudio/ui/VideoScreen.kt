package com.prabhu.wallpaperstudio.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.video.VideoOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uriText by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var start by rememberSaveable { mutableStateOf(0f) }
    var end by rememberSaveable { mutableStateOf(0f) }
    var quality by rememberSaveable { mutableIntStateOf(1080) } // 0 = screen, 1080, 720
    var fps by rememberSaveable { mutableIntStateOf(30) }      // 0 = original
    var fill by rememberSaveable { mutableStateOf(true) }
    var progress by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uriText = uri.toString()
            start = 0f
            end = 0f
        }
    }

    LaunchedEffect(uriText) {
        val u = uriText ?: return@LaunchedEffect
        info = withContext(Dispatchers.IO) { readInfo(context, Uri.parse(u)) }
        val d = info?.durationMs ?: 0L
        if (end <= 0f && d > 0) end = minOf(d, 30_000L).toFloat()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Video wallpaper", Modifier.weight(1f))
            FilledTonalButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null)
                Text("Upload", Modifier.padding(start = 6.dp))
            }
        }

        val u = uriText
        if (u == null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a video", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap Upload and pick any video. It is trimmed, cropped to your screen and " +
                            "re-encoded so it plays smoothly and saves battery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (Store.prefs.getString(Keys.VIDEO_PATH, null) != null) {
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { applyWallpaper(context, video = true) }) {
                            Text("Use my last video")
                        }
                    }
                }
            }
            if (Store.prefs.getString(Keys.VIDEO_PATH, null) != null) SoundCard(Modifier.padding(top = 12.dp))
            return@Column
        }

        VideoPreview(u, start.toLong(), Modifier.padding(top = 4.dp))

        val i = info
        if (i != null) {
            Text(
                "${i.width} × ${i.height} · ${formatMs(i.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (i.durationMs > 1000) {
                SliderLabel("Trim loop", "${formatMs(start.toLong())} – ${formatMs(end.toLong())} · ${((end - start) / 1000).toInt()} s")
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

        SoundCard(Modifier.padding(top = 12.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Optimize for this phone", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    "Quality",
                    listOf("0" to "Screen", "1080" to "1080p", "720" to "720p"),
                    quality.toString(),
                ) { quality = it.toInt() }
                ChoiceRow(
                    "Frame rate",
                    listOf("24" to "24 fps", "30" to "30 fps", "60" to "60 fps", "0" to "Original"),
                    fps.toString(),
                ) { fps = it.toInt() }
                ChoiceRow(
                    "Fit",
                    listOf("fill" to "Fill screen", "fit" to "Fit inside"),
                    if (fill) "fill" else "fit",
                ) { fill = it == "fill" }
                Text(
                    "1080p at 30 fps is the battery-friendly choice. Video pauses when the screen is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        val p = progress
        if (p != null) {
            Text("Optimizing… $p%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            LinearProgressIndicator(
                progress = { p / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Button(
            enabled = p == null && i != null,
            onClick = {
                val vi = i ?: return@Button
                progress = 0
                scope.launch {
                    try {
                        val out = VideoOptimizer.optimize(
                            context,
                            VideoOptimizer.Options(
                                uri = Uri.parse(u),
                                startMs = start.toLong(),
                                endMs = (if (end > start) end else vi.durationMs.toFloat()).toLong(),
                                shortSide = quality,
                                fps = fps,
                                fill = fill,
                                sound = Store.bool(Keys.VIDEO_SOUND, false),
                            ),
                        ) { progress = it }
                        val old = Store.prefs.getString(Keys.VIDEO_PATH, null)
                        Store.edit { putString(Keys.VIDEO_PATH, out.absolutePath) }
                        if (old != null && old != out.absolutePath) File(old).delete()
                        applyWallpaper(context, video = true)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't optimize this video: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        progress = null
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(52.dp),
        ) { Text("Optimize & set as wallpaper") }

        Text(
            "Android then asks whether to use it on the home screen, lock screen or both.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun SoundCard(modifier: Modifier = Modifier) {
    Store.tick.intValue
    val on = Store.bool(Keys.VIDEO_SOUND, false)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (on) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                if (on) "Sound on" else "Sound off",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Text(
                if (on) "Plays whenever your home screen shows" else "Muted. Turn on to hear the video",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
            )
        }
        Switch(checked = on, onCheckedChange = { v -> Store.edit { putBoolean(Keys.VIDEO_SOUND, v) } })
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreview(uri: String, seekMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
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
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
