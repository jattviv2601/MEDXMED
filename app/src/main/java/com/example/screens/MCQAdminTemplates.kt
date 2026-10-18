package com.example.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Production-ready ChatGPT Prompt for generating textbook medical MCQs
 * across all formats (Assertion-Reason, Match Column, Statements, Flowchart, True/False).
 */
val CHATGPT_MCQ_PROMPT = """
You are a senior medical faculty and exam question designer. Generate high-yield MBBS / NEET-PG / USMLE practice MCQs on the topic requested by the user.

CRITICAL INSTRUCTIONS:
1. Output MUST be ONLY a valid JSON array of question objects (starting with `[` and ending with `]`).
2. Do NOT wrap in conversational greetings, apologies, or explanations outside the JSON.
3. Every question MUST have an "options" array with 4 distinct choices.
4. "answer" MUST be the 0-based index of the correct option (0, 1, 2, or 3) or the option letter ("A", "B", "C", or "D").
5. Provide a clear, high-yield "explanation" for why the answer is correct and why other distractors are wrong.
6. Provide diverse question types matching standard medical textbooks:
   - "type": "MCQ" (Standard Single Best Answer / Clinical Vignette)
   - "type": "ASSERTION_REASON" (with separate "assertion" and "reason" fields)
   - "type": "MATCH_THE_COLUMN" (with "matchColumnA" and "matchColumnB" arrays of 4 items each)
   - "type": "STATEMENT_BASED" (with "statements" array of Statement I, II, III)
   - "type": "FLOWCHART" (with "flowchartSteps" array showing steps connected by arrows)
   - "type": "TRUE_FALSE" (True/False evaluation)

--- JSON FORMAT SCHEMA & EXAMPLES ---

[
  {
    "type": "ASSERTION_REASON",
    "question": "Given below are two statements: one is labelled as Assertion (A) and the other is labelled as Reason (R):",
    "assertion": "Myocardial infarction results in coagulative necrosis of cardiac myocytes.",
    "reason": "Severe ischemia causes denaturation of structural and enzymatic proteins, preventing immediate autolysis.",
    "options": [
      "Both Assertion and Reason are true, and Reason is the correct explanation of Assertion",
      "Both Assertion and Reason are true, but Reason is NOT the correct explanation of Assertion",
      "Assertion is true, but Reason is false",
      "Assertion is false, but Reason is true"
    ],
    "answer": 0,
    "explanation": "Ischemia causes cellular acidosis and protein denaturation, preventing immediate autolysis and preserving tissue architecture for several days.",
    "difficulty": "Hard",
    "tags": "Pathology"
  },
  {
    "type": "MATCH_THE_COLUMN",
    "question": "Match the organelle in Column I with its primary function in Column II:",
    "matchColumnA": [
      "A. Mitochondria",
      "B. Rough Endoplasmic Reticulum",
      "C. Lysosome",
      "D. Peroxisome"
    ],
    "matchColumnB": [
      "1. Secretory protein translation & folding",
      "2. ATP generation via oxidative phosphorylation",
      "3. Very long-chain fatty acid beta-oxidation",
      "4. Acid hydrolase digestion of cellular debris"
    ],
    "options": [
      "A-2, B-1, C-4, D-3",
      "A-1, B-2, C-3, D-4",
      "A-2, B-4, C-1, D-3",
      "A-3, B-1, C-4, D-2"
    ],
    "answer": 0,
    "explanation": "Mitochondria generate ATP (2), RER translates secretory proteins (1), lysosomes digest debris via acid hydrolases (4), peroxisomes oxidize VLCFAs (3).",
    "difficulty": "Medium",
    "tags": "Biochemistry"
  },
  {
    "type": "STATEMENT_BASED",
    "question": "Consider the following statements regarding Vitamin B12 deficiency:",
    "statements": [
      "Statement I: Vitamin B12 deficiency leads to subacute combined degeneration of the spinal cord.",
      "Statement II: Serum methylmalonic acid (MMA) levels are typically normal in isolated B12 deficiency.",
      "Statement III: Peripheral blood smear characteristically shows hypersegmented neutrophils."
    ],
    "options": [
      "Statements I and III only are correct",
      "Statement II only is correct",
      "Statements I and II only are correct",
      "Statements I, II and III are correct"
    ],
    "answer": 0,
    "explanation": "Serum methylmalonic acid (MMA) is elevated in B12 deficiency due to impaired methylmalonyl-CoA mutase. Statements I and III are correct.",
    "difficulty": "Hard",
    "tags": "Hematology"
  },
  {
    "type": "FLOWCHART",
    "question": "Identify the missing enzyme (X) in the following catecholamine biosynthesis pathway:",
    "flowchartSteps": [
      "L-Tyrosine",
      "L-DOPA",
      "Dopamine",
      "Norepinephrine (via X)",
      "Epinephrine"
    ],
    "options": [
      "Dopamine beta-hydroxylase",
      "Tyrosine hydroxylase",
      "DOPA decarboxylase",
      "Phenylethanolamine N-methyltransferase"
    ],
    "answer": 0,
    "explanation": "Dopamine beta-hydroxylase converts Dopamine to Norepinephrine inside synaptic vesicles.",
    "difficulty": "Medium",
    "tags": "Pharmacology"
  },
  {
    "type": "TRUE_FALSE",
    "question": "Which of the following statements regarding Diabetic Ketoacidosis (DKA) is TRUE?",
    "options": [
      "High anion gap metabolic acidosis with low serum bicarbonate is typically present",
      "Insulin should be initiated immediately even if potassium is less than 3.3 mEq/L",
      "Standard urine dipstick tests predominantly detect beta-hydroxybutyrate",
      "Fluid resuscitation is contraindicated in adult DKA due to risk of cerebral edema"
    ],
    "answer": 0,
    "explanation": "DKA characteristically presents with a high anion gap metabolic acidosis. Urine test strips detect acetoacetate, not beta-hydroxybutyrate.",
    "difficulty": "Medium",
    "tags": "Endocrinology"
  },
  {
    "type": "MCQ",
    "question": "A 45-year-old male presents with severe crushing retrosternal chest pain radiating to his left jaw. ECG reveals ST-segment elevation in leads II, III, and aVF. Which coronary artery is most likely occluded?",
    "options": [
      "Right Coronary Artery (RCA)",
      "Left Anterior Descending (LAD)",
      "Left Circumflex (LCx)",
      "Left Main Coronary Artery"
    ],
    "answer": 0,
    "explanation": "Leads II, III, and aVF monitor the inferior wall of the left ventricle, which is supplied by the Right Coronary Artery in right-dominant circulation.",
    "difficulty": "Medium",
    "tags": "Cardiology"
  }
]
""".trimIndent()

