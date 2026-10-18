package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY cachedAtTimestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE profCode = :profCode ORDER BY cachedAtTimestamp DESC")
    fun getNotesByProf(profCode: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isCachedOffline = 1 ORDER BY cachedAtTimestamp DESC")
    fun getCachedOfflineNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Query("UPDATE notes SET isCachedOffline = :isCached WHERE id = :id")
    suspend fun updateOfflineStatus(id: String, isCached: Boolean)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM notes")
    suspend fun clearAllNotes()

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNotesCount(): Int
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY id ASC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE profCode = :profCode ORDER BY id ASC")
    fun getFlashcardsByProf(profCode: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE isSavedOffline = 1 ORDER BY id ASC")
    fun getSavedOfflineFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE isMastered = 1 ORDER BY id ASC")
    fun getMasteredFlashcards(): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity)

    @Query("UPDATE flashcards SET isMastered = :isMastered WHERE id = :id")
    suspend fun updateMastery(id: String, isMastered: Boolean)

    @Query("UPDATE flashcards SET isSavedOffline = :isSaved WHERE id = :id")
    suspend fun updateSavedOffline(id: String, isSaved: Boolean)

    @Query("UPDATE flashcards SET isSavedOffline = :isSaved WHERE folderName = :folderName")
    suspend fun updateDeckSavedOffline(folderName: String, isSaved: Boolean)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcardById(id: String)

    @Query("DELETE FROM flashcards WHERE folderId = :folderId")
    suspend fun deleteFlashcardsByFolderId(folderId: String)

    @Query("DELETE FROM flashcards WHERE folderName = :folderName")
    suspend fun deleteFlashcardsByFolderName(folderName: String)

    @Query("DELETE FROM flashcards")
    suspend fun clearAllFlashcards()

    @Query("SELECT COUNT(*) FROM flashcards")
    suspend fun getFlashcardsCount(): Int
}

@Dao
interface PYQDao {
    @Query("SELECT * FROM pyqs ORDER BY year DESC")
    fun getAllPYQs(): Flow<List<PYQEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPYQs(pyqs: List<PYQEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPYQ(pyq: PYQEntity)

    @Query("DELETE FROM pyqs WHERE id = :id")
    suspend fun deletePYQById(id: String)

    @Query("DELETE FROM pyqs")
    suspend fun clearAllPYQs()

    @Query("SELECT COUNT(*) FROM pyqs")
    suspend fun getPYQsCount(): Int
}

@Dao
interface LectureDao {
    @Query("SELECT * FROM lectures")
    fun getAllLectures(): Flow<List<LectureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLectures(lectures: List<LectureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureEntity)

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteLectureById(id: String)

    @Query("DELETE FROM lectures")
    suspend fun clearAllLectures()

    @Query("SELECT COUNT(*) FROM lectures")
    suspend fun getLecturesCount(): Int
}

@Dao
interface ShortDao {
    @Query("SELECT * FROM shorts ORDER BY createdAt DESC")
    fun getAllShorts(): Flow<List<ShortEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShorts(shorts: List<ShortEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShort(short: ShortEntity)

    @Query("UPDATE shorts SET title = :newTitle, description = :newDesc WHERE id = :id")
    suspend fun updateShort(id: String, newTitle: String, newDesc: String)

    @Query("DELETE FROM shorts WHERE id = :id")
    suspend fun deleteShortById(id: String)

    @Query("DELETE FROM shorts")
    suspend fun clearAllShorts()

    @Query("SELECT COUNT(*) FROM shorts")
    suspend fun getShortsCount(): Int
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<ContentFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: ContentFolderEntity)

    @Query("UPDATE folders SET name = :newName, emoji = :newEmoji WHERE id = :id")
    suspend fun renameFolder(id: String, newName: String, newEmoji: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    @Query("DELETE FROM folders WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))")
    suspend fun deleteFoldersByName(name: String)

    @Query("DELETE FROM folders WHERE id = :id OR LOWER(TRIM(name)) = LOWER(TRIM(:name))")
    suspend fun deleteFolderByIdOrName(id: String, name: String)

    @Query("DELETE FROM folders WHERE subjectId = :subjectId")
    suspend fun deleteFoldersBySubjectId(subjectId: String)

    @Query("DELETE FROM folders")
    suspend fun clearAllFolders()

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFoldersCount(): Int
}

@Dao
interface StudyContentDao {
    @Query("SELECT * FROM study_contents ORDER BY createdAt DESC")
    fun getAllContents(): Flow<List<StudyContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: StudyContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContents(contents: List<StudyContentEntity>)

