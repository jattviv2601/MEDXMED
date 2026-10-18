package com.example.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.MedRepository
import com.example.model.MCQBookmark
import com.example.model.MCQQuizHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ProfileSubScreen {
    Main,
    History,
    Bookmarks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onOpenAdminSection: () -> Unit
) {
    val context = LocalContext.current

    val currentName by MedRepository.userName.collectAsState()
    val currentAvatar by MedRepository.userAvatar.collectAsState()
    val currentPhotoUri by MedRepository.userPhotoUri.collectAsState()
    val currentTheme by MedRepository.currentTheme.collectAsState()

    val quizHistory by MedRepository.quizHistory.collectAsState()
    val bookmarkedMCQs by MedRepository.bookmarkedMCQs.collectAsState()

    // App Info & Settings StateFlows (About Us, Notice, Privacy, Refer, Rating)
    val aboutUsText by MedRepository.aboutUsText.collectAsState()
    val aboutUsUrl by MedRepository.aboutUsUrl.collectAsState()
    val noticeText by MedRepository.noticeText.collectAsState()
    val noticeUrl by MedRepository.noticeUrl.collectAsState()
    val privacyText by MedRepository.privacyText.collectAsState()
    val privacyUrl by MedRepository.privacyUrl.collectAsState()
    val referText by MedRepository.referText.collectAsState()
    val referUrl by MedRepository.referUrl.collectAsState()
    val ratingUrl by MedRepository.ratingUrl.collectAsState()

    var activeSubScreen by remember { mutableStateOf(ProfileSubScreen.Main) }

    var showEditDialog by remember { mutableStateOf(false) }
    var activeInfoDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeInfoDialogText by remember { mutableStateOf<String?>(null) }
    var activeInfoDialogUrl by remember { mutableStateOf<String?>(null) }
    var lastLockClickTime by remember { mutableLongStateOf(0L) }

    // Edit Profile state
    var editNameInput by remember(showEditDialog) { mutableStateOf(currentName) }
    var editAvatar by remember(showEditDialog) { mutableStateOf(currentAvatar) }
    var editPhotoUri by remember(showEditDialog) { mutableStateOf(currentPhotoUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = "profile_avatar_image_${System.currentTimeMillis()}.jpg"
                    val file = java.io.File(context.filesDir, fileName)
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    val path = file.absolutePath
                    editPhotoUri = path
                    MedRepository.updateProfile(currentName, currentAvatar, path)
                    Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val avatarOptions = listOf("👨‍⚕️", "👩‍⚕️", "🩺", "🥼", "🧠", "💉", "🧬", "⚕️", "🦷", "🫀")
    val themeBgColor = MaterialTheme.colorScheme.background

    when (activeSubScreen) {
        ProfileSubScreen.History -> {
            BackHandler { activeSubScreen = ProfileSubScreen.Main }
            QuizHistoryScreen(
                quizHistory = quizHistory,
                currentTheme = currentTheme,
                onBack = { activeSubScreen = ProfileSubScreen.Main }
            )
        }
        ProfileSubScreen.Bookmarks -> {
            BackHandler { activeSubScreen = ProfileSubScreen.Main }
            BookmarksScreen(
                bookmarkedMCQs = bookmarkedMCQs,
                currentTheme = currentTheme,
                onBack = { activeSubScreen = ProfileSubScreen.Main }
            )
        }
        ProfileSubScreen.Main -> {
            BackHandler { onBackClick() }
            Scaffold(
                topBar = {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "My Profile",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                },
                containerColor = themeBgColor
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Profile Hero Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(96.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Avatar Circle
                                    Box(
                                        modifier = Modifier
                                            .size(88.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    if (currentTheme == 1) listOf(Color(0xFF334155), Color(0xFF1E293B))
                                                    else listOf(Color(0xFFDBEAFE), Color(0xFFEFF6FF))
                                                )
                                            )
                                            .border(2.5.dp, if (currentTheme == 1) Color(0xFF00E5FF) else Color(0xFF2563EB), CircleShape)
                                            .clickable {
                                                photoPickerLauncher.launch(
                                                    androidx.activity.result.PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    )
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!currentPhotoUri.isNullOrBlank()) {
                                            AsyncImage(
                                                model = currentPhotoUri,
                                                contentDescription = "Profile Photo",
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(text = currentAvatar, fontSize = 42.sp)
                                        }
                                    }

                                    // Pen-type Edit Photo Badge (Clearly visible, unclipped at bottom-right)
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(30.dp)
                                            .offset(x = (-2).dp, y = (-2).dp)
                                            .clickable {
                                                photoPickerLauncher.launch(
                                                    androidx.activity.result.PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    )
                                                )
                                            },
                                        shape = CircleShape,
                                        color = if (currentTheme == 1) Color(0xFF00E5FF) else Color(0xFF2563EB),
                                        shadowElevation = 3.dp,
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Upload Profile Photo",
                                                tint = if (currentTheme == 1) Color.Black else Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showEditDialog = true }
                                ) {
                                    Text(
                                        text = currentName,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentTheme == 1) Color.White else Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Name",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Dedicated Quick Cards: History & Bookmarks (Tapping opens new interface)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quiz History Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeSubScreen = ProfileSubScreen.History },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📊", fontSize = 24.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Quiz History",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (currentTheme == 1) Color.White else Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${quizHistory.size} attempts",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // Bookmarks Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeSubScreen = ProfileSubScreen.Bookmarks },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFEF3C7),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🔖", fontSize = 24.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Bookmarks",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (currentTheme == 1) Color.White else Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${bookmarkedMCQs.size} saved",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Theme Switcher Card
                    item {
                        val themeNames = listOf("Classic White", "Neon Night", "Study Room", "Ocean Breeze", "Midnight OLED")
                        val themeIcons = listOf("🌓", "✨", "📖", "🌊", "🌑")

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { MedRepository.updateTheme((currentTheme + 1) % 5) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(text = themeIcons[currentTheme % 5], fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Theme: ${themeNames[currentTheme % 5]}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (currentTheme == 1) Color.White else Color(0xFF0F172A)
                                        )
                                        Text("Tap to cycle app colors", fontSize = 12.sp, color = Color(0xFF64748B))
                                    }
                                }
                                Icon(Icons.Default.Refresh, contentDescription = "Cycle Theme", tint = Color(0xFF2563EB))
                            }
                        }
                    }

                    // 4. Quick Action Icons Card (Clean 2-Row Icon & Label Grid)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    text = "Quick Actions & Information",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTheme == 1) Color.White else Color(0xFF0F172A),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                                )

                                // Row 1: About Us, Notice, Privacy, Contact
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProfileQuickIcon(
                                        icon = "ℹ️",
                                        label = "About Us",
                                        bgColor = Color(0xFFEFF6FF),
                                        onClick = {
                                            activeInfoDialogTitle = "ℹ️ About Us"
                                            activeInfoDialogText = aboutUsText
                                            activeInfoDialogUrl = aboutUsUrl
                                        }
                                    )

                                    ProfileQuickIcon(
                                        icon = "📢",
                                        label = "Notice",
                                        bgColor = Color(0xFFFEF3C7),
                                        onClick = {
                                            activeInfoDialogTitle = "📢 Notice"
                                            activeInfoDialogText = noticeText
                                            activeInfoDialogUrl = noticeUrl
                                        }
                                    )

                                    ProfileQuickIcon(
                                        icon = "🔒",
                                        label = "Privacy",
                                        bgColor = Color(0xFFF1F5F9),
                                        onClick = {
                                            activeInfoDialogTitle = "🔒 Privacy Policy"
                                            activeInfoDialogText = privacyText
                                            activeInfoDialogUrl = privacyUrl.ifBlank { "https://sites.google.com/view/medxmed-privacy-policy/home" }
                                        }
                                    )

                                    ProfileQuickIcon(
                                        icon = "📧",
                                        label = "Contact",
                                        bgColor = Color(0xFFFCE7F3),
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:jattviv2601@gmail.com?subject=MedXMed%20Support%20%26%20Inquiry")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Row 2: Refer, Rate Us, Admin Lock
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProfileQuickIcon(
                                        icon = "🎁",
                                        label = "Refer",
                                        bgColor = Color(0xFFDCFCE7),
                                        onClick = {
                                            val shareMsg = if (referUrl.isNotBlank()) "$referText\n\nLink: $referUrl" else referText
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareMsg)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Refer MedXMed App"))
                                        }
                                    )

                                    ProfileQuickIcon(
                                        icon = "⭐",
                                        label = "Rate Us",
                                        bgColor = Color(0xFFFEF9C3),
                                        onClick = {
                                            if (ratingUrl.isNotBlank()) {
                                                try {
                                                    val formattedUrl = if (ratingUrl.startsWith("http://") || ratingUrl.startsWith("https://")) ratingUrl else "https://$ratingUrl"
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Could not open rating link", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Rating URL not configured yet", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )

                                    ProfileQuickIcon(
                                        icon = "🔐",
                                        label = "Admin",
                                        bgColor = Color(0xFFF1F5F9),
                                        onClick = {
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastLockClickTime < 1500) {
                                                onOpenAdminSection()
                                                lastLockClickTime = 0L
                                            } else {
                                                lastLockClickTime = currentTime
                                                Toast.makeText(context, "Tap again to open Admin Panel", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Unofficial App Footer
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "MedXMed unofficial app",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (currentTheme == 1) Color(0xFF64748B) else Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Edit Profile Modal
    if (showEditDialog) {
        Dialog(onDismissRequest = { showEditDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Your Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📷 Upload Photo from Phone")
                    }

                    if (!editPhotoUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                editPhotoUri = null
                                MedRepository.updateProfile(editNameInput.trim(), editAvatar, null)
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text("Remove Custom Photo (Use Emoji)", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Or Choose Avatar Emoji:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(avatarOptions) { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = if (editAvatar == emoji && editPhotoUri.isNullOrBlank()) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = if (editAvatar == emoji && editPhotoUri.isNullOrBlank()) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2563EB)) else null,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable {
                                        editAvatar = emoji
                                        editPhotoUri = null
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                MedRepository.updateProfile(editNameInput.trim(), editAvatar, editPhotoUri)
                                showEditDialog = false
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Info Dialog (with Text and optional URL button)
    if (activeInfoDialogTitle != null && activeInfoDialogText != null) {
        AlertDialog(
            onDismissRequest = {
                activeInfoDialogTitle = null
                activeInfoDialogText = null
                activeInfoDialogUrl = null
            },
            title = { Text(activeInfoDialogTitle.orEmpty(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(activeInfoDialogText.orEmpty(), fontSize = 14.sp, lineHeight = 20.sp)
                    if (!activeInfoDialogUrl.isNullOrBlank() && activeInfoDialogTitle != "🔒 Privacy Policy") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Link: ${activeInfoDialogUrl.orEmpty()}",
                            fontSize = 12.sp,
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    if (!activeInfoDialogUrl.isNullOrBlank()) {
                        Button(
                            onClick = {
                                try {
                                    val url = activeInfoDialogUrl.orEmpty()
                                    val formattedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(if (activeInfoDialogTitle == "🔒 Privacy Policy") "🌐 Open in Web Browser" else "Open Link 🔗")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = {
                        activeInfoDialogTitle = null
                        activeInfoDialogText = null
                        activeInfoDialogUrl = null
                    }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
fun ProfileQuickIcon(
    icon: String,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = icon, fontSize = 22.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

/**
 * Dedicated Quiz History Screen (Opens when History icon is clicked)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizHistoryScreen(
    quizHistory: List<MCQQuizHistory>,
    currentTheme: Int,
    onBack: () -> Unit
) {
    val themeBgColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Quiz History",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Profile"
                            )
                        }
                    },
                    actions = {
                        if (quizHistory.isNotEmpty()) {
                            IconButton(onClick = { MedRepository.clearAllQuizHistory() }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All History",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        },
        containerColor = themeBgColor
    ) { paddingValues ->
        if (quizHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📊", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Quiz Attempts Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (currentTheme == 1) Color.White else Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Practice questions in the MCQ section to see your scores, statistics, and pie charts here.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Attempts: ${quizHistory.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                        TextButton(
                            onClick = { MedRepository.clearAllQuizHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                items(quizHistory, key = { it.id }) { record ->
                    QuizHistoryCard(
                        record = record,
                        currentTheme = currentTheme,
                        onDelete = { MedRepository.deleteQuizHistory(record.id) }
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Bookmarks Screen (Opens when Bookmark icon is clicked)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarkedMCQs: List<MCQBookmark>,
    currentTheme: Int,
    onBack: () -> Unit
) {
    val themeBgColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Bookmarked MCQs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Profile"
                            )
                        }
                    },
                    actions = {
                        if (bookmarkedMCQs.isNotEmpty()) {
                            IconButton(onClick = { MedRepository.clearAllBookmarks() }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All Bookmarks",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        },
        containerColor = themeBgColor
    ) { paddingValues ->
        if (bookmarkedMCQs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔖", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Bookmarked MCQs",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (currentTheme == 1) Color.White else Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the bookmark icon on any question during MCQ tests to save it here for quick revision.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Questions: ${bookmarkedMCQs.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                        TextButton(
                            onClick = { MedRepository.clearAllBookmarks() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                items(bookmarkedMCQs, key = { it.id }) { bm ->
                    BookmarkCard(
                        bookmark = bm,
                        currentTheme = currentTheme,
                        onDelete = { MedRepository.removeBookmark(bm.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuizHistoryCard(
    record: MCQQuizHistory,
    currentTheme: Int,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    val totalMarksScored = if (record.totalMarks != 0f) {
        record.totalMarks
    } else {
        (record.correctCount * 1.0f) - (record.incorrectCount * 0.25f)
    }
    val formattedMarks = if (totalMarksScored % 1f == 0f) {
        "${totalMarksScored.toInt()}"
    } else {
        String.format(Locale.US, "%.2f", totalMarksScored)
    }

    var isReviewExpanded by remember { mutableStateOf(false) }
    val allMCQs by MedRepository.mcqs.collectAsState()
    val matchingQuestions = remember(record, allMCQs) {
        val byAnswers = allMCQs.filter { record.userAnswers.containsKey(it.id) }
        if (byAnswers.isNotEmpty()) {
            byAnswers
        } else {
            allMCQs.filter {
                it.chapterName.equals(record.chapterName, ignoreCase = true) ||
                (record.folderName.isNotBlank() && (it.folderId == record.folderName || it.folderName.equals(record.folderName, ignoreCase = true)))
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.chapterName.ifBlank { "MCQ Test" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (currentTheme == 1) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Attempt",
                        tint = Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Pie Chart
                MCQResultsPieChart(
                    correct = record.correctCount,
                    incorrect = record.incorrectCount,
                    skipped = record.skippedCount,
                    modifier = Modifier.size(76.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Marks Scored:", fontSize = 11.5.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                        Text(
                            "$formattedMarks / ${record.totalQuestions}",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                    Text(
                        text = "(+1 for correct • -0.25 for incorrect)",
                        fontSize = 9.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Correct (+1):", fontSize = 11.sp, color = Color(0xFF16A34A))
                        Text("${record.correctCount}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF16A34A))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Incorrect (-0.25):", fontSize = 11.sp, color = Color(0xFFDC2626))
                        Text("${record.incorrectCount}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Skipped (0):", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text("${record.skippedCount}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }

            // Review questions button if questions are available
            if (matchingQuestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { isReviewExpanded = !isReviewExpanded },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text(
                        text = if (isReviewExpanded) "Hide Review Questions ▲" else "Review Questions & Answers (${matchingQuestions.size}) ▼",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isReviewExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val letters = listOf("A", "B", "C", "D", "E")
                    matchingQuestions.forEachIndexed { qIdx, q ->
                        val userPick = record.userAnswers[q.id]
                        val isUserCorrect = userPick == q.correctOptionIndex
                        val isUserSkipped = userPick == null

                        Surface(
                            color = if (currentTheme == 1) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Q ${qIdx + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF2563EB)
                                    )
                                    Surface(
                                        color = when {
                                            isUserCorrect -> Color(0xFFDCFCE7)
                                            isUserSkipped -> Color(0xFFF1F5F9)
                                            else -> Color(0xFFFEE2E2)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isUserCorrect -> "✓ Correct (+1)"
                                                isUserSkipped -> "○ Unattempted"
                                                else -> "✗ Incorrect (-0.25)"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isUserCorrect -> Color(0xFF15803D)
                                                isUserSkipped -> Color(0xFF64748B)
                                                else -> Color(0xFFB91C1C)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                val parsedContent = remember(q.id) { parseMCQContentFromItem(q) }
                                TextbookQuestionContent(content = parsedContent, isDarkTheme = currentTheme == 1)

                                Spacer(modifier = Modifier.height(8.dp))

                                q.options.forEachIndexed { optIdx, opt ->
                                    val isCorrectOpt = optIdx == q.correctOptionIndex
                                    val isUserOpt = optIdx == userPick

                                    Surface(
                                        color = when {
                                            isCorrectOpt -> Color(0xFFDCFCE7)
                                            isUserOpt && !isCorrectOpt -> Color(0xFFFEE2E2)
                                            else -> if (currentTheme == 1) Color(0xFF1E293B) else Color.White
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${letters.getOrNull(optIdx) ?: (optIdx + 1)}. ",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = if (isCorrectOpt) Color(0xFF15803D) else Color(0xFF64748B)
                                            )
                                            Text(
                                                text = opt.text,
                                                fontSize = 11.5.sp,
                                                color = if (isCorrectOpt) Color(0xFF15803D) else if (isUserOpt) Color(0xFFB91C1C) else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isCorrectOpt) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("✓ Correct", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                            }
                                            if (isUserOpt && !isCorrectOpt) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("✗ Your Pick", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                }

                                if (q.explanation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "💡 Explanation: ${q.explanation}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF15803D),
                                        lineHeight = 16.sp
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

@Composable
fun BookmarkCard(
    bookmark: MCQBookmark,
    currentTheme: Int,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTheme == 1) Color(0xFF1E293B) else Color.White
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = bookmark.chapterName.ifBlank { "MCQ Practice" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Bookmark",
                        tint = Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val parsedContent = remember(bookmark.id) { parseMCQContentFromBookmark(bookmark) }
            TextbookQuestionContent(content = parsedContent, isDarkTheme = currentTheme == 1)

            Spacer(modifier = Modifier.height(10.dp))

            // Options
            val letters = listOf("A", "B", "C", "D", "E")
            bookmark.options.forEachIndexed { idx, opt ->
                val isCorrect = idx == bookmark.correctOptionIndex
                Surface(
                    color = if (isCorrect) Color(0xFFDCFCE7) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${letters.getOrNull(idx) ?: (idx + 1)}. ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isCorrect) Color(0xFF15803D) else Color(0xFF64748B)
                        )
                        Text(
                            text = opt.text,
                            fontSize = 12.sp,
                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCorrect) Color(0xFF166534) else Color(0xFF1E293B)
                        )
                        if (isCorrect) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✓ Correct", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        }
                    }
                }
            }

            if (bookmark.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "💡 Explanation:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF15803D)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bookmark.explanation,
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B),
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}
