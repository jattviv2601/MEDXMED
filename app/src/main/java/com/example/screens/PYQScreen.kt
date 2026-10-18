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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MedRepository
import com.example.model.ContentFolder
import com.example.model.ProfYear
import com.example.model.StudyContentItem

@Composable
fun PYQScreen(
    onBackClick: () -> Unit,
    onPdfClick: (title: String, mediaUrl: String) -> Unit = { _, _ -> }
) {
    val contents by MedRepository.contents.collectAsState()
    val folders by MedRepository.folders.collectAsState()

    val pyqItems = remember(contents) { contents.filter { it.isPyq } }

    // Collect all PYQ folders cleanly (no prof grouping or extra headers)
    val pyqFolders = remember(folders, pyqItems) {
        val explicitFolders = folders.filter { it.sectionType == "PYQ" || it.name.contains("PYQ", ignoreCase = true) }
        val itemFolderIds = pyqItems.map { it.folderId }.toSet()
        val itemFolderNames = pyqItems.map { it.folderName.trim() }.toSet()

        val matchedFolders = (explicitFolders + folders.filter { it.id in itemFolderIds || it.name.trim() in itemFolderNames })
            .distinctBy { it.id.ifBlank { it.name.trim() } }

        val existingFolderNames = matchedFolders.map { it.name.trim() }.toSet()
        val virtualFolders = pyqItems
            .filter { it.folderName.trim().isNotBlank() && it.folderName.trim() !in existingFolderNames }
            .map { it.folderName.trim() }
            .distinct()
            .map { name ->
                ContentFolder(
                    id = "pyq_folder_${name.hashCode().toString().replace("-", "n")}",
                    prof = ProfYear.FIRST_PROF,
                    subjectId = "pyq",
                    name = name,
                    emoji = "📝",
                    sectionType = "PYQ"
                )
            }

        (matchedFolders + virtualFolders).sortedBy { it.name.lowercase() }
    }

    var selectedFolder by remember { mutableStateOf<ContentFolder?>(null) }
    var selectedSubfolderName by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Back button navigation: Subfolder -> Folder -> Root PYQ Bank
    BackHandler(enabled = selectedFolder != null) {
        if (selectedSubfolderName != null) {
            selectedSubfolderName = null
            searchQuery = ""
        } else {
            selectedFolder = null
            searchQuery = ""
        }
    }

    // Global Theme Colors
    val currentTheme by MedRepository.currentTheme.collectAsState()
    val themeBgColor = when (currentTheme) {
        1 -> Color(0xFF0F172A) // Dark
        2 -> Color(0xFFFEF9C3) // Sepia
        else -> Color(0xFFF8FAFC) // White / Light
    }
    val cardBgColor = when (currentTheme) {
        1 -> Color(0xFF1E293B)
        2 -> Color(0xFFFEF08A)
        else -> Color.White
    }
    val primaryTextColor = when (currentTheme) {
        1 -> Color.White
        2 -> Color(0xFF422006)
        else -> Color(0xFF0F172A)
    }
    val secondaryTextColor = when (currentTheme) {
        1 -> Color(0xFF94A3B8)
        2 -> Color(0xFF713F12)
        else -> Color(0xFF64748B)
    }
    val backBtnBgColor = when (currentTheme) {
        1 -> Color(0xFF334155)
        2 -> Color(0xFFFDE047)
        else -> Color(0xFFF1F5F9)
    }

    // Safe Top Inset: Positioned nicely inside layout, away from curved top corners and status bar
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val effectiveTopPadding = if (statusBarInset > 0.dp) statusBarInset + 12.dp else 26.dp

    if (selectedFolder != null && selectedSubfolderName != null) {
        // =========================================================================
        // LEVEL 3: SUBFOLDER VIEW (Shows Chapter / Paper List inside Subfolder)
        // Sequence: Touching any title immediately opens PDF!
        // =========================================================================
        val activeFolder = selectedFolder!!
        val activeSubfolder = selectedSubfolderName!!

        val subfolderItems = remember(pyqItems, activeFolder, activeSubfolder, searchQuery) {
            val itemsInSub = pyqItems.filter {
                (it.folderId == activeFolder.id || it.folderName.equals(activeFolder.name, ignoreCase = true)) &&
                        it.chapterName.trim().equals(activeSubfolder, ignoreCase = true)
            }
            if (searchQuery.isBlank()) itemsInSub
            else itemsInSub.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        Scaffold(
            topBar = {
                PYQHeader(
                    title = activeSubfolder,
                    subtitle = activeFolder.name,
                    onBackClick = {
                        selectedSubfolderName = null
                        searchQuery = ""
                    },
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    backBtnBgColor = backBtnBgColor,
                    topPadding = effectiveTopPadding
                )
            },
            containerColor = themeBgColor
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 800.dp)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("Search papers in $activeSubfolder...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = secondaryTextColor) },
                        colors = com.example.ui.theme.medTextFieldColors(),
                        singleLine = true
                    )
                }

                if (subfolderItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No papers match '$searchQuery'" else "No papers in this subfolder yet",
                                color = secondaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                items(subfolderItems) { item ->
                    PYQPaperRowItem(
                        item = item,
                        cardBgColor = cardBgColor,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        onClick = { onPdfClick(item.title, item.mediaUrl) }
                    )
                }
            }
        }
    } else if (selectedFolder != null) {
        // =========================================================================
        // LEVEL 2: FOLDER VIEW (Shows Subfolders and any Direct Papers inside Folder)
        // Sequence: User taps folder -> opens new interface showing Subfolders!
        // =========================================================================
        val activeFolder = selectedFolder!!
        val folderItems = remember(pyqItems, activeFolder) {
            pyqItems.filter {
                it.folderId == activeFolder.id || it.folderName.equals(activeFolder.name, ignoreCase = true)
            }
        }

        // Distinct Subfolder / Chapter names inside this folder
        val subfolderNames = remember(folderItems, searchQuery) {
            val allSubs = folderItems.map { it.chapterName.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            if (searchQuery.isBlank()) allSubs
            else allSubs.filter { it.contains(searchQuery, ignoreCase = true) }
        }

        // Papers that don't belong to any subfolder (Direct items)
        val directItems = remember(folderItems, searchQuery) {
            val direct = folderItems.filter { it.chapterName.trim().isBlank() }
            if (searchQuery.isBlank()) direct
            else direct.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        Scaffold(
            topBar = {
                PYQHeader(
                    title = activeFolder.name,
                    leadingEmoji = activeFolder.emoji,
                    onBackClick = {
                        selectedFolder = null
                        searchQuery = ""
                    },
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    backBtnBgColor = backBtnBgColor,
                    topPadding = effectiveTopPadding
                )
            },
            containerColor = themeBgColor
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("Search subfolders or papers...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = secondaryTextColor) },
                        colors = com.example.ui.theme.medTextFieldColors(),
                        singleLine = true
                    )
                }

                // 1. Subfolders (New Interface / Folder Cards)
                if (subfolderNames.isNotEmpty()) {
                    items(subfolderNames) { subName ->
                        val count = folderItems.count { it.chapterName.trim().equals(subName, ignoreCase = true) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSubfolderName = subName
                                    searchQuery = ""
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = primaryTextColor
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Subfolder",
                                    tint = secondaryTextColor
                                )
                            }
                        }
                    }
                }

                // 2. Direct Papers (Without Subfolder)
                if (directItems.isNotEmpty()) {
                    items(directItems) { item ->
                        PYQPaperRowItem(
                            item = item,
                            cardBgColor = cardBgColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onClick = { onPdfClick(item.title, item.mediaUrl) }
                        )
                    }
                }

                // Empty State
                if (subfolderNames.isEmpty() && directItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching subfolders or papers" else "No papers in this folder yet",
                                color = secondaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // LEVEL 1: ROOT PYQ BANK (Shows Folders directly, NO 3RD PROF HEADING!)
        // Clean layout matching admin folder sequence without extra text or prof header
        // =========================================================================
        val filteredFolders = remember(pyqFolders, searchQuery) {
            if (searchQuery.isBlank()) pyqFolders
            else pyqFolders.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        Scaffold(
            topBar = {
                PYQHeader(
                    title = "PYQ Bank",
                    onBackClick = onBackClick,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    backBtnBgColor = backBtnBgColor,
                    topPadding = effectiveTopPadding
                )
            },
            containerColor = themeBgColor
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Field
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("Search PYQ bank...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = secondaryTextColor) },
                        colors = com.example.ui.theme.medTextFieldColors(),
                        singleLine = true
                    )
                }

                // Empty State
                if (filteredFolders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching folders found" else "No PYQ folders found",
                                color = secondaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Folders List directly (NO 3rd prof heading! Clean folder list)
                items(filteredFolders) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFolder = folder
                                selectedSubfolderName = null
                                searchQuery = ""
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (currentTheme == 1) Color(0xFF334155) else Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(folder.emoji, fontSize = 22.sp)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
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

/**
 * Beautiful, well-inset Header Component:
 * - Positioned safely below the status bar and device rounded corners (thoda niche)
 * - Has horizontal padding matching the content (layout ke andr)
 * - Clean circular back button and bold title
 */
@Composable
fun PYQHeader(
    title: String,
    subtitle: String? = null,
    leadingEmoji: String? = null,
    onBackClick: () -> Unit,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    backBtnBgColor: Color,
    topPadding: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = backBtnBgColor,
                modifier = Modifier.size(40.dp),
                onClick = onBackClick
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            if (!leadingEmoji.isNullOrBlank()) {
                Text(text = leadingEmoji, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE2E8F0).copy(alpha = 0.6f))
    }
}

/**
 * Reusable row item for a PYQ Paper / Chapter title.
 * Tapping directly opens the PDF via [onClick]!
 */
@Composable
fun PYQPaperRowItem(
    item: StudyContentItem,
    cardBgColor: Color = Color.White,
    primaryTextColor: Color = Color(0xFF0F172A),
    secondaryTextColor: Color = Color(0xFF64748B),
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFDCFCE7),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = primaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2563EB)
            ) {
                Text(
                    text = "View PDF",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