    @Query("UPDATE study_contents SET title = :newTitle, description = :newDesc WHERE id = :id")
    suspend fun updateContent(id: String, newTitle: String, newDesc: String)

    @Query("UPDATE study_contents SET mediaUrl = :newUrl, cloudflareKey = :newUrl WHERE id = :id")
    suspend fun updateMediaUrl(id: String, newUrl: String)

    @Query("DELETE FROM study_contents WHERE id = :id")
    suspend fun deleteContentById(id: String)

    @Query("DELETE FROM study_contents WHERE folderId = :folderId")
    suspend fun deleteContentsByFolderId(folderId: String)

    @Query("DELETE FROM study_contents WHERE LOWER(TRIM(folderName)) = LOWER(TRIM(:folderName))")
    suspend fun deleteContentsByFolderName(folderName: String)

    @Query("DELETE FROM study_contents WHERE folderId = :folderId OR LOWER(TRIM(folderName)) = LOWER(TRIM(:folderName))")
    suspend fun deleteContentsByFolderIdOrName(folderId: String, folderName: String)

    @Query("DELETE FROM study_contents WHERE subjectId = :subjectId")
    suspend fun deleteContentsBySubjectId(subjectId: String)

    @Query("DELETE FROM study_contents")
    suspend fun clearAllContents()
}

@Dao
interface PomodoroSessionDao {
    @Query("SELECT * FROM pomodoro_sessions ORDER BY completedAtTimestamp DESC")
    fun getAllSessions(): Flow<List<PomodoroSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PomodoroSessionEntity)

    @Query("DELETE FROM pomodoro_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM pomodoro_sessions")
    suspend fun clearAllSessions()
}

@Dao
interface MCQDao {
    @Query("SELECT * FROM mcqs ORDER BY createdAt ASC")
    fun getAllMCQs(): Flow<List<MCQEntity>>

    @Query("SELECT * FROM mcqs WHERE folderId = :folderId ORDER BY createdAt ASC")
    fun getMCQsByFolder(folderId: String): Flow<List<MCQEntity>>

    @Query("SELECT * FROM mcqs WHERE folderId = :folderId AND chapterName = :chapterName ORDER BY createdAt ASC")
    fun getMCQsByChapter(folderId: String, chapterName: String): Flow<List<MCQEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMCQ(mcq: MCQEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMCQs(mcqs: List<MCQEntity>)

    @Query("DELETE FROM mcqs WHERE id = :id")
    suspend fun deleteMCQById(id: String)

    @Query("DELETE FROM mcqs WHERE folderId = :folderId AND chapterName = :chapterName")
    suspend fun deleteMCQsByChapter(folderId: String, chapterName: String)

    @Query("DELETE FROM mcqs WHERE (folderId = :folderId OR folderName = :folderName) AND chapterName = :chapterName")
    suspend fun deleteMCQsByFolderAndChapter(folderId: String, folderName: String, chapterName: String)

    @Query("DELETE FROM mcqs WHERE (folderId = :folderId OR folderName = :folderName) AND subfolderName = :subfolderName")
    suspend fun deleteMCQsBySubfolder(folderId: String, folderName: String, subfolderName: String)

    @Query("DELETE FROM mcqs WHERE folderName = :folderName")
    suspend fun deleteMCQsByFolderName(folderName: String)

    @Query("DELETE FROM mcqs WHERE folderId = :folderId")
    suspend fun deleteMCQsByFolderId(folderId: String)

    @Query("DELETE FROM mcqs WHERE sectionId = :sectionId")
    suspend fun deleteMCQsBySectionId(sectionId: String)

    @Query("DELETE FROM mcqs")
    suspend fun clearAllMCQs()

    @Query("DELETE FROM mcqs WHERE id IN (:ids)")
    suspend fun deleteMCQsByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM mcqs")
    suspend fun getMCQsCount(): Int
}

@Dao
interface MCQSectionDao {
    @Query("SELECT * FROM mcq_sections ORDER BY orderIndex ASC")
    fun getAllSections(): Flow<List<MCQSectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: MCQSectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<MCQSectionEntity>)

    @Query("DELETE FROM mcq_sections WHERE id = :id")
    suspend fun deleteSectionById(id: String)

    @Query("DELETE FROM mcq_sections")
    suspend fun clearAllSections()
}

