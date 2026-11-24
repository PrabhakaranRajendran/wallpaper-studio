package com.prabhu.wallpaperstudio.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.render.PhotoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CropScreen(uri: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val screen = remember { PhotoMath.screenSize(context) }
    val initial = remember(uri) { Store.crops()[uri] ?: Crop() }
    var zoom by remember(uri) { mutableFloatStateOf(initial.zoom) }
    var ox by remember(uri) { mutableFloatStateOf(initial.x) }
    var oy by remember(uri) { mutableFloatStateOf(initial.y) }
    var rotation by remember(uri) { mutableIntStateOf(initial.rotation) }
    var flip by remember(uri) { mutableStateOf(initial.flip) }
    var brightness by remember(uri) { mutableFloatStateOf(initial.brightness) }
    var applyAll by remember(uri) { mutableStateOf(false) }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    val selected = Store.stringList(Keys.SELECTED)
    val position = selected.indexOf(uri)

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                PhotoMath.decode(context, uri, 2048)
            } catch (e: Exception) {
                null
            }
        }
        failed = bitmap == null
    }

    fun clampOffsets() {
        val b = bitmap ?: return
        val (mx, my) = PhotoMath.maxOffset(
            b.width, b.height, screen.x.toFloat(), screen.y.toFloat(),
            Crop(zoom, ox, oy, rotation, flip),
        )
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
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                Column(Modifier.weight(1f)) {
                    Text("Crop & zoom", style = MaterialTheme.typography.titleLarge)
                    if (position >= 0) {
                        Text(
                            "Photo ${position + 1} of ${selected.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(onClick = {
                    val crop = Crop(zoom, ox, oy, rotation, flip, brightness)
                    val map = Store.crops().toMutableMap()
                    map[uri] = crop
                    if (applyAll) {
                        selected.filter { it != uri }.forEach { other ->
                            val c = map[other] ?: Crop()
                            map[other] = c.copy(zoom = zoom)
                        }
                    }
                    Store.putCrops(map)
                    onClose()
                }) { Text("Save") }
            }

            // Editor: a frame with this phone's screen shape.
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
                        .pointerInput(uri, fwPx, fhPx) {
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
                            PhotoMath.place(
                                m, b.width, b.height, size.width, size.height,
                                Crop(zoom, ox, oy, rotation, flip, brightness), "fill",
                            )
                            paint.colorFilter = PhotoMath.brightnessFilter(brightness)
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(b, m, paint) }
                        }
                    } else {
                        Text(
                            if (failed) "Couldn't open this photo" else "Loading…",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    // Lock-screen clock, so you can see what it will cover.
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

            SliderLabel("Zoom", "%.1f×".format(zoom))
            Slider(
                value = zoom,
                onValueChange = {
                    zoom = it
                    clampOffsets()
                },
                valueRange = 1f..4f,
            )
            SliderLabel("Brightness", "${(brightness * 100).toInt()}%")
            Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -0.5f..0.5f)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        rotation = (rotation + 90) % 360
                        clampOffsets()
                    },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.RotateRight, null); Text(" Rotate") }
                FilledTonalButton(onClick = { flip = !flip }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Flip, null); Text(" Flip")
                }
                FilledTonalButton(
                    onClick = {
                        zoom = 1f; ox = 0f; oy = 0f; rotation = 0; flip = false; brightness = 0f
                    },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.RestartAlt, null); Text(" Reset") }
            }
            SwitchRow("Use this zoom for all photos", null, applyAll) { applyAll = it }
        }
    }
}
