package com.nexo.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.canEditGymSettings

@Composable
fun TeamScreen(repository: BackendRepository, gymId: String, userRole: UserRole?, platformRole: PlatformRole) {
    val viewModel: TeamViewModel = viewModel(
        factory = viewModelFactory { initializer { TeamViewModel(repository, gymId) } },
        key = gymId
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedMember by remember { mutableStateOf<TeamMember?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    val canManageTeam = canEditGymSettings(userRole, platformRole)

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Team Member")
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = "${uiState.team.size} Members",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            OutlinedTextField(
                value = uiState.searchText,
                onValueChange = viewModel::updateSearchText,
                placeholder = { Text("Search team") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            when {
                uiState.isLoading -> Column(
                    Modifier.fillMaxSize().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }

                uiState.team.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No Team Members", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add team members to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.filteredTeam.isEmpty() -> Text(
                    "No results for \"${uiState.searchText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredTeam, key = { it.id }) { member ->
                        TeamMemberRow(member, onClick = { selectedMember = member })
                    }
                }
            }
        }
    }

    selectedMember?.let { member ->
        TeamMemberDetailDialog(
            member = member,
            isSelf = member.id == uiState.currentUID,
            canManageTeam = canManageTeam,
            onDismiss = { selectedMember = null },
            onRoleChange = { role -> viewModel.updateTeamMemberRole(member.id, role) },
            onRemove = {
                viewModel.removeTeamMember(member.id)
                selectedMember = null
            }
        )
    }

    if (showAddSheet) {
        AddTeamMemberDialog(
            isSaving = uiState.isAddingMember,
            canSelectRole = canManageTeam,
            onDismiss = { showAddSheet = false },
            onAddExisting = { email, role, name ->
                viewModel.addTeamMember(email, role, name) { success ->
                    if (success) showAddSheet = false
                }
            },
            onRegister = { firstName, lastName, email, password, role ->
                viewModel.registerTeamMember(firstName, lastName, email, password, role) { success ->
                    if (success) showAddSheet = false
                }
            }
        )
    }
}

@Composable
private fun TeamMemberRow(member: TeamMember, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = member.fullName, style = MaterialTheme.typography.titleMedium)
                Text(text = member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = member.role.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamMemberDetailDialog(
    member: TeamMember,
    isSelf: Boolean,
    canManageTeam: Boolean,
    onDismiss: () -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onRemove: () -> Unit
) {
    var roleMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(member.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(member.email)
                if (canManageTeam) {
                    ExposedDropdownMenuBox(
                        expanded = roleMenuExpanded,
                        onExpandedChange = { if (!isSelf) roleMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = member.role.displayName,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isSelf,
                            label = { Text("Role") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                            listOf(UserRole.OWNER, UserRole.COACH).forEach { option ->
                                DropdownMenuItem(text = { Text(option.displayName) }, onClick = { onRoleChange(option); roleMenuExpanded = false })
                            }
                        }
                    }
                    if (isSelf) {
                        Text(
                            "You can't change your own role.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(member.role.displayName)
                }
            }
        },
        confirmButton = {
            if (canManageTeam) {
                TextButton(onClick = onRemove, enabled = !isSelf) {
                    Text("Remove from Team", color = if (isSelf) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private enum class AddMode(val label: String) { SEARCH("Search"), REGISTER("New Account") }

/** Two-mode add sheet — mirrors iOS's `AddTeamMemberView`: "Search" attaches an existing platform user by email ([onAddExisting]); "New Account" registers a brand-new Auth account via the secondary-`FirebaseApp` mechanism ([onRegister]). [canSelectRole] mirrors iOS's `canSelectRole` (Owner/Platform Admin only) — Coaches inviting someone are restricted to Coach, so the role picker is hidden for them in both modes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTeamMemberDialog(
    isSaving: Boolean,
    canSelectRole: Boolean,
    onDismiss: () -> Unit,
    onAddExisting: (email: String, role: UserRole, name: String) -> Unit,
    onRegister: (firstName: String, lastName: String, email: String, password: String, role: UserRole) -> Unit
) {
    var mode by remember { mutableStateOf(AddMode.SEARCH) }

    // Search mode
    var searchEmail by remember { mutableStateOf("") }
    var searchName by remember { mutableStateOf("") }
    var searchRole by remember { mutableStateOf(UserRole.COACH) }

    // Register mode
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var registerEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registerRole by remember { mutableStateOf(UserRole.COACH) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Team Member") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } }
                )
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AddMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { mode = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AddMode.entries.size)
                        ) { Text(option.label) }
                    }
                }
                Spacer(Modifier.height(20.dp))

                when (mode) {
                    AddMode.SEARCH -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "The person must already have a Nexo account. Enter the email they signed up with.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(value = searchName, onValueChange = { searchName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = searchEmail, onValueChange = { searchEmail = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (canSelectRole) {
                            RolePicker(role = searchRole, onRoleChange = { searchRole = it })
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onAddExisting(searchEmail.trim(), if (canSelectRole) searchRole else UserRole.COACH, searchName.trim()) },
                            enabled = searchEmail.isNotBlank() && searchName.isNotBlank() && !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add to Team")
                        }
                    }

                    AddMode.REGISTER -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Creates a brand-new Nexo account for this person.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = registerEmail,
                            onValueChange = { registerEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (canSelectRole) {
                            RolePicker(role = registerRole, onRoleChange = { registerRole = it })
                        }
                        Spacer(Modifier.height(8.dp))
                        val isValid = firstName.isNotBlank() && registerEmail.contains("@") && password.length >= 6
                        Button(
                            onClick = { onRegister(firstName.trim(), lastName.trim(), registerEmail.trim(), password, if (canSelectRole) registerRole else UserRole.COACH) },
                            enabled = isValid && !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add Team Member")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RolePicker(role: UserRole, onRoleChange: (UserRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = role.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(UserRole.COACH, UserRole.OWNER).forEach { option ->
                DropdownMenuItem(text = { Text(option.displayName) }, onClick = { onRoleChange(option); expanded = false })
            }
        }
    }
}
