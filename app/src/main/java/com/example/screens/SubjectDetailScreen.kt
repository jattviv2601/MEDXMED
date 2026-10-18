package com.example.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.MedRepository
import com.example.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    subject: Subject,
    onBackClick: () -> Unit,
    onPdfClick: (title: String, mediaUrl: String) -> Unit = { _, _ -> },
    onVideoClick: (title: String, mediaUrl: String, chapterName: String, subjectName: String, folderId: String) -> Unit = { _, _, _, _, _ -> }
) {
    // ONLY TWO TABS: 0 = Notes (PDF), 1 = Videos
    var selectedTab by remember { mutableIntStateOf(0) }
    val allFolders by MedRepository.folders.collectAsState()
    val allContents by MedRepository.contents.collectAsState()

    // Navigation state inside Subject:
    // When selectedFolder == null -> shows list of Main Folders for this Subject & Section
    // When selectedFolder != null -> shows Dedicated Folder View (New Interface with Subfolders & Files)
    var selectedFolder by remember { mutableStateOf<ContentFolder?>(null) }
    var selectedSubfolderName by remember { mutableStateOf<String?>(null) }
    var folderSearchQuery by remember { mutableStateOf("") }
    var selectedContentForViewing by remember { mutableStateOf<StudyContentItem?>(null) }

    val currentType = if (selectedTab == 0) StudyContentType.PDF else StudyContentType.VIDEO

    // All contents for this subject & current tab (PDF vs Video)
    val currentTypeContents = remember(allContents, subject, currentType) {
        allContents.filter { it.subjectId == subject.id && it.prof == subject.prof && it.type == currentType }
    }

    // Folders for this subject strictly filtered by Section:
    // Notes tab shows ONLY folders marked as "PDF" or (folders marked as "ALL" that contain PDFs) or folders containing PDFs
    // Videos tab shows ONLY folders marked as "VIDEO" or (folders marked as "ALL" that contain Videos) or folders containing Videos
    val currentSectionFolders = remember(allFolders, currentTypeContents, subject, selectedTab) {
        val expectedSection = if (selectedTab == 0) "PDF" else "VIDEO"
        allFolders.filter { f ->
            f.subjectId == subject.id && f.prof == subject.prof && (
                f.sectionType == expectedSection ||
                (f.sectionType == "ALL" && currentTypeContents.any { it.folderId == f.id })
            )
        }
    }

    // Handle back button for internal folder and subfolder navigation
    BackHandler(enabled = selectedFolder != null) {
        if (selectedSubfolderName != null) {
            selectedSubfolderName = null
        } else {
            selectedFolder = null
            folderSearchQuery = ""
        }
    }

    // Global Theme State
    val currentTheme by MedRepository.currentTheme.collectAsState()
    val themeBgColor = when (currentTheme) {
        1 -> Color(0xFF0F172A) // Neon Dark
        2 -> Color(0xFFFEF9C3) // Study Sepia
        else -> Color(0xFFF8FAFC) // Default White
    }
    val mainTextColor = when (currentTheme) {
        1 -> Color.White
        2 -> Color(0xFF422006)
        else -> Color(0xFF0F172A)
    }

    // ==========================================
    // 1. DEDICATED FOLDER & SUBFOLDER VIEW
    // ==========================================
    if (selectedFolder != null) {
        val activeFolder = selectedFolder!!
        val folderItems = remember(currentTypeContents, activeFolder) {
            currentTypeContents.filter { it.folderId == activeFolder.id }
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
                        val displayEmoji = if (selectedTab == 1) "🎥" else activeFolder.emoji
                        Text(text = displayEmoji, fontSize = 18.sp)
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
                    // Signature MedXMed Bottom Gradient Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF0062FF),
                                        Color(0xFF8B2CF5),
                                        Color(0xFFFF1493)
                                    )
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
                // Subfolders List (when no subfolder is selected yet and subfolders exist)
                if (selectedSubfolderName == null && subfolderNames.isNotEmpty()) {
                    item {
                        Text(
                            text = "📁 Subfolders / Modules (${subfolderNames.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (currentTheme == 1) Color.LightGray else Color(0xFF1E293B),
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(subfolderNames) { subName ->
                        val subItemsCount = folderItems.count {
                            it.chapterName.equals(subName, ignoreCase = true)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSubfolderName = subName },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (currentTheme == 1) Color(0xFF334155) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (currentTheme == 1) Color(0xFF1E3A8A) else Color(0xFFEFF6FF),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("📁", fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (currentTheme == 1) Color.White else Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$subItemsCount ${if (selectedTab == 0) "PDF file(s)" else "video(s)"}",
                                        fontSize = 12.sp,
                                        color = if (currentTheme == 1) Color.Gray else Color(0xFF64748B)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = if (currentTheme == 1) Color.Gray else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    if (displayedItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "📄 Direct Files (${displayedItems.size}):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (currentTheme == 1) Color.LightGray else Color(0xFF1E293B),
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                    }
                }

                // Files List
                if (displayedItems.isEmpty() && (selectedSubfolderName != null || subfolderNames.isEmpty())) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (selectedTab == 0) "📄" else "🎥",
                                    fontSize = 42.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No ${if (selectedTab == 0) "PDF notes" else "videos"} available here",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (currentTheme == 1) Color.Gray else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                } else {
                    items(displayedItems, key = { it.id }) { item ->
                        if (item.type == StudyContentType.VIDEO) {
                            VideoThumbnailCard(
                                item = item,
                                subjectName = subject.name,
                                onClick = {
                                    onVideoClick(item.title, item.mediaUrl, item.chapterName, subject.name, item.folderId)
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
        // ==========================================
        // 2. MAIN SUBJECT VIEW (2 TABS: NOTES & VIDEOS)
        // ==========================================
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
                        Text(text = subject.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = subject.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                lineHeight = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subject.prof.title,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B),
                                lineHeight = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Signature MedXMed Bottom Gradient Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF0062FF),
                                        Color(0xFF8B2CF5),
                                        Color(0xFFFF1493)
                                    )
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
                // STRICTLY TWO TABS ONLY: NOTES & VIDEOS
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (currentTheme == 1) Color(0xFF0F172A) else Color.White,
                    contentColor = if (currentTheme == 1) Color(0xFF00E5FF) else Color(0xFF2563EB)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            selectedFolder = null
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📚", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Notes (PDF)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (currentTheme == 1) Color.White else Color.Unspecified)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            selectedFolder = null
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎬", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Videos", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (currentTheme == 1) Color.White else Color.Unspecified)
                            }
                        }
                    )
                }

                if (currentSectionFolders.isEmpty() && currentTypeContents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selectedTab == 0) "📚" else "🎬", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No ${if (selectedTab == 0) "Notes / PDFs" else "Videos"} Available Yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (currentTheme == 1) Color.Gray else Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Upload from the Admin Panel to see them here",
                                fontSize = 12.sp,
                                color = if (currentTheme == 1) Color.DarkGray else Color(0xFF94A3B8)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text(
                                text = if (selectedTab == 0) "Notes Folders" else "Video Folders",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTheme == 1) Color.White else Color(0xFF1E293B)
                            )
                        }

                        // Folder Cards Grid / List
                        items(currentSectionFolders, key = { it.id }) { folder ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Open the DEDICATED FOLDER SCREEN!
                                        selectedFolder = folder
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White),
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
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (selectedTab == 0) Color(0xFFDCFCE7).copy(alpha = if (currentTheme == 1) 0.2f else 1.0f) else Color(0xFFDBEAFE).copy(alpha = if (currentTheme == 1) 0.2f else 1.0f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val displayEmoji = if (selectedTab == 1) "🎥" else folder.emoji
                                            Text(text = displayEmoji, fontSize = 22.sp)
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Text(
                                            text = folder.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (currentTheme == 1) Color.White else Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open Folder",
                                        tint = if (currentTheme == 1) Color.Gray else Color(0xFF94A3B8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Direct unfiled items (if any content exists not assigned to any folder)
                        val unfiledItems = currentTypeContents.filter { item ->
                            currentSectionFolders.none { it.id == item.folderId }
                        }

                        if (unfiledItems.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "📄 Other Materials (${unfiledItems.size}):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (currentTheme == 1) Color.LightGray else Color(0xFF64748B)
                                )
                            }
                            items(unfiledItems, key = { it.id }) { item ->
                                if (item.type == StudyContentType.VIDEO) {
                                    VideoThumbnailCard(
                                        item = item,
                                        subjectName = subject.name,
                                        onClick = {
                                            onVideoClick(item.title, item.mediaUrl, item.chapterName, subject.name, item.folderId)
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
            }
        }
    }

    // Media Viewer Dialog (Video or PDF info)
    selectedContentForViewing?.let { item ->
        Dialog(onDismissRequest = { selectedContentForViewing = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (item.type == StudyContentType.VIDEO) Color(0xFFDBEAFE) else Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (item.type == StudyContentType.VIDEO) "VIDEO LECTURE" else "PDF NOTE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.type == StudyContentType.VIDEO) Color(0xFF2563EB) else Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        IconButton(onClick = { selectedContentForViewing = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )

                    if (item.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.description,
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Source: ${item.mediaUrl}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val currentItem = selectedContentForViewing
                                selectedContentForViewing = null
                                if (currentItem?.type == StudyContentType.PDF) {
                                    onPdfClick(currentItem.title, currentItem.mediaUrl)
                                } else if (currentItem != null) {
                                    onVideoClick(currentItem.title, currentItem.mediaUrl, currentItem.chapterName, subject.name, currentItem.folderId)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.type == StudyContentType.VIDEO) Color(0xFF2563EB) else Color(0xFF059669)
                            )
                        ) {
                            Icon(
                                if (item.type == StudyContentType.VIDEO) Icons.Default.PlayArrow else Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (item.type == StudyContentType.VIDEO) "Play Video in App" else "Read PDF in App")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContentItemRow(
    item: StudyContentItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item.type == StudyContentType.VIDEO) Color(0xFFDBEAFE) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.type == StudyContentType.VIDEO) Icons.Default.PlayArrow else Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = if (item.type == StudyContentType.VIDEO) Color(0xFF2563EB) else Color(0xFF64748B),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
