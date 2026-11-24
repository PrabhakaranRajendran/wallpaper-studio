package com.prabhu.wallpaperstudio.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import kotlin.math.exp

@Composable
fun ParallaxScreen() {
    val context = LocalContext.current
    Store.tick.intValue
    val on = Store.gyroOn()
    val intensity = Store.gyroIntensity()
    val smooth = Store.gyroSmooth()
    val style = Store.gyroStyle()
    val selected = Store.stringList(Keys.SELECTED)
    val previewUri = if (selected.isNotEmpty()) selected[Store.int(Keys.PHOTO_INDEX, 0).mod(selected.size)] else null

    // Same motion model as the wallpaper, for a live preview.
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    DisposableEffect(on) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var last = 0L
        var rawX = 0f
        var rawY = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (last != 0L) {
                    val dt = (e.timestamp - last) / 1_000_000_000f
                    if (dt > 0f && dt < 0.2f) {
                        val decay = exp(-dt / 1.6f)
                        rawX = ((rawX + e.values[1] * dt) * decay).coerceIn(-0.45f, 0.45f)
                        rawY = ((rawY + e.values[0] * dt) * decay).coerceIn(-0.45f, 0.45f)
                        val a = 0.35f - 0.3f * Store.gyroSmooth()
                        tx += (rawX / 0.45f - tx) * a
                        ty += (rawY / 0.45f - ty) * a
                    }
                }
                last = e.timestamp
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (on && sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sm?.unregisterListener(listener)
            tx = 0f
            ty = 0f
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("3D gyro effect")

        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            Box(
                Modifier
                    .size(width = 98.dp, height = 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .background(Color(0xFF34506B))
            ) {
                if (previewUri != null) {
                    val inten = if (on) intensity else 0f
                    AsyncImage(
                        model = previewUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val over = 1f + 0.14f * inten
                                scaleX = over
                                scaleY = over
                                translationX = tx * 0.05f * inten * size.width
                                translationY = ty * 0.05f * inten * size.height
                                if (style == "tilt") {
                                    rotationY = -tx * 9f * inten
                                    rotationX = ty * 9f * inten
                                    cameraDistance = 14f * density.density
                                }
                                if (style == "zoom") {
                                    val z = 1f + 0.06f * inten * minOf(1f, kotlin.math.hypot(tx, ty))
                                    scaleX = over * z
                                    scaleY = over * z
                                }
                            },
                    )
                } else {
                    Text(
                        "Choose photos to preview",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                    )
                }
            }
            Text(
                "Tilt your phone",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
            )
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Gyro 3D effect", style = MaterialTheme.typography.titleMedium)
                    Text("Pauses when the screen is off to save battery", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = on,
                    onCheckedChange = { v -> Store.edit { putBoolean(Keys.GYRO, v) } },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                        checkedThumbColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                )
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val sliderColors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                )
                SliderLabel("Depth intensity", levelLabel(intensity))
                Slider(
                    value = intensity,
                    onValueChange = { v -> Store.edit { putFloat(Keys.GYRO_INTENSITY, v) } },
                    colors = sliderColors,
                    enabled = on,
                )
                SliderLabel("Smoothness", levelLabel(smooth))
                Slider(
                    value = smooth,
                    onValueChange = { v -> Store.edit { putFloat(Keys.GYRO_SMOOTH, v) } },
                    colors = sliderColors,
                    enabled = on,
                )
                ChoiceRow(
                    "Depth style",
                    listOf("tilt" to "3D tilt", "shift" to "Shift", "zoom" to "Zoom"),
                    style,
                ) { v -> Store.edit { putString(Keys.GYRO_STYLE, v) } }
            }
        }

        Text(
            "Works with the photo changer. 3D for video wallpapers is planned for a later update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { Store.edit { putLong(Keys.RECENTER, System.currentTimeMillis()) } },
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, null)
                Text(" Re-center")
            }
            Button(
                onClick = { applyWallpaper(context, video = false) },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) { Text("Apply 3D effect") }
        }
    }
}

private fun levelLabel(v: Float) = when {
    v < 0.34f -> "Low"
    v < 0.67f -> "Medium"
    else -> "High"
}
