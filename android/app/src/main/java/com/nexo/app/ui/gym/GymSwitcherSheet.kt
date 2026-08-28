package com.nexo.app.ui.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.UserRole

/**
 * Bottom sheet for switching the active gym — mirrors `GymSwitcherSheet`
 * on iOS. Invoked from the Home header card and the Schedule header's
 * gym-name chevron. [showPlatformDashboardOption] (Platform Admins only)
 * adds a row that returns to [com.nexo.app.ui.platform.PlatformDashboardScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymSwitcherSheet(
    myGyms: List<Pair<Gym, UserRole>>,
    currentGymId: String,
    showPlatformDashboardOption: Boolean = false,
    onSelectGym: (String) -> Unit,
    onSelectPlatformDashboard: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (showPlatformDashboardOption) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPlatformDashboard()
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Platform Dashboard", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider()
                }
            }
            items(myGyms, key = { it.first.id }) { (gym, role) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectGym(gym.id)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = gym.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = role.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (gym.id == currentGymId) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Currently selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
