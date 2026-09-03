package com.nexo.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlanComponent
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import com.nexo.app.domain.model.PlanType
import com.nexo.app.domain.model.ValidityUnit
import java.util.UUID

@Composable
fun MembershipPlansScreen(repository: BackendRepository, gymId: String, workoutTypes: List<String>) {
    val viewModel: MembershipPlansViewModel = viewModel(
        factory = viewModelFactory { initializer { MembershipPlansViewModel(repository, gymId) } },
        key = gymId
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var planToEdit by remember { mutableStateOf<MembershipPlan?>(null) }
    var planToDelete by remember { mutableStateOf<MembershipPlan?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create Plan")
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = "${uiState.plans.size} Plans",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            when {
                uiState.isLoading -> Column(
                    Modifier.fillMaxSize().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }

                uiState.plans.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No Membership Plans", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a plan to start granting memberships to your members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.plans, key = { it.id }) { plan ->
                        PlanRow(plan, onEdit = { planToEdit = plan }, onDelete = { planToDelete = plan })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlanFormDialog(
            existingPlan = null,
            workoutTypes = workoutTypes,
            isSaving = uiState.isSaving,
            onDismiss = { showCreateDialog = false },
            onSave = { plan ->
                viewModel.createPlan(plan)
                showCreateDialog = false
            }
        )
    }

    planToEdit?.let { plan ->
        PlanFormDialog(
            existingPlan = plan,
            workoutTypes = workoutTypes,
            isSaving = uiState.isSaving,
            onDismiss = { planToEdit = null },
            onSave = { updated ->
                viewModel.updatePlan(updated)
                planToEdit = null
            }
        )
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete \"${plan.name}\"?") },
            text = { Text("This cannot be undone. Members who already have this plan granted keep their wallet items.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlan(plan.id)
                    planToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { planToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PlanRow(plan: MembershipPlan, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = plan.name, style = MaterialTheme.typography.titleMedium)
                    Text(text = if (plan.price % 1.0 == 0.0) "%.0f".format(plan.price) else "%.2f".format(plan.price), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlanTypeTag(plan.type)
                plan.components.forEach { component ->
                    Text(
                        text = "• ${component.summary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit plan")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete plan")
            }
        }
    }
}

private val monthlyTagColor = Color(0xFF1976D2)
private val classPassTagColor = Color(0xFFEF6C00)

@Composable
private fun PlanTypeTag(type: PlanType) {
    Text(
        text = type.displayName,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (type == PlanType.MONTHLY) monthlyTagColor else classPassTagColor
    )
}

private val quickValidityMonths = listOf(1, 2, 3, 6, 12)

private fun defaultComponentFor(type: PlanType): PlanComponent = if (type == PlanType.MONTHLY) {
    PlanComponent(type = PlanComponentType.UNLIMITED, validityValue = 1, validityUnit = ValidityUnit.MONTHS)
} else {
    PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10, validityValue = 3, validityUnit = ValidityUnit.MONTHS)
}

private fun defaultNameFor(type: PlanType): String = if (type == PlanType.MONTHLY) "Monthly Unlimited" else "10-Class Pass"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanFormDialog(
    existingPlan: MembershipPlan?,
    workoutTypes: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (MembershipPlan) -> Unit
) {
    val isEditMode = existingPlan != null
    var type by remember { mutableStateOf(existingPlan?.type ?: PlanType.MONTHLY) }
    var name by remember { mutableStateOf(existingPlan?.name ?: defaultNameFor(type)) }
    var priceText by remember { mutableStateOf(existingPlan?.price?.toString().orEmpty()) }
    var components by remember { mutableStateOf(existingPlan?.components ?: listOf(defaultComponentFor(type))) }

    val isValid = name.isNotBlank() && components.isNotEmpty() && priceText.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Edit Plan" else "New Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Plan Category", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PlanType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = type == option,
                            onClick = {
                                // When creating a new plan, suggest reasonable defaults when switching category.
                                if (!isEditMode) {
                                    if (name == "Monthly Unlimited" || name == "10-Class Pass" || name.isBlank()) {
                                        name = defaultNameFor(option)
                                    }
                                    if (components.size <= 1) {
                                        components = listOf(defaultComponentFor(option))
                                    }
                                }
                                type = option
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, PlanType.entries.size)
                        ) { Text(option.displayName) }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan Name (e.g. Gold Unlimited, 10-Class Pass)") },
                    singleLine = true
                )
                OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Price") }, singleLine = true)

                Text("Included Access / Components (${components.size})", style = MaterialTheme.typography.labelLarge)

                components.forEachIndexed { index, component ->
                    ComponentEditor(
                        component = component,
                        workoutTypes = workoutTypes,
                        onChange = { updated -> components = components.toMutableList().also { it[index] = updated } },
                        onRemove = if (components.size > 1) {
                            { components = components.toMutableList().also { it.removeAt(index) } }
                        } else null
                    )
                }

                TextButton(onClick = { components = components + defaultComponentFor(type) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Add Another Component")
                }
                Text(
                    "Customize what this plan unlocks. Add extra components for hybrid plans (e.g. Unlimited CrossFit + 4 Pilates credits).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid && !isSaving,
                onClick = {
                    onSave(
                        MembershipPlan(
                            id = existingPlan?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = type,
                            price = priceText.toDoubleOrNull() ?: 0.0,
                            components = components
                        )
                    )
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEditMode) "Save" else "Create")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentEditor(
    component: PlanComponent,
    workoutTypes: List<String>,
    onChange: (PlanComponent) -> Unit,
    onRemove: (() -> Unit)?
) {
    var classTypeMenuExpanded by remember { mutableStateOf(false) }
    val isStandardShortcut = component.validityUnit == ValidityUnit.MONTHS && component.validityValue in quickValidityMonths
    val isCustom = !isStandardShortcut

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    PlanComponentType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = component.type == option,
                            onClick = { onChange(component.copy(type = option)) },
                            shape = SegmentedButtonDefaults.itemShape(index, PlanComponentType.entries.size)
                        ) { Text(option.displayName) }
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove component", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            ExposedDropdownMenuBox(expanded = classTypeMenuExpanded, onExpandedChange = { classTypeMenuExpanded = it }) {
                OutlinedTextField(
                    value = component.workoutType ?: "All Classes",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Class Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classTypeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = classTypeMenuExpanded, onDismissRequest = { classTypeMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("All Classes") }, onClick = { onChange(component.copy(workoutType = null)); classTypeMenuExpanded = false })
                    workoutTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type) }, onClick = { onChange(component.copy(workoutType = type)); classTypeMenuExpanded = false })
                    }
                }
            }

            if (component.type == PlanComponentType.CREDITS) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PlanResetPeriod.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = component.resetPeriod == option,
                            onClick = { onChange(component.copy(resetPeriod = option)) },
                            shape = SegmentedButtonDefaults.itemShape(index, PlanResetPeriod.entries.size)
                        ) { Text(option.displayName) }
                    }
                }
                OutlinedTextField(
                    value = component.creditCount.toString(),
                    onValueChange = { text -> text.toIntOrNull()?.let { onChange(component.copy(creditCount = it)) } },
                    label = { Text(if (component.resetPeriod == PlanResetPeriod.MONTHLY) "Credits / Month" else "Total Credits") },
                    singleLine = true
                )
            }

            Text("Validity Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickValidityMonths.forEach { months ->
                    val isSelected = component.validityUnit == ValidityUnit.MONTHS && component.validityValue == months
                    ValidityChip(
                        label = if (months == 12) "1 Yr" else "$months Mo",
                        selected = isSelected,
                        onClick = { onChange(component.copy(validityUnit = ValidityUnit.MONTHS, validityValue = months)) }
                    )
                }
                ValidityChip(
                    label = "Custom",
                    selected = isCustom,
                    onClick = {
                        if (!isCustom) onChange(component.copy(validityUnit = ValidityUnit.DAYS, validityValue = 30))
                    }
                )
            }

            if (isCustom) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = component.validityValue.toString(),
                        onValueChange = { text -> text.toIntOrNull()?.let { onChange(component.copy(validityValue = it)) } },
                        label = { Text("Valid for") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    var unitMenuExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = unitMenuExpanded, onExpandedChange = { unitMenuExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = component.validityUnit.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                            ValidityUnit.entries.forEach { unit ->
                                DropdownMenuItem(text = { Text(unit.displayName) }, onClick = { onChange(component.copy(validityUnit = unit)); unitMenuExpanded = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
