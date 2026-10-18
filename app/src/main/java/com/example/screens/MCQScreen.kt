package com.example.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MedRepository
import com.example.model.ContentFolder
import com.example.model.MCQItem
import com.example.model.MCQSection
import com.example.model.ProfYear

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCQScreen(
    onBackClick: () -> Unit,
    onOpenChapter: (folderId: String, chapterName: String, subfolderName: String) -> Unit
) {
    val allMCQs by MedRepository.mcqs.collectAsState()
    val allFolders by MedRepository.folders.collectAsState()
    val mcqSections by MedRepository.mcqSections.collectAsState()
    val hiddenItemKeys by MedRepository.hiddenItemKeys.collectAsState()
    val currentTheme by MedRepository.currentTheme.collectAsState()

    // Filter visible MCQs
    val visibleMCQs = remember(allMCQs, hiddenItemKeys) {
        allMCQs.filterNot { m ->
            hiddenItemKeys.contains("mcq_${m.id}") ||
            hiddenItemKeys.contains("chap_${m.folderId}_${m.subfolderName.lowercase()}_${m.chapterName.lowercase()}") ||
            hiddenItemKeys.contains("sub_${m.folderId}_${m.subfolderName.lowercase()}") ||
            hiddenItemKeys.contains("folder_${m.folderId}") ||
            hiddenItemKeys.contains("folder_${m.folderName.lowercase()}") ||
            (m.sectionId.isNotBlank() && hiddenItemKeys.contains("sec_${m.sectionId}"))
        }
    }

    // Level navigation state
    var selectedSection by remember { mutableStateOf<MCQSection?>(null) }
    var selectedFolder by remember { mutableStateOf<ContentFolder?>(null) }
    var selectedSubfolderName by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Theme colors
    val themeBgColor = when (currentTheme) {
        1 -> Color(0xFF0F172A)
        2 -> Color(0xFFFAF8F5)
        3 -> Color(0xFF0C4A6E)
        4 -> Color(0xFF171717)
        else -> Color(0xFFF8FAFC)
    }
    val cardBgColor = when (currentTheme) {
        1 -> Color(0xFF1E293B)
        2 -> Color(0xFFFFFFFF)
        3 -> Color(0xFF075985)
        4 -> Color(0xFF262626)
        else -> Color.White
    }
    val primaryTextColor = when (currentTheme) {
        1 -> Color(0xFFF8FAFC)
        3 -> Color.White
        4 -> Color.White
        else -> Color(0xFF0F172A)
    }
    val secondaryTextColor = when (currentTheme) {
        1 -> Color(0xFF94A3B8)
        3 -> Color(0xFFBAE6FD)
        4 -> Color(0xFFA3A3A3)
        else -> Color(0xFF64748B)
    }

    // Filter MCQ Folders and exclude hidden folders
    val mcqFolders = remember(allFolders, visibleMCQs, hiddenItemKeys) {
        val directFolders = allFolders.filter {
            (it.sectionType.equals("MCQ", ignoreCase = true) ||
            it.subjectId.equals("mcq", ignoreCase = true) ||
            it.name.contains("MCQ", ignoreCase = true)) &&
            !hiddenItemKeys.contains("folder_${it.id}") &&
            !hiddenItemKeys.contains("folder_${it.name.lowercase()}")
        }
        val folderNames = directFolders.map { it.name.trim().lowercase() }.toSet()
        val extraFolders = visibleMCQs.map { it.folderName.trim() }
            .filter { it.isNotBlank() && !folderNames.contains(it.lowercase()) }
            .distinct()
            .filter { name -> !hiddenItemKeys.contains("folder_${name.lowercase()}") }
            .map { name ->
                val sample = visibleMCQs.firstOrNull { it.folderName.equals(name, ignoreCase = true) }
                ContentFolder(
                    id = sample?.folderId?.ifBlank { "f_${name.lowercase().replace(" ", "_")}" } ?: "f_${name}",
                    prof = sample?.prof ?: ProfYear.FIRST_PROF,
                    subjectId = sample?.sectionId?.ifBlank { "mcq" } ?: "mcq",
                    name = name,
                    emoji = "🎯",
                    sectionType = "MCQ"
                )
            }
            .filter { !hiddenItemKeys.contains("folder_${it.id}") }
        (directFolders + extraFolders).distinctBy { it.id }
    }

    // Step-by-step back handling: Chapter -> Subfolder -> Folder -> Section -> Main
    BackHandler {
        if (selectedSubfolderName != null) {
            selectedSubfolderName = null
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else if (selectedSection != null) {
            selectedSection = null
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                selectedSubfolderName != null -> selectedSubfolderName ?: "MCQs"
                                selectedFolder != null -> selectedFolder?.name ?: "MCQs"
                                selectedSection != null -> selectedSection?.title ?: "MCQs"
                                else -> "MCQ Practice Bank 🎯"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedSubfolderName != null) {
                                selectedSubfolderName = null
                            } else if (selectedFolder != null) {
                                selectedFolder = null
                            } else if (selectedSection != null) {
                                selectedSection = null
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.testTag("mcq_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBgColor)
            )
        },
        containerColor = themeBgColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // =========================================================================
                // LEVEL 3: CHAPTERS VIEW (Inside selected Subfolder)
                // =========================================================================
                selectedFolder != null && selectedSubfolderName != null -> {
                    val activeFolder = selectedFolder!!
                    val activeSub = selectedSubfolderName!!
                    val subfolderMCQs = remember(visibleMCQs, activeFolder, activeSub) {
                        visibleMCQs.filter {
                            (it.folderId == activeFolder.id || it.folderName.trim().equals(activeFolder.name.trim(), ignoreCase = true)) &&
                            (it.subfolderName.trim().equals(activeSub.trim(), ignoreCase = true) || (activeSub.trim().equals("General", ignoreCase = true) && it.subfolderName.trim().isBlank()))
                        }
                    }
                    val chapters = remember(subfolderMCQs, hiddenItemKeys, activeFolder, activeSub) {
                        subfolderMCQs.map { it.chapterName.ifBlank { "General Chapter" } }
                            .distinct()
                            .filterNot { chap ->
                                hiddenItemKeys.contains("chap_${activeFolder.id}_${activeSub.lowercase()}_${chap.lowercase()}")
                            }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (chapters.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No chapters available in this subfolder yet.", color = secondaryTextColor)
                                }
                            }
                        }

                        items(chapters) { chapterName ->
                            MCQChapterCard(
                                chapterName = chapterName,
                                cardBgColor = cardBgColor,
                                primaryTextColor = primaryTextColor,
                                onClick = { onOpenChapter(activeFolder.id, chapterName, activeSub) }
                            )
                        }
                    }
                }

                // =========================================================================
                // LEVEL 2: SUBFOLDERS VIEW (Inside selected Folder)
                // =========================================================================
                selectedFolder != null -> {
                    val activeFolder = selectedFolder!!
                    val folderMCQs = remember(visibleMCQs, activeFolder) {
                        visibleMCQs.filter {
                            it.folderId == activeFolder.id || it.folderName.equals(activeFolder.name, ignoreCase = true)
                        }
                    }
                    val subfolderNames = remember(folderMCQs, hiddenItemKeys, activeFolder) {
                        folderMCQs.map { it.subfolderName.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .filterNot { sub ->
                                hiddenItemKeys.contains("sub_${activeFolder.id}_${sub.lowercase()}")
                            }
                    }
                    val directChapters = remember(folderMCQs, hiddenItemKeys, activeFolder) {
                        folderMCQs.filter { it.subfolderName.isBlank() }
                            .map { it.chapterName.ifBlank { "General Practice" } }
                            .distinct()
                            .filterNot { chap ->
                                hiddenItemKeys.contains("chap_${activeFolder.id}__${chap.lowercase()}")
                            }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Subfolders
                        items(subfolderNames) { subName ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSubfolderName = subName },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📁", fontSize = 26.sp)
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = subName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = primaryTextColor
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = secondaryTextColor)
                                }
                            }
                        }

                        // Direct Chapters (if any without subfolder)
                        items(directChapters) { chapterName ->
                            MCQChapterCard(
                                chapterName = chapterName,
                                cardBgColor = cardBgColor,
                                primaryTextColor = primaryTextColor,
                                onClick = { onOpenChapter(activeFolder.id, chapterName, "") }
                            )
                        }

                        if (subfolderNames.isEmpty() && directChapters.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No subfolders or chapters uploaded yet.", color = secondaryTextColor)
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // LEVEL 1: FOLDERS VIEW (Inside selected Section)
                // =========================================================================
                selectedSection != null -> {
                    val activeSection = selectedSection!!
                    val sectionFolders = remember(mcqFolders, visibleMCQs, activeSection, searchQuery, mcqSections) {
                        mcqFolders.filter { folder ->
                            val matchesSearch = searchQuery.isBlank() || folder.name.contains(searchQuery, ignoreCase = true)
                            val matchesSection = folder.subjectId == activeSection.id ||
                                visibleMCQs.any { m ->
                                    (m.folderId == folder.id || m.folderName.equals(folder.name, ignoreCase = true)) &&
                                    (m.sectionId == activeSection.id || m.sectionName.equals(activeSection.title, ignoreCase = true))
                                } ||
                                (folder.subjectId == "mcq" && mcqSections.firstOrNull()?.id == activeSection.id)
                            matchesSearch && matchesSection
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Search Bar
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mcq_search_input"),
                                shape = RoundedCornerShape(14.dp),
                                placeholder = { Text("Search topics...", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = secondaryTextColor) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = com.example.ui.theme.medTextFieldColors()
                            )
                        }

                        if (sectionFolders.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No folders available in this section yet.", color = secondaryTextColor)
                                }
                            }
                        }

                        // Clean Folder Cards
                        items(sectionFolders) { folder ->
                            val isSectionRoot = folder.subjectId == activeSection.id || folder.name.contains("Create", ignoreCase = true)
                            
                            if (isSectionRoot) {
                                // 🌟 Big Layout for Section Root Folder or Created Folder
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 110.dp)
                                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x33000000))
                                        .clickable {
                                            selectedFolder = folder
                                            selectedSubfolderName = null
                                        }
                                        .testTag("mcq_section_folder_${folder.id}"),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Brush.horizontalGradient(listOf(Color(0xFF0D9488), Color(0xFF0F766E))))
                                            .padding(18.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Surface(shape = CircleShape, color = Color(0x33FFFFFF), modifier = Modifier.size(52.dp)) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(text = folder.emoji.ifBlank { "🎯" }, fontSize = 26.sp)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Text(text = folder.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = Color.White)
                                        }
                                    }
                                }
                            } else {
                                // 📁 Standard Folder Layout
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFolder = folder
                                            selectedSubfolderName = null
                                        }
                                        .testTag("mcq_folder_${folder.id}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(Color(0xFFDBEAFE), Color(0xFFEDE9FE))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(folder.emoji.ifBlank { "🎯" }, fontSize = 26.sp)
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Text(
                                                text = folder.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = primaryTextColor
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Open Folder",
                                            tint = secondaryTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // LEVEL 0: MAIN SCREEN (Sections ONLY if sections exist, else Folders directly)
                // =========================================================================
                else -> {
                    val visibleSections = remember(mcqSections, hiddenItemKeys) {
                        mcqSections.filterNot { hiddenItemKeys.contains("sec_${it.id}") }
                    }
                    val filteredSections = remember(visibleSections, searchQuery) {
                        if (searchQuery.isBlank()) visibleSections
                        else visibleSections.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    }

                    val filteredDirectFolders = remember(mcqFolders, searchQuery) {
                        if (searchQuery.isBlank()) mcqFolders
                        else mcqFolders.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Search Bar
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mcq_search_input"),
                                shape = RoundedCornerShape(14.dp),
                                placeholder = { Text("Search MCQ topics...", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = secondaryTextColor) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = com.example.ui.theme.medTextFieldColors()
                            )
                        }

                        // If sections exist: Show ONLY Sections! Do NOT show folders below it!
                        if (mcqSections.isNotEmpty()) {
                            if (filteredSections.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No sections match '$searchQuery'", color = secondaryTextColor)
                                    }
                                }
                            }

                            items(filteredSections) { sec ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 96.dp)
                                        .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color(0x33000000))
                                        .clickable { selectedSection = sec }
                                        .testTag("mcq_section_${sec.id}"),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(sec.gradientBrush)
                                            .padding(18.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0x33FFFFFF),
                                                    modifier = Modifier.size(52.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(text = sec.emoji.ifBlank { "🎯" }, fontSize = 26.sp)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(14.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = sec.title,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 17.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (sec.subtitle.isNotBlank()) {
                                                        Text(
                                                            text = sec.subtitle,
                                                            color = Color(0xE6FFFFFF),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open Section",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // Show practice folders not in sections so no user-created folder is ever hidden
                            val unassignedFolders = filteredDirectFolders.filter { f ->
                                !mcqSections.any { sec ->
                                    f.subjectId == sec.id || f.subjectId.equals(sec.title, ignoreCase = true) ||
                                    visibleMCQs.any { m ->
                                        (m.folderId == f.id || m.folderName.trim().equals(f.name.trim(), ignoreCase = true)) &&
                                        (m.sectionId == sec.id || m.sectionName.trim().equals(sec.title.trim(), ignoreCase = true))
                                    }
                                }
                            }
                            if (unassignedFolders.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "📁 Practice Decks & Folders (${unassignedFolders.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = primaryTextColor
                                    )
                                }
                                items(unassignedFolders) { folder ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedFolder = folder
                                                selectedSubfolderName = null
                                            }
                                            .testTag("mcq_folder_${folder.id}"),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(Color(0xFFDBEAFE), Color(0xFFEDE9FE))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(folder.emoji.ifBlank { "🎯" }, fontSize = 26.sp)
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Text(
                                                    text = folder.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = primaryTextColor
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open Folder",
                                                tint = secondaryTextColor
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Only if NO sections exist: show folders directly
                            if (filteredDirectFolders.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🎯", fontSize = 36.sp)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = if (searchQuery.isNotBlank()) "No topics match '$searchQuery'" else "No MCQ folders available yet",
                                                fontWeight = FontWeight.Bold,
                                                color = primaryTextColor
                                            )
                                        }
                                    }
                                }
                            }

                            items(filteredDirectFolders) { folder ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFolder = folder
                                            selectedSubfolderName = null
                                        }
                                        .testTag("mcq_folder_${folder.id}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(Color(0xFFDBEAFE), Color(0xFFEDE9FE))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(folder.emoji.ifBlank { "🎯" }, fontSize = 26.sp)
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Text(
                                                text = folder.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = primaryTextColor
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Open Folder",
                                            tint = secondaryTextColor
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

@Composable
fun MCQChapterCard(
    chapterName: String,
    cardBgColor: Color,
    primaryTextColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📝", fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = chapterName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = primaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = Color(0xFF2563EB),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Start ➔",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
