package com.prabhu.wallpaperstudio.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.prabhu.wallpaperstudio.data.Crop
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.render.PhotoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crop & zoom for one photo, or for the video ([key] = "video").
 * The frame has this phone's screen shape, so what you see is what the wallpaper shows.
 */
@Composable
fun CropScreen(key: String, source: String, isVideo: Boolean, target: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val snack = LocalSnack.current
    val confirmTap = rememberConfirmTap()
    val screen = remember { PhotoMath.screenSize(context) }
    val initial = remember(key) { Store.crops()[key] ?: Crop() }
    var zoom by remember(key) { mutableFloatStateOf(initial.zoom) }
    var ox by remember(key) { mutableFloatStateOf(initial.x) }
    var oy by remember(key) { mutableFloatStateOf(initial.y) }
    var rotation by remember(key) { mutableIntStateOf(initial.rotation) }
    var flip by remember(key) { mutableStateOf(initial.flip) }
    var brightness by remember(key) { mutableFloatStateOf(initial.brightness) }
    var applyAll by remember(key) { mutableStateOf(false) }
    var bitmap by remember(key) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (isVideo) videoFrame(source) else PhotoMath.decode(context, source, 2048)
            } catch (e: Exception) {
                null
            }
        }
        failed = bitmap == null
    }

    fun clampOffsets() {
        val b = bitmap ?: return
        val (mx, my) = PhotoMath.maxOffset(b.width, b.height, screen.x.toFloat(), screen.y.toFloat(), Crop(zoom, ox, oy, rotation, flip))
        ox = ox.coerceIn(-mx, mx)
        oy = oy.coerceIn(-my, my)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(64.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close without saving") }
                Column(Modifier.weight(1f)) {
                    Text(if (isVideo) "Crop & zoom video" else "Crop & zoom", style = MaterialTheme.typography.titleLarge)
                    Text("Drag to move · pinch to zoom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PressButton(
                    "Save",
                    onClick = {
                        val crop = Crop(zoom, ox, oy, rotation, flip, brightness)
                        val map = Store.crops().toMutableMap()
                        map[key] = crop
                        if (applyAll && target != null) {
                            PhotoLists.get(context, target, 0L).filter { it != key }.forEach { other ->
                                map[other] = (map[other] ?: Crop()).copy(zoom = zoom)
                            }
                        }
                        Store.putCrops(map)
                        confirmTap()
                        snack(if (applyAll) "Crop saved, zoom used for all photos" else "Crop saved")
                        onClose()
                    },
                    height = 44,
                    modifier = Modifier.width(104.dp),
                )
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val aspect = screen.x.toFloat() / screen.y
                val frameH = if (maxWidth / aspect < maxHeight) maxWidth / aspect else maxHeight
                val frameW = frameH * aspect
                val density = LocalDensity.current
                val fwPx = with(density) { frameW.toPx() }
                val fhPx = with(density) { frameH.toPx() }
                val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
                Box(
                    Modifier
                        .size(frameW, frameH)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                        .pointerInput(key, fwPx, fhPx) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                ox += pan.x / fwPx
                                oy += pan.y / fhPx
                                clampOffsets()
                            }
                        },
                ) {
                    val b = bitmap
                    if (b != null) {
                        val m = remember { Matrix() }
                        val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
                        Canvas(Modifier.fillMaxSize()) {
                            PhotoMath.place(m, b.width, b.height, size.width, size.height, Crop(zoom, ox, oy, rotation, flip, brightness), "fill")
                            paint.colorFilter = PhotoMath.brightnessFilter(brightness)
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(b, m, paint) }
                        }
                    } else {
                        Text(
                            if (failed) "Couldn't open this ${if (isVideo) "video" else "photo"}" else "Loading…",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Text(
                        clock,
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp),
                    )
                }
            }

            SliderRow("Zoom", "%.1f×".format(zoom), zoom, 1f..4f, onChange = {
                zoom = it
                clampOffsets()
            })
            if (!isVideo) {
                SliderRow("Brightness", "${(brightness * 100).toInt()}%", brightness, -0.5f..0.5f, onChange = { brightness = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PressButton("Rotate", onClick = {
                    rotation = (rotation + 90) % 360
                    clampOffsets()
                }, icon = Icons.Rounded.RotateRight, style = BtnStyle.Tonal, height = 46, modifier = Modifier.weight(1f))
                PressButton("Flip", onClick = { flip = !flip }, icon = Icons.Rounded.Flip, style = BtnStyle.Tonal, height = 46, modifier = Modifier.weight(1f))
                PressButton("Reset", onClick = {
                    zoom = 1f; ox = 0f; oy = 0f; rotation = 0; flip = false; brightness = 0f
                }, icon = Icons.Rounded.RestartAlt, style = BtnStyle.Tonal, height = 46, modifier = Modifier.weight(1f))
            }
            if (!isVideo && target != null) {
                SwitchRow("Use this zoom for all photos", null, applyAll) { applyAll = it }
            } else {
                Box(Modifier.height(12.dp))
            }
        }
    }
}

/** First frame of a video, standing upright. */
fun videoFrame(path: String): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(path)
        val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: frame.width
        // Some phones return the frame still lying on its side: turn it upright.
        if (rot % 180 != 0 && frame.width == w) {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, m, true)
        } else {
            frame
        }
    } catch (e: Exception) {
        null
    } finally {
        try {
            r.release()
        } catch (_: Exception) {
        }
    }
}
