package de.controlme.app.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.controlme.app.core.Prefs
import kotlinx.coroutines.runBlocking

/** Startet die Bereitschaft nach einem Neustart oder App-Update automatisch. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (runBlocking { Prefs(context).clientWantedNow() }) runCatching { ClientService.start(context) }
    }
}
