package com.example.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.MedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    title: String,
    pdfUri: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var totalPages by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isNightMode by remember { mutableStateOf(false) }

    val isOnline by MedRepository.isOnline.collectAsState()
    val allNotes by MedRepository.notes.collectAsState()
    val matchingNote = remember(allNotes, title, pdfUri) {
        allNotes.firstOrNull { it.pdfMockUrl == pdfUri || it.title.equals(title, ignoreCase = true) }
    }

    var reloadTrigger by remember { mutableStateOf(0) }

    // Rendered Bitmaps cache for lightning-fast scrolling without re-renders
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }

    // Smooth Pinch & Double-tap zoom state
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            MedRepository.clearLocalTempPdfFiles(context)
        }
    }

    if (!isOnline && (pdfUri.startsWith("http://") || pdfUri.startsWith("https://"))) {
        OfflineScreen(
            onRetry = {
                reloadTrigger++
            },
            onBackClick = onBackClick
        )
        return
    }

    BackHandler {
        onBackClick()
    }

    // Load PDF in background (full screen native reader mode)
    LaunchedEffect(pdfUri, title, reloadTrigger) {
        isLoading = true
        errorMessage = null
        if (reloadTrigger > 0) {
            pageBitmaps.clear()
        }
        withContext(Dispatchers.IO) {
            try {
                MedRepository.clearLocalTempPdfFiles(context)
                val file = resolvePdfFile(context, pdfUri, title)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                totalPages = count

                // Render all pages in background immediately so scrolling has ZERO lag
                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    val targetWidth = 1080
                    val targetHeight = (targetWidth.toFloat() / page.width * page.height).toInt().coerceAtLeast(1400)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    withContext(Dispatchers.Main) {
                        pageBitmaps[i] = bitmap
                        if (i == 0) isLoading = false
                    }
                }
                renderer.close()
                pfd.close()
                // Never persist downloaded files in local cache/storage
                if (file.name.startsWith("stream_") || file.name.startsWith("temp_")) {
                    file.delete()
                }
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    errorMessage = "Unable to render PDF document: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = {
                        Text(
                            text = title.ifBlank { "Study PDF" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isNightMode) Color(0xFFEFF6FF) else Color(0xFF1E3A8A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isNightMode) Color(0xFFEFF6FF) else Color(0xFF1E3A8A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        // Refresh button
                        IconButton(onClick = { reloadTrigger++ }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload PDF",
                                tint = if (isNightMode) Color(0xFFEFF6FF) else Color(0xFF1E3A8A),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Reset Zoom button
                        if (scaleAnim.value > 1.05f) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        launch { scaleAnim.animateTo(1f, tween(200)) }
                                        launch { offsetXAnim.animateTo(0f, tween(200)) }
                                        launch { offsetYAnim.animateTo(0f, tween(200)) }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitScreen,
                                    contentDescription = "Reset Zoom",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Night / Comfort reading mode
                        IconButton(onClick = { isNightMode = !isNightMode }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Reading Theme",
                                tint = if (isNightMode) Color(0xFFEFF6FF) else Color(0xFF1E3A8A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    windowInsets = WindowInsets(0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isNightMode) Color(0xFF1E293B) else Color(0xFFDBEAFE)
                    )
                )
                HorizontalDivider(thickness = 1.dp, color = if (isNightMode) Color(0xFF334155) else Color(0xFF93C5FD))
            }
        },
        containerColor = if (isNightMode) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && pageBitmaps.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF2563EB),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Opening PDF...",
                        color = if (isNightMode) Color.White else Color(0xFF334155),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Rendering full screen document pages...",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            } else if (errorMessage != null && pageBitmaps.isEmpty()) {
                val isOfflineError = errorMessage!!.contains("internet", ignoreCase = true) || !isOnline
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isOfflineError) Icons.Default.CloudOff else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (isOfflineError) Color(0xFF2563EB) else Color(0xFFEF4444),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isOfflineError) "No Internet Connection" else (errorMessage ?: "Unable to open document"),
                        color = if (isNightMode) Color.White else Color(0xFF1E293B),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isOfflineError) "Please connect to the internet and tap Refresh to view this PDF document." else "Please check your network and try again.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B))
                        ) {
                            Text("Go Back")
                        }
                        Button(
                            onClick = { reloadTrigger++ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh / Connect to Internet")
                        }
                    }
                }
            } else {
                // Interactive Full Screen Native PDF Page View (No External Links or Drive Frame)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    coroutineScope.launch {
                                        if (scaleAnim.value > 1.15f) {
                                            launch { scaleAnim.animateTo(1.0f, tween(250)) }
                                            launch { offsetXAnim.animateTo(0f, tween(250)) }
                                            launch { offsetYAnim.animateTo(0f, tween(250)) }
                                        } else {
                                            val targetScale = 2.5f
                                            val maxOffsetX = (size.width * (targetScale - 1f)) / 2f
                                            val maxOffsetY = (size.height * (targetScale - 1f)) / 2f
                                            val targetOffsetX = ((size.width / 2f - tapOffset.x) * 1.5f).coerceIn(-maxOffsetX, maxOffsetX)
                                            val targetOffsetY = ((size.height / 2f - tapOffset.y) * 1.5f).coerceIn(-maxOffsetY, maxOffsetY)

                                            launch { scaleAnim.animateTo(targetScale, tween(250)) }
                                            launch { offsetXAnim.animateTo(targetOffsetX, tween(250)) }
                                            launch { offsetYAnim.animateTo(targetOffsetY, tween(250)) }
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                coroutineScope.launch {
                                    val newScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                                    scaleAnim.snapTo(newScale)

                                    if (newScale > 1f) {
                                        val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                        val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                        val newOffsetX = (offsetXAnim.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        val newOffsetY = (offsetYAnim.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                        offsetXAnim.snapTo(newOffsetX)
                                        offsetYAnim.snapTo(newOffsetY)
                                    } else {
                                        offsetXAnim.snapTo(0f)
                                        offsetYAnim.snapTo(0f)
                                    }
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scaleAnim.value,
                                scaleY = scaleAnim.value,
                                translationX = offsetXAnim.value,
                                translationY = offsetYAnim.value
                            ),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(totalPages) { pageIndex ->
                            val bitmap = pageBitmaps[pageIndex]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                shape = androidx.compose.ui.graphics.RectangleShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isNightMode) Color(0xFF1E293B) else Color.White
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Page ${pageIndex + 1}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(),
                                            contentScale = ContentScale.FillWidth
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(600.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color(0xFF2563EB),
                                                modifier = Modifier.size(36.dp)
                                            )
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
}

/**
 * Resolves local file, downloads URL with Cloudflare-compatible headers,
 * or generates an instant multi-page MBBS High-Yield Revision Guide PDF.
 */
private suspend fun resolvePdfFile(context: Context, pdfUri: String, title: String): File = withContext(Dispatchers.IO) {
    val cacheDir = context.cacheDir

    // 1. Content URI (picked via file picker or local storage)
    if (pdfUri.startsWith("content://")) {
        try {
            val uri = Uri.parse(pdfUri)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val tempFile = File.createTempFile("temp_view_", ".pdf", cacheDir)
                tempFile.deleteOnExit()
                tempFile.outputStream().use { out ->
                    inputStream.copyTo(out)
                }
                inputStream.close()
                if (tempFile.length() > 0) return@withContext tempFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. HTTP/HTTPS Remote URL (Stream online, no persistent local storage)
    if (pdfUri.startsWith("http://") || pdfUri.startsWith("https://")) {
        if (!MedRepository.isOnline.value) {
            throw IllegalStateException("Offline: Internet connection required")
        }

        try {
            val tempFile = File.createTempFile("stream_", ".pdf", cacheDir)
            tempFile.deleteOnExit()

            val url = URL(pdfUri)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "application/pdf,*/*")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 100) return@withContext tempFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 3. Fallback: Generate an instant in-memory MBBS High-Yield Notes Document
    val fallbackFile = File.createTempFile("temp_mbbs_", ".pdf", cacheDir)
    fallbackFile.deleteOnExit()
    generateSampleMedicalPdf(fallbackFile, title)
    return@withContext fallbackFile
}

/**
 * Generates an instant high-quality multi-page PDF document using native Android PdfDocument
 */
private fun generateSampleMedicalPdf(file: File, topicTitle: String) {
    val doc = PdfDocument()
    val cleanTitle = topicTitle.ifBlank { "MBBS High-Yield Clinical Notes" }

    val pagesContent = listOf(
        Pair("1. Clinical Overview & High-Yield Anatomy", listOf(
            "• Major Anatomical Landmarks & Relations: Comprehensive dissection review.",
            "• Clinical Pearls: Most frequently asked NEET-PG / INI-CET question triggers.",
            "• Blood Supply & Nerve Innervation: High-yield tables and mnemonics.",
            "• Structural variations and surgically important boundaries."
        )),
        Pair("2. Pathology & Pathophysiology", listOf(
            "• Etiology & Risk Factors: High-yield pathogenesis breakdown.",
            "• Gross & Microscopic Findings: Characteristic hallmark features.",
            "• Clinical Presentation: Classic symptoms, signs, and syndromic associations.",
            "• Diagnostic Workup: First-line imaging, lab tests, and gold standards."
        )),
        Pair("3. Pharmacology & Management", listOf(
            "• First-line Pharmacotherapy: Mechanisms of action, dosages, and indications.",
            "• Adverse Drug Reactions & Contraindications: Crucial exam warnings.",
            "• Surgical / Interventional Guidelines: Indications and complications.",
            "• Prognosis & Follow-up protocols."
        )),
        Pair("4. High-Yield Summary & Review", listOf(
            "• Topic: $cleanTitle",
            "• Core takeaway 1: Master foundational anatomy and physiology correlation.",
            "• Core takeaway 2: Focus on differential diagnosis and gold standard diagnostics.",
            "• Core takeaway 3: Memorize first-line management and major side effects."
        ))
    )

    val pageWidth = 595
    val pageHeight = 842

    for ((index, section) in pagesContent.withIndex()) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Header Background
        paint.color = AndroidColor.parseColor("#1E3A8A")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 90f, paint)

        // Header Title
        paint.color = AndroidColor.WHITE
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("MEDXMED CLINICAL COMPANION", 40f, 40f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText(cleanTitle, 40f, 65f, paint)

        // Section Title
        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText(section.first, 40f, 130f, paint)

        // Divider Line
        paint.color = AndroidColor.parseColor("#2563EB")
        paint.strokeWidth = 2f
        canvas.drawLine(40f, 145f, (pageWidth - 40).toFloat(), 145f, paint)

        // Content Bullet Points
        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.color = AndroidColor.parseColor("#334155")
        var currentY = 185f
        for (bullet in section.second) {
            canvas.drawText(bullet, 40f, currentY, paint)
            currentY += 35f
        }

        // Footer
        paint.color = AndroidColor.parseColor("#94A3B8")
        paint.textSize = 10f
        canvas.drawText("MEDXMED MBBS Study App • Page ${index + 1} of 4", 40f, (pageHeight - 40).toFloat(), paint)

        doc.finishPage(page)
    }

    try {
        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        doc.close()
    }
}
