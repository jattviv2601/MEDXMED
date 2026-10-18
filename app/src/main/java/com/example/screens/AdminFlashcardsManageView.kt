package com.example.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.MedRepository
import com.example.data.cloud.CloudflareR2Manager
import com.example.model.FlashcardItem
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AdminFlashcardsManageView() {
    val flashcards by MedRepository.flashcards.collectAsState()
    val folders by MedRepository.folders.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFolderForDetail by remember { mutableStateOf<String?>(null) }
    var expandedChapterKey by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var renamingFolderName by remember { mutableStateOf<String?>(null) }
    var renamingChapterData by remember { mutableStateOf<Triple<String, String, String>?>(null) } // folder, subfolder, chapter
    var deletingFolderConfirm by remember { mutableStateOf<String?>(null) }
    var deletingChapterConfirm by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var deletingCardConfirm by remember { mutableStateOf<FlashcardItem?>(null) }
    var editingCard by remember { mutableStateOf<FlashcardItem?>(null) }

    if (flashcards.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📇", fontSize = 42.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No Flashcards Uploaded Yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Go to Upload Center > Flashcards Upload to add cards.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
        return
    }

    // Group flashcards by folderName
    val groupedByFolder = remember(flashcards) {
        flashcards.groupBy { it.folderName.ifBlank { "General Deck" } }
    }

    // Detail drill-down inside a selected folder
    if (selectedFolderForDetail != null) {
        val currentFolder = selectedFolderForDetail!!
        val cardsInFolder = groupedByFolder[currentFolder].orEmpty()
        val matchingFolderObj = folders.firstOrNull { it.name.equals(currentFolder, ignoreCase = true) }
        val folderEmoji = matchingFolderObj?.emoji ?: "📇"

        // Group cards in this folder by subfolder & chapter
        val groupedChapters = remember(cardsInFolder) {
            cardsInFolder.groupBy {
                val sub = it.subfolderName.ifBlank { "General" }
                val chap = it.chapterName.ifBlank { "Direct Questions" }
                Pair(sub, chap)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar to return to folders
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = { selectedFolderForDetail = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2563EB))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(folderEmoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = currentFolder,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${cardsInFolder.size} Flashcards • ${groupedChapters.size} Chapters",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { renamingFolderName = currentFolder }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename Folder", tint = Color(0xFF2563EB))
                        }
                        IconButton(onClick = { deletingFolderConfirm = currentFolder }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Folder", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }

            // Chapters and cards inside
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedChapters.forEach { (subAndChap, cardsInChap) ->
                    val (subfolder, chapter) = subAndChap
                    val chapterKey = "${currentFolder}_${subfolder}_${chapter}"
                    val isExpanded = expandedChapterKey == chapterKey

                    item(key = chapterKey) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedChapterKey = if (isExpanded) null else chapterKey
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "📖 $chapter",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF1E3A8A)
                                            )
                                            if (subfolder.isNotBlank() && subfolder != "General") {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFF1F5F9),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "📁 $subfolder",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF475569),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${cardsInChap.size} Flashcard Items",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { renamingChapterData = Triple(currentFolder, subfolder, chapter) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename Chapter", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { deletingChapterConfirm = Triple(currentFolder, subfolder, chapter) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Chapter", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    cardsInChap.forEachIndexed { index, card ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFF8FAFC),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Q${index + 1}: ${card.question}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                    if (card.questionImageUrl.isNotBlank()) {
                                                        Text("🖼️ Has Question Image", fontSize = 11.sp, color = Color(0xFF0284C7))
                                                    }
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = "Ans: ${card.answer}",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF15803D),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (card.answerImageUrl.isNotBlank()) {
                                                        Text("🖼️ Has Answer Image", fontSize = 11.sp, color = Color(0xFF0284C7))
                                                    }
                                                    if (card.hint.isNotBlank()) {
                                                        Text("💡 Hint: ${card.hint}", fontSize = 11.sp, color = Color(0xFFD97706))
                                                    }
                                                    if (card.tags.isNotBlank()) {
                                                        Text("🏷️ ${card.tags}", fontSize = 10.sp, color = Color(0xFF64748B))
                                                    }
                                                }

                                                Row {
                                                    IconButton(onClick = { editingCard = card }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Edit, contentDescription = "Edit Card", tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
                                                    }
                                                    IconButton(onClick = { deletingCardConfirm = card }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete Card", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Level 1: Folders List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(groupedByFolder.keys.toList()) { folderName ->
                val cards = groupedByFolder[folderName].orEmpty()
                val matchingFolderObj = folders.firstOrNull { it.name.equals(folderName, ignoreCase = true) }
                val emoji = matchingFolderObj?.emoji ?: "📇"
                val distinctChapters = cards.map { it.chapterName.ifBlank { "General" } }.distinct().size

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFolderForDetail = folderName },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = folderName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${cards.size} Flashcards • $distinctChapters Chapters",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { renamingFolderName = folderName }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { deletingFolderConfirm = folderName }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // Rename Folder Dialog
    renamingFolderName?.let { folderName ->
        var newName by remember { mutableStateOf(folderName) }
        Dialog(onDismissRequest = { renamingFolderName = null }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Rename Flashcard Folder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Folder Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { renamingFolderName = null }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank() && newName.trim() != folderName) {
                                    MedRepository.renameFlashcardFolder(folderName, newName.trim())
                                }
                                renamingFolderName = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Rename Chapter Dialog
    renamingChapterData?.let { (folder, subfolder, chapter) ->
        var newChapterName by remember { mutableStateOf(chapter) }
        Dialog(onDismissRequest = { renamingChapterData = null }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Rename Flashcard Chapter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newChapterName,
                        onValueChange = { newChapterName = it },
                        label = { Text("Chapter Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { renamingChapterData = null }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newChapterName.isNotBlank() && newChapterName.trim() != chapter) {
                                    MedRepository.renameFlashcardChapter(folder, subfolder, chapter, newChapterName.trim())
                                }
                                renamingChapterData = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Delete Folder Confirmation Dialog
    deletingFolderConfirm?.let { folderName ->
        AlertDialog(
            onDismissRequest = { deletingFolderConfirm = null },
            title = { Text("Delete Flashcard Folder?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete folder \"$folderName\" and all its flashcards? This will remove it from all users and delete from Cloudflare.") },
            confirmButton = {
                Button(
                    onClick = {
                        MedRepository.deleteFlashcardsByFolder(folderName)
                        if (selectedFolderForDetail == folderName) selectedFolderForDetail = null
                        deletingFolderConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolderConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Chapter Confirmation Dialog
    deletingChapterConfirm?.let { (folder, subfolder, chapter) ->
        AlertDialog(
            onDismissRequest = { deletingChapterConfirm = null },
            title = { Text("Delete Chapter?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete chapter \"$chapter\" and all its flashcards? This will also remove it from Cloudflare R2.") },
            confirmButton = {
                Button(
                    onClick = {
                        MedRepository.deleteFlashcardsByChapter(folder, subfolder, chapter)
                        deletingChapterConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Chapter")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingChapterConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Flashcard Confirmation Dialog
    deletingCardConfirm?.let { card ->
        AlertDialog(
            onDismissRequest = { deletingCardConfirm = null },
            title = { Text("Delete Flashcard?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this flashcard? This will also remove any images from Cloudflare.") },
            confirmButton = {
                Button(
                    onClick = {
                        MedRepository.deleteFlashcard(card.id)
                        deletingCardConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCardConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Flashcard Dialog
    editingCard?.let { card ->
        var editQ by remember { mutableStateOf(card.question) }
        var editA by remember { mutableStateOf(card.answer) }
        var editExp by remember { mutableStateOf(card.explanation) }
        var editHint by remember { mutableStateOf(card.hint) }
        var editTags by remember { mutableStateOf(card.tags) }
        var editQImg by remember { mutableStateOf(card.questionImageUrl) }
        var editAImg by remember { mutableStateOf(card.answerImageUrl) }
        var isUploadingImg by remember { mutableStateOf(false) }

        val qImgPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                isUploadingImg = true
                coroutineScope.launch {
                    val key = "flashcards/images/q_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}.jpg"
                    val res = CloudflareR2Manager.uploadCompressedImage(context, uri, key)
                    if (res.isSuccess) editQImg = res.getOrThrow()
                    isUploadingImg = false
                }
            }
        }

        val aImgPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                isUploadingImg = true
                coroutineScope.launch {
                    val key = "flashcards/images/a_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}.jpg"
                    val res = CloudflareR2Manager.uploadCompressedImage(context, uri, key)
                    if (res.isSuccess) editAImg = res.getOrThrow()
                    isUploadingImg = false
                }
            }
        }

        Dialog(onDismissRequest = { editingCard = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Flashcard", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editQ,
                        onValueChange = { editQ = it },
                        label = { Text("Question *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { qImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (editQImg.isNotBlank()) "Change Q Img" else "Add Q Img", fontSize = 11.sp)
                        }
                        if (editQImg.isNotBlank()) {
                            IconButton(onClick = { editQImg = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editA,
                        onValueChange = { editA = it },
                        label = { Text("Answer *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { aImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (editAImg.isNotBlank()) "Change Ans Img" else "Add Ans Img", fontSize = 11.sp)
                        }
                        if (editAImg.isNotBlank()) {
                            IconButton(onClick = { editAImg = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editHint,
                        onValueChange = { editHint = it },
                        label = { Text("Hint (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editTags,
                        onValueChange = { editTags = it },
                        label = { Text("Tags") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { editingCard = null }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (editQ.isNotBlank() && editA.isNotBlank()) {
                                    MedRepository.editFlashcardFull(
                                        id = card.id,
                                        newQuestion = editQ.trim(),
                                        newAnswer = editA.trim(),
                                        newExplanation = editExp.trim(),
                                        newHint = editHint.trim(),
                                        newTags = editTags.trim(),
                                        newQuestionImg = editQImg.trim(),
                                        newAnswerImg = editAImg.trim()
                                    )
                                }
                                editingCard = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
