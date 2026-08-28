package com.nexo.app.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.RecurrenceType
import com.nexo.app.domain.model.applyTimeOfDay
import com.nexo.app.domain.model.formattedAbbreviatedDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private val WEEKDAY_SYMBOLS = listOf("S", "M", "T", "W", "T", "F", "S") // index 0 = weekday 1 (Sunday) .. index 6 = weekday 7 (Saturday)

/** Matches iOS's `AddClassView` — create or edit a class, mirroring the section-by-section layout (Class Details, Description, Schedule, Capacity, Repeat). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClassSheet(
    repository: BackendRepository,
    gymId: String,
    availableClassTypes: List<String>,
    existingClass: GymClass?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: AddClassViewModel = viewModel(
        factory = viewModelFactory { initializer { AddClassViewModel(repository, gymId, availableClassTypes, existingClass) } }
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.didSave) {
        if (uiState.didSave) onSaved()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (viewModel.isEditMode) "Edit Class" else "New Class") },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ClassDetailsCard(
                    classType = uiState.classType,
                    availableClassTypes = availableClassTypes,
                    onClassTypeChange = viewModel::updateClassType,
                    coach = uiState.coach,
                    availableCoaches = uiState.availableCoaches,
                    onCoachChange = viewModel::updateCoach,
                    isPremium = uiState.isPremium,
                    onIsPremiumChange = viewModel::updateIsPremium
                )

                DescriptionCard(description = uiState.description, onDescriptionChange = viewModel::updateDescription)

                ScheduleCard(
                    startTimeMillis = uiState.startTimeMillis,
                    durationMinutes = uiState.durationMinutes,
                    onOpenDatePicker = { showDatePicker = true },
                    onOpenTimePicker = { showTimePicker = true },
                    onDurationChange = viewModel::updateDurationMinutes
                )

                CapacityCard(capacity = uiState.capacity, onCapacityChange = viewModel::updateCapacity)

                if (!viewModel.isEditMode) {
                    RepeatCard(
                        recurrenceType = uiState.recurrenceType,
                        onRecurrenceTypeChange = viewModel::updateRecurrenceType,
                        selectedWeekdays = uiState.selectedWeekdays,
                        onToggleWeekday = viewModel::toggleWeekday,
                        repeatEndMillis = uiState.repeatEndMillis,
                        onOpenEndDatePicker = { showEndDatePicker = true }
                    )
                }

                Button(
                    onClick = viewModel::handleSaveTapped,
                    enabled = uiState.isValid && !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (viewModel.isEditMode) "Save Changes" else "Create Class", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        AddClassDatePickerDialog(
            initialMillis = uiState.startTimeMillis,
            onConfirm = { newDateMillis -> viewModel.updateStartTime(applyTimeOfDay(newDateMillis, uiState.startTimeMillis)) },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        AddClassTimePickerDialog(
            initialMillis = uiState.startTimeMillis,
            onConfirm = { newTimeMillis -> viewModel.updateStartTime(applyTimeOfDay(uiState.startTimeMillis, newTimeMillis)) },
            onDismiss = { showTimePicker = false }
        )
    }
    if (showEndDatePicker) {
        AddClassDatePickerDialog(
            initialMillis = uiState.repeatEndMillis,
            onConfirm = { viewModel.updateRepeatEndMillis(it) },
            onDismiss = { showEndDatePicker = false }
        )
    }

    if (uiState.showSeriesPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSeriesPrompt,
            title = { Text("Update this class only or all future occurrences in the series?") },
            text = {},
            confirmButton = {
                TextButton(onClick = { viewModel.save(applyToSeries = true) }) { Text("This & Future Classes") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.save(applyToSeries = false) }) { Text("This Class Only") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassDetailsCard(
    classType: String,
    availableClassTypes: List<String>,
    onClassTypeChange: (String) -> Unit,
    coach: String,
    availableCoaches: List<String>,
    onCoachChange: (String) -> Unit,
    isPremium: Boolean,
    onIsPremiumChange: (Boolean) -> Unit
) {
    FormCard(title = "Class Details") {
        LabeledDropdown(
            label = "Class Type",
            value = classType,
            options = availableClassTypes,
            onSelect = onClassTypeChange
        )
        LabeledDropdown(
            label = "Coach",
            value = coach.ifBlank { "Unassigned" },
            options = listOf("Unassigned") + availableCoaches,
            onSelect = { onCoachChange(if (it == "Unassigned") "" else it) }
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Requires Additional Pay")
            Switch(checked = isPremium, onCheckedChange = onIsPremiumChange)
        }
    }
}

@Composable
private fun DescriptionCard(description: String, onDescriptionChange: (String) -> Unit) {
    FormCard(title = "Description") {
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            placeholder = { Text("Workout notes, WOD, etc.") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
    }
}

@Composable
private fun ScheduleCard(
    startTimeMillis: Long,
    durationMinutes: Int,
    onOpenDatePicker: () -> Unit,
    onOpenTimePicker: () -> Unit,
    onDurationChange: (Int) -> Unit
) {
    val zoned = Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault())
    FormCard(title = "Schedule") {
        FormRow(label = "Date", value = formattedAbbreviatedDate(zoned.toLocalDate()), onClick = onOpenDatePicker)
        FormRow(label = "Time", value = "%02d:%02d".format(zoned.hour, zoned.minute), onClick = onOpenTimePicker)
        StepperRow(label = "Duration", value = durationMinutes, suffix = " min", step = 5, range = 15..180, onValueChange = onDurationChange)
    }
}

@Composable
private fun CapacityCard(capacity: Int, onCapacityChange: (Int) -> Unit) {
    FormCard(title = "Capacity") {
        StepperRow(label = "Max participants", value = capacity, suffix = "", step = 1, range = 1..50, onValueChange = onCapacityChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatCard(
    recurrenceType: RecurrenceType,
    onRecurrenceTypeChange: (RecurrenceType) -> Unit,
    selectedWeekdays: Set<Int>,
    onToggleWeekday: (Int) -> Unit,
    repeatEndMillis: Long,
    onOpenEndDatePicker: () -> Unit
) {
    FormCard(title = "Repeat") {
        LabeledDropdown(
            label = "Repeats",
            value = recurrenceType.displayName(),
            options = RecurrenceType.entries.map { it.displayName() },
            onSelect = { label -> onRecurrenceTypeChange(RecurrenceType.entries.first { it.displayName() == label }) }
        )
        if (recurrenceType == RecurrenceType.CUSTOM) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { day ->
                    WeekdayChip(symbol = WEEKDAY_SYMBOLS[day - 1], isSelected = day in selectedWeekdays, onClick = { onToggleWeekday(day) })
                }
            }
        }
        if (recurrenceType != RecurrenceType.NONE) {
            FormRow(label = "End Date", value = formattedAbbreviatedDate(Instant.ofEpochMilli(repeatEndMillis).atZone(ZoneId.systemDefault()).toLocalDate()), onClick = onOpenEndDatePicker)
        }
    }
}

private fun RecurrenceType.displayName(): String = when (this) {
    RecurrenceType.NONE -> "Never"
    RecurrenceType.DAILY -> "Every Day"
    RecurrenceType.WEEKLY -> "Weekly"
    RecurrenceType.BIWEEKLY -> "Bi-weekly"
    RecurrenceType.MONTHLY -> "Monthly"
    RecurrenceType.CUSTOM -> "Custom Days"
}

@Composable
private fun WeekdayChip(symbol: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun FormRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        TextButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, suffix: String, step: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: $value$suffix")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }, enabled = value - step >= range.first) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }, enabled = value + step <= range.last) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddClassDatePickerDialog(initialMillis: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddClassTimePickerDialog(initialMillis: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val initialTime = Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    val state = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                val time = LocalTime.of(state.hour, state.minute)
                val today = LocalDate.now()
                onConfirm(today.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
