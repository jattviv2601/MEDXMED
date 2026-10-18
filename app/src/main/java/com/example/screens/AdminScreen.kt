package com.example.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.util.extractYouTubeVideoId
import com.example.util.FlashcardUtils
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import com.example.data.MedRepository
import com.example.data.cloud.CloudflareR2Manager
import com.example.data.cloud.CloudSyncEngine
import com.example.model.*
import com.example.Screen
import com.example.screens.EditMCQSectionDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

private val adminUploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

const val ADMIN_SECURITY_PASSWORD = "526255"

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (name.isNullOrBlank()) {
        name = uri.lastPathSegment?.substringAfterLast('/')
    }
    return name ?: "notes_${System.currentTimeMillis()}.pdf"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLockView(
    onUnlock: () -> Unit,
    onBackClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun verifyPin(input: String) {
        if (input == ADMIN_SECURITY_PASSWORD) {
            isError = false
            errorMessage = ""
            onUnlock()
        } else {
            isError = true
            errorMessage = "Incorrect Password! Access Denied."
            pin = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = "Admin Security Lock",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A8A)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E3A8A)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFEFF6FF))
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Security Shield Icon
            Surface(
                shape = CircleShape,
                color = Color(0xFFDBEAFE),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF93C5FD)),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Admin Access Protected",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Please enter 6-digit security password to unlock Admin controls & uploads.",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN Indicator Boxes (6 Digits)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val isFilled = i < pin.length
                    val isCurrent = i == pin.length
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isError -> Color(0xFFFEE2E2)
                            isFilled -> Color(0xFF2563EB)
                            isCurrent -> Color(0xFFEFF6FF)
                            else -> Color(0xFFF1F5F9)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = when {
                                isError -> Color(0xFFEF4444)
                                isFilled -> Color(0xFF2563EB)
                                isCurrent -> Color(0xFF3B82F6)
                                else -> Color(0xFFCBD5E1)
                            }
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isFilled) {
                                if (showPassword) {
                                    Text(
                                        text = pin[i].toString(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }

            if (isError) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️ ", fontSize = 13.sp)
                        Text(
                            text = errorMessage,
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Direct keyboard textfield
            OutlinedTextField(
                value = pin,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(6)
                    pin = filtered
                    isError = false
                    errorMessage = ""
                    if (filtered.length == 6) {
                        verifyPin(filtered)
                    }
                },
                label = { Text("Enter 6-Digit Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        verifyPin(pin)
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Tactile Number Pad
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val keyRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("Clear", "0", "⌫")
                    )

                    keyRows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                Surface(
                                    onClick = {
                                        when (key) {
                                            "Clear" -> {
                                                pin = ""
                                                isError = false
                                                errorMessage = ""
                                            }
                                            "⌫" -> {
                                                if (pin.isNotEmpty()) {
                                                    pin = pin.dropLast(1)
                                                    isError = false
                                                    errorMessage = ""
                                                }
                                            }
                                            else -> {
                                                if (pin.length < 6) {
                                                    val nextPin = pin + key
                                                    pin = nextPin
                                                    isError = false
                                                    errorMessage = ""
                                                    if (nextPin.length == 6) {
                                                        verifyPin(nextPin)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = when (key) {
                                        "Clear", "⌫" -> Color(0xFFF1F5F9)
                                        else -> Color(0xFFF8FAFC)
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = key,
                                            fontSize = if (key.length > 1) 13.sp else 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (key) {
                                                "Clear" -> Color(0xFFDC2626)
                                                "⌫" -> Color(0xFF1E293B)
                                                else -> Color(0xFF0F172A)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { verifyPin(pin) },
                enabled = pin.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock Admin Panel", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel & Go Back", color = Color(0xFF64748B), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBackClick: () -> Unit,
    navigateTo: (Screen) -> Unit
) {
    var isUnlocked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    if (!isUnlocked) {
        AdminLockView(
            onUnlock = { isUnlocked = true },
            onBackClick = onBackClick
        )
        return
    }

    var mainTabState by remember { mutableIntStateOf(0) } // 0 = Upload Center, 1 = Manage Library
    var subUploadTab by remember { mutableIntStateOf(0) }
    var isViewingUploadForm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isSyncing by CloudSyncEngine.isSyncing.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showR2ConfigDialog by remember { mutableStateOf(false) }
    var showAppInfoConfigDialog by remember { mutableStateOf(false) }

    var showPurgeConfirmation by remember { mutableStateOf(false) }

    if (showPurgeConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmation = false },
            title = { Text("Delete Excluded Sections Data?", fontWeight = FontWeight.Bold) },
            text = { Text("Permanently delete all stored data for Custom Sections, PYQs, Flashcards, and MCQs? This action is irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        MedRepository.purgeAdminExcludedData()
                        showPurgeConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Complete Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete All Content?") },
            text = { Text("Are you sure you want to delete ALL content folders? This action cannot be undone, and all associated files will be removed from storage.") },
            confirmButton = {
                TextButton(onClick = {
                    MedRepository.deleteAllFolders()
                    showDeleteConfirmation = false
                }) {
                    Text("Delete All", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    modifier = Modifier.height(52.dp),
                    title = {
                        Text(
                            text = "Admin Panel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF1E3A8A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        var showAdminMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showAdminMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Admin Menu",
                                    tint = Color(0xFF1E3A8A)
                                )
                            }
                            DropdownMenu(
                                expanded = showAdminMenu,
                                onDismissRequest = { showAdminMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Purge Excluded Data") },
                                    onClick = { showAdminMenu = false; showPurgeConfirmation = true },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFDC2626)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete All Content") },
                                    onClick = { showAdminMenu = false; showDeleteConfirmation = true },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Lock Admin") },
                                    onClick = { showAdminMenu = false; isUnlocked = false },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("App Info & Notice URLs") },
                                    onClick = { showAdminMenu = false; showAppInfoConfigDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Cloudflare R2 Settings") },
                                    onClick = { showAdminMenu = false; showR2ConfigDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = if (CloudflareR2Manager.isConfigured()) Color(0xFF16A34A) else Color(0xFFDC2626)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sync from Cloud") },
                                    onClick = {
                                        showAdminMenu = false
                                        coroutineScope.launch {
                                            CloudSyncEngine.syncFromCloud(context)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF2563EB)) }
                                )
                            }
                        }
                    },
                                        windowInsets = WindowInsets(0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFEFF6FF))
                )
                HorizontalDivider(thickness = 1.dp, color = Color(0xFFBFDBFE))
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!CloudflareR2Manager.isConfigured()) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showR2ConfigDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Cloudflare R2 API Keys Not Set!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "Click to set Access Key ID & Secret Key to upload files directly to Cloudflare bucket.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                        Surface(
                            color = Color(0xFFDC2626),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Setup ➔", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }



            // Main Top level tab selector: Upload vs Manage
            TabRow(
                selectedTabIndex = mainTabState,
                containerColor = Color(0xFFF1F5F9),
                contentColor = Color(0xFF2563EB),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = mainTabState == 0,
                    onClick = { mainTabState = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Center", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )
                Tab(
                    selected = mainTabState == 1,
                    onClick = { mainTabState = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage Library", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )
            }

                        // Only show folders/categories unless one is selected in Upload Center
            if (mainTabState == 0 && !isViewingUploadForm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Select Upload Category", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF1E293B))

                    val uploadOptions = listOf(
                        Triple(0, "📚 Notes & PDF Upload", "Upload lecture notes, PDFs & structured chapters"),
                        Triple(1, "🎬 Shorts Upload", "Upload clinical mnemonic videos & shorts"),
                        Triple(2, "📇 Flashcard Upload", "Upload clinical flashcards, decks, subfolders & JSON"),
                        Triple(3, "⚙️ App Info & Notice Settings", "Configure notices, about us, privacy & URLs")
                    )
                    uploadOptions.forEach { (idx, title, desc) ->
                        Surface(
                            onClick = {
                                subUploadTab = idx
                                isViewingUploadForm = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(if (idx == 0) "📚" else if (idx == 1) "🎬" else if (idx == 2) "📇" else "⚙️", fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            } else if (mainTabState == 1) {
                ManageSectionView(navigateTo = navigateTo)
            } else {
                Surface(
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isViewingUploadForm = false }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2563EB))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (subUploadTab) {
                                    0 -> "📚 Notes & PDF Upload"
                                    1 -> "🎬 Shorts Upload"
                                    2 -> "📇 Flashcard Upload"
                                    else -> "⚙️ App Info & Notice Settings"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                        TextButton(onClick = { isViewingUploadForm = false }) {
                            Text("📁 Change Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                when (subUploadTab) {
                    0 -> HierarchyUploadView(onOpenR2Settings = { showR2ConfigDialog = true })
                    1 -> UploadShortsView()
                    2 -> FlashcardUploadView()
                    3 -> AppInfoAndLinksManageView()
                }
            }
        }
    }

    if (showAppInfoConfigDialog) {
        AppInfoSettingsDialog(onDismiss = { showAppInfoConfigDialog = false })
    }

    val currentR2AccountId by CloudflareR2Manager.accountId.collectAsState()
    val currentR2AccessKey by CloudflareR2Manager.accessKeyId.collectAsState()
    val currentR2SecretKey by CloudflareR2Manager.secretAccessKey.collectAsState()
    val currentR2Bucket by CloudflareR2Manager.bucketName.collectAsState()
    val currentR2PublicBaseUrl by CloudflareR2Manager.publicBaseUrl.collectAsState()

    if (showR2ConfigDialog) {
        var r2AccountId by remember(currentR2AccountId) { mutableStateOf(currentR2AccountId) }
        var r2AccessKey by remember(currentR2AccessKey) { mutableStateOf(currentR2AccessKey) }
        var r2SecretKey by remember(currentR2SecretKey) { mutableStateOf(currentR2SecretKey) }
        var r2Bucket by remember(currentR2Bucket) { mutableStateOf(currentR2Bucket) }
        var r2PublicBaseUrl by remember(currentR2PublicBaseUrl) { mutableStateOf(currentR2PublicBaseUrl) }
        var testStatus by remember { mutableStateOf<String?>(null) }
        var isTesting by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showR2ConfigDialog = false }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth().padding(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("☁️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cloudflare R2 Bucket Setup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                        IconButton(onClick = { showR2ConfigDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                    // Test Status Banner
                    testStatus?.let { status ->
                        Surface(
                            color = if (status.startsWith("✓")) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = status,
                                color = if (status.startsWith("✓")) Color(0xFF15803D) else Color(0xFFDC2626),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = r2AccountId,
                        onValueChange = { r2AccountId = it },
                        label = { Text("Cloudflare Account ID *") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = r2AccessKey,
                        onValueChange = { r2AccessKey = it },
                        label = { Text("R2 Access Key ID *") },
                        placeholder = { Text("e.g. 8f4e9123abc...", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = r2SecretKey,
                        onValueChange = { r2SecretKey = it },
                        label = { Text("R2 Secret Access Key *") },
                        placeholder = { Text("e.g. 7c3d1045def...", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = r2Bucket,
                        onValueChange = { r2Bucket = it },
                        label = { Text("Bucket Name *") },
                        placeholder = { Text("e.g. medxrattamaar", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = r2PublicBaseUrl,
                        onValueChange = { r2PublicBaseUrl = it },
                        label = { Text("Public CDN / r2.dev Base URL") },
                        placeholder = { Text("https://pub-...r2.dev", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test Connection Button
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testStatus = "Testing connection to Cloudflare R2..."
                            coroutineScope.launch {
                                // Save current fields first
                                CloudflareR2Manager.saveCredentials(r2AccountId, r2AccessKey, r2SecretKey, r2Bucket, r2PublicBaseUrl)
                                val res = CloudflareR2Manager.testConnection()
                                isTesting = false
                                testStatus = if (res.isSuccess) {
                                    "✓ ${res.getOrThrow()}"
                                } else {
                                    "❌ ${res.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        enabled = !isTesting && r2AccountId.isNotBlank() && r2AccessKey.isNotBlank() && r2SecretKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isTesting) "Testing..." else "⚡ Test Connection with R2", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showR2ConfigDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                CloudflareR2Manager.saveCredentials(r2AccountId, r2AccessKey, r2SecretKey, r2Bucket, r2PublicBaseUrl)
                                showR2ConfigDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save & Connect", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }


}

@Composable
fun HierarchyUploadView(onOpenR2Settings: () -> Unit = {}) {
    val folders by MedRepository.folders.collectAsState()
    val allContents by MedRepository.contents.collectAsState()

    // 1. Content / Section Type (PDF Notes vs Video Lectures)
    var contentType by remember { mutableStateOf(StudyContentType.PDF) } // Default to PDF Notes
    val targetSectionType = if (contentType == StudyContentType.PDF) "PDF" else "VIDEO"

    // 2. Prof
    var selectedProf by remember { mutableStateOf(ProfYear.FIRST_PROF) }
    val subjects = remember(selectedProf) { MedRepository.getSubjectsForProf(selectedProf) }

    // 3. Subject
    var selectedSubject by remember(selectedProf) {
        mutableStateOf(subjects.firstOrNull() ?: MedRepository.allSubjects.first())
    }

    // Filtered folders strictly for selected prof + subject + sectionType (PDF vs Video)
    val availableFolders = remember(folders, selectedProf, selectedSubject, targetSectionType) {
        folders.filter {
            it.prof == selectedProf && it.subjectId == selectedSubject.id &&
            (it.sectionType == targetSectionType || it.sectionType == "ALL")
        }
    }

    // 4. Folder mode: Existing vs Create New
    var isCreatingNewFolder by remember { mutableStateOf(availableFolders.isEmpty()) }
    var selectedFolderId by remember(availableFolders) { mutableStateOf(availableFolders.firstOrNull()?.id ?: "") }
    var newFolderName by remember { mutableStateOf("") }
    var selectedFolderEmoji by remember { mutableStateOf("📁") }
    val emojiChoices = listOf("📁", "📚", "🔬", "🫀", "🧠", "🦴", "🧪", "💊", "👁️", "👶")

    // 5. Chapter / Subfolder (Optional - Inside Folder)
    // Mode: 0 = None (Direct in Folder), 1 = Select Existing, 2 = Create New
    val existingChaptersInFolder = remember(allContents, selectedFolderId, isCreatingNewFolder) {
        if (isCreatingNewFolder || selectedFolderId.isBlank()) emptyList()
        else allContents.filter { it.folderId == selectedFolderId && it.chapterName.isNotBlank() }
            .map { it.chapterName.trim() }
            .distinct()
    }

    var chapterMode by remember(selectedFolderId, isCreatingNewFolder) {
        mutableIntStateOf(0) // Default: None
    }
    var selectedExistingChapter by remember(existingChaptersInFolder) {
        mutableStateOf(existingChaptersInFolder.firstOrNull() ?: "")
    }
    var newChapterName by remember { mutableStateOf("") }

    // 6. Content Details
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customUrlOrFile by remember { mutableStateOf("") }

    val context = LocalContext.current
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfFileName by remember { mutableStateOf<String?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            val fname = getFileNameFromUri(context, uri)
            selectedPdfFileName = fname
            if (title.isBlank()) {
                title = fname.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            }
        }
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Update isCreatingNewFolder when availableFolders changes
    LaunchedEffect(availableFolders) {
        if (availableFolders.isEmpty()) {
            isCreatingNewFolder = true
        } else if (selectedFolderId.isBlank() || availableFolders.none { it.id == selectedFolderId }) {
            selectedFolderId = availableFolders.first().id
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        statusMessage?.let { msg ->
            item {
                val isUploading = msg.contains("Uploading")
                val isError = msg.startsWith("⚠️") || msg.startsWith("❌")
                Surface(
                    color = when {
                        isUploading -> Color(0xFFEFF6FF)
                        isError -> Color(0xFFFEE2E2)
                        else -> Color(0xFFDCFCE7)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            isUploading -> Color(0xFFBFDBFE)
                            isError -> Color(0xFFFCA5A5)
                            else -> Color(0xFF86EFAC)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = msg,
                            color = when {
                                isUploading -> Color(0xFF1D4ED8)
                                isError -> Color(0xFFDC2626)
                                else -> Color(0xFF15803D)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // STEP 1: SECTION / CONTENT TYPE (PDF Notes vs Video Lectures)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Target Section", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { contentType = StudyContentType.PDF },
                            shape = RoundedCornerShape(10.dp),
                            color = if (contentType == StudyContentType.PDF) Color(0xFF059669) else Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📝 PDF Notes Section", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (contentType == StudyContentType.PDF) Color.White else Color(0xFF334155))
                            }
                        }

                        Surface(
                            onClick = { contentType = StudyContentType.VIDEO },
                            shape = RoundedCornerShape(10.dp),
                            color = if (contentType == StudyContentType.VIDEO) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎥 Videos Section", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (contentType == StudyContentType.VIDEO) Color.White else Color(0xFF334155))
                            }
                        }
                    }
                }
            }
        }

        // STEP 2: PROF
        item {
            Text("Select Prof", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProfYear.values().forEach { prof ->
                    val isSelected = selectedProf == prof
                    Surface(
                        onClick = {
                            selectedProf = prof
                            selectedSubject = MedRepository.getSubjectsForProf(prof).firstOrNull() ?: MedRepository.allSubjects.first()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = prof.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF475569),
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // STEP 3: SUBJECT
        item {
            Text("Select Subject", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(subjects) { sub ->
                    val isSelected = selectedSubject.id == sub.id
                    Surface(
                        onClick = { selectedSubject = sub },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color.White,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(sub.emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                sub.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }

        // STEP 4: FOLDER (Existing vs Create New - Strictly for this Section)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Main Folder", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))

                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !isCreatingNewFolder,
                                onClick = { isCreatingNewFolder = false },
                                label = { Text("Existing", fontSize = 11.sp) },
                                enabled = availableFolders.isNotEmpty()
                            )
                            FilterChip(
                                selected = isCreatingNewFolder,
                                onClick = { isCreatingNewFolder = true },
                                label = { Text("+ Create New", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isCreatingNewFolder && availableFolders.isNotEmpty()) {
                        // Select from existing section folders
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableFolders) { folder ->
                                val isSelected = selectedFolderId == folder.id
                                Surface(
                                    onClick = { selectedFolderId = folder.id },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(folder.emoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            folder.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Create New Folder
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("Folder Name") },
                            placeholder = { Text("Enter folder title", color = Color(0xFF94A3B8)) },
                            textStyle = com.example.ui.theme.medInputTextStyle(),
                            colors = com.example.ui.theme.medTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // STEP 5: CHAPTER / SUBFOLDER (Optional Subfolder inside Folder)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Chapter / Subfolder (Optional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode chips: None vs Existing Chapter vs New Chapter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = chapterMode == 0,
                            onClick = { chapterMode = 0 },
                            label = { Text("None (Direct)", fontSize = 11.sp) }
                        )
                        if (existingChaptersInFolder.isNotEmpty()) {
                            FilterChip(
                                selected = chapterMode == 1,
                                onClick = { chapterMode = 1 },
                                label = { Text("Select Existing (${existingChaptersInFolder.size})", fontSize = 11.sp) }
                            )
                        }
                        FilterChip(
                            selected = chapterMode == 2,
                            onClick = { chapterMode = 2 },
                            label = { Text("+ New Chapter", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (chapterMode) {
                        0 -> {

                        }
                        1 -> {
                            // Existing Chapters chips
                            Text("Select an existing subfolder/chapter:", fontSize = 11.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(existingChaptersInFolder) { ch ->
                                    val isSelected = selectedExistingChapter.equals(ch, ignoreCase = true)
                                    Surface(
                                        onClick = { selectedExistingChapter = ch },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Text(
                                            text = "🗂️ $ch",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Create New Chapter Name
                            OutlinedTextField(
                                value = newChapterName,
                                onValueChange = { newChapterName = it },
                                label = { Text("New Chapter / Subfolder Name") },
                                placeholder = { Text("e.g. Chapter 1: Brachial Plexus, Cardiac Cycle", color = Color(0xFF94A3B8)) },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        // STEP 6: CONTENT DETAILS & FILE PICKER
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("6. File & Details (Cloudflare R2):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (contentType == StudyContentType.VIDEO) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Video Title *") },
                            placeholder = { Text("e.g. Brachial Plexus Masterclass", color = Color(0xFF94A3B8)) },
                            textStyle = com.example.ui.theme.medInputTextStyle(),
                            colors = com.example.ui.theme.medTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (Optional)") },
                            textStyle = com.example.ui.theme.medInputTextStyle(),
                            colors = com.example.ui.theme.medTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customUrlOrFile,
                            onValueChange = { customUrlOrFile = it },
                            label = { Text("YouTube URL / Video File Link *") },
                            placeholder = { Text("e.g. https://youtu.be/... or lecture.mp4", color = Color(0xFF94A3B8)) },
                            textStyle = com.example.ui.theme.medInputTextStyle(),
                            colors = com.example.ui.theme.medTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        if (customUrlOrFile.isNotBlank()) {
                            val ytId = extractYouTubeVideoId(customUrlOrFile)
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (ytId != null) "🎬 Detected YouTube Video (Plays in App):" else "🎥 Video Stream URL (Plays in App):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2563EB)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (ytId != null) {
                                        coil.compose.AsyncImage(
                                            model = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                                            contentDescription = "Video preview",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // PDF Notes - Direct file upload from device / gallery
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (selectedPdfUri != null) {
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Text("📄", fontSize = 28.sp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = selectedPdfFileName ?: "Document.pdf",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF15803D),
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "✓ PDF Selected from Device Storage",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF16A34A)
                                                )
                                            }
                                        }

                                        IconButton(onClick = {
                                            selectedPdfUri = null
                                            selectedPdfFileName = null
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF15803D))
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Choose Different PDF from Device", fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("📁 Select & Upload PDF from Device", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("PDF Title / Subject Topic") },
                                placeholder = { Text(selectedPdfFileName?.substringBeforeLast(".") ?: "e.g. Anatomy High Yield Notes", color = Color(0xFF94A3B8)) },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description / Notes Summary (Optional)") },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                minLines = 2
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cloudflare R2 Storage Preview Path
                    val folderLabel = if (isCreatingNewFolder) newFolderName.ifBlank { "Folder" } else (availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: "Folder")
                    val fileNamePreview = if (contentType == StudyContentType.PDF) (selectedPdfFileName ?: (title.ifBlank { "document" } + ".pdf")) else (customUrlOrFile.ifBlank { title.ifBlank { "video" } + ".mp4" })
                    val cloudflarePreview = MedRepository.buildCloudflareUrl(selectedProf, selectedSubject.id, folderLabel, fileNamePreview)

                    Text(
                        text = "Cloudflare R2 Key: $cloudflarePreview",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 2
                    )
                }
            }
        }

        // ACTION BUTTONS: Upload & Save + Add Another
        item {
            val canSubmit = (!isCreatingNewFolder || newFolderName.isNotBlank()) &&
                ((contentType == StudyContentType.PDF && (selectedPdfUri != null || title.isNotBlank())) ||
                 (contentType == StudyContentType.VIDEO && title.isNotBlank()))

            val isR2Configured = CloudflareR2Manager.isConfigured()

            fun executeUpload(onSuccessReset: () -> Unit) {
                if (!canSubmit) {
                    statusMessage = if (contentType == StudyContentType.PDF && selectedPdfUri == null && title.isBlank()) {
                        "Please select a PDF file from your device!"
                    } else {
                        "Please provide Title and Folder Name!"
                    }
                    return
                }

                // Determine final folder ID and Name
                val finalFolderId: String
                val finalFolderName: String

                if (isCreatingNewFolder) {
                    val folderId = "f_${UUID.randomUUID().toString().take(6)}"
                    val folder = ContentFolder(
                        id = folderId,
                        prof = selectedProf,
                        subjectId = selectedSubject.id,
                        name = newFolderName.trim(),
                        emoji = listOf("📂", "📚", "🔬", "🧪", "🧬", "🩺", "🫀", "🧠", "🦴", "💊", "🧫", "📊", "👁️", "👂").random(),
                        sectionType = targetSectionType
                    )
                    MedRepository.createFolder(folder)
                    finalFolderId = folderId
                    finalFolderName = newFolderName.trim()
                    isCreatingNewFolder = false
                    selectedFolderId = folderId
                } else {
                    finalFolderId = selectedFolderId
                    finalFolderName = availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: "General"
                }

                // Determine final Chapter/Subfolder name
                val finalChapterName = when (chapterMode) {
                    0 -> ""
                    1 -> selectedExistingChapter.trim()
                    2 -> newChapterName.trim()
                    else -> ""
                }

                val contentId = "c_${UUID.randomUUID().toString().take(6)}"
                val fileName = if (contentType == StudyContentType.PDF) {
                    selectedPdfFileName ?: (title.trim() + ".pdf")
                } else {
                    customUrlOrFile.ifBlank { title.trim() + ".mp4" }
                }

                val finalTitle = title.ifBlank {
                    selectedPdfFileName?.substringBeforeLast(".")?.replace("_", " ") ?: "Study Content"
                }

                val cloudflareUrl = MedRepository.buildCloudflareUrl(selectedProf, selectedSubject.id, finalFolderName, fileName)

                // 1. Save locally FIRST so content is immediately secure in Room database
                val initialMediaUrl = if (customUrlOrFile.startsWith("http")) customUrlOrFile else cloudflareUrl
                val studyItem = StudyContentItem(
                    id = contentId,
                    prof = selectedProf,
                    subjectId = selectedSubject.id,
                    folderId = finalFolderId,
                    folderName = finalFolderName,
                    chapterName = finalChapterName,
                    type = contentType,
                    title = finalTitle.trim(),
                    description = description.trim(),
                    mediaUrl = initialMediaUrl,
                    cloudflareKey = cloudflareUrl
                )
                MedRepository.addStudyContent(studyItem)

                if (contentType == StudyContentType.PDF && selectedPdfUri != null) {
                    statusMessage = "Uploading PDF to Cloudflare R2... 0%"
                    val uploadUri = selectedPdfUri!!
                    adminUploadScope.launch {
                        try {
                            val cleanProf = selectedProf.code
                            val cleanSubject = selectedSubject.id.trim().replace(" ", "_")
                            val cleanFolder = finalFolderName.trim().replace(" ", "_")
                            val cleanFile = fileName.trim().replace(" ", "_")
                            val objectKey = "/$cleanProf/$cleanSubject/$cleanFolder/$cleanFile"

                            val uploadResult = CloudflareR2Manager.uploadFile(
                                context = context,
                                fileUri = uploadUri,
                                objectKey = objectKey,
                                mimeType = "application/pdf"
                            ) { progress ->
                                statusMessage = "Uploading PDF to Cloudflare R2... ${(progress * 100).toInt()}%"
                            }

                            if (uploadResult.isSuccess) {
                                val publicUrl = uploadResult.getOrThrow()
                                MedRepository.updateStudyContentMediaUrl(contentId, publicUrl)
                                CloudSyncEngine.publishCatalogToCloud()
                                statusMessage = "✓ Successfully uploaded to Cloudflare & saved!"
                                onSuccessReset()
                            } else {
                                val err = uploadResult.exceptionOrNull()?.localizedMessage ?: "Upload failed"
                                statusMessage = "⚠️ Saved locally in app, but Cloudflare upload failed: $err"
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            e.printStackTrace()
                            statusMessage = "⚠️ Saved locally in app, but upload error: ${e.localizedMessage}"
                        }
                    }
                } else {
                    MedRepository.triggerCatalogPublish()
                    statusMessage = "✓ Successfully saved!"
                    onSuccessReset()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        executeUpload {
                            title = ""
                            description = ""
                            customUrlOrFile = ""
                            selectedPdfUri = null
                            selectedPdfFileName = null
                            newChapterName = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (contentType == StudyContentType.PDF) Color(0xFF059669) else Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload & Save to Cloud", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Add Other Button for quick bulk upload in same section
                OutlinedButton(
                    onClick = {
                        executeUpload {
                            title = ""
                            description = ""
                            customUrlOrFile = ""
                            selectedPdfUri = null
                            selectedPdfFileName = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Other (Same Section)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun PYQUploadView(onOpenR2Settings: () -> Unit = {}) {
    val folders by MedRepository.folders.collectAsState()
    val contents by MedRepository.contents.collectAsState()

    // Filter strictly PYQ folders so admin can easily pick existing PYQ folders or create new ones
    val availableFolders = remember(folders, contents) {
        val explicitFolders = folders.filter { 
            it.sectionType.equals("PYQ", ignoreCase = true) || 
            it.subjectId.equals("pyq", ignoreCase = true) || 
            it.name.contains("PYQ", ignoreCase = true) || 
            it.name.contains("Paper", ignoreCase = true) 
        }
        val itemFolderNames = contents.filter { it.isPyq || it.subjectId.equals("pyq", ignoreCase = true) }
            .map { it.folderName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val existingNames = explicitFolders.map { it.name.trim().lowercase() }.toSet()
        val virtualFolders = itemFolderNames.filter { it.lowercase() !in existingNames }.map { name ->
            ContentFolder(
                id = "pyq_folder_${name.hashCode().toString().replace("-", "n")}",
                prof = ProfYear.FIRST_PROF,
                subjectId = "pyq",
                name = name,
                emoji = "📝",
                sectionType = "PYQ"
            )
        }
        (explicitFolders + virtualFolders).distinctBy { it.name.trim().lowercase() }.sortedBy { it.name.lowercase() }
    }

    var isCreatingNewFolder by remember { mutableStateOf(availableFolders.isEmpty()) }
    var selectedFolderId by remember(availableFolders) { mutableStateOf(availableFolders.firstOrNull()?.id ?: "") }
    var newFolderName by remember { mutableStateOf("") }

    // Subfolders / Chapters existing in chosen PYQ Folder
    val existingChaptersInPYQ = remember(contents, selectedFolderId, isCreatingNewFolder, availableFolders, newFolderName) {
        val targetFolderName = if (!isCreatingNewFolder) {
            availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: ""
        } else {
            newFolderName.trim()
        }
        if (targetFolderName.isBlank()) emptyList()
        else contents.filter { it.isPyq && it.folderName.equals(targetFolderName, ignoreCase = true) }
            .map { it.chapterName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    var chapterMode by remember { mutableIntStateOf(0) } // 0 = None (Direct), 1 = Existing Subfolder, 2 = New Subfolder
    var selectedExistingSubfolder by remember { mutableStateOf("") }
    var newSubfolderName by remember { mutableStateOf("") }

    LaunchedEffect(existingChaptersInPYQ) {
        if (existingChaptersInPYQ.isNotEmpty() && selectedExistingSubfolder.isBlank()) {
            selectedExistingSubfolder = existingChaptersInPYQ.first()
        }
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val context = LocalContext.current
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfFileName by remember { mutableStateOf<String?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            val fname = getFileNameFromUri(context, uri)
            selectedPdfFileName = fname
            if (title.isBlank()) {
                title = fname.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            }
        }
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(availableFolders) {
        if (availableFolders.isEmpty()) {
            isCreatingNewFolder = true
        } else if (selectedFolderId.isBlank() || availableFolders.none { it.id == selectedFolderId }) {
            selectedFolderId = availableFolders.first().id
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        statusMessage?.let { msg ->
            item {
                val isUploading = msg.contains("Uploading")
                val isError = msg.startsWith("⚠️") || msg.startsWith("❌")
                Surface(
                    color = when {
                        isUploading -> Color(0xFFEFF6FF)
                        isError -> Color(0xFFFEE2E2)
                        else -> Color(0xFFDCFCE7)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            isUploading -> Color(0xFFBFDBFE)
                            isError -> Color(0xFFFCA5A5)
                            else -> Color(0xFF86EFAC)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = msg,
                            color = when {
                                isUploading -> Color(0xFF1D4ED8)
                                isError -> Color(0xFFDC2626)
                                else -> Color(0xFF15803D)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // STEP 1: PYQ FOLDER (Existing vs Create New)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. Select PYQ Folder:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !isCreatingNewFolder,
                                onClick = { isCreatingNewFolder = false },
                                label = { Text("Existing Folders", fontSize = 11.sp) },
                                enabled = availableFolders.isNotEmpty()
                            )
                            FilterChip(
                                selected = isCreatingNewFolder,
                                onClick = { isCreatingNewFolder = true },
                                label = { Text("+ Create New Folder", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isCreatingNewFolder && availableFolders.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableFolders) { folder ->
                                val isSelected = selectedFolderId == folder.id
                                Surface(
                                    onClick = { selectedFolderId = folder.id },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text("📝", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            folder.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("New PYQ Folder Name") },
                            placeholder = { Text("e.g. 2020-2025 Professional Exam Papers", color = Color(0xFF94A3B8)) },
                            textStyle = com.example.ui.theme.medInputTextStyle(),
                            colors = com.example.ui.theme.medTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // STEP 2: SUBFOLDER / CHAPTER (Optional)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("2. Subfolder / Chapter Name (Optional):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Organizes papers into a subfolder/chapter inside the PYQ folder", fontSize = 11.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = chapterMode == 0,
                            onClick = { chapterMode = 0 },
                            label = { Text("None (Direct)", fontSize = 11.sp) }
                        )
                        if (existingChaptersInPYQ.isNotEmpty()) {
                            FilterChip(
                                selected = chapterMode == 1,
                                onClick = { chapterMode = 1 },
                                label = { Text("Existing Subfolder (${existingChaptersInPYQ.size})", fontSize = 11.sp) }
                            )
                        }
                        FilterChip(
                            selected = chapterMode == 2,
                            onClick = { chapterMode = 2 },
                            label = { Text("+ New Subfolder", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (chapterMode) {
                        0 -> {
                            Text(
                                text = "ℹ️ Paper will be placed directly in the main PYQ folder.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        1 -> {
                            Text("Select an existing subfolder/chapter:", fontSize = 11.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(existingChaptersInPYQ) { sub ->
                                    val isSelected = selectedExistingSubfolder.equals(sub, ignoreCase = true)
                                    Surface(
                                        onClick = { selectedExistingSubfolder = sub },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Text(
                                            text = "📁 $sub",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            OutlinedTextField(
                                value = newSubfolderName,
                                onValueChange = { newSubfolderName = it },
                                label = { Text("New Subfolder / Chapter Name") },
                                placeholder = { Text("e.g. Paper 1 - General Pathology", color = Color(0xFF94A3B8)) },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        // STEP 5: UPLOAD PDF FROM DEVICE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("5. Upload PYQ PDF Notes:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedPdfUri != null) {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("📄", fontSize = 28.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = selectedPdfFileName ?: "PYQ.pdf",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF15803D),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "✓ PYQ PDF Selected from Device Storage",
                                            fontSize = 11.sp,
                                            color = Color(0xFF16A34A)
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    selectedPdfUri = null
                                    selectedPdfFileName = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF15803D))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Different PDF", fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📁 Select & Upload PYQ PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("PYQ Paper Title *") },
                        placeholder = { Text(selectedPdfFileName?.substringBeforeLast(".") ?: "e.g. Professional Exam 2024 PYQ", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Answer Key Notes (Optional)") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2
                    )
                }
            }
        }

        // SUBMIT
        item {
            val canSubmit = (!isCreatingNewFolder || newFolderName.isNotBlank()) && (selectedPdfUri != null || title.isNotBlank())

            val isR2Configured = CloudflareR2Manager.isConfigured()

            fun executeUpload(onSuccessReset: () -> Unit) {
                if (!canSubmit) {
                    statusMessage = "Please select a PDF file and provide Title!"
                    return
                }

                val finalFolderId: String
                val finalFolderName: String

                if (isCreatingNewFolder) {
                    val folderId = "pyq_f_${UUID.randomUUID().toString().take(6)}"
                    val folder = ContentFolder(
                        id = folderId,
                        prof = ProfYear.FIRST_PROF,
                        subjectId = "pyq",
                        name = newFolderName.trim(),
                        emoji = listOf("📝", "📂", "📚", "📊", "🩺", "🔬").random(),
                        sectionType = "PYQ"
                    )
                    MedRepository.createFolder(folder)
                    finalFolderId = folderId
                    finalFolderName = newFolderName.trim()
                    isCreatingNewFolder = false
                    selectedFolderId = folderId
                } else {
                    finalFolderId = selectedFolderId
                    finalFolderName = availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: "PYQ Bank"
                }

                val finalChapterName = when (chapterMode) {
                    1 -> selectedExistingSubfolder.trim()
                    2 -> newSubfolderName.trim()
                    else -> ""
                }

                val contentId = "pyq_c_${UUID.randomUUID().toString().take(6)}"
                val fileName = selectedPdfFileName ?: (title.trim() + ".pdf")
                val finalTitle = title.ifBlank {
                    selectedPdfFileName?.substringBeforeLast(".")?.replace("_", " ") ?: "PYQ Paper"
                }

                // Clean Cloudflare R2 path under pyq/ - NO THIRD_PROF_2!
                val cleanFolder = finalFolderName.trim().replace(" ", "_")
                val cleanFile = fileName.trim().replace(" ", "_")
                val cleanChapter = finalChapterName.trim().replace(" ", "_")
                val objectKey = if (cleanChapter.isNotBlank()) {
                    "pyq/$cleanFolder/$cleanChapter/$cleanFile"
                } else {
                    "pyq/$cleanFolder/$cleanFile"
                }
                val cloudflareUrl = "${CloudflareR2Manager.publicBaseUrl.value.trimEnd('/')}/$objectKey"

                // 1. Save locally FIRST so PYQ is immediately secure in Room database forever
                val studyItem = StudyContentItem(
                    id = contentId,
                    prof = ProfYear.FIRST_PROF,
                    subjectId = "pyq",
                    folderId = finalFolderId,
                    folderName = finalFolderName,
                    chapterName = finalChapterName,
                    type = StudyContentType.PDF,
                    title = finalTitle.trim(),
                    description = description.trim(),
                    mediaUrl = cloudflareUrl,
                    cloudflareKey = cloudflareUrl,
                    isPyq = true
                )
                MedRepository.addStudyContent(studyItem)

                if (selectedPdfUri != null) {
                    statusMessage = "Uploading PYQ to Cloudflare R2... 0%"
                    val uploadUri = selectedPdfUri!!
                    adminUploadScope.launch {
                        try {
                            val uploadResult = CloudflareR2Manager.uploadFile(
                                context = context,
                                fileUri = uploadUri,
                                objectKey = objectKey,
                                mimeType = "application/pdf"
                            ) { progress ->
                                statusMessage = "Uploading PYQ to Cloudflare R2... ${(progress * 100).toInt()}%"
                            }

                            if (uploadResult.isSuccess) {
                                val publicUrl = uploadResult.getOrThrow()
                                MedRepository.updateStudyContentMediaUrl(contentId, publicUrl)
                                CloudSyncEngine.publishCatalogToCloud()
                                statusMessage = "✓ Successfully saved PYQ & uploaded to Cloudflare!"
                                onSuccessReset()
                            } else {
                                val err = uploadResult.exceptionOrNull()?.localizedMessage ?: "Upload failed"
                                statusMessage = "⚠️ Saved locally in app, but Cloudflare upload failed: $err"
                                CloudSyncEngine.publishCatalogToCloud()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            e.printStackTrace()
                            statusMessage = "⚠️ Saved locally in app, but upload error: ${e.localizedMessage}"
                            CloudSyncEngine.publishCatalogToCloud()
                        }
                    }
                } else {
                    adminUploadScope.launch {
                        CloudSyncEngine.publishCatalogToCloud()
                    }
                    statusMessage = "✓ Successfully saved PYQ!"
                    onSuccessReset()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        executeUpload {
                            title = ""
                            description = ""
                            selectedPdfUri = null
                            selectedPdfFileName = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload PYQ & Save to Cloud", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                OutlinedButton(
                    onClick = {
                        executeUpload {
                            title = ""
                            description = ""
                            selectedPdfUri = null
                            selectedPdfFileName = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Other PYQ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun FlashcardUploadView() {
    val folders by MedRepository.folders.collectAsState()
    val allFlashcards by MedRepository.flashcards.collectAsState()

    val availableFolders = remember(folders, allFlashcards) {
        val direct = folders.filter {
            it.sectionType.equals("FLASHCARD", ignoreCase = true) ||
            it.sectionType.equals("Flashcards", ignoreCase = true) ||
            it.subjectId.equals("flashcard", ignoreCase = true) ||
            it.name.contains("Flashcard", ignoreCase = true)
        }.toMutableList()
        val seenNames = direct.map { it.name.trim().lowercase() }.toSet()
        val fromCards = allFlashcards
            .map { it.folderName.trim() }
            .filter { it.isNotBlank() && !seenNames.contains(it.lowercase()) }
            .distinct()
            .map { name ->
                ContentFolder(
                    id = "f_${name.hashCode()}",
                    prof = ProfYear.FIRST_PROF,
                    subjectId = "flashcard",
                    name = name,
                    emoji = "📇",
                    sectionType = "FLASHCARD"
                )
            }
        direct + fromCards
    }

    var isCreatingNewFolder by remember { mutableStateOf(availableFolders.isEmpty()) }
    var selectedFolderId by remember(availableFolders) { mutableStateOf(availableFolders.firstOrNull()?.id ?: "") }
    var newFolderName by remember { mutableStateOf("") }

    // Subfolder state
    var subfolderMode by remember { mutableIntStateOf(0) } // 0 = None (Direct), 1 = Existing Subfolder, 2 = New Subfolder
    var selectedExistingSubfolder by remember { mutableStateOf("") }
    var newSubfolderName by remember { mutableStateOf("") }

    // Existing Subfolders inside chosen folder
    val existingSubfoldersInFlashcards = remember(allFlashcards, selectedFolderId, isCreatingNewFolder, availableFolders, newFolderName) {
        val targetFolderName = if (!isCreatingNewFolder) {
            availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: ""
        } else {
            newFolderName.trim()
        }
        if (targetFolderName.isBlank()) emptyList()
        else allFlashcards.filter { it.folderName.equals(targetFolderName, ignoreCase = true) || it.folderId == selectedFolderId }
            .map { it.subfolderName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    LaunchedEffect(existingSubfoldersInFlashcards) {
        if (existingSubfoldersInFlashcards.isNotEmpty() && selectedExistingSubfolder.isBlank()) {
            selectedExistingSubfolder = existingSubfoldersInFlashcards.first()
        }
    }

    // Chapter state
    var chapterMode by remember { mutableIntStateOf(0) } // 0 = None (Direct), 1 = Existing Chapter, 2 = New Chapter
    var selectedExistingChapter by remember { mutableStateOf("") }
    var newChapterName by remember { mutableStateOf("") }

    // Existing Chapters inside chosen folder and subfolder
    val existingChaptersInFlashcards = remember(allFlashcards, selectedFolderId, isCreatingNewFolder, availableFolders, newFolderName, subfolderMode, selectedExistingSubfolder, newSubfolderName) {
        val targetFolderName = if (!isCreatingNewFolder) {
            availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: ""
        } else {
            newFolderName.trim()
        }
        val targetSubfolder = when (subfolderMode) {
            1 -> selectedExistingSubfolder.trim()
            2 -> newSubfolderName.trim()
            else -> ""
        }
        if (targetFolderName.isBlank()) emptyList()
        else allFlashcards.filter { 
            (it.folderName.equals(targetFolderName, ignoreCase = true) || it.folderId == selectedFolderId) &&
            (targetSubfolder.isBlank() || it.subfolderName.equals(targetSubfolder, ignoreCase = true))
        }
            .map { it.chapterName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    LaunchedEffect(existingChaptersInFlashcards) {
        if (existingChaptersInFlashcards.isNotEmpty() && selectedExistingChapter.isBlank()) {
            selectedExistingChapter = existingChaptersInFlashcards.first()
        }
    }

    var flashcardTab by remember { mutableIntStateOf(0) }
    var jsonInput by remember { mutableStateOf("") }

    var manualQuestion by remember { mutableStateOf("") }
    var manualAnswer by remember { mutableStateOf("") }
    var manualHint by remember { mutableStateOf("") }
    var manualTags by remember { mutableStateOf("High-Yield") }

    var questionImageUri by remember { mutableStateOf<Uri?>(null) }
    var answerImageUri by remember { mutableStateOf<Uri?>(null) }

    val questionImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) questionImageUri = uri }

    val answerImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) answerImageUri = uri }

    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(availableFolders) {
        if (availableFolders.isEmpty()) {
            isCreatingNewFolder = true
        } else if (selectedFolderId.isBlank() || availableFolders.none { it.id == selectedFolderId }) {
            selectedFolderId = availableFolders.first().id
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        statusMessage?.let { msg ->
            item {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // STEP 1: FLASHCARD FOLDER (Existing vs Create New)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. Select Flashcard Folder:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !isCreatingNewFolder,
                                onClick = { isCreatingNewFolder = false },
                                label = { Text("Existing Folders", fontSize = 11.sp) },
                                enabled = availableFolders.isNotEmpty()
                            )
                            FilterChip(
                                selected = isCreatingNewFolder,
                                onClick = { isCreatingNewFolder = true },
                                label = { Text("+ Create New Folder", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isCreatingNewFolder && availableFolders.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableFolders) { folder ->
                                val isSelected = selectedFolderId == folder.id
                                Surface(
                                    onClick = { selectedFolderId = folder.id },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else null
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(folder.emoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            folder.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("New Flashcard Folder Name") },
                            placeholder = { Text("e.g. Pharmacology Rapid Revision Cards", color = Color(0xFF94A3B8)) },
                            textStyle = com.example.ui.theme.medInputTextStyle(),
                            colors = com.example.ui.theme.medTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // STEP 2: SUBFOLDER (Optional)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("2. Select Subfolder (Optional):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = subfolderMode == 0,
                            onClick = { subfolderMode = 0 },
                            label = { Text("None (Direct)", fontSize = 11.sp) }
                        )
                        if (existingSubfoldersInFlashcards.isNotEmpty()) {
                            FilterChip(
                                selected = subfolderMode == 1,
                                onClick = { subfolderMode = 1 },
                                label = { Text("Existing (${existingSubfoldersInFlashcards.size})", fontSize = 11.sp) }
                            )
                        }
                        FilterChip(
                            selected = subfolderMode == 2,
                            onClick = { subfolderMode = 2 },
                            label = { Text("+ New", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (subfolderMode) {
                        0 -> {}
                        1 -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(existingSubfoldersInFlashcards) { sub ->
                                    val isSelected = selectedExistingSubfolder.equals(sub, ignoreCase = true)
                                    Surface(
                                        onClick = { selectedExistingSubfolder = sub },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Text(
                                            text = "📁 $sub",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            OutlinedTextField(
                                value = newSubfolderName,
                                onValueChange = { newSubfolderName = it },
                                label = { Text("New Subfolder Name") },
                                placeholder = { Text("e.g. Upper Limb, Thorax", color = Color(0xFF94A3B8)) },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        // STEP 3: CHAPTER / TOPIC NAME (Optional)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("3. Chapter / Topic Name (Optional):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = chapterMode == 0,
                            onClick = { chapterMode = 0 },
                            label = { Text("None (Direct)", fontSize = 11.sp) }
                        )
                        if (existingChaptersInFlashcards.isNotEmpty()) {
                            FilterChip(
                                selected = chapterMode == 1,
                                onClick = { chapterMode = 1 },
                                label = { Text("Existing (${existingChaptersInFlashcards.size})", fontSize = 11.sp) }
                            )
                        }
                        FilterChip(
                            selected = chapterMode == 2,
                            onClick = { chapterMode = 2 },
                            label = { Text("+ New", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (chapterMode) {
                        0 -> {}
                        1 -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(existingChaptersInFlashcards) { chap ->
                                    val isSelected = selectedExistingChapter.equals(chap, ignoreCase = true)
                                    Surface(
                                        onClick = { selectedExistingChapter = chap },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Text(
                                            text = "📖 $chap",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            OutlinedTextField(
                                value = newChapterName,
                                onValueChange = { newChapterName = it },
                                label = { Text("New Chapter Name") },
                                placeholder = { Text("e.g. Brachial Plexus", color = Color(0xFF94A3B8)) },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        // STEP 5: UPLOAD MODE (Bulk JSON vs Manual + Images)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { flashcardTab = 0 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (flashcardTab == 0) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "📋 Bulk JSON Import",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (flashcardTab == 0) Color.White else Color(0xFF334155),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                        Surface(
                            onClick = { flashcardTab = 1 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (flashcardTab == 1) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "✍️ Manual + Images",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (flashcardTab == 1) Color.White else Color(0xFF334155),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (flashcardTab == 0) {
                        val context = LocalContext.current
                        val clipboardManager = LocalClipboardManager.current
                        val jsonFilePickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                try {
                                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                    if (!text.isNullOrBlank()) {
                                        jsonInput = text
                                        statusMessage = "📁 Selected JSON file loaded successfully!"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error reading JSON file: ${e.localizedMessage}"
                                }
                            }
                        }

                        val samplePrompt = """
                            Act as an expert medical education faculty for MBBS / USMLE / NEET-PG.
                            Generate 20 high-yield medical flashcards in clean, structured textbook format (Clinical MCQs, Assertion-Reason, and Match the Columns).
                            STRICT JSON array format required.
                            
                            Field Guidelines:
                            - question: Cleanly formatted question stem with options placed line-by-line.
                            - answer: Correct answer with high-yield clinical explanation.
                            - questionImageUrl: "" (or image URL if diagram-based).
                            - answerImageUrl: "" (or image URL).
                            - hint: A short high-yield diagnostic clue.
                            - tags: Subject tag like "Anatomy", "Pathology", "Physiology", "Pharmacology".
                            
                            Reference Structured Template Samples:
                            [
                              {
                                "question": "A 45-year-old patient presents with weakness in shoulder abduction after fracture of the surgical neck of the humerus. Which nerve is most likely damaged?\nA. Radial nerve\nB. Axillary nerve\nC. Musculocutaneous nerve\nD. Suprascapular nerve",
                                "answer": "B. Axillary nerve\n\nExplanation: The deltoid muscle is supplied by the axillary nerve (C5, C6), which winds around the surgical neck of the humerus.",
                                "questionImageUrl": "",
                                "answerImageUrl": "",
                                "hint": "Nerve winding directly around the surgical neck.",
                                "tags": "Anatomy"
                              }
                            ]
                        """.trimIndent()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { jsonFilePickerLauncher.launch("application/json") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📁 Pick JSON File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(samplePrompt))
                                    statusMessage = "📋 GPT Prompt copied! Paste it in ChatGPT."
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF))
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📋 Copy Prompt", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = jsonInput,
                            onValueChange = { jsonInput = it },
                            placeholder = {
                                Text("[\n  {\n    \"question\": \"Q1?\",\n    \"answer\": \"Ans1\",\n    \"hint\": \"Hint1\",\n    \"tags\": \"High-Yield\"\n  }\n]", color = Color(0xFF94A3B8))
                            },
                            textStyle = com.example.ui.theme.medInputTextStyle(),
                            colors = com.example.ui.theme.medTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val raw = jsonInput.trim()
                                if (raw.isBlank()) {
                                    statusMessage = "⚠️ Please paste JSON text or select a JSON file!"
                                    return@Button
                                }

                                try {
                                    var cleanedRaw = raw
                                    if (cleanedRaw.startsWith("```")) {
                                        cleanedRaw = cleanedRaw.substringAfter("\n")
                                        if (cleanedRaw.contains("```")) {
                                            cleanedRaw = cleanedRaw.substringBeforeLast("```").trim()
                                        }
                                    }
                                    // Extract array or object if surrounded by markdown comments
                                    val firstBracket = cleanedRaw.indexOf('[')
                                    val firstBrace = cleanedRaw.indexOf('{')
                                    if (firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace)) {
                                        val lastBracket = cleanedRaw.lastIndexOf(']')
                                        if (lastBracket > firstBracket) {
                                            cleanedRaw = cleanedRaw.substring(firstBracket, lastBracket + 1)
                                        }
                                    } else if (firstBrace != -1) {
                                        val lastBrace = cleanedRaw.lastIndexOf('}')
                                        if (lastBrace > firstBrace) {
                                            cleanedRaw = cleanedRaw.substring(firstBrace, lastBrace + 1)
                                        }
                                    }

                                    var jsonArrayToProcess = org.json.JSONArray()
                                    var topFolder = ""
                                    var topSubfolder = ""
                                    var topChapter = ""

                                    if (cleanedRaw.startsWith("{")) {
                                        val rootObj = org.json.JSONObject(cleanedRaw)
                                        jsonArrayToProcess = rootObj.optJSONArray("flashcards")
                                            ?: rootObj.optJSONArray("questions")
                                            ?: rootObj.optJSONArray("items")
                                            ?: rootObj.optJSONArray("cards")
                                            ?: rootObj.optJSONArray("data")
                                            ?: org.json.JSONArray()
                                        topFolder = rootObj.optString("folder", "")
                                            .ifBlank { rootObj.optString("folderName", "") }
                                            .ifBlank { rootObj.optString("deck", "") }
                                        topSubfolder = rootObj.optString("subfolder", "").ifBlank { rootObj.optString("subfolderName", "") }
                                        topChapter = rootObj.optString("chapter", "").ifBlank { rootObj.optString("chapterName", "") }
                                    } else {
                                        jsonArrayToProcess = org.json.JSONArray(cleanedRaw)
                                    }

                                    val defaultSubfolderName = when (subfolderMode) {
                                        1 -> selectedExistingSubfolder.trim()
                                        2 -> newSubfolderName.trim()
                                        else -> ""
                                    }

                                    val defaultChapterName = when (chapterMode) {
                                        1 -> selectedExistingChapter.trim()
                                        2 -> newChapterName.trim()
                                        else -> ""
                                    }

                                    val effectiveFolder = when {
                                        topFolder.isNotBlank() -> topFolder.trim()
                                        isCreatingNewFolder && newFolderName.isNotBlank() -> newFolderName.trim()
                                        !isCreatingNewFolder && selectedFolderId.isNotBlank() -> {
                                            availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: "Clinical Flashcards"
                                        }
                                        newFolderName.isNotBlank() -> newFolderName.trim()
                                        else -> "Clinical Flashcards"
                                    }

                                    val existing = availableFolders.firstOrNull { it.name.equals(effectiveFolder, ignoreCase = true) }
                                        ?: folders.firstOrNull { it.name.equals(effectiveFolder, ignoreCase = true) }
                                    val finalFolderId: String
                                    val finalFolderName: String

                                    if (existing != null) {
                                        finalFolderId = existing.id
                                        finalFolderName = existing.name
                                    } else {
                                        val folderId = "f_${UUID.randomUUID().toString().take(6)}"
                                        val folder = ContentFolder(
                                            id = folderId,
                                            prof = ProfYear.FIRST_PROF,
                                            subjectId = "flashcard",
                                            sectionType = "FLASHCARD",
                                            name = effectiveFolder,
                                            emoji = listOf("📇", "📂", "📚", "🧠", "🔬", "🧪", "💊").random()
                                        )
                                        MedRepository.createFolder(folder)
                                        finalFolderId = folderId
                                        finalFolderName = effectiveFolder
                                    }

                                    val importedCardsList = mutableListOf<FlashcardItem>()
                                    for (i in 0 until jsonArrayToProcess.length()) {
                                        val obj = jsonArrayToProcess.getJSONObject(i)
                                        val q = obj.optString("question", "")
                                            .ifBlank { obj.optString("Question", "") }
                                            .ifBlank { obj.optString("q", "") }
                                            .ifBlank { obj.optString("Q", "") }
                                            .ifBlank { obj.optString("stem", "") }
                                            .ifBlank { obj.optString("prompt", "") }

                                        val a = obj.optString("answer", "")
                                            .ifBlank { obj.optString("Answer", "") }
                                            .ifBlank { obj.optString("a", "") }
                                            .ifBlank { obj.optString("A", "") }
                                            .ifBlank { obj.optString("ans", "") }
                                            .ifBlank { obj.optString("explanation", "") }
                                            .ifBlank { obj.optString("Explanation", "") }

                                        val subFromObj = obj.optString("subfolder", "").ifBlank { obj.optString("subfolderName", "") }.ifBlank { obj.optString("sub", "") }
                                        val chapFromObj = obj.optString("chapter", "").ifBlank { obj.optString("chapterName", "") }.ifBlank { obj.optString("chap", "") }
                                        val qImg = obj.optString("questionImageUrl", "").ifBlank { obj.optString("questionImage", "") }
                                        val aImg = obj.optString("answerImageUrl", "").ifBlank { obj.optString("answerImage", "") }
                                        val h = obj.optString("hint", "").ifBlank { obj.optString("Hint", "") }
                                        val t = obj.optString("tags", "High-Yield").ifBlank { obj.optString("tag", "High-Yield") }

                                        val resolvedSubfolder = subFromObj.ifBlank { topSubfolder }.ifBlank { defaultSubfolderName }.trim()
                                        val resolvedChapter = chapFromObj.ifBlank { topChapter }.ifBlank { defaultChapterName }.trim()

                                        if (q.isNotBlank() && a.isNotBlank()) {
                                            val qNum = FlashcardUtils.extractQuestionNumber(q)
                                            val orderPad = String.format("%04d", qNum ?: (i + 1))
                                            val card = FlashcardItem(
                                                id = "fc_${System.currentTimeMillis()}_${orderPad}_${UUID.randomUUID().toString().take(4)}",
                                                subjectId = "flashcard",
                                                prof = ProfYear.FIRST_PROF,
                                                folderId = finalFolderId,
                                                folderName = finalFolderName,
                                                subfolderName = resolvedSubfolder,
                                                chapterName = resolvedChapter,
                                                questionType = QuestionType.TEXT,
                                                question = q,
                                                questionOptions = emptyList(),
                                                questionImageUrl = qImg,
                                                answer = a,
                                                answerImageUrl = aImg,
                                                explanation = "",
                                                hint = h,
                                                tags = t
                                            )
                                            importedCardsList.add(card)
                                        }
                                    }

                                    if (importedCardsList.isNotEmpty()) {
                                        MedRepository.addFlashcards(importedCardsList)
                                        statusMessage = "✓ Successfully imported ${importedCardsList.size} questions into \"$finalFolderName\"!"
                                        jsonInput = ""
                                    } else {
                                        statusMessage = "⚠️ No valid questions found in JSON. Please check fields."
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error parsing JSON: ${e.localizedMessage}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON Flashcards", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = manualQuestion,
                                onValueChange = { manualQuestion = it },
                                label = { Text("Question Text *") },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                minLines = 2
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { questionImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (questionImageUri != null) "✓ Q Image Selected" else "Add Question Image", fontSize = 12.sp)
                                }
                                if (questionImageUri != null) {
                                    TextButton(onClick = { questionImageUri = null }) {
                                        Text("Remove", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = manualAnswer,
                                onValueChange = { manualAnswer = it },
                                label = { Text("Answer Text / Explanation *") },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                minLines = 2
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { answerImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (answerImageUri != null) "✓ Ans Image Selected" else "Add Answer Image", fontSize = 12.sp)
                                }
                                if (answerImageUri != null) {
                                    TextButton(onClick = { answerImageUri = null }) {
                                        Text("Remove", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = manualHint,
                                onValueChange = { manualHint = it },
                                label = { Text("Hint (Optional)") },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = manualTags,
                                onValueChange = { manualTags = it },
                                label = { Text("Tags (Comma-separated)") },
                                textStyle = com.example.ui.theme.medInputTextStyle(),
                                colors = com.example.ui.theme.medTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (manualQuestion.isBlank() || manualAnswer.isBlank()) {
                                        statusMessage = "Please fill Question and Answer!"
                                        return@Button
                                    }

                                    val finalFolderId: String
                                    val finalFolderName: String

                                    val finalSubfolderName = when (subfolderMode) {
                                        1 -> selectedExistingSubfolder.trim()
                                        2 -> newSubfolderName.trim()
                                        else -> ""
                                    }

                                    val finalChapterName = when (chapterMode) {
                                        1 -> selectedExistingChapter.trim()
                                        2 -> newChapterName.trim()
                                        else -> ""
                                    }

                                    val effectiveFolder = if (isCreatingNewFolder && newFolderName.isNotBlank()) {
                                        newFolderName.trim()
                                    } else if (!isCreatingNewFolder && selectedFolderId.isNotBlank()) {
                                        availableFolders.firstOrNull { it.id == selectedFolderId }?.name ?: "Clinical Flashcards"
                                    } else if (newFolderName.isNotBlank()) {
                                        newFolderName.trim()
                                    } else {
                                        "Clinical Flashcards"
                                    }

                                    val existing = availableFolders.firstOrNull { it.name.equals(effectiveFolder, ignoreCase = true) }
                                        ?: folders.firstOrNull { it.name.equals(effectiveFolder, ignoreCase = true) }

                                    if (existing != null) {
                                        finalFolderId = existing.id
                                        finalFolderName = existing.name
                                    } else {
                                        val folderId = "f_${UUID.randomUUID().toString().take(6)}"
                                        val folder = ContentFolder(
                                            id = folderId,
                                            prof = ProfYear.FIRST_PROF,
                                            subjectId = "flashcard",
                                            sectionType = "FLASHCARD",
                                            name = effectiveFolder,
                                            emoji = listOf("📇", "📂", "📚", "🧠", "🔬", "🧪", "💊").random()
                                        )
                                        MedRepository.createFolder(folder)
                                        finalFolderId = folderId
                                        finalFolderName = effectiveFolder
                                    }

                                    val card = FlashcardItem(
                                        id = "fc_${UUID.randomUUID().toString().take(6)}",
                                        subjectId = "flashcard",
                                        prof = ProfYear.FIRST_PROF,
                                        folderId = finalFolderId,
                                        folderName = finalFolderName,
                                        subfolderName = finalSubfolderName,
                                        chapterName = finalChapterName,
                                        questionType = QuestionType.TEXT,
                                        question = manualQuestion.trim(),
                                        questionOptions = emptyList(),
                                        questionImageUrl = questionImageUri?.toString().orEmpty(),
                                        answer = manualAnswer.trim(),
                                        answerImageUrl = answerImageUri?.toString().orEmpty(),
                                        explanation = "",
                                        hint = manualHint.trim(),
                                        tags = manualTags.trim()
                                    )
                                    MedRepository.addFlashcard(card)
                                    statusMessage = "✓ Flashcard published successfully to Cloudflare R2 & DB!"
                                    manualQuestion = ""
                                    manualAnswer = ""
                                    manualHint = ""
                                    questionImageUri = null
                                    answerImageUri = null
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Publish Flashcard", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 🌟 ADD / MANAGE CUSTOM SECTIONS VIEW
// ==========================================
@Composable
fun AddCustomSectionView() {
    val customSections by MedRepository.customSections.collectAsState()
    val mcqSections by MedRepository.mcqSections.collectAsState()
    val allMCQs by MedRepository.mcqs.collectAsState()
    val folders by MedRepository.folders.collectAsState()

    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🌟") }
    var selectedLayout by remember { mutableStateOf("GRID") }
    var selectedColor by remember { mutableStateOf("#2563EB") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var editingSection by remember { mutableStateOf<com.example.model.CustomSection?>(null) }
    var editingMCQSectionInAdd by remember { mutableStateOf<com.example.model.MCQSection?>(null) }
    var addingFolderToSection by remember { mutableStateOf<com.example.model.CustomSection?>(null) }
    var newFolderInsideSectionName by remember { mutableStateOf("") }
    var newFolderInsideSectionEmoji by remember { mutableStateOf("📁") }

    val emojiList = listOf("🌟", "🏥", "🩺", "⚡", "🔬", "🧪", "🗂️", "🔥", "🎯", "🧠", "📚", "💊", "🫀", "🧬")
    val colorList = listOf(
        "#2563EB" to "Royal Blue",
        "#059669" to "Emerald Green",
        "#7C3AED" to "Purple",
        "#D97706" to "Amber",
        "#DC2626" to "Crimson",
        "#0891B2" to "Cyan",
        "#4F46E5" to "Indigo"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        statusMessage?.let { msg ->
            item {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // CREATE NEW SECTION CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌟", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Create New Big Section (Homepage)",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Creates a major section with full folder & subfolder hierarchy",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Section Title * (e.g. SPECIAL CLINICAL MODULES)") },
                        placeholder = { Text("e.g. OSCE & PRACTICAL ESSENTIALS", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtitle
                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("Subtitle / Description") },
                        placeholder = { Text("e.g. High-yield case discussions, algorithms & notes", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Select Emoji Icon
                    Text("Select Emoji / Icon:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(emojiList) { em ->
                            val isSelected = selectedEmoji == em
                            Surface(
                                onClick = { selectedEmoji = em },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2563EB)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(em, fontSize = 18.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Color theme
                    Text("Select Theme Color:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(colorList) { (hex, name) ->
                            val isSelected = selectedColor == hex
                            val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color(0xFF2563EB) }
                            Surface(
                                onClick = { selectedColor = hex },
                                shape = RoundedCornerShape(8.dp),
                                color = col,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, Color.Black) else null,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isSelected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                statusMessage = "Please enter section title!"
                                return@Button
                            }

                            val newSec = com.example.model.CustomSection(
                                id = "sec_${UUID.randomUUID().toString().take(6)}",
                                title = title.trim(),
                                subtitle = subtitle.trim(),
                                emoji = selectedEmoji,
                                colorHex1 = selectedColor,
                                colorHex2 = selectedColor,
                                description = subtitle.trim(),
                                orderIndex = customSections.size
                            )
                            MedRepository.addCustomSection(newSec)
                            statusMessage = "✓ Section '${title.trim()}' created and synced with Cloudflare & Homepage!"
                            title = ""
                            subtitle = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create & Publish Section", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // EXISTING SECTIONS LIST & HIERARCHY
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "All Custom Sections (${customSections.size})",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            }
        }

        if (customSections.isEmpty() && mcqSections.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No custom or MCQ sections created yet. Create one above!", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(customSections) { sec ->
                val sectionFolders = folders.filter { it.subjectId == sec.id }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(sec.emoji, fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        sec.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    if (sec.subtitle.isNotBlank()) {
                                        Text(sec.subtitle, fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1)
                                    }
                                    Text(
                                        "📁 ${sectionFolders.size} Folders inside",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = { editingSection = sec }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { MedRepository.deleteCustomSection(sec.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Folders list inside section
                        if (sectionFolders.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(sectionFolders) { f ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF1F5F9),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(f.emoji, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(f.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Add folder inside this section button
                        OutlinedButton(
                            onClick = {
                                addingFolderToSection = sec
                                newFolderInsideSectionName = ""
                            },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Folder in ${sec.title}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (mcqSections.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                ) {
                    Text("🎯", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MCQ SECTIONS (${mcqSections.size})",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }
            }

            items(mcqSections) { sec ->
                val sectionFolders = folders.filter { f ->
                    f.subjectId == sec.id || allMCQs.any { m ->
                        (m.folderId == f.id || m.folderName.equals(f.name, ignoreCase = true)) &&
                        (m.sectionId == sec.id || m.sectionName.equals(sec.title, ignoreCase = true))
                    }
                }
                val sectionMCQs = allMCQs.filter { m ->
                    m.sectionId == sec.id || m.sectionName.equals(sec.title, ignoreCase = true)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(sec.emoji.ifBlank { "🎯" }, fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        sec.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    if (sec.subtitle.isNotBlank()) {
                                        Text(sec.subtitle, fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1)
                                    }
                                    Text(
                                        "📁 ${sectionFolders.size} Folders • ${sectionMCQs.size} Questions",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = { editingMCQSectionInAdd = sec }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { MedRepository.deleteMCQSection(sec.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Folder Inside Section Dialog
    addingFolderToSection?.let { sec ->
        Dialog(onDismissRequest = { addingFolderToSection = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add Folder in ${sec.title}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newFolderInsideSectionName,
                        onValueChange = { newFolderInsideSectionName = it },
                        label = { Text("Folder Name (e.g. OSCE Stations, Case Studies)") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Select Folder Emoji:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("📁", "📂", "🗂️", "📚", "🩺", "🔬", "🧪", "⚡", "💊", "🫀", "🧠")) { em ->
                            Surface(
                                onClick = { newFolderInsideSectionEmoji = em },
                                shape = RoundedCornerShape(6.dp),
                                color = if (newFolderInsideSectionEmoji == em) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = if (newFolderInsideSectionEmoji == em) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2563EB)) else null,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(em, fontSize = 16.sp) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { addingFolderToSection = null }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newFolderInsideSectionName.isNotBlank()) {
                                    val newFolder = ContentFolder(
                                        id = "f_${UUID.randomUUID().toString().take(6)}",
                                        prof = ProfYear.FIRST_PROF,
                                        subjectId = sec.id,
                                        sectionType = "CUSTOM_SECTION",
                                        name = newFolderInsideSectionName.trim(),
                                        emoji = newFolderInsideSectionEmoji
                                    )
                                    MedRepository.createFolder(newFolder)
                                    addingFolderToSection = null
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Create Folder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Edit Custom Section Dialog
    editingSection?.let { sec ->
        var editTitle by remember { mutableStateOf(sec.title) }
        var editSubtitle by remember { mutableStateOf(sec.subtitle) }
        var editEmoji by remember { mutableStateOf(sec.emoji) }

        Dialog(onDismissRequest = { editingSection = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Custom Section", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editSubtitle,
                        onValueChange = { editSubtitle = it },
                        label = { Text("Subtitle") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(emojiList) { em ->
                            Surface(
                                onClick = { editEmoji = em },
                                shape = RoundedCornerShape(6.dp),
                                color = if (editEmoji == em) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = if (editEmoji == em) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2563EB)) else null,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(em, fontSize = 16.sp) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingSection = null }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editTitle.isNotBlank()) {
                                    val updated = sec.copy(
                                        title = editTitle.trim(),
                                        subtitle = editSubtitle.trim(),
                                        emoji = editEmoji
                                    )
                                    MedRepository.updateCustomSection(updated)
                                    editingSection = null
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    editingMCQSectionInAdd?.let { sec ->
        com.example.screens.EditMCQSectionDialog(
            section = sec,
            onDismiss = { editingMCQSectionInAdd = null },
            onSave = { updated ->
                MedRepository.updateMCQSection(updated)
                editingMCQSectionInAdd = null
            }
        )
    }
}

sealed class DeletionTarget {
    data class Folder(val folder: ContentFolder, val count: Int = 0) : DeletionTarget()
    data class StudyContent(val item: StudyContentItem) : DeletionTarget()
}

@Composable
fun ManageLibraryCategoryList(
    onCategorySelected: (Int) -> Unit
) {
    val contents by MedRepository.contents.collectAsState()
    val shorts by MedRepository.shorts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val manageOptions = listOf(
            Triple(0, "📚 PDFs & Videos", "Manage lecture notes, PDFs & chapters (${contents.count { !it.isPyq }})"),
            Triple(1, "🎬 Shorts", "Manage clinical mnemonic videos (${shorts.size})"),
            Triple(2, "📇 Flashcards", "Manage clinical flashcard decks & items")
        )
        manageOptions.forEach { (idx, title, desc) ->
            Surface(
                onClick = { onCategorySelected(idx) },
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ManageSectionView(
    navigateTo: (Screen) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    
    if (selectedCategory != null) {
        BackHandler { selectedCategory = null }
        
        // Full screen detail view
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Back Button
                Surface(
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedCategory = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2563EB))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedCategory) {
                                0 -> "📚 PDFs & Videos"
                                1 -> "🎬 Shorts"
                                2 -> "📇 Flashcards"
                                else -> "Manage Library"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E3A8A)
                        )
                    }
                }
                
                // Detail content
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    when (selectedCategory) {
                        0 -> { /* PDF implementation */ }
                        1 -> { /* Shorts implementation */ }
                        2 -> { item { AdminFlashcardsManageView() } }
                    }
                }
            }
        }
    } else {
        // Category list
        ManageLibraryCategoryList(onCategorySelected = { selectedCategory = it })
    }
}

@Composable
fun AdminDeletionDialogs(
    deletionTarget: DeletionTarget?,
    onDismiss: () -> Unit
) {
    if (deletionTarget != null) {
        val target = deletionTarget
        val (dialogTitle, dialogMessage, confirmButtonText) = when (target) {
            is DeletionTarget.Folder -> Triple(
                "Delete Folder?",
                "Are you sure you want to permanently delete folder \"${target.folder.name}\" and all ${target.count} file(s) in it?\n\nThis cannot be undone.",
                "Delete Folder"
            )
            is DeletionTarget.StudyContent -> Triple(
                "Delete File?",
                "Are you sure you want to permanently delete \"${target.item.title}\"?\n\nThis cannot be undone.",
                "Delete File"
            )
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete Icon",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = dialogTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = dialogMessage,
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (val t = deletionTarget) {
                            is DeletionTarget.Folder -> {
                                MedRepository.deleteFolder(t.folder.id, t.folder.name)
                            }
                            is DeletionTarget.StudyContent -> MedRepository.deleteStudyContent(t.item)
                            null -> {}
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(confirmButtonText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }
}

    selectedFolderForDetail?.let { folder ->
        val folderItems = contents.filter { !it.isPyq && (it.folderId == folder.id || it.folderName.equals(folder.name, ignoreCase = true)) }
        val groupedByChapter = folderItems.groupBy { it.chapterName.trim().ifBlank { "General" } }

        Dialog(onDismissRequest = { selectedFolderForDetail = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(folder.emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(folder.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                                Text("${folder.prof.title} • ${folderItems.size} Files", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                        }
                        IconButton(onClick = { selectedFolderForDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(12.dp))

                    if (folderItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No files inside this folder yet", color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            groupedByChapter.forEach { (chapter, itemsInChapter) ->
                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(chapter, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2563EB))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            itemsInChapter.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                        Text(if (item.type == StudyContentType.PDF) "📄" else "🎬", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                                        Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    Row {
                                                        IconButton(onClick = { editingStudyItem = item }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                                                        }
                                                        IconButton(onClick = { MedRepository.deleteStudyContent(item) }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
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
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Section Selector Chips
        // Vertical Category Cards for Manage Library (no horizontal scrolling)
        // Vertical Category List for Manage Library (no horizontal scrolling)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val manageOptions = listOf(
                Triple(0, "📚 PDFs & Videos", "Manage lecture notes, PDFs & chapters (${contents.count { !it.isPyq }})"),
                Triple(1, "🎬 Shorts", "Manage clinical mnemonic videos (${shorts.size})"),
                Triple(2, "📇 Flashcards", "Manage clinical flashcard decks & items")
            )
            manageOptions.forEach { (idx, title, desc) ->
                val isSelected = selectedSectionTab == idx
                Surface(
                    onClick = { selectedSectionTab = idx },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedSectionTab) {
                0 -> {
                    // ================= 📚 NOTES & PDF SECTION =================
                    val pdfFolders = folders.filter {
                        !it.sectionType.equals("PYQ", ignoreCase = true) &&
                        !it.sectionType.equals("FLASHCARD", ignoreCase = true) &&
                        !it.sectionType.equals("MCQ", ignoreCase = true) &&
                        !it.subjectId.equals("mcq", ignoreCase = true) &&
                        !it.subjectId.equals("flashcard", ignoreCase = true)
                    }
                    val pdfContents = contents.filter { !it.isPyq }

                    // Grouping: Prof -> Folder -> Chapter
                    val groupedByProf = pdfFolders.groupBy { it.prof }.toSortedMap()

                    // Unassigned Content
                    val folderIds = pdfFolders.map { it.id }.toSet()
                    val unassignedNotes = pdfContents.filter { it.folderId.isBlank() || !folderIds.contains(it.folderId) }

                    if (groupedByProf.isEmpty() && unassignedNotes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("No PDF Notes or Videos uploaded yet", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    
                    if (unassignedNotes.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("⚠️ Unassigned Notes (${unassignedNotes.size})", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                    unassignedNotes.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.title, fontSize = 13.sp, color = Color(0xFF475569), modifier = Modifier.weight(1f))
                                            IconButton(onClick = { MedRepository.deleteStudyContent(item) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    groupedByProf.forEach { (prof, profFolders) ->
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            ) {
                                Text("🏢", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = prof.title.uppercase(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = Color(0xFF1E293B),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        profFolders.forEach { folder ->
                            val folderFileCount = pdfContents.count { it.folderId == folder.id || it.folderName.equals(folder.name, ignoreCase = true) }
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .clickable { selectedFolderForDetail = folder },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFFEFF6FF),
                                                modifier = Modifier.size(46.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(folder.emoji, fontSize = 24.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column {
                                                Text(
                                                    text = folder.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${folder.prof.title} • $folderFileCount files inside",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF64748B),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { renamingFolder = folder }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { MedRepository.deleteFolder(folder.id) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                                1 -> {
                    // ================= 🎬 SHORTS SECTION =================
                    if (shorts.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("No Short videos uploaded yet", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        items(shorts) { short ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(short.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A), maxLines = 1)
                                        if (short.mnemonicTip.isNotBlank()) {
                                            Text(short.mnemonicTip, fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1)
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = { editingShort = short }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { MedRepository.deleteShort(short.id) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // ================= 📇 FLASHCARDS SECTION =================
                    item {
                        AdminFlashcardsManageView()
                    }
                }
            }
        }
    }

    // Rename Folder Dialog
    renamingFolder?.let { folder ->
        var newName by remember { mutableStateOf(folder.name) }
        var newEmoji by remember { mutableStateOf(folder.emoji) }

        Dialog(onDismissRequest = { renamingFolder = null }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Rename Folder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Folder Name") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("📁", "📚", "🔬", "🫀", "🧠", "🦴", "🧪").forEach { em ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (newEmoji == em) Color(0xFFDBEAFE) else Color(0xFFF1F5F9))
                                    .clickable { newEmoji = em },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(em, fontSize = 18.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { renamingFolder = null }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    MedRepository.renameFolder(folder.id, newName.trim(), newEmoji)
                                }
                                renamingFolder = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Edit Study Item Dialog
    editingStudyItem?.let { item ->
        var editTitle by remember { mutableStateOf(item.title) }
        var editDesc by remember { mutableStateOf(item.description) }

        Dialog(onDismissRequest = { editingStudyItem = null }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Item", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { editingStudyItem = null }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (editTitle.isNotBlank()) {
                                    MedRepository.editStudyContent(item.id, editTitle.trim(), editDesc.trim())
                                }
                                editingStudyItem = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Edit Short Dialog
    editingShort?.let { short ->
        var editTitle by remember { mutableStateOf(short.title) }
        var editDesc by remember { mutableStateOf(short.mnemonicTip) }

        Dialog(onDismissRequest = { editingShort = null }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Short Video", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Short Title") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Clinical Mnemonic / Description") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { editingShort = null }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (editTitle.isNotBlank()) {
                                    MedRepository.editShort(short.id, editTitle.trim(), editDesc.trim())
                                }
                                editingShort = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

}
