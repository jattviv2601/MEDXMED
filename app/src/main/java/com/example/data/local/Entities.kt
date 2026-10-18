package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.*

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val profCode: String,
    val title: String,
    val chapter: String,
    val pagesCount: Int,
    val readMinutes: Int,
    val highYieldSummary: String,
    val pdfMockUrl: String = "",
    val isCachedOffline: Boolean = true,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): NoteItem {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        return NoteItem(
            id = id,
            subjectId = subjectId,
            prof = prof,
            title = title,
            chapter = chapter,
            pagesCount = pagesCount,
            readMinutes = readMinutes,
            highYieldSummary = highYieldSummary,
            pdfMockUrl = pdfMockUrl,
            isCachedOffline = isCachedOffline
        )
    }

    companion object {
        fun fromDomain(domain: NoteItem): NoteEntity {
            return NoteEntity(
                id = domain.id,
                subjectId = domain.subjectId,
                profCode = domain.prof.code,
                title = domain.title,
                chapter = domain.chapter,
                pagesCount = domain.pagesCount,
                readMinutes = domain.readMinutes,
                highYieldSummary = domain.highYieldSummary,
                pdfMockUrl = domain.pdfMockUrl,
                isCachedOffline = domain.isCachedOffline
            )
        }
    }
}

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val profCode: String,
    val folderId: String = "",
    val folderName: String = "",
    val subfolderName: String = "",
    val chapterName: String = "",
    val questionType: String,
    val question: String,
    val questionOptions: String = "",
    val questionImageUrl: String = "",
    val answer: String,
    val answerImageUrl: String = "",
    val explanation: String = "",
    val hint: String = "",
    val tags: String = "High-Yield",
    val mnemonic: String? = null,
    val clinicalPearl: String? = null,
    val isMastered: Boolean = false,
    val isSavedOffline: Boolean = true,
    val difficulty: String = "Medium",
    val reviewCount: Int = 0,
    val lastReviewedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): FlashcardItem {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        return FlashcardItem(
            id = id,
            subjectId = subjectId,
            prof = prof,
            folderId = folderId,
            folderName = folderName,
            subfolderName = subfolderName,
            chapterName = chapterName,
            questionType = QuestionType.valueOf(questionType),
            question = question,
            questionOptions = questionOptions.split("|"),
            questionImageUrl = questionImageUrl,
            answer = answer,
            answerImageUrl = answerImageUrl,
            explanation = explanation,
            hint = hint,
            tags = tags,
            mnemonic = mnemonic,
            clinicalPearl = clinicalPearl,
            isMastered = isMastered,
            isSavedOffline = isSavedOffline,
            difficulty = difficulty
        )
    }

    companion object {
        fun fromDomain(domain: FlashcardItem): FlashcardEntity {
            return FlashcardEntity(
                id = domain.id,
                subjectId = domain.subjectId,
                profCode = domain.prof.code,
                folderId = domain.folderId,
                folderName = domain.folderName,
                subfolderName = domain.subfolderName,
                chapterName = domain.chapterName,
                questionType = domain.questionType.name,
                question = domain.question,
                questionOptions = domain.questionOptions.joinToString("|"),
                questionImageUrl = domain.questionImageUrl,
                answer = domain.answer,
                answerImageUrl = domain.answerImageUrl,
                explanation = domain.explanation,
                hint = domain.hint,
                tags = domain.tags,
                mnemonic = domain.mnemonic,
                clinicalPearl = domain.clinicalPearl,
                isMastered = domain.isMastered,
                isSavedOffline = domain.isSavedOffline,
                difficulty = domain.difficulty
            )
        }
    }
}

@Entity(tableName = "pyqs")
data class PYQEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val profCode: String,
    val year: Int,
    val examType: String,
    val question: String,
    val marks: Int,
    val answerKey: String,
    val highYieldTip: String
) {
    fun toDomain(): PYQItem {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        return PYQItem(
            id = id,
            subjectId = subjectId,
            prof = prof,
            year = year,
            examType = examType,
            question = question,
            marks = marks,
            answerKey = answerKey,
            highYieldTip = highYieldTip
        )
    }

    companion object {
        fun fromDomain(domain: PYQItem): PYQEntity {
            return PYQEntity(
                id = domain.id,
                subjectId = domain.subjectId,
                profCode = domain.prof.code,
                year = domain.year,
                examType = domain.examType,
                question = domain.question,
                marks = domain.marks,
                answerKey = domain.answerKey,
                highYieldTip = domain.highYieldTip
            )
        }
    }
}

@Entity(tableName = "lectures")
data class LectureEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val profCode: String,
    val title: String,
    val chapter: String,
    val faculty: String,
    val duration: String,
    val videoUrl: String,
    val isWatched: Boolean = false
) {
    fun toDomain(): LectureItem {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        return LectureItem(
            id = id,
            subjectId = subjectId,
            prof = prof,
            title = title,
            chapter = chapter,
            faculty = faculty,
            duration = duration,
            videoUrl = videoUrl,
            isWatched = isWatched
        )
    }

    companion object {
        fun fromDomain(domain: LectureItem): LectureEntity {
            return LectureEntity(
                id = domain.id,
                subjectId = domain.subjectId,
                profCode = domain.prof.code,
                title = domain.title,
                chapter = domain.chapter,
                faculty = domain.faculty,
                duration = domain.duration,
                videoUrl = domain.videoUrl,
                isWatched = domain.isWatched
            )
        }
    }
}

