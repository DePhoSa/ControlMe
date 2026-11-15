package de.controlme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.controlme.app.client.ClientHome
import de.controlme.app.client.ClientService
import de.controlme.app.core.Prefs
import de.controlme.app.core.Role
import de.controlme.app.helper.HelperHome
import de.controlme.app.helper.HelperNameScreen
import de.controlme.app.helper.HelperSession
import de.controlme.app.helper.RemoteScreen
import de.controlme.app.ui.AppTheme
import de.controlme.app.ui.RoleScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Laufende Sitzung überlebt Bildschirmdrehungen. */
    private var session by mutableStateOf<Pair<HelperSession, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        @Suppress("DEPRECATION")
        val retained = lastCustomNonConfigurationInstance as? Pair<*, *>
        @Suppress("UNCHECKED_CAST")
        if (retained != null) session = retained as Pair<HelperSession, String>

        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val loaded by remember { prefs.role.map { it to true } }.collectAsState(initial = null to false)
                    val helperName by prefs.helperName.collectAsState(initial = "")
                    val scope = rememberCoroutineScope()
                    val (role, ready) = loaded
                    if (!ready) return@Surface Box(Modifier.fillMaxSize())

                    val resetRole: () -> Unit = {
                        scope.launch {
                            stopService(android.content.Intent(this@MainActivity, ClientService::class.java))
                            prefs.setKeepClient(false)
                            prefs.setRole(null)
                        }
                    }
                    // Moduswechsel ohne Zurücksetzen: Kopplungen bleiben erhalten.
                    // Ein Handy, das Hilfe bekommt, bleibt auch im Helfer-Modus erreichbar.
                    val switchToHelper: () -> Unit = {
                        scope.launch {
                            prefs.setKeepClient(true)
                            prefs.setRole(Role.HELPER)
                        }
                    }
                    val switchToClient: () -> Unit = {
                        scope.launch { prefs.setRole(Role.CLIENT) }
                    }
                    when (role) {
                        null -> RoleScreen { picked -> scope.launch { prefs.setRole(picked) } }
                        Role.CLIENT -> ClientHome(onResetRole = resetRole, onSwitchToHelper = switchToHelper)
                        Role.HELPER -> {
                            val current = session
                            when {
                                helperName.isBlank() -> HelperNameScreen { scope.launch { prefs.setHelperName(it) } }
                                current != null -> RemoteScreen(current.first, current.second) {
                                    current.first.stop()
                                    session = null
                                }
                                else -> HelperHome(
                                    helperName = helperName,
                                    onConnect = { device, name ->
                                        val s = HelperSession(applicationContext, device.id, helperName, App.scope)
                                        session = s to name
                                        s.start()
                                    },
                                    onResetRole = resetRole,
                                    onSwitchToClient = switchToClient,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Einfachste Möglichkeit, die Sitzung über eine Drehung zu retten")
    override fun onRetainCustomNonConfigurationInstance(): Any? = session

    override fun onDestroy() {
        if (!isChangingConfigurations) session?.first?.stop()
        super.onDestroy()
    }
}
