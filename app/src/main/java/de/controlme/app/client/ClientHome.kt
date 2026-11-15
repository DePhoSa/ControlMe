package de.controlme.app.client

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.controlme.app.core.Prefs
import de.controlme.app.ui.BigButton
import de.controlme.app.ui.SuccessGreen
import kotlinx.coroutines.launch

/** Startbildschirm auf dem Handy, das Hilfe bekommt – bewusst groß und einfach. */
@Composable
fun ClientHome(onResetRole: () -> Unit, onSwitchToHelper: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        ClientService.start(ctx)
        onPauseOrDispose { }
    }
    // Freigaben bei jedem Zurückkehren in die App neu prüfen
    val notif = remember(refresh) { Permissions.notificationsGranted(ctx) }
    val a11y = remember(refresh) { Permissions.accessibilityEnabled(ctx) }
    val battery = remember(refresh) { Permissions.batteryUnrestricted(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val helpers by ClientService.helpers.collectAsState()
    val code by ClientService.pairCode.collectAsState()
    val online by ClientService.online.collectAsState()
    val active by ScreenShareService.activeHelper.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            when {
                active != null -> ActiveSession(active!!) { ScreenShareService.stop(ctx) }

                !(notif && a11y && battery) -> SetupSteps(
                    notif = notif, a11y = a11y, battery = battery,
                    onNotif = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onA11y = { Permissions.openAccessibilitySettings(ctx) },
                    onAppInfo = { Permissions.openAppInfo(ctx) },
                    onBattery = { Permissions.requestBatteryUnrestricted(ctx) },
                )

                helpers.isEmpty() || showCode -> PairCodeView(
                    code = code?.code,
                    first = helpers.isEmpty(),
                    onNewCode = { ClientService.requestNewPairCode(ctx) },
                    onDone = if (helpers.isNotEmpty()) ({ showCode = false }) else null,
                )

                else -> Ready(helpers.values.toList(), online)
            }
        }
        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) { Icon(Icons.Rounded.Settings, "Einstellungen", tint = MaterialTheme.colorScheme.outline) }
    }

    if (showSettings) ClientSettings(
        onDismiss = { showSettings = false },
        onAddHelper = { showSettings = false; showCode = true; ClientService.requestNewPairCode(ctx) },
        onResetRole = onResetRole,
        onSwitchToHelper = { showSettings = false; onSwitchToHelper() },
    )
}

