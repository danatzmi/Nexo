package com.nexo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A member's credit-wallet card — "MY PLANS" header, one [PlanHomeRow] per
 * item (or an empty-state message). Shared by `GymHomeScreen` and
 * `ProfileScreen`, mirroring iOS's `PlanHomeRow`/`myPlansHomeCard` — iOS
 * itself renders `ProfileView.myPlansCard` with a slightly different
 * layout/copy than `GymHomeView.myPlansHomeCard` for the same data; Android
 * intentionally uses one shared component for both screens instead of
 * mirroring that inconsistency.
 */
@Composable
fun MyPlansCard(activePlans: List<ActivePlanItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "MY PLANS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (activePlans.isEmpty()) {
                Text(
                    text = "No active plans. Contact your gym to purchase a membership plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    activePlans.forEachIndexed { index, plan ->
                        if (index > 0) HorizontalDivider()
                        PlanHomeRow(plan = plan)
                    }
                }
            }
        }
    }
}

private val expirationFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

/** One row in [MyPlansCard] — plan name, access detail (unlimited or remaining credits), and an expiration date with an Active/Expired status pill. Mirrors iOS's `PlanHomeRow`. */
@Composable
private fun PlanHomeRow(plan: ActivePlanItem) {
    val scope = plan.workoutType?.let { " ($it)" } ?: ""
    val accessDetail = if (plan.type == PlanComponentType.UNLIMITED) {
        "Unlimited access$scope"
    } else if (plan.resetPeriod == PlanResetPeriod.MONTHLY) {
        val available = plan.availableCredits()
        val resetsText = remember(plan.id, plan.cycleAnchorDateMillis, plan.expiresAtMillis) {
            Instant.ofEpochMilli(plan.currentCycleBounds().second).atZone(ZoneId.systemDefault()).format(expirationFormatter)
        }
        "$available of ${plan.creditCount} credits remaining$scope · Resets $resetsText"
    } else {
        "${plan.remainingCredits} ${if (plan.remainingCredits == 1) "credit" else "credits"} remaining$scope"
    }
    val expiresText = remember(plan.expiresAtMillis) {
        Instant.ofEpochMilli(plan.expiresAtMillis).atZone(ZoneId.systemDefault()).format(expirationFormatter)
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(text = plan.planName, style = MaterialTheme.typography.titleMedium)
            Text(text = accessDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "Expires $expiresText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (plan.isExpired) MaterialTheme.colorScheme.errorContainer else Color(0xFFDCEDC8)
        ) {
            Text(
                text = if (plan.isExpired) "Expired" else "Active",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (plan.isExpired) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
