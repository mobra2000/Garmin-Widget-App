package de.mbraisch.garminwidget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.ui.screens.AccountScreen
import de.mbraisch.garminwidget.ui.screens.LoginScreen
import de.mbraisch.garminwidget.ui.screens.TodayScreen
import de.mbraisch.garminwidget.ui.screens.WidgetSettingsScreen
import de.mbraisch.garminwidget.ui.theme.GarminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            GarminTheme(dynamicColor = settings.dynamicColor) {
                AppRoot(vm)
            }
        }
    }
}

private data class Tab(val label: String, @DrawableRes val selected: Int, @DrawableRes val unselected: Int)

private val TABS = listOf(
    Tab("Heute", R.drawable.ic_home, R.drawable.ic_home_outline),
    Tab("Widget", R.drawable.ic_widgets, R.drawable.ic_widgets_outline),
    Tab("Konto", R.drawable.ic_person, R.drawable.ic_person_outline),
)

@Composable
private fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    if (showLogin) {
        BackHandler { showLogin = false; vm.resetLogin() }
        LoginScreen(
            vm = vm,
            onClose = { showLogin = false; vm.resetLogin() },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                painterResource(if (tab == index) t.selected else t.unselected),
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (tab) {
            0 -> TodayScreen(vm, onLogin = { showLogin = true }, modifier = mod)
            1 -> WidgetSettingsScreen(vm, modifier = mod)
            else -> AccountScreen(vm, onLogin = { showLogin = true }, modifier = mod)
        }
    }
}