@Entity(tableName = "shorts")
data class ShortEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val youtubeUrl: String = "",
    val youtubeVideoId: String = "",
    val thumbnailUrl: String = "",
    val cloudflareKey: String = "",
    val subjectName: String,
    val emoji: String,
    val profCode: String,
    val doctorName: String,
    val durationSec: Int,
    val likesCount: Int,
    val mnemonicTip: String,
    val audioTitle: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ShortItem {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        return ShortItem(
            id = id,
            title = title,
            description = description,
            youtubeUrl = youtubeUrl,
            youtubeVideoId = youtubeVideoId,
            thumbnailUrl = thumbnailUrl,
            cloudflareKey = cloudflareKey,
            subjectName = subjectName,
            emoji = emoji,
            prof = prof,
            doctorName = doctorName,
            durationSec = durationSec,
            likesCount = likesCount,
            mnemonicTip = mnemonicTip,
            audioTitle = audioTitle,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(domain: ShortItem): ShortEntity {
            return ShortEntity(
                id = domain.id,
                title = domain.title,
                description = domain.description,
                youtubeUrl = domain.youtubeUrl,
                youtubeVideoId = domain.youtubeVideoId,
                thumbnailUrl = domain.thumbnailUrl,
                cloudflareKey = domain.cloudflareKey,
                subjectName = domain.subjectName,
                emoji = domain.emoji,
                profCode = domain.prof.code,
                doctorName = domain.doctorName,
                durationSec = domain.durationSec,
                likesCount = domain.likesCount,
                mnemonicTip = domain.mnemonicTip,
                audioTitle = domain.audioTitle,
                createdAt = domain.createdAt
            )
        }
    }
}

@Entity(tableName = "folders")
data class ContentFolderEntity(
    @PrimaryKey val id: String,
    val profCode: String,
    val subjectId: String,
    val name: String,
    val emoji: String = "📁",
    val sectionType: String = "ALL"
) {
    fun toDomain(): ContentFolder {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        return ContentFolder(
            id = id,
            prof = prof,
            subjectId = subjectId,
            name = name,
            emoji = emoji,
            sectionType = sectionType
        )
    }

    companion object {
        fun fromDomain(domain: ContentFolder): ContentFolderEntity {
            return ContentFolderEntity(
                id = domain.id,
                profCode = domain.prof.code,
                subjectId = domain.subjectId,
                name = domain.name,
                emoji = domain.emoji,
                sectionType = domain.sectionType
            )
        }
    }
}

@Entity(tableName = "study_contents")
data class StudyContentEntity(
    @PrimaryKey val id: String,
    val profCode: String,
    val subjectId: String,
    val folderId: String,
    val folderName: String,
    val chapterName: String,
    val typeName: String, // VIDEO or PDF
    val title: String,
    val description: String,
    val mediaUrl: String,
    val cloudflareKey: String,
    val createdAt: Long,
    val isPyq: Boolean = false
) {
    fun toDomain(): StudyContentItem {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        val type = if (typeName == "PDF") StudyContentType.PDF else StudyContentType.VIDEO
        return StudyContentItem(
            id = id,
            prof = prof,
            subjectId = subjectId,
            folderId = folderId,
            folderName = folderName,
            chapterName = chapterName,
            type = type,
            title = title,
            description = description,
            mediaUrl = mediaUrl,
            cloudflareKey = cloudflareKey,
            createdAt = createdAt,
            isPyq = isPyq
        )
    }

    companion object {
        fun fromDomain(domain: StudyContentItem): StudyContentEntity {
            return StudyContentEntity(
                id = domain.id,
                profCode = domain.prof.code,
                subjectId = domain.subjectId,
                folderId = domain.folderId,
                folderName = domain.folderName,
                chapterName = domain.chapterName,
                typeName = domain.type.name,
                title = domain.title,
                description = domain.description,
                mediaUrl = domain.mediaUrl,
                cloudflareKey = domain.cloudflareKey,
                createdAt = domain.createdAt,
                isPyq = domain.isPyq
            )
        }
    }
}

@Entity(tableName = "pomodoro_sessions")
data class PomodoroSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val durationMinutes: Int,
    val completedAtTimestamp: Long,
    val formattedDate: String,
    val modeName: String
)

