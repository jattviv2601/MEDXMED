package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.util.extractYouTubeVideoId

class YouTubePlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "VIDEO_URL"
        const val EXTRA_VIDEO_TITLE = "VIDEO_TITLE"

        fun launch(context: Context, videoUrl: String, title: String = "") {
            val intent = Intent(context, YouTubePlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    // Root Containers
    private lateinit var rootFrameLayout: FrameLayout
    private lateinit var normalLayout: LinearLayout
    private lateinit var playerContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var errorContainer: LinearLayout
    private lateinit var fullscreenToggleBtn: ImageButton

    // Fullscreen View & Callback references
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var videoId: String? = null
    private var videoUrl: String = ""
    private var videoTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Block screenshots, screen recording, and external capture for video lectures
        com.example.util.SecurityUtils.applyScreenshotProtection(this)

        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty()
        videoId = extractYouTubeVideoId(videoUrl)

        buildUiHierarchy()

        // Handle Back button: only exit fullscreen if active; otherwise close activity
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenView != null) {
                    handleHideCustomView()
                } else {
                    finish()
                }
            }
        })

        if (!videoId.isNullOrBlank()) {
            loadYouTubeEmbed(videoId!!)
        } else {
            showErrorLayout()
        }
    }

    private fun buildUiHierarchy() {
        // 1. Root FrameLayout: black background, fills screen
        rootFrameLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // 2. Normal Layout: vertical column containing Top Header and Player
        normalLayout = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // Header Bar (Dark slate #0F172A)
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0F172A.toInt())
            setPadding(dpToPx(4), dpToPx(8), dpToPx(16), dpToPx(8))
            minimumHeight = dpToPx(56)
        }

        // Apply status bar insets to top padding of Header Bar
        ViewCompat.setOnApplyWindowInsetsListener(headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(dpToPx(4), statusBarInsets.top, dpToPx(16), 0)
            insets
        }

        // Header Back Button
        val backButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            setImageResource(R.drawable.ic_arrow_back_white)
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            if (typedValue.resourceId != 0) {
                setBackgroundResource(typedValue.resourceId)
            } else {
                setBackgroundColor(Color.TRANSPARENT)
            }
            contentDescription = "Back"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        }
        headerBar.addView(backButton)

        // Header Title
        val titleView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dpToPx(6)
            }
            text = if (videoTitle.isNotBlank()) videoTitle else "YouTube Lecture"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        headerBar.addView(titleView)

        normalLayout.addView(
            headerBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // 16:9 Aspect Ratio Container for the Player in portrait
        playerContainer = AspectRatioFrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // WebView instantiation and configuration
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        setupWebView(webView)
        playerContainer.addView(webView)

        // Floating Fullscreen Toggle Button on the player corner
        fullscreenToggleBtn = ImageButton(this).apply {
            val btnSize = dpToPx(38)
            val margin = dpToPx(10)
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = margin
                rightMargin = margin
                marginEnd = margin
            }
            setImageResource(R.drawable.ic_fullscreen_white)
            contentDescription = "Fullscreen"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val shape = GradientDrawable().apply {
                this.shape = GradientDrawable.OVAL
                setColor(0xB3000000.toInt())
            }
            background = shape

            setOnClickListener {
                if (fullscreenView != null) {
                    handleHideCustomView()
                } else {
                    // Trigger fullscreen via JavaScript on the YouTube iframe video element
                    val script = """
                        (function() {
                            var v = document.querySelector('video');
                            if (v && v.requestFullscreen) {
                                v.requestFullscreen();
                            } else if (document.documentElement.requestFullscreen) {
                                document.documentElement.requestFullscreen();
                            }
                        })();
                    """.trimIndent()
                    webView.evaluateJavascript(script, null)
                }
            }
        }
        playerContainer.addView(fullscreenToggleBtn)

        // Error Layout (hidden by default)
        errorContainer = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))

            val errorIcon = ImageView(this@YouTubePlayerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(56), dpToPx(56))
                setImageResource(R.drawable.ic_error_outline_red)
                contentDescription = "Error"
            }
            addView(errorIcon)

            val errorText = TextView(this@YouTubePlayerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(16)
                    bottomMargin = dpToPx(20)
                }
                text = "Video unavailable for embedded playback"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(errorText)

            val retryBtn = Button(this@YouTubePlayerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = "Retry"
                setTextColor(Color.WHITE)
                val btnShape = GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(0xFF2563EB.toInt())
                }
                background = btnShape
                setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10))
                setOnClickListener {
                    errorContainer.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                    val id = extractYouTubeVideoId(videoUrl)
                    if (!id.isNullOrBlank()) {
                        videoId = id
                        loadYouTubeEmbed(id)
                    } else {
                        showErrorLayout()
                    }
                }
            }
            addView(retryBtn)
        }
        playerContainer.addView(errorContainer)

        normalLayout.addView(playerContainer)

        // Empty space below player (clean black backdrop)
        val bottomSpace = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.BLACK)
        }
        normalLayout.addView(bottomSpace)

        // Add normal layout to root
        rootFrameLayout.addView(normalLayout)

        // 3. Fullscreen Container: placed directly in rootFrameLayout ABOVE normalLayout
        fullscreenContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        rootFrameLayout.addView(fullscreenContainer)

        // Set Root FrameLayout as Activity content view
        setContentView(rootFrameLayout)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(targetWebView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(targetWebView, true)

        val settings = targetWebView.settings
        // Strip WebView identifier so YouTube does not trigger embed error 152 / 150 bot detection
        val rawUA = settings.userAgentString
        settings.userAgentString = rawUA.replace("; wv", "").replace("Version/4.0 ", "")

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        targetWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                if (url.startsWith("https://www.youtube.com/watch") ||
                    url.startsWith("https://m.youtube.com/watch") ||
                    url.startsWith("https://youtu.be/")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        return true
                    } catch (_: Exception) {}
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showErrorLayout()
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                try {
                    (view?.parent as? ViewGroup)?.removeView(view)
                    view?.destroy()
                } catch (_: Exception) {}
                showErrorLayout()
                return true
            }
        }

        targetWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                handleShowCustomView(view, callback)
            }

            override fun onHideCustomView() {
                handleHideCustomView()
            }
        }
    }

    private fun loadYouTubeEmbed(id: String) {
        val embedUrl = "https://www.youtube-nocookie.com/embed/$id?playsinline=1&controls=1&fs=1&rel=0&enablejsapi=1&origin=https://www.youtube-nocookie.com&widget_referrer=https://www.youtube-nocookie.com"
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background-color: #000000;
                        overflow: hidden;
                    }
                    iframe {
                        width: 100%;
                        height: 100%;
                        border: 0;
                    }
                </style>
            </head>
            <body>
                <iframe src="$embedUrl"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                        referrerpolicy="strict-origin-when-cross-origin"
                        allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", "https://www.youtube-nocookie.com")
    }

    private fun handleShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback?) {
        // 1. If another fullscreen view is already active, call the previous callback safely.
        if (fullscreenView != null) {
            try {
                fullscreenCallback?.onCustomViewHidden()
            } catch (_: Exception) {}
            fullscreenContainer.removeView(fullscreenView)
        }

        // 2. Save references
        fullscreenView = view
        fullscreenCallback = callback

        // 3. Add the supplied fullscreen view directly to the Activity's root FrameLayout container
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreenContainer.removeAllViews()
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 4. Make fullscreen view VISIBLE, hide normal layout behind it (do NOT destroy webView)
        fullscreenContainer.visibility = View.VISIBLE
        normalLayout.visibility = View.INVISIBLE

        // 5. Rotate to landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // 6. Hide status bar and navigation bar using modern WindowInsetsController APIs
        hideSystemBars()

        // 7 & 8. Ensure root background remains black
        rootFrameLayout.setBackgroundColor(Color.BLACK)
    }

    private fun handleHideCustomView() {
        if (fullscreenView == null) return

        // 1. Remove the fullscreen custom View from the root FrameLayout container
        val viewToRemove = fullscreenView
        if (viewToRemove != null) {
            fullscreenContainer.removeView(viewToRemove)
            (viewToRemove.parent as? ViewGroup)?.removeView(viewToRemove)
        }
        fullscreenContainer.removeAllViews()
        fullscreenContainer.visibility = View.GONE

        // 2. Call the saved CustomViewCallback.onCustomViewHidden() safely
        try {
            fullscreenCallback?.onCustomViewHidden()
        } catch (_: Exception) {}

        // 3. Clear fullscreenView
        fullscreenView = null

        // 4. Clear fullscreenCallback
        fullscreenCallback = null

        // 5. Show the original normal layout and WebView again
        normalLayout.visibility = View.VISIBLE

        // 6. Restore system bars
        showSystemBars()

        // 7. Set orientation back to UNSPECIFIED (supports large screens & tablets)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun hideSystemBars() {
        window?.let { win ->
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        window?.let { win ->
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showErrorLayout() {
        webView.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        if (fullscreenView != null) {
            handleHideCustomView()
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}

/**
 * 16:9 Aspect Ratio FrameLayout for embedded video player in portrait mode.
 */
class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val calculatedHeight = if (width > 0) (width * 9) / 16 else MeasureSpec.getSize(heightMeasureSpec)
        val newHeightSpec = MeasureSpec.makeMeasureSpec(calculatedHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, newHeightSpec)
    }
}
