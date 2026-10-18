package com.example.model

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

enum class ProfYear(
    val title: String,
    val subtitle: String,
    val code: String,
    val iconEmoji: String,
    val gradientColors: List<Color>
) {
    FIRST_PROF(
        title = "1ST PROF",
        subtitle = "PRE-CLINICAL (1ST YEAR)",
        code = "prof1",
        iconEmoji = "🦴",
        gradientColors = listOf(Color(0xFF0284C7), Color(0xFF0369A1))
    ),
    SECOND_PROF(
        title = "2ND PROF",
        subtitle = "PARA-CLINICAL (2ND YEAR)",
        code = "prof2",
        iconEmoji = "💊",
        gradientColors = listOf(Color(0xFF15803D), Color(0xFF166534))
    ),
    THIRD_PROF_1(
        title = "3RD PROF I",
        subtitle = "CLINICAL PART 1 (3RD YEAR)",
        code = "prof3_1",
        iconEmoji = "🩺",
        gradientColors = listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))
    ),
    THIRD_PROF_2(
        title = "3RD PROF II",
        subtitle = "FINAL CLINICAL",
        code = "prof3_2",
        iconEmoji = "🫀",
        gradientColors = listOf(Color(0xFFE11D48), Color(0xFF9F1239))
    );

    val gradientBrush: Brush
        get() = Brush.verticalGradient(gradientColors)
}

data class Subject(
    val id: String,
    val name: String,
    val emoji: String,
    val prof: ProfYear,
    val description: String,
    val totalChapters: Int,
    val highYieldTopicCount: Int
)

@Serializable
data class CustomSection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val emoji: String = "📚",
    val colorHex1: String = "#0284C7",
    val colorHex2: String = "#0369A1",
    val description: String = "",
    val orderIndex: Int = 0
) {
    val gradientColors: List<Color>
        get() = try {
            listOf(
                Color(android.graphics.Color.parseColor(colorHex1)),
                Color(android.graphics.Color.parseColor(colorHex2))
            )
        } catch (e: Exception) {
            listOf(Color(0xFF0284C7), Color(0xFF0369A1))
        }

    val gradientBrush: Brush
        get() = Brush.verticalGradient(gradientColors)
}

enum class StudyContentType {
    VIDEO,
    PDF
}

data class ContentFolder(
    val id: String,
    val prof: ProfYear,
    val subjectId: String,
    val name: String,
    val emoji: String = "📁",
    val sectionType: String = "ALL" // "PDF", "VIDEO", or "ALL"
)

data class StudyContentItem(
    val id: String,
    val prof: ProfYear,
    val subjectId: String,
    val folderId: String,
    val folderName: String,
    val chapterName: String = "",
    val type: StudyContentType,
    val title: String,
    val description: String = "",
    val mediaUrl: String,
    val cloudflareKey: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isPyq: Boolean = false
)

data class LectureItem(
    val id: String,
    val subjectId: String,
    val prof: ProfYear,
    val title: String,
    val chapter: String,
    val faculty: String,
    val duration: String,
    val videoUrl: String,
    val isWatched: Boolean = false,
    val tags: List<String> = listOf("High Yield", "NMC")
)

data class NoteItem(
    val id: String,
    val subjectId: String,
    val prof: ProfYear,
    val title: String,
    val chapter: String,
    val pagesCount: Int,
    val readMinutes: Int,
    val highYieldSummary: String,
    val pdfMockUrl: String = "",
    val isCachedOffline: Boolean = true
)

@Serializable
enum class QuestionType {
    TEXT,
    MCQ,
    TRUE_FALSE,
    MATCH_THE_COLUMN,
    ASSERTION_REASON
}

@Serializable
data class FlashcardItem(
    val id: String,
    val subjectId: String,
    val prof: ProfYear,
    val folderId: String = "",
    val folderName: String = "",
    val subfolderName: String = "",
    val chapterName: String = "",
    val questionType: QuestionType = QuestionType.TEXT,
    val question: String,
    val questionOptions: List<String> = emptyList(), // For MCQ / Match
    val questionImageUrl: String = "",
    val answer: String, // Can be option index, or full text
    val answerImageUrl: String = "",
    val explanation: String = "", // Added detailed explanation
    val hint: String = "",
    val tags: String = "High-Yield",
    val mnemonic: String? = null,
    val clinicalPearl: String? = null,
    val isMastered: Boolean = false,
    val isSavedOffline: Boolean = true,
    val difficulty: String = "Medium"
)

