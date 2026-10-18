package com.example.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.MedRepository

@Composable
fun AppInfoAndLinksManageView(onClose: (() -> Unit)? = null) {
    val context = LocalContext.current

    val currentAboutUs by MedRepository.aboutUsText.collectAsState()
    val currentAboutUsUrl by MedRepository.aboutUsUrl.collectAsState()
    val currentNotice by MedRepository.noticeText.collectAsState()
    val currentNoticeUrl by MedRepository.noticeUrl.collectAsState()
    val currentPrivacy by MedRepository.privacyText.collectAsState()
    val currentPrivacyUrl by MedRepository.privacyUrl.collectAsState()
    val currentRefer by MedRepository.referText.collectAsState()
    val currentReferUrl by MedRepository.referUrl.collectAsState()
    val currentRatingUrl by MedRepository.ratingUrl.collectAsState()

    var aboutText by remember(currentAboutUs) { mutableStateOf(currentAboutUs) }
    var aboutUrl by remember(currentAboutUsUrl) { mutableStateOf(currentAboutUsUrl) }
    var noticeText by remember(currentNotice) { mutableStateOf(currentNotice) }
    var noticeUrl by remember(currentNoticeUrl) { mutableStateOf(currentNoticeUrl) }
    var privacyText by remember(currentPrivacy) { mutableStateOf(currentPrivacy) }
    var privacyUrl by remember(currentPrivacyUrl) { mutableStateOf(currentPrivacyUrl) }
    var referText by remember(currentRefer) { mutableStateOf(currentRefer) }
    var referUrl by remember(currentReferUrl) { mutableStateOf(currentReferUrl) }
    var ratingUrl by remember(currentRatingUrl) { mutableStateOf(currentRatingUrl) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ App Info, Notice & Link Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF1E293B)
                    )
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }
                }
                Text(
                    text = "Configure text and URLs for Notice, About Us, Privacy Policy, Refer, and Rating buttons across the app.",
                    fontSize = 12.5.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // 1. Notice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("📢 Notice Section", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF92400E))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = noticeText,
                            onValueChange = { noticeText = it },
                            label = { Text("Notice Text Message") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = noticeUrl,
                            onValueChange = { noticeUrl = it },
                            label = { Text("Notice URL / Link (Optional)") },
                            placeholder = { Text("https://example.com/notice") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. About Us
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("ℹ️ About Us Section", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E40AF))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = aboutText,
                            onValueChange = { aboutText = it },
                            label = { Text("About Us Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = aboutUrl,
                            onValueChange = { aboutUrl = it },
                            label = { Text("Website / About URL (Optional)") },
                            placeholder = { Text("https://example.com/about") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Privacy Policy
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🔒 Privacy Policy Section", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = privacyText,
                            onValueChange = { privacyText = it },
                            label = { Text("Privacy Policy Details") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = privacyUrl,
                            onValueChange = { privacyUrl = it },
                            label = { Text("Privacy Policy URL (Optional)") },
                            placeholder = { Text("https://example.com/privacy") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Refer & Share
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🎁 Refer & Share Section", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF166534))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = referText,
                            onValueChange = { referText = it },
                            label = { Text("Referral Share Text") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = referUrl,
                            onValueChange = { referUrl = it },
                            label = { Text("Referral App Download Link") },
                            placeholder = { Text("https://play.google.com/store/apps/...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Rating & Play Store
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFCE8))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("⭐ Rating & Review Section", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF854D0E))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ratingUrl,
                            onValueChange = { ratingUrl = it },
                            label = { Text("Google Play Store / Rating URL") },
                            placeholder = { Text("https://play.google.com/store/apps/details?id=com.example.medxmed") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        MedRepository.updateAppInfoSettings(
                            aboutText = aboutText.trim(),
                            aboutUrl = aboutUrl.trim(),
                            noticeTextVal = noticeText.trim(),
                            noticeUrlVal = noticeUrl.trim(),
                            privacyTextVal = privacyText.trim(),
                            privacyUrlVal = privacyUrl.trim(),
                            referTextVal = referText.trim(),
                            referUrlVal = referUrl.trim(),
                            ratingUrlVal = ratingUrl.trim()
                        )
                        Toast.makeText(context, "App Info, Notice & URLs saved successfully!", Toast.LENGTH_SHORT).show()
                        onClose?.invoke()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save App Info, Notices & URLs", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun AppInfoSettingsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppInfoAndLinksManageView(onClose = onDismiss)
            }
        }
    }
}
