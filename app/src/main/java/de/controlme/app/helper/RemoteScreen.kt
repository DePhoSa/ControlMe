package de.controlme.app.helper

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.controlme.app.core.Rtc
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun RemoteScreen(session: HelperSession, deviceName: String, onClose: () -> Unit) {
    val status by session.status.collectAsState()
    val video by session.video.collectAsState()
    val control by session.controlAvailable.collectAsState()
    var frameRatio by remember { mutableStateOf<Float?>(null) }
    var showKeyboard by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    // Bildschirm des Helfers während der Sitzung anlassen
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { if (status is HelperSession.Status.Ended) onClose() else confirmEnd = true }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Kopfzeile
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(deviceName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (status) {
                        HelperSession.Status.Requesting -> "Anfrage wird gesendet …"
                        HelperSession.Status.WaitingForConsent -> "Warte auf Freigabe am Handy …"
                        HelperSession.Status.Connecting -> "Verbinde …"
                        HelperSession.Status.Connected -> if (control) "Verbunden" else "Nur Ansicht – Fernbedienung am Handy ausgeschaltet"
                        is HelperSession.Status.Ended -> "Getrennt"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledIconButton(
                onClick = { if (status is HelperSession.Status.Ended) onClose() else confirmEnd = true },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Icon(Icons.Rounded.CallEnd, "Beenden") }
        }

        // Bildschirm des anderen Handys
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val track = video
            val ended = status as? HelperSession.Status.Ended
            when {
                ended != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text(ended.message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onClose) { Text("Zurück") }
                }
                track == null -> CircularProgressIndicator(color = Color.White)
                else -> Box(Modifier.aspectRatio(frameRatio ?: (9f / 20f))) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                val main = Handler(Looper.getMainLooper())
                                init(Rtc.egl.eglBaseContext, object : RendererCommon.RendererEvents {
                                    override fun onFirstFrameRendered() {}
                                    override fun onFrameResolutionChanged(w: Int, h: Int, rotation: Int) {
                                        val (fw, fh) = if (rotation % 180 == 0) w to h else h to w
                                        if (fw > 0 && fh > 0) main.post { frameRatio = fw.toFloat() / fh }
                                    }
                                })
                                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                setEnableHardwareScaler(true)
                                track.addSink(this)
                            }
                        },
                        onRelease = { view ->
                            runCatching { track.removeSink(view) }
                            view.release()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Touch-Ebene: Tippen, lange drücken, wischen
                    Box(Modifier.fillMaxSize().pointerInput(session) { detectRemoteGestures(session) })
                }
            }
        }

        // Navigationsleiste
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavKey(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") { session.key("back") }
            NavKey(Icons.Rounded.Circle, "Startbildschirm") { session.key("home") }
            NavKey(Icons.Rounded.CropSquare, "Apps") { session.key("recents") }
            NavKey(Icons.Rounded.Notifications, "Benachrichtigungen") { session.key("notifications") }
            NavKey(Icons.Rounded.Keyboard, "Tastatur") { showKeyboard = true }
        }
    }

    if (showKeyboard) KeyboardSheet(session) { showKeyboard = false }

    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text("Fernhilfe beenden?") },
        confirmButton = { TextButton(onClick = { confirmEnd = false; session.stop(); onClose() }) { Text("Beenden") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Weiter helfen") } },
    )
}

@Composable
private fun NavKey(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) { Icon(icon, label) }
}

@Composable
private fun KeyboardSheet(session: HelperSession, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text eingeben") },
        text = {
            Column {
                Text(
                    "Tippe vorher auf dem anderen Handy in das Eingabefeld. Der Text wird dort angehängt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Text") })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { session.deleteChar() }) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Löschen") }
                    FilledTonalIconButton(onClick = { session.enter() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, "Eingabe") }
                    Surface(Modifier.weight(1f)) {}
                    FilledIconButton(onClick = { if (text.isNotEmpty()) session.text(text); text = "" }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Senden")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}

/** Übersetzt Fingerbewegungen in Gesten für das andere Handy (Koordinaten 0..1). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectRemoteGestures(session: HelperSession) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val start = down.uptimeMillis
        var end = start
        val points = mutableListOf(down.position)
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                end = change.uptimeMillis
                points += change.position
                break
            }
            if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            if (moved) points += change.position
            change.consume()
        }
        val duration = (end - start).coerceIn(40, 10_000)
        fun norm(o: Offset) = (o.x / size.width).coerceIn(0f, 1f) to (o.y / size.height).coerceIn(0f, 1f)
        if (!moved) {
            val (x, y) = norm(down.position)
            session.tap(x, y, duration)
        } else {
            val step = maxOf(1, points.size / 40)
            val sampled = points.filterIndexed { i, _ -> i % step == 0 } + points.last()
            session.path(sampled.map(::norm), duration)
        }
    }
}
