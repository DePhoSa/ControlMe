package de.controlme.app

import android.app.Application
import de.controlme.app.client.ClientService
import de.controlme.app.core.Notifications
import de.controlme.app.core.Prefs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking

class App : Application() {
    companion object {
        /** Lebt so lange wie die App – für Sitzungen, die Bildschirmdrehungen überstehen sollen. */
        val scope = MainScope()
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        if (runBlocking { Prefs(this@App).clientWantedNow() }) {
            runCatching { ClientService.start(this) }
        }
    }
}
