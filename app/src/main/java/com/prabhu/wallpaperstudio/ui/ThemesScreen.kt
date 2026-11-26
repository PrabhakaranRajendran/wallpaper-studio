package com.prabhu.wallpaperstudio.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store

/** Pick how the app looks: colour theme, light/dark, and button effects. */
@Composable
fun ThemesScreen() {
    val context = LocalContext.current
    val save = rememberSaver()
    Store.tick.intValue
    val current = Store.palette()
    val dark = isAppInDarkTheme()

    val options = buildList {
        Palettes.forEach { p ->
            val (scheme, brand) = schemeFor(p.id, dark)!!
            add(ThemeOption(p.id, p.name, scheme, brand))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val s = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            add(ThemeOption(MATERIAL_YOU, "Material You", s, Brand(s.primary, s.onPrimary)))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Themes", "Choose how Wallpaper Studio looks")

        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { opt ->
                    ThemeCard(opt, selected = opt.id == current, modifier = Modifier.weight(1f)) {
                        save("Theme: ${opt.name}") { Store.edit { putString(Keys.PALETTE, opt.id) } }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        SectionLabel("Light or dark")
        ChoiceRow("Mode", listOf("system" to "Same as phone", "light" to "Light", "dark" to "Dark"), Store.string(Keys.THEME, "system")) { v ->
            save(
                when (v) {
                    "light" -> "Light mode"
                    "dark" -> "Dark mode"
                    else -> "Follows your phone's light or dark mode"
                }
            ) { Store.edit { putString(Keys.THEME, v) } }
        }

        SectionLabel("Buttons")
        SettingCard {
            SwitchRow("3D buttons", "Buttons look raised and sink when pressed", Store.press3d()) { v ->
                save(if (v) "3D buttons on" else "Flat buttons") { Store.edit { putBoolean(Keys.PRESS_3D, v) } }
            }
            SwitchRow("Vibrate on tap", "A light buzz when you press buttons and switches", Store.haptics()) { v ->
                Store.edit { putBoolean(Keys.HAPTICS, v) }
                save(if (v) "Vibration on" else "Vibration off") {}
            }
        }
        Text("Try them:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        val snack = LocalSnack.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 32.dp)) {
            PressButton("Brand", onClick = { snack("Pressed") }, style = BtnStyle.Brand, icon = Icons.Rounded.TouchApp, modifier = Modifier.weight(1f))
            PressButton("Primary", onClick = { snack("Pressed") }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
            PressButton("Outline", onClick = { snack("Pressed") }, style = BtnStyle.Outline, modifier = Modifier.weight(1f))
        }
    }
}

private data class ThemeOption(val id: String, val name: String, val scheme: ColorScheme, val brand: Brand)

/** A small picture of the app in this theme. */
@Composable
private fun ThemeCard(opt: ThemeOption, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = opt.scheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = s.background),
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) opt.brand.color else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp)) {
            // Hero card with stripes
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(opt.brand.color)
                    .drawBehind {
                        val w = 5.dp.toPx()
                        val gap = 5.dp.toPx()
                        var x = size.width - 6.dp.toPx() - 4 * w - 3 * gap
                        repeat(4) {
                            drawRect(Color.White.copy(alpha = 0.25f), Offset(x, 0f), Size(w, size.height))
                            x += w + gap
                        }
                    }
                    .padding(8.dp)
            ) {
                Column {
                    Box(Modifier.width(46.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(opt.brand.onColor))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(30.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(opt.brand.onColor.copy(alpha = 0.6f)))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .width(40.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(opt.brand.onColor)
                )
            }
            // A list row and a button
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(s.primaryContainer))
                Box(Modifier.padding(start = 6.dp).width(54.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.onSurface.copy(alpha = 0.5f)))
            }
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(s.primary)
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(opt.name, style = MaterialTheme.typography.titleSmall, color = s.onBackground, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, "Selected", tint = opt.brand.color, modifier = Modifier.size(20.dp))
                } else {
                    Row {
                        listOf(opt.brand.color, s.primary, s.primaryContainer).forEach {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(it))
                        }
                    }
                }
            }
        }
    }
}
