package com.example.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.MedRepository
import com.example.data.cloud.CloudSyncEngine
import com.example.model.CustomSection
import com.example.model.ProfYear
import com.example.model.StudyContentType
import com.example.model.Subject
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onProfClick: (ProfYear) -> Unit,
    onSubjectClick: (Subject) -> Unit = {},
    onCustomSectionClick: (CustomSection) -> Unit = {},
    onShortsClick: () -> Unit = {},
    onFlashcardsClick: () -> Unit,
    onPYQClick: () -> Unit,
    onMCQClick: () -> Unit = {},
    onProfileClick: () -> Unit,
    onPdfClick: (title: String, mediaUrl: String) -> Unit = { _, _ -> },
    onVideoClick: (title: String, mediaUrl: String, chapterName: String, subjectName: String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val userAvatar by MedRepository.userAvatar.collectAsState()
    val userPhotoUri by MedRepository.userPhotoUri.collectAsState()
    val userName by MedRepository.userName.collectAsState()

    val allContents by MedRepository.contents.collectAsState()
    val allFolders by MedRepository.folders.collectAsState()
    val allShorts by MedRepository.shorts.collectAsState()
    val allFlashcards by MedRepository.flashcards.collectAsState()
    val allPYQs by MedRepository.pyqs.collectAsState()
    val allMCQs by MedRepository.mcqs.collectAsState()
    val customSections by MedRepository.customSections.collectAsState()
    val mcqSections by MedRepository.mcqSections.collectAsState()

    // Clean case-insensitive query for robust search (small, capital, mixed)
    val cleanQuery = searchQuery.trim().lowercase(Locale.ROOT)
    val isSearching = cleanQuery.isNotBlank()
    val queryTokens = remember(cleanQuery) {
        cleanQuery.split(" ").filter { it.isNotBlank() }
    }

    // Helper for multi-token fuzzy/containment match
    fun matchesQuery(vararg texts: String): Boolean {
        if (!isSearching) return false
        val combined = texts.joinToString(" ").lowercase(Locale.ROOT)
        // Match full query or all individual words
        return combined.contains(cleanQuery) || queryTokens.all { combined.contains(it) }
    }

    // 1. Matched Subjects
    val matchedSubjects = remember(cleanQuery) {
        if (!isSearching) emptyList()
        else MedRepository.allSubjects.filter {
            matchesQuery(it.name, it.description, it.prof.title, it.prof.subtitle)
        }
    }

    // 2. Matched Study Contents (PDF Notes & Video Lectures)
    val matchedContents = remember(cleanQuery, allContents) {
        if (!isSearching) emptyList()
        else allContents.filter {
            matchesQuery(it.title, it.description, it.chapterName, it.folderName, it.subjectId, it.prof.title)
        }
    }

    // 3. Matched Folders & Chapters
    val matchedFolders = remember(cleanQuery, allFolders) {
        if (!isSearching) emptyList()
        else allFolders.filter {
            matchesQuery(it.name, it.subjectId, it.prof.code, it.prof.title)
        }
    }

    // 4. Matched Shorts
    val matchedShorts = remember(cleanQuery, allShorts) {
        if (!isSearching) emptyList()
        else allShorts.filter {
            matchesQuery(it.title, it.mnemonicTip, it.subjectName, it.doctorName, it.description)
        }
    }

    // 5. Matched Flashcards
    val matchedFlashcards = remember(cleanQuery, allFlashcards) {
        if (!isSearching) emptyList()
        else allFlashcards.filter {
            matchesQuery(it.question, it.answer, it.explanation, it.mnemonic ?: "", it.subjectId)
        }
    }

    // 6. Matched PYQs
    val matchedPYQs = remember(cleanQuery, allPYQs) {
        if (!isSearching) emptyList()
        else allPYQs.filter {
            matchesQuery(it.question, it.subjectId, it.examType, it.highYieldTip, it.year.toString())
        }
    }

    val totalMatches = matchedSubjects.size + matchedContents.size + matchedFolders.size + matchedShorts.size + matchedFlashcards.size + matchedPYQs.size

    val isSyncing by CloudSyncEngine.isSyncing.collectAsState()
    val isOnline by MedRepository.isOnline.collectAsState()
    val lastSyncStatus by CloudSyncEngine.lastSyncStatus.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    val currentTheme by MedRepository.currentTheme.collectAsState()

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = {
            val statusMessage = if (isOnline) "Online" else "Offline"
            android.widget.Toast.makeText(context, statusMessage, android.widget.Toast.LENGTH_SHORT).show()
            if (isOnline) {
                coroutineScope.launch {
                    MedRepository.triggerCloudSync(context)
                }
            }
        },
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        ) {
            // Top Header matching MedXMed Poster Design & Texture
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Logo
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.medxmed_theme_logo),
                                contentDescription = "MEDXMED Logo",
                                modifier = Modifier
                                    .padding(3.dp)
                                    .fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Center: MEDXMED
                        Text(
                            text = "MEDXMED",
                            style = androidx.compose.ui.text.TextStyle(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF0062FF), // Vibrant Electric Blue
                                        Color(0xFF8B2CF5), // Vivid Purple
                                        Color(0xFFFF1493)  // Hot Pink / Magenta
                                    )
                                ),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        )

                        // Right: Profile Circle Button with Gradient Ring
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0062FF), Color(0xFFFF1493))
                                    )
                                )
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onProfileClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!userPhotoUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = userPhotoUri,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = userAvatar,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }

                    // Bottom Signature Medical Gradient Accent Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
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
            }

            if (!isOnline) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val connected = MedRepository.checkNetworkOnline(context)
                                    if (connected) {
                                        CloudSyncEngine.syncFromCloud(context)
                                    } else {
                                        android.widget.Toast.makeText(context, "No connection. Please check internet.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar matching reference image (Soft rounded pill container)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            textStyle = com.example.ui.theme.medInputTextStyle(),
            colors = com.example.ui.theme.medTextFieldColors(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(22.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (isSearching) {
            // Live Search Results List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Results for \"$searchQuery\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (totalMatches > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "$totalMatches found",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalMatches > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (totalMatches == 0) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🔍", fontSize = 36.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No study material found",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Try searching by subject name (e.g. Anatomy, Pharma), chapter, or note title.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 1. Content Items (PDF Notes & Videos)
                if (matchedContents.isNotEmpty()) {
                    item {
                        Text(
                            text = "📄 PDF Notes & Videos (${matchedContents.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    items(matchedContents, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.type == StudyContentType.PDF) {
                                        onPdfClick(item.title, item.mediaUrl)
                                    } else if (item.mediaUrl.isNotBlank()) {
                                        val sub = MedRepository.allSubjects.firstOrNull { it.id == item.subjectId }
                                        onVideoClick(item.title, item.mediaUrl, item.chapterName, sub?.name ?: "")
                                    } else {
                                        val sub = MedRepository.allSubjects.firstOrNull { it.id == item.subjectId }
                                        if (sub != null) onSubjectClick(sub)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (item.type == StudyContentType.PDF) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(if (item.type == StudyContentType.PDF) "📄" else "🎥", fontSize = 20.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = item.prof.title,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = item.folderName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (item.description.isNotBlank()) {
                                        Text(
                                            text = item.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. Subjects Found
                if (matchedSubjects.isNotEmpty()) {
                    item {
                        Text(
                            text = "📚 MBBS Subjects (${matchedSubjects.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    items(matchedSubjects, key = { it.id }) { subject ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSubjectClick(subject) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(subject.emoji, fontSize = 22.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = subject.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEFF6FF)
                                        ) {
                                            Text(
                                                text = subject.prof.title,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = subject.description,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Subject",
                                    tint = Color(0xFF2563EB)
                                )
                            }
                        }
                    }
                }

                // 3. Folders & Chapters Found
                if (matchedFolders.isNotEmpty()) {
                    item {
                        Text(
                            text = "📁 Topic Folders & Units (${matchedFolders.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    items(matchedFolders, key = { it.id }) { folder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val sub = MedRepository.allSubjects.firstOrNull { it.id == folder.subjectId }
                                    if (sub != null) onSubjectClick(sub)
                                    else {
                                        val firstSub = MedRepository.allSubjects.firstOrNull()
                                        if (firstSub != null) onSubjectClick(firstSub)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("📂", fontSize = 20.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Subject ID: ${folder.subjectId} • ${folder.prof.title}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Folder",
                                    tint = Color(0xFF2563EB)
                                )
                            }
                        }
                    }
                }

                // 4. Shorts Found
                if (matchedShorts.isNotEmpty()) {
                    item {
                        Text(
                            text = "🎬 High-Yield Shorts (${matchedShorts.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    items(matchedShorts, key = { it.id }) { short ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShortsClick() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = short.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "${short.subjectName} • ${short.mnemonicTip}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Short", tint = Color(0xFFFF2E93))
                            }
                        }
                    }
                }

                // 5. PYQs Found
                if (matchedPYQs.isNotEmpty()) {
                    item {
                        Text(
                            text = "📝 Previous Year Questions (${matchedPYQs.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    items(matchedPYQs, key = { it.id }) { pyq ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPYQClick() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📋", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pyq.question,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pyq.examType} (${pyq.year}) • ${pyq.marks} Marks",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open PYQ", tint = Color(0xFF2563EB))
                            }
                        }
                    }
                }
            }
        } else {
            // Normal Home Screen View (Prof Grid + Smart Study Tools)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // "Select MBBS Prof" Section Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select MBBS Prof",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.2).sp,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "4 Years",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 2x2 Prof Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ProfCard(
                                prof = ProfYear.FIRST_PROF,
                                iconText = ProfYear.FIRST_PROF.iconEmoji,
                                gradient = ProfYear.FIRST_PROF.gradientBrush,
                                modifier = Modifier.weight(1f),
                                onClick = { onProfClick(ProfYear.FIRST_PROF) }
                            )

                            ProfCard(
                                prof = ProfYear.SECOND_PROF,
                                iconText = ProfYear.SECOND_PROF.iconEmoji,
                                gradient = ProfYear.SECOND_PROF.gradientBrush,
                                modifier = Modifier.weight(1f),
                                onClick = { onProfClick(ProfYear.SECOND_PROF) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ProfCard(
                                prof = ProfYear.THIRD_PROF_1,
                                iconText = ProfYear.THIRD_PROF_1.iconEmoji,
                                gradient = ProfYear.THIRD_PROF_1.gradientBrush,
                                modifier = Modifier.weight(1f),
                                onClick = { onProfClick(ProfYear.THIRD_PROF_1) }
                            )

                            ProfCard(
                                prof = ProfYear.THIRD_PROF_2,
                                iconText = ProfYear.THIRD_PROF_2.iconEmoji,
                                gradient = ProfYear.THIRD_PROF_2.gradientBrush,
                                modifier = Modifier.weight(1f),
                                onClick = { onProfClick(ProfYear.THIRD_PROF_2) }
                            )
                        }
                    }
                }

                // Custom / Special Programs & Big Sections
                if (customSections.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Special Programs & Modules",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "High-yield integrated sections, OSCE, and clinical cases",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Text(
                                        text = "${customSections.size} Sections",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Render Custom Sections as Large Cards
                    items(customSections, key = { it.id }) { sec ->
                        val sectionFoldersCount = allFolders.count { it.subjectId == sec.id }
                        val sectionContentsCount = allContents.count { it.subjectId == sec.id }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    spotColor = Color(0x33000000)
                                )
                                .clickable { onCustomSectionClick(sec) },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(sec.gradientBrush)
                                    .padding(16.dp)
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
                                                Text(text = sec.emoji, fontSize = 26.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sec.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp,
                                                letterSpacing = 0.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (sec.subtitle.isNotBlank()) {
                                                Text(
                                                    text = sec.subtitle,
                                                    color = Color(0xE6FFFFFF),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0x33FFFFFF),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 🎯 Large MCQ Section Cards / Practice Bank
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    spotColor = Color(0x33000000)
                                )
                                .clickable { onMCQClick() }
                                .testTag("home_mcq_section_card"),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF0D9488), Color(0xFF0F766E))
                                        )
                                    )
                                    .padding(16.dp)
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
                                                Text(text = "🎯", fontSize = 26.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "MCQ Practice Bank",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                letterSpacing = 0.3.sp
                                            )
                                            Text(
                                                text = "Chapter-wise High-Yield Practice Questions",
                                                color = Color(0xD9FFFFFF),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0x33FFFFFF),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open MCQ Bank",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                // "Self-Study & Resources" Section Header
                item {
                    Text(
                        text = "Self-Study & Resources",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.2).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp)
                    )
                }

                // Question Bank & Flashcards Resource Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Question Bank Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPYQClick() },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEF3C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "📝", fontSize = 22.sp)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "PYQ Bank",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 2. Flashcards Card (Restored on Homepage)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onFlashcardsClick() },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF3E8FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "📇", fontSize = 22.sp)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Flashcards",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
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

@Composable
fun ProfCard(
    prof: ProfYear,
    iconText: String,
    gradient: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .heightIn(min = 128.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp)
    ) {
        val currentTheme by MedRepository.currentTheme.collectAsState()
        val themedGradient = when(currentTheme) {
            4 -> Brush.verticalGradient(listOf(Color(0xFF262626), Color(0xFF171717))) // Midnight
            3 -> Brush.verticalGradient(listOf(Color(0xFF075985), Color(0xFF0C4A6E))) // Ocean
            else -> gradient
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themedGradient)
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = iconText, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = prof.title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = prof.subtitle,
                    color = Color(0xE6FFFFFF),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ResourceCard(
    title: String,
    subtitle: String,
    iconBg: Color,
    iconContent: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .heightIn(min = 120.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(22.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val currentTheme by MedRepository.currentTheme.collectAsState()
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (currentTheme == 4) Color(0xFF262626) else iconBg.copy(alpha = if (currentTheme == 1 || currentTheme == 3) 0.2f else 1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconContent, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
