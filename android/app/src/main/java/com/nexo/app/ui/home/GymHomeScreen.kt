package com.nexo.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.ui.components.MyPlansCard
import com.nexo.app.ui.components.RoleBadge

@Composable
fun GymHomeScreen(
    repository: BackendRepository,
    gymId: String,
    onNavigateToSchedule: () -> Unit = {},
    onOpenGymSwitcher: () -> Unit = {},
    onOpenClassDetail: (String) -> Unit = {}
) {
    val viewModel: GymHomeViewModel = viewModel(
        factory = viewModelFactory { initializer { GymHomeViewModel(repository, gymId) } },
        key = gymId
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (uiState.isLoading) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GymHeaderCard(
                    gymName = uiState.gymName,
                    roleDisplayName = uiState.roleDisplayName,
                    onClick = onOpenGymSwitcher
                )
            }
            item {
                Text(
                    text = if (uiState.userDisplayName.isNotBlank()) "${uiState.userDisplayName}, ready for your workout?" else "Ready for your workout?",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            item {
                QuickSummaryCard(
                    nextBookedClass = uiState.nextBookedClass,
                    onClick = uiState.nextBookedClass?.let { gymClass -> { onOpenClassDetail(gymClass.id) } }
                )
            }
            item {
                Button(onClick = onNavigateToSchedule, modifier = Modifier.fillMaxWidth()) {
                    Text("Book a Class")
                }
            }
            if (uiState.roleDisplayName == "Member") {
                item { MyPlansCard(activePlans = uiState.activePlans) }
            }
        }
    }
}

@Composable
private fun GymHeaderCard(gymName: String, roleDisplayName: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(text = gymName, style = MaterialTheme.typography.titleLarge)
            }
            RoleBadge(text = roleDisplayName)
        }
    }
}

@Composable
private fun QuickSummaryCard(nextBookedClass: GymClass?, onClick: (() -> Unit)?) {
    val cardModifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Card(modifier = cardModifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Next Up", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            if (nextBookedClass == null) {
                Text(text = "No upcoming bookings — head to Schedule to book your next class!", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text = nextBookedClass.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${nextBookedClass.formattedDayDate} · ${nextBookedClass.formattedTime} · ${nextBookedClass.coach}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
