package com.example.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.MedRepository
import com.example.data.cloud.CloudSyncEngine
import com.example.data.cloud.CloudflareR2Manager
import com.example.model.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCQUploadView(
    onOpenR2Settings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val folders by MedRepository.folders.collectAsState()
    val allMCQs by MedRepository.mcqs.collectAsState()

    // Filter MCQ folders - use only explicit folders
    val availableFolders = remember(folders) {
        folders.filter {
            it.sectionType.equals("MCQ", ignoreCase = true) ||
            it.subjectId.equals("mcq", ignoreCase = true) ||
            it.name.contains("MCQ", ignoreCase = true)
        }
    }

    val sections by MedRepository.mcqSections.collectAsState()

    // Upload mode: 0 = Manual 1-by-1, 1 = Bulk JSON Import
    var uploadMode by remember { mutableIntStateOf(0) }

    // Hierarchy selection
    var isCreatingNewSection by remember { mutableStateOf(sections.isEmpty()) }
    var selectedSectionId by remember { mutableStateOf("") }
    var newSectionTitle by remember { mutableStateOf("") }
    var newSectionSubtitle by remember { mutableStateOf("") }
    var newSectionEmoji by remember { mutableStateOf("🎯") }
    var newSectionColor1 by remember { mutableStateOf("#0D9488") }
    var newSectionColor2 by remember { mutableStateOf("#0F766E") }

    var isCreatingNewFolder by remember { mutableStateOf(availableFolders.isEmpty()) }
    var selectedFolderId by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderEmoji by remember { mutableStateOf("🎯") }

    // Subfolder & Chapter
    var subfolderName by remember { mutableStateOf("") }
    var chapterName by remember { mutableStateOf("") }

    // Question form state
    var questionText by remember { mutableStateOf("") }
    var questionImageUrl by remember { mutableStateOf("") }
    var isUploadingQuestionImage by remember { mutableStateOf(false) }

    // Dynamic options: start with 4 options by default
    val optionsList = remember { mutableStateListOf("Option A", "Option B", "Option C", "Option D") }
    var correctOptionIndex by remember { mutableIntStateOf(0) }

    // Explanation & tags
    var explanationText by remember { mutableStateOf("") }
    var explanationImageUrl by remember { mutableStateOf("") }
    var isUploadingExplanationImage by remember { mutableStateOf(false) }
    var difficulty by remember { mutableStateOf("Medium") }
    var tags by remember { mutableStateOf("High-Yield") }

    // Question Type & Rich Textbook Fields (Assertion, Column, Statements, Flowchart)
    var manualQuestionType by remember { mutableStateOf("MCQ") }
    var assertionInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var matchColumnAInput by remember { mutableStateOf("") }
    var matchColumnBInput by remember { mutableStateOf("") }
    var statementsInput by remember { mutableStateOf("") }
    var flowchartStepsInput by remember { mutableStateOf("") }

    // Bulk JSON Import state
    var jsonInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Question Image Picker
    val questionImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingQuestionImage = true
            coroutineScope.launch {
                val objectKey = "mcqs/images/q_${System.currentTimeMillis()}.jpg"
                val uploadResult = CloudflareR2Manager.uploadCompressedImage(context, uri, objectKey)
                isUploadingQuestionImage = false
                if (uploadResult.isSuccess) {
                    questionImageUrl = uploadResult.getOrThrow()
                    statusMessage = "✓ Question image compressed & uploaded to Cloudflare R2!"
                } else {
                    errorMessage = "Failed to upload question image: ${uploadResult.exceptionOrNull()?.localizedMessage}"
                }
            }
        }
    }

    // Explanation Image Picker
    val explanationImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingExplanationImage = true
            coroutineScope.launch {
                val objectKey = "mcqs/images/exp_${System.currentTimeMillis()}.jpg"
                val uploadResult = CloudflareR2Manager.uploadCompressedImage(context, uri, objectKey)
                isUploadingExplanationImage = false
                if (uploadResult.isSuccess) {
                    explanationImageUrl = uploadResult.getOrThrow()
                    statusMessage = "✓ Explanation image compressed & uploaded to Cloudflare R2!"
                } else {
                    errorMessage = "Failed to upload explanation image: ${uploadResult.exceptionOrNull()?.localizedMessage}"
                }
            }
        }
    }

    LaunchedEffect(sections) {
        if (sections.isEmpty()) {
            isCreatingNewSection = true
        } else if (selectedSectionId.isBlank() || sections.none { it.id == selectedSectionId }) {
            selectedSectionId = sections.first().id
        }
    }

    LaunchedEffect(availableFolders) {
        if (availableFolders.isEmpty()) {
            isCreatingNewFolder = true
        } else if (selectedFolderId.isBlank() || availableFolders.none { it.id == selectedFolderId }) {
            selectedFolderId = availableFolders.first().id
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status & Error Banners
        statusMessage?.let { msg ->
            item {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(msg, color = Color(0xFF15803D), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        errorMessage?.let { err ->
            item {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(err, color = Color(0xFFB91C1C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { errorMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFB91C1C), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Mode Switcher: Manual 1-by-1 vs Bulk JSON
        item {
            TabRow(
                selectedTabIndex = uploadMode,
                containerColor = Color(0xFFF1F5F9),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = uploadMode == 0,
                    onClick = { uploadMode = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual 1-by-1 Creator", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )
                Tab(
                    selected = uploadMode == 1,
                    onClick = { uploadMode = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bulk JSON Import", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )
            }
        }

        // ==========================================
        // SECTION SELECTOR / CREATOR (Homepage-like Layout)
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select or Create Section", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = !isCreatingNewSection,
                            onClick = { if (sections.isNotEmpty()) isCreatingNewSection = false },
                            label = { Text("Choose Existing Section (${sections.size})") },
                            enabled = sections.isNotEmpty()
                        )
                        FilterChip(
                            selected = isCreatingNewSection,
                            onClick = { isCreatingNewSection = true },
                            label = { Text("+ Create New Section") }
                        )
                    }

                    if (isCreatingNewSection) {
                        OutlinedTextField(
                            value = newSectionTitle,
                            onValueChange = { newSectionTitle = it },
                            label = { Text("Section Title *") },
                            placeholder = { Text("e.g. Clinical MCQ Masterbank") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newSectionSubtitle,
                            onValueChange = { newSectionSubtitle = it },
                            label = { Text("Section Subtitle (Optional)") },
                            placeholder = { Text("e.g. High-Yield Board Exam Practice") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newSectionEmoji,
                                onValueChange = { newSectionEmoji = it },
                                label = { Text("Emoji") },
                                modifier = Modifier.width(90.dp),
                                singleLine = true
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Card Gradient Preset:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val presets = listOf(
                                        Pair("#0D9488", "#0F766E"),
                                        Pair("#2563EB", "#7C3AED"),
                                        Pair("#15803D", "#166534"),
                                        Pair("#E11D48", "#9F1239")
                                    )
                                    presets.forEach { (c1, c2) ->
                                        Surface(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable {
                                                    newSectionColor1 = c1
                                                    newSectionColor2 = c2
                                                },
                                            shape = CircleShape,
                                            color = Color(android.graphics.Color.parseColor(c1)),
                                            border = if (newSectionColor1 == c1) androidx.compose.foundation.BorderStroke(2.dp, Color.Black) else null
                                        ) {}
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val sTitle = newSectionTitle.trim()
                                if (sTitle.isBlank()) {
                                    errorMessage = "Please enter section title"
                                    return@Button
                                }
                                val newSecId = "sec_${UUID.randomUUID().toString().take(6)}"
                                val sec = com.example.model.MCQSection(
                                    id = newSecId,
                                    title = sTitle,
                                    subtitle = newSectionSubtitle.trim(),
                                    emoji = newSectionEmoji.ifBlank { "🎯" },
                                    colorHex1 = newSectionColor1,
                                    colorHex2 = newSectionColor2
                                )
                                MedRepository.addMCQSection(sec)
                                selectedSectionId = newSecId
                                isCreatingNewSection = false
                                newSectionTitle = ""
                                newSectionSubtitle = ""
                                statusMessage = "✓ Section '$sTitle' created and saved to Cloudflare!"
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✓ Save & Create Section Now", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        var sectionDropdownExpanded by remember { mutableStateOf(false) }
                        val activeSec = sections.firstOrNull { it.id == selectedSectionId }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = activeSec?.title ?: "Select Section",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selected Section") },
                                trailingIcon = {
                                    IconButton(onClick = { sectionDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { sectionDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = sectionDropdownExpanded,
                                onDismissRequest = { sectionDropdownExpanded = false }
                            ) {
                                sections.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s.emoji} ${s.title}") },
                                        onClick = {
                                            selectedSectionId = s.id
                                            sectionDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // FOLDER SELECTOR / CREATOR
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select or Create Folder", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = !isCreatingNewFolder,
                            onClick = { if (availableFolders.isNotEmpty()) isCreatingNewFolder = false },
                            label = { Text("Choose Existing (${availableFolders.size})") },
                            enabled = availableFolders.isNotEmpty()
                        )
                        FilterChip(
                            selected = isCreatingNewFolder,
                            onClick = { isCreatingNewFolder = true },
                            label = { Text("+ Create New Folder") }
                        )
                    }

                    if (isCreatingNewFolder) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("New Folder Name *") },
                            placeholder = { Text("e.g. Pathology MCQs / NEET-PG High Yield") },
                            modifier = Modifier.fillMaxWidth().testTag("mcq_new_folder_name"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newFolderEmoji,
                            onValueChange = { newFolderEmoji = it },
                            label = { Text("Emoji") },
                            modifier = Modifier.width(90.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val fName = newFolderName.trim()
                                if (fName.isBlank()) {
                                    errorMessage = "Please enter folder name"
                                    return@Button
                                }
                                val newFId = "f_mcq_${UUID.randomUUID().toString().take(6)}"
                                val folder = ContentFolder(
                                    id = newFId,
                                    prof = ProfYear.FIRST_PROF,
                                    subjectId = selectedSectionId.ifBlank { "mcq" },
                                    name = fName,
                                    emoji = newFolderEmoji.ifBlank { "🎯" },
                                    sectionType = "MCQ"
                                )
                                MedRepository.createFolder(folder)
                                selectedFolderId = newFId
                                isCreatingNewFolder = false
                                newFolderName = ""
                                statusMessage = "✓ Folder '$fName' created and saved!"
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✓ Save & Create Folder Now", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Dropdown of existing folders
                        var folderDropdownExpanded by remember { mutableStateOf(false) }
                        val activeFolder = availableFolders.firstOrNull { it.id == selectedFolderId }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = activeFolder?.name ?: "Select Folder",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selected Folder") },
                                trailingIcon = {
                                    IconButton(onClick = { folderDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { folderDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = folderDropdownExpanded,
                                onDismissRequest = { folderDropdownExpanded = false }
                            ) {
                                availableFolders.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text("${f.emoji} ${f.name}") },
                                        onClick = {
                                            selectedFolderId = f.id
                                            folderDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // SUBFOLDER & CHAPTER
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Subfolder (Optional) & Chapter Name", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))

                    OutlinedTextField(
                        value = subfolderName,
                        onValueChange = { subfolderName = it },
                        label = { Text("Subfolder Name (Optional)") },
                        placeholder = { Text("e.g. General Pathology (leave empty if none)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = chapterName,
                        onValueChange = { chapterName = it },
                        label = { Text("Chapter / Topic Name *") },
                        placeholder = { Text("e.g. Chapter 1 - Cell Injury & Adaptation") },
                        modifier = Modifier.fillMaxWidth().testTag("mcq_chapter_name"),
                        singleLine = true
                    )
                }
            }
        }

        // ==========================================
        // STEP 3: QUESTIONS (MANUAL VS JSON)
        // ==========================================
        if (uploadMode == 0) {
            // MANUAL 1-BY-1 FORM
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("✍️ Step 3: Question Format & Content", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))

                        // Question Format Selector Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Select Question Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val typesList = listOf(
                                    Pair("MCQ", "🎯 Standard MCQ"),
                                    Pair("ASSERTION_REASON", "⚖️ Assertion-Reason"),
                                    Pair("MATCH_THE_COLUMN", "🔀 Match Column"),
                                    Pair("STATEMENT_BASED", "📝 Statements"),
                                    Pair("FLOWCHART", "🔄 Flowchart"),
                                    Pair("TRUE_FALSE", "⚖️ True/False")
                                )
                                typesList.forEach { (typeKey, typeLabel) ->
                                    FilterChip(
                                        selected = manualQuestionType == typeKey,
                                        onClick = {
                                            manualQuestionType = typeKey
                                            if (typeKey == "ASSERTION_REASON" && questionText.isBlank()) {
                                                questionText = "Given below are two statements: one is labelled as Assertion (A) and the other is labelled as Reason (R):"
                                                optionsList.clear()
                                                optionsList.addAll(
                                                    listOf(
                                                        "Both Assertion and Reason are true, and Reason is the correct explanation of Assertion",
                                                        "Both Assertion and Reason are true, but Reason is NOT the correct explanation of Assertion",
                                                        "Assertion is true, but Reason is false",
                                                        "Assertion is false, but Reason is true"
                                                    )
                                                )
                                            }
                                        },
                                        label = { Text(typeLabel, fontSize = 11.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF1E293B),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // Question Input / Prompt
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            label = { Text(if (manualQuestionType == "MCQ") "Question Text *" else "Question Prompt / Intro *") },
                            placeholder = { Text(if (manualQuestionType == "MCQ") "Enter medical multiple-choice question..." else "Enter question intro prompt...") },
                            modifier = Modifier.fillMaxWidth().testTag("mcq_question_input"),
                            minLines = if (manualQuestionType == "MCQ") 3 else 2
                        )

                        // Format-Specific Inputs
                        if (manualQuestionType == "ASSERTION_REASON") {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("⚖️ Assertion & Reason Statements", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                    OutlinedTextField(
                                        value = assertionInput,
                                        onValueChange = { assertionInput = it },
                                        label = { Text("Assertion (A) *") },
                                        placeholder = { Text("e.g. Myocardial infarction results in coagulative necrosis...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )
                                    OutlinedTextField(
                                        value = reasonInput,
                                        onValueChange = { reasonInput = it },
                                        label = { Text("Reason (R) *") },
                                        placeholder = { Text("e.g. Ischemia causes protein denaturation...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )
                                }
                            }
                        } else if (manualQuestionType == "MATCH_THE_COLUMN") {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("🔀 Columns Setup (One item per line)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                    OutlinedTextField(
                                        value = matchColumnAInput,
                                        onValueChange = { matchColumnAInput = it },
                                        label = { Text("Column I items (one per line) *") },
                                        placeholder = { Text("A. Mitochondria\nB. Ribosome\nC. Lysosome\nD. Peroxisome") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                    OutlinedTextField(
                                        value = matchColumnBInput,
                                        onValueChange = { matchColumnBInput = it },
                                        label = { Text("Column II items (one per line) *") },
                                        placeholder = { Text("1. Protein translation\n2. ATP production\n3. Autophagy\n4. VLCFA oxidation") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }
                            }
                        } else if (manualQuestionType == "STATEMENT_BASED") {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("📝 Statements List (One statement per line)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                    OutlinedTextField(
                                        value = statementsInput,
                                        onValueChange = { statementsInput = it },
                                        label = { Text("Statements (one per line) *") },
                                        placeholder = { Text("Statement I: B12 deficiency causes subacute combined degeneration...\nStatement II: Serum MMA is normal...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }
                            }
                        } else if (manualQuestionType == "FLOWCHART") {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("🔄 Flowchart Sequence Steps", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                    OutlinedTextField(
                                        value = flowchartStepsInput,
                                        onValueChange = { flowchartStepsInput = it },
                                        label = { Text("Steps (separated by -> or one per line) *") },
                                        placeholder = { Text("L-Tyrosine -> L-DOPA -> Dopamine -> Norepinephrine -> Epinephrine") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )
                                }
                            }
                        }

                        // Question Image Upload
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    questionImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploadingQuestionImage
                            ) {
                                if (isUploadingQuestionImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compressing & Uploading...")
                                } else {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (questionImageUrl.isBlank()) "Add Question Image (Compressed)" else "Replace Image")
                                }
                            }
                            if (questionImageUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.width(10.dp))
                                IconButton(onClick = { questionImageUrl = "" }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Image", tint = Color.Red)
                                }
                            }
                        }

                        if (questionImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = questionImageUrl,
                                contentDescription = "Question preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        // Options Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Options (${optionsList.size}) - Select radio button for correct answer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            Button(
                                onClick = {
                                    val nextChar = ('A'.code + optionsList.size).toChar()
                                    optionsList.add("Option $nextChar")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("mcq_add_option_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add Option", fontSize = 12.sp)
                            }
                        }

                        val optionLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")

                        // Options dynamic list
                        optionsList.forEachIndexed { index, optionVal ->
                            val isCorrect = correctOptionIndex == index
                            val letter = optionLetters.getOrNull(index) ?: "${index + 1}"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCorrect) Color(0xFFF0FDF4) else Color(0xFFF8FAFC))
                                    .border(1.dp, if (isCorrect) Color(0xFF86EFAC) else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isCorrect,
                                    onClick = { correctOptionIndex = index },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF16A34A))
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = if (isCorrect) Color(0xFF16A34A) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(letter, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isCorrect) Color.White else Color(0xFF334155))
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedTextField(
                                    value = optionVal,
                                    onValueChange = { optionsList[index] = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                                )

                                if (optionsList.size > 2) {
                                    IconButton(
                                        onClick = {
                                            optionsList.removeAt(index)
                                            if (correctOptionIndex >= optionsList.size) {
                                                correctOptionIndex = optionsList.size - 1
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove option", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        // Explanation Input
                        OutlinedTextField(
                            value = explanationText,
                            onValueChange = { explanationText = it },
                            label = { Text("Explanation / Clinical Reasoning") },
                            placeholder = { Text("Explain why the answer is correct...") },
                            modifier = Modifier.fillMaxWidth().testTag("mcq_explanation_input"),
                            minLines = 2
                        )

                        // Explanation Image
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    explanationImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploadingExplanationImage
                            ) {
                                if (isUploadingExplanationImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compressing & Uploading...")
                                } else {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (explanationImageUrl.isBlank()) "Add Explanation Image (Compressed)" else "Replace Image")
                                }
                            }
                            if (explanationImageUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.width(10.dp))
                                IconButton(onClick = { explanationImageUrl = "" }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Image", tint = Color.Red)
                                }
                            }
                        }

                        if (explanationImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = explanationImageUrl,
                                contentDescription = "Explanation preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Tag
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            label = { Text("Tag") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Save & Add Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val qText = questionText.trim()
                            val cName = chapterName.trim()
                            val effectiveFolder = if (isCreatingNewFolder) newFolderName.trim() else availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: ""

                            if (qText.isBlank()) {
                                errorMessage = "Please enter question text."
                                return@Button
                            }
                            if (cName.isBlank()) {
                                errorMessage = "Please enter chapter name."
                                return@Button
                            }
                            if (effectiveFolder.isBlank()) {
                                errorMessage = "Please provide or select a folder."
                                return@Button
                            }

                            val finalSectionId = if (isCreatingNewSection) {
                                "sec_${UUID.randomUUID().toString().take(6)}"
                            } else {
                                selectedSectionId
                            }
                            val effectiveSectionTitle = if (isCreatingNewSection) {
                                newSectionTitle.ifBlank { "MCQ Practice Section" }
                            } else {
                                sections.firstOrNull { it.id == selectedSectionId }?.title ?: ""
                            }

                            if (isCreatingNewSection) {
                                MedRepository.addMCQSection(
                                    com.example.model.MCQSection(
                                        id = finalSectionId,
                                        title = effectiveSectionTitle,
                                        subtitle = newSectionSubtitle.trim(),
                                        emoji = newSectionEmoji.ifBlank { "🎯" },
                                        colorHex1 = newSectionColor1,
                                        colorHex2 = newSectionColor2
                                    )
                                )
                                isCreatingNewSection = false
                                selectedSectionId = finalSectionId
                            }

                            val finalFolderId = if (isCreatingNewFolder) {
                                "f_mcq_${UUID.randomUUID().toString().take(6)}"
                            } else {
                                selectedFolderId
                            }

                            if (isCreatingNewFolder) {
                                MedRepository.createFolder(
                                    ContentFolder(
                                        id = finalFolderId,
                                        prof = ProfYear.FIRST_PROF,
                                        subjectId = finalSectionId.ifBlank { "mcq" },
                                        name = effectiveFolder,
                                        emoji = newFolderEmoji.ifBlank { "🎯" },
                                        sectionType = "MCQ"
                                    )
                                )
                                isCreatingNewFolder = false
                                selectedFolderId = finalFolderId
                            }

                            val mcqOptions = optionsList.mapIndexed { idx, text ->
                                MCQOption(id = "opt_$idx", text = text.trim())
                            }

                            val colAList = matchColumnAInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val colBList = matchColumnBInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val stmtsList = statementsInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val fcList = if (flowchartStepsInput.contains("->")) {
                                flowchartStepsInput.split(Regex("\\s*->\\s*")).map { it.trim() }.filter { it.isNotBlank() }
                            } else {
                                flowchartStepsInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            }

                            val newMCQ = MCQItem(
                                id = "mcq_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                prof = ProfYear.FIRST_PROF,
                                subjectId = finalSectionId.ifBlank { "mcq" },
                                sectionId = finalSectionId,
                                sectionName = effectiveSectionTitle,
                                folderId = finalFolderId,
                                folderName = effectiveFolder,
                                subfolderName = subfolderName.trim(),
                                chapterName = cName,
                                question = qText,
                                questionImageUrl = questionImageUrl,
                                options = mcqOptions,
                                correctOptionIndex = correctOptionIndex,
                                explanation = explanationText.trim(),
                                explanationImageUrl = explanationImageUrl,
                                difficulty = difficulty,
                                tags = tags,
                                questionType = manualQuestionType,
                                assertion = assertionInput.trim(),
                                reason = reasonInput.trim(),
                                statements = stmtsList,
                                matchColumnA = colAList,
                                matchColumnB = colBList,
                                flowchartSteps = fcList
                            )

                            MedRepository.addMCQ(newMCQ)
                            statusMessage = "✓ Question saved successfully! Enter next question below."
                            errorMessage = null

                            // Reset question form only, keeping folder & chapter intact
                            questionText = ""
                            questionImageUrl = ""
                            explanationText = ""
                            explanationImageUrl = ""
                            assertionInput = ""
                            reasonInput = ""
                            matchColumnAInput = ""
                            matchColumnBInput = ""
                            statementsInput = ""
                            flowchartStepsInput = ""
                            correctOptionIndex = 0
                            optionsList.clear()
                            optionsList.addAll(listOf("Option A", "Option B", "Option C", "Option D"))
                        },
                        modifier = Modifier.weight(1f).testTag("mcq_save_next_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("💾 Save & Next Question")
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val res = CloudSyncEngine.publishCatalogToCloud()
                                if (res.isSuccess) {
                                    statusMessage = "✓ Published all MCQs & chapters to Cloudflare R2!"
                                } else {
                                    errorMessage = "Cloud publish failed: ${res.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("☁️ Sync Cloud")
                    }
                }
            }
        } else {
            // BULK JSON IMPORT: AI PROMPTS, SAMPLES & PARSER
            item {
                MCQChatGPTAndSamplesCard(
                    onLoadJson = { jsonInput = it }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📦 Paste JSON Array", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (jsonInput.isNotBlank()) {
                                TextButton(
                                    onClick = { jsonInput = "" },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Clear ✕", color = Color(0xFFEF4444), fontSize = 12.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = jsonInput,
                            onValueChange = { jsonInput = it },
                            placeholder = { Text("Paste JSON array of MCQs (supports Assertion-Reason, Match Columns, Statements, Flowcharts, etc.)...") },
                            modifier = Modifier.fillMaxWidth().testTag("mcq_json_input"),
                            minLines = 8,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )

                        Button(
                            onClick = {
                                val raw = jsonInput.trim()
                                val cName = chapterName.trim()
                                val effectiveFolder = if (isCreatingNewFolder) newFolderName.trim() else availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: ""

                                if (raw.isBlank()) {
                                    errorMessage = "Please enter JSON."
                                    return@Button
                                }

                                try {
                                    val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                                    val jsonArray = if (cleaned.startsWith("[")) {
                                        JSONArray(cleaned)
                                    } else {
                                        val obj = JSONObject(cleaned)
                                        obj.optJSONArray("mcqs") ?: obj.optJSONArray("questions") ?: JSONArray()
                                    }

                                    if (jsonArray.length() == 0) {
                                        errorMessage = "No questions found in JSON."
                                        return@Button
                                    }

                                    val finalSectionId = if (isCreatingNewSection) {
                                        "sec_${UUID.randomUUID().toString().take(6)}"
                                    } else {
                                        selectedSectionId
                                    }
                                    val effectiveSectionTitle = if (isCreatingNewSection) {
                                        newSectionTitle.trim().ifBlank { "MCQ Practice Section" }
                                    } else {
                                        sections.firstOrNull { it.id == selectedSectionId }?.title ?: ""
                                    }

                                    if (isCreatingNewSection && newSectionTitle.isNotBlank()) {
                                        MedRepository.addMCQSection(
                                            com.example.model.MCQSection(
                                                id = finalSectionId,
                                                title = effectiveSectionTitle,
                                                subtitle = newSectionSubtitle.trim(),
                                                emoji = newSectionEmoji.ifBlank { "🎯" },
                                                colorHex1 = newSectionColor1,
                                                colorHex2 = newSectionColor2
                                            )
                                        )
                                        isCreatingNewSection = false
                                        selectedSectionId = finalSectionId
                                    }

                                    val finalFolderId = if (isCreatingNewFolder) {
                                        "f_mcq_${UUID.randomUUID().toString().take(6)}"
                                    } else {
                                        selectedFolderId
                                    }

                                    if (isCreatingNewFolder && effectiveFolder.isNotBlank()) {
                                        MedRepository.createFolder(
                                            ContentFolder(
                                                id = finalFolderId,
                                                prof = ProfYear.FIRST_PROF,
                                                subjectId = finalSectionId.ifBlank { "mcq" },
                                                name = effectiveFolder,
                                                emoji = newFolderEmoji.ifBlank { "🎯" },
                                                sectionType = "MCQ"
                                            )
                                        )
                                        isCreatingNewFolder = false
                                        selectedFolderId = finalFolderId
                                    }

                                    val parsedList = mutableListOf<MCQItem>()
                                    for (i in 0 until jsonArray.length()) {
                                        val item = jsonArray.getJSONObject(i)
                                        val q = item.optString("question", item.optString("q", "")).trim()
                                        if (q.isBlank()) continue

                                        val optArr = item.optJSONArray("options") ?: item.optJSONArray("opts") ?: JSONArray()
                                        val optionsList = mutableListOf<MCQOption>()
                                        for (j in 0 until optArr.length()) {
                                            val optVal = optArr.get(j)
                                            if (optVal is JSONObject) {
                                                optionsList.add(MCQOption(id = "opt_$j", text = optVal.optString("text", ""), imageUrl = optVal.optString("imageUrl", "")))
                                            } else {
                                                optionsList.add(MCQOption(id = "opt_$j", text = optVal.toString()))
                                            }
                                        }

                                        val ansRaw = item.opt("answer") ?: item.opt("correct") ?: 0
                                        val ansIndex = when (ansRaw) {
                                            is Number -> ansRaw.toInt()
                                            is String -> {
                                                val clean = ansRaw.trim().uppercase()
                                                if (clean.length == 1 && clean[0] in 'A'..'Z') clean[0] - 'A'
                                                else clean.toIntOrNull() ?: 0
                                            }
                                            else -> 0
                                        }

                                        val exp = item.optString("explanation", item.optString("sol", ""))

                                        // Parse Rich Format Fields
                                        val qType = item.optString("type", item.optString("questionType", "MCQ")).trim().uppercase()
                                        val assertionVal = item.optString("assertion", item.optString("assert", "")).trim()
                                        val reasonVal = item.optString("reason", item.optString("rsn", "")).trim()

                                        val stmtsList = mutableListOf<String>()
                                        val stmtsArr = item.optJSONArray("statements") ?: item.optJSONArray("stmts")
                                        if (stmtsArr != null) {
                                            for (s in 0 until stmtsArr.length()) {
                                                val sText = stmtsArr.optString(s).trim()
                                                if (sText.isNotBlank()) stmtsList.add(sText)
                                            }
                                        }

                                        val colAList = mutableListOf<String>()
                                        val colAArr = item.optJSONArray("matchColumnA") ?: item.optJSONArray("columnA") ?: item.optJSONArray("columnI") ?: item.optJSONArray("column1")
                                        if (colAArr != null) {
                                            for (c in 0 until colAArr.length()) {
                                                val cText = colAArr.optString(c).trim()
                                                if (cText.isNotBlank()) colAList.add(cText)
                                            }
                                        }

                                        val colBList = mutableListOf<String>()
                                        val colBArr = item.optJSONArray("matchColumnB") ?: item.optJSONArray("columnB") ?: item.optJSONArray("columnII") ?: item.optJSONArray("column2")
                                        if (colBArr != null) {
                                            for (c in 0 until colBArr.length()) {
                                                val cText = colBArr.optString(c).trim()
                                                if (cText.isNotBlank()) colBList.add(cText)
                                            }
                                        }

                                        val fcList = mutableListOf<String>()
                                        val fcArr = item.optJSONArray("flowchartSteps") ?: item.optJSONArray("flowchart") ?: item.optJSONArray("steps") ?: item.optJSONArray("pathway")
                                        if (fcArr != null) {
                                            for (f in 0 until fcArr.length()) {
                                                val fText = fcArr.optString(f).trim()
                                                if (fText.isNotBlank()) fcList.add(fText)
                                            }
                                        }

                                        val itemChapter = item.optString("chapter", item.optString("chapterName", cName)).trim().ifBlank { cName.ifBlank { "General Practice" } }
                                        val itemFolder = item.optString("folder", item.optString("folderName", effectiveFolder)).trim().ifBlank { effectiveFolder.ifBlank { "General MCQ Bank" } }
                                        val itemSub = item.optString("subfolder", item.optString("subfolderName", subfolderName.trim())).trim()
                                        val itemSec = item.optString("section", item.optString("sectionName", effectiveSectionTitle)).trim().ifBlank { effectiveSectionTitle }

                                        parsedList.add(
                                            MCQItem(
                                                id = "mcq_${System.currentTimeMillis()}_$i",
                                                prof = ProfYear.FIRST_PROF,
                                                subjectId = finalSectionId.ifBlank { "mcq" },
                                                sectionId = finalSectionId,
                                                sectionName = itemSec,
                                                folderId = finalFolderId,
                                                folderName = itemFolder,
                                                subfolderName = itemSub,
                                                chapterName = itemChapter,
                                                question = q,
                                                questionImageUrl = item.optString("questionImageUrl", ""),
                                                options = optionsList,
                                                correctOptionIndex = ansIndex.coerceIn(0, (optionsList.size - 1).coerceAtLeast(0)),
                                                explanation = exp,
                                                explanationImageUrl = item.optString("explanationImageUrl", ""),
                                                difficulty = item.optString("difficulty", "Medium"),
                                                tags = item.optString("tags", "High-Yield"),
                                                questionType = qType.ifBlank { "MCQ" },
                                                assertion = assertionVal,
                                                reason = reasonVal,
                                                statements = stmtsList,
                                                matchColumnA = colAList,
                                                matchColumnB = colBList,
                                                flowchartSteps = fcList
                                            )
                                        )
                                    }

                                    if (parsedList.isNotEmpty()) {
                                        MedRepository.addMCQs(parsedList)
                                        statusMessage = "✓ Successfully imported ${parsedList.size} MCQs!"
                                        errorMessage = null
                                        jsonInput = ""
                                    } else {
                                        errorMessage = "No valid questions parsed from JSON."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "JSON Parse Error: ${e.localizedMessage}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("mcq_import_json_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📥 Parse & Import MCQs")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Edit MCQ Modal Dialog
 */
@Composable
fun EditMCQDialog(
    mcq: MCQItem,
    onDismiss: () -> Unit,
    onSave: (MCQItem) -> Unit
) {
    var questionText by remember { mutableStateOf(mcq.question) }
    var explanationText by remember { mutableStateOf(mcq.explanation) }
    var chapterNameText by remember { mutableStateOf(mcq.chapterName.trim()) }
    var subfolderNameText by remember { mutableStateOf(mcq.subfolderName.trim()) }
    var correctIndex by remember { mutableIntStateOf(mcq.correctOptionIndex) }

    val initialOptions = remember {
        if (mcq.options.isNotEmpty()) {
            mcq.options.map { it.text }
        } else {
            listOf("", "", "", "")
        }
    }
    val optionsList = remember { mutableStateListOf(*initialOptions.toTypedArray()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✏️ Edit MCQ & Answers", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0F172A))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        label = { Text("Question *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }

                item {
                    Text(
                        "Options & Correct Answer (Select radio for correct answer):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                }

                itemsIndexed(optionsList) { index, opt ->
                    val optLetter = if (index < 26) ('A' + index).toString() else "${index + 1}"
                    val isCorrect = correctIndex == index

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RadioButton(
                            selected = isCorrect,
                            onClick = { correctIndex = index },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF16A34A))
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isCorrect) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCorrect) Color(0xFF16A34A) else Color(0xFFCBD5E1)),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = optLetter,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isCorrect) Color(0xFF15803D) else Color(0xFF475569)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = opt,
                            onValueChange = { optionsList[index] = it },
                            placeholder = { Text("Option $optLetter") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (optionsList.size > 2) {
                            IconButton(
                                onClick = {
                                    optionsList.removeAt(index)
                                    if (correctIndex >= optionsList.size) {
                                        correctIndex = (optionsList.size - 1).coerceAtLeast(0)
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Option", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { optionsList.add("") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Option (${if (optionsList.size < 26) ('A' + optionsList.size).toString() else "${optionsList.size + 1}"})")
                    }
                }

                item {
                    OutlinedTextField(
                        value = explanationText,
                        onValueChange = { explanationText = it },
                        label = { Text("Explanation / Solution") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chapterNameText,
                            onValueChange = { chapterNameText = it },
                            label = { Text("Chapter") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = subfolderNameText,
                            onValueChange = { subfolderNameText = it },
                            label = { Text("Subfolder") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val updatedOptions = optionsList.mapIndexed { idx, txt ->
                                    MCQOption(id = "opt_$idx", text = txt.trim())
                                }
                                onSave(
                                    mcq.copy(
                                        question = questionText.trim(),
                                        options = updatedOptions,
                                        correctOptionIndex = correctIndex.coerceIn(0, (updatedOptions.size - 1).coerceAtLeast(0)),
                                        explanation = explanationText.trim(),
                                        chapterName = chapterNameText.trim().ifBlank { mcq.chapterName.trim() },
                                        subfolderName = subfolderNameText.trim().ifBlank { mcq.subfolderName.trim() }
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

/**
 * LazyListScope helper to render the entire MCQs management section inside ManageSectionView
 * Strict Hierarchy: Section -> Folder -> Subfolder -> Chapter -> Question
 * Granular individual deletion and visibility toggle at each level
 */


@Composable
fun MCQManageInterface(
    mcqs: List<MCQItem>,
    folders: List<ContentFolder>,
    mcqSections: List<com.example.model.MCQSection>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    hiddenItemKeys: Set<String>,
    onToggleHide: (String) -> Unit,
    onEditMCQ: (MCQItem) -> Unit,
    onDeleteMCQ: (MCQItem) -> Unit,
    selectedQuestionIds: Set<String>,
    onToggleQuestionSelection: (String) -> Unit,
    onBulkDeleteMCQs: (List<String>) -> Unit,
    onBulkMoveMCQs: (List<String>) -> Unit,
    onRenameFolder: (ContentFolder) -> Unit,
    onDeleteFolder: (ContentFolder) -> Unit,
    onEditMCQSection: (com.example.model.MCQSection) -> Unit,
    onDeleteMCQSection: (com.example.model.MCQSection) -> Unit,
    onDeleteAllMCQs: () -> Unit
) {
    var selectedSectionId by remember { mutableStateOf<String?>(null) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedSubfolder by remember { mutableStateOf<String?>(null) }

    val allMCQFolders = remember(folders, mcqs, mcqSections) {
        val list = mutableListOf<ContentFolder>()
        val seenNames = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        folders.filter {
            it.sectionType.equals("MCQ", ignoreCase = true) ||
            it.subjectId.equals("mcq", ignoreCase = true) ||
            it.name.contains("MCQ", ignoreCase = true) ||
            mcqSections.any { sec -> sec.id == it.subjectId }
        }.forEach {
            list.add(it)
            seenIds.add(it.id)
            seenNames.add(it.name.trim().lowercase())
        }

        mcqs.map { it.folderName.trim() }
            .filter { it.isNotBlank() && !seenNames.contains(it.lowercase()) }
            .distinct()
            .forEach { fName ->
                val sample = mcqs.firstOrNull { it.folderName.equals(fName, ignoreCase = true) }
                val fId = sample?.folderId?.ifBlank { "f_mcq_${fName.lowercase().replace(" ", "_")}" } ?: "f_mcq_${fName}"
                if (!seenIds.contains(fId)) {
                    val folder = ContentFolder(
                        id = fId,
                        prof = sample?.prof ?: com.example.model.ProfYear.FIRST_PROF,
                        subjectId = sample?.sectionId?.ifBlank { "mcq" } ?: "mcq",
                        name = fName,
                        emoji = "🎯",
                        sectionType = "MCQ"
                    )
                    list.add(folder)
                    seenIds.add(fId)
                    seenNames.add(fName.lowercase())
                }
            }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Breadcrumb / Navigation Top Bar
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (selectedSectionId != null || selectedFolderId != null || selectedSubfolder != null) {
                        IconButton(
                            onClick = {
                                if (selectedSubfolder != null) selectedSubfolder = null
                                else if (selectedFolderId != null) selectedFolderId = null
                                else selectedSectionId = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2563EB))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    val titleText = when {
                        selectedSubfolder != null -> selectedSubfolder!!
                        selectedFolderId != null -> allMCQFolders.find { it.id == selectedFolderId }?.name ?: "Folder"
                        selectedSectionId != null -> mcqSections.find { it.id == selectedSectionId }?.title ?: "Section"
                        else -> "Manage MCQs"
                    }
                    Column {
                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        val subtitleText = when {
                            selectedSubfolder != null -> "Chapters & Questions"
                            selectedFolderId != null -> "Subfolders & Chapters"
                            selectedSectionId != null -> "Folders inside section"
                            mcqSections.isNotEmpty() -> "Select section or direct folder to manage"
                            else -> "Select folder to manage"
                        }
                        Text(text = subtitleText, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }

                if (selectedSectionId == null) {
                    OutlinedButton(
                        onClick = onDeleteAllMCQs,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("🔍 Search ${mcqs.size} questions...", fontSize = 13.sp) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Hierarchical Content Switcher
        when {
            // ================= LEVEL 3: QUESTIONS INSIDE SUBFOLDER =================
            selectedFolderId != null && selectedSubfolder != null -> {
                val fId = selectedFolderId!!
                val subName = selectedSubfolder!!
                val folder = allMCQFolders.find { it.id == fId }
                val mcqsInSub = mcqs.filter { m ->
                    (folder == null || m.folderId == folder.id || m.folderName.trim().equals(folder.name.trim(), ignoreCase = true)) &&
                    (m.subfolderName.trim().equals(subName, ignoreCase = true) || (subName.equals("General", ignoreCase = true) && m.subfolderName.trim().isBlank()))
                }

                val filtered = if (searchQuery.isBlank()) mcqsInSub else mcqsInSub.filter {
                    it.question.contains(searchQuery, ignoreCase = true) ||
                    it.chapterName.contains(searchQuery, ignoreCase = true) ||
                    it.options.any { opt -> opt.text.contains(searchQuery, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No questions found in this subfolder.", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    filtered.forEachIndexed { idx, mcqItem ->
                        val isQHidden = hiddenItemKeys.contains("mcq_${mcqItem.id}")

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isQHidden) Color(0xFFF1F5F9) else Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(
                                            checked = selectedQuestionIds.contains(mcqItem.id),
                                            onCheckedChange = { onToggleQuestionSelection(mcqItem.id) }
                                        )
                                        Text(
                                            text = "Q${idx + 1}. ${mcqItem.question}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isQHidden) Color(0xFF64748B) else Color(0xFF0F172A)
                                        )
                                    }
                                    ManageMenu(
                                        onEdit = { onEditMCQ(mcqItem) },
                                        onDelete = { onDeleteMCQ(mcqItem) },
                                        onToggleHide = { onToggleHide("mcq_${mcqItem.id}") },
                                        isItemHidden = isQHidden
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Chapter: ${mcqItem.chapterName.ifBlank { "General" }}", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)

                                val letters = listOf("A", "B", "C", "D", "E", "F")
                                mcqItem.options.forEachIndexed { oIdx, opt ->
                                    val isCorrect = oIdx == mcqItem.correctOptionIndex
                                    Surface(
                                        color = if (isCorrect) Color(0xFFDCFCE7) else Color.White,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isCorrect) Color(0xFF86EFAC) else Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = letters.getOrElse(oIdx) { "?" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isCorrect) Color(0xFF15803D) else Color(0xFF64748B)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = opt.text,
                                                fontSize = 12.sp,
                                                color = if (isCorrect) Color(0xFF15803D) else Color(0xFF334155),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isCorrect) {
                                                Text("✓ Correct", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= LEVEL 2: SUBFOLDERS INSIDE FOLDER =================
            selectedFolderId != null && selectedSubfolder == null -> {
                val fId = selectedFolderId!!
                val folder = allMCQFolders.find { it.id == fId }
                val mcqsInFolder = mcqs.filter { m ->
                    folder == null || m.folderId == folder.id || m.folderName.trim().equals(folder.name.trim(), ignoreCase = true)
                }
                val subfolders = mcqsInFolder.groupBy { it.subfolderName.trim().ifBlank { "General" } }

                if (subfolders.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📂", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Questions or Subfolders in this Folder", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Upload MCQs to this folder using the Upload Center.", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                } else {
                    subfolders.forEach { (subName, mcqsInSub) ->
                        val isSubHidden = hiddenItemKeys.contains("sub_${fId}_${subName.lowercase()}")

                        Card(
                            onClick = { selectedSubfolder = subName },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSubHidden) Color(0xFFF1F5F9) else Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("📂", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(subName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                        Text("${mcqsInSub.size} Questions • ${mcqsInSub.map { it.chapterName.ifBlank { "General" } }.distinct().size} Chapters", fontSize = 11.5.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }

            // ================= LEVEL 1: FOLDERS LIST INSIDE SECTION =================
            selectedSectionId != null && selectedFolderId == null -> {
                val secId = selectedSectionId!!
                val sec = mcqSections.find { it.id == secId }
                val sectionFolders = allMCQFolders.filter { f ->
                    sec == null || f.subjectId == sec.id ||
                    f.subjectId.equals(sec.title, ignoreCase = true) ||
                    mcqs.any { m ->
                        (m.folderId == f.id || m.folderName.trim().equals(f.name.trim(), ignoreCase = true)) &&
                        (m.sectionId == sec.id || m.sectionName.trim().equals(sec.title.trim(), ignoreCase = true))
                    }
                }.distinctBy { it.id }

                if (sectionFolders.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📁", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Folders in this Section", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Upload MCQs into folders using the Upload Center.", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                } else {
                    sectionFolders.forEach { folder ->
                        val isFolderHidden = hiddenItemKeys.contains("folder_${folder.id}")
                        val mcqsInFolder = mcqs.filter { m ->
                            m.folderId == folder.id || m.folderName.trim().equals(folder.name.trim(), ignoreCase = true)
                        }

                        Card(
                            onClick = { selectedFolderId = folder.id; selectedSubfolder = null },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isFolderHidden) Color(0xFFF1F5F9) else Color.White),
                            border = BorderStroke(1.dp, if (isFolderHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(folder.emoji.ifBlank { "🎯" }, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(folder.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Color(0xFF0F172A))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (isFolderHidden) {
                                                Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
                                                    Text("Hidden", fontSize = 9.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        Text("${mcqsInFolder.size} MCQs", fontSize = 11.5.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ManageMenu(
                                        onRename = { onRenameFolder(folder) },
                                        onDelete = { onDeleteFolder(folder) },
                                        onToggleHide = { onToggleHide("folder_${folder.id}") },
                                        isItemHidden = isFolderHidden
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }
            }

            // ================= LEVEL 0: ALL FOLDERS & SECTIONS LIST =================
            else -> {
                // Summary Header
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎯", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MCQ Bank (${mcqs.size} Total Questions in ${allMCQFolders.size} Folders)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                }

                // If MCQ Sections exist: Show them as quick filter / management cards
                if (mcqSections.isNotEmpty()) {
                    Text(
                        text = "MCQ PRACTICE SECTIONS (${mcqSections.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    mcqSections.forEach { sec ->
                        val isSecHidden = hiddenItemKeys.contains("sec_${sec.id}")
                        val sectionFolders = allMCQFolders.filter { f ->
                            f.subjectId == sec.id ||
                            f.subjectId.equals(sec.title, ignoreCase = true) ||
                            mcqs.any { m ->
                                (m.folderId == f.id || m.folderName.trim().equals(f.name.trim(), ignoreCase = true)) &&
                                (m.sectionId == sec.id || m.sectionName.trim().equals(sec.title.trim(), ignoreCase = true))
                            }
                        }.distinctBy { it.id }

                        val secMCQs = mcqs.filter { m ->
                            m.sectionId == sec.id || m.sectionName.equals(sec.title, ignoreCase = true)
                        }

                        Card(
                            onClick = { selectedSectionId = sec.id },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSecHidden) Color(0xFFF1F5F9) else Color.White),
                            border = BorderStroke(1.dp, if (isSecHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(sec.emoji.ifBlank { "🎯" }, fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(sec.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (isSecHidden) {
                                                Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
                                                    Text("Hidden", fontSize = 9.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        Text("📁 ${sectionFolders.size} Folders • ${secMCQs.size} Questions", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ManageMenu(
                                        onEdit = { onEditMCQSection(sec) },
                                        onDelete = { onDeleteMCQSection(sec) },
                                        onToggleHide = { onToggleHide("sec_${sec.id}") },
                                        isItemHidden = isSecHidden
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // ALL FOLDERS DIRECTLY - ALWAYS ACCESSIBLE & CLICKABLE!
                Text(
                    text = "ALL MCQ FOLDERS (${allMCQFolders.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )

                val displayFolders = if (searchQuery.isBlank()) allMCQFolders else allMCQFolders.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }

                if (displayFolders.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📁", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No folders match '$searchQuery'" else "No MCQ Folders Available Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Upload questions to create folders from the Upload Center.", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                } else {
                    displayFolders.forEach { folder ->
                        val isFolderHidden = hiddenItemKeys.contains("folder_${folder.id}")
                        val mcqsInFolder = mcqs.filter { m ->
                            m.folderId == folder.id || m.folderName.trim().equals(folder.name.trim(), ignoreCase = true)
                        }

                        Card(
                            onClick = { selectedFolderId = folder.id; selectedSubfolder = null },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isFolderHidden) Color(0xFFF1F5F9) else Color.White),
                            border = BorderStroke(1.dp, if (isFolderHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(folder.emoji.ifBlank { "🎯" }, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(folder.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Color(0xFF0F172A))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (isFolderHidden) {
                                                Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
                                                    Text("Hidden", fontSize = 9.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        Text("${mcqsInFolder.size} MCQs", fontSize = 11.5.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ManageMenu(
                                        onRename = { onRenameFolder(folder) },
                                        onDelete = { onDeleteFolder(folder) },
                                        onToggleHide = { onToggleHide("folder_${folder.id}") },
                                        isItemHidden = isFolderHidden
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper to render a folder and its nested Subfolder -> Chapter -> Question hierarchy
 */
@Composable
private fun renderMCQFolderCard(
    folder: ContentFolder,
    isFolderHidden: Boolean,
    mcqsInFolder: List<MCQItem>,
    expandedChapters: MutableMap<String, Boolean>,
    expandedFolders: MutableMap<String, Boolean>,
    hiddenItemKeys: Set<String>,
    onToggleHide: (String) -> Unit,
    onRenameFolder: (ContentFolder) -> Unit,
    onDeleteFolder: (ContentFolder) -> Unit,
    onDeleteSubfolder: (folderId: String, folderName: String, subfolderName: String, count: Int) -> Unit,
    onDeleteChapter: (folderId: String, folderName: String, subfolderName: String, chapterName: String, count: Int) -> Unit,
    onEditMCQ: (MCQItem) -> Unit,
    onDeleteMCQ: (MCQItem) -> Unit,
    onFolderClick: (ContentFolder) -> Unit,
    selectedQuestionIds: Set<String>,
    onToggleQuestionSelection: (String) -> Unit
) {
    val folderKey = "folder_exp_${folder.id}"
    val isFolderExpanded = expandedFolders[folderKey] != false

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFolderHidden) Color(0xFFF8FAFC) else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isFolderHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Folder Header with click to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick(folder) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isFolderExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(folder.emoji.ifBlank { "🎯" }, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = folder.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isFolderHidden) Color(0xFF64748B) else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (isFolderHidden) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isFolderHidden) "👁️‍🗨️ Hidden" else "👁️ Active",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFolderHidden) Color(0xFFDC2626) else Color(0xFF16A34A),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "${mcqsInFolder.size} MCQs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (mcqsInFolder.isEmpty()) Color(0xFF94A3B8) else Color(0xFF16A34A)
                        )
                    }
                }

                ManageMenu(
                    onRename = { onRenameFolder(folder) },
                    onDelete = { onDeleteFolder(folder) },
                    onToggleHide = { onToggleHide("folder_${folder.id}") },
                    isItemHidden = isFolderHidden
                )
            }

            if (isFolderExpanded) {
                if (mcqsInFolder.isEmpty()) {
                    Text(
                        text = "📁 Empty folder (0 questions).",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                } else {
                    val subfoldersInFolder = mcqsInFolder.groupBy { it.subfolderName.ifBlank { "General" } }
                    subfoldersInFolder.forEach { (subfolderName, mcqsInSubfolder) ->
                        val isSubHidden = hiddenItemKeys.contains("sub_${folder.id}_${subfolderName.lowercase()}")

                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("📂", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$subfolderName (${mcqsInSubfolder.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = if (isSubHidden) Color(0xFF64748B) else Color(0xFF334155)
                                    )
                                }
                                ManageMenu(
                                    onDelete = { onDeleteSubfolder(folder.id, folder.name, subfolderName, mcqsInSubfolder.size) },
                                    onToggleHide = { onToggleHide("sub_${folder.id}_${subfolderName.lowercase()}") },
                                    isItemHidden = isSubHidden
                                )
                            }

                            val chaptersInSubfolder = mcqsInSubfolder.groupBy { it.chapterName.ifBlank { "General Practice" } }
                            chaptersInSubfolder.forEach { (chapterName, mcqsInChapter) ->
                                val isChapHidden = hiddenItemKeys.contains("chap_${folder.id}_${subfolderName.lowercase()}_${chapterName.lowercase()}")
                                val chapterKey = "mcq_ch_${folder.id}_${subfolderName}_${chapterName}"
                                val isExpanded = expandedChapters[chapterKey] != false

                                Column(modifier = Modifier.padding(top = 3.dp, start = 6.dp)) {
                                    Surface(
                                        onClick = { expandedChapters[chapterKey] = !isExpanded },
                                        color = if (isChapHidden) Color(0xFFF1F5F9) else Color(0xFFF8FAFC),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isChapHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = Color(0xFF475569),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("📖", fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = chapterName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isChapHidden) Color(0xFF64748B) else Color(0xFF1E293B),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                ManageMenu(
                                                    onDelete = { onDeleteChapter(folder.id, folder.name, subfolderName, chapterName, mcqsInChapter.size) },
                                                    onToggleHide = { onToggleHide("chap_${folder.id}_${subfolderName.lowercase()}_${chapterName.lowercase()}") },
                                                    isItemHidden = isChapHidden
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${mcqsInChapter.size} Qs",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF16A34A)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                ManageMenu(
                                                onDelete = { onDeleteChapter(folder.id, folder.name, subfolderName, chapterName, mcqsInChapter.size) },
                                                onToggleHide = { onToggleHide("chap_${folder.id}_${subfolderName.lowercase()}_${chapterName.lowercase()}") },
                                                isItemHidden = isChapHidden
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        mcqsInChapter.forEachIndexed { qIdx, mcqItem ->
                                            val isQHidden = hiddenItemKeys.contains("mcq_${mcqItem.id}")

                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isQHidden) Color(0xFFF1F5F9) else Color(0xFFF8FAFC)
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isQHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                            Checkbox(
                                                                checked = selectedQuestionIds.contains(mcqItem.id),
                                                                onCheckedChange = { onToggleQuestionSelection(mcqItem.id) }
                                                            )
                                                            Text(
                                                                text = "Q${qIdx + 1}. ${mcqItem.question}",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.5.sp,
                                                                color = if (isQHidden) Color(0xFF64748B) else Color(0xFF0F172A)
                                                            )
                                                        }
                                                        ManageMenu(
                                                            onEdit = { onEditMCQ(mcqItem) },
                                                            onDelete = { onDeleteMCQ(mcqItem) },
                                                            onToggleHide = { onToggleHide("mcq_${mcqItem.id}") },
                                                            isItemHidden = isQHidden
                                                        )
                                                    }

                                                    val letters = listOf("A", "B", "C", "D", "E", "F")
                                                    mcqItem.options.forEachIndexed { oIdx, opt ->
                                                        val isCorrect = oIdx == mcqItem.correctOptionIndex
                                                        Text(
                                                            text = "${letters.getOrNull(oIdx) ?: (oIdx + 1)}. ${opt.text} ${if (isCorrect) "✓" else ""}",
                                                            fontSize = 11.sp,
                                                            color = if (isCorrect) Color(0xFF16A34A) else Color(0xFF64748B),
                                                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                                            modifier = Modifier.padding(start = 6.dp, top = 1.dp)
                                                        )
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
    }
}

@Composable
fun EditMCQSectionDialog(
    section: com.example.model.MCQSection,
    onDismiss: () -> Unit,
    onSave: (com.example.model.MCQSection) -> Unit
) {
    var editTitle by remember { mutableStateOf(section.title) }
    var editSubtitle by remember { mutableStateOf(section.subtitle) }
    var editEmoji by remember { mutableStateOf(section.emoji) }
    val emojiList = listOf("🎯", "🧠", "🩺", "📚", "🔬", "💊", "🫀", "🧬", "🌟", "🏥", "⚡")

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Edit MCQ Section", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Section Title") },
                    textStyle = com.example.ui.theme.medInputTextStyle(),
                    colors = com.example.ui.theme.medTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = editSubtitle,
                    onValueChange = { editSubtitle = it },
                    label = { Text("Subtitle") },
                    textStyle = com.example.ui.theme.medInputTextStyle(),
                    colors = com.example.ui.theme.medTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Choose Section Icon:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(emojiList) { em ->
                        Surface(
                            onClick = { editEmoji = em },
                            shape = RoundedCornerShape(8.dp),
                            color = if (editEmoji == em) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                            border = if (editEmoji == em) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2563EB)) else null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(em, fontSize = 18.sp) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (editTitle.isNotBlank()) {
                                onSave(
                                    section.copy(
                                        title = editTitle.trim(),
                                        subtitle = editSubtitle.trim(),
                                        emoji = editEmoji
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}



@Composable
fun ViewMCQSectionDialog(
    folder: ContentFolder,
    mcqs: List<MCQItem>,
    onDismiss: () -> Unit,
    onEditMCQ: (MCQItem) -> Unit,
    onDeleteMCQ: (MCQItem) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("${folder.name} - ${mcqs.size} Questions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(mcqs) { mcqItem ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(mcqItem.question, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                mcqItem.options.forEachIndexed { oIdx, opt ->
                                    Text("${oIdx + 1}. ${opt.text}", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCQSectionHierarchyScreen(
    folder: ContentFolder,
    onBackClick: () -> Unit
) {
    val allMCQs by MedRepository.mcqs.collectAsState()
    val hiddenItemKeys by MedRepository.hiddenItemKeys.collectAsState()
    val folderMCQs = allMCQs.filter { it.folderId == folder.id || it.folderName.equals(folder.name, ignoreCase = true) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedQuestionIds by remember { mutableStateOf(setOf<String>()) }
    var editingMCQ by remember { mutableStateOf<MCQItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var currentFolderName by remember { mutableStateOf(folder.name) }
    var currentFolderEmoji by remember { mutableStateOf(folder.emoji) }

    var expandedSubfolders by remember { mutableStateOf(setOf<String>()) }
    var expandedChapters by remember { mutableStateOf(setOf<String>()) }

    val filteredMCQs = if (searchQuery.isBlank()) folderMCQs else folderMCQs.filter {
        it.question.contains(searchQuery, ignoreCase = true) ||
        it.chapterName.contains(searchQuery, ignoreCase = true) ||
        it.subfolderName.contains(searchQuery, ignoreCase = true) ||
        it.options.any { opt -> opt.text.contains(searchQuery, ignoreCase = true) }
    }

    val hierarchy = filteredMCQs.groupBy { it.subfolderName.ifBlank { "General Subfolder" } }
        .mapValues { (_, mcqs) -> mcqs.groupBy { it.chapterName.ifBlank { "General Chapter" } } }

    val isFolderHidden = hiddenItemKeys.contains("folder_${folder.id}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage: $currentFolderName (${folderMCQs.size} Qs)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ManageMenu(
                        onRename = { showRenameDialog = true },
                        onDelete = { MedRepository.deleteFolder(folder.id); onBackClick() },
                        onToggleHide = { MedRepository.toggleHideItem("folder_${folder.id}") },
                        isItemHidden = isFolderHidden
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("🔍 Search questions, chapters, subfolders...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = com.example.ui.theme.medTextFieldColors()
            )

            if (selectedQuestionIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedQuestionIds.size} selected", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1D4ED8))
                        Row {
                            TextButton(onClick = {
                                MedRepository.deleteMCQsByIds(selectedQuestionIds.toList())
                                selectedQuestionIds = emptySet()
                            }) {
                                Text("Delete", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredMCQs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No questions found in this section.", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hierarchy.forEach { (subfolder, chapters) ->
                        val isSubHidden = hiddenItemKeys.contains("sub_${folder.id}_${subfolder.lowercase()}")

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSubHidden) Color(0xFFF1F5F9) else Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f).clickable {
                                                expandedSubfolders = if (expandedSubfolders.contains(subfolder))
                                                    expandedSubfolders - subfolder
                                                else expandedSubfolders + subfolder
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (expandedSubfolders.contains(subfolder) || searchQuery.isNotBlank()) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("📂 $subfolder", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                        }
                                        ManageMenu(
                                            onDelete = { MedRepository.deleteMCQsBySubfolder(folder.id, folder.name, subfolder) },
                                            onToggleHide = { MedRepository.toggleHideItem("sub_${folder.id}_${subfolder.lowercase()}") },
                                            isItemHidden = isSubHidden
                                        )
                                    }

                                    if (expandedSubfolders.contains(subfolder) || searchQuery.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        chapters.forEach { (chapter, mcqs) ->
                                            val isChapHidden = hiddenItemKeys.contains("chap_${folder.id}_${subfolder.lowercase()}_${chapter.lowercase()}")

                                            Column(modifier = Modifier.padding(start = 12.dp, top = 6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f).clickable {
                                                            expandedChapters = if (expandedChapters.contains(chapter))
                                                                expandedChapters - chapter
                                                            else expandedChapters + chapter
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = if (expandedChapters.contains(chapter) || searchQuery.isNotBlank()) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
                                                            contentDescription = null,
                                                            tint = Color(0xFF64748B),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("📖 $chapter (${mcqs.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Color(0xFF334155))
                                                    }
                                                    ManageMenu(
                                                        onDelete = { MedRepository.deleteMCQsByChapter(folder.id, folder.name, subfolder, chapter) },
                                                        onToggleHide = { MedRepository.toggleHideItem("chap_${folder.id}_${subfolder.lowercase()}_${chapter.lowercase()}") },
                                                        isItemHidden = isChapHidden
                                                    )
                                                }

                                                if (expandedChapters.contains(chapter) || searchQuery.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    mcqs.forEachIndexed { qIdx, mcq ->
                                                        val isQHidden = hiddenItemKeys.contains("mcq_${mcq.id}")
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = CardDefaults.cardColors(containerColor = if (isQHidden) Color(0xFFF1F5F9) else Color(0xFFF8FAFC)),
                                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                                        ) {
                                                            Column(modifier = Modifier.padding(10.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                                        Checkbox(
                                                                            checked = selectedQuestionIds.contains(mcq.id),
                                                                            onCheckedChange = {
                                                                                selectedQuestionIds = if (selectedQuestionIds.contains(mcq.id))
                                                                                    selectedQuestionIds - mcq.id
                                                                                else selectedQuestionIds + mcq.id
                                                                            }
                                                                        )
                                                                        Text(
                                                                            text = "Q${qIdx + 1}: ${mcq.question}",
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 13.sp,
                                                                            color = if (isQHidden) Color(0xFF64748B) else Color(0xFF0F172A)
                                                                        )
                                                                    }
                                                                    ManageMenu(
                                                                        onEdit = { editingMCQ = mcq },
                                                                        onDelete = { MedRepository.deleteMCQ(mcq.id) },
                                                                        onToggleHide = { MedRepository.toggleHideItem("mcq_${mcq.id}") },
                                                                        isItemHidden = isQHidden
                                                                    )
                                                                }

                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                val letters = listOf("A", "B", "C", "D", "E", "F")
                                                                mcq.options.forEachIndexed { oIdx, opt ->
                                                                    val isCorrect = oIdx == mcq.correctOptionIndex
                                                                    Text(
                                                                        text = "${letters.getOrNull(oIdx) ?: (oIdx + 1)}. ${opt.text} ${if (isCorrect) "✓" else ""}",
                                                                        fontSize = 11.5.sp,
                                                                        color = if (isCorrect) Color(0xFF16A34A) else Color(0xFF64748B),
                                                                        fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                                                        modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                                                                    )
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
                }
            }
        }
    }

    // Rename Folder Dialog
    if (showRenameDialog) {
        Dialog(onDismissRequest = { showRenameDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Rename Folder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = currentFolderName,
                        onValueChange = { currentFolderName = it },
                        label = { Text("Folder Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (currentFolderName.isNotBlank()) {
                                    MedRepository.renameFolder(folder.id, currentFolderName.trim(), currentFolderEmoji)
                                }
                                showRenameDialog = false
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Edit MCQ Dialog
    editingMCQ?.let { mcq ->
        EditMCQDialog(
            mcq = mcq,
            onDismiss = { editingMCQ = null },
            onSave = { updated ->
                MedRepository.updateMCQ(updated)
                editingMCQ = null
            }
        )
    }
}

@Composable
fun FlashcardManageInterface(
    flashcards: List<FlashcardItem>,
    decks: List<ContentFolder>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    hiddenItemKeys: Set<String>,
    onToggleHide: (String) -> Unit,
    onEditFlashcard: (FlashcardItem) -> Unit,
    onDeleteFlashcard: (FlashcardItem) -> Unit,
    onRenameDeck: (ContentFolder) -> Unit,
    onDeleteDeck: (ContentFolder) -> Unit,
    onDeleteAllFlashcards: () -> Unit
) {
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedSubfolder by remember { mutableStateOf<String?>(null) }

    val allFlashcardDecks = remember(decks, flashcards) {
        val list = mutableListOf<ContentFolder>()
        val seenNames = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        decks.filter {
            it.sectionType.equals("FLASHCARD", ignoreCase = true) ||
            it.subjectId.equals("flashcard", ignoreCase = true) ||
            it.name.contains("Flashcard", ignoreCase = true)
        }.forEach {
            list.add(it)
            seenIds.add(it.id)
            seenNames.add(it.name.trim().lowercase())
        }

        flashcards.map { it.folderName.trim() }
            .filter { it.isNotBlank() && !seenNames.contains(it.lowercase()) }
            .distinct()
            .forEach { fName ->
                val sample = flashcards.firstOrNull { it.folderName.equals(fName, ignoreCase = true) }
                val fId = sample?.folderId?.ifBlank { "f_fc_${fName.lowercase().replace(" ", "_")}" } ?: "f_fc_${fName}"
                if (!seenIds.contains(fId)) {
                    val folder = ContentFolder(
                        id = fId,
                        prof = sample?.prof ?: com.example.model.ProfYear.FIRST_PROF,
                        subjectId = "flashcard",
                        name = fName,
                        emoji = "📇",
                        sectionType = "FLASHCARD"
                    )
                    list.add(folder)
                    seenIds.add(fId)
                    seenNames.add(fName.lowercase())
                }
            }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Breadcrumb & Back Bar
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (selectedFolderId != null) {
                        IconButton(
                            onClick = {
                                if (selectedSubfolder != null) selectedSubfolder = null
                                else selectedFolderId = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2563EB))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    val titleText = when {
                        selectedSubfolder != null -> selectedSubfolder!!
                        selectedFolderId != null -> allFlashcardDecks.find { it.id == selectedFolderId }?.name ?: "Folder"
                        else -> "Manage Flashcards"
                    }
                    Column {
                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        val subtitleText = when {
                            selectedSubfolder != null -> "Questions inside subfolder"
                            selectedFolderId != null -> "Subfolders & Chapters"
                            else -> "Select folder to manage"
                        }
                        Text(text = subtitleText, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }

                if (selectedFolderId == null) {
                    OutlinedButton(
                        onClick = onDeleteAllFlashcards,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("🔍 Search ${flashcards.size} flashcards...", fontSize = 13.sp) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Hierarchical Levels
        when {
            // ================= LEVEL 0: FOLDERS LIST =================
            selectedFolderId == null -> {
                if (allFlashcardDecks.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📇", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Flashcard Folders Yet", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Import flashcards using the Upload Center.", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                } else {
                    allFlashcardDecks.forEach { folder ->
                        val isFolderHidden = hiddenItemKeys.contains("fc_folder_${folder.id}")
                        val cardsInFolder = flashcards.filter {
                            it.folderId == folder.id || it.folderName.equals(folder.name, ignoreCase = true)
                        }

                        Card(
                            onClick = { selectedFolderId = folder.id },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isFolderHidden) Color(0xFFF1F5F9) else Color.White),
                            border = BorderStroke(1.dp, if (isFolderHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(folder.emoji.ifBlank { "📇" }, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(folder.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Color(0xFF0F172A))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (isFolderHidden) {
                                                Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
                                                    Text("Hidden", fontSize = 9.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        Text("${cardsInFolder.size} Flashcards", fontSize = 11.5.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ManageMenu(
                                        onRename = { onRenameDeck(folder) },
                                        onDelete = { onDeleteDeck(folder) },
                                        onToggleHide = { onToggleHide("fc_folder_${folder.id}") },
                                        isItemHidden = isFolderHidden
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }
            }

            // ================= LEVEL 1: SUBFOLDERS INSIDE FOLDER =================
            selectedFolderId != null && selectedSubfolder == null -> {
                val fId = selectedFolderId!!
                val folder = allFlashcardDecks.find { it.id == fId }
                val cardsInFolder = flashcards.filter {
                    folder == null || it.folderId == folder.id || it.folderName.equals(folder.name, ignoreCase = true)
                }
                val subfolders = cardsInFolder.groupBy { if (it.subfolderName.isNotBlank()) it.subfolderName.trim() else "General" }

                if (subfolders.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📂", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Subfolders in this Folder", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                        }
                    }
                } else {
                    subfolders.forEach { (subName, subCards) ->
                        val isSubHidden = hiddenItemKeys.contains("fc_sub_${fId}_${subName.lowercase()}")

                        Card(
                            onClick = { selectedSubfolder = subName },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSubHidden) Color(0xFFF1F5F9) else Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("📂", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(subName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                        Text("${subCards.size} Cards • ${subCards.map { it.chapterName }.filter { it.isNotBlank() }.distinct().size} Chapters", fontSize = 11.5.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }

            // ================= LEVEL 2: QUESTIONS INSIDE SUBFOLDER =================
            selectedSubfolder != null -> {
                val fId = selectedFolderId!!
                val subName = selectedSubfolder!!
                val folder = allFlashcardDecks.find { it.id == fId }
                val cardsInSub = flashcards.filter {
                    (folder == null || it.folderId == folder.id || it.folderName.equals(folder.name, ignoreCase = true)) &&
                    (it.subfolderName.equals(subName, ignoreCase = true) || (subName == "General" && it.subfolderName.isBlank()))
                }

                val filteredCards = if (searchQuery.isBlank()) cardsInSub else cardsInSub.filter {
                    it.question.contains(searchQuery, ignoreCase = true) ||
                    it.answer.contains(searchQuery, ignoreCase = true) ||
                    it.chapterName.contains(searchQuery, ignoreCase = true) ||
                    it.tags.contains(searchQuery, ignoreCase = true)
                }

                if (filteredCards.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📇", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Flashcards Found", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                        }
                    }
                } else {
                    filteredCards.forEachIndexed { idx, card ->
                        val isHidden = hiddenItemKeys.contains("fc_${card.id}")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isHidden) Color(0xFFF1F5F9) else Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, if (isHidden) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Q${idx + 1}: ", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF2563EB))
                                            Text(card.question, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            if (isHidden) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
                                                    Text("Hidden", fontSize = 9.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Ans: ${card.answer}", fontSize = 12.sp, color = Color(0xFF059669), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (card.chapterName.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("📖 Chapter: ${card.chapterName}", fontSize = 11.sp, color = Color(0xFF475569))
                                        }
                                    }

                                    ManageMenu(
                                        onEdit = { onEditFlashcard(card) },
                                        onDelete = { onDeleteFlashcard(card) },
                                        onToggleHide = { onToggleHide("fc_${card.id}") },
                                        isItemHidden = isHidden
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

