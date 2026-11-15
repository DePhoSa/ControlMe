package de.controlme.app.client

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.controlme.app.core.Backend
import de.controlme.app.core.Notifications
import de.controlme.app.core.Prefs
import de.controlme.app.core.SessionState
import de.controlme.app.ui.AppTheme
import de.controlme.app.ui.BigButton
import kotlinx.coroutines.launch

/**
 * Wird gestartet, wenn ein Helfer verbinden möchte. Fragt (optional) nach und
 * öffnet dann den System-Dialog zur Bildschirmfreigabe.
 */
class CaptureActivity : ComponentActivity() {

    companion object {
        @Volatile var launchedFor: String? = null
        private const val EXTRA_DEVICE = "device"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_HELPER = "helper"
        private const val EXTRA_HELPER_UID = "helper_uid"

        fun intent(ctx: Context, deviceId: String, sessionId: String, helperUid: String, helperName: String) =
            Intent(ctx, CaptureActivity::class.java)
                .putExtra(EXTRA_DEVICE, deviceId)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_HELPER, helperName)
                .putExtra(EXTRA_HELPER_UID, helperUid)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    private lateinit var deviceId: String
    private lateinit var sessionId: String
    private lateinit var helperName: String
    private var handled = false

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            RemoteControlService.awaitConsentUntil = 0L
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                ScreenShareService.start(this, result.resultCode, data, deviceId, sessionId, helperName)
            } else {
                Backend.session(deviceId, sessionId).update("state", SessionState.DECLINED)
            }
            handled = true
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        deviceId = intent.getStringExtra(EXTRA_DEVICE) ?: return finish()
        sessionId = intent.getStringExtra(EXTRA_SESSION) ?: return finish()
        helperName = intent.getStringExtra(EXTRA_HELPER) ?: "Helfer"
        launchedFor = sessionId
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_REQUEST)

        if (savedInstanceState != null) return
        lifecycleScope.launch {
            val helperUid = intent.getStringExtra(EXTRA_HELPER_UID)
            if (!Prefs(this@CaptureActivity).mustAsk(helperUid)) {
                requestProjection()
            } else {
                setContent {
                    AppTheme {
                        AskScreen(
                            helperName = helperName,
                            onAllow = { requestProjection() },
                            onDeny = {
                                Backend.session(deviceId, sessionId).update("state", SessionState.DECLINED)
                                handled = true
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun requestProjection() {
        lifecycleScope.launch {
            Backend.session(deviceId, sessionId).update("state", SessionState.ACCEPTED)
            if (Prefs(this@CaptureActivity).autoConfirmNow()) {
                // Die Bedienungshilfe tippt den System-Dialog automatisch an (falls möglich).
                RemoteControlService.awaitConsentUntil = System.currentTimeMillis() + 15_000
            }
            val mpm = getSystemService(MediaProjectionManager::class.java)
            // Nur "ganzer Bildschirm" anbieten – sonst müsste man erst etwas auswählen.
            projectionLauncher.launch(
                mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AskScreen(helperName: String, onAllow: () -> Unit, onDeny: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.SupportAgent, null,
                Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "$helperName möchte dir helfen",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "$helperName sieht dann deinen Bildschirm und kann für dich tippen.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            BigButton("Ja, gerne", onAllow, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            BigButton("Nein", onDeny, Modifier.fillMaxWidth(), secondary = true)
        }
    }
}
