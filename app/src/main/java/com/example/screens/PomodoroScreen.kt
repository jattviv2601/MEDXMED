package com.example.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.PomodoroManager
import com.example.data.PomodoroMode
import kotlinx.coroutines.delay

@Composable
fun PomodoroScreen(
    onBackClick: () -> Unit
) {
    val timeLeft by PomodoroManager.timeLeftSeconds.collectAsState()
    val totalTime by PomodoroManager.totalDurationSeconds.collectAsState()
    val isRunning by PomodoroManager.isRunning.collectAsState()
    val currentMode by PomodoroManager.currentMode.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showControls by remember { mutableStateOf(!isRunning) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showUnlockPrompt by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }

    // Keep screen ON so timer functions as a desk clock; do not force sensor override
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
            PomodoroManager.stopAlarm()
        }
    }

    // Fullscreen and System Bars management based on Landscape / Portrait orientation
    LaunchedEffect(isLandscape) {
        val window = activity?.window
        if (window != null) {
            if (isLandscape) {
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                WindowInsetsControllerCompat(window, window.decorView).show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
        }
    }

    // Auto-hide controls when timer is running
    LaunchedEffect(isRunning, showControls, isScreenLocked) {
        if (isRunning && showControls && !isScreenLocked) {
            delay(3000L)
            showControls = false
        }
    }

    // Hide unlock prompt after 3s of inactivity when locked
    LaunchedEffect(showUnlockPrompt) {
        if (showUnlockPrompt) {
            delay(3000L)
            showUnlockPrompt = false
        }
    }

    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)
    val progress = if (totalTime > 0) {
        ((totalTime - timeLeft).toFloat() / totalTime).coerceIn(0f, 1f)
    } else 0f

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val effectiveTopPadding = if (topInset > 0.dp) topInset + 12.dp else 48.dp

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val effectiveBottomPadding = if (bottomInset > 0.dp) bottomInset + 16.dp else 28.dp

    BackHandler {
        if (isScreenLocked) {
            showUnlockPrompt = true
        } else if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            onBackClick()
        }
    }

    // Root Pure Black Fullscreen Container (Zero white margins, zero headers)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (isScreenLocked) {
                    showUnlockPrompt = !showUnlockPrompt
                } else {
                    showControls = !showControls
                }
            }
    ) {
        if (isLandscape) {
            // ========================================================
            // LANDSCAPE MODE: Fullscreen Massive Electric Cyan Digital Clock
            // Dynamic Auto-Sizing that NEVER wraps or overlaps on any Tablet or Phone
            // ========================================================
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val chars = formattedTime.length.coerceAtLeast(5)
                val widthConstraint = (maxWidth.value * 0.86f) / (chars * 0.58f)
                val heightConstraint = maxHeight.value * 0.62f
                val landscapeFontSize = minOf(widthConstraint, heightConstraint).coerceIn(36f, 220f).sp

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Status Badge in Landscape
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color(0xFF00E5FF) else Color(0xFF71717A))
                        )
                        Text(
                            text = if (isRunning) "FOCUSING" else "PAUSED",
                            color = if (isRunning) Color(0xFF00E5FF) else Color(0xFF71717A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    Text(
                        text = formattedTime,
                        fontSize = landscapeFontSize,
                        lineHeight = landscapeFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF00E5FF),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        letterSpacing = 2.sp
                    )
                }
            }
        } else {
            // ========================================================
            // PORTRAIT MODE: Clean Analog Dial + Growing Cyan Progress Arc + White Digits
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = effectiveTopPadding + 44.dp, bottom = effectiveBottomPadding + 70.dp),
                contentAlignment = Alignment.Center
            ) {
                PomodoroClockFace(
                    progress = progress,
                    formattedTime = formattedTime,
                    isRunning = isRunning,
                    modifier = Modifier
                        .fillMaxHeight(0.76f)
                        .aspectRatio(1f)
                )
            }
        }

        // ========================================================
        // TOP HEADER ROW (Mode Selector on Left, Close Button on Top Right)
        // Only visible in Portrait mode; completely hidden in Landscape desk clock mode!
        // ========================================================
        AnimatedVisibility(
            visible = !isLandscape && (!isRunning || showControls) && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = effectiveTopPadding, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top Left: Mode Selector Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDD18181B),
                    onClick = { showModeDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentMode.emoji} ${currentMode.title}",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Select Mode",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Top Right Corner: Close Button (✕) to exit
                Surface(
                    shape = CircleShape,
                    color = Color(0xDD1E1E1E),
                    modifier = Modifier.size(46.dp),
                    onClick = onBackClick
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Pomodoro",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ========================================================
        // BOTTOM CONTROLS ROW (Circular action buttons)
        // Always visible in Portrait mode; auto-hides only in Landscape desk clock mode
        // ========================================================
        val isControlsVisible = if (isLandscape) {
            (showControls || !isRunning) && !isScreenLocked
        } else {
            !isScreenLocked
        }

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = effectiveBottomPadding, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Reset / Restart Button
                Surface(
                    shape = CircleShape,
                    color = Color(0xEE1E1E1E),
                    modifier = Modifier.size(52.dp),
                    onClick = { PomodoroManager.resetTimer() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Timer",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Screen Lock Button
                Surface(
                    shape = CircleShape,
                    color = if (isScreenLocked) Color(0xFF00E5FF) else Color(0xEE1E1E1E),
                    modifier = Modifier.size(52.dp),
                    onClick = {
                        isScreenLocked = true
                        showControls = false
                        showUnlockPrompt = false
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Screen",
                            tint = if (isScreenLocked) Color.Black else Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 3. Play / Pause Button (Large Vibrant Cyan)
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.size(68.dp),
                    onClick = {
                        PomodoroManager.toggleTimer()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Pause Pomodoro Timer" else "Start Pomodoro Timer",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // 4. Landscape / Rotate Mode Button
                Surface(
                    shape = CircleShape,
                    color = Color(0xEE1E1E1E),
                    modifier = Modifier.size(52.dp),
                    onClick = {
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Rotate Screen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 5. Close / Exit Button
                Surface(
                    shape = CircleShape,
                    color = Color(0xEE1E1E1E),
                    modifier = Modifier.size(52.dp),
                    onClick = onBackClick
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Pomodoro",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // ========================================================
        // UNLOCK BUTTON (Shown when screen is locked and user taps anywhere)
        // ========================================================
        AnimatedVisibility(
            visible = isScreenLocked && showUnlockPrompt,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF00E5FF),
                shadowElevation = 8.dp,
                modifier = Modifier.clickable {
                    isScreenLocked = false
                    showControls = true
                    showUnlockPrompt = false
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Unlock",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Tap to Unlock",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Mode Selection Dialog
        if (showModeDialog) {
            AlertDialog(
                onDismissRequest = { showModeDialog = false },
                containerColor = Color(0xFF18181B),
                titleContentColor = Color.White,
                textContentColor = Color(0xFF94A3B8),
                title = { Text("Select Pomodoro Session", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PomodoroMode.values().forEach { mode ->
                            val isSelected = currentMode == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF27272A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        PomodoroManager.setMode(mode)
                                        showModeDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${mode.emoji} ${mode.title}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                    Text(
                                        text = "${mode.minutes} min",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.Black else Color(0xFFA1A1AA)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModeDialog = false }) {
                        Text("Done", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/**
 * Responsive Analog-Digital Pomodoro Clock Face for Portrait & Tablets:
 * - Automatically computes font size so text NEVER overlaps the dial ticks or clips on tablets
 * - 60 clock ticks on dial (12 prominent 5-min ticks + 48 1-min ticks)
 * - Clockwise growing vibrant Cyan progress arc starting at 12 o'clock (-90°)
 * - Glowing white tip indicator at the leading edge
 * - Single-line non-wrapping pure white digital countdown text (e.g. 25:00)
 */
@Composable
fun PomodoroClockFace(
    progress: Float,
    formattedTime: String,
    isRunning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val minDim = minOf(maxWidth, maxHeight)
        val availableDiameter = (minDim.value - 76f).coerceAtLeast(80f)
        val calculatedFontSize = (availableDiameter / 3.4f).coerceIn(32f, 76f).sp

        Canvas(modifier = Modifier.size(minDim)) {
            val canvasSize = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = canvasSize / 2f - 20.dp.toPx()

            // 1. Outer subtle dark frame circle
            drawCircle(
                color = Color(0xFF18181B),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 2. 60 Clock ticks around perimeter
            val tickRadius = radius - 8.dp.toPx()
            for (i in 0 until 60) {
                val angleInRad = Math.toRadians((i * 6 - 90).toDouble())
                val isMajor = (i % 5 == 0)
                val tickLength = if (isMajor) 14.dp.toPx() else 6.dp.toPx()
                val tickWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx()
                val tickColor = if (isMajor) Color(0xFF71717A) else Color(0xFF3F3F46)

                val startX = (center.x + (tickRadius - tickLength) * Math.cos(angleInRad)).toFloat()
                val startY = (center.y + (tickRadius - tickLength) * Math.sin(angleInRad)).toFloat()
                val endX = (center.x + tickRadius * Math.cos(angleInRad)).toFloat()
                val endY = (center.y + tickRadius * Math.sin(angleInRad)).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }

            // 3. Clockwise Growing Vibrant Cyan Progress Arc
            if (progress > 0.001f) {
                val sweepAngle = (progress * 360f).coerceIn(0f, 360f)
                val arcRadius = radius + 6.dp.toPx()
                val arcStroke = 8.dp.toPx()

                drawArc(
                    color = Color(0xFF00E5FF),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = arcStroke, cap = StrokeCap.Round)
                )

                // 4. White Glowing Tip Dot at leading edge
                val tipAngleRad = Math.toRadians((-90f + sweepAngle).toDouble())
                val tipX = (center.x + arcRadius * Math.cos(tipAngleRad)).toFloat()
                val tipY = (center.y + arcRadius * Math.sin(tipAngleRad)).toFloat()

                drawCircle(
                    color = Color.White,
                    radius = arcStroke / 2f + 1.dp.toPx(),
                    center = Offset(tipX, tipY)
                )
            }
        }

        // Center Content: Status indicator + countdown digits
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status Pill (FOCUSING vs PAUSED)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF).copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = "FOCUSING",
                        color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF71717A))
                    )
                    Text(
                        text = "PAUSED",
                        color = Color(0xFF71717A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            // Bold, Crisp Centered Pure White Digital Countdown Text
            Text(
                text = formattedTime,
                fontSize = calculatedFontSize,
                lineHeight = calculatedFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}
