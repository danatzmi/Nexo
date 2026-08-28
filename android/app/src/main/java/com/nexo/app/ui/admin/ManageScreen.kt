package com.nexo.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.canEditGymSettings

/** The Manage tab's top-level structure — mirrors `AdminView` on iOS. Only reachable when [NexoApp] has already gated visibility to Owner/Coach/Platform Admin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(repository: BackendRepository, gymId: String, gym: Gym?, userRole: UserRole?, platformRole: PlatformRole) {
    var selectedTab by remember { mutableStateOf(ManageTab.MEMBERS) }
    var showGymSettings by remember { mutableStateOf(false) }
    val canEditGymSettings = canEditGymSettings(userRole, platformRole)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Tools") },
                actions = {
                    if (canEditGymSettings && gym != null) {
                        IconButton(onClick = { showGymSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Gym Settings")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ManageTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            when (selectedTab) {
                ManageTab.MEMBERS -> MembersTabContent(repository, gymId, userRole, platformRole)
                ManageTab.PLANS -> MembershipPlansScreen(repository, gymId, gym?.workoutTypes ?: Gym.DEFAULT_WORKOUT_TYPES)
                ManageTab.REPORTS -> ReportsPlaceholder()
            }
        }
    }

    if (showGymSettings && gym != null) {
        GymSettingsSheet(
            repository = repository,
            gymId = gymId,
            initialName = gym.name,
            initialWorkoutTypes = gym.workoutTypes,
            onDismiss = { showGymSettings = false },
            onSaved = { showGymSettings = false }
        )
    }
}

private enum class ManageTab(val label: String, val icon: ImageVector) {
    MEMBERS("Members", Icons.Filled.Group),
    PLANS("Plans", Icons.Filled.CreditCard),
    REPORTS("Reports", Icons.Filled.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersTabContent(repository: BackendRepository, gymId: String, userRole: UserRole?, platformRole: PlatformRole) {
    var subTab by remember { mutableStateOf(MembersSubTab.MEMBERS) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = subTab.ordinal) {
            MembersSubTab.entries.forEach { tab ->
                Tab(selected = subTab == tab, onClick = { subTab = tab }, text = { Text(tab.label) })
            }
        }

        when (subTab) {
            MembersSubTab.MEMBERS -> GymMembersScreen(repository, gymId, userRole, platformRole)
            MembersSubTab.TEAM -> TeamScreen(repository, gymId, userRole, platformRole)
        }
    }
}

private enum class MembersSubTab(val label: String) {
    MEMBERS("Members"),
    TEAM("Team")
}

@Composable
private fun ReportsPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BarChart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = "Reports", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            text = "Analytics coming soon",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
