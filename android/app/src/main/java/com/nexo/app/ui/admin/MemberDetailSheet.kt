package com.nexo.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanType
import com.nexo.app.ui.components.CancelBookingDialog
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Manage tab → Members → tap a member — mirrors iOS's `MemberDetailView`:
 * profile, credit wallet (grant/revoke), bookings (staff can cancel on the
 * member's behalf), and Remove Member. [canManageGym] gates the wallet and
 * booking-cancel actions (Owner/Coach/Platform Admin); [canRemoveMember] is
 * narrower — Owner/Platform Admin only, matching iOS's
 * `appState.isAdmin || appState.gymRole == .owner`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailSheet(
    repository: BackendRepository,
    gymId: String,
    member: GymMember,
    canManageGym: Boolean,
    canRemoveMember: Boolean,
    onDismiss: () -> Unit
) {
    val viewModel: MemberDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { MemberDetailViewModel(repository, gymId, member) } },
        key = member.id
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGrantPlanDialog by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var classToCancel by remember { mutableStateOf<GymClass?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.didRemove) {
        if (uiState.didRemove) onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(member.fullName) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            if (uiState.isLoading) {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { ProfileCard(member) }

                if (canManageGym) {
                    item {
                        WalletCard(
                            activePlans = uiState.activePlans,
                            onGrantPlan = { showGrantPlanDialog = true },
                            onRevoke = viewModel::revokeActivePlan
                        )
                    }
                }

                item { Text("Upcoming Bookings (${uiState.upcomingBookings.size})", style = MaterialTheme.typography.titleMedium) }
                if (uiState.upcomingBookings.isEmpty()) {
                    item { Text("No upcoming bookings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(uiState.upcomingBookings, key = { it.id }) { gymClass ->
                        BookingRow(
                            gymClass = gymClass,
                            onCancel = if (canManageGym) ({ classToCancel = gymClass }) else null
                        )
                    }
                }

                item { Text("Past Classes (${uiState.pastBookings.size})", style = MaterialTheme.typography.titleMedium) }
                if (uiState.pastBookings.isEmpty()) {
                    item { Text("No past classes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(uiState.pastBookings, key = { it.id }) { gymClass -> BookingRow(gymClass = gymClass, onCancel = null) }
                }

                if (canRemoveMember) {
                    item {
                        OutlinedButton(
                            onClick = { showRemoveConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Remove Member from Gym") }
                    }
                }
            }
        }
    }

    if (showGrantPlanDialog) {
        GrantPlanDialog(
            plans = uiState.availablePlans,
            onGrant = { plan, customExpiresAtMillis -> viewModel.grantPlan(plan, customExpiresAtMillis); showGrantPlanDialog = false },
            onDismiss = { showGrantPlanDialog = false }
        )
    }

    classToCancel?.let { gymClass ->
        CancelBookingDialog(
            classTitle = gymClass.title,
            onConfirm = { viewModel.cancelBooking(gymClass.id); classToCancel = null },
            onDismiss = { classToCancel = null }
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove ${member.fullName} from the gym?") },
            text = { Text("This cannot be undone. Their bookings and credit wallet will be removed.") },
            confirmButton = {
                TextButton(onClick = { showRemoveConfirm = false; viewModel.removeMember() }) {
                    Text("Remove Member", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileCard(member: GymMember) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledRow("Name", member.fullName)
            LabeledRow("Email", member.email)
            LabeledRow("Joined", SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(member.joinedAtMillis)))
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WalletCard(activePlans: List<ActivePlanItem>, onGrantPlan: () -> Unit, onRevoke: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Credit Wallet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (activePlans.isEmpty()) {
                Text(
                    "No active plans",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activePlans.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        WalletItemRow(item = item, onRevoke = { onRevoke(item.id) })
                    }
                }
            }
            TextButton(onClick = onGrantPlan, modifier = Modifier.padding(top = 8.dp)) { Text("+ Grant Plan") }
        }
    }
}

private val walletExpirationFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
private fun WalletItemRow(item: ActivePlanItem, onRevoke: () -> Unit) {
    val scope = item.workoutType?.let { " ($it)" } ?: ""
    val detail = if (item.type == PlanComponentType.UNLIMITED) "Unlimited access$scope" else "${item.remainingCredits} credits remaining$scope"
    val expiresText = Instant.ofEpochMilli(item.expiresAtMillis).atZone(ZoneId.systemDefault()).format(walletExpirationFormatter)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.planName, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Expires $expiresText",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRevoke) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun BookingRow(gymClass: GymClass, onCancel: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(gymClass.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${gymClass.formattedDayDate} · ${gymClass.formattedTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onCancel != null) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel booking", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private val monthlyBadgeColor = Color(0xFF1976D2)
private val classPassBadgeColor = Color(0xFFEF6C00)

@Composable
private fun PlanCategoryBadge(type: PlanType) {
    val color = if (type == PlanType.MONTHLY) monthlyBadgeColor else classPassBadgeColor
    Text(
        text = type.shortName,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrantPlanDialog(plans: List<MembershipPlan>, onGrant: (MembershipPlan, Long?) -> Unit, onDismiss: () -> Unit) {
    var selectedPlan by remember { mutableStateOf<MembershipPlan?>(null) }
    var useCustomExpiration by remember { mutableStateOf(false) }
    var customExpirationMillis by remember {
        mutableStateOf(Instant.now().atZone(ZoneId.systemDefault()).plusMonths(1).toInstant().toEpochMilli())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Grant Plan") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = {
                        TextButton(
                            enabled = selectedPlan != null,
                            onClick = {
                                selectedPlan?.let { onGrant(it, if (useCustomExpiration) customExpirationMillis else null) }
                            }
                        ) { Text("Grant") }
                    }
                )
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select Plan Template", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plans.isEmpty()) {
                    Text(
                        "No plans yet — create one in Manage → Plans first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        plans.forEach { plan ->
                            Card(onClick = { selectedPlan = plan }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(plan.name, style = MaterialTheme.typography.titleSmall)
                                            PlanCategoryBadge(plan.type)
                                        }
                                        Text(plan.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (selectedPlan?.id == plan.id) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedPlan != null) {
                    Text("Expiration Options", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Set Custom Expiration Date", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = useCustomExpiration, onCheckedChange = { useCustomExpiration = it })
                    }
                    if (useCustomExpiration) {
                        val expiresText = Instant.ofEpochMilli(customExpirationMillis).atZone(ZoneId.systemDefault()).format(walletExpirationFormatter)
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Expires On: $expiresText")
                        }
                    } else {
                        Text(
                            "Will use default expiration defined by plan components.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        GrantPlanDatePickerDialog(
            initialMillis = customExpirationMillis,
            onConfirm = { customExpirationMillis = it },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun GrantPlanDatePickerDialog(initialMillis: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val initialDate = Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val initialUtcMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}
