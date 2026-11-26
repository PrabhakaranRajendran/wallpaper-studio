package com.prabhu.wallpaperstudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.PhotoLists
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.render.LockChanger
import com.prabhu.wallpaperstudio.wallpaper.LockWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The lock screen ("screensaver"): its own album and timing. */
@Composable
fun LockScreenTab(onCrop: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberSaver()
    val snack = LocalSnack.current
    val confirmTap = rememberConfirmTap()
    Store.tick.intValue
    resumeTick()
    val mode = Store.lockMode()
    var confirmSet by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val homeIsOurs = screenStatus(context, lock = false) != null
    val last = Store.long(Keys.last(Target.LOCK), 0L)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Lock screen", "What you see before you unlock")

        SettingCard {
            val active = mode == "slideshow" && last > 0
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Icon(
                    if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                    null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        mode == "off" -> "Wallpaper Studio isn't changing your lock screen"
                        mode == "same" -> "Uses your Home screen wallpaper"
                        active -> "Slideshow is on"
                        else -> "Slideshow is set up, not started yet"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            ChoiceRow(
                "Lock screen shows",
                listOf("slideshow" to "Photo slideshow", "same" to "Same as Home", "off" to "Off"),
                mode,
            ) { v ->
                save(
                    when (v) {
                        "slideshow" -> "Lock screen slideshow on"
                        "same" -> "Lock screen will use the Home wallpaper"
                        else -> "Lock screen slideshow off"
                    }
                ) { Store.edit { putString(Keys.LOCK_MODE, v) } }
                LockWorker.schedule(context)
                if (v == "slideshow") LockChanger.changeAsync(context, "slideshow turned on")
            }
            Text(
                when (mode) {
                    "slideshow" -> "Sets a normal picture on the lock screen and changes it for you. This works on OnePlus and every other phone."
                    "same" -> "Shows the Home wallpaper (photos, video, 3D) on the lock screen too. Apply it with \"Home and lock screens\". Some phones, including OnePlus, may still show their own lock picture."
                    else -> "Turn on the slideshow to give the lock screen its own photos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            if (mode == "slideshow") {
                val count = remember(Store.tick.intValue) { PhotoLists.get(context, Target.LOCK).size }
                SummaryLine("Photos", "$count")
                SummaryLine("Changes", if (Store.interval(Target.LOCK) <= 0) "every time you lock" else "every ${intervalLabel(Store.interval(Target.LOCK))}")
                SummaryLine("Order · fit", "${if (Store.order(Target.LOCK) == "shuffle") "Shuffle" else "In order"} · ${Store.fit(Target.LOCK).replaceFirstChar { it.uppercase() }}")
                SummaryLine("Last changed", if (last == 0L) "never" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(last)))
                PressButton(
                    "Set lock screen now",
                    onClick = { if (count == 0) snack("Add photos first") else confirmSet = true },
                    style = BtnStyle.Brand,
                    icon = Icons.Rounded.Lock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp),
                )
            }
        }

        if (mode == "slideshow") {
            SectionLabel("Photos")
            AlbumSection(Target.LOCK, onCrop = onCrop)

            SectionLabel("Changing")
            SettingCard {
                Text("New lock screen photo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                IntervalChips(
                    current = Store.interval(Target.LOCK),
                    presets = listOf(0, 15, 30, 60, 360, 1440),
                    zeroLabel = "Every lock",
                    onPick = { m ->
                        save(if (m == 0) "New photo every time you lock" else "Lock screen changes every ${intervalLabel(m)}") {
                            Store.edit { putInt(Keys.interval(Target.LOCK), m) }
                        }
                        LockWorker.schedule(context)
                    },
                    onCustom = { custom = true },
                )
                if (Store.interval(Target.LOCK) <= 0 && !homeIsOurs) {
                    Text(
                        "\"Every lock\" needs Wallpaper Studio as your Home wallpaper too. Until then it changes every 15 minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                ChoiceRow("Order", listOf("shuffle" to "Shuffle", "sequence" to "In order"), Store.order(Target.LOCK)) { v ->
                    save("Order: ${if (v == "shuffle") "shuffle" else "in order"}") { Store.edit { putString(Keys.order(Target.LOCK), v) } }
                }
                ChoiceRow("Fit", listOf("fill" to "Fill", "fit" to "Fit", "blur" to "Blur fill"), Store.fit(Target.LOCK)) { v ->
                    save("Fit: ${if (v == "blur") "blur fill" else v}") { Store.edit { putString(Keys.fit(Target.LOCK), v) } }
                }
            }
            Text(
                "Tip: your phone's own lock screen clock and notifications stay on top of these photos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 32.dp),
            )
        }
    }

    if (confirmSet) {
        ConfirmDialog(
            title = "Set lock screen now?",
            message = "The next photo from your Lock screen album will be set on your lock screen. Your Home screen isn't changed.",
            confirm = "Set now",
            onConfirm = {
                confirmSet = false
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { LockChanger.change(context, "set now") }
                    LockWorker.schedule(context)
                    if (ok) confirmTap()
                    snack(if (ok) "Lock screen updated. Lock your phone to see it." else "Couldn't set the lock screen. See Settings › Wallpaper check.")
                }
            },
            onDismiss = { confirmSet = false },
        )
    }
    if (custom) {
        CustomIntervalDialog(Store.interval(Target.LOCK).coerceAtLeast(15), allowZero = false, onSave = { m ->
            custom = false
            save("Lock screen changes every ${intervalLabel(m)}") { Store.edit { putInt(Keys.interval(Target.LOCK), m) } }
            LockWorker.schedule(context)
        }, onDismiss = { custom = false })
    }
}
