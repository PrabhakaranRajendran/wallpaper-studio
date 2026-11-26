package com.prabhu.wallpaperstudio.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.wallpaper.PhotoWallpaperService
import com.prabhu.wallpaperstudio.wallpaper.VideoWallpaperService
import java.io.File

// ---------------- pop-ups and feedback ----------------

/** Shows a short "Saved" style message at the bottom of the screen. */
val LocalSnack = staticCompositionLocalOf<(String) -> Unit> { {} }

/** A light vibration for taps (can be turned off in Themes). */
@Composable
fun rememberTap(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            if (Store.haptics()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

/** A firmer vibration for saving or applying something. */
@Composable
fun rememberConfirmTap(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            if (Store.haptics()) {
                val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                view.performHapticFeedback(c)
            }
        }
    }
}

/** Saves a setting, vibrates and shows a pop-up so it's clear the change was kept. */
@Composable
fun rememberSaver(): (String, () -> Unit) -> Unit {
    val tap = rememberTap()
    val snack = LocalSnack.current
    return remember(tap, snack) {
        { message, save ->
            save()
            tap()
            snack(message)
        }
    }
}

// ---------------- 3D press button ----------------

enum class BtnStyle { Primary, Brand, Tonal, Outline }

/**
 * A button that looks raised and sinks when pressed, with a small vibration.
 * The raised look can be turned off in Themes.
 */
@Composable
fun PressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Int = 52,
) {
    Store.tick.intValue
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tap = rememberTap()
    val brand = LocalBrand.current
    val cs = MaterialTheme.colorScheme
    val depth = if (Store.press3d()) 5.dp else 0.dp
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> cs.primary to cs.onPrimary
        BtnStyle.Brand -> brand.color to brand.onColor
        BtnStyle.Tonal -> cs.primaryContainer to cs.onPrimaryContainer
        BtnStyle.Outline -> cs.surfaceContainer to cs.primary
    }
    val edge = if (style == BtnStyle.Outline) cs.outline else lerp(bg, Color.Black, 0.35f)
    val sink by animateDpAsState(if (pressed && enabled) depth else 0.dp, label = "sink")
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "scale")
    val shape = RoundedCornerShape((height / 2).dp)
    Box(
        modifier
            .height(height.dp + depth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        if (depth > 0.dp) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(top = depth)
                    .clip(shape)
                    .background(edge)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .offset(y = sink)
                .clip(shape)
                .background(bg)
                .then(if (style == BtnStyle.Outline) Modifier.border(1.5.dp, cs.outline, shape) else Modifier)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                ) {
                    tap()
                    onClick()
                }
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Box(Modifier.size(8.dp))
            }
            Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- dialogs ----------------

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tap = rememberConfirmTap()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                tap()
                onConfirm()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- layout pieces ----------------

@Composable
fun ScreenTitle(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tap = rememberTap()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable {
                tap()
                onChange(!checked)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = {
            tap()
            onChange(it)
        })
    }
}

/** A label above a full-width single-choice segmented row. */
@Composable
fun ChoiceRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val tap = rememberTap()
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = {
                        tap()
                        onSelect(value)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) {
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit = {},
    enabled: Boolean = true,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, enabled = enabled)
    }
}

/** Chips for "change every …". 0 minutes is shown as [zeroLabel] (lock screen: every lock). */
@Composable
fun IntervalChips(current: Int, presets: List<Int>, zeroLabel: String? = null, onPick: (Int) -> Unit, onCustom: () -> Unit) {
    val tap = rememberTap()
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { m ->
            FilterChip(
                selected = current == m,
                onClick = {
                    tap()
                    onPick(m)
                },
                label = { Text(if (m == 0 && zeroLabel != null) zeroLabel else intervalLabel(m)) },
                leadingIcon = if (current == m) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
        val custom = current !in presets
        FilterChip(
            selected = custom,
            onClick = {
                tap()
                onCustom()
            },
            label = { Text(if (custom) intervalLabel(current) else "Custom…") },
        )
    }
}

/** A summary line like "Changes every · 30 min". */
@Composable
fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Wordmark() {
    Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            "Wallpaper Studio",
            fontFamily = FontFamily.Cursive,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text("LIVE · WALLPAPER · CHANGER", fontSize = 10.sp, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Increments every time the app comes back to the foreground. */
@Composable
fun resumeTick(): Int {
    var n by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        n++
        onPauseOrDispose { }
    }
    return n
}

// ---------------- helpers ----------------

fun intervalLabel(min: Int): String = when {
    min <= 0 -> "Every lock"
    min < 60 -> "$min min"
    min % 1440 == 0 -> if (min == 1440) "Daily" else "${min / 1440} days"
    min % 60 == 0 -> "${min / 60} hr"
    else -> "${min / 60} hr ${min % 60} min"
}

/** What Coil should load for a photo id (file path or content URI). */
fun photoModel(photo: String): Any = if (photo.startsWith("/")) File(photo) else photo

/**
 * What a screen is showing: "photos", "video", "slideshow" (lock only) or null.
 * The wallpapers report this themselves, which is more reliable than asking the system on some phones.
 */
fun screenStatus(context: Context, lock: Boolean): String? {
    if (lock && Store.lockMode() == "slideshow" && Store.long(Keys.last(com.prabhu.wallpaperstudio.data.Target.LOCK), 0L) > 0L) {
        return "slideshow"
    }
    val video = Store.bool(if (lock) Keys.ACTIVE_VIDEO_LOCK else Keys.ACTIVE_VIDEO_HOME, false)
    val photos = Store.bool(if (lock) Keys.ACTIVE_PHOTO_LOCK else Keys.ACTIVE_PHOTO_HOME, false)
    if (!video && !photos) return null
    val info = try {
        val wm = WallpaperManager.getInstance(context)
        if (lock && Build.VERSION.SDK_INT >= 34) wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK) else if (!lock) wm.wallpaperInfo else null
    } catch (e: Exception) {
        null
    }
    if (info != null && info.packageName != context.packageName) return null
    if (info != null) return if (info.serviceName == VideoWallpaperService::class.java.name) "video" else "photos"
    return if (video) "video" else "photos"
}

/** Opens Android's "Set wallpaper" screen for our photo or video wallpaper. */
fun applyWallpaper(context: Context, video: Boolean): Boolean {
    val service = if (video) VideoWallpaperService::class.java else PhotoWallpaperService::class.java
    val want = if (video) "video" else "photos"
    if (screenStatus(context, lock = false) == want) return false
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, service))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e2: Exception) {
            Toast.makeText(context, "Open Wallpaper settings and choose Wallpaper Studio", Toast.LENGTH_LONG).show()
        }
    }
    return true
}