data class MCQSampleTemplate(
    val title: String,
    val emoji: String,
    val description: String,
    val json: String
)

val SAMPLE_MCQ_TEMPLATES = listOf(
    MCQSampleTemplate(
        title = "All 6 Formats Combined",
        emoji = "🌟",
        description = "Contains 1 sample of each format (Assertion-Reason, Match Column, Statements, Flowchart, True/False, Standard)",
        json = """
[
  {
    "type": "ASSERTION_REASON",
    "question": "Given below are two statements: one is labelled as Assertion (A) and the other is labelled as Reason (R):",
    "assertion": "Myocardial infarction results in coagulative necrosis of cardiac myocytes.",
    "reason": "Severe ischemia causes denaturation of structural and enzymatic proteins, preventing immediate autolysis.",
    "options": [
      "Both Assertion and Reason are true, and Reason is the correct explanation of Assertion",
      "Both Assertion and Reason are true, but Reason is NOT the correct explanation of Assertion",
      "Assertion is true, but Reason is false",
      "Assertion is false, but Reason is true"
    ],
    "answer": 0,
    "explanation": "Ischemia produces intracellular acidosis and denatures cellular enzymes, blocking immediate proteolysis and preserving tissue architecture for several days.",
    "difficulty": "Hard",
    "tags": "Pathology"
  },
  {
    "type": "MATCH_THE_COLUMN",
    "question": "Match the organelle in Column I with its primary function in Column II:",
    "matchColumnA": [
      "A. Mitochondria",
      "B. Rough Endoplasmic Reticulum",
      "C. Lysosome",
      "D. Peroxisome"
    ],
    "matchColumnB": [
      "1. Secretory protein translation & folding",
      "2. ATP generation via oxidative phosphorylation",
      "3. Very long-chain fatty acid beta-oxidation",
      "4. Acid hydrolase digestion of cellular debris"
    ],
    "options": [
      "A-2, B-1, C-4, D-3",
      "A-1, B-2, C-3, D-4",
      "A-2, B-4, C-1, D-3",
      "A-3, B-1, C-4, D-2"
    ],
    "answer": 0,
    "explanation": "Mitochondria produce ATP (2), RER translates secretory proteins (1), lysosomes digest debris via acid hydrolases (4), peroxisomes oxidize VLCFAs (3).",
    "difficulty": "Medium",
    "tags": "Biochemistry"
  },
  {
    "type": "STATEMENT_BASED",
    "question": "Consider the following statements regarding Vitamin B12 deficiency:",
    "statements": [
      "Statement I: Vitamin B12 deficiency leads to subacute combined degeneration of the spinal cord.",
      "Statement II: Serum methylmalonic acid (MMA) levels are typically normal in isolated B12 deficiency.",
      "Statement III: Peripheral blood smear characteristically shows hypersegmented neutrophils."
    ],
    "options": [
      "Statements I and III only are correct",
      "Statement II only is correct",
      "Statements I and II only are correct",
      "Statements I, II and III are correct"
    ],
    "answer": 0,
    "explanation": "Serum methylmalonic acid (MMA) is elevated in B12 deficiency (not normal), distinguishing it from folate deficiency. Statements I and III are correct.",
    "difficulty": "Hard",
    "tags": "Hematology"
  },
  {
    "type": "FLOWCHART",
    "question": "Identify the missing enzyme (X) in the following catecholamine biosynthesis pathway:",
    "flowchartSteps": [
      "L-Tyrosine",
      "L-DOPA",
      "Dopamine",
      "Norepinephrine (via X)",
      "Epinephrine"
    ],
    "options": [
      "Dopamine beta-hydroxylase",
      "Tyrosine hydroxylase",
      "DOPA decarboxylase",
      "Phenylethanolamine N-methyltransferase"
    ],
    "answer": 0,
    "explanation": "Dopamine beta-hydroxylase converts Dopamine to Norepinephrine inside storage vesicles. PNMT then converts Norepinephrine to Epinephrine.",
    "difficulty": "Medium",
    "tags": "Pharmacology"
  },
  {
    "type": "TRUE_FALSE",
    "question": "Which of the following statements regarding Diabetic Ketoacidosis (DKA) is TRUE?",
    "options": [
      "High anion gap metabolic acidosis with low serum bicarbonate is typically present",
      "Insulin should be initiated immediately even if potassium is less than 3.3 mEq/L",
      "Standard urine dipstick tests predominantly detect beta-hydroxybutyrate",
      "Fluid resuscitation is contraindicated in adult DKA due to risk of cerebral edema"
    ],
    "answer": 0,
    "explanation": "DKA characteristically presents with a high anion gap metabolic acidosis. Standard urine test strips detect acetoacetate, not beta-hydroxybutyrate.",
    "difficulty": "Medium",
    "tags": "Endocrinology"
  },
  {
    "type": "MCQ",
    "question": "A 45-year-old male presents with severe crushing retrosternal chest pain radiating to his left jaw. ECG reveals ST-segment elevation in leads II, III, and aVF. Which coronary artery is most likely occluded?",
    "options": [
      "Right Coronary Artery (RCA)",
      "Left Anterior Descending (LAD)",
      "Left Circumflex (LCx)",
      "Left Main Coronary Artery"
    ],
    "answer": 0,
    "explanation": "Leads II, III, and aVF monitor the inferior myocardial wall, which is predominantly supplied by the Right Coronary Artery (RCA) in right-dominant circulation.",
    "difficulty": "Medium",
    "tags": "Cardiology"
  }
]
        """.trimIndent()
    ),
    MCQSampleTemplate(
        title = "Assertion & Reason Format",
        emoji = "⚖️",
        description = "Standard textbook Assertion (A) and Reason (R) statements with dark highlight styling",
        json = """
[
  {
    "type": "ASSERTION_REASON",
    "question": "Given below are two statements: one is labelled as Assertion (A) and the other is labelled as Reason (R):",
    "assertion": "Patients with hereditary spherocytosis commonly present with splenomegaly and hemolytic anemia.",
    "reason": "Mutations in ankyrin or spectrin reduce erythrocyte membrane surface area, making cells rigid and liable to splenic cord entrapment.",
    "options": [
      "Both Assertion and Reason are true, and Reason is the correct explanation of Assertion",
      "Both Assertion and Reason are true, but Reason is NOT the correct explanation of Assertion",
      "Assertion is true, but Reason is false",
      "Assertion is false, but Reason is true"
    ],
    "answer": 0,
    "explanation": "Cytoskeleton defects in ankyrin/spectrin cause loss of membrane surface area to volume ratio, resulting in spherical rigid cells that are sequestered and lysed in the spleen.",
    "difficulty": "Hard",
    "tags": "Hematology"
  }
]
        """.trimIndent()
    ),
    MCQSampleTemplate(
        title = "Match the Column Format",
        emoji = "🔀",
        description = "Two structured columns (Column I and Column II) side-by-side with combination codes",
        json = """
[
  {
    "type": "MATCH_THE_COLUMN",
    "question": "Match the antimicrobial agent in Column I with its hallmark mechanism of action in Column II:",
    "matchColumnA": [
      "A. Vancomycin",
      "B. Gentamicin",
      "C. Ciprofloxacin",
      "D. Rifampin"
    ],
    "matchColumnB": [
      "1. Bacterial DNA gyrase (topoisomerase II) inhibition",
      "2. D-Ala-D-Ala cell wall synthesis inhibition",
      "3. 30S ribosomal subunit misreading",
      "4. DNA-dependent RNA polymerase inhibition"
    ],
    "options": [
      "A-2, B-3, C-1, D-4",
      "A-3, B-2, C-1, D-4",
      "A-2, B-1, C-3, D-4",
      "A-4, B-3, C-1, D-2"
    ],
    "answer": 0,
    "explanation": "Vancomycin binds D-Ala-D-Ala (2), Gentamicin binds 30S (3), Ciprofloxacin inhibits DNA gyrase (1), Rifampin inhibits RNA polymerase (4).",
    "difficulty": "Medium",
    "tags": "Pharmacology"
  }
]
        """.trimIndent()
    ),
    MCQSampleTemplate(
        title = "Statement Based Format",
        emoji = "📝",
        description = "Multiple numbered/roman statements (Statement I, II, III) with combination answer keys",
        json = """
[
  {
    "type": "STATEMENT_BASED",
    "question": "Regarding Nephrotic Syndrome in pediatric patients, consider the following statements:",
    "statements": [
      "Statement I: Minimal Change Disease is the most common cause in children aged 2 to 6 years.",
      "Statement II: Massive proteinuria (> 3.5 g/24h or > 40 mg/m²/h) with severe hypoalbuminemia is a hallmark diagnostic feature.",
      "Statement III: Gross hematuria and severe hypertension are universally present in all patients with Minimal Change Disease."
    ],
    "options": [
      "Statements I and II only are correct",
      "Statement III only is correct",
      "Statements I, II and III are correct",
      "Statement I only is correct"
    ],
    "answer": 0,
    "explanation": "Minimal Change Disease presents with selective proteinuria and edema, but gross hematuria and hypertension are rare and suggest nephritic etiologies.",
    "difficulty": "Medium",
    "tags": "Pediatrics"
  }
]
        """.trimIndent()
    ),
    MCQSampleTemplate(
        title = "Flowchart Pathway Format",
        emoji = "🔄",
        description = "Sequential biochemical or clinical pathway steps with arrows",
        json = """
[
  {
    "type": "FLOWCHART",
    "question": "Identify the missing intermediate step (X) in the urea cycle sequence:",
    "flowchartSteps": [
      "Ornithine + Carbamoyl phosphate",
      "Citrulline (via OTC)",
      "Argininosuccinate (via X)",
      "Arginine + Fumarate",
      "Urea + Ornithine"
    ],
    "options": [
      "Addition of Aspartate via Argininosuccinate synthetase",
      "Hydrolysis via Arginase",
      "Carbamoyl phosphate synthetase I activation",
      "Glutamate dehydrogenase transamination"
    ],
    "answer": 0,
    "explanation": "Citrulline condenses with Aspartate to form Argininosuccinate catalyzed by Argininosuccinate synthetase in the cytosol.",
    "difficulty": "Hard",
    "tags": "Biochemistry"
  }
]
        """.trimIndent()
    ),
    MCQSampleTemplate(
        title = "Standard Single Best Answer",
        emoji = "🎯",
        description = "High-yield clinical vignette or direct factual question with options A, B, C, D",
        json = """
[
  {
    "type": "MCQ",
    "question": "A 28-year-old female presents with tremors, heat intolerance, weight loss, and diffuse thyroid enlargement with an audible bruit. Laboratory studies show suppressed TSH and elevated free T4. Which autoantibody is most specific for this condition?",
    "options": [
      "Thyroid Stimulating Hormone Receptor Antibodies (TRAb / TSI)",
      "Anti-Thyroid Peroxidase (Anti-TPO) Antibodies",
      "Anti-Thyroglobulin (Anti-Tg) Antibodies",
      "Thyroid Hormone Binding Globulin Antibodies"
    ],
    "answer": 0,
    "explanation": "Graves disease is caused by autoantibodies directed against the TSH receptor (TRAb/TSI) that chronically stimulate thyroid follicular cells.",
    "difficulty": "Medium",
    "tags": "Endocrinology"
  }
]
        """.trimIndent()
    )
)

