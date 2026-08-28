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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.canEditGymSettings
import com.nexo.app.domain.model.canManageGym

@Composable
fun GymMembersScreen(repository: BackendRepository, gymId: String, userRole: UserRole?, platformRole: PlatformRole) {
    val viewModel: GymMembersViewModel = viewModel(
        factory = viewModelFactory { initializer { GymMembersViewModel(repository, gymId) } },
        key = gymId
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedMember by remember { mutableStateOf<GymMember?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val canManage = canManageGym(userRole, platformRole)
    val canRemoveMember = canEditGymSettings(userRole, platformRole)

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Member")
                }
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = "${uiState.members.size} Members",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            OutlinedTextField(
                value = uiState.searchText,
                onValueChange = viewModel::updateSearchText,
                placeholder = { Text("Search members") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            when {
                uiState.isLoading -> Column(
                    Modifier.fillMaxSize().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }

                uiState.members.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No Members Yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Members will appear here once they join your gym",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.filteredMembers.isEmpty() -> Text(
                    "No results for \"${uiState.searchText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredMembers, key = { it.id }) { member ->
                        MemberRow(member, onClick = { selectedMember = member })
                    }
                }
            }
        }
    }

    selectedMember?.let { member ->
        MemberDetailSheet(
            repository = repository,
            gymId = gymId,
            member = member,
            canManageGym = canManage,
            canRemoveMember = canRemoveMember,
            onDismiss = { selectedMember = null }
        )
    }

    if (showAddDialog) {
        AddMemberDialog(
            isSaving = uiState.isAddingMember,
            onDismiss = { showAddDialog = false },
            onAddExisting = { email ->
                viewModel.addMember(email) { success ->
                    if (success) showAddDialog = false
                }
            },
            onRegister = { firstName, lastName, email, password ->
                viewModel.registerMember(firstName, lastName, email, password) { success ->
                    if (success) showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun MemberRow(member: GymMember, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = member.fullName, style = MaterialTheme.typography.titleMedium)
                Text(text = member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            member.activePlanName?.let { planName ->
                Text(text = planName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private enum class AddMemberMode(val label: String) { SEARCH("Search"), REGISTER("New Account") }

/** Two-mode add sheet — mirrors iOS's `AddMemberView`: "Search" attaches an existing platform user by email ([onAddExisting]); "New Account" registers a brand-new Auth account via the secondary-`FirebaseApp` mechanism ([onRegister]). No role picker — members are always [UserRole.MEMBER]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onAddExisting: (email: String) -> Unit,
    onRegister: (firstName: String, lastName: String, email: String, password: String) -> Unit
) {
    var mode by remember { mutableStateOf(AddMemberMode.SEARCH) }

    var searchEmail by remember { mutableStateOf("") }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var registerEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Member") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } }
                )
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AddMemberMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { mode = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AddMemberMode.entries.size)
                        ) { Text(option.label) }
                    }
                }
                Spacer(Modifier.height(20.dp))

                when (mode) {
                    AddMemberMode.SEARCH -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "The person must already have a Nexo account. Enter the email they signed up with.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(value = searchEmail, onValueChange = { searchEmail = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onAddExisting(searchEmail.trim()) },
                            enabled = searchEmail.isNotBlank() && !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add to Gym")
                        }
                    }

                    AddMemberMode.REGISTER -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Spacer(Modifier.height(8.dp))
                        val isValid = firstName.isNotBlank() && registerEmail.contains("@") && password.length >= 6
                        Button(
                            onClick = { onRegister(firstName.trim(), lastName.trim(), registerEmail.trim(), password) },
                            enabled = isValid && !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add Member")
                        }
                    }
                }
            }
        }
    }
}
