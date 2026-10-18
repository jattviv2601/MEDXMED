package com.example.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.MedRepository
import com.example.model.ShortItem
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShortsScreen(
    onBackClick: () -> Unit = {},
    onOpenAdminUpload: () -> Unit = {}
) {
    val shorts by MedRepository.shorts.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler {
        onBackClick()
    }

    var likedSet by remember { mutableStateOf(setOf<String>()) }
    var bookmarkedSet by remember { mutableStateOf(setOf<String>()) }

    if (shorts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .systemBarsPadding()
        ) {
            // Top Bar in Empty State
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "MEDXMED SHORTS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontSize = 18.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .align(Alignment.Center)
            ) {
                Surface(
                    color = Color(0x223B82F6),
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎬", fontSize = 36.sp)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "No Shorts Available",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "High-yield video reels, clinical pearls, and mnemonics will appear here once published. Check back soon!",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // VerticalPager for TikTok / YouTube Shorts style vertical swiping
    // beyondViewportPageCount = 1 pre-fetches and pre-buffers the next video so it plays instantly with zero delay!
    val pagerState = rememberPagerState(initialPage = 0) { shorts.size }

    // Pre-cache thumbnail images for upcoming shorts so user never sees a blank frame
    LaunchedEffect(pagerState.currentPage, shorts) {
        val current = pagerState.currentPage
        listOf(current + 1, current + 2).forEach { nextIdx ->
            if (nextIdx in shorts.indices) {
                val nextItem = shorts[nextIdx]
                val thumb = if (nextItem.thumbnailUrl.isNotBlank()) {
                    nextItem.thumbnailUrl
                } else if (nextItem.youtubeVideoId.isNotBlank()) {
                    "https://img.youtube.com/vi/${nextItem.youtubeVideoId}/hqdefault.jpg"
                } else ""
                if (thumb.isNotBlank()) {
                    try {
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(thumb)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                        coil.Coil.imageLoader(context).enqueue(req)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 2,
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
            val short = shorts[pageIndex]
            val isCurrentPage = pagerState.currentPage == pageIndex
            val isLiked = likedSet.contains(short.id)
            val isBookmarked = bookmarkedSet.contains(short.id)

            Box(modifier = Modifier.fillMaxSize()) {
                // High Quality YouTube Player or Thumbnail Preview
                if (short.youtubeVideoId.isNotBlank()) {
                    YouTubePlayerItem(
                        videoId = short.youtubeVideoId,
                        isPlaying = isCurrentPage,
                        thumbnailUrl = short.thumbnailUrl
                    )
                } else {
                    // Fallback visual card when no video link
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF020617))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(text = short.emoji.ifBlank { "🩺" }, fontSize = 54.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = short.title,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Dark gradient overlay for bottom text and button readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x66000000), Color(0xCC000000))
                            )
                        )
                )

                // Clean Short Title at bottom-left (No extra clinical, mnemonic, or high-yield tags)
                if (short.title.isNotBlank()) {
                    Text(
                        text = short.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, bottom = 16.dp, end = 90.dp)
                            .fillMaxWidth(0.78f)
                    )
                }

                // Right Side Action Buttons (Like, Share, Direct YouTube)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, end = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Like Action
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                likedSet = if (isLiked) likedSet - short.id else likedSet + short.id
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) Color.Red else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = if (isLiked) "${short.likesCount + 1}" else "${short.likesCount}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Share Action
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    val url = if (short.youtubeVideoId.isNotBlank()) "https://youtu.be/${short.youtubeVideoId}" else ""
                                    putExtra(Intent.EXTRA_SUBJECT, short.title)
                                    putExtra(Intent.EXTRA_TEXT, "Medical Mnemonic: ${short.title}\n$url\nShared via MedXMed")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Mnemonic"))
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "Share",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Open in YouTube Direct Option
                    if (short.youtubeVideoId.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/${short.youtubeVideoId}"))
                                    context.startActivity(ytIntent)
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color(0x33FFFFFF), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = "Watch on YouTube",
                                    tint = Color.Red,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "YouTube",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Consolidated Top Navigation Bar with Logo Overlap
                var showMenu by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .padding(top = 4.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Navigation and App Title Layout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(end = 12.dp)
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "MEDXMED SHORTS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x77000000)
                            ) {
                                Text(
                                    text = "${pageIndex + 1}/${shorts.size}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 3-dot Menu Icon at top-right
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E293B))
                                    .widthIn(max = 280.dp)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    if (short.description.isNotBlank()) {
                                        Text(
                                            text = "Description:",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = short.description,
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    } else {
                                        Text(
                                            text = "No Description Available",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 12.sp
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayerItem(
    videoId: String,
    isPlaying: Boolean,
    thumbnailUrl: String
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastPlaybackState by remember { mutableStateOf<Boolean?>(null) }
    val initialPlay = remember(videoId) { isPlaying }
    val thumbUrl = if (thumbnailUrl.isNotBlank()) thumbnailUrl else "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Mount the WebView for BOTH active and adjacent pre-buffered videos!
        // VerticalPager beyondViewportPageCount=2 preloads upcoming videos in the background,
        // so when the user swipes, the video stream is already buffered in memory and plays INSTANTLY without search delay!
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    // Strip WebView identifier (Version/4.0 and ; wv) so YouTube treats this as a regular mobile browser
                    // and does not trigger embed error 152 / 150 bot detection
                    val rawUA = settings.userAgentString
                    settings.userAgentString = rawUA.replace("; wv", "").replace("Version/4.0 ", "")

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setBackgroundColor(android.graphics.Color.BLACK)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: ""
                            if (url.startsWith("https://www.youtube.com/watch") ||
                                url.startsWith("https://m.youtube.com/watch") ||
                                url.startsWith("https://youtu.be/")) {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    return true
                                } catch (_: Exception) {}
                            }
                            return false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url != null && (url.startsWith("https://www.youtube.com/watch") ||
                                    url.startsWith("https://m.youtube.com/watch") ||
                                    url.startsWith("https://youtu.be/"))) {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    return true
                                } catch (_: Exception) {}
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (isPlaying) {
                                view?.evaluateJavascript("playFromBeginning();", null)
                            } else {
                                view?.evaluateJavascript("pauseVideo();", null)
                            }
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            try {
                                (view?.parent as? ViewGroup)?.removeView(view)
                                view?.destroy()
                            } catch (_: Exception) {}
                            return true
                        }
                    }
                    webChromeClient = WebChromeClient()

                    val html = buildYouTubeHtml(videoId, posterUrl = thumbUrl, initialPlay = initialPlay)
                    loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", "https://www.youtube-nocookie.com")
                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
            },
            onRelease = { webView ->
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                } catch (_: Exception) {}
            }
        )

        // Reactive playback trigger: only fires when isPlaying actually changes (on scrolling into or out of view)
        LaunchedEffect(isPlaying, webViewRef) {
            val wv = webViewRef ?: return@LaunchedEffect
            if (lastPlaybackState != isPlaying) {
                lastPlaybackState = isPlaying
                try {
                    if (isPlaying) {
                        wv.evaluateJavascript("playFromBeginning();", null)
                    } else {
                        wv.evaluateJavascript("pauseVideo();", null)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}

private fun buildYouTubeHtml(videoId: String, posterUrl: String, initialPlay: Boolean): String {
    val autoplay = if (initialPlay) "1" else "0"
    val mute = if (initialPlay) "0" else "1"
    val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=$autoplay&mute=$mute&playsinline=1&controls=0&rel=0&loop=1&playlist=$videoId&enablejsapi=1&fs=0&modestbranding=1&iv_load_policy=3&origin=https://www.youtube-nocookie.com&widget_referrer=https://www.youtube-nocookie.com"
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body {
              width: 100%; height: 100%;
              background: #000;
              overflow: hidden;
              position: relative;
            }
            iframe {
              width: 100%; height: 100%;
              border: 0; outline: none;
              position: absolute; top: 0; left: 0;
              z-index: 1;
            }
            #poster {
              position: absolute; top: 0; left: 0; width: 100%; height: 100%;
              background-size: cover; background-position: center;
              background-repeat: no-repeat;
              background-image: url('$posterUrl');
              z-index: 2;
              transition: opacity 0.25s ease;
              pointer-events: none;
            }
            #poster.hide {
              opacity: 0;
              display: none;
            }
          </style>
        </head>
        <body>
          <div id="poster"></div>
          <iframe id="shortsPlayer"
                  src="$embedUrl"
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                  referrerpolicy="strict-origin-when-cross-origin"
                  allowfullscreen>
          </iframe>
          <script>
            var playerFrame = document.getElementById('shortsPlayer');
            var poster = document.getElementById('poster');
            var isActive = ${if (initialPlay) "true" else "false"};

            function post(func, args) {
              try {
                if (playerFrame && playerFrame.contentWindow) {
                  playerFrame.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: func,
                    args: args !== undefined ? args : ""
                  }), '*');
                }
              } catch(e) {}
            }

            function hidePoster() {
              if (poster) {
                poster.classList.add('hide');
              }
            }

            window.addEventListener('message', function(e) {
              try {
                var msg = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
                if (!msg) return;

                if (msg.event === 'onReady') {
                  if (isActive) {
                    post('seekTo', [0, true]);
                    post('unMute');
                    post('playVideo');
                    hidePoster();
                  } else {
                    post('mute');
                    post('pauseVideo');
                    post('seekTo', [0, true]);
                  }
                }

                if (msg.event === 'infoDelivery' && msg.info) {
                  if (!isActive) {
                    // Pre-buffered background video: freeze at 0s so it never advances in background
                    post('mute');
                    post('pauseVideo');
                    post('seekTo', [0, true]);
                  } else {
                    if (msg.info.playerState === 1) {
                      hidePoster();
                    }
                  }
                }
              } catch(err) {}
            });

            function playFromBeginning() {
              isActive = true;
              hidePoster();
              // Always rewind to 0 seconds so the video starts from the beginning!
              post('seekTo', [0, true]);
              post('unMute');
              post('playVideo');
              // Ensure seek sticks once video starts playing
              setTimeout(function() {
                if (isActive) {
                  post('seekTo', [0, true]);
                  post('unMute');
                  post('playVideo');
                }
              }, 120);
            }

            function playVideo() {
              playFromBeginning();
            }

            function pauseVideo() {
              isActive = false;
              post('pauseVideo');
              // Reset to beginning (0s) when scrolling away so it's ready at the start next time
              post('seekTo', [0, true]);
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
