package de.controlme.app.core

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Datenmodell in Firestore (kostenloser Spark-Tarif reicht völlig):
 *
 *  devices/{deviceUid}                       – das Handy, das Hilfe bekommt
 *      name, model, admins[uid], adminNames{uid:name}, lastSeen
 *  devices/{deviceUid}/claims/{helperUid}    – Kopplungs-Anfrage eines Helfers (code, name)
 *  devices/{deviceUid}/sessions/{sessionId}  – eine Fernhilfe-Sitzung (state, offer, answer …)
 *      …/clientIce, …/helperIce              – WebRTC-Verbindungskandidaten
 *  pairCodes/{code}                          – 6-stelliger Code -> deviceUid (10 Minuten gültig)
 */
object Backend {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** Anonyme Anmeldung – niemand muss ein Konto anlegen oder ein Passwort kennen. */
    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    val uid: String? get() = auth.currentUser?.uid

    fun device(id: String): DocumentReference = db.collection("devices").document(id)
    fun session(deviceId: String, sessionId: String): DocumentReference =
        device(deviceId).collection("sessions").document(sessionId)
    fun pairCode(code: String): DocumentReference = db.collection("pairCodes").document(code)

    const val PAIR_CODE_VALIDITY_MS = 10 * 60 * 1000L
    const val SESSION_REQUEST_VALIDITY_MS = 2 * 60 * 1000L
}

/** Sitzungszustände (Feld "state" im Session-Dokument). */
object SessionState {
    const val REQUESTED = "requested"   // Helfer möchte verbinden
    const val ACCEPTED = "accepted"     // Handy fragt gerade die Bildschirmfreigabe an
    const val OFFERED = "offered"       // Handy hat ein WebRTC-Angebot geschickt
    const val CONNECTED = "connected"   // Helfer hat geantwortet
    const val DECLINED = "declined"     // Freigabe abgelehnt
    const val ENDED = "ended"
}
