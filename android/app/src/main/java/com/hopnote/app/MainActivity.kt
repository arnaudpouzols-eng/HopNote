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
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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

class CaptureViewModel(private val dao: CaptureDao, private val syncer: NotionSyncer, private val retention: LocalRetention) : ViewModel() {
    val captures = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(text: String, source: CaptureSource, onSaved: (Capture) -> Unit = {}) = viewModelScope.launch {
        val cleaned = text.trim()
        if (cleaned.isNotEmpty()) {
            val capture = Capture(text = cleaned, source = source)
            dao.insert(capture)
            onSaved(capture)
            syncer.syncPending()
            cleanAutomatically()
        }
    }

    fun undo(capture: Capture) = viewModelScope.launch { dao.deleteById(capture.id) }
    fun cleanAll(onDone: (Int) -> Unit) = viewModelScope.launch { onDone(dao.deleteAll()) }
    fun retrySync() = viewModelScope.launch { syncer.syncPending(); cleanAutomatically() }
    fun performAutomaticCleanup() = viewModelScope.launch { cleanAutomatically() }

    private suspend fun cleanAutomatically() {
        if (retention.automaticCleanupEnabled()) dao.deleteSyncedBefore(System.currentTimeMillis() - 14 * 86_400_000L)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, HopNoteDatabase::class.java, "hopnote.db")
            .addMigrations(MIGRATION_1_2)
            .build()
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = CaptureViewModel(database.captures(), NotionSyncer(database.captures(), HopNoteSession(applicationContext)), LocalRetention(applicationContext)) as T
        })[CaptureViewModel::class.java]
        viewModel.performAutomaticCleanup()
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
    val retention = remember { LocalRetention(context) }
    var googleEmail by remember { mutableStateOf(google.email()) }
    val notionSession = remember { HopNoteSession(context) }
    var notionConnected by remember { mutableStateOf(false) }
    var notionError by remember { mutableStateOf<String?>(null) }
    var showCleanConfirmation by remember { mutableStateOf(false) }
    var cleanResult by remember { mutableStateOf<String?>(null) }
    var disconnectTarget by remember { mutableStateOf<String?>(null) }
    var showRetentionHelp by remember { mutableStateOf(false) }
    var automaticCleanup by remember { mutableStateOf(retention.automaticCleanupEnabled()) }
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
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connexions", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            ConnectionCard("Compte Google", googleEmail ?: "Compte de l’appareil", if (googleEmail != null) "" else "Connecter", googleEmail != null,
                secondaryAction = if (googleEmail != null) "Déconnecter" to {
                    disconnectTarget = "google"
                } else null
            ) {
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken("921418049789-ier4iualalt27prl0mrutlasvu82ipfk.apps.googleusercontent.com")
                    .build()
                googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
            }
            ConnectionCard("Compte Notion", "Synchronisation vers HopNote", if (notionConnected) "" else "Connecter", notionConnected,
                secondaryAction = if (notionConnected) "Déconnecter" to {
                    disconnectTarget = "notion"
                } else null,
                onClick = onNotionSetup
            )
            notionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mémoire locale", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(20.dp).border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape).clickable { showRetentionHelp = true }, contentAlignment = Alignment.Center) { Text("?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = automaticCleanup, onCheckedChange = { enabled -> automaticCleanup = enabled; retention.setAutomaticCleanupEnabled(enabled) })
                Text("Suppression automatique après 14 jours", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { showCleanConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Vider les notes locales") }
            cleanResult?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Text("Thème", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ThemeOption(AppTheme.ELECTRIC_BLUE, "Bleu", theme, onThemeChange)
                ThemeOption(AppTheme.INDUSTRIAL_AMBER, "Ambre", theme, onThemeChange)
                ThemeOption(AppTheme.LASER_RED, "Rouge", theme, onThemeChange)
            }
            ConnectionCard("Crédits & Dons", "HopNote v0.2.0 · Créé par Arnaud Pouzols", "Voir", null, onClick = onCredits)
        }
    }
    disconnectTarget?.let { target -> AlertDialog(
        onDismissRequest = { disconnectTarget = null },
        title = { Text("Déconnecter ${if (target == "google") "Google" else "Notion"} ?") },
        text = { Text(if (target == "google") "Ce compte ne sera plus associé à HopNote sur ce téléphone." else "HopNote ne pourra plus synchroniser tes captures. La page Notion et les notes déjà envoyées resteront intactes.") },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                onClick = {
                    disconnectTarget = null
                    if (target == "google") {
                        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
                        GoogleSignIn.getClient(context, options).signOut().addOnCompleteListener { google.clear(); googleEmail = null }
                    } else {
                        scope.launch {
                            HopNoteApi.disconnect(notionSession)
                                .onSuccess { notionSession.clear(); notionConnected = false; notionError = null }
                                .onFailure { notionError = "Impossible de déconnecter Notion." }
                        }
                    }
                }
            ) { Text("Déconnecter") }
        },
        dismissButton = { OutlinedButton(onClick = { disconnectTarget = null }) { Text("Annuler") } }
    ) }
    if (showRetentionHelp) AlertDialog(
        onDismissRequest = { showRetentionHelp = false },
        title = { Text("Mémoire locale") },
        text = { Text("Quand la suppression automatique est activée, HopNote supprime du téléphone les captures déjà synchronisées depuis plus de 14 jours. Cette vérification se fait à l’ouverture de l’app et après une synchronisation. Désactive-la pour conserver toutes tes captures locales. Le bouton « Vider les notes locales » supprime immédiatement tout le flux de l’app, mais jamais les notes déjà présentes dans Notion.") },
        confirmButton = { Button(onClick = { showRetentionHelp = false }) { Text("Compris") } }
    )
    if (showCleanConfirmation) AlertDialog(
        onDismissRequest = { showCleanConfirmation = false },
        title = { Text("Vider les notes locales ?") },
        text = { Text("Toutes les captures présentes dans HopNote seront supprimées de ce téléphone, y compris celles qui ne sont pas encore synchronisées. Les notes déjà envoyées dans Notion resteront intactes.") },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), onClick = {
                showCleanConfirmation = false
                viewModel.cleanAll { count ->
                    cleanResult = if (count == 0) "Aucune note locale à vider." else "$count note${if (count > 1) "s" else ""} supprimée${if (count > 1) "s" else ""} du téléphone."
                }
            }) { Text("Vider") }
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
private fun ThemeOption(option: AppTheme, name: String, selectedTheme: AppTheme, onThemeChange: (AppTheme) -> Unit) =
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onThemeChange(option) }.padding(4.dp)) {
        Box(
            Modifier.size(42.dp)
                .border(if (selectedTheme == option) 3.dp else 1.dp, if (selectedTheme == option) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                .padding(4.dp)
                .background(option.accentColor(), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) { if (selectedTheme == option) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
private fun ConnectionCard(name: String, description: String, status: String, connected: Boolean? = null, secondaryAction: Pair<String, () -> Unit>? = null, onClick: (() -> Unit)? = null) = Card(
    if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable { onClick() }
) {
    Box(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            secondaryAction?.let { Text(it.first, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { it.second() }.padding(8.dp)) }
        }
    }
    connected?.let { Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(10.dp).background(if (it) androidx.compose.ui.graphics.Color(0xFF32D583) else MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape)) }
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
