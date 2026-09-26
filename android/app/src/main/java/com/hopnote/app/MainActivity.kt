package com.hopnote.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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
    fun cleanSynced(retentionDays: Int) = viewModelScope.launch { dao.deleteSyncedBefore(System.currentTimeMillis() - retentionDays * 86_400_000L) }
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
            override fun <T : ViewModel> create(modelClass: Class<T>) = CaptureViewModel(database.captures(), NotionSyncer(database.captures(), NotionPreferences(applicationContext))) as T
        })[CaptureViewModel::class.java]
        enableEdgeToEdge()
        val preferences = getSharedPreferences("hopnote_preferences", MODE_PRIVATE)
        val initialTheme = runCatching {
            AppTheme.valueOf(preferences.getString("theme", AppTheme.ELECTRIC_BLUE.name)!!)
        }.getOrDefault(AppTheme.ELECTRIC_BLUE)
        setContent {
            var theme by remember { mutableStateOf(initialTheme) }
            HopNoteTheme(theme) {
                HopNoteApp(viewModel, theme) { selected ->
                    theme = selected
                    preferences.edit().putString("theme", selected.name).apply()
                }
            }
        }
    }

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE captures ADD COLUMN syncedAt INTEGER")
                database.execSQL("ALTER TABLE captures ADD COLUMN notionBlockId TEXT")
            }
        }
    }
}

@Composable
private fun HopNoteApp(viewModel: CaptureViewModel, theme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var showNotionSetup by remember { mutableStateOf(false) }
    if (showSettings) SettingsScreen(viewModel, theme, onThemeChange, onNotionSetup = { showNotionSetup = true }, onBack = { showSettings = false })
    else HopNoteScreen(viewModel, onSettings = { showSettings = true })
    if (showNotionSetup) NotionSetupDialog(onConnected = { viewModel.retrySync() }, onDismiss = { showNotionSetup = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HopNoteScreen(viewModel: CaptureViewModel, onSettings: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var recentCapture by remember { mutableStateOf<Capture?>(null) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spoken.isNullOrBlank()) voiceError = "Je n'ai pas compris. Réessaie quand tu veux."
        else viewModel.save(spoken, CaptureSource.VOICE) { recentCapture = it; voiceError = null }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.launch(voiceIntent()) else voiceError = "L'accès au micro est nécessaire pour dicter."
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(recentCapture?.id) {
        if (recentCapture != null) {
            delay(5_000)
            recentCapture = null
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(
            title = { Text("HopNote", fontWeight = FontWeight.Bold) },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Réglages", tint = MaterialTheme.colorScheme.secondary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
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
            Button(onClick = { viewModel.save(text, CaptureSource.TEXT) { recentCapture = it }; text = ""; focusRequester.requestFocus(); keyboard?.show() }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), enabled = text.trim().isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Garder")
            }
        }
        if (captures.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Aucune capture.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(captures, key = { it.id }) { CaptureCard(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: CaptureViewModel, theme: AppTheme, onThemeChange: (AppTheme) -> Unit, onNotionSetup: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(
            title = { Text("RÉGLAGES") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = MaterialTheme.colorScheme.secondary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connexions", style = MaterialTheme.typography.headlineSmall)
            Text("La capture reste toujours locale et instantanée. Les connexions seront ajoutées sans modifier ce geste.")
            ConnectionCard("Compte Google", "Sauvegarde de tes captures", "Prévu en v0.2")
            ConnectionCard("Notion", "Copie unidirectionnelle vers une page HopNote", "Configurer", onNotionSetup)
            Spacer(Modifier.height(8.dp))
            Text("Mémoire locale", style = MaterialTheme.typography.headlineSmall)
            Text("Les notes synchronisées depuis plus de 14 jours peuvent être retirées du téléphone. Notion n'est jamais modifié.")
            OutlinedButton(onClick = { viewModel.cleanSynced(14) }, modifier = Modifier.fillMaxWidth()) { Text("Nettoyer les notes synchronisées") }
            Spacer(Modifier.height(8.dp))
            Text("Thème", style = MaterialTheme.typography.headlineSmall)
            ThemeOption(AppTheme.ELECTRIC_BLUE, "Bleu électrique", "Le thème HopNote par défaut", theme, onThemeChange)
            ThemeOption(AppTheme.INDUSTRIAL_AMBER, "Ambre industriel", "Signal chaud et contrasté", theme, onThemeChange)
            ThemeOption(AppTheme.LASER_RED, "Rouge laser", "Signal intense et direct", theme, onThemeChange)
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
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        RadioButton(selected = selectedTheme == option, onClick = { onThemeChange(option) })
    }
}

@Composable
private fun ConnectionCard(name: String, description: String, status: String, onClick: (() -> Unit)? = null) = Card(
    if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable { onClick() }
) {
    Column(Modifier.padding(16.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
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
