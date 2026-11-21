package de.youtschik.privat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Yellow = Color(0xFFFFD60A)
private val Grey = Color(0xFF1C1C1E)

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Yellow, onPrimary = Color.Black,
                    background = Color.Black, onBackground = Color.White,
                    surface = Grey, onSurface = Color.White
                )
            ) {
                Surface(Modifier.fillMaxSize().systemBarsPadding(), color = Color.Black) {
                    App(sharedUrl, onUrl = { sharedUrl = it })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        Regex("https?://\\S+").find(text)?.let {
            sharedUrl = it.value
            Engine.reset()
        }
        // Intent verbrauchen, damit er beim Neuaufbau der Activity nicht erneut gelesen wird
        setIntent(Intent(this, MainActivity::class.java))
    }
}

@Composable
private fun App(url: String, onUrl: (String) -> Unit) {
    var settings by remember { mutableStateOf(false) }
    BackHandler(settings) { settings = false }
    if (settings) SettingsScreen { settings = false } else MainScreen(url, onUrl) { settings = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(url: String, onUrl: (String) -> Unit, openSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = Engine.state
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }

    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        update = withContext(Dispatchers.IO) { runCatching { Updater.check() }.getOrNull() }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                onUrl("")
                Engine.reset()
                update = withContext(Dispatchers.IO) { runCatching { Updater.check() }.getOrNull() }
                delay(300)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Youtschik", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = openSettings) { Text("⚙", fontSize = 32.sp, color = Color.White) }
        }

        update?.let { u ->
            Button(
                onClick = {
                    updateBusy = true
                    scope.launch(Dispatchers.IO) {
                        Updater.checkAndInstall(ctx, {}, u)
                        updateBusy = false
                    }
                },
                enabled = !updateBusy,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                val p = Updater.progress
                val label = if (updateBusy) (if (p != null && p >= 0f) "Lade Update… ${(p * 100).toInt()} %" else "Lade Update…") else "Update installieren"
                Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            ProgressBar(Updater.progress)
        }

        OutlinedTextField(
            value = url,
            onValueChange = onUrl,
            placeholder = { Text("YouTube-Link") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        val running = state is DlState.Running
        Button(
            onClick = { Engine.start(ctx.applicationContext, url.trim()) },
            enabled = url.isNotBlank() && !running,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Yellow, contentColor = Color.Black,
                disabledContainerColor = Grey, disabledContentColor = Color.Gray
            )
        ) {
            val label = when {
                state is DlState.Running && state.progress >= 0f -> "${(state.progress * 100).toInt()} %"
                running -> "…"
                else -> "DOWNLOAD"
            }
            Text(label, fontSize = 40.sp, fontWeight = FontWeight.Black)
        }

        Box(Modifier.fillMaxWidth().heightIn(min = 90.dp), contentAlignment = Alignment.TopCenter) {
            when (state) {
                is DlState.Running -> if (state.progress >= 0f)
                    LinearProgressIndicator(progress = { state.progress }, Modifier.fillMaxWidth().height(8.dp), color = Yellow)
                else LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = Yellow)
                is DlState.Done -> Text("✓ " + state.text, fontSize = 18.sp, color = Color(0xFF30D158), textAlign = TextAlign.Center)
                is DlState.Error -> Text(state.text, fontSize = 16.sp, color = Color(0xFFFF453A), textAlign = TextAlign.Center)
                DlState.Idle -> {}
            }
        }
    }
    }
}

@Composable
private fun ProgressBar(p: Float?) {
    if (p == null) return
    if (p >= 0f) LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(8.dp), color = Yellow)
    else LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = Yellow)
}

@Composable
private fun SettingsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var pickKey by remember { mutableStateOf(Storage.KEY_PRIMARY) }
    var info by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Storage.set(ctx, pickKey, uri.toString())
            refresh++
        }
    }

    @Composable
    fun FolderButton(title: String, prefKey: String) {
        key(refresh) {
            Button(
                onClick = { pickKey = prefKey; picker.launch(null) },
                modifier = Modifier.fillMaxWidth().height(88.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Grey, contentColor = Color.White)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(title, fontSize = 14.sp, color = Color.Gray)
                    Text(Storage.folderLabel(ctx, prefKey), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("←", fontSize = 32.sp, color = Color.White) }
            Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        FolderButton("Standardordner", Storage.KEY_PRIMARY)
        FolderButton("Ersatzordner", Storage.KEY_FALLBACK)
        var auto by remember { mutableStateOf(Storage.autoExternal(ctx)) }
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Immer externen Datenträger nutzen", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = {
                auto = it
                Storage.setAutoExternal(ctx, it)
                if (it && !Storage.hasAllFilesAccess()) {
                    ctx.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }
            })
        }
        Text("Ohne gültigen Ordner: Music/YoutschikPrivat", fontSize = 14.sp, color = Color.Gray)

        var sponsor by remember { mutableStateOf(Storage.sponsorBlock(ctx)) }
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Werbung im Video entfernen", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = sponsor, onCheckedChange = { sponsor = it; Storage.setSponsorBlock(ctx, it) })
        }

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) { info = Updater.checkAndInstall(ctx, { info = it }) }
            },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Grey, contentColor = Color.White)
        ) { Text("Nach Update suchen", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        ProgressBar(Updater.progress)
        if (info.isNotEmpty()) Text(info, fontSize = 16.sp)
        Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 14.sp, color = Color.Gray)
    }
}
