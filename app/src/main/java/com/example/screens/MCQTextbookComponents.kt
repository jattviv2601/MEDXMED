package com.example.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MCQBookmark
import com.example.model.MCQItem

/**
 * Structured Data for Textbook-style MCQ Rendering
 */
data class FormattedMCQContent(
    val type: String, // ASSERTION_REASON, MATCH_THE_COLUMN, STATEMENT_BASED, FLOWCHART, TRUE_FALSE, MCQ
    val introPrompt: String,
    val assertion: String = "",
    val reason: String = "",
    val matchColumnA: List<String> = emptyList(),
    val matchColumnB: List<String> = emptyList(),
    val statements: List<String> = emptyList(),
    val flowchartSteps: List<String> = emptyList(),
    val conclusionQuery: String = ""
)

/**
 * Intelligent parser that converts an MCQItem into structured textbook content.
 * Supports both explicit fields and natural question text parsing.
 */
fun parseMCQContent(
    questionText: String,
    questionType: String,
    assertion: String,
    reason: String,
    statements: List<String>,
    matchColumnA: List<String>,
    matchColumnB: List<String>,
    flowchartSteps: List<String>
): FormattedMCQContent {
    val qTypeUpper = questionType.trim().uppercase()

    // 1. Explicit Fields Checking
    if (assertion.isNotBlank() && reason.isNotBlank()) {
        return FormattedMCQContent(
            type = "ASSERTION_REASON",
            introPrompt = questionText.ifBlank { "Given below are two statements: one is labelled as Assertion (A) and the other is labelled as Reason (R):" },
            assertion = assertion.trim(),
            reason = reason.trim(),
            conclusionQuery = "In the light of the above statements, choose the most appropriate answer from the options given below:"
        )
    }

    if (matchColumnA.isNotEmpty() && matchColumnB.isNotEmpty()) {
        return FormattedMCQContent(
            type = "MATCH_THE_COLUMN",
            introPrompt = questionText.ifBlank { "Match the items in Column I with those in Column II and choose the correct combination:" },
            matchColumnA = matchColumnA,
            matchColumnB = matchColumnB,
            conclusionQuery = "Select the correct option matching Column I and Column II:"
        )
    }

    if (statements.isNotEmpty()) {
        return FormattedMCQContent(
            type = "STATEMENT_BASED",
            introPrompt = questionText.ifBlank { "Consider the following statements:" },
            statements = statements,
            conclusionQuery = "Which of the statements given above is/are correct?"
        )
    }

    if (flowchartSteps.isNotEmpty()) {
        return FormattedMCQContent(
            type = "FLOWCHART",
            introPrompt = questionText.ifBlank { "Examine the following biochemical/clinical pathway:" },
            flowchartSteps = flowchartSteps,
            conclusionQuery = "Identify the correct step or enzyme from the options below:"
        )
    }

    // 2. Question Type Override from JSON
    if (qTypeUpper == "ASSERTION_REASON" || qTypeUpper == "ASSERTION" || qTypeUpper == "AR") {
        val extractedAR = extractAssertionReasonFromText(questionText)
        return FormattedMCQContent(
            type = "ASSERTION_REASON",
            introPrompt = extractedAR.first.ifBlank { "Given below are two statements: one is labelled as Assertion (A) and the other is labelled as Reason (R):" },
            assertion = extractedAR.second,
            reason = extractedAR.third,
            conclusionQuery = "Choose the correct answer from the options given below:"
        )
    }

    if (qTypeUpper == "MATCH_THE_COLUMN" || qTypeUpper == "MATCH" || qTypeUpper == "COLUMN") {
        val extractedMatch = extractMatchColumnsFromText(questionText)
        return FormattedMCQContent(
            type = "MATCH_THE_COLUMN",
            introPrompt = extractedMatch.first.ifBlank { "Match Column I with Column II:" },
            matchColumnA = extractedMatch.second,
            matchColumnB = extractedMatch.third,
            conclusionQuery = "Select the correct match from the options below:"
        )
    }

    if (qTypeUpper == "STATEMENT_BASED" || qTypeUpper == "STATEMENTS") {
        val extractedStmts = extractStatementsFromText(questionText)
        return FormattedMCQContent(
            type = "STATEMENT_BASED",
            introPrompt = extractedStmts.first.ifBlank { "Consider the following statements:" },
            statements = extractedStmts.second,
            conclusionQuery = extractedStmts.third.ifBlank { "Which of the statements given above is/are correct?" }
        )
    }

    if (qTypeUpper == "FLOWCHART" || qTypeUpper == "PATHWAY" || qTypeUpper == "SEQUENCE") {
        val extractedSteps = extractFlowchartFromText(questionText)
        return FormattedMCQContent(
            type = "FLOWCHART",
            introPrompt = extractedSteps.first.ifBlank { "Observe the pathway sequence below:" },
            flowchartSteps = extractedSteps.second,
            conclusionQuery = extractedSteps.third.ifBlank { "Choose the correct intermediate/answer from the options below:" }
        )
    }

    if (qTypeUpper == "TRUE_FALSE" || qTypeUpper == "TF") {
        return FormattedMCQContent(
            type = "TRUE_FALSE",
            introPrompt = questionText,
            conclusionQuery = ""
        )
    }

    // 3. Natural Text Auto-Detection
    // Detect Assertion & Reason in text
    if (questionText.contains("Assertion", ignoreCase = true) && questionText.contains("Reason", ignoreCase = true)) {
        val extractedAR = extractAssertionReasonFromText(questionText)
        if (extractedAR.second.isNotBlank() && extractedAR.third.isNotBlank()) {
            return FormattedMCQContent(
                type = "ASSERTION_REASON",
                introPrompt = extractedAR.first.ifBlank { "Given below are two statements: Assertion (A) and Reason (R):" },
                assertion = extractedAR.second,
                reason = extractedAR.third,
                conclusionQuery = "In light of the above statements, choose the most appropriate answer:"
            )
        }
    }

    // Detect Match the Column in text
    if ((questionText.contains("Column I", ignoreCase = true) || questionText.contains("Column 1", ignoreCase = true) || questionText.contains("Column-I", ignoreCase = true)) &&
        (questionText.contains("Column II", ignoreCase = true) || questionText.contains("Column 2", ignoreCase = true) || questionText.contains("Column-II", ignoreCase = true))
    ) {
        val extractedMatch = extractMatchColumnsFromText(questionText)
        if (extractedMatch.second.isNotEmpty() && extractedMatch.third.isNotEmpty()) {
            return FormattedMCQContent(
                type = "MATCH_THE_COLUMN",
                introPrompt = extractedMatch.first.ifBlank { "Match Column I with Column II:" },
                matchColumnA = extractedMatch.second,
                matchColumnB = extractedMatch.third,
                conclusionQuery = "Select the correct combination:"
            )
        }
    }

    // Detect Statement I & Statement II in text
    if (questionText.contains("Statement I", ignoreCase = true) || questionText.contains("Statement 1", ignoreCase = true) || questionText.contains("Statement-I", ignoreCase = true)) {
        val extractedStmts = extractStatementsFromText(questionText)
        if (extractedStmts.second.isNotEmpty()) {
            return FormattedMCQContent(
                type = "STATEMENT_BASED",
                introPrompt = extractedStmts.first.ifBlank { "Consider the following statements:" },
                statements = extractedStmts.second,
                conclusionQuery = extractedStmts.third.ifBlank { "Which of the statements given above is/are correct?" }
            )
        }
    }

    // Detect Flowchart arrows in text
    if (questionText.contains("->") || questionText.contains("➔") || questionText.contains("-->") || questionText.contains("=>")) {
        val extractedSteps = extractFlowchartFromText(questionText)
        if (extractedSteps.second.size >= 2) {
            return FormattedMCQContent(
                type = "FLOWCHART",
                introPrompt = extractedSteps.first.ifBlank { "Trace the sequence in the pathway:" },
                flowchartSteps = extractedSteps.second,
                conclusionQuery = extractedSteps.third.ifBlank { "Identify the correct step from the options below:" }
            )
        }
    }

    // Standard MCQ
    return FormattedMCQContent(
        type = "MCQ",
        introPrompt = questionText,
        conclusionQuery = ""
    )
}

