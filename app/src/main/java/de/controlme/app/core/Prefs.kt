package de.controlme.app.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class Role { CLIENT, HELPER }

const val HELPER_FAMILY = "family"
const val HELPER_FRIEND = "friend"
const val HELPER_PENDING = "pending"

private val Context.store by preferencesDataStore("controlme")

/** Kleine lokale Einstellungen. */
class Prefs(private val context: Context) {
    private object K {
        val role = stringPreferencesKey("role")
        val helperName = stringPreferencesKey("helper_name")
        val nicknames = stringPreferencesKey("nicknames")
        val autoAccept = booleanPreferencesKey("auto_accept")
        val autoConfirm = booleanPreferencesKey("auto_confirm")
        val keepClient = booleanPreferencesKey("keep_client")
        val helperModes = stringPreferencesKey("helper_modes")
    }

    val role: Flow<Role?> = context.store.data.map { p -> p[K.role]?.let { runCatching { Role.valueOf(it) }.getOrNull() } }
    val helperName: Flow<String> = context.store.data.map { it[K.helperName] ?: "" }
    val autoAccept: Flow<Boolean> = context.store.data.map { it[K.autoAccept] ?: true }
    val autoConfirm: Flow<Boolean> = context.store.data.map { it[K.autoConfirm] ?: true }
    val nicknames: Flow<Map<String, String>> = context.store.data.map { p -> parseMap(p[K.nicknames]) }

    /** Bleibt dieses Handy für seine Helfer erreichbar, obwohl gerade der Helfer-Modus aktiv ist? */
    val keepClient: Flow<Boolean> = context.store.data.map { it[K.keepClient] ?: false }

    /**
     * Wie darf ein Helfer verbinden? uid -> "family" (ohne Nachfrage) oder "friend" (jedes Mal fragen)
     * oder "pending" (gerade gekoppelt, Auswahl steht noch aus). Kein Eintrag = family.
     */
    val helperModes: Flow<Map<String, String>> = context.store.data.map { p -> parseMap(p[K.helperModes]) }

    suspend fun setHelperMode(helperUid: String, mode: String?) = edit {
        val m = parseMap(it[K.helperModes]).toMutableMap()
        if (mode == null) m.remove(helperUid) else m[helperUid] = mode
        it[K.helperModes] = JSONObject(m as Map<*, *>).toString()
    }

    /** Muss vor dem Verbinden mit diesem Helfer nachgefragt werden? */
    suspend fun mustAsk(helperUid: String?): Boolean {
        if (!autoAccept.first()) return true
        val mode = helperUid?.let { helperModes.first()[it] }
        return mode == HELPER_FRIEND || mode == HELPER_PENDING
    }

    suspend fun setRole(role: Role?) = edit { if (role == null) it.remove(K.role) else it[K.role] = role.name }
    suspend fun setHelperName(name: String) = edit { it[K.helperName] = name.trim() }
    suspend fun setAutoAccept(v: Boolean) = edit { it[K.autoAccept] = v }
    suspend fun setAutoConfirm(v: Boolean) = edit { it[K.autoConfirm] = v }
    suspend fun setKeepClient(v: Boolean) = edit { it[K.keepClient] = v }
    suspend fun setNickname(deviceId: String, name: String) = edit {
        val m = parseMap(it[K.nicknames]).toMutableMap()
        if (name.isBlank()) m.remove(deviceId) else m[deviceId] = name.trim()
        it[K.nicknames] = JSONObject(m as Map<*, *>).toString()
    }

    suspend fun roleNow() = role.first()

    /** Soll die Bereitschaft (ClientService) laufen? */
    suspend fun clientWantedNow() = roleNow() == Role.CLIENT || keepClient.first()
    suspend fun helperNameNow() = helperName.first()
    suspend fun autoAcceptNow() = autoAccept.first()
    suspend fun autoConfirmNow() = autoConfirm.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.store.edit { block(it) }
    }

    private fun parseMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }
}