data class PYQItem(
    val id: String,
    val subjectId: String,
    val prof: ProfYear,
    val year: Int,
    val examType: String,
    val question: String,
    val marks: Int,
    val answerKey: String,
    val highYieldTip: String
)

data class ShortItem(
    val id: String,
    val title: String,
    val description: String = "",
    val youtubeUrl: String = "",
    val youtubeVideoId: String = "",
    val thumbnailUrl: String = "",
    val cloudflareKey: String = "",
    val subjectName: String = "General",
    val emoji: String = "🩺",
    val prof: ProfYear = ProfYear.FIRST_PROF,
    val doctorName: String = "Dr. Viv",
    val durationSec: Int = 60,
    val likesCount: Int = 142,
    val mnemonicTip: String = "",
    val audioTitle: String = "MedXMed Rapid Mnemonics 🩺",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class MCQOption(
    val id: String = "",
    val text: String,
    val imageUrl: String = ""
)

@Serializable
data class MCQSection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val emoji: String = "🎯",
    val colorHex1: String = "#0D9488",
    val colorHex2: String = "#0F766E",
    val prof: ProfYear = ProfYear.FIRST_PROF,
    val orderIndex: Int = 0
) {
    val gradientColors: List<Color>
        get() = try {
            listOf(
                Color(android.graphics.Color.parseColor(colorHex1)),
                Color(android.graphics.Color.parseColor(colorHex2))
            )
        } catch (e: Exception) {
            listOf(Color(0xFF0D9488), Color(0xFF0F766E))
        }

    val gradientBrush: Brush
        get() = Brush.horizontalGradient(gradientColors)
}

@Serializable
data class MCQItem(
    val id: String,
    val prof: ProfYear = ProfYear.FIRST_PROF,
    val subjectId: String = "mcq",
    val sectionId: String = "",
    val sectionName: String = "",
    val folderId: String = "",
    val folderName: String = "",
    val subfolderName: String = "",
    val chapterName: String = "",
    val question: String,
    val questionImageUrl: String = "",
    val options: List<MCQOption> = emptyList(),
    val correctOptionIndex: Int = 0,
    val explanation: String = "",
    val explanationImageUrl: String = "",
    val difficulty: String = "Medium",
    val tags: String = "High-Yield",
    val createdAt: Long = System.currentTimeMillis(),
    val questionType: String = "MCQ", // "MCQ", "ASSERTION_REASON", "MATCH_THE_COLUMN", "STATEMENT_BASED", "FLOWCHART", "TRUE_FALSE"
    val assertion: String = "",
    val reason: String = "",
    val statements: List<String> = emptyList(),
    val matchColumnA: List<String> = emptyList(),
    val matchColumnB: List<String> = emptyList(),
    val flowchartSteps: List<String> = emptyList()
)

@Serializable
data class MCQBookmark(
    val id: String,
    val question: String,
    val options: List<MCQOption> = emptyList(),
    val correctOptionIndex: Int = 0,
    val userSelectedOptionIndex: Int? = null,
    val explanation: String = "",
    val explanationImageUrl: String = "",
    val chapterName: String = "",
    val folderName: String = "",
    val tags: String = "",
    val bookmarkedAt: Long = System.currentTimeMillis(),
    val questionType: String = "MCQ",
    val assertion: String = "",
    val reason: String = "",
    val statements: List<String> = emptyList(),
    val matchColumnA: List<String> = emptyList(),
    val matchColumnB: List<String> = emptyList(),
    val flowchartSteps: List<String> = emptyList()
)

@Serializable
data class MCQQuizHistory(
    val id: String,
    val chapterName: String,
    val folderName: String = "",
    val totalQuestions: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val skippedCount: Int,
    val scorePercentage: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val totalMarks: Float = 0f,
    val userAnswers: Map<String, Int> = emptyMap()
)

