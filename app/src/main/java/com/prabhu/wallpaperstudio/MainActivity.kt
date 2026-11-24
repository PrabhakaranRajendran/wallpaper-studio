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
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.ui.CropScreen
import com.prabhu.wallpaperstudio.ui.HomeScreen
import com.prabhu.wallpaperstudio.ui.ParallaxScreen
import com.prabhu.wallpaperstudio.ui.PhotosScreen
import com.prabhu.wallpaperstudio.ui.SettingsScreen
import com.prabhu.wallpaperstudio.ui.StudioTheme
import com.prabhu.wallpaperstudio.ui.VideoScreen
import com.prabhu.wallpaperstudio.ui.isAppInDarkTheme
import com.prabhu.wallpaperstudio.update.Updater
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Video("Video", Icons.Rounded.Videocam),
    Photos("Photos", Icons.Rounded.PhotoLibrary),
    ThreeD("3D", Icons.Rounded.ViewInAr),
    Settings("Settings", Icons.Rounded.Tune),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            val dark = isAppInDarkTheme()
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            StudioTheme(dark) { App() }
        }
    }
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var photosTab by rememberSaveable { mutableIntStateOf(0) }
    var cropUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Automatic update check, at most every 6 hours.
    LaunchedEffect(Unit) {
        val last = Store.long(Keys.LAST_UPDATE_CHECK, 0L)
        if (Store.bool(Keys.AUTO_UPDATE, true) && System.currentTimeMillis() - last > 6 * 3_600_000L) {
            Updater.check(quiet = true)
        }
    }

    val crop = cropUri
    if (crop != null) {
        BackHandler { cropUri = null }
        CropScreen(uri = crop, onClose = { cropUri = null })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
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
                Tab.Home -> HomeScreen(onOpen = { tab = it })
                Tab.Video -> VideoScreen()
                Tab.Photos -> PhotosScreen(
                    selectedTab = photosTab,
                    onTabChange = { photosTab = it },
                    onCrop = { cropUri = it },
                )
                Tab.ThreeD -> ParallaxScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
    }

    UpdateDialog()
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
                if (rel.notes.isNotBlank()) {
                    Text(rel.notes.take(400), modifier = Modifier.padding(top = 12.dp))
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = progress == null,
                onClick = { scope.launch { Updater.downloadAndInstall(context, rel) } },
            ) { Text("Update now") }
        },
        dismissButton = {
            TextButton(onClick = { Updater.dialogDismissed = true }) { Text("Later") }
        },
    )
}
