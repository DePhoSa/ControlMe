package de.controlme.app.helper

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import de.controlme.app.core.Backend
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class RemoteDevice(
    val id: String,
    val name: String,
    val model: String,
    val lastSeen: Timestamp?,
)

object Devices {

    /** Alle Handys, bei denen ich als Helfer eingetragen bin. */
    fun observe(uid: String): Flow<List<RemoteDevice>> = callbackFlow {
        val reg = Backend.db.collection("devices")
            .whereArrayContains("admins", uid)
            .addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                trySend(snap.documents.map {
                    RemoteDevice(
                        id = it.id,
                        name = it.getString("name") ?: "Handy",
                        model = it.getString("model") ?: "",
                        lastSeen = it.getTimestamp("lastSeen"),
                    )
                }.sortedBy { it.name })
            }
        awaitClose { reg.remove() }
    }

    /** Koppelt sich über den 6-stelligen Code mit einem Handy. */
    suspend fun pair(code: String, helperName: String): Result<String> = runCatching {
        val uid = Backend.ensureSignedIn()
        val snap = runCatching { Backend.pairCode(code).get().await() }.getOrNull()
        if (snap == null || !snap.exists()) error("Diese Zahl ist unbekannt. Bitte prüfen.")
        val deviceId = snap.getString("deviceId") ?: error("Ungültiger Code")
        if ((snap.getLong("expiresAt") ?: 0) < System.currentTimeMillis()) error("Die Zahl ist abgelaufen. Bitte eine neue anzeigen lassen.")
        Backend.device(deviceId).collection("claims").document(uid).set(
            mapOf("code" to code, "name" to helperName, "createdAt" to System.currentTimeMillis())
        ).await()
        // Warten, bis das andere Handy uns eingetragen hat.
        repeat(20) {
            delay(1500)
            val ok = runCatching { Backend.device(deviceId).get().await() }.getOrNull()
            @Suppress("UNCHECKED_CAST")
            if (ok != null && (ok.get("admins") as? List<String>)?.contains(uid) == true) return@runCatching deviceId
        }
        error("Das andere Handy hat nicht geantwortet. Ist die App dort geöffnet und online?")
    }

    suspend fun remove(deviceId: String) {
        val uid = Backend.ensureSignedIn()
        Backend.device(deviceId).update(
            mapOf("admins" to FieldValue.arrayRemove(uid), "adminNames.$uid" to FieldValue.delete())
        ).await()
    }
}
