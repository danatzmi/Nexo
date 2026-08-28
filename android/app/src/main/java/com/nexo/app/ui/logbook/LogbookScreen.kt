package com.nexo.app.ui.logbook

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.WorkoutLog
import com.nexo.app.domain.model.formattedDetail
import com.nexo.app.ui.components.StatCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogbookSegment(val label: String) {
    ACTIVITY("Activity"),
    EXERCISES("Exercises")
}

/** Mirrors iOS's `LogbookView` — an "Activity" segment (stats + attended-class timeline) and an "Exercises" segment (movements grouped with their personal record, tap for full history). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(repository: BackendRepository, gymId: String) {
    val viewModel: LogbookViewModel = viewModel(
        factory = viewModelFactory { initializer { LogbookViewModel(repository, gymId) } },
        key = gymId
    )
    val uiState by viewModel.uiState.collectAsState()
    var segment by remember { mutableStateOf(LogbookSegment.ACTIVITY) }
    var editingLog by remember { mutableStateOf<WorkoutLog?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedMovement by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (segment == LogbookSegment.EXERCISES) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Log Activity")
                }
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                LogbookSegment.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = segment == option,
                        onClick = { segment = option },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LogbookSegment.entries.size)
                    ) { Text(option.label) }
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                segment == LogbookSegment.ACTIVITY -> ActivitySegment(uiState)
                else -> ExercisesSegment(uiState, onMovementClick = { selectedMovement = it })
            }
        }
    }

    if (showAddDialog) {
        ActivityEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { movement, score, reps, sets ->
                viewModel.addLog(movement, score, reps, sets, System.currentTimeMillis())
                showAddDialog = false
            }
        )
    }
    editingLog?.let { log ->
        ActivityEditDialog(
            initial = log,
            onDismiss = { editingLog = null },
            onSave = { movement, score, reps, sets ->
                viewModel.updateLog(log, movement, score, reps, sets, log.dateMillis)
                editingLog = null
            }
        )
    }
    selectedMovement?.let { movement ->
        MovementHistorySheet(
            movement = movement,
            logs = uiState.logs(movement),
            onEdit = { editingLog = it },
            onDelete = { viewModel.deleteLog(it.id) },
            onDismiss = { selectedMovement = null }
        )
    }
}

// MARK: - Activity Segment

@Composable
private fun ActivitySegment(uiState: LogbookViewModel.UiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "Total Sessions", value = uiState.totalWorkouts.toString(), modifier = Modifier.weight(1f))
                StatCard(title = "Weekly Avg (${uiState.previousMonthLabel})", value = uiState.formattedWeeklyAverage, modifier = Modifier.weight(1f))
            }
        }

        item { Text(text = "Activity Timeline", style = MaterialTheme.typography.titleMedium) }

        if (uiState.activityTimeline.isEmpty()) {
            item {
                Text(
                    "No completed classes yet — book and attend a class to start your timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(uiState.activityTimeline, key = { it.id }) { gymClass -> TimelineRow(gymClass) }
        }
    }
}

@Composable
private fun TimelineRow(gymClass: GymClass) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = gymClass.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${gymClass.formattedDayDate} · ${gymClass.formattedTime}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Exercises Segment

@Composable
private fun ExercisesSegment(uiState: LogbookViewModel.UiState, onMovementClick: (String) -> Unit) {
    if (uiState.displayedMovements.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No Logged Activities Yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Track building blocks or activities for any gym type — lifts, classes, or bodyweight sessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.displayedMovements, key = { it }) { movement ->
                MovementCard(movement = movement, pr = uiState.personalRecords[movement], onClick = { onMovementClick(movement) })
            }
        }
    }
}

@Composable
private fun MovementCard(movement: String, pr: WorkoutLog?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = movement, style = MaterialTheme.typography.titleMedium)
                if (pr != null) {
                    Text(text = pr.formattedDetail, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${if (pr.score != null) "PR" else "Last logged"} on ${formatDate(pr.dateMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("No sessions logged yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementHistorySheet(
    movement: String,
    logs: List<WorkoutLog>,
    onEdit: (WorkoutLog) -> Unit,
    onDelete: (WorkoutLog) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(movement) },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Done") } }
                )
            }
        ) { innerPadding ->
            if (logs.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No Logs Yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Log a session for $movement to see it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        HistoryLogRow(log = log, onEdit = { onEdit(log) }, onDelete = { onDelete(log) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLogRow(log: WorkoutLog, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = log.formattedDetail, style = MaterialTheme.typography.titleMedium)
                Text(text = formatDate(log.dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
private fun ActivityEditDialog(
    initial: WorkoutLog?,
    onDismiss: () -> Unit,
    onSave: (movement: String, score: Double?, reps: Int?, sets: Int?) -> Unit
) {
    var movement by remember { mutableStateOf(initial?.movement.orEmpty()) }
    var scoreText by remember { mutableStateOf(initial?.score?.toString().orEmpty()) }
    var repsText by remember { mutableStateOf(initial?.reps?.toString().orEmpty()) }
    var setsText by remember { mutableStateOf(initial?.sets?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log Activity" else "Edit Activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = movement,
                    onValueChange = { movement = it },
                    label = { Text("e.g. Squat, Yoga Flow, Heavy Bag Work") },
                    singleLine = true
                )
                OutlinedTextField(value = scoreText, onValueChange = { scoreText = it }, label = { Text("Score / Value — optional") }, singleLine = true)
                OutlinedTextField(value = repsText, onValueChange = { repsText = it }, label = { Text("Reps — optional") }, singleLine = true)
                OutlinedTextField(value = setsText, onValueChange = { setsText = it }, label = { Text("Sets — optional") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = movement.isNotBlank(),
                onClick = {
                    onSave(
                        movement.trim(),
                        scoreText.toDoubleOrNull(),
                        repsText.toIntOrNull(),
                        setsText.toIntOrNull()
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
