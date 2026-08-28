package com.nexo.app.ui.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.PlatformUser
import com.nexo.app.ui.admin.GymSettingsSheet
import com.nexo.app.ui.gym.CreateGymSheet

/** The Platform Admin Dashboard — mirrors iOS's `PlatformDashboardView`. Shown in place of a gym-scoped Home when a Platform Admin has no specific gym entered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformDashboardScreen(
    repository: BackendRepository,
    onEnterGym: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val viewModel: PlatformDashboardViewModel = viewModel(factory = viewModelFactory { initializer { PlatformDashboardViewModel(repository) } })
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(DashboardTab.GYMS) }
    var showCreateGym by remember { mutableStateOf(false) }
    var gymToEdit by remember { mutableStateOf<Gym?>(null) }
    var pendingRoleUser by remember { mutableStateOf<PlatformUser?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                    }
                },
                actions = {
                    if (selectedTab == DashboardTab.GYMS) {
                        IconButton(onClick = { showCreateGym = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Create Gym")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardStatCard(title = "Gyms", value = uiState.gyms.size, icon = Icons.Filled.Business, modifier = Modifier.weight(1f))
                DashboardStatCard(title = "Users", value = uiState.users.size, icon = Icons.Filled.Groups, modifier = Modifier.weight(1f))
            }

            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                DashboardTab.entries.forEach { tab ->
                    Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }, text = { Text(tab.label) })
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                selectedTab == DashboardTab.GYMS -> GymsList(
                    gyms = uiState.gyms,
                    onEnterGym = onEnterGym,
                    onEdit = { gymToEdit = it },
                    onDelete = viewModel::requestDeleteGym
                )
                else -> UsersList(users = uiState.users, onSelectUser = { pendingRoleUser = it })
            }
        }
    }

    if (showCreateGym) {
        CreateGymSheet(
            repository = repository,
            onDismiss = { showCreateGym = false },
            onEnterDashboard = { gymId ->
                showCreateGym = false
                viewModel.load()
                onEnterGym(gymId)
            }
        )
    }

    gymToEdit?.let { gym ->
        GymSettingsSheet(
            repository = repository,
            gymId = gym.id,
            initialName = gym.name,
            initialWorkoutTypes = gym.workoutTypes,
            onDismiss = { gymToEdit = null },
            onSaved = { gymToEdit = null; viewModel.load() }
        )
    }

    uiState.gymToDelete?.let { gym ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteGymPrompt,
            title = { Text("Delete Gym?") },
            text = {
                Text("Are you sure you want to delete ${gym.name}? This will permanently delete all classes, bookings, plans, memberships, and records. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteGym) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteGymPrompt) { Text("Cancel") }
            }
        )
    }

    pendingRoleUser?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingRoleUser = null },
            title = { Text("Change role for ${user.displayName}") },
            text = {},
            confirmButton = {
                if (user.role == PlatformRole.ADMIN) {
                    TextButton(onClick = {
                        viewModel.updateUserRole(user, PlatformRole.USER)
                        pendingRoleUser = null
                    }) { Text("Remove Admin", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = {
                        viewModel.updateUserRole(user, PlatformRole.ADMIN)
                        pendingRoleUser = null
                    }) { Text("Make Admin") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoleUser = null }) { Text("Cancel") }
            }
        )
    }
}

private enum class DashboardTab(val label: String) {
    GYMS("Gyms"),
    USERS("Users")
}

@Composable
private fun DashboardStatCard(title: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GymsList(gyms: List<Gym>, onEnterGym: (String) -> Unit, onEdit: (Gym) -> Unit, onDelete: (Gym) -> Unit) {
    if (gyms.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No Gyms Yet", style = MaterialTheme.typography.titleMedium)
            Text("Tap + to create the first gym", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(gyms, key = { it.id }) { gym ->
            Card(Modifier.fillMaxWidth().clickable { onEnterGym(gym.id) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(gym.name, style = MaterialTheme.typography.titleMedium)
                        Text("Tap to manage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onEdit(gym) }) { Icon(Icons.Filled.Settings, contentDescription = "Edit ${gym.name}") }
                    IconButton(onClick = { onDelete(gym) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${gym.name}") }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UsersList(users: List<PlatformUser>, onSelectUser: (PlatformUser) -> Unit) {
    if (users.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No Users", style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(users, key = { it.id }) { user ->
            Card(Modifier.fillMaxWidth().clickable { onSelectUser(user) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PlatformRoleBadge(user.role)
                }
            }
        }
    }
}

@Composable
private fun PlatformRoleBadge(role: PlatformRole) {
    val isAdmin = role == PlatformRole.ADMIN
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isAdmin) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = if (isAdmin) "Admin" else "User",
            style = MaterialTheme.typography.labelMedium,
            color = if (isAdmin) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
