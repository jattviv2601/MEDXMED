package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MEDXMED", appName)
  }

  @Test
  fun `room database stores and retrieves notes and flashcards offline`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.MedXMedDatabase.getDatabase(context)
    val noteDao = db.noteDao()
    val flashcardDao = db.flashcardDao()
    val folderDao = db.folderDao()
    val studyContentDao = db.studyContentDao()
    
    org.junit.Assert.assertNotNull(noteDao)
    org.junit.Assert.assertNotNull(flashcardDao)
    org.junit.Assert.assertNotNull(folderDao)
    org.junit.Assert.assertNotNull(studyContentDao)
  }

  @Test
  fun `flashcards are sorted serially and leading numbers are cleaned`() {
    val rawCard10 = com.example.model.FlashcardItem(
        id = "fc_random_uuid_a",
        subjectId = "flashcard",
        prof = com.example.model.ProfYear.FIRST_PROF,
        question = "10. Which enzyme is rate limiting in glycolysis?",
        answer = "Phosphofructokinase-1"
    )
    val rawCard2 = com.example.model.FlashcardItem(
        id = "fc_random_uuid_b",
        subjectId = "flashcard",
        prof = com.example.model.ProfYear.FIRST_PROF,
        question = "2) Which nerve innervates the biceps brachii?",
        answer = "Musculocutaneous nerve"
    )
    val rawCard1 = com.example.model.FlashcardItem(
        id = "fc_random_uuid_c",
        subjectId = "flashcard",
        prof = com.example.model.ProfYear.FIRST_PROF,
        question = "Q1: What is the primary pacemaker of heart?",
        answer = "SA node"
    )

    val sorted = com.example.util.FlashcardUtils.sortFlashcardsSerially(listOf(rawCard10, rawCard2, rawCard1))
    assertEquals("fc_random_uuid_c", sorted[0].id)
    assertEquals("fc_random_uuid_b", sorted[1].id)
    assertEquals("fc_random_uuid_a", sorted[2].id)

    // Test text cleaning
    assertEquals("What is the primary pacemaker of heart?", com.example.util.FlashcardUtils.cleanQuestionText(sorted[0].question))
    assertEquals("Which nerve innervates the biceps brachii?", com.example.util.FlashcardUtils.cleanQuestionText(sorted[1].question))
    assertEquals("Which enzyme is rate limiting in glycolysis?", com.example.util.FlashcardUtils.cleanQuestionText(sorted[2].question))
  }
}
