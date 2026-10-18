package com.example.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.MCQSection

@Composable
fun EditMCQSectionDialog(
    section: MCQSection,
    onDismiss: () -> Unit,
    onSave: (MCQSection) -> Unit
) {
    var editTitle by remember { mutableStateOf(section.title) }
    var editSubtitle by remember { mutableStateOf(section.subtitle) }
    var editEmoji by remember { mutableStateOf(section.emoji) }
    val emojiList = listOf("🎯", "🧠", "🩺", "📚", "🔬", "💊", "🫀", "🧬", "🌟", "🏥", "⚡")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editSubtitle,
                    onValueChange = { editSubtitle = it },
                    label = { Text("Subtitle") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Select Emoji", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(emojiList.size) { index ->
                        val emoji = emojiList[index]
                        Surface(
                            onClick = { editEmoji = emoji },
                            color = if (editEmoji == emoji) Color(0xFFE2E8F0) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(emoji, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    section.copy(
                        title = editTitle.trim(),
                        subtitle = editSubtitle.trim(),
                        emoji = editEmoji
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