fun parseMCQContentFromItem(item: MCQItem): FormattedMCQContent {
    return parseMCQContent(
        questionText = item.question,
        questionType = item.questionType,
        assertion = item.assertion,
        reason = item.reason,
        statements = item.statements,
        matchColumnA = item.matchColumnA,
        matchColumnB = item.matchColumnB,
        flowchartSteps = item.flowchartSteps
    )
}

fun parseMCQContentFromBookmark(bm: MCQBookmark): FormattedMCQContent {
    return parseMCQContent(
        questionText = bm.question,
        questionType = bm.questionType,
        assertion = bm.assertion,
        reason = bm.reason,
        statements = bm.statements,
        matchColumnA = bm.matchColumnA,
        matchColumnB = bm.matchColumnB,
        flowchartSteps = bm.flowchartSteps
    )
}

// ==========================================
// REGEX TEXT EXTRACTION HELPERS
// ==========================================

private fun extractAssertionReasonFromText(text: String): Triple<String, String, String> {
    var intro = ""
    var assertion = ""
    var reason = ""

    val aRegex = Regex("(?:Assertion\\s*(?:\\([Aa]\\))?[:\\-]?\\s*)(.*?)(?=(?:Reason\\s*(?:\\([Rr]\\))?[:\\-]?)|$)", RegexOption.DOT_MATCHES_ALL)
    val rRegex = Regex("(?:Reason\\s*(?:\\([Rr]\\))?[:\\-]?\\s*)(.*)", RegexOption.DOT_MATCHES_ALL)

    val aMatch = aRegex.find(text)
    if (aMatch != null) {
        val aIndex = aMatch.range.first
        if (aIndex > 0) {
            intro = text.substring(0, aIndex).trim()
        }
        assertion = aMatch.groupValues[1].trim()
    }

    val rMatch = rRegex.find(text)
    if (rMatch != null) {
        reason = rMatch.groupValues[1].trim()
    }

    if (assertion.isBlank() && reason.isBlank()) {
        assertion = text
    }

    return Triple(intro, assertion, reason)
}

