package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MedRepository
import com.example.data.PomodoroManager
import com.example.model.ContentFolder
import com.example.model.CustomSection
import com.example.model.ProfYear
import com.example.model.Subject
import com.example.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.util.isYouTubeUrl
import kotlin.math.roundToInt

sealed interface Screen {
    object Splash : Screen
    object Home : Screen
    data class ProfDetail(val prof: ProfYear) : Screen
    data class SubjectDetail(val subject: Subject) : Screen
    object Flashcards : Screen
    object PYQ : Screen
    object MCQ : Screen
    data class MCQPlayer(
        val folderId: String,
        val chapterName: String,
        val subfolderName: String = ""
    ) : Screen
    object Shorts : Screen
    object Pomodoro : Screen
    object Profile : Screen
    object Admin : Screen
    data class CustomSectionDetail(val section: CustomSection) : Screen
    data class MCQSectionHierarchy(val folder: ContentFolder) : Screen
    data class PdfViewer(val title: String, val pdfUri: String) : Screen
    data class VideoPlayer(
        val title: String,
        val videoUrl: String,
        val chapterName: String = "",
        val subjectName: String = "",
        val folderId: String = ""
    ) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots, screen recording, and external capture to prevent data leaks
        com.example.util.SecurityUtils.applyScreenshotProtection(this)

        MedRepository.initialize(applicationContext)
        PomodoroManager.initialize(applicationContext)
        
        // Network Monitoring for Offline/Online logic
        MedRepository.checkNetworkOnline(applicationContext)
        MedRepository.clearLocalTempPdfFiles(applicationContext)
        try {
            val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkRequest = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    MedRepository.updateOnlineStatus(true)
                }
                override fun onLost(network: android.net.Network) {
                    MedRepository.updateOnlineStatus(false)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to register network callback", e)
        }

        // Optimize Coil ImageLoader for memory and bitmap performance
        try {
            val imageLoader = coil.ImageLoader.Builder(this)
                .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                .crossfade(true)
                .respectCacheHeaders(false)
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to configure Coil image loader", e)
        }

        enableEdgeToEdge()
        
        // Auto-check for Play Store app update on startup
        com.example.util.AppUpdateHelper.checkForAppUpdate(this)

        setContent {
            val currentTheme by MedRepository.currentTheme.collectAsState()
            MyApplicationTheme(themeIndex = currentTheme) {
                MedXMedApp()
            }
        }
    }
}

