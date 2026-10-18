package com.example.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val progress = remember { Animatable(0.23f) }

    // Smooth loading animation with guaranteed completion
    LaunchedEffect(Unit) {
        try {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
            )
            delay(50)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            onSplashFinished()
        }
    }

    val progressPercent = (progress.value * 100).toInt()

    val medxmedGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF3B82F6), // Vibrant Blue
            Color(0xFF8B5CF6), // Purple
            Color(0xFFFF2E93)  // Hot Magenta Pink
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable { onSplashFinished() }
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Theme Logo Image Card matching Image 1 & 2
            Card(
                modifier = Modifier
                    .size(280.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(36.dp),
                        ambientColor = Color(0x15000000),
                        spotColor = Color(0x253B82F6)
                    ),
                shape = RoundedCornerShape(36.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.medxmed_theme_logo),
                        contentDescription = "MEDXMED Theme Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(36.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // MEDXMED Exact Theme Gradient Title
            Text(
                text = "MEDXMED",
                style = TextStyle(
                    brush = medxmedGradient,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // RATTA KHATAM • CONCEPT SURU
            Text(
                text = "RATTA KHATAM • CONCEPT SURU",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Complete MBBS 1st Prof to 3rd Prof II Study Platform",
                fontSize = 12.5.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }

        // Bottom Loading Progress Indicator matching Screenshot 2
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Custom Gradient Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(medxmedGradient)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Loading $progressPercent%",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
