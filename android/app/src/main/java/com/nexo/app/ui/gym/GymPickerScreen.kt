package com.nexo.app.ui.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexo.app.data.repository.BackendRepository

/**
 * Shown when the signed-in user has no gym memberships — the "2-Option
 * Onboarding Hub", mirroring iOS's `GymPickerView.welcomeOnboardingView`.
 */
@Composable
fun GymPickerScreen(repository: BackendRepository, onGymEntered: (String) -> Unit, onSignOut: () -> Unit) {
    var showJoinGym by remember { mutableStateOf(false) }
    var showCreateGym by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text("Welcome to Nexo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Choose how you'd like to get started:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        OnboardingChoiceCard(
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            title = "I am a Gym Member",
            description = "Find your gym, book classes, and track your daily workouts.",
            onClick = { showJoinGym = true }
        )
        Spacer(Modifier.height(16.dp))
        OnboardingChoiceCard(
            icon = Icons.Filled.Business,
            title = "I am a Gym Owner",
            description = "Set up a new gym, schedule classes, and manage your members.",
            onClick = { showCreateGym = true }
        )

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSignOut) { Text("Sign Out") }
    }

    if (showJoinGym) {
        JoinGymSheet(
            repository = repository,
            onDismiss = { showJoinGym = false },
            onJoined = { gymId ->
                showJoinGym = false
                onGymEntered(gymId)
            }
        )
    }
    if (showCreateGym) {
        CreateGymSheet(
            repository = repository,
            onDismiss = { showCreateGym = false },
            onEnterDashboard = { gymId ->
                showCreateGym = false
                onGymEntered(gymId)
            }
        )
    }
}

@Composable
private fun OnboardingChoiceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
