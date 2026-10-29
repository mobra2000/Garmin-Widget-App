package de.mbraisch.garminwidget.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.mbraisch.garminwidget.data.AppStore
import kotlinx.coroutines.runBlocking

/**
 * Unsichtbare Zwischenstation für Klicks aufs Widget: öffnet je nach Einstellung die
 * Garmin-Connect-App oder diese App und schließt sich sofort wieder.
 */
class OpenFromWidgetActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tapOpensGarmin = runCatching {
            runBlocking { AppStore(this@OpenFromWidgetActivity).currentSettings().tapOpensGarmin }
        }.getOrDefault(true)

        val garmin = if (tapOpensGarmin) packageManager.getLaunchIntentForPackage(GARMIN_CONNECT_PACKAGE) else null
        val target = garmin ?: Intent(this, MainActivity::class.java)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(target) }
        finish()
    }

    companion object {
        const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
    }
}
