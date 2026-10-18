package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        FlashcardEntity::class,
        PYQEntity::class,
        LectureEntity::class,
        ShortEntity::class,
        ContentFolderEntity::class,
        StudyContentEntity::class,
        PomodoroSessionEntity::class,
        MCQEntity::class,
        MCQSectionEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class MedXMedDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun pyqDao(): PYQDao
    abstract fun lectureDao(): LectureDao
    abstract fun shortDao(): ShortDao
    abstract fun folderDao(): FolderDao
    abstract fun studyContentDao(): StudyContentDao
    abstract fun pomodoroSessionDao(): PomodoroSessionDao
    abstract fun mcqDao(): MCQDao
    abstract fun mcqSectionDao(): MCQSectionDao

    companion object {
        @Volatile
        private var INSTANCE: MedXMedDatabase? = null

        fun getDatabase(context: Context): MedXMedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedXMedDatabase::class.java,
                    "medxmed_mbbs_study.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