private fun extractMatchColumnsFromText(text: String): Triple<String, List<String>, List<String>> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    var intro = ""
    val colA = mutableListOf<String>()
    val colB = mutableListOf<String>()

    var inColA = false
    var inColB = false

    for (line in lines) {
        if (line.contains("Column I", ignoreCase = true) || line.contains("Column 1", ignoreCase = true) || line.contains("Column-I", ignoreCase = true)) {
            inColA = true
            inColB = false
            continue
        }
        if (line.contains("Column II", ignoreCase = true) || line.contains("Column 2", ignoreCase = true) || line.contains("Column-II", ignoreCase = true)) {
            inColB = true
            inColA = false
            continue
        }

        if (!inColA && !inColB) {
            intro += if (intro.isEmpty()) line else "\n$line"
        } else if (inColA) {
            colA.add(line)
        } else if (inColB) {
            colB.add(line)
        }
    }

    return Triple(intro, colA, colB)
}

private fun extractStatementsFromText(text: String): Triple<String, List<String>, String> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    var intro = ""
    val stmts = mutableListOf<String>()
    var conclusion = ""

    for (line in lines) {
        val isStmt = line.startsWith("Statement", ignoreCase = true) ||
                Regex("^[1-4]\\.\\s+.*").matches(line) ||
                Regex("^[I|V|X]+\\.\\s+.*").matches(line) ||
                Regex("^\\([1-4]\\)\\s+.*").matches(line)

        val isQuestionEnd = line.contains("Which of the", ignoreCase = true) || line.contains("Choose the correct", ignoreCase = true)

        if (isQuestionEnd) {
            conclusion = line
        } else if (isStmt) {
            stmts.add(line)
        } else if (stmts.isEmpty()) {
            intro += if (intro.isEmpty()) line else "\n$line"
        } else {
            conclusion += if (conclusion.isEmpty()) line else "\n$line"
        }
    }

    return Triple(intro, stmts, conclusion)
}

private fun extractFlowchartFromText(text: String): Triple<String, List<String>, String> {
    val arrowRegex = Regex("\\s*(?:->|➔|-->|=>|→)\\s*")
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    var intro = ""
    var flowchartLine = ""
    var conclusion = ""

    for (line in lines) {
        if (line.contains("->") || line.contains("➔") || line.contains("-->") || line.contains("=>") || line.contains("→")) {
            flowchartLine = line
        } else if (flowchartLine.isEmpty()) {
            intro += if (intro.isEmpty()) line else "\n$line"
        } else {
            conclusion += if (conclusion.isEmpty()) line else "\n$line"
        }
    }

    val steps = if (flowchartLine.isNotBlank()) {
        flowchartLine.split(arrowRegex).map { it.trim() }.filter { it.isNotBlank() }
    } else emptyList()

    return Triple(intro, steps, conclusion)
}

/**
 * Parses markdown bold (**word**) into Jetpack Compose AnnotatedString
 * to deliver crisp medical textbook dark highlight styling.
 */
