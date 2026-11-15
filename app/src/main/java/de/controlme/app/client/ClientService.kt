package de.controlme.app.client

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import de.controlme.app.MainActivity
import de.controlme.app.R
import de.controlme.app.core.Backend
import de.controlme.app.core.Notifications
import de.controlme.app.core.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Läuft dauerhaft (mit einer unauffälligen Benachrichtigung) auf dem Handy,
 * das Hilfe bekommt. Wartet auf Kopplungs- und Hilfe-Anfragen.
 */
class ClientService : LifecycleService() {

    data class PairCode(val code: String, val expiresAt: Long)

    companion object {
        private const val TAG = "ClientService"
        private const val ACTION_NEW_CODE = "new_code"
        private const val HEARTBEAT_MS = 5 * 60 * 1000L

        private val _pairCode = MutableStateFlow<PairCode?>(null)
        val pairCode: StateFlow<PairCode?> = _pairCode

        private val _helpers = MutableStateFlow<Map<String, String>>(emptyMap())
        /** Gekoppelte Helfer: uid -> Name */
        val helpers: StateFlow<Map<String, String>> = _helpers

        private val _online = MutableStateFlow(false)
        val online: StateFlow<Boolean> = _online

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ClientService::class.java))
        }

        fun requestNewPairCode(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, ClientService::class.java).setAction(ACTION_NEW_CODE)
            )
        }
    }

    private var deviceId: String? = null
    private val registrations = mutableListOf<ListenerRegistration>()
    private val handledSessions = mutableSetOf<String>()
    private var started = false

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        startForeground(
            Notifications.ID_CLIENT, statusNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!started) {
            started = true
            lifecycleScope.launch { connect() }
        }
        if (intent?.action == ACTION_NEW_CODE) {
            lifecycleScope.launch { createPairCode() }
        }
        return START_STICKY
    }

    private suspend fun connect() {
        val uid = retrying { Backend.ensureSignedIn() }
        deviceId = uid
        retrying { registerDevice(uid) }
        listenDevice(uid)
        listenClaims(uid)
        listenSessions(uid)
        _online.value = true
        // Lebenszeichen, damit der Helfer sieht, dass das Handy erreichbar ist.
        lifecycleScope.launch {
            while (isActive) {
                runCatching { Backend.device(uid).update("lastSeen", FieldValue.serverTimestamp()).await() }
                delay(HEARTBEAT_MS)
            }
        }
    }

    private suspend fun registerDevice(uid: String) {
        val ref = Backend.device(uid)
        val snap = ref.get().await()
        val name = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?: Build.MODEL
        if (!snap.exists()) {
            ref.set(
                mapOf(
                    "name" to name,
                    "model" to Build.MODEL,
                    "sdk" to Build.VERSION.SDK_INT,
                    "admins" to emptyList<String>(),
                    "adminNames" to emptyMap<String, String>(),
                    "lastSeen" to FieldValue.serverTimestamp(),
                )
            ).await()
            createPairCode()
        } else {
            ref.update(
                mapOf("name" to name, "model" to Build.MODEL, "sdk" to Build.VERSION.SDK_INT)
            ).await()
            @Suppress("UNCHECKED_CAST")
            val admins = snap.get("admins") as? List<String> ?: emptyList()
            if (admins.isEmpty()) createPairCode()
        }
    }

    private fun listenDevice(uid: String) {
        registrations += Backend.device(uid).addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val admins = snap?.get("admins") as? List<String> ?: return@addSnapshotListener
            @Suppress("UNCHECKED_CAST")
            val names = snap.get("adminNames") as? Map<String, String> ?: emptyMap()
            _helpers.value = admins.associateWith { names[it] ?: "Helfer" }
        }
    }

    /** Erzeugt einen neuen 6-stelligen Code, den der Helfer in seiner App eintippt. */
    private suspend fun createPairCode() {
        val uid = deviceId ?: return
        _pairCode.value?.let { old -> runCatching { Backend.pairCode(old.code).delete().await() } }
        val random = SecureRandom()
        repeat(5) {
            val code = (100000 + random.nextInt(900000)).toString()
            val expires = System.currentTimeMillis() + Backend.PAIR_CODE_VALIDITY_MS
            val ok = runCatching {
                Backend.pairCode(code).set(mapOf("deviceId" to uid, "expiresAt" to expires)).await()
            }.isSuccess
            if (ok) {
                _pairCode.value = PairCode(code, expires)
                return
            }
        }
    }

    private fun listenClaims(uid: String) {
        registrations += Backend.device(uid).collection("claims").addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "claims", err); return@addSnapshotListener }
            snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { change ->
                val doc = change.document
                val helperUid = doc.id
                val code = doc.getString("code")
                val name = doc.getString("name")?.take(40)?.ifBlank { null } ?: "Helfer"
                val current = _pairCode.value
                lifecycleScope.launch {
                    if (current != null && code == current.code && System.currentTimeMillis() < current.expiresAt) {
                        runCatching {
                            Backend.device(uid).update(
                                mapOf(
                                    "admins" to FieldValue.arrayUnion(helperUid),
                                    "adminNames.$helperUid" to name,
                                )
                            ).await()
                            Backend.pairCode(current.code).delete().await()
                            _pairCode.value = null
                            infoNotification("$name kann dir jetzt helfen.")
                        }.onFailure { Log.w(TAG, "pair", it) }
                    }
                    runCatching { doc.reference.delete().await() }
                }
            }
        }
    }

    private fun listenSessions(uid: String) {
        registrations += Backend.device(uid).collection("sessions")
            .whereEqualTo("state", SessionState.REQUESTED)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "sessions", err); return@addSnapshotListener }
                snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { change ->
                    val doc = change.document
                    if (!handledSessions.add(doc.id)) return@forEach
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    val helperUid = doc.getString("helperUid") ?: return@forEach
                    if (System.currentTimeMillis() - createdAt > Backend.SESSION_REQUEST_VALIDITY_MS ||
                        helperUid !in _helpers.value.keys
                    ) {
                        doc.reference.update("state", SessionState.ENDED)
                        return@forEach
                    }
                    val helperName = _helpers.value[helperUid] ?: "Helfer"
                    onHelpRequested(uid, doc.id, helperName)
                }
            }
    }

    private fun onHelpRequested(deviceId: String, sessionId: String, helperName: String) {
        val intent = CaptureActivity.intent(this, deviceId, sessionId, helperName)
        // Dank aktiver Bedienungshilfe darf die App direkt in den Vordergrund.
        runCatching { startActivity(intent) }
        lifecycleScope.launch {
            delay(4000)
            if (CaptureActivity.launchedFor != sessionId) {
                // Fallback: auffällige Benachrichtigung zum Antippen.
                val pi = PendingIntent.getActivity(
                    this@ClientService, sessionId.hashCode(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val n = NotificationCompat.Builder(this@ClientService, Notifications.CH_SESSION)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("$helperName möchte dir helfen")
                    .setContentText("Hier tippen, um den Bildschirm zu zeigen")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .setAutoCancel(true)
                    .setTimeoutAfter(Backend.SESSION_REQUEST_VALIDITY_MS)
                    .build()
                getSystemService(NotificationManager::class.java).notify(Notifications.ID_REQUEST, n)
            }
        }
    }

    private fun statusNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Notifications.CH_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fernhilfe ist bereit")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun infoNotification(text: String) {
        val n = NotificationCompat.Builder(this, Notifications.CH_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fernhilfe")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(Notifications.ID_INFO, n) }
    }

    private suspend fun <T> retrying(block: suspend () -> T): T {
        var wait = 2000L
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                Log.w(TAG, "retry", e)
                delay(wait)
                wait = (wait * 2).coerceAtMost(60_000)
            }
        }
    }

    override fun onDestroy() {
        registrations.forEach { it.remove() }
        _online.value = false
        super.onDestroy()
    }
}
