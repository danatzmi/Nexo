package com.nexo.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.formattedAbbreviatedDate
import com.nexo.app.domain.model.weekHeaderTitle
import com.nexo.app.ui.components.BookingSuccessPopup
import com.nexo.app.ui.components.CancelBookingDialog
import com.nexo.app.ui.components.LeaveWaitlistDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(
    repository: BackendRepository,
    gymId: String,
    gymName: String,
    workoutTypes: List<String> = emptyList(),
    canManage: Boolean = false,
    onOpenGymSwitcher: () -> Unit,
    onOpenClassDetail: (String) -> Unit = {}
) {
    val viewModel: ScheduleViewModel = viewModel(
        factory = viewModelFactory { initializer { ScheduleViewModel(repository, gymId) } },
        key = gymId
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddClassSheet by remember { mutableStateOf(false) }
    var classToCancel by remember { mutableStateOf<GymClass?>(null) }
    var classToLeaveWaitlist by remember { mutableStateOf<GymClass?>(null) }
    var showSuccessPopup by remember { mutableStateOf(false) }
    var successPopupTitle by remember { mutableStateOf("Booked!") }
    var successPopupMessage by remember { mutableStateOf("") }
    var successPopupIsWaitlist by remember { mutableStateOf(false) }

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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (canManage) {
                    FloatingActionButton(onClick = { showAddClassSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Class")
                    }
                }
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                ScheduleHeader(
                    gymName = gymName,
                    onOpenGymSwitcher = onOpenGymSwitcher,
                    onOpenDatePicker = { showDatePicker = true }
                )
                WeekDayPicker(
                    weekDates = uiState.weekDates,
                    selectedDate = uiState.selectedDate,
                    onSelectDate = viewModel::selectDate,
                    onShiftWeek = viewModel::shiftWeek
                )
                HorizontalDivider()

                when {
                    uiState.isLoading -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }

                    uiState.classesForSelectedDate.isEmpty() -> EmptyScheduleState(uiState.selectedDate)

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.classesForSelectedDate, key = { it.id }) { gymClass ->
                            ClassRow(
                                gymClass = gymClass,
                                isBooked = gymClass.id in uiState.bookedClassIds,
                                isWaitlisted = gymClass.id in uiState.waitlistedClassIds,
                                onBook = {
                                    viewModel.book(gymClass.id)
                                    successPopupTitle = "Booked!"
                                    successPopupMessage = "${gymClass.title} · ${gymClass.formattedTime}"
                                    successPopupIsWaitlist = false
                                    showSuccessPopup = true
                                },
                                onCancel = { classToCancel = gymClass },
                                onJoinWaitlist = {
                                    viewModel.joinWaitlist(gymClass.id)
                                    successPopupTitle = "Waitlisted!"
                                    successPopupMessage = "${gymClass.title} · ${gymClass.formattedTime}"
                                    successPopupIsWaitlist = true
                                    showSuccessPopup = true
                                },
                                onLeaveWaitlist = { classToLeaveWaitlist = gymClass },
                                onOpenDetail = { onOpenClassDetail(gymClass.id) }
                            )
                        }
                    }
                }
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

    classToCancel?.let { gymClass ->
        CancelBookingDialog(
            classTitle = gymClass.title,
            onConfirm = { viewModel.cancel(gymClass.id); classToCancel = null },
            onDismiss = { classToCancel = null }
        )
    }

    classToLeaveWaitlist?.let { gymClass ->
        LeaveWaitlistDialog(
            classTitle = gymClass.title,
            onConfirm = { viewModel.leaveWaitlist(gymClass.id); classToLeaveWaitlist = null },
            onDismiss = { classToLeaveWaitlist = null }
        )
    }

    if (showDatePicker) {
        SchedulePickDateDialog(
            initialDate = uiState.selectedDate,
            onConfirm = { viewModel.selectDate(it) },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showAddClassSheet) {
        AddClassSheet(
            repository = repository,
            gymId = gymId,
            availableClassTypes = workoutTypes,
            existingClass = null,
            onDismiss = { showAddClassSheet = false },
            onSaved = {
                showAddClassSheet = false
                viewModel.refresh()
            }
        )
    }
}

@Composable
private fun ScheduleHeader(gymName: String, onOpenGymSwitcher: () -> Unit, onOpenDatePicker: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = "Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.clickable(onClick = onOpenGymSwitcher),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = gymName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Switch gym",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onOpenDatePicker) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick a date")
        }
    }
}

@Composable
private fun WeekDayPicker(
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onShiftWeek(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
            }
            Text(
                text = weekHeaderTitle(weekDates),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = { onShiftWeek(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next week")
            }
        }

        var dragAccumulator by remember { mutableFloatStateOf(0f) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragEnd = {
                            when {
                                dragAccumulator < -80f -> onShiftWeek(1)
                                dragAccumulator > 80f -> onShiftWeek(-1)
                            }
                            dragAccumulator = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekDates.forEach { date ->
                DayButton(
                    date = date,
                    isSelected = date == selectedDate,
                    isToday = date == LocalDate.now(),
                    onClick = { onSelectDate(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayButton(date: LocalDate, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val borderModifier = if (isToday && !isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .background(background, shape)
            .then(borderModifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyScheduleState(date: LocalDate) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.EventBusy,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = "No Classes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            text = "No classes scheduled for ${formattedAbbreviatedDate(date)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulePickDateDialog(initialDate: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // Compose's DatePicker works in UTC — a LocalDate must round-trip through UTC millis, not the system zone.
    val initialUtcMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun ClassRow(
    gymClass: GymClass,
    isBooked: Boolean,
    isWaitlisted: Boolean,
    onBook: () -> Unit,
    onCancel: () -> Unit,
    onJoinWaitlist: () -> Unit,
    onLeaveWaitlist: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clickable(onClick = onOpenDetail)) {
                Text(text = gymClass.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${gymClass.formattedTime} · ${gymClass.coach}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = gymClass.formattedAttendeesLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                when {
                    isBooked -> OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    isWaitlisted -> OutlinedButton(onClick = onLeaveWaitlist) { Text("Leave Waitlist") }
                    gymClass.isFull -> Button(onClick = onJoinWaitlist) { Text("Join Waitlist") }
                    else -> Button(onClick = onBook) { Text("Book") }
                }
            }
        }
    }
}
