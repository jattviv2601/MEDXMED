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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MedRepository
import com.example.model.ContentFolder
import com.example.model.CustomSection
import com.example.model.StudyContentItem
import com.example.model.StudyContentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSectionDetailScreen(
    section: CustomSection,
    onBackClick: () -> Unit,
    onPdfClick: (title: String, mediaUrl: String) -> Unit = { _, _ -> },
    onVideoClick: (title: String, mediaUrl: String, chapterName: String, subjectName: String, folderId: String) -> Unit = { _, _, _, _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Notes, 1 = Videos
    val allFolders by MedRepository.folders.collectAsState()
    val allContents by MedRepository.contents.collectAsState()

    var selectedFolder by remember { mutableStateOf<ContentFolder?>(null) }
    var selectedSubfolderName by remember { mutableStateOf<String?>(null) }
    var folderSearchQuery by remember { mutableStateOf("") }

    val currentType = if (selectedTab == 0) StudyContentType.PDF else StudyContentType.VIDEO

    // Contents filtered by this custom section ID
    val sectionContents = remember(allContents, section, currentType) {
        allContents.filter { it.subjectId == section.id && it.type == currentType }
    }

    // Folders for this custom section
    val sectionFolders = remember(allFolders, sectionContents, section, selectedTab) {
        val expectedSection = if (selectedTab == 0) "PDF" else "VIDEO"
        allFolders.filter { f ->
            f.subjectId == section.id && (
                f.sectionType == expectedSection ||
                (f.sectionType == "ALL" && sectionContents.any { it.folderId == f.id }) ||
                f.sectionType == "ALL"
            )
        }
    }

    BackHandler(enabled = selectedFolder != null) {
        if (selectedSubfolderName != null) {
            selectedSubfolderName = null
        } else {
            selectedFolder = null
            folderSearchQuery = ""
        }
    }

    val currentTheme by MedRepository.currentTheme.collectAsState()
    val themeBgColor = when (currentTheme) {
        1 -> Color(0xFF0F172A)
        2 -> Color(0xFFFEF9C3)
        else -> Color(0xFFF8FAFC)
    }

    if (selectedFolder != null) {
        // ========================================================
        // DEDICATED FOLDER & SUBFOLDER VIEW FOR CUSTOM SECTION
        // ========================================================
        val activeFolder = selectedFolder!!
        val folderItems = remember(sectionContents, activeFolder) {
            sectionContents.filter { it.folderId == activeFolder.id }
        }

        // Extract subfolders / chapters
        val subfolderNames = remember(folderItems) {
            folderItems.map { it.chapterName.trim() }
                .filter { it.isNotBlank() && !it.equals("General", ignoreCase = true) && !it.equals("Direct", ignoreCase = true) }
                .distinct()
        }

        val displayedItems = remember(folderItems, selectedSubfolderName, folderSearchQuery) {
            val baseList = if (selectedSubfolderName != null) {
                folderItems.filter { it.chapterName.equals(selectedSubfolderName, ignoreCase = true) }
            } else if (subfolderNames.isNotEmpty()) {
                // Direct items with no subfolder
                folderItems.filter {
                    it.chapterName.isBlank() ||
                    it.chapterName.equals("General", ignoreCase = true) ||
                    it.chapterName.equals("Direct", ignoreCase = true)
                }
            } else {
                folderItems
            }

            if (folderSearchQuery.isBlank()) baseList
            else baseList.filter { it.title.contains(folderSearchQuery, ignoreCase = true) }
        }

        Scaffold(
            topBar = {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(Color.White)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedSubfolderName != null) {
                                    selectedSubfolderName = null
                                } else {
                                    selectedFolder = null
                                    folderSearchQuery = ""
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (selectedTab == 1) "🎥" else activeFolder.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedSubfolderName != null) selectedSubfolderName!! else activeFolder.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedSubfolderName != null) {
                                Text(
                                    text = "📁 ${activeFolder.name}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    // MedXMed Gradient Accent Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0062FF), Color(0xFF8B2CF5), Color(0xFFFF1493))
                                )
                            )
                    )
                }
            },
            containerColor = themeBgColor
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Bar within folder
                item {
                    OutlinedTextField(
                        value = folderSearchQuery,
                        onValueChange = { folderSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search in this folder...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = com.example.ui.theme.medTextFieldColors()
                    )
                }

                // Subfolders List (when no subfolder is currently open)
                if (selectedSubfolderName == null && subfolderNames.isNotEmpty()) {
                    item {
                        Text(
                            text = "📁 Subfolders / Modules (${subfolderNames.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(subfolderNames) { subName ->
                        val subItemsCount = folderItems.count { it.chapterName.equals(subName, ignoreCase = true) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSubfolderName = subName },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(text = "📁", fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = subName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "$subItemsCount items",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF2563EB),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                            }
                        }
                    }

                    if (displayedItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "📄 Direct Files:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                if (displayedItems.isEmpty() && (selectedSubfolderName != null || subfolderNames.isEmpty())) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (selectedTab == 0) "📄" else "🎥", fontSize = 42.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No materials uploaded yet in this section", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(displayedItems, key = { it.id }) { item ->
                        if (item.type == StudyContentType.VIDEO) {
                            VideoThumbnailCard(
                                item = item,
                                subjectName = section.title,
                                onClick = {
                                    onVideoClick(item.title, item.mediaUrl, item.chapterName, section.title, item.folderId)
                                }
                            )
                        } else {
                            ContentItemRow(
                                item = item,
                                onClick = {
                                    onPdfClick(item.title, item.mediaUrl)
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // ========================================================
        // MAIN CUSTOM SECTION OVERVIEW (WITH TABS: NOTES & VIDEOS)
        // ========================================================
        Scaffold(
            topBar = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(Color.White)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEFF6FF),
                            modifier = Modifier.size(34.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = section.emoji, fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = section.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                lineHeight = 19.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (section.subtitle.isNotBlank()) {
                                Text(
                                    text = section.subtitle,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B),
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Signature MedXMed Bottom Gradient Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0062FF), Color(0xFF8B2CF5), Color(0xFFFF1493))
                                )
                            )
                    )
                }
            },
            containerColor = themeBgColor
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Section Hero Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(section.gradientBrush)
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = section.emoji, fontSize = 38.sp)
                            Column {
                                Text(
                                    text = section.title,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                if (section.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = section.description,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2 Tabs: Notes (PDF) vs Videos
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = Color(0xFF2563EB),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📚 Notes & PDFs", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎥 Video Lectures", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sectionFolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📁", fontSize = 42.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No folders created in this section yet", color = Color.Gray, fontSize = 13.sp)
                                    Text("Upload folders from Admin Panel to see them here.", color = Color.LightGray, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = if (selectedTab == 0) "Notes Folders (${sectionFolders.size})" else "Video Folders (${sectionFolders.size})",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        items(sectionFolders, key = { it.id }) { folder ->
                            val count = sectionContents.count { it.folderId == folder.id }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolder = folder },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selectedTab == 0) Color(0xFFDCFCE7) else Color(0xFFDBEAFE),
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(text = if (selectedTab == 1) "🎥" else folder.emoji, fontSize = 22.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = folder.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "$count materials inside",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF2563EB),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
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
