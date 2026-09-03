package com.nexo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.SharedPreferencesSessionStore
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.ui.auth.AuthScreen
import com.nexo.app.ui.gym.GymPickerScreen
import com.nexo.app.ui.platform.PlatformDashboardScreen

/**
 * The app's true root — routes between the auth flow, an empty-gym-state
 * prompt, and the authenticated 4-tab [NexoApp] shell, based on
 * [SessionViewModel]'s state. Mirrors the session-routing `ContentView`
 * does on iOS after `AuthView`.
 */
@Composable
fun NexoRoot(repository: BackendRepository) {
    val context = LocalContext.current
    val sessionStore = remember { SharedPreferencesSessionStore(context) }
    val viewModel: SessionViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionViewModel(repository, sessionStore) } }
    )
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        is SessionViewModel.SessionState.Loading -> FullScreenLoading()
        is SessionViewModel.SessionState.SignedOut -> AuthScreen(repository, onAuthenticated = viewModel::refresh)
        is SessionViewModel.SessionState.NoGyms -> GymPickerScreen(
            repository = repository,
            onRefresh = viewModel::refresh,
            onSignOut = viewModel::signOut
        )
        is SessionViewModel.SessionState.PlatformDashboard -> PlatformDashboardScreen(
            repository = repository,
            onEnterGym = viewModel::enterGym,
            onSignOut = viewModel::signOut
        )
        is SessionViewModel.SessionState.Ready -> NexoApp(
            repository = repository,
            gymId = current.gymId,
            myGyms = current.myGyms,
            platformRole = current.platformRole,
            onSwitchGym = viewModel::switchGym,
            onEnterPlatformDashboard = viewModel::enterPlatformDashboard,
            onSignOut = viewModel::signOut
        )
    }
}

@Composable
private fun FullScreenLoading() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}