fun formatTextWithBoldKeywords(text: String, baseColor: Color = Color(0xFF0F172A)): AnnotatedString {
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    return buildAnnotatedString {
        var lastIndex = 0

        boldRegex.findAll(text).forEach { matchResult ->
            val range = matchResult.range
            if (range.first > lastIndex) {
                append(text.substring(lastIndex, range.first))
            }
            val boldContent = matchResult.groupValues[1]
            pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = baseColor))
            append(boldContent)
            pop()
            lastIndex = range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

// ==========================================
// TEXTBOOK QUESTION STEM COMPOSABLE
// ==========================================

@Composable
fun TextbookQuestionContent(
    content: FormattedMCQContent,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val darkCardBg = Color(0xFF1E293B)
    val lightCardBg = Color.White
    val textColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Question Type Badge Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (content.type) {
                "ASSERTION_REASON" -> {
                    Surface(
                        color = if (isDarkTheme) Color(0xFF312E81) else Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFF4F46E5) else Color(0xFFC7D2FE))
                    ) {
                        Text(
                            text = "⚖️ ASSERTION & REASON",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFFA5B4FC) else Color(0xFF3730A3),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                "MATCH_THE_COLUMN" -> {
                    Surface(
                        color = if (isDarkTheme) Color(0xFF14532D) else Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFF22C55E) else Color(0xFFBBF7D0))
                    ) {
                        Text(
                            text = "🔀 MATCH THE COLUMN",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFF86EFAC) else Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                "STATEMENT_BASED" -> {
                    Surface(
                        color = if (isDarkTheme) Color(0xFF78350F) else Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFFF59E0B) else Color(0xFFFDE68A))
                    ) {
                        Text(
                            text = "📝 STATEMENT BASED",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFFFCD34D) else Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                "FLOWCHART" -> {
                    Surface(
                        color = if (isDarkTheme) Color(0xFF0F766E) else Color(0xFFF0FDFA),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFF14B8A6) else Color(0xFF99F6E4))
                    ) {
                        Text(
                            text = "🔄 FLOWCHART / PATHWAY",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFF5EEAD4) else Color(0xFF115E59),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                "TRUE_FALSE" -> {
                    Surface(
                        color = if (isDarkTheme) Color(0xFF581C87) else Color(0xFFFAF5FF),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFFA855F7) else Color(0xFFE9D5FF))
                    ) {
                        Text(
                            text = "⚖️ TRUE / FALSE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFFD8B4FE) else Color(0xFF6B21A8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Question Prompt
        if (content.introPrompt.isNotBlank()) {
            Text(
                text = formatTextWithBoldKeywords(content.introPrompt, textColor),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                lineHeight = 23.sp
            )
        }

        // Dynamic Format Views
        when (content.type) {
            "ASSERTION_REASON" -> {
                // Assertion (A) Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDarkTheme) Color(0xFF312E81) else Color(0xFF1E1B4B)
                            ) {
                                Text(
                                    text = "ASSERTION (A)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatTextWithBoldKeywords(content.assertion, textColor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Linking indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    Surface(
                        shape = CircleShape,
                        color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "and",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = subTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                }

                // Reason (R) Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDarkTheme) Color(0xFF065F46) else Color(0xFF064E3B)
                            ) {
                                Text(
                                    text = "REASON (R)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatTextWithBoldKeywords(content.reason, textColor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            "MATCH_THE_COLUMN" -> {
                // Side-by-Side 2-Column Table Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFF1E293B))
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "COLUMN I",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "COLUMN II",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Rows
                        val maxRows = maxOf(content.matchColumnA.size, content.matchColumnB.size)
                        for (i in 0 until maxRows) {
                            val itemA = content.matchColumnA.getOrNull(i) ?: ""
                            val itemB = content.matchColumnB.getOrNull(i) ?: ""
                            val rowBg = if (i % 2 == 0) {
                                if (isDarkTheme) Color(0xFF1E293B) else Color.White
                            } else {
                                if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = itemA,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = itemB,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (i < maxRows - 1) {
                                HorizontalDivider(thickness = 0.5.dp, color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                            }
                        }
                    }
                }
            }

            "STATEMENT_BASED" -> {
                // Statement Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val romanLetters = listOf("I", "II", "III", "IV", "V", "VI")
                    content.statements.forEachIndexed { sIdx, stmt ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isDarkTheme) Color(0xFF334155) else Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = romanLetters.getOrNull(sIdx) ?: "${sIdx + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatTextWithBoldKeywords(stmt, textColor),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            "FLOWCHART" -> {
                // Colorful Pathway Flowchart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val colorsList = listOf(
                            Pair(Color(0xFFEFF6FF), Color(0xFF2563EB)),
                            Pair(Color(0xFFF0FDF4), Color(0xFF16A34A)),
                            Pair(Color(0xFFFEF3C7), Color(0xFFD97706)),
                            Pair(Color(0xFFFDF2F8), Color(0xFFDB2777)),
                            Pair(Color(0xFFF5F3FF), Color(0xFF7C3AED))
                        )

                        content.flowchartSteps.forEachIndexed { fIdx, step ->
                            val colorPair = colorsList[fIdx % colorsList.size]
                            Card(
                                modifier = Modifier.fillMaxWidth(0.92f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkTheme) Color(0xFF0F172A) else colorPair.first
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, colorPair.second)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colorPair.second,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${fIdx + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = step,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (fIdx < content.flowchartSteps.size - 1) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Flows to",
                                    tint = colorPair.second,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Conclusion Query (e.g. "Which of the statements given above is/are correct?")
        if (content.conclusionQuery.isNotBlank()) {
            Text(
                text = content.conclusionQuery,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                lineHeight = 22.sp
            )
        }
    }
}