@Entity(tableName = "mcqs")
data class MCQEntity(
    @PrimaryKey val id: String,
    val profCode: String,
    val subjectId: String = "mcq",
    val sectionId: String = "",
    val sectionName: String = "",
    val folderId: String = "",
    val folderName: String = "",
    val subfolderName: String = "",
    val chapterName: String = "",
    val question: String,
    val questionImageUrl: String = "",
    val optionsJson: String = "[]",
    val correctOptionIndex: Int = 0,
    val explanation: String = "",
    val explanationImageUrl: String = "",
    val difficulty: String = "Medium",
    val tags: String = "High-Yield",
    val createdAt: Long = System.currentTimeMillis(),
    val questionType: String = "MCQ",
    val assertion: String = "",
    val reason: String = "",
    val statementsJson: String = "[]",
    val matchColumnAJson: String = "[]",
    val matchColumnBJson: String = "[]",
    val flowchartStepsJson: String = "[]"
) {
    fun toDomain(): MCQItem {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        val optionsList = mutableListOf<MCQOption>()
        try {
            val arr = org.json.JSONArray(optionsJson)
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                if (item is org.json.JSONObject) {
                    optionsList.add(
                        MCQOption(
                            id = item.optString("id", "opt_$i"),
                            text = item.optString("text", ""),
                            imageUrl = item.optString("imageUrl", "")
                        )
                    )
                } else if (item is String) {
                    optionsList.add(MCQOption(id = "opt_$i", text = item))
                }
            }
        } catch (_: Exception) {}

        fun parseStringList(jsonStr: String): List<String> {
            val res = mutableListOf<String>()
            try {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) res.add(arr.optString(i))
            } catch (_: Exception) {}
            return res
        }

        return MCQItem(
            id = id,
            prof = prof,
            subjectId = subjectId,
            sectionId = sectionId,
            sectionName = sectionName,
            folderId = folderId,
            folderName = folderName,
            subfolderName = subfolderName,
            chapterName = chapterName,
            question = question,
            questionImageUrl = questionImageUrl,
            options = optionsList,
            correctOptionIndex = correctOptionIndex,
            explanation = explanation,
            explanationImageUrl = explanationImageUrl,
            difficulty = difficulty,
            tags = tags,
            createdAt = createdAt,
            questionType = questionType,
            assertion = assertion,
            reason = reason,
            statements = parseStringList(statementsJson),
            matchColumnA = parseStringList(matchColumnAJson),
            matchColumnB = parseStringList(matchColumnBJson),
            flowchartSteps = parseStringList(flowchartStepsJson)
        )
    }

    companion object {
        fun fromDomain(domain: MCQItem): MCQEntity {
            val arr = org.json.JSONArray()
            for (opt in domain.options) {
                val obj = org.json.JSONObject().apply {
                    put("id", opt.id)
                    put("text", opt.text)
                    put("imageUrl", opt.imageUrl)
                }
                arr.put(obj)
            }
            fun toJsonArray(list: List<String>): String {
                val jArr = org.json.JSONArray()
                list.forEach { jArr.put(it) }
                return jArr.toString()
            }
            return MCQEntity(
                id = domain.id,
                profCode = domain.prof.code,
                subjectId = domain.subjectId,
                sectionId = domain.sectionId,
                sectionName = domain.sectionName,
                folderId = domain.folderId,
                folderName = domain.folderName,
                subfolderName = domain.subfolderName,
                chapterName = domain.chapterName,
                question = domain.question,
                questionImageUrl = domain.questionImageUrl,
                optionsJson = arr.toString(),
                correctOptionIndex = domain.correctOptionIndex,
                explanation = domain.explanation,
                explanationImageUrl = domain.explanationImageUrl,
                difficulty = domain.difficulty,
                tags = domain.tags,
                createdAt = domain.createdAt,
                questionType = domain.questionType,
                assertion = domain.assertion,
                reason = domain.reason,
                statementsJson = toJsonArray(domain.statements),
                matchColumnAJson = toJsonArray(domain.matchColumnA),
                matchColumnBJson = toJsonArray(domain.matchColumnB),
                flowchartStepsJson = toJsonArray(domain.flowchartSteps)
            )
        }
    }
}

@Entity(tableName = "mcq_sections")
data class MCQSectionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String = "",
    val emoji: String = "🎯",
    val colorHex1: String = "#0D9488",
    val colorHex2: String = "#0F766E",
    val profCode: String = "prof1",
    val orderIndex: Int = 0
) {
    fun toDomain(): com.example.model.MCQSection {
        val prof = ProfYear.values().firstOrNull { it.code == profCode } ?: ProfYear.FIRST_PROF
        return com.example.model.MCQSection(
            id = id,
            title = title,
            subtitle = subtitle,
            emoji = emoji,
            colorHex1 = colorHex1,
            colorHex2 = colorHex2,
            prof = prof,
            orderIndex = orderIndex
        )
    }

    companion object {
        fun fromDomain(domain: com.example.model.MCQSection): MCQSectionEntity {
            return MCQSectionEntity(
                id = domain.id,
                title = domain.title,
                subtitle = domain.subtitle,
                emoji = domain.emoji,
                colorHex1 = domain.colorHex1,
                colorHex2 = domain.colorHex2,
                profCode = domain.prof.code,
                orderIndex = domain.orderIndex
            )
        }
    }
}

