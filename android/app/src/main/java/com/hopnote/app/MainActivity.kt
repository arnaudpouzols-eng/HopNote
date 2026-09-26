package com.hopnote.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CaptureViewModel(private val dao: CaptureDao, private val syncer: NotionSyncer) : ViewModel() {
    val captures = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(text: String, source: CaptureSource, onSaved: (Capture) -> Unit = {}) = viewModelScope.launch {
        val cleaned = text.trim()
        if (cleaned.isNotEmpty()) {
            val capture = Capture(text = cleaned, source = source)
            dao.insert(capture)
            onSaved(capture)
            syncer.syncPending()
        }
    }

    fun undo(capture: Capture) = viewModelScope.launch { dao.deleteById(capture.id) }
    fun cleanSynced(retentionDays: Int, onDone: (Int) -> Unit) = viewModelScope.launch {
        onDone(dao.deleteSyncedBefore(System.currentTimeMillis() - retentionDays * 86_400_000L))
    }
    fun retrySync() = viewModelScope.launch { syncer.syncPending() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, HopNoteDatabase::class.java, "hopnote.db")
            .addMigrations(MIGRATION_1_2)
            .build()
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = CaptureViewModel(database.captures(), NotionSyncer(database.captures(), HopNoteSession(applicationContext))) as T
        })[CaptureViewModel::class.java]
        enableEdgeToEdge()
        val preferences = getSharedPreferences("hopnote_preferences", MODE_PRIVATE)
        val initialTheme = runCatching {
            AppTheme.valueOf(preferences.getString("theme", AppTheme.ELECTRIC_BLUE.name)!!)
        }.getOrDefault(AppTheme.ELECTRIC_BLUE)
        setContent {
            var theme by remember { mutableStateOf(initialTheme) }
            HopNoteTheme(theme) {
                HopNoteApp(viewModel, theme, intent.getBooleanExtra(START_VOICE, false)) { selected ->
                    theme = selected
                    preferences.edit().putString("theme", selected.name).apply()
                }
            }
        }
    }

    companion object {
        const val START_VOICE = "com.hopnote.app.START_VOICE"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE captures ADD COLUMN syncedAt INTEGER")
                database.execSQL("ALTER TABLE captures ADD COLUMN notionBlockId TEXT")
            }
        }
    }
}

