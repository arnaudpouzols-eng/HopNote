package com.hopnote.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CaptureViewModel(private val dao: CaptureDao) : ViewModel() {
    val captures = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(text: String, source: CaptureSource) = viewModelScope.launch {
        val cleaned = text.trim()
        if (cleaned.isNotEmpty()) dao.insert(Capture(text = cleaned, source = source))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, HopNoteDatabase::class.java, "hopnote.db").build()
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = CaptureViewModel(database.captures()) as T
        })[CaptureViewModel::class.java]
        enableEdgeToEdge()
        setContent { MaterialTheme { HopNoteScreen(viewModel) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HopNoteScreen(viewModel: CaptureViewModel) {
    var text by remember { mutableStateOf("") }
    var voiceText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.launch(voiceIntent()) else voiceText = "Autorisation micro refusée."
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        TopAppBar(title = {
            Column {
                Text("HopNote", fontWeight = FontWeight.Bold)
                Text("Capturer d'abord", style = MaterialTheme.typography.labelMedium)
            }
        })
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Une idée ? Garde-la.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f).focusRequester(focusRequester), placeholder = { Text("Écrire une pensée…") }, singleLine = false, maxLines = 4)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.launch(voiceIntent()) else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Default.Mic, "Dicter une capture") }
            }
            voiceText?.let { spoken ->
                Text(if (spoken == "Autorisation micro refusée.") spoken else "Dicté : $spoken", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.secondary)
                if (spoken != "Autorisation micro refusée.") Button(onClick = { viewModel.save(spoken, CaptureSource.VOICE); voiceText = null }) { Text("Garder cette capture") }
            }
            Button(onClick = { viewModel.save(text, CaptureSource.TEXT); text = ""; focusManager.clearFocus() }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), enabled = text.trim().isNotEmpty()) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Garder") }
            Text("Mes captures", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (captures.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text("Encore vide.", style = MaterialTheme.typography.titleLarge); Text("Écris ou dicte la première pensée qui passe.") }
        else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(captures, key = { it.id }) { CaptureCard(it) } }
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
