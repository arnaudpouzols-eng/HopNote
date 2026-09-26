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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CaptureViewModel(private val dao: CaptureDao) : ViewModel() {
    val captures = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(text: String, source: CaptureSource, onSaved: (Capture) -> Unit = {}) = viewModelScope.launch {
        val cleaned = text.trim()
        if (cleaned.isNotEmpty()) {
            val capture = Capture(text = cleaned, source = source)
            dao.insert(capture)
            onSaved(capture)
        }
    }

    fun undo(capture: Capture) = viewModelScope.launch { dao.deleteById(capture.id) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, HopNoteDatabase::class.java, "hopnote.db").build()
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = CaptureViewModel(database.captures()) as T
        })[CaptureViewModel::class.java]
        enableEdgeToEdge()
        setContent { MaterialTheme { HopNoteApp(viewModel) } }
    }
}

@Composable
private fun HopNoteApp(viewModel: CaptureViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) SettingsScreen(onBack = { showSettings = false })
    else HopNoteScreen(viewModel, onSettings = { showSettings = true })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HopNoteScreen(viewModel: CaptureViewModel, onSettings: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var lastVoiceCapture by remember { mutableStateOf<Capture?>(null) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spoken.isNullOrBlank()) voiceError = "Je n'ai pas compris. Réessaie quand tu veux."
        else viewModel.save(spoken, CaptureSource.VOICE) { lastVoiceCapture = it; voiceError = null }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.launch(voiceIntent()) else voiceError = "L'accès au micro est nécessaire pour dicter."
    }

    LaunchedEffect(lastVoiceCapture?.id) {
        if (lastVoiceCapture != null) {
            delay(5_000)
            lastVoiceCapture = null
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(
            title = { Column { Text("HopNote", fontWeight = FontWeight.Bold); Text("Capturer d'abord", style = MaterialTheme.typography.labelMedium) } },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Réglages") } }
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Une idée ? Garde-la.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Écrire une pensée…") }, minLines = 2, maxLines = 4)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.launch(voiceIntent())
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Default.Mic, "Dicter et enregistrer") }
            }
            voiceError?.let { Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
            lastVoiceCapture?.let { capture ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Capturé", color = MaterialTheme.colorScheme.secondary)
                    OutlinedButton(onClick = { viewModel.undo(capture); lastVoiceCapture = null }) { Text("Annuler") }
                }
            }
            Button(onClick = { viewModel.save(text, CaptureSource.TEXT); text = ""; focusManager.clearFocus() }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), enabled = text.trim().isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Garder")
            }
            Text("Mes captures", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (captures.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Encore vide.", style = MaterialTheme.typography.titleLarge)
            Text("Écris ou dicte la première pensée qui passe.")
        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(captures, key = { it.id }) { CaptureCard(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(
            title = { Text("Réglages") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connexions", style = MaterialTheme.typography.headlineSmall)
            Text("La capture reste toujours locale et instantanée. Les connexions seront ajoutées sans modifier ce geste.")
            ConnectionCard("Compte Google", "Sauvegarde de tes captures", "Prévu en v0.2")
            ConnectionCard("Notion", "Copie unidirectionnelle vers une page HopNote", "Prévu en v0.3")
        }
    }
}

@Composable
private fun ConnectionCard(name: String, description: String, status: String) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
    }
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
        Text("$source · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(capture.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}