@Composable
private fun Ready(helpers: List<String>, online: Boolean) {
    Icon(
        if (online) Icons.Rounded.CheckCircle else Icons.Rounded.CloudOff, null,
        Modifier.size(160.dp),
        tint = if (online) SuccessGreen else MaterialTheme.colorScheme.outline,
    )
    Spacer(Modifier.height(32.dp))
    Text(
        if (online) "Alles bereit" else "Verbinde …",
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "${helpers.joinToString(" und ")} ${if (helpers.size == 1) "kann" else "können"} dir jetzt jederzeit helfen.\n\nDu musst nichts weiter tun.",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActiveSession(helper: String, onStop: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Icon(Icons.AutoMirrored.Rounded.ScreenShare, null, Modifier.padding(32.dp).size(96.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Spacer(Modifier.height(32.dp))
    Text("$helper hilft dir gerade", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text(
        "$helper sieht deinen Bildschirm und kann für dich tippen.",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(40.dp))
    BigButton("Hilfe beenden", onStop, Modifier.fillMaxWidth(), danger = true)
}

@Composable
private fun PairCodeView(code: String?, first: Boolean, onNewCode: () -> Unit, onDone: (() -> Unit)?) {
    Text(
        if (first) "Fast geschafft!" else "Weiteren Helfer hinzufügen",
        style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Sage diese Zahl der Person, die dir helfen soll:",
        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            code?.chunked(3)?.joinToString(" ") ?: "…",
            Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
            fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "Die Zahl ist 10 Minuten gültig.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    TextButton(onClick = onNewCode) { Text("Neue Zahl anzeigen", fontSize = 18.sp) }
    if (onDone != null) TextButton(onClick = onDone) { Text("Fertig", fontSize = 18.sp) }
}

@Composable
private fun SetupSteps(
    notif: Boolean, a11y: Boolean, battery: Boolean,
    onNotif: () -> Unit, onA11y: () -> Unit, onAppInfo: () -> Unit, onBattery: () -> Unit,
) {
    Text("Einrichtung", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Nur einmal nötig. Tippe auf die blauen Knöpfe.",
        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Step(1, "Benachrichtigungen erlauben", "Damit du siehst, wenn jemand hilft.", notif, "Erlauben", onNotif)
    Step(
        2, "Fernbedienung einschalten",
        "Im nächsten Bildschirm „Fernhilfe“ antippen und den Schalter einschalten.",
        a11y, "Einschalten", onA11y,
        hint = "Ist der Schalter grau? Dann zuerst „App-Info“ öffnen, oben rechts auf ⋮ tippen und „Eingeschränkte Einstellungen zulassen“ wählen.",
        hintAction = "App-Info öffnen" to onAppInfo,
    )
    Step(3, "Immer erreichbar sein", "Damit die Hilfe auch funktioniert, wenn das Handy länger liegt.", battery, "Erlauben", onBattery)
}

@Composable
private fun Step(
    number: Int, title: String, text: String, done: Boolean, action: String, onClick: () -> Unit,
    hint: String? = null, hintAction: Pair<String, () -> Unit>? = null,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(40.dp), tint = SuccessGreen)
                } else {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (!done) {
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                BigButton(action, onClick, Modifier.fillMaxWidth())
                if (hint != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    hintAction?.let { (label, act) -> TextButton(onClick = act) { Text(label) } }
                }
            }
        }
    }
}

@Composable
private fun ClientSettings(
    onDismiss: () -> Unit,
    onAddHelper: () -> Unit,
    onResetRole: () -> Unit,
    onSwitchToHelper: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val autoAccept by prefs.autoAccept.collectAsState(initial = true)
    val autoConfirm by prefs.autoConfirm.collectAsState(initial = true)
    var confirmReset by remember { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Einstellungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ListItem(
                    headlineContent = { Text("Vor dem Verbinden fragen") },
                    supportingContent = { Text("Zeigt „Ja/Nein“, bevor jemand zuschauen darf.") },
                    trailingContent = { Switch(!autoAccept, { scope.launch { prefs.setAutoAccept(!it) } }) },
                )
                ListItem(
                    headlineContent = { Text("Freigabe automatisch bestätigen") },
                    supportingContent = { Text("Tippt den Android-Dialog „Bildschirm teilen“ selbst an (experimentell).") },
                    trailingContent = { Switch(autoConfirm, { scope.launch { prefs.setAutoConfirm(it) } }) },
                )
                HorizontalDivider()
                TextButton(onClick = onAddHelper) { Text("Weiteren Helfer hinzufügen") }
                TextButton(onClick = { confirmSwitch = true }) { Text("Selbst jemandem helfen") }
                TextButton(onClick = { confirmReset = true }) {
                    Text("App zurücksetzen", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Wirklich zurücksetzen?") },
        text = { Text("Danach muss die App neu eingerichtet werden.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; onDismiss(); onResetRole() }) { Text("Zurücksetzen") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
    )
    if (confirmSwitch) AlertDialog(
        onDismissRequest = { confirmSwitch = false },
        title = { Text("Selbst jemandem helfen?") },
        text = {
            Text(
                "Die App wechselt in den Helfer-Modus. Deine Helfer können dir trotzdem weiterhin helfen.\n\n" +
                    "Zurück geht es im Helfer-Modus oben rechts über ⋮ → „Selbst Hilfe bekommen“."
            )
        },
        confirmButton = { TextButton(onClick = { confirmSwitch = false; onSwitchToHelper() }) { Text("Wechseln") } },
        dismissButton = { TextButton(onClick = { confirmSwitch = false }) { Text("Abbrechen") } },
    )
}
