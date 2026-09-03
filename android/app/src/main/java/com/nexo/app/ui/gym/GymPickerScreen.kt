package com.nexo.app.ui.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Shown when the signed-in user has no gym memberships — there's no
 * self-serve gym creation or public join directory (see FEEDBACK.md's
 * "Owner-Driven Membership" model), so this is purely a waiting screen
 * until a gym owner adds this user by email. [onRefresh] re-triggers
 * [com.nexo.app.ui.SessionViewModel.refresh], which flips to a full-screen
 * loading state immediately and swaps this screen out for the normal app
 * shell once the owner's addition is picked up — no local loading state
 * needed here. Mirrors iOS's `GymPickerView.awaitingEnrollmentView`.
 */
@Composable
fun GymPickerScreen(repository: BackendRepository, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    var userEmail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        userEmail = try { repository.fetchMyProfile()?.email.orEmpty() } catch (e: Exception) { "" }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("Welcome to Nexo!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "You haven't been added to a gym yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (userEmail.isNotEmpty()) {
            Text(
                "Ask your gym owner to add you using your email:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onRefresh,
            modifier = Modifier.width(220.dp).height(48.dp)
        ) {
            Text("Check Again")
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onSignOut) {
            Text("Sign Out")
        }
    }
}
