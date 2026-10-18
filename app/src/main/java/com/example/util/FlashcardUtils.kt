package com.example.util

import com.example.model.FlashcardItem

object FlashcardUtils {

    /**
     * Extracts the leading question number if present (e.g. "1.", "Q2:", "3)", "Question 4:").
     * If not found in the question text, falls back to numeric suffix in the ID.
     */
    fun extractQuestionNumber(question: String, id: String = ""): Int? {
        val clean = question.trim()
        val match = Regex("""^(?:[Qq](?:uestion)?\.?\s*(\d+)|\[?(\d+)\]?|\((\d+)\)|(\d+))[\.\)\:\-\s]""").find(clean)
        if (match != null) {
            val numStr = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            val parsed = numStr?.toIntOrNull()
            if (parsed != null) return parsed
        }

        // Fallback: Check if the ID has a number, e.g. "fc_seed_1" -> 1, "fc_42" -> 42
        val idMatch = Regex("""\b(\d+)\b""").find(id) ?: Regex("""_(\d+)$""").find(id)
        return idMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * Cleans leading question numbers/prefixes like "1. ", "Q1: ", "1) ", "Question 2 - ".
     * Ensures options like "A. ... B. ...", clinical vignettes starting with "A 45-year-old..." remain completely intact.
     */
    fun cleanQuestionText(question: String): String {
        var text = question.trim()
        val regex = Regex("""^(?:[Qq](?:uestion)?\.?\s*\d+|\[?\d+\]?|\(\d+\)|\d+)[\.\)\:\-\s]+\s*""")
        var safetyCounter = 0
        while (regex.containsMatchIn(text) && safetyCounter < 5) {
            val replaced = text.replaceFirst(regex, "").trim()
            if (replaced.isBlank() || replaced == text) break
            text = replaced
            safetyCounter++
        }
        return text.ifBlank { question.trim() }
    }

    /**
     * Sorts flashcards serially based on natural question numbers (1, 2, 3... 10... 42).
     * If cards do not have a question number, falls back to ID number or stable relative position.
     */
    fun sortFlashcardsSerially(cards: List<FlashcardItem>): List<FlashcardItem> {
        return cards.mapIndexed { originalIndex, card ->
            val qNum = extractQuestionNumber(card.question, card.id)
            Triple(card, qNum, originalIndex)
        }.sortedWith { a, b ->
            val numA = a.second
            val numB = b.second
            when {
                numA != null && numB != null -> {
                    val cmp = numA.compareTo(numB)
                    if (cmp != 0) cmp else a.third.compareTo(b.third)
                }
                numA != null && numB == null -> -1
                numA == null && numB != null -> 1
                else -> a.third.compareTo(b.third)
            }
        }.map { it.first }
    }
}
