package com.prabhu.wallpaperstudio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.ui.CropScreen
import com.prabhu.wallpaperstudio.ui.HomeScreen
import com.prabhu.wallpaperstudio.ui.LocalSnack
import com.prabhu.wallpaperstudio.ui.LockScreenTab
import com.prabhu.wallpaperstudio.ui.SettingsScreen
import com.prabhu.wallpaperstudio.ui.StudioTheme
import com.prabhu.wallpaperstudio.ui.ThemesScreen
import com.prabhu.wallpaperstudio.ui.WallpaperScreen
import com.prabhu.wallpaperstudio.ui.isAppInDarkTheme
import com.prabhu.wallpaperstudio.ui.rememberTap
import com.prabhu.wallpaperstudio.update.Updater
import com.prabhu.wallpaperstudio.wallpaper.LockWorker
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Wallpaper("Wallpaper", Icons.Rounded.Wallpaper),
    Lock("Lock screen", Icons.Rounded.Lock),
    Themes("Themes", Icons.Rounded.Palette),
    Settings("Settings", Icons.Rounded.Tune),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (BuildConfig.DEBUG) DebugSetup.apply(this, intent)
        LockWorker.schedule(this)
        val startTab = intent.getStringExtra("tab")?.let { name -> AppTab.entries.firstOrNull { it.name.equals(name, true) } }
        val startSub = intent.getIntExtra("sub", 0)
        setContent {
            val dark = isAppInDarkTheme()
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            StudioTheme(dark) { App(startTab ?: AppTab.Home, startSub) }
        }
    }
}

@Composable
private fun App(startTab: AppTab, startSub: Int) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var wallpaperSub by rememberSaveable { mutableIntStateOf(if (startTab == AppTab.Wallpaper) startSub else 0) }
    // Crop editor: key, source, isVideo, target
    var cropKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cropSource by rememberSaveable { mutableStateOf("") }
    var cropVideo by rememberSaveable { mutableStateOf(false) }
    var cropTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tap = rememberTap()
    val showSnack: (String) -> Unit = remember {
        { msg ->
            scope.launch {
                snackHost.currentSnackbarData?.dismiss()
                snackHost.showSnackbar(msg, duration = SnackbarDuration.Short)
            }
        }
    }

    // Automatic update check, at most every 6 hours.
    LaunchedEffect(Unit) {
        val last = Store.long(Keys.LAST_UPDATE_CHECK, 0L)
        if (Store.bool(Keys.AUTO_UPDATE, true) && System.currentTimeMillis() - last > 6 * 3_600_000L) {
            Updater.check(quiet = true)
        }
    }

    CompositionLocalProvider(LocalSnack provides showSnack) {
        val key = cropKey
        if (key != null) {
            BackHandler { cropKey = null }
            Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { pad ->
                Box(Modifier.padding(pad)) {
                    CropScreen(key = key, source = cropSource, isVideo = cropVideo, target = cropTarget, onClose = { cropKey = null })
                }
            }
            return@CompositionLocalProvider
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackHost) },
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                if (tab != t) tap()
                                tab = t
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    AppTab.Home -> HomeScreen(onOpen = { tab = it })
                    AppTab.Wallpaper -> WallpaperScreen(
                        subTab = wallpaperSub,
                        onSubTab = { wallpaperSub = it },
                        onCrop = { k, src, video ->
                            cropKey = k
                            cropSource = src
                            cropVideo = video
                            cropTarget = if (video) null else Target.HOME
                        },
                    )
                    AppTab.Lock -> LockScreenTab(onCrop = { p ->
                        cropKey = p
                        cropSource = p
                        cropVideo = false
                        cropTarget = Target.LOCK
                    })
                    AppTab.Themes -> ThemesScreen()
                    AppTab.Settings -> SettingsScreen()
                }
            }
        }
        UpdateDialog()
    }
}

@Composable
private fun UpdateDialog() {
    val rel = Updater.available ?: return
    if (Updater.dialogDismissed) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = Updater.downloadProgress
    AlertDialog(
        onDismissRequest = { if (progress == null) Updater.dialogDismissed = true },
        title = { Text("New version available") },
        text = {
            Column {
                Text("Version ${rel.version} is on GitHub. You have ${Updater.currentVersion}.")
                if (rel.notes.isNotBlank()) Text(rel.notes.take(400), modifier = Modifier.padding(top = 12.dp))
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 16.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = { scope.launch { Updater.downloadAndInstall(context, rel) } }) { Text("Update now") }
        },
        dismissButton = { TextButton(onClick = { Updater.dialogDismissed = true }) { Text("Later") } },
    )
}
