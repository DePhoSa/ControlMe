package de.controlme.app.helper

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.controlme.app.core.Backend
import de.controlme.app.core.Prefs
import de.controlme.app.ui.SuccessGreen
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** Name des Helfers beim ersten Start abfragen. */
@Composable
fun HelperNameScreen(onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Wie heißt du?", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Dieser Name wird auf dem anderen Handy angezeigt, z. B. „Sascha möchte dir helfen“.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Dein Name") }, singleLine = true)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onSave(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Weiter")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelperHome(
    helperName: String,
    onConnect: (RemoteDevice, String) -> Unit,
    onResetRole: () -> Unit,
    onSwitchToClient: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val uid by produceState<String?>(null) { value = runCatching { Backend.ensureSignedIn() }.getOrNull() }
    val devices by remember(uid) { uid?.let { Devices.observe(it) } ?: emptyFlow() }.collectAsState(initial = null)
    val nicknames by prefs.nicknames.collectAsState(initial = emptyMap())
    var showPair by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Fernhilfe") },
                scrollBehavior = scroll,
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menü") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Einladung teilen (z. B. WhatsApp)") },
                            onClick = { menu = false; shareInvite(ctx) },
                        )
                        DropdownMenuItem(
                            text = { Text("Selbst Hilfe bekommen") },
                            onClick = { menu = false; onSwitchToClient() },
                        )
                        DropdownMenuItem(text = { Text("App zurücksetzen") }, onClick = { menu = false; onResetRole() })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPair = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Handy hinzufügen") },
            )
        },
    ) { padding ->
        when {
            devices == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            devices!!.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Noch kein Handy gekoppelt", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Installiere die App auf dem anderen Handy, wähle „Ich möchte Hilfe bekommen“ und tippe hier auf „Handy hinzufügen“.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices!!, key = { it.id }) { d ->
                    val name = nicknames[d.id] ?: d.name
                    DeviceCard(
                        d, name,
                        onConnect = { onConnect(d, name) },
                        onRename = { scope.launch { prefs.setNickname(d.id, it) } },
                        onRemove = { scope.launch { runCatching { Devices.remove(d.id) } } },
                    )
                }
            }
        }
    }

    if (showPair) PairDialog(helperName) { showPair = false }
}

@Composable
private fun DeviceCard(d: RemoteDevice, name: String, onConnect: () -> Unit, onRename: (String) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val lastSeen = d.lastSeen?.toDate()?.time
    val recentlyOnline = lastSeen != null && System.currentTimeMillis() - lastSeen < 12 * 60 * 1000
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.PhoneAndroid, null, Modifier.padding(12.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (recentlyOnline) SuccessGreen else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(8.dp),
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                lastSeen == null -> d.model
                                recentlyOnline -> "Bereit · ${d.model}"
                                else -> "Zuletzt aktiv ${DateUtils.getRelativeTimeSpanString(lastSeen)} · ${d.model}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mehr") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menu = false; rename = true })
                        DropdownMenuItem(text = { Text("Entfernen") }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Verbinden") }
        }
    }
    if (rename) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Umbenennen") },
            text = { OutlinedTextField(text, { text = it.take(30) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(text); rename = false }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun PairDialog(helperName: String, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trigger by remember { mutableStateOf(0) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        busy = true
        error = null
        Devices.pair(code, helperName)
            .onSuccess { onDismiss() }
            .onFailure { error = it.message }
        busy = false
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Handy hinzufügen") },
        text = {
            Column {
                Text("Gib die 6-stellige Zahl ein, die auf dem anderen Handy angezeigt wird.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    code, { v -> code = v.filter { it.isDigit() }.take(6) },
                    label = { Text("Zahl") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                )
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Verbinde …")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { trigger++ }, enabled = code.length == 6 && !busy) { Text("Koppeln") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } },
    )
}

/** Play-Store-Link für den internen Test. Funktioniert nur für eingetragene Tester. */
private const val INVITE_LINK = "https://play.google.com/apps/internaltest/4700897120132257250"

/** Öffnet das Android-Teilen-Menü (WhatsApp, SMS, E-Mail …) mit dem Einladungslink. */
private fun shareInvite(ctx: android.content.Context) {
    val text = "Hallo! Ich lade dich zu „Fernhilfe“ ein – damit kann ich dir am Handy aus der Ferne helfen.\n\n" +
        "1. Diesen Link öffnen und „Tester werden“ antippen:\n$INVITE_LINK\n" +
        "2. Danach über den Link „Bei Google Play herunterladen“ die App installieren.\n\n" +
        "Wichtig: Am Handy muss das Google-Konto angemeldet sein, das ich dir genannt habe."
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, text)
    ctx.startActivity(
        android.content.Intent.createChooser(send, "Einladung teilen")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