@Composable
fun MedXMedApp() {
    val screenStack = remember { mutableStateListOf<Screen>(Screen.Splash) }
    val currentScreen = screenStack.lastOrNull() ?: Screen.Home

    val currentContext = androidx.compose.ui.platform.LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (currentContext is android.app.Activity) {
            com.example.util.AppUpdateHelper.checkForAppUpdate(currentContext) {
                showUpdateDialog = true
            }
        }
    }

    com.example.util.AppUpdateBlockingDialog(
        showDialog = showUpdateDialog,
        onUpdateClick = {
            if (currentContext is android.app.Activity) {
                com.example.util.AppUpdateHelper.checkForAppUpdate(currentContext) {}
            }
        }
    )

    val isPomodoroRunning by PomodoroManager.isRunning.collectAsState()
    val pomoTimeLeft by PomodoroManager.timeLeftSeconds.collectAsState()
    var isFloatingTimerDismissed by remember { mutableStateOf(false) }

    // Reset dismissed state whenever user navigates into Pomodoro
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.Pomodoro) {
            isFloatingTimerDismissed = false
        }
    }

    // Floating Timer widget draggable offsets (Anchored at bottom center)
    var floatingOffsetX by remember { mutableFloatStateOf(0f) }
    var floatingOffsetY by remember { mutableFloatStateOf(0f) }

    // Back handling
    BackHandler(enabled = screenStack.size > 1) {
        screenStack.removeAt(screenStack.lastIndex)
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler(enabled = screenStack.size == 1) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun navigateTo(screen: Screen) {
        if (screen == Screen.Home) {
            screenStack.clear()
            screenStack.add(Screen.Home)
        } else {
            screenStack.add(screen)
        }
    }

    fun navigateReplace(screen: Screen) {
        screenStack.clear()
        screenStack.add(screen)
    }

    if (currentScreen == Screen.Splash) {
        SplashScreen(
            onSplashFinished = {
                navigateReplace(Screen.Home)
            }
        )
        return
    }

    // Determine bottom nav active item
    val isHomeActive = currentScreen == Screen.Home
    val isShortsActive = currentScreen == Screen.Shorts
    val isPomodoroActive = currentScreen == Screen.Pomodoro
    // Shorts and Pomodoro open in complete dedicated interfaces without bottom nav interfering!
    val showBottomBar = currentScreen == Screen.Home

    val handleVideoClick: (String, String, String, String, String) -> Unit = { title, url, ch, sub, fId ->
        if (isYouTubeUrl(url)) {
            YouTubePlayerActivity.launch(context, url, title)
        } else {
            navigateTo(Screen.VideoPlayer(title, url, ch, sub, fId))
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 16.dp, ambientColor = Color(0x10000000), spotColor = Color(0x15000000))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Crisp Footer Top Line/Divider
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFF94A3B8)
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            // Home Nav Button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (!isHomeActive) navigateReplace(Screen.Home)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHomeActive) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home",
                                    tint = if (isHomeActive) Color(0xFF2563EB) else Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Home",
                                    fontSize = 11.sp,
                                    fontWeight = if (isHomeActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isHomeActive) Color(0xFF2563EB) else Color(0xFF64748B)
                                )
                            }

                            // SHORTS Highlight Pill Button
                            Surface(
                                onClick = {
                                    if (!isShortsActive) navigateTo(Screen.Shorts)
                                },
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFFFF4D6D),
                                shadowElevation = 4.dp,
                                modifier = Modifier.height(44.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlashOn,
                                        contentDescription = "Shorts",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shorts",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Pomodoro Nav Button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        navigateTo(Screen.Pomodoro)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPomodoroActive) Icons.Filled.Timer else Icons.Outlined.Timer,
                                    contentDescription = "Pomodoro",
                                    tint = if (isPomodoroActive) Color(0xFF2563EB) else Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Pomodoro",
                                    fontSize = 11.sp,
                                    fontWeight = if (isPomodoroActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPomodoroActive) Color(0xFF2563EB) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
                }
            }
        },
        containerColor = if (currentScreen == Screen.Pomodoro || currentScreen is Screen.VideoPlayer || currentScreen == Screen.Shorts) Color.Black else MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val effectivePadding = if (currentScreen == Screen.Pomodoro || currentScreen is Screen.VideoPlayer || currentScreen == Screen.Shorts) {
            PaddingValues(0.dp)
        } else {
            paddingValues
        }
        val isOnline by MedRepository.isOnline.collectAsState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(effectivePadding)
        ) {
            if (!isOnline && currentScreen != Screen.Home && currentScreen != Screen.Pomodoro && currentScreen != Screen.Splash) {
                OfflineScreen(
                    onRetry = {
                        MedRepository.checkNetworkOnline(context)
                    },
                    onBackClick = {
                        if (screenStack.size > 1) screenStack.removeAt(screenStack.lastIndex)
                        else navigateReplace(Screen.Home)
                    }
                )
            } else {
                when (val screen = currentScreen) {
                    Screen.Splash -> {
                        /* handled above */
                    }
                    Screen.Home -> {
                        HomeScreen(
                            onProfClick = { prof -> navigateTo(Screen.ProfDetail(prof)) },
                            onSubjectClick = { sub -> navigateTo(Screen.SubjectDetail(sub)) },
                            onCustomSectionClick = { sec -> navigateTo(Screen.CustomSectionDetail(sec)) },
                            onShortsClick = { navigateTo(Screen.Shorts) },
                            onFlashcardsClick = { navigateTo(Screen.Flashcards) },
                            onPYQClick = { navigateTo(Screen.PYQ) },
                            onMCQClick = { navigateTo(Screen.MCQ) },
                            onProfileClick = { navigateTo(Screen.Profile) },
                            onPdfClick = { title, uri -> navigateTo(Screen.PdfViewer(title, uri)) },
                            onVideoClick = { title, url, ch, sub -> handleVideoClick(title, url, ch, sub, "") }
                        )
                    }
                    is Screen.ProfDetail -> {
                        ProfDetailScreen(
                            profYear = screen.prof,
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) },
                            onSubjectClick = { sub -> navigateTo(Screen.SubjectDetail(sub)) }
                        )
                    }
                    is Screen.SubjectDetail -> {
                        SubjectDetailScreen(
                            subject = screen.subject,
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) },
                            onPdfClick = { title, uri -> navigateTo(Screen.PdfViewer(title, uri)) },
                            onVideoClick = { title, url, ch, sub, fId -> handleVideoClick(title, url, ch, sub, fId) }
                        )
                    }
                    Screen.Flashcards -> {
                        FlashcardsScreen(
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) }
                        )
                    }
                    Screen.PYQ -> {
                        PYQScreen(
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) },
                            onPdfClick = { title, uri -> navigateTo(Screen.PdfViewer(title, uri)) }
                        )
                    }
                    Screen.MCQ -> {
                        MCQScreen(
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) },
                            onOpenChapter = { fId, ch, sub -> navigateTo(Screen.MCQPlayer(fId, ch, sub)) }
                        )
                    }
                    is Screen.MCQPlayer -> {
                        MCQPlayerScreen(
                            folderId = screen.folderId,
                            chapterName = screen.chapterName,
                            subfolderName = screen.subfolderName,
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) }
                        )
                    }
                    Screen.Shorts -> {
                        ShortsScreen(
                            onBackClick = {
                                if (screenStack.size > 1) screenStack.removeAt(screenStack.lastIndex)
                                else navigateReplace(Screen.Home)
                            }
                        )
                    }
                    Screen.Pomodoro -> {
                        PomodoroScreen(
                            onBackClick = {
                                if (screenStack.size > 1) screenStack.removeAt(screenStack.lastIndex)
                                else navigateReplace(Screen.Home)
                            }
                        )
                    }
                    Screen.Profile -> {
                        ProfileScreen(
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) },
                            onOpenAdminSection = { navigateTo(Screen.Admin) }
                        )
                    }
                    Screen.Admin -> {
                        AdminScreen(
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) },
                            navigateTo = ::navigateTo
                        )
                    }
                    is Screen.CustomSectionDetail -> {
                        CustomSectionDetailScreen(
                            section = screen.section,
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) },
                            onPdfClick = { title, uri -> navigateTo(Screen.PdfViewer(title, uri)) },
                            onVideoClick = { title, url, ch, sub, fId -> handleVideoClick(title, url, ch, sub, fId) }
                        )
                    }
                    is Screen.MCQSectionHierarchy -> {
                        MCQSectionHierarchyScreen(
                            folder = screen.folder,
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) }
                        )
                    }
                    is Screen.PdfViewer -> {
                        PdfViewerScreen(
                            title = screen.title,
                            pdfUri = screen.pdfUri,
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) }
                        )
                    }
                    is Screen.VideoPlayer -> {
                        VideoPlayerScreen(
                            title = screen.title,
                            videoUrl = screen.videoUrl,
                            chapterName = screen.chapterName,
                            subjectName = screen.subjectName,
                            folderId = screen.folderId,
                            onBackClick = { screenStack.removeAt(screenStack.lastIndex) }
                        )
                    }
                }
            }

            // Global Draggable Floating Timer Widget anchored at bottom center
            if (isPomodoroRunning && currentScreen != Screen.Pomodoro && !isFloatingTimerDismissed) {
                val pomoMins = pomoTimeLeft / 60
                val pomoSecs = pomoTimeLeft % 60
                val pomoTimeStr = String.format("%02d:%02d", pomoMins, pomoSecs)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (showBottomBar) 78.dp else 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(floatingOffsetX.roundToInt(), floatingOffsetY.roundToInt()) }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    floatingOffsetX = (floatingOffsetX + dragAmount.x).coerceIn(-280f, 280f)
                                    floatingOffsetY = (floatingOffsetY + dragAmount.y).coerceIn(-1200f, 60f)
                                }
                            }
                            .shadow(12.dp, RoundedCornerShape(26.dp), spotColor = Color(0x66000000))
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xF20F172A))
                            .border(1.2.dp, Color(0xFF00E5FF), RoundedCornerShape(26.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left: Pause / Play Circular Button
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                                    .clickable { PomodoroManager.toggleTimer() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPomodoroRunning) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
                                    contentDescription = if (isPomodoroRunning) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Center: Digital Countdown Time (Tapping navigates to Pomodoro screen)
                            Text(
                                text = pomoTimeStr,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier
                                    .clickable { navigateTo(Screen.Pomodoro) }
                                    .padding(horizontal = 2.dp)
                            )

                            // Right: Cut / Close (✕) button
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { isFloatingTimerDismissed = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Close floating timer",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
