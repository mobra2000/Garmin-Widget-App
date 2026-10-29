package de.mbraisch.garminwidget.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mbraisch.garminwidget.auth.AuthExpiredException
import de.mbraisch.garminwidget.auth.GarminBlockedException
import de.mbraisch.garminwidget.data.GarminRepository
import de.mbraisch.garminwidget.data.Snapshot
import de.mbraisch.garminwidget.data.WidgetSettings
import de.mbraisch.garminwidget.sync.SyncScheduler
import de.mbraisch.garminwidget.widget.GarminWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface LoginState {
    data object Idle : LoginState
    data object Working : LoginState
    data class Failed(val message: String) : LoginState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GarminRepository(app)
    private val store = repo.store

    val settings: StateFlow<WidgetSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, WidgetSettings())
    val snapshot: StateFlow<Snapshot> =
        store.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, Snapshot.EMPTY)
    val loggedIn: StateFlow<Boolean> =
        store.loggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, repo.client.isLoggedIn)
    val syncing: StateFlow<Boolean> =
        store.syncing.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val displayName: StateFlow<String?> =
        store.displayName.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    /** Kurzmeldung für die Snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun refresh() {
        if (syncing.value) return
        viewModelScope.launch {
            try {
                repo.sync()
            } catch (e: AuthExpiredException) {
                _message.value = "Anmeldung abgelaufen – bitte neu anmelden"
            } catch (e: GarminBlockedException) {
                _message.value = e.message
            } catch (e: IOException) {
                _message.value = "Aktualisierung fehlgeschlagen: ${e.message ?: "keine Verbindung"}"
            }
            GarminWidget().updateAll(getApplication())
        }
    }

    /** Wird aufgerufen, sobald die Anmeldeseite das Service-Ticket geliefert hat. */
    fun completeLogin(ticket: String, onDone: () -> Unit) {
        if (_loginState.value == LoginState.Working) return
        _loginState.value = LoginState.Working
        viewModelScope.launch {
            try {
                repo.completeLogin(ticket)
                SyncScheduler.schedulePeriodic(getApplication(), settings.value.intervalMinutes, replace = true)
                GarminWidget().updateAll(getApplication())
                _loginState.value = LoginState.Idle
                _message.value = "Mit Garmin Connect verbunden"
                onDone()
            } catch (e: Exception) {
                _loginState.value = LoginState.Failed(
                    when (e) {
                        is GarminBlockedException -> e.message ?: "Von Garmin abgelehnt"
                        is IOException -> "Anmeldung fehlgeschlagen: ${e.message}"
                        else -> "Unerwarteter Fehler: ${e.message}"
                    }
                )
            }
        }
    }

    fun resetLogin() {
        _loginState.value = LoginState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            SyncScheduler.cancelAll(getApplication())
            GarminWidget().updateAll(getApplication())
        }
    }

    fun updateSettings(transform: (WidgetSettings) -> WidgetSettings) {
        viewModelScope.launch {
            val (old, new) = store.updateSettings(transform)
            if (new == old) return@launch
            if (new.intervalMinutes != old.intervalMinutes && loggedIn.value) {
                SyncScheduler.schedulePeriodic(getApplication(), new.intervalMinutes, replace = true)
            }
            GarminWidget().updateAll(getApplication())
        }
    }
}
