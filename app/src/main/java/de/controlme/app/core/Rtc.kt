package de.controlme.app.core

import android.content.Context
import de.controlme.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Gemeinsame WebRTC-Bausteine für beide Seiten. */
object Rtc {
    val egl: EglBase by lazy { EglBase.create() }

    @Volatile private var factory: PeerConnectionFactory? = null

    @Synchronized
    fun factory(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    fun rtcConfig(): PeerConnection.RTCConfiguration {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder(
                listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")
            ).createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
        )
        if (BuildConfig.TURN_URL.isNotBlank()) {
            servers += PeerConnection.IceServer.builder(BuildConfig.TURN_URL.split(",").map { it.trim() })
                .setUsername(BuildConfig.TURN_USER)
                .setPassword(BuildConfig.TURN_PASS)
                .createIceServer()
        }
        return PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
    }

    fun iceToMap(c: IceCandidate, sessionId: String) = mapOf(
        "sdpMid" to c.sdpMid,
        "sdpMLineIndex" to c.sdpMLineIndex,
        "candidate" to c.sdp,
        "sid" to sessionId,
    )

    fun iceFromMap(m: Map<String, Any?>): IceCandidate? {
        val cand = m["candidate"] as? String ?: return null
        return IceCandidate(m["sdpMid"] as? String, (m["sdpMLineIndex"] as? Number)?.toInt() ?: 0, cand)
    }

    fun sdpToMap(s: SessionDescription) = mapOf("type" to s.type.canonicalForm(), "sdp" to s.description)

    fun sdpFromMap(m: Map<*, *>?): SessionDescription? {
        val type = m?.get("type") as? String ?: return null
        val sdp = m["sdp"] as? String ?: return null
        return SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
    }
}

// ---- kleine Coroutine-Helfer rund um die Callback-API von WebRTC ----

private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

suspend fun PeerConnection.createOfferAsync(): SessionDescription = suspendCancellableCoroutine { cont ->
    createOffer(object : SimpleSdpObserver() {
        override fun onCreateSuccess(desc: SessionDescription?) { cont.resume(desc!!) }
        override fun onCreateFailure(error: String?) { cont.resumeWithException(IllegalStateException(error)) }
    }, MediaConstraints())
}

suspend fun PeerConnection.createAnswerAsync(): SessionDescription = suspendCancellableCoroutine { cont ->
    createAnswer(object : SimpleSdpObserver() {
        override fun onCreateSuccess(desc: SessionDescription?) { cont.resume(desc!!) }
        override fun onCreateFailure(error: String?) { cont.resumeWithException(IllegalStateException(error)) }
    }, MediaConstraints())
}

suspend fun PeerConnection.setLocalAsync(desc: SessionDescription) = suspendCancellableCoroutine { cont ->
    setLocalDescription(object : SimpleSdpObserver() {
        override fun onSetSuccess() { cont.resume(Unit) }
        override fun onSetFailure(error: String?) { cont.resumeWithException(IllegalStateException(error)) }
    }, desc)
}

suspend fun PeerConnection.setRemoteAsync(desc: SessionDescription) = suspendCancellableCoroutine { cont ->
    setRemoteDescription(object : SimpleSdpObserver() {
        override fun onSetSuccess() { cont.resume(Unit) }
        override fun onSetFailure(error: String?) { cont.resumeWithException(IllegalStateException(error)) }
    }, desc)
}

/** Basis-Observer, damit man nur überschreibt, was man braucht. */
open class PcObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: org.webrtc.MediaStream?) {}
    override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
    override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
}
