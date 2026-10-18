package com.example.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.YouTubePlayerActivity
import com.example.data.MedRepository
import com.example.model.StudyContentItem
import com.example.model.StudyContentType
import com.example.util.extractYouTubeVideoId
import com.example.util.isYouTubeUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    title: String,
    videoUrl: String,
    chapterName: String = "",
    subjectName: String = "",
    folderId: String = "",
    onBackClick: () -> Unit
) {
    // Current active video playing states
    var activeTitle by remember(title) { mutableStateOf(title) }
    var activeUrl by remember(videoUrl) { mutableStateOf(videoUrl) }
    var activeChapter by remember(chapterName) { mutableStateOf(chapterName) }
    var activeSubject by remember(subjectName) { mutableStateOf(subjectName) }
    var activeFolderId by remember(folderId) { mutableStateOf(folderId) }

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(activeUrl) {
        if (isYouTubeUrl(activeUrl)) {
            YouTubePlayerActivity.launch(context, activeUrl, activeTitle)
            onBackClick()
        }
    }

    var isFullscreen by remember { mutableStateOf(false) }

    // Manage orientation and system UI for fullscreen landscape
    LaunchedEffect(isFullscreen) {
        val window = activity?.window
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBackClick()
        }
    }

    // Load all contents and find other videos in the same folder/chapter/subject
    val allContents by MedRepository.contents.collectAsState()
    val allFolders by MedRepository.folders.collectAsState()

    val currentFolderName = remember(allFolders, activeFolderId) {
        allFolders.firstOrNull { it.id == activeFolderId }?.name ?: ""
    }

    val relatedVideos = remember(allContents, activeFolderId, activeChapter, activeSubject, activeUrl) {
        val allVideos = allContents.filter { it.type == StudyContentType.VIDEO }
        val matching = if (activeFolderId.isNotBlank()) {
            allVideos.filter { it.folderId == activeFolderId }
        } else if (activeChapter.isNotBlank()) {
            allVideos.filter { it.chapterName.equals(activeChapter, ignoreCase = true) }
        } else if (activeSubject.isNotBlank()) {
            allVideos.filter { it.subjectId.equals(activeSubject, ignoreCase = true) }
        } else {
            allVideos
        }
        if (matching.isNotEmpty()) matching else allVideos
    }

    val nextVideos = remember(relatedVideos, activeUrl, activeTitle) {
        relatedVideos.filter { it.mediaUrl != activeUrl && it.title != activeTitle }
    }

    Scaffold(
        containerColor = if (isFullscreen) Color.Black else Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // HEADER - Only visible when not in fullscreen
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "🎥", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeChapter.isNotBlank()) activeChapter else if (currentFolderName.isNotBlank()) currentFolderName else "Video Lecture",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // VIDEO PLAYER SECTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFullscreen) Modifier.fillMaxSize()
                        else Modifier.aspectRatio(16f / 9f)
                    )
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                DirectVideoInAppPlayerView(
                    videoUrl = activeUrl,
                    modifier = Modifier.fillMaxSize()
                )

                // WATERMARK OVERLAY
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (isFullscreen) 24.dp else 12.dp, end = 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "MedXMed",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Floating Fullscreen Toggle Button
                IconButton(
                    onClick = { isFullscreen = !isFullscreen },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen Landscape",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // DETAILS & NEXT VIDEOS LIST (BELOW PLAYER)
            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = activeTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (activeSubject.isNotBlank()) activeSubject else "Medical Lecture",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (activeChapter.isNotBlank()) {
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "📁 $activeChapter",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (nextVideos.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🎬 Next in this Folder (${nextVideos.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Tap to play",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        items(nextVideos, key = { it.id }) { videoItem ->
                            VideoThumbnailCard(
                                item = videoItem,
                                subjectName = activeSubject,
                                isPlaying = false,
                                onClick = {
                                    if (isYouTubeUrl(videoItem.mediaUrl)) {
                                        YouTubePlayerActivity.launch(context, videoItem.mediaUrl, videoItem.title)
                                    } else {
                                        activeTitle = videoItem.title
                                        activeUrl = videoItem.mediaUrl
                                        activeChapter = videoItem.chapterName
                                        activeFolderId = videoItem.folderId
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * In-App YouTube Player View loading official YouTube watch page directly
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayerView(
    videoId: String,
    isFullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        key(videoId) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                if (url.contains("youtube.com") || url.contains("googlevideo.com") || url.contains("ytimg.com") || url.contains("youtu.be")) {
                                    return false
                                }
                                return true
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                val target = url.orEmpty()
                                if (target.contains("youtube.com") || target.contains("googlevideo.com") || target.contains("ytimg.com") || target.contains("youtu.be")) {
                                    return false
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val hideScript = """
                                    (function() {
                                        var style = document.createElement('style');
                                        style.innerHTML = `
                                            ytm-mobile-topbar-renderer,
                                            .mobile-topbar-header,
                                            #masthead-container,
                                            header,
                                            .watch-card-header,
                                            ytd-appbar,
                                            ytm-appbar,
                                            .sign-in-container,
                                            [aria-label="Sign in"],
                                            .unauth-header,
                                            c3-panel,
                                            ytm-companion-slot-renderer,
                                            div[class*="masthead"],
                                            div[class*="topbar"],
                                            div[class*="sign-in"] {
                                                display: none !important;
                                                visibility: hidden !important;
                                                height: 0 !important;
                                            }
                                        `;
                                        document.head.appendChild(style);

                                        setInterval(function() {
                                            var banners = document.querySelectorAll('ytm-mobile-topbar-renderer, .mobile-topbar-header, #masthead-container, header');
                                            banners.forEach(function(b) { b.style.display = 'none'; });
                                        }, 500);
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(hideScript, null)
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                try {
                                    (view?.parent as? ViewGroup)?.removeView(view)
                                    view?.destroy()
                                } catch (_: Exception) {}
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                onToggleFullscreen(true)
                            }

                            override fun onHideCustomView() {
                                onToggleFullscreen(false)
                            }
                        }

                        loadUrl("https://www.youtube.com/watch?v=$videoId")
                    }
                },
                update = { webView ->
                    // No-op
                },
                onRelease = { webView ->
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Reusable Video Thumbnail Card:
 * Displays 16:9 Thumbnail Image with Center Play Button (▶) and Video Title & Badges
 */
@Composable
fun VideoThumbnailCard(
    item: StudyContentItem,
    subjectName: String = "",
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ytId = remember(item.mediaUrl) { extractYouTubeVideoId(item.mediaUrl) }
    val thumbUrl = remember(ytId) {
        if (!ytId.isNullOrBlank()) "https://img.youtube.com/vi/$ytId/hqdefault.jpg"
        else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) Color(0xFFEFF6FF) else Color.White
        ),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.5f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎥", fontSize = 42.sp)
                    }
                }

                // CENTER PLAY BUTTON
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isPlaying) Color(0xFF2563EB) else Color(0xCC0F172A), CircleShape)
                        .border(2.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // TOP-LEFT BADGE
                Surface(
                    color = if (isPlaying) Color(0xFF16A34A) else Color(0xDD2563EB),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "▶ PLAYING" else "🎥 LECTURE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // BOTTOM-RIGHT IN-APP BADGE
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "HD 1080p",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // DETAILS FOOTER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isPlaying) Color(0xFF1D4ED8) else Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (item.chapterName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isPlaying) Color(0xFFDBEAFE) else Color(0xFFEFF6FF)
                            ) {
                                Text(
                                    text = "📁 ${item.chapterName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2563EB),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val dispSubject = if (item.folderName.isNotBlank()) item.folderName else subjectName
                        if (dispSubject.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = dispSubject,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Play",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Direct Video In-App Player View (ExoPlayer)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DirectVideoInAppPlayerView(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
