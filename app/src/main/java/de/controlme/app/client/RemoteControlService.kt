package de.controlme.app.client

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * Bedienungshilfe: führt Tipp- und Wischgesten des Helfers aus, drückt
 * Zurück/Home und kann Text in das aktive Eingabefeld schreiben.
 *
 * Protokoll (JSON über den WebRTC-Datenkanal, Koordinaten 0..1):
 *  {"t":"tap","x":0.5,"y":0.5,"d":60}            – Tippen (d = Dauer in ms, lang = gedrückt halten)
 *  {"t":"path","p":[x,y,x,y,…],"d":300}         – Wischen / Ziehen
 *  {"t":"key","k":"back|home|recents|notifications|quick|lock|screenshot"}
 *  {"t":"text","s":"Hallo"}                     – Text ins fokussierte Feld schreiben
 *  {"t":"del"} / {"t":"enter"}                  – letztes Zeichen löschen / Eingabe bestätigen
 */
class RemoteControlService : AccessibilityService() {

    companion object {
        @Volatile var instance: RemoteControlService? = null
            private set

        /** Bis zu diesem Zeitpunkt wird der Freigabe-Dialog automatisch bestätigt. */
        @Volatile var awaitConsentUntil: Long = 0L

        private val CONSENT_IDS = listOf(
            "android:id/button1",
            "com.android.systemui:id/button1",
        )
        private val CONSENT_TEXTS = listOf(
            "jetzt starten", "starten", "bildschirm teilen", "teilen", "weiter",
            "start now", "start", "share screen", "share", "next",
        )
        private val CONSENT_PACKAGES = setOf("com.android.systemui", "android")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > awaitConsentUntil) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in CONSENT_PACKAGES) return
        if (tryConfirmConsent()) awaitConsentUntil = 0L
    }

    /** Versucht den "Starten"-Knopf im System-Dialog der Bildschirmfreigabe zu drücken. */
    private fun tryConfirmConsent(): Boolean {
        val roots = buildList {
            windows.forEach { w -> w.root?.let { add(it) } }
            rootInActiveWindow?.let { add(it) }
        }.filter { it.packageName?.toString() in CONSENT_PACKAGES }

        for (root in roots) {
            for (id in CONSENT_IDS) {
                root.findAccessibilityNodeInfosByViewId(id)
                    .firstOrNull { it.isEnabled && it.isVisibleToUser }
                    ?.let { if (click(it)) return true }
            }
        }
        for (root in roots) {
            for (label in CONSENT_TEXTS) {
                root.findAccessibilityNodeInfosByText(label)
                    .firstOrNull { n ->
                        n.isEnabled && n.isVisibleToUser &&
                            n.text?.toString()?.trim()?.equals(label, ignoreCase = true) == true &&
                            (n.className?.contains("Button") == true || n.isClickable)
                    }
                    ?.let { if (click(it)) return true }
            }
        }
        return false
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    // ---------------- Fernsteuerung ----------------

    fun handle(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (o.optString("t")) {
            "tap" -> tap(o.optDouble("x"), o.optDouble("y"), o.optLong("d", 60))
            "path" -> path(o.optJSONArray("p")?.let { a -> List(a.length()) { a.optDouble(it) } } ?: return, o.optLong("d", 300))
            "key" -> key(o.optString("k"))
            "text" -> text(o.optString("s"))
            "del" -> deleteLast()
            "enter" -> enter()
        }
    }

    private fun screenSize(): Pair<Float, Float> {
        val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return b.width().toFloat() to b.height().toFloat()
    }

    private fun tap(x: Double, y: Double, duration: Long) {
        if (x.isNaN() || y.isNaN()) return
        val (w, h) = screenSize()
        val p = Path().apply { moveTo((x * w).toFloat().coerceIn(0f, w - 1), (y * h).toFloat().coerceIn(0f, h - 1)) }
        dispatch(p, duration)
    }

    private fun path(coords: List<Double>, duration: Long) {
        if (coords.size < 4) return
        val (w, h) = screenSize()
        val p = Path()
        coords.chunked(2).forEachIndexed { i, (x, y) ->
            val px = (x * w).toFloat().coerceIn(0f, w - 1)
            val py = (y * h).toFloat().coerceIn(0f, h - 1)
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        dispatch(p, duration)
    }

    private fun dispatch(p: Path, duration: Long) {
        val d = duration.coerceIn(1, GestureDescription.getMaxGestureDuration() - 1)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, d))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun key(k: String) {
        val action = when (k) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN
            "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> return
        }
        performGlobalAction(action)
    }

    private fun text(s: String) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current + s)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun deleteLast() {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        if (node.isShowingHintText) return
        val current = node.text?.toString().orEmpty()
        if (current.isEmpty()) return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.dropLast(1))
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun enter() {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }
}
