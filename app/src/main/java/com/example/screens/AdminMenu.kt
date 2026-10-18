package com.example.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ManageMenu(
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onToggleHide: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    isItemHidden: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onToggleHide?.let {
                DropdownMenuItem(
                    text = { Text(if (isItemHidden) "Show" else "Hide") },
                    onClick = { expanded = false; it() },
                    leadingIcon = { Icon(if (isItemHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, modifier = Modifier.size(16.dp)) }
                )
            }
            onRename?.let {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { expanded = false; it() },
                    leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                )
            }
            onEdit?.let {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { expanded = false; it() },
                    leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                )
            }
            onDelete?.let {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { expanded = false; it() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}
