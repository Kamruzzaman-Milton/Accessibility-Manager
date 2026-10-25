package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun EnableAllConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Confirmation",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "Enable All Services?")
        },
        text = {
            Text(
                text = "Are you sure you want to enable all $count detected accessibility services? " +
                        "Running multiple accessibility services simultaneously may affect battery life and device responsiveness."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_enable_all_button")
            ) {
                Text("Enable All")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_enable_all_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DisableAllConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = "Disable All Services?")
        },
        text = {
            Text(
                text = "This will immediately turn off all currently active accessibility services. " +
                        "Apps that rely on accessibility (such as gesture navigators, text expanders, and automation tools) will stop working."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_disable_all_button")
            ) {
                Text("Disable All")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_disable_all_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
