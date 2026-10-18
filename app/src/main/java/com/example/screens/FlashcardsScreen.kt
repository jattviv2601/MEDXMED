package com.example.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MedRepository
import com.example.model.ContentFolder
import com.example.model.FlashcardItem
import com.example.model.ProfYear
import com.example.util.FlashcardUtils

data class FlashcardFolderSummary(
    val folderId: String,
    val folderName: String,
    val emoji: String,
    val prof: ProfYear,
    val subfolderNames: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    onBackClick: () -> Unit
) {
    val allFlashcards by MedRepository.flashcards.collectAsState()
    val allFolders by MedRepository.folders.collectAsState()

    // Filter flashcard folders
    val flashcardFolders = remember(allFolders, allFlashcards) {
        val directFolders = allFolders.filter {
            it.sectionType == "FLASHCARD" ||
            it.sectionType == "Flashcards" ||
            it.subjectId == "flashcard" ||
            it.name.contains("Flashcard", ignoreCase = true)
        }

        val existingFolderNames = directFolders.map { it.name.trim().lowercase() }.toSet()
        val extraFoldersFromCards = allFlashcards
            .map { it.folderName.trim() }
            .filter { it.isNotBlank() && !existingFolderNames.contains(it.lowercase()) }
            .distinct()
            .map { name ->
                ContentFolder(
                    id = "f_${name.hashCode()}",
                    prof = ProfYear.FIRST_PROF,
                    subjectId = "flashcard",
                    name = name,
                    emoji = "📇",
                    sectionType = "FLASHCARD"
                )
            }

        directFolders + extraFoldersFromCards
    }

    // Navigation State:
    // Level 1: selectedFolder == null (Root Folders list)
    // Level 2: selectedFolder != null && activeStudyDeckTitle == null (Chapters / Subfolders list)
    // Level 3: activeStudyDeckTitle != null (Interactive Flashcard deck player)
    var selectedFolder by remember { mutableStateOf<FlashcardFolderSummary?>(null) }
    var selectedSubfolderName by remember { mutableStateOf<String?>(null) }
    var selectedChapterName by remember { mutableStateOf<String?>(null) }
    var activeStudyDeckTitle by remember { mutableStateOf<String?>(null) }
    val isOnline by MedRepository.isOnline.collectAsState()

    // Handle back button hierarchically
    BackHandler {
        if (activeStudyDeckTitle != null) {
            activeStudyDeckTitle = null
            selectedChapterName = null
        } else if (selectedChapterName != null) {
            selectedChapterName = null
        } else if (selectedSubfolderName != null) {
            selectedSubfolderName = null
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else {
            onBackClick()
        }
    }

    // Build Folder Summaries
    val folderSummaries = remember(flashcardFolders, allFlashcards) {
        val list = mutableListOf<FlashcardFolderSummary>()
        
        flashcardFolders.forEach { folder ->
            val cardsInFolder = allFlashcards.filter { card ->
                card.folderId == folder.id || card.folderName.equals(folder.name, ignoreCase = true)
            }
            if (cardsInFolder.isNotEmpty()) {
                val subfolderNames = cardsInFolder
                    .map { if (it.subfolderName.isNotBlank()) it.subfolderName.trim() else it.chapterName.trim() }
                    .filter { it.isNotBlank() && !it.equals("General", ignoreCase = true) && !it.equals("Direct", ignoreCase = true) }
                    .distinct()

                list.add(
                    FlashcardFolderSummary(
                        folderId = folder.id,
                        folderName = folder.name,
                        emoji = folder.emoji.ifBlank { "📇" },
                        prof = folder.prof,
                        subfolderNames = subfolderNames
                    )
                )
            }
        }

        // Add General Cards if present
        val unassignedCards = allFlashcards.filter { card ->
            card.folderName.isBlank() || card.folderName.equals("General", ignoreCase = true)
        }
        if (unassignedCards.isNotEmpty() && list.none { it.folderName.equals("General Medical Cards", ignoreCase = true) }) {
            val subNames = unassignedCards
                .map { if (it.subfolderName.isNotBlank()) it.subfolderName.trim() else it.chapterName.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            list.add(
                0,
                FlashcardFolderSummary(
                    folderId = "f_general",
                    folderName = "General Medical Cards",
                    emoji = "🩺",
                    prof = ProfYear.FIRST_PROF,
                    subfolderNames = subNames
                )
            )
        }

        list
    }

    // Global Theme State
    val currentTheme by MedRepository.currentTheme.collectAsState()
    val themeBgColor = when (currentTheme) {
        1 -> Color(0xFF0F172A)
        2 -> Color(0xFFFEF9C3)
        else -> Color(0xFFF8FAFC)
    }

    // TopBar Title
    val currentTopTitle = when {
        activeStudyDeckTitle != null -> activeStudyDeckTitle!!
        selectedChapterName != null -> selectedChapterName!!
        selectedSubfolderName != null -> selectedSubfolderName!!
        selectedFolder != null -> selectedFolder!!.folderName
        else -> "Flashcards Bank"
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    modifier = Modifier.height(52.dp),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedFolder != null && activeStudyDeckTitle == null && selectedSubfolderName == null) {
                                Text(selectedFolder!!.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = currentTopTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    lineHeight = 19.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedFolder != null && (selectedSubfolderName != null || activeStudyDeckTitle != null)) {
                                    Text(
                                        text = "📁 ${selectedFolder!!.folderName}" + 
                                               (if (selectedSubfolderName != null) " ➔ 📂 $selectedSubfolderName" else "") +
                                               (if (selectedChapterName != null) " ➔ 📖 $selectedChapterName" else ""),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (activeStudyDeckTitle != null) {
                                    activeStudyDeckTitle = null
                                    selectedChapterName = null
                                } else if (selectedChapterName != null) {
                                    selectedChapterName = null
                                } else if (selectedSubfolderName != null) {
                                    selectedSubfolderName = null
                                } else if (selectedFolder != null) {
                                    selectedFolder = null
                                } else {
                                    onBackClick()
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
                    },
                    actions = {
                        IconButton(
                            onClick = { MedRepository.updateTheme((currentTheme + 1) % 3) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text(
                                text = when (currentTheme) {
                                    1 -> "✨"
                                    2 -> "📖"
                                    else -> "🌓"
                                },
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    windowInsets = WindowInsets(0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
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
        when {
            // =========================================================================
            // LEVEL 4: INTERACTIVE FLASHCARD STUDY DECK PLAYER
            // =========================================================================
            activeStudyDeckTitle != null -> {
                val currentFolder = selectedFolder
                val targetSubfolder = selectedSubfolderName
                val targetChapter = selectedChapterName

                val studyCards = remember(allFlashcards, currentFolder, targetSubfolder, targetChapter, activeStudyDeckTitle) {
                    val filtered = allFlashcards.filter { card ->
                        val folderMatches = if (currentFolder != null) {
                            card.folderId == currentFolder.folderId || card.folderName.equals(currentFolder.folderName, ignoreCase = true)
                        } else true

                        val subfolderMatches = if (!targetSubfolder.isNullOrBlank()) {
                            card.subfolderName.equals(targetSubfolder, ignoreCase = true) || card.chapterName.equals(targetSubfolder, ignoreCase = true)
                        } else true

                        val chapterMatches = if (!targetChapter.isNullOrBlank()) {
                            card.chapterName.equals(targetChapter, ignoreCase = true)
                        } else true

                        folderMatches && subfolderMatches && chapterMatches
                    }
                    FlashcardUtils.sortFlashcardsSerially(filtered)
                }

                InteractiveFlashcardDeckPlayer(
                    cards = studyCards,
                    deckTitle = activeStudyDeckTitle ?: "Deck",
                    modifier = Modifier.padding(paddingValues),
                    currentTheme = currentTheme
                )
            }

            // =========================================================================
            // LEVEL 3: CHAPTERS LIST VIEW INSIDE SELECTED SUBFOLDER
            // =========================================================================
            selectedFolder != null && selectedSubfolderName != null && selectedChapterName == null -> {
                val activeFolder = selectedFolder!!
                val subName = selectedSubfolderName!!
                val folderCards = remember(allFlashcards, activeFolder, subName) {
                    allFlashcards.filter {
                        (it.folderId == activeFolder.folderId || it.folderName.equals(activeFolder.folderName, ignoreCase = true)) &&
                        (it.subfolderName.equals(subName, ignoreCase = true) || it.chapterName.equals(subName, ignoreCase = true))
                    }
                }

                val chaptersList = remember(folderCards, subName) {
                    folderCards
                        .map { it.chapterName.trim() }
                        .filter { it.isNotBlank() && !it.equals("General", ignoreCase = true) }
                        .distinct()
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    if (chaptersList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Chapters",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        items(chaptersList) { chapName ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedChapterName = chapName
                                        activeStudyDeckTitle = chapName
                                    },
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
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFEFF6FF),
                                            modifier = Modifier.size(42.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("📖", fontSize = 20.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = chapName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Open",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // LEVEL 2: SUBFOLDERS LIST VIEW INSIDE SELECTED MAIN FOLDER
            // =========================================================================
            selectedFolder != null -> {
                val activeFolder = selectedFolder!!
                val folderCards = remember(allFlashcards, activeFolder) {
                    allFlashcards.filter {
                        it.folderId == activeFolder.folderId || it.folderName.equals(activeFolder.folderName, ignoreCase = true)
                    }
                }

                val subfoldersList = remember(folderCards) {
                    folderCards
                        .map { if (it.subfolderName.isNotBlank()) it.subfolderName.trim() else it.chapterName.trim() }
                        .filter { it.isNotBlank() && !it.equals("General", ignoreCase = true) && !it.equals("Direct", ignoreCase = true) }
                        .distinct()
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header removed
                    if (subfoldersList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Subfolders",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    if (subfoldersList.isEmpty() && folderCards.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📇", fontSize = 42.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No flashcards in this folder yet", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    items(subfoldersList) { subName ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSubfolderName = subName
                                    selectedChapterName = null
                                },
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
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(42.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📂", fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = subName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Open",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // LEVEL 1: ROOT FLASHCARD FOLDERS LIST
            // =========================================================================
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (folderSummaries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📇", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No flashcard folders uploaded yet.", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    items(folderSummaries, key = { it.folderId }) { folder ->
                        val cardsInDeck = remember(allFlashcards, folder) {
                            allFlashcards.filter {
                                it.folderId == folder.folderId || it.folderName.equals(folder.folderName, ignoreCase = true)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFolder = folder
                                    selectedSubfolderName = null
                                },
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
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(42.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(folder.emoji, fontSize = 20.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.folderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "${cardsInDeck.size} Cards",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Open",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveFlashcardDeckPlayer(
    cards: List<FlashcardItem>,
    deckTitle: String,
    modifier: Modifier = Modifier,
    currentTheme: Int = 0
) {
    if (cards.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📇", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No cards available in this selection", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF64748B))
            }
        }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember(currentIndex) { mutableStateOf(false) }
    var showHint by remember(currentIndex) { mutableStateOf(false) }

    // Horizontal Swipe Gesture Offset State
    var offsetX by remember(currentIndex) { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember(currentIndex) { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "swipe_offset"
    )

    val currentCard = cards[currentIndex.coerceIn(0, cards.size - 1)]

    // Flip animation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "card_flip"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Progress Bar & Count
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Card ${currentIndex + 1} of ${cards.size}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }

            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / cards.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF2563EB),
                trackColor = Color(0xFFE2E8F0)
            )
        }

        // Center Container: Compact Standard Sized Flashcard with Swipe & Tap Gestures
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isFlipped = !isFlipped
                },
            contentAlignment = Alignment.Center
        ) {
            val currentTranslationX = if (isDragging) offsetX else animatedOffsetX
            val currentRotationZ = (currentTranslationX / 25f).coerceIn(-12f, 12f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 400.dp)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        translationX = currentTranslationX
                        rotationZ = currentRotationZ
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            offsetX += delta
                            dragAccumulator += delta
                        },
                        onDragStarted = {
                            isDragging = true
                            dragAccumulator = 0f
                        },
                        onDragStopped = {
                            isDragging = false
                            if (dragAccumulator < -80f && currentIndex < cards.size - 1) {
                                currentIndex++
                            } else if (dragAccumulator > 80f && currentIndex > 0) {
                                currentIndex--
                            }
                            offsetX = 0f
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isFlipped = !isFlipped
                    }
            ) {
                if (rotation <= 90f) {
                    // ==================== FRONT OF CARD (QUESTION) ====================
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB).copy(0.3f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Header Row: ONLY Hint Bulb Button on Top Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentCard.hint.isNotBlank()) {
                                    IconButton(
                                        onClick = { showHint = !showHint },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("💡", fontSize = 22.sp)
                                    }
                                }
                            }

                            // Question Content & Image
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 12.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (currentCard.questionImageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = currentCard.questionImageUrl,
                                        contentDescription = "Question Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                val displayQuestion = remember(currentCard.question) {
                                    FlashcardUtils.cleanQuestionText(currentCard.question)
                                }

                                Text(
                                    text = displayQuestion,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTheme == 1) Color.White else Color(0xFF0F172A),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 24.sp
                                )

                                if (showHint && currentCard.hint.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFEF3C7),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = currentCard.hint,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(10.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ==================== BACK OF CARD (ANSWER) ====================
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF059669).copy(0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentCard.hint.isNotBlank()) {
                                    IconButton(
                                        onClick = { showHint = !showHint },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("💡", fontSize = 22.sp)
                                    }
                                }
                            }

                            // Answer Content & Image
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 12.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (currentCard.answerImageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = currentCard.answerImageUrl,
                                        contentDescription = "Answer Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Text(
                                    text = currentCard.answer,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 24.sp
                                )

                                if (currentCard.explanation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = currentCard.explanation,
                                        fontSize = 13.sp,
                                        color = Color(0xFF475569),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (showHint && currentCard.hint.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFEF3C7),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = currentCard.hint,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(10.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Primary Show Answer / Show Question Action Button Layout (Outside Card)
        Button(
            onClick = { isFlipped = !isFlipped },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFlipped) Color(0xFF059669) else Color(0xFF2563EB)
            )
        ) {
            Icon(
                imageVector = if (isFlipped) Icons.Default.HelpOutline else Icons.Default.Visibility,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isFlipped) "Show Question 🔄" else "Show Answer 💡",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Controls (Previous & Next)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (currentIndex > 0) currentIndex--
                },
                enabled = currentIndex > 0,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Previous", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    if (currentIndex < cards.size - 1) currentIndex++
                },
                enabled = currentIndex < cards.size - 1,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text("Next", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}