@Composable
private fun HopNoteApp(viewModel: CaptureViewModel, theme: AppTheme, startVoice: Boolean, onThemeChange: (AppTheme) -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var showNotionSetup by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    if (showCredits) CreditsScreen(onBack = { showCredits = false })
    else if (showSettings) SettingsScreen(viewModel, theme, onThemeChange, onNotionSetup = { showNotionSetup = true }, onCredits = { showCredits = true }, onBack = { showSettings = false })
    else HopNoteScreen(viewModel, startVoice, onSettings = { showSettings = true })
    if (showNotionSetup) NotionSetupDialog(onConnected = { viewModel.retrySync() }, onDismiss = { showNotionSetup = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HopNoteScreen(viewModel: CaptureViewModel, startVoice: Boolean, onSettings: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var recentCapture by remember { mutableStateOf<Capture?>(null) }
    var scrollToCaptureId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val serverSession = remember { HopNoteSession(context) }
    var notionReady by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val captureListState = rememberLazyListState()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spoken.isNullOrBlank()) voiceError = "Je n'ai pas compris. Réessaie quand tu veux."
        else viewModel.save(spoken, CaptureSource.VOICE) { recentCapture = it; scrollToCaptureId = it.id; voiceError = null }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.launch(voiceIntent()) else voiceError = "L'accès au micro est nécessaire pour dicter."
    }

    LaunchedEffect(Unit) {
        notionReady = HopNoteApi.connected(serverSession)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(startVoice) { if (startVoice && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.launch(voiceIntent()) }

    LaunchedEffect(recentCapture?.id) {
        if (recentCapture != null) {
            delay(5_000)
            recentCapture = null
        }
    }
    LaunchedEffect(captures, scrollToCaptureId) {
        if (scrollToCaptureId != null && captures.any { it.id == scrollToCaptureId }) {
            captureListState.animateScrollToItem(0)
            scrollToCaptureId = null
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_hopnote), null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("HopNote", fontWeight = FontWeight.Bold) } },
            actions = {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_notion), "Notion", modifier = Modifier.size(23.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
                    Box(Modifier.align(Alignment.BottomEnd).size(9.dp).background(if (notionReady) androidx.compose.ui.graphics.Color(0xFF32D583) else MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Réglages", tint = MaterialTheme.colorScheme.secondary) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Column(Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f).focusRequester(focusRequester), placeholder = { Text("Écrire une pensée…") }, minLines = 2, maxLines = 4)
                Spacer(Modifier.width(8.dp))
                FilledIconButton(modifier = Modifier.size(64.dp), onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.launch(voiceIntent())
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Default.Mic, "Dicter et enregistrer", tint = MaterialTheme.colorScheme.onPrimary) }
            }
            voiceError?.let { Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
            recentCapture?.let { capture ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Capturé", color = MaterialTheme.colorScheme.secondary)
                    OutlinedButton(onClick = { viewModel.undo(capture); recentCapture = null }) { Text("Annuler") }
                }
            }
            Button(onClick = { viewModel.save(text, CaptureSource.TEXT) { recentCapture = it; scrollToCaptureId = it.id }; text = ""; focusRequester.requestFocus(); keyboard?.show() }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), enabled = text.trim().isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Garder")
            }
        }
        if (captures.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Aucune capture.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), state = captureListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(captures, key = { it.id }) { CaptureCard(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: CaptureViewModel, theme: AppTheme, onThemeChange: (AppTheme) -> Unit, onNotionSetup: () -> Unit, onCredits: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val google = remember { GoogleAccount(context) }
    var googleEmail by remember { mutableStateOf(google.email()) }
    val notionSession = remember { HopNoteSession(context) }
    var notionConnected by remember { mutableStateOf(false) }
    var notionError by remember { mutableStateOf<String?>(null) }
    var showCleanConfirmation by remember { mutableStateOf(false) }
    var cleanResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { notionConnected = HopNoteApi.connected(notionSession) }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data).result?.email?.let { google.save(it); googleEmail = it }
    }
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(
            title = { Text("RÉGLAGES") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = MaterialTheme.colorScheme.secondary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connexions", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            ConnectionCard("Compte Google", googleEmail ?: "Compte de l’appareil", if (googleEmail != null) "Connecté" else "Connecter", googleEmail != null) {
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken("921418049789-ier4iualalt27prl0mrutlasvu82ipfk.apps.googleusercontent.com")
                    .build()
                googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
            }
            ConnectionCard("Compte Notion", "Synchronisation vers HopNote", if (notionConnected) "Connecté" else "Connecter", notionConnected, onNotionSetup)
            if (notionConnected) OutlinedButton(
                onClick = {
                    scope.launch {
                        HopNoteApi.disconnect(notionSession)
                            .onSuccess { notionSession.clear(); notionConnected = false; notionError = null }
                            .onFailure { notionError = "Impossible de déconnecter Notion." }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Déconnecter Notion") }
            notionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Text("Mémoire locale", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("Nettoyage local uniquement. Les notes restent dans Notion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { showCleanConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Nettoyer les notes synchronisées") }
            cleanResult?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.height(8.dp))
            Text("Thème", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            ThemeOption(AppTheme.ELECTRIC_BLUE, "Bleu électrique", "Le thème HopNote par défaut", theme, onThemeChange)
            ThemeOption(AppTheme.INDUSTRIAL_AMBER, "Ambre industriel", "Signal chaud et contrasté", theme, onThemeChange)
            ThemeOption(AppTheme.LASER_RED, "Rouge laser", "Signal intense et direct", theme, onThemeChange)
            ConnectionCard("Crédits", "HopNote v0.2.0 · Créé par Arnaud Pouzols", "Voir", null, onCredits)
        }
    }
    if (showCleanConfirmation) AlertDialog(
        onDismissRequest = { showCleanConfirmation = false },
        title = { Text("Nettoyer les captures ?") },
        text = { Text("Les captures synchronisées depuis plus de 14 jours seront supprimées de ce téléphone. Elles resteront dans Notion.") },
        confirmButton = {
            Button(onClick = {
                showCleanConfirmation = false
                viewModel.cleanSynced(14) { count ->
                    cleanResult = if (count == 0) "Aucune capture à nettoyer pour le moment." else "$count capture${if (count > 1) "s" else ""} supprimée${if (count > 1) "s" else ""} du téléphone."
                }
            }) { Text("Nettoyer") }
        },
        dismissButton = { OutlinedButton(onClick = { showCleanConfirmation = false }) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(title = { Text("CRÉDITS") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("HopNote", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Version 0.2.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Créé par Arnaud Pouzols", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("Si HopNote vous est utile et que vous souhaitez soutenir son développement, vous pouvez laisser un pourboire libre.", color = MaterialTheme.colorScheme.onSurface)
            Text("C’est totalement facultatif : HopNote reste identique pour tout le monde.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Soutenir HopNote sur Tipeee",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fr.tipeee.com/hopnote/"))) }.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ThemeOption(option: AppTheme, name: String, description: String, selectedTheme: AppTheme, onThemeChange: (AppTheme) -> Unit) = Card(
    Modifier.fillMaxWidth().clickable { onThemeChange(option) }
) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).background(option.accentColor()))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selectedTheme == option, onClick = { onThemeChange(option) })
    }
}

@Composable
private fun ConnectionCard(name: String, description: String, status: String, connected: Boolean? = null, onClick: (() -> Unit)? = null) = Card(
    if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable { onClick() }
) {
    Box(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
    }
    connected?.let { Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(10.dp).background(if (it) androidx.compose.ui.graphics.Color(0xFF32D583) else MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotionSetupDialog(onConnected: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = remember { HopNoteSession(context) }
    var status by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { if (HopNoteApi.connected(session)) status = "Connecté · page HopNote prête" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connecter Notion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Une seule autorisation. HopNote crée ensuite sa page automatiquement.")
            Button(
                enabled = !connecting,
                onClick = {
                    connecting = true
                    status = "Ouverture de Notion…"
                    scope.launch {
                        HopNoteApi.authorizationUrl(session)
                            .onSuccess { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); status = "Autorise HopNote dans Notion, puis reviens ici." }
                            .onFailure { error -> status = error.message ?: "Connexion impossible" }
                        connecting = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (connecting) "Connexion…" else "Connecter Notion") }
            OutlinedButton(onClick = {
                scope.launch {
                    status = if (HopNoteApi.connected(session)) { onConnected(); "Connecté · page HopNote prête" } else "Autorisation en attente. Termine-la dans Notion."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("J'ai autorisé HopNote") }
            status?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

private fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictez votre pensée")
}

@Composable
private fun CaptureCard(capture: Capture) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(capture.text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        val source = if (capture.source == CaptureSource.VOICE) "Voix" else "Texte"
        val state = when (capture.syncStatus) {
            SyncStatus.SYNCED -> "✓ Notion · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(capture.syncedAt!!))}"
            SyncStatus.FAILED -> "Échec de synchro"
            SyncStatus.SYNCING -> "Synchronisation…"
            SyncStatus.LOCAL_ONLY -> "À synchroniser"
        }
        Text("$source · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(capture.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}
