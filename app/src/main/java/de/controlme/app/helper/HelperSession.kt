package de.controlme.app.helper

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import de.controlme.app.core.Backend
import de.controlme.app.core.PcObserver
import de.controlme.app.core.Rtc
import de.controlme.app.core.SessionState
import de.controlme.app.core.createAnswerAsync
import de.controlme.app.core.setLocalAsync
import de.controlme.app.core.setRemoteAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Eine Fernhilfe-Sitzung aus Sicht des Helfers. */
class HelperSession(
    private val context: Context,
    val deviceId: String,
    private val helperName: String,
    private val scope: CoroutineScope,
) {
    sealed interface Status {
        data object Requesting : Status
        data object WaitingForConsent : Status
        data object Connecting : Status
        data object Connected : Status
        data class Ended(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Requesting)
    val status: StateFlow<Status> = _status

    private val _video = MutableStateFlow<VideoTrack?>(null)
    val video: StateFlow<VideoTrack?> = _video

    private val _controlAvailable = MutableStateFlow(true)
    val controlAvailable: StateFlow<Boolean> = _controlAvailable

    private var sessionId: String? = null
    private var pc: PeerConnection? = null
    private var dc: DataChannel? = null
    private val registrations = mutableListOf<ListenerRegistration>()
    private var timeoutJob: Job? = null
    private var answering = false
    private var closed = false

    fun start() = scope.launch {
        try {
            val uid = Backend.ensureSignedIn()
            val ref = Backend.device(deviceId).collection("sessions").document()
            sessionId = ref.id
            ref.set(
                mapOf(
                    "state" to SessionState.REQUESTED,
                    "helperUid" to uid,
                    "helperName" to helperName,
                    "createdAt" to System.currentTimeMillis(),
                )
            ).await()
            registrations += ref.addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                when (snap.getString("state")) {
                    SessionState.ACCEPTED -> setStatus(Status.WaitingForConsent)
                    SessionState.OFFERED -> {
                        val offer = Rtc.sdpFromMap(snap.get("offer") as? Map<*, *>)
                        if (offer != null && !answering) {
                            answering = true
                            setStatus(Status.Connecting)
                            scope.launch { answer(ref.id, offer) }
                        }
                    }
                    SessionState.DECLINED -> finish("Die Freigabe wurde abgelehnt.", notifyRemote = false)
                    SessionState.ENDED -> finish("Die Verbindung wurde beendet.", notifyRemote = false)
                }
            }
            timeoutJob = scope.launch {
                delay(45_000)
                if (_status.value == Status.Requesting) {
                    finish("Das Handy antwortet nicht. Ist es eingeschaltet und mit dem Internet verbunden?", true)
                } else if (_status.value != Status.Connected) {
                    delay(45_000)
                    if (_status.value != Status.Connected && _status.value !is Status.Ended) {
                        finish("Die Verbindung konnte nicht aufgebaut werden.", true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HelperSession", "start", e)
            finish("Fehler: ${e.localizedMessage}", notifyRemote = false)
        }
    }

    private suspend fun answer(sid: String, offer: SessionDescription) {
        val ref = Backend.session(deviceId, sid)
        val factory = Rtc.factory(context)
        val peer = factory.createPeerConnection(Rtc.rtcConfig(), object : PcObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                ref.collection("helperIce").add(Rtc.iceToMap(candidate, sid))
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                (receiver?.track() as? VideoTrack)?.let { _video.value = it }
            }

            override fun onDataChannel(channel: DataChannel?) {
                channel ?: return
                dc = channel
                channel.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    override fun onStateChange() {}
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val bytes = ByteArray(buffer.data.remaining())
                        buffer.data.get(bytes)
                        val o = runCatching { JSONObject(String(bytes, StandardCharsets.UTF_8)) }.getOrNull() ?: return
                        if (o.optString("t") == "info") _controlAvailable.value = o.optBoolean("control", true)
                    }
                })
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> setStatus(Status.Connected)
                    PeerConnection.PeerConnectionState.FAILED ->
                        scope.launch(Dispatchers.Main) { finish("Verbindung fehlgeschlagen. (Evtl. wird ein TURN-Server benötigt – siehe Anleitung.)", true) }
                    else -> {}
                }
            }
        }) ?: return finish("WebRTC konnte nicht gestartet werden.", true)
        pc = peer
        try {
            peer.setRemoteAsync(offer)
            val answer = peer.createAnswerAsync()
            peer.setLocalAsync(answer)
            ref.update(mapOf("answer" to Rtc.sdpToMap(answer), "state" to SessionState.CONNECTED)).await()
            registrations += ref.collection("clientIce").addSnapshotListener { snap, _ ->
                snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach {
                    Rtc.iceFromMap(it.document.data)?.let { c -> peer.addIceCandidate(c) }
                }
            }
        } catch (e: Exception) {
            Log.e("HelperSession", "answer", e)
            finish("Fehler beim Verbinden: ${e.localizedMessage}", true)
        }
    }

    private fun setStatus(s: Status) {
        if (_status.value !is Status.Ended) _status.value = s
    }

    // ---- Befehle an das andere Handy ----

    private fun send(o: JSONObject) {
        val channel = dc ?: return
        if (channel.state() != DataChannel.State.OPEN) return
        val bytes = o.toString().toByteArray(StandardCharsets.UTF_8)
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    fun tap(x: Float, y: Float, durationMs: Long) =
        send(JSONObject().put("t", "tap").put("x", x.toDouble()).put("y", y.toDouble()).put("d", durationMs))

    fun path(points: List<Pair<Float, Float>>, durationMs: Long) {
        val arr = JSONArray()
        points.forEach { (x, y) -> arr.put(x.toDouble()); arr.put(y.toDouble()) }
        send(JSONObject().put("t", "path").put("p", arr).put("d", durationMs))
    }

    fun key(k: String) = send(JSONObject().put("t", "key").put("k", k))
    fun text(s: String) = send(JSONObject().put("t", "text").put("s", s))
    fun deleteChar() = send(JSONObject().put("t", "del"))
    fun enter() = send(JSONObject().put("t", "enter"))

    fun stop() = finish("Beendet.", notifyRemote = true)

    private fun finish(message: String, notifyRemote: Boolean) {
        if (closed) return
        closed = true
        timeoutJob?.cancel()
        _status.value = Status.Ended(message)
        val sid = sessionId
        if (notifyRemote && sid != null) Backend.session(deviceId, sid).update("state", SessionState.ENDED)
        registrations.forEach { it.remove() }
        registrations.clear()
        _video.value = null
        runCatching { dc?.close() }
        runCatching { pc?.close() }
        dc = null
        pc = null
    }
}
