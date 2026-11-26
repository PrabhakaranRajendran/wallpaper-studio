package com.prabhu.wallpaperstudio.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.wallpaperstudio.data.CalendarSource
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.data.Weather
import com.prabhu.wallpaperstudio.data.WidgetDataSource
import com.prabhu.wallpaperstudio.data.WidgetItem
import com.prabhu.wallpaperstudio.data.Widgets
import com.prabhu.wallpaperstudio.render.PhotoMath
import com.prabhu.wallpaperstudio.render.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Clock, date, battery, weather, your text and next event – each with font, colour, size and position. */
@Composable
fun WidgetsEditor() {
    val context = LocalContext.current
    val save = rememberSaver()
    Store.tick.intValue
    val items = Widgets.load()
    val screen = remember { PhotoMath.screenSize(context) }

    // Background for the preview: the current Home photo, or the video's first frame.
    var background by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Store.homeSource()) {
        background = withContext(Dispatchers.IO) {
            try {
                if (Store.homeSource() == "video") {
                    Store.prefs.getString(Keys.VIDEO_PATH, null)?.let { videoFrame(it) }
                } else {
                    PhotoLists.current(context, Target.HOME)?.let { PhotoMath.decode(context, it, 1200) }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Column {
        Text(
            "Shown on your Home screen wallpaper. The lock screen keeps your phone's own clock.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Live preview, drawn by exactly the same code as the wallpaper.
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            val bg = background
            val m = remember { Matrix() }
            val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
            Canvas(
                Modifier
                    .width(200.dp)
                    .aspectRatio(screen.x / screen.y.toFloat())
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1D2A3A))
                    .border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            ) {
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    if (bg != null) {
                        PhotoMath.place(m, bg.width, bg.height, size.width, size.height, null, "fill")
                        nc.drawBitmap(bg, m, paint)
                    }
                    WidgetRenderer.draw(
                        nc, size.width.toInt(), size.height.toInt(), items,
                        WidgetDataSource.read(context), Store.bool(Keys.WIDGET_SHADOW, true), preview = true,
                    )
                }
            }
        }

        SettingCard(Modifier.padding(top = 12.dp)) {
            SwitchRow("Soft shadow behind text", "Easier to read on bright photos", Store.bool(Keys.WIDGET_SHADOW, true)) { v ->
                save(if (v) "Text shadow on" else "Text shadow off") { Store.edit { putBoolean(Keys.WIDGET_SHADOW, v) } }
            }
        }

        items.forEach { item -> WidgetCard(item) }
    }
}

@Composable
private fun WidgetCard(item: WidgetItem) {
    val context = LocalContext.current
    val save = rememberSaver()
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                withContext(Dispatchers.IO) { CalendarSource.refresh(context, force = true) }
                save("Next calendar event on") { Widgets.update(item.copy(on = true)) }
            }
        } else {
            snack("Calendar permission is needed for this widget")
        }
    }
    val name = Widgets.label(item.type)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, if (item.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SwitchRow(name, if (item.on) "Shown" else "Hidden", item.on) { v ->
                if (v && item.type == "event" && !CalendarSource.hasPermission(context)) {
                    calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                } else {
                    save(if (v) "$name on" else "$name off") { Widgets.update(item.copy(on = v)) }
                    if (v && item.type == "event") scope.launch(Dispatchers.IO) { CalendarSource.refresh(context, force = true) }
                }
            }
            if (!item.on) return@Column

            when (item.type) {
                "clock" -> {
                    ChoiceRow("Style", listOf("big" to "One line", "stacked" to "Stacked"), item.style.ifEmpty { "big" }) { v ->
                        save("Clock style saved") { Widgets.update(item.copy(style = v)) }
                    }
                    SwitchRow("24-hour clock", null, item.h24) { v -> save(if (v) "24-hour clock" else "12-hour clock") { Widgets.update(item.copy(h24 = v)) } }
                }
                "date" -> ChoiceRow("Format", listOf("short" to "Thu, 26 Nov", "long" to "Thursday…", "numeric" to "26/11/2026"), item.style.ifEmpty { "short" }) { v ->
                    save("Date format saved") { Widgets.update(item.copy(style = v)) }
                }
                "text" -> {
                    var text by remember(item.type) { mutableStateOf(item.text) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it.take(80)
                            Widgets.update(item.copy(text = text))
                        },
                        label = { Text("Your text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
                "weather" -> {
                    var city by remember { mutableStateOf(Store.string(Keys.WEATHER_CITY, "")) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it.take(40) },
                            label = { Text("City") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        PressButton("Set", onClick = {
                            scope.launch {
                                val err = withContext(Dispatchers.IO) { Weather.setCity(city) }
                                snack(err ?: "Weather for ${Store.string(Keys.WEATHER_CITY, city)} saved")
                            }
                        }, height = 46, modifier = Modifier
                            .padding(start = 8.dp)
                            .width(84.dp))
                    }
                    Text(
                        Store.prefs.getString(Keys.WEATHER_TEXT, null)?.let { "Now: $it" } ?: "Weather data by Open-Meteo.com. Only the city name is sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                "event" -> Text(
                    CalendarSource.cached?.let { "Next: $it" } ?: "No event in the next 24 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // Font
            Text("Font", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Widgets.FONTS.forEach { (id, label) ->
                    val selected = item.font == id
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .clickable { save("Font: $label") { Widgets.update(item.copy(font = id)) } }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            fontFamily = FontFamily(androidx.compose.ui.text.font.Typeface(WidgetRenderer.typeface(id))),
                            fontSize = 17.sp,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Colour
            Text("Colour", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Widgets.COLORS.forEach { c ->
                    val selected = item.color == c
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { save("Colour saved") { Widgets.update(item.copy(color = c)) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, null, tint = if (c == 0xFFFFFFFF.toInt()) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            var hue by remember(item.type) { mutableFloatStateOf(hueOf(item.color)) }
            Text("Any colour", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    Widgets.update(item.copy(color = Color.hsv(it, 0.65f, 1f).toArgb()))
                },
                onValueChangeFinished = { save("Colour saved") {} },
                valueRange = 0f..359f,
            )

            SliderRow("Size", "${(item.size * 100).toInt()}%", item.size, 0.5f..2f, onChange = { v ->
                Widgets.update(item.copy(size = v))
            }, onDone = { save("Size saved") {} })
            SliderRow("Position (top ↔ bottom)", "${(item.y * 100).toInt()}%", item.y, 0.04f..0.96f, onChange = { v ->
                Widgets.update(item.copy(y = v))
            }, onDone = { save("Position saved") {} })
            ChoiceRow("Align", listOf("left" to "Left", "center" to "Centre", "right" to "Right"), item.align) { v ->
                save("Alignment saved") { Widgets.update(item.copy(align = v)) }
            }
        }
    }
}

private fun hueOf(color: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    return hsv[0]
}
