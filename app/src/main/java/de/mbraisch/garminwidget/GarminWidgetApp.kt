package de.mbraisch.garminwidget

import android.app.Application
import de.mbraisch.garminwidget.data.AppStore
import de.mbraisch.garminwidget.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GarminWidgetApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val store = AppStore(this@GarminWidgetApp)
            // Falls die App abgestürzt ist, bleibt sonst evtl. "synchronisiert…" hängen.
            store.setSyncing(false)
            if (store.isLoggedIn()) {
                SyncScheduler.schedulePeriodic(this@GarminWidgetApp, store.currentSettings().intervalMinutes)
            }
        }
    }
}
