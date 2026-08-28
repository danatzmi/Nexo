package com.nexo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
 *
 * [pendingJoinCode] is a deep-linked join code (from a `nexo://join/...`
 * or `https://nexo.fit/join/...` launch) that hasn't been shown to the
 * user yet — mirrors iOS's `DeepLinkHandler`. It's presented via
 * [SessionViewModel.presentDeepLinkCode] once the session is signed in
 * (immediately if already signed in, or as soon as sign-in completes if
 * not — the `LaunchedEffect` below re-fires whenever [state] changes).
 * [onConsumedPendingJoinCode] clears it in the caller (`MainActivity`) so
 * it isn't re-presented on the next recomposition/config change.
 */
@Composable
fun NexoRoot(
    repository: BackendRepository,
    pendingJoinCode: String? = null,
    onConsumedPendingJoinCode: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionStore = remember { SharedPreferencesSessionStore(context) }
    val viewModel: SessionViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionViewModel(repository, sessionStore) } }
    )
    val state by viewModel.state.collectAsState()
    val directJoinState by viewModel.directJoinState.collectAsState()

    LaunchedEffect(pendingJoinCode, state) {
        val signedIn = state !is SessionViewModel.SessionState.Loading && state !is SessionViewModel.SessionState.SignedOut
        if (pendingJoinCode != null && signedIn) {
            viewModel.presentDeepLinkCode(pendingJoinCode)
            onConsumedPendingJoinCode()
        }
    }

    when (val current = state) {
        is SessionViewModel.SessionState.Loading -> FullScreenLoading()
        is SessionViewModel.SessionState.SignedOut -> AuthScreen(repository, onAuthenticated = viewModel::refresh)
        is SessionViewModel.SessionState.NoGyms -> GymPickerScreen(
            repository = repository,
            onGymEntered = viewModel::enterGym,
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

    directJoinState?.let { direct ->
        DirectJoinDialog(state = direct, onConfirm = viewModel::confirmDirectJoin, onDismiss = viewModel::dismissDirectJoin)
    }
}

@Composable
private fun FullScreenLoading() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DirectJoinDialog(state: SessionViewModel.DirectJoinState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Gym") },
        text = {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                state.gym != null -> Text("You've been invited to join ${state.gym.name}. Join now?")
                else -> Text(state.errorMessage ?: "No gym found with that code.")
            }
        },
        confirmButton = {
            if (state.gym != null) {
                TextButton(onClick = onConfirm) {
                    if (state.isJoining) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Join")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (state.gym != null) "Cancel" else "OK") }
        }
    )
}
