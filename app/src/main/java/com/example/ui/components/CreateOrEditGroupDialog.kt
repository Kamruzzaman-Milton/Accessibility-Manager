package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AccessibilityServiceItem
import com.example.model.AppGroupUiModel

@Composable
fun CreateOrEditGroupDialog(
    editingGroup: AppGroupUiModel?,
    availableServices: List<AccessibilityServiceItem>,
    onSave: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var groupName by remember {
        mutableStateOf(editingGroup?.name ?: "")
    }
    var selectedServiceKeys by remember {
        mutableStateOf(
            editingGroup?.memberServices?.map { it.componentFlattened }?.toSet() ?: emptySet()
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    val filteredServices = remember(availableServices, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            availableServices
        } else {
            availableServices.filter {
                it.appName.lowercase().contains(q) ||
                        it.packageName.lowercase().contains(q) ||
                        it.serviceLabel.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingGroup == null) "Create New Group" else "Edit Group",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Group Name Input
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        nameError = false
                    },
                    label = { Text("Group Name") },
                    placeholder = { Text("e.g., Work Tools, Gaming, Battery Saver") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Group name cannot be empty") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // App Selection Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Assign Apps (${selectedServiceKeys.size} selected)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row {
                        TextButton(
                            onClick = {
                                selectedServiceKeys = availableServices.map { it.componentFlattened }.toSet()
                            }
                        ) {
                            Text("All", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = { selectedServiceKeys = emptySet() }
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Search Filter for apps
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter apps…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable List of Apps with Checkboxes
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(filteredServices, key = { it.componentFlattened }) { service ->
                        val isChecked = selectedServiceKeys.contains(service.componentFlattened)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedServiceKeys = if (isChecked) {
                                        selectedServiceKeys - service.componentFlattened
                                    } else {
                                        selectedServiceKeys + service.componentFlattened
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedServiceKeys = if (checked) {
                                        selectedServiceKeys + service.componentFlattened
                                    } else {
                                        selectedServiceKeys - service.componentFlattened
                                    }
                                }
                            )

                            // App Icon
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (service.appIcon != null) {
                                    Image(
                                        bitmap = service.appIcon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccessibilityNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = service.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = service.serviceLabel.ifBlank { service.serviceSimpleName },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        nameError = true
                    } else {
                        onSave(groupName.trim(), selectedServiceKeys.toList())
                    }
                },
                modifier = Modifier.testTag("save_group_button")
            ) {
                Text(if (editingGroup == null) "Create Group" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_group_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
