package com.nexo.app.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.Member
import com.nexo.app.ui.components.AvatarView
import com.nexo.app.ui.components.BookingSuccessPopup
import com.nexo.app.ui.components.CancelBookingDialog
import com.nexo.app.ui.components.LeaveWaitlistDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    repository: BackendRepository,
    gymId: String,
    classId: String,
    workoutTypes: List<String> = emptyList(),
    canManage: Boolean = false,
    onBack: () -> Unit
) {
    val viewModel: ClassDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { ClassDetailViewModel(repository, gymId, classId) } },
        key = "$gymId/$classId"
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showLeaveWaitlistDialog by remember { mutableStateOf(false) }
    var showSuccessPopup by remember { mutableStateOf(false) }
    var successPopupTitle by remember { mutableStateOf("Booked!") }
    var successPopupMessage by remember { mutableStateOf("") }
    var successPopupIsWaitlist by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.didDelete) {
        if (uiState.didDelete) onBack()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Class Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (canManage && uiState.gymClass != null) {
                            IconButton(onClick = { showEditSheet = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit Class")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Class")
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val gymClass = uiState.gymClass
                if (gymClass != null && gymClass.startTimeMillis >= System.currentTimeMillis()) {
                    ClassDetailActionBar(
                        isBooked = uiState.isBooked,
                        isWaitlisted = uiState.isWaitlisted,
                        waitlistPosition = uiState.waitlistPosition,
                        isFull = gymClass.isFull,
                        isActionInProgress = uiState.isActionInProgress,
                        onBook = {
                            viewModel.book()
                            successPopupTitle = "Booked!"
                            successPopupMessage = "${gymClass.title} · ${gymClass.formattedTime}"
                            successPopupIsWaitlist = false
                            showSuccessPopup = true
                        },
                        onCancel = { showCancelDialog = true },
                        onJoinWaitlist = {
                            viewModel.joinWaitlist()
                            successPopupTitle = "Waitlisted!"
                            successPopupMessage = "${gymClass.title} · ${gymClass.formattedTime}"
                            successPopupIsWaitlist = true
                            showSuccessPopup = true
                        },
                        onLeaveWaitlist = { showLeaveWaitlistDialog = true }
                    )
                }
            }
        ) { innerPadding ->
            val gymClass = uiState.gymClass
            if (gymClass == null) {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(text = gymClass.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                ClassInfoCard(gymClass)

                if (gymClass.description.isNotBlank()) {
                    WorkoutDetailsCard(gymClass.description)
                }

                AttendeesCard(
                    gymClass = gymClass,
                    attendees = uiState.attendees,
                    checkedInCount = uiState.checkedInCount,
                    isLoadingAttendees = uiState.isLoadingAttendees,
                    isWaitlisted = uiState.isWaitlisted,
                    waitlistPosition = uiState.waitlistPosition,
                    canManage = canManage,
                    onToggleAttendance = viewModel::toggleAttendance
                )
            }
        }

        BookingSuccessPopup(
            visible = showSuccessPopup,
            title = successPopupTitle,
            message = successPopupMessage,
            isWaitlist = successPopupIsWaitlist,
            onDismissed = { showSuccessPopup = false }
        )
    }

    if (showCancelDialog) {
        CancelBookingDialog(
            classTitle = uiState.gymClass?.title.orEmpty(),
            onConfirm = { viewModel.cancel(); showCancelDialog = false },
            onDismiss = { showCancelDialog = false }
        )
    }

    if (showLeaveWaitlistDialog) {
        LeaveWaitlistDialog(
            classTitle = uiState.gymClass?.title.orEmpty(),
            onConfirm = { viewModel.leaveWaitlist(); showLeaveWaitlistDialog = false },
            onDismiss = { showLeaveWaitlistDialog = false }
        )
    }

    if (showEditSheet) {
        AddClassSheet(
            repository = repository,
            gymId = gymId,
            availableClassTypes = workoutTypes,
            existingClass = uiState.gymClass,
            onDismiss = { showEditSheet = false },
            onSaved = {
                showEditSheet = false
                viewModel.loadAttendees()
            }
        )
    }

    if (showDeleteConfirm) {
        val gymClass = uiState.gymClass
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${gymClass?.title.orEmpty()}\"?") },
            text = {
                Text(if (gymClass?.seriesId != null) "This is part of a recurring series." else "This cannot be undone.")
            },
            confirmButton = {
                if (gymClass?.seriesId != null) {
                    TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteClass(applyToSeries = true) }) {
                        Text("Delete This & Future", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteClass(applyToSeries = false) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                Column {
                    if (gymClass?.seriesId != null) {
                        TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteClass(applyToSeries = false) }) {
                            Text("Delete This Class Only", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun ClassInfoCard(gymClass: GymClass) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            InfoRow(icon = Icons.Filled.CalendarMonth, label = "Date", value = gymClass.formattedFullDate)
            HorizontalDivider()
            InfoRow(icon = Icons.Filled.AccessTime, label = "Time", value = gymClass.formattedTimeRange)
            HorizontalDivider()
            InfoRow(icon = Icons.Filled.Person, label = "Coach", value = gymClass.formattedCoach)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WorkoutDetailsCard(description: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "WORKOUT DETAILS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(text = description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AttendeesCard(
    gymClass: GymClass,
    attendees: List<Member>,
    checkedInCount: Int,
    isLoadingAttendees: Boolean,
    isWaitlisted: Boolean,
    waitlistPosition: Int?,
    canManage: Boolean,
    onToggleAttendance: (Member) -> Unit
) {
    val spots = "${gymClass.currentAttendees}/${gymClass.capacity}"
    val title = when {
        canManage -> "Attendees ($spots · $checkedInCount Checked In)"
        isWaitlisted && waitlistPosition != null -> "Attendees ($spots · Waitlist: $waitlistPosition/${gymClass.waitlistCount})"
        else -> "Attendees ($spots)"
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            when {
                isLoadingAttendees -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                attendees.isEmpty() -> Text(
                    text = "No attendees yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    attendees.forEach { attendee ->
                        AttendeeRow(
                            attendee = attendee,
                            canManage = canManage,
                            onToggleAttendance = { onToggleAttendance(attendee) }
                        )
                    }
                }
            }
        }
    }
}

// Deliberately muted, desaturated greens (not the system-default bright
// green) for the checked-in state — matches this app's "quiet, premium
// visual language" convention (see the mutedCrimson/mutedSlate/mutedAmber
// colors already used the same way for Schedule row capacity/waitlist text).
private val CheckedInGreen = Color(0xFF2E7D32)
private val CheckedInGreenContainer = Color(0xFFDCEDC8)

@Composable
private fun AttendeeRow(attendee: Member, canManage: Boolean, onToggleAttendance: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvatarView(name = attendee.fullName, profilePicBase64 = attendee.profilePicBase64, size = 40.dp)
        Text(text = attendee.fullName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))

        if (canManage) {
            if (attendee.isCheckedIn) {
                Surface(shape = RoundedCornerShape(50), color = CheckedInGreenContainer, onClick = onToggleAttendance) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = CheckedInGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = attendee.checkedInAtMillis?.let { "Checked in at ${formatCheckInTime(it)}" } ?: "Checked In",
                            style = MaterialTheme.typography.labelSmall,
                            color = CheckedInGreen
                        )
                    }
                }
            } else {
                OutlinedButton(onClick = onToggleAttendance, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Check In", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private val CHECK_IN_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatCheckInTime(millis: Long): String =
    CHECK_IN_TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun ClassDetailActionBar(
    isBooked: Boolean,
    isWaitlisted: Boolean,
    waitlistPosition: Int?,
    isFull: Boolean,
    isActionInProgress: Boolean,
    onBook: () -> Unit,
    onCancel: () -> Unit,
    onJoinWaitlist: () -> Unit,
    onLeaveWaitlist: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                isBooked -> OutlinedButton(
                    onClick = onCancel,
                    enabled = !isActionInProgress,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel Booking")
                    }
                }

                isWaitlisted -> OutlinedButton(
                    onClick = onLeaveWaitlist,
                    enabled = !isActionInProgress,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (waitlistPosition != null) "Leave Waitlist (Position #$waitlistPosition)" else "Leave Waitlist")
                    }
                }

                isFull -> Button(
                    onClick = onJoinWaitlist,
                    enabled = !isActionInProgress,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Join Waitlist")
                    }
                }

                else -> Button(
                    onClick = onBook,
                    enabled = !isActionInProgress,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Book Class")
                    }
                }
            }
        }
    }
}