/**
 * Reusable Card providing ChatGPT Prompt Copy & Sample JSON Format Switcher
 */
@Composable
fun MCQChatGPTAndSamplesCard(
    onLoadJson: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTemplateIndex by remember { mutableIntStateOf(0) }
    var isPromptExpanded by remember { mutableStateOf(false) }

    val activeTemplate = SAMPLE_MCQ_TEMPLATES[selectedTemplateIndex]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🤖", fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ChatGPT Prompt & Sample JSON",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Copy prompt for ChatGPT or load format templates",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Top Buttons: Copy ChatGPT Prompt & Toggle Prompt View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(CHATGPT_MCQ_PROMPT))
                        Toast.makeText(context, "✓ ChatGPT Prompt copied! Paste in ChatGPT 🚀", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy ChatGPT Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { isPromptExpanded = !isPromptExpanded },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(if (isPromptExpanded) "Hide Prompt" else "View Prompt", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isPromptExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expandable ChatGPT Prompt Box
            AnimatedVisibility(visible = isPromptExpanded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📋 ChatGPT Prompt Template", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(CHATGPT_MCQ_PROMPT))
                                    Toast.makeText(context, "✓ Copied prompt!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Copy 📋", color = Color(0xFF38BDF8), fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CHATGPT_MCQ_PROMPT,
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                            maxLines = 14
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Color(0xFFE2E8F0))

            // Format Selection Chips Header
            Text(
                text = "📚 1-Tap Sample Formats (Assertion, Column, Statements, Flowchart):",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF334155)
            )

            // Horizontal Scrollable Format Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SAMPLE_MCQ_TEMPLATES.forEachIndexed { index, template ->
                    FilterChip(
                        selected = selectedTemplateIndex == index,
                        onClick = { selectedTemplateIndex = index },
                        label = {
                            Text(
                                text = "${template.emoji} ${template.title}",
                                fontSize = 11.5.sp,
                                fontWeight = if (selectedTemplateIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E293B),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Description of active format
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeTemplate.emoji} ${activeTemplate.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = activeTemplate.description,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Load into Input Button
                        FilledTonalButton(
                            onClick = {
                                onLoadJson(activeTemplate.json)
                                Toast.makeText(context, "✓ Loaded '${activeTemplate.title}' sample into input!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Load ⬇️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Copy JSON Button
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(activeTemplate.json))
                                Toast.makeText(context, "✓ Copied '${activeTemplate.title}' JSON!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
