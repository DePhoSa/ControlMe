package de.controlme.app.client

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import de.controlme.app.MainActivity
import de.controlme.app.R
import de.controlme.app.core.Backend
import de.controlme.app.core.Notifications
import de.controlme.app.core.PcObserver
import de.controlme.app.core.Rtc
import de.controlme.app.core.SessionState
import de.controlme.app.core.createOfferAsync
import de.controlme.app.core.setLocalAsync
import de.controlme.app.core.setRemoteAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.RtpParameters
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Überträgt den Bildschirm per WebRTC (direkt, verschlüsselt, ohne eigenen Server)
 * und nimmt Tipp-/Wisch-Befehle des Helfers entgegen.
 */
class ScreenShareService : LifecycleService() {

    companion object {
        private const val TAG = "ScreenShare"
        private const val ACTION_STOP = "stop"
        private const val MAX_WIDTH = 720

        private val _activeHelper = MutableStateFlow<String?>(null)
        /** Name des Helfers, der gerade zuschaut – oder null. */
        val activeHelper: StateFlow<String?> = _activeHelper

        fun start(ctx: Context, resultCode: Int, data: Intent, deviceId: String, sessionId: String, helper: String) {
            val i = Intent(ctx, ScreenShareService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
                .putExtra("device", deviceId)
                .putExtra("session", sessionId)
                .putExtra("helper", helper)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, ScreenShareService::class.java).setAction(ACTION_STOP))
        }
    }

    private var sessionId: String? = null
    private var deviceId: String? = null
    private var pc: PeerConnection? = null
    private var dc: DataChannel? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var source: VideoSource? = null
    private var track: VideoTrack? = null
    private val registrations = mutableListOf<ListenerRegistration>()
    private var disconnectJob: Job? = null
    private var stopped = false
    private var captureSize = 0 to 0

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val size = computeCaptureSize()
            if (size != captureSize) {
                captureSize = size
                runCatching { capturer?.changeCaptureFormat(size.first, size.second, 30) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            shutdown(notifyRemote = true)
            return START_NOT_STICKY
        }
        val data = intent?.let { IntentCompat.getParcelableExtra(it, "data", Intent::class.java) }
        val device = intent?.getStringExtra("device")
        val session = intent?.getStringExtra("session")
        val helper = intent?.getStringExtra("helper") ?: "Helfer"
        if (data == null || device == null || session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Eine neue Sitzung ersetzt eine eventuell noch laufende.
        if (sessionId != null) cleanup(notifyRemote = true, stopService = false)
        stopped = false
        sessionId = session
        deviceId = device
        // Android 14+: zuerst als mediaProjection-Dienst in den Vordergrund, dann aufnehmen.
        startForeground(Notifications.ID_SHARE, notification(helper), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        _activeHelper.value = helper
        lifecycleScope.launch {
            try {
                startStreaming(data, device, session)
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
                shutdown(notifyRemote = true)
            }
        }
        return START_NOT_STICKY
    }

    private fun computeCaptureSize(): Pair<Int, Int> {
        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val w = bounds.width()
        val h = bounds.height()
        val longSide = maxOf(w, h)
        val shortSide = minOf(w, h)
        val scale = minOf(1f, MAX_WIDTH.toFloat() / shortSide)
        fun even(v: Float) = (v.toInt() / 2) * 2
        val sw = even(shortSide * scale)
        val sl = even(longSide * scale)
        return if (w <= h) sw to sl else sl to sw
    }

    private suspend fun startStreaming(data: Intent, device: String, session: String) {
        val factory = Rtc.factory(this)
        val sessionRef = Backend.session(device, session)

        val cap = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                // z. B. über den Statusleisten-Chip beendet
                lifecycleScope.launch(Dispatchers.Main) { shutdown(notifyRemote = true) }
            }
        })
        capturer = cap
        val texHelper = SurfaceTextureHelper.create("ScreenCapture", Rtc.egl.eglBaseContext)
        surfaceHelper = texHelper
        val videoSource = factory.createVideoSource(true)
        source = videoSource
        cap.initialize(texHelper, this, videoSource.capturerObserver)
        captureSize = computeCaptureSize()
        cap.startCapture(captureSize.first, captureSize.second, 30)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, null)
        val videoTrack = factory.createVideoTrack("screen", videoSource)
        track = videoTrack

        val peer = factory.createPeerConnection(Rtc.rtcConfig(), object : PcObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                sessionRef.collection("clientIce").add(Rtc.iceToMap(candidate, session))
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                lifecycleScope.launch(Dispatchers.Main) { onConnectionState(newState) }
            }
        }) ?: error("PeerConnection konnte nicht erstellt werden")
        pc = peer

        val sender = peer.addTrack(videoTrack, listOf("screen"))
        // Lieber scharfe Schrift als flüssige Bewegung.
        runCatching {
            val params = sender.parameters
            params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
            sender.setParameters(params)
        }

        dc = peer.createDataChannel("input", DataChannel.Init().apply { ordered = true }).also { channel ->
            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}
                override fun onStateChange() {
                    if (channel.state() == DataChannel.State.OPEN) sendInfo()
                }
                override fun onMessage(buffer: DataChannel.Buffer) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val msg = String(bytes, StandardCharsets.UTF_8)
                    val service = RemoteControlService.instance
                    if (service == null) sendInfo() else service.handle(msg)
                }
            })
        }

        val offer = peer.createOfferAsync()
        peer.setLocalAsync(offer)
        sessionRef.update(mapOf("offer" to Rtc.sdpToMap(offer), "state" to SessionState.OFFERED))

        registrations += sessionRef.addSnapshotListener { snap, _ ->
            snap ?: return@addSnapshotListener
            val state = snap.getString("state")
            if (state == SessionState.ENDED) {
                shutdown(notifyRemote = false)
                return@addSnapshotListener
            }
            val answer = Rtc.sdpFromMap(snap.get("answer") as? Map<*, *>)
            if (answer != null && peer.remoteDescription == null) {
                lifecycleScope.launch { runCatching { peer.setRemoteAsync(answer) } }
            }
        }
        registrations += sessionRef.collection("helperIce").addSnapshotListener { snap, _ ->
            snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach {
                Rtc.iceFromMap(it.document.data)?.let { c -> peer.addIceCandidate(c) }
            }
        }
        // Wenn der Helfer nicht innerhalb einer Minute verbindet: aufräumen.
        disconnectJob = lifecycleScope.launch {
            delay(60_000)
            if (peer.connectionState() != PeerConnection.PeerConnectionState.CONNECTED) shutdown(notifyRemote = true)
        }
    }

    private fun onConnectionState(state: PeerConnection.PeerConnectionState?) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> disconnectJob?.cancel()
            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                disconnectJob?.cancel()
                disconnectJob = lifecycleScope.launch {
                    delay(15_000)
                    shutdown(notifyRemote = true)
                }
            }
            PeerConnection.PeerConnectionState.FAILED,
            PeerConnection.PeerConnectionState.CLOSED -> shutdown(notifyRemote = true)
            else -> {}
        }
    }

    /** Teilt dem Helfer Bildgröße und Steuerungsstatus mit. */
    private fun sendInfo() {
        val o = JSONObject()
            .put("t", "info")
            .put("control", RemoteControlService.instance != null)
        val bytes = o.toString().toByteArray(StandardCharsets.UTF_8)
        runCatching { dc?.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false)) }
    }

    private fun notification(helper: String): Notification {
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Notifications.CH_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$helper sieht deinen Bildschirm")
            .setContentText("Zum Beenden auf „Beenden“ tippen")
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(0, "Beenden", stopPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun shutdown(notifyRemote: Boolean) {
        cleanup(notifyRemote, stopService = true)
    }

    private fun cleanup(notifyRemote: Boolean, stopService: Boolean) {
        if (stopped) return
        stopped = true
        disconnectJob?.cancel()
        val device = deviceId
        val session = sessionId
        if (notifyRemote && device != null && session != null) {
            Backend.session(device, session).update("state", SessionState.ENDED)
        }
        registrations.forEach { it.remove() }
        registrations.clear()
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { dc?.close() }
        runCatching { pc?.close() }
        runCatching { track?.dispose() }
        runCatching { source?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        capturer = null; dc = null; pc = null; track = null; source = null; surfaceHelper = null
        sessionId = null; deviceId = null
        _activeHelper.value = null
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        cleanup(notifyRemote = true, stopService = false)
        super.onDestroy()
    }
}
