package de.mbraisch.garminwidget.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.mbraisch.garminwidget.auth.AuthExpiredException
import de.mbraisch.garminwidget.data.GarminRepository
import de.mbraisch.garminwidget.widget.GarminWidget
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Holt im Hintergrund die aktuellen Werte und aktualisiert danach alle Widgets. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = GarminRepository(applicationContext)
        if (!repo.client.isLoggedIn) return Result.success()
        return try {
            repo.sync()
            Result.success()
        } catch (e: AuthExpiredException) {
            Result.success() // Neu-Anmeldung nötig, Wiederholen bringt nichts
        } catch (e: IOException) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            GarminWidget().updateAll(applicationContext)
        }
    }
}

object SyncScheduler {
    private const val PERIODIC = "garmin_periodic_sync"
    private const val NOW = "garmin_sync_now"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Plant die regelmäßige Aktualisierung (Intervall aus den Einstellungen, min. 15 min). */
    fun schedulePeriodic(context: Context, intervalMinutes: Int, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Sofortige Aktualisierung, z. B. über den Button im Widget. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
