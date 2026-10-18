package com.example.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MedRepository
import com.example.model.MCQItem
import com.example.model.MCQQuizHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCQPlayerScreen(
    folderId: String,
    chapterName: String,
    subfolderName: String = "",
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val allMCQs by MedRepository.mcqs.collectAsState()
    val bookmarkedMCQs by MedRepository.bookmarkedMCQs.collectAsState()

    val chapterQuestions = remember(allMCQs, folderId, chapterName, subfolderName) {
        val folder = MedRepository.folders.value.firstOrNull { it.id == folderId }
        val folderName = (folder?.name ?: folderId).trim()
        allMCQs.filter {
            val matchesFolder = it.folderId == folderId || 
                it.folderName.trim().equals(folderId.trim(), ignoreCase = true) ||
                (folderName.isNotBlank() && it.folderName.trim().equals(folderName, ignoreCase = true))
            val matchesChapter = chapterName.isBlank() || 
                it.chapterName.trim().equals(chapterName.trim(), ignoreCase = true) ||
                (chapterName.trim().equals("General Practice", ignoreCase = true) && it.chapterName.trim().isBlank()) ||
                (chapterName.trim().equals("General Chapter", ignoreCase = true) && it.chapterName.trim().isBlank())
            val matchesSubfolder = subfolderName.isBlank() || 
                it.subfolderName.trim().equals(subfolderName.trim(), ignoreCase = true) ||
                (subfolderName.trim().equals("General", ignoreCase = true) && it.subfolderName.trim().isBlank())
            matchesFolder && matchesChapter && matchesSubfolder
        }
    }

    val totalQuestions = chapterQuestions.size
    var currentIndex by remember { mutableIntStateOf(0) }
    // Map of question ID -> selected option index
    val userAnswers = remember { mutableStateMapOf<String, Int>() }

    var isReviewMode by remember { mutableStateOf(false) }
    var showConfirmSubmitDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    val currentQuestion = chapterQuestions.getOrNull(currentIndex)

    // Calculate score with +1 for correct, -0.25 (-1/4) for incorrect, 0 for unattempted
    val correctCount = remember(userAnswers.toMap(), chapterQuestions) {
        chapterQuestions.count { q -> userAnswers[q.id] == q.correctOptionIndex }
    }
    val answeredCount = userAnswers.size
    val incorrectCount = remember(userAnswers.toMap(), chapterQuestions) {
        chapterQuestions.count { q ->
            val ans = userAnswers[q.id]
            ans != null && ans != q.correctOptionIndex
        }
    }
    val skippedCount = totalQuestions - answeredCount
    val totalMarksScored = (correctCount * 1.0f) - (incorrectCount * 0.25f)
    val formattedMarks = if (totalMarksScored % 1f == 0f) {
        "${totalMarksScored.toInt()}"
    } else {
        String.format(Locale.US, "%.2f", totalMarksScored)
    }
    val scorePercentage = if (totalQuestions > 0) {
        ((correctCount.toFloat() / totalQuestions) * 100).toInt()
    } else {
        0
    }

    val isCurrentBookmarked = currentQuestion != null && bookmarkedMCQs.any { it.id == currentQuestion.id }

    fun submitQuizNow() {
        val historyRecord = MCQQuizHistory(
            id = UUID.randomUUID().toString(),
            chapterName = chapterName.ifBlank { "MCQ Practice" },
            folderName = folderId,
            totalQuestions = totalQuestions,
            correctCount = correctCount,
            incorrectCount = incorrectCount,
            skippedCount = skippedCount,
            scorePercentage = scorePercentage,
            timestamp = System.currentTimeMillis(),
            totalMarks = totalMarksScored,
            userAnswers = userAnswers.toMap()
        )
        MedRepository.saveQuizHistory(historyRecord)
        isReviewMode = true
        showConfirmSubmitDialog = false
        Toast.makeText(context, "Test Submitted! Saved to History 📊", Toast.LENGTH_SHORT).show()
    }

    fun restartQuiz() {
        userAnswers.clear()
        currentIndex = 0
        isReviewMode = false
    }

    BackHandler {
        if (isReviewMode) {
            onBackClick()
        } else if (userAnswers.isNotEmpty()) {
            showExitConfirmDialog = true
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isReviewMode) "Test Review & Solutions" else (chapterName.ifBlank { "MCQ Practice" }),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        if (totalQuestions > 0) {
                            Text(
                                text = if (isReviewMode) {
                                    "$totalQuestions Questions • Marks: $formattedMarks / $totalQuestions"
                                } else {
                                    "Question ${currentIndex + 1} of $totalQuestions"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isReviewMode) {
                                onBackClick()
                            } else if (userAnswers.isNotEmpty()) {
                                showExitConfirmDialog = true
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.testTag("mcq_player_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Quiz")
                    }
                },
                actions = {
                    if (isReviewMode) {
                        // In Review Mode: Retest button in top bar
                        OutlinedButton(
                            onClick = { restartQuiz() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp).testTag("retest_top_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retest", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // In Solving Mode: Exactly ONE bookmark icon in top bar
                        if (currentQuestion != null) {
                            IconButton(
                                onClick = {
                                    MedRepository.toggleBookmark(currentQuestion, userAnswers[currentQuestion.id])
                                    val isNow = !isCurrentBookmarked
                                    Toast.makeText(
                                        context,
                                        if (isNow) "Question bookmarked! 🔖" else "Bookmark removed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.testTag("mcq_bookmark_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isCurrentBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Submit Button at the top at any time
                        if (totalQuestions > 0) {
                            Button(
                                onClick = {
                                    if (skippedCount > 0) {
                                        showConfirmSubmitDialog = true
                                    } else {
                                        submitQuizNow()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("mcq_top_submit_button")
                            ) {
                                Text("Submit", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isReviewMode && totalQuestions > 0) {
                Surface(
                    tonalElevation = 10.dp,
                    shadowElevation = 14.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("mcq_prev_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Previous", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Button(
                            onClick = { if (currentIndex < totalQuestions - 1) currentIndex++ },
                            enabled = currentIndex < totalQuestions - 1,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("mcq_next_button")
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (totalQuestions == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("🎯", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No questions in this chapter yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Add questions from Admin Center > MCQs", color = Color(0xFF64748B), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) { Text("Go Back") }
                }
            }
        } else if (isReviewMode) {
            // FULL VERTICAL SCROLL REVIEW SCREEN AFTER SUBMISSION
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Scorecard Summary Card with Pie Chart
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Quiz Result",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = chapterName.ifBlank { "MCQ Practice" },
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Surface(
                                    color = if (scorePercentage >= 60) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (scorePercentage >= 60) "Passed" else "Review Needed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (scorePercentage >= 60) Color(0xFF15803D) else Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Pie Chart
                            MCQResultsPieChart(
                                correct = correctCount,
                                incorrect = incorrectCount,
                                skipped = skippedCount,
                                modifier = Modifier.size(130.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Total Marks Scored Out Of Total Marks (+1 for correct, -0.25 for incorrect)
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Total Marks",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$formattedMarks / $totalQuestions",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1E40AF)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "(+1 mark per correct • -0.25 mark per incorrect)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Breakdown Stats Grid
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Correct", fontSize = 11.sp, color = Color(0xFF64748B))
                                        Text("✓ $correctCount", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), fontSize = 15.sp)
                                        Text("+${correctCount}", fontSize = 10.5.sp, color = Color(0xFF16A34A))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Incorrect", fontSize = 11.sp, color = Color(0xFF64748B))
                                        Text("✗ $incorrectCount", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), fontSize = 15.sp)
                                        val negMarks = String.format(Locale.US, "%.2f", incorrectCount * 0.25f)
                                        Text("-$negMarks", fontSize = 10.5.sp, color = Color(0xFFDC2626))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Unattempted", fontSize = 11.sp, color = Color(0xFF64748B))
                                        Text("• $skippedCount", fontWeight = FontWeight.Bold, color = Color(0xFF64748B), fontSize = 15.sp)
                                        Text("0", fontSize = 10.5.sp, color = Color(0xFF64748B))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Retest & Back buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { restartQuiz() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retest", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                }

                                Button(
                                    onClick = onBackClick,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "✓ Saved to Profile History with Date & Time",
                                fontSize = 11.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Header for questions list
                item {
                    Text(
                        text = "Questions & Detailed Solutions ($totalQuestions Questions)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Vertically scrolling list of all questions with answers & solutions
                itemsIndexed(chapterQuestions) { index, q ->
                    val userPick = userAnswers[q.id]
                    val isCorrect = userPick == q.correctOptionIndex
                    val isSkipped = userPick == null
                    val isBookmarked = bookmarkedMCQs.any { it.id == q.id }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isCorrect -> Color(0xFF86EFAC)
                                isSkipped -> Color(0xFFE2E8F0)
                                else -> Color(0xFFFCA5A5)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Question Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Q ${index + 1}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Status Badge
                                    Surface(
                                        color = when {
                                            isCorrect -> Color(0xFFDCFCE7)
                                            isSkipped -> Color(0xFFF1F5F9)
                                            else -> Color(0xFFFEE2E2)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isCorrect -> "✓ Correct (+1)"
                                                isSkipped -> "○ Unattempted (0)"
                                                else -> "✗ Incorrect (-0.25)"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = when {
                                                isCorrect -> Color(0xFF15803D)
                                                isSkipped -> Color(0xFF64748B)
                                                else -> Color(0xFFB91C1C)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        MedRepository.toggleBookmark(q, userPick)
                                        val isNow = !isBookmarked
                                        Toast.makeText(
                                            context,
                                            if (isNow) "Question bookmarked! 🔖" else "Bookmark removed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Question Content
                            val parsedContent = remember(q.id) { parseMCQContentFromItem(q) }
                            TextbookQuestionContent(content = parsedContent, isDarkTheme = false)

                            if (q.questionImageUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { fullscreenImageUrl = q.questionImageUrl }
                                ) {
                                    AsyncImage(
                                        model = q.questionImageUrl,
                                        contentDescription = "Question Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ZoomIn,
                                                contentDescription = "Zoom",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Full Screen", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Options List with clear correct / user selected indicators
                            val letters = listOf("A", "B", "C", "D", "E", "F")
                            q.options.forEachIndexed { optIndex, option ->
                                val isOptionCorrect = optIndex == q.correctOptionIndex
                                val isUserChoice = optIndex == userPick

                                val (optBg, optBorder, optText) = when {
                                    isOptionCorrect -> Triple(Color(0xFFDCFCE7), Color(0xFF22C55E), Color(0xFF15803D))
                                    isUserChoice && !isOptionCorrect -> Triple(Color(0xFFFEE2E2), Color(0xFFEF4444), Color(0xFFB91C1C))
                                    else -> Triple(Color.White, Color(0xFFE2E8F0), Color(0xFF475569))
                                }

                                Surface(
                                    color = optBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(if (isOptionCorrect || isUserChoice) 2.dp else 1.dp, optBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = when {
                                                isOptionCorrect -> Color(0xFF16A34A)
                                                isUserChoice -> Color(0xFFDC2626)
                                                else -> Color(0xFFF1F5F9)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = letters.getOrNull(optIndex) ?: "${optIndex + 1}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isOptionCorrect || isUserChoice) Color.White else Color(0xFF475569)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.text,
                                                fontSize = 14.sp,
                                                fontWeight = if (isOptionCorrect || isUserChoice) FontWeight.Bold else FontWeight.Normal,
                                                color = optText
                                            )
                                        }

                                        // Badge tag for this option
                                        if (isOptionCorrect) {
                                            Surface(
                                                color = Color(0xFF16A34A),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isUserChoice) "✓ Your Answer (Correct)" else "✓ Correct Answer",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else if (isUserChoice) {
                                            Surface(
                                                color = Color(0xFFDC2626),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "✗ Your Answer",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Explanation & Solution Card
                            if (q.explanation.isNotBlank() || q.explanationImageUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = Color(0xFFF0FDF4),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("💡", fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Explanation & Solution",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = Color(0xFF15803D)
                                            )
                                        }

                                        val correctOptLetter = letters.getOrNull(q.correctOptionIndex) ?: "${q.correctOptionIndex + 1}"
                                        val correctOptText = q.options.getOrNull(q.correctOptionIndex)?.text ?: ""
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Correct Answer: Option $correctOptLetter ($correctOptText)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534)
                                        )

                                        if (q.explanation.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = q.explanation,
                                                fontSize = 13.5.sp,
                                                color = Color(0xFF1F2937),
                                                lineHeight = 19.sp
                                            )
                                        }

                                        if (q.explanationImageUrl.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 220.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { fullscreenImageUrl = q.explanationImageUrl }
                                            ) {
                                                AsyncImage(
                                                    model = q.explanationImageUrl,
                                                    contentDescription = "Explanation Image",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ZoomIn,
                                                            contentDescription = "Zoom",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Full Screen", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        } else if (currentQuestion != null) {
            // SOLVING MODE (ONE QUESTION AT A TIME)
            val q = currentQuestion
            val selectedOption = userAnswers[q.id]

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Progress Indicator
                item {
                    LinearProgressIndicator(
                        progress = { (currentIndex + 1).toFloat() / totalQuestions.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF2563EB),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }

                // Question Card (NO extra yellow topics, NO duplicate bookmark button)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top of Question Card: Only shows Q number (no extra topic tags or duplicate bookmark)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Q ${currentIndex + 1} of $totalQuestions",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1D4ED8),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                if (selectedOption != null) {
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Attempted",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF15803D),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Textbook Question Content (Assertion-Reason, Match Column, Flowcharts, etc.)
                            val parsedContent = remember(q.id) { parseMCQContentFromItem(q) }
                            TextbookQuestionContent(content = parsedContent, isDarkTheme = false)

                            // Question Image if present
                            if (q.questionImageUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { fullscreenImageUrl = q.questionImageUrl }
                                ) {
                                    AsyncImage(
                                        model = q.questionImageUrl,
                                        contentDescription = "Question Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ZoomIn,
                                                contentDescription = "Zoom",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Full Screen", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Options List
                val letters = listOf("A", "B", "C", "D", "E", "F")
                itemsIndexed(q.options) { optIndex, option ->
                    val isSelected = selectedOption == optIndex

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                userAnswers[q.id] = optIndex
                            }
                            .testTag("mcq_option_$optIndex"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                        ),
                        elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Option Letter Circle
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letters.getOrNull(optIndex) ?: "${optIndex + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else Color(0xFF475569)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.text,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF1E40AF) else Color(0xFF1E293B)
                                )
                                if (option.imageUrl.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { fullscreenImageUrl = option.imageUrl }
                                    ) {
                                        AsyncImage(
                                            model = option.imageUrl,
                                            contentDescription = "Option Image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Submit Confirmation Dialog
    if (showConfirmSubmitDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmSubmitDialog = false },
            title = {
                Text("Submit Quiz?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "You have answered $answeredCount of $totalQuestions questions ($skippedCount unattempted).",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to submit now? You will be able to review all questions and solutions.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitQuizNow() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSubmitDialog = false }) {
                    Text("Continue Test")
                }
            }
        )
    }

    // Exit Confirmation Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Exit Quiz?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Your current test session will be lost if you leave without submitting. Do you want to exit?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Exit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Stay")
                }
            }
        )
    }

    // Fullscreen Image Popup Dialog (No extra text, just image in full screen)
    if (fullscreenImageUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenImageUrl = null }
            ) {
                AsyncImage(
                    model = fullscreenImageUrl,
                    contentDescription = "Fullscreen Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun MCQResultsPieChart(
    correct: Int,
    incorrect: Int,
    skipped: Int,
    modifier: Modifier = Modifier
) {
    val total = (correct + incorrect + skipped).coerceAtLeast(1)
    val correctAngle = (correct.toFloat() / total.toFloat()) * 360f
    val incorrectAngle = (incorrect.toFloat() / total.toFloat()) * 360f
    val skippedAngle = (skipped.toFloat() / total.toFloat()) * 360f

    val green = Color(0xFF22C55E)
    val red = Color(0xFFEF4444)
    val gray = Color(0xFFCBD5E1)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var currentAngle = -90f

            if (correctAngle > 0f) {
                drawArc(
                    color = green,
                    startAngle = currentAngle,
                    sweepAngle = correctAngle,
                    useCenter = true
                )
                currentAngle += correctAngle
            }

            if (incorrectAngle > 0f) {
                drawArc(
                    color = red,
                    startAngle = currentAngle,
                    sweepAngle = incorrectAngle,
                    useCenter = true
                )
                currentAngle += incorrectAngle
            }

            if (skippedAngle > 0f) {
                drawArc(
                    color = gray,
                    startAngle = currentAngle,
                    sweepAngle = skippedAngle,
                    useCenter = true
                )
            }

            // Donut hole in the center
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 3.4f
            )
        }

        val pct = (correct * 100) / total
        Text(
            text = "$pct%",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = Color(0xFF0F172A)
        )
    }
}
