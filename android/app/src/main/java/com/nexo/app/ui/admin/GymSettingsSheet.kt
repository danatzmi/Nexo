package com.nexo.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexo.app.data.repository.BackendRepository
import kotlinx.coroutines.launch

/** Owner/Admin-only gym settings — rename the gym and manage its class types. Mirrors `GymSettingsSheet` on iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymSettingsSheet(
    repository: BackendRepository,
    gymId: String,
    initialName: String,
    initialWorkoutTypes: List<String>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var workoutTypes by remember { mutableStateOf(initialWorkoutTypes) }
    var newCategoryName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Gym Settings", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Gym Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text("Class Types", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                workoutTypes.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(category, style = MaterialTheme.typography.bodyMedium)
                        if (workoutTypes.size > 1) {
                            IconButton(onClick = { workoutTypes = workoutTypes - category }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove $category")
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("e.g. Spinning") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val trimmed = newCategoryName.trim()
                        newCategoryName = ""
                        if (trimmed.isNotEmpty() && trimmed !in workoutTypes) {
                            workoutTypes = workoutTypes + trimmed
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add class type")
                    }
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    enabled = name.isNotBlank() && !isSaving,
                    onClick = {
                        isSaving = true
                        errorMessage = null
                        scope.launch {
                            try {
                                repository.updateGymSettings(gymId, name.trim(), workoutTypes)
                                onSaved()
                            } catch (e: Exception) {
                                errorMessage = "Error saving gym settings: ${e.message}"
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                ) { Text("Save") }
            }
        }
    }
}
