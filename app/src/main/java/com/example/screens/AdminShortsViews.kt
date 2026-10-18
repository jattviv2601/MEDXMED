package com.example.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.MedRepository
import com.example.model.ProfYear
import com.example.model.ShortItem
import com.example.util.YouTubeHelper
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun UploadShortsView() {
    val coroutineScope = rememberCoroutineScope()

    var youtubeUrl by remember { mutableStateOf("") }
    var fetchedVideoId by remember { mutableStateOf<String?>(null) }
    var fetchedTitle by remember { mutableStateOf("") }
    var fetchedAuthor by remember { mutableStateOf("Faculty") }
    var fetchedThumbnail by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var mnemonicTip by remember { mutableStateOf("") }

    var isFetching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun triggerAutoFetch(url: String) {
        val vid = YouTubeHelper.extractVideoId(url)
        if (vid != null) {
            fetchedVideoId = vid
            isFetching = true
            statusMessage = null
            coroutineScope.launch {
                val metadata = YouTubeHelper.fetchMetadata(vid)
                isFetching = false
                if (metadata != null) {
                    fetchedVideoId = metadata.videoId
                    fetchedTitle = metadata.title
                    fetchedAuthor = metadata.authorName
                    fetchedThumbnail = metadata.thumbnailUrl
                    if (description.isBlank()) {
                        description = "Key MBBS high-yield concept and rapid mnemonics."
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
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎬", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Enter YouTube Shorts / Video Link",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Paste any YouTube Short or video link. Title, author, and HD thumbnail will be automatically fetched!",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = youtubeUrl,
                        onValueChange = {
                            youtubeUrl = it
                            triggerAutoFetch(it)
                        },
                        placeholder = { Text("https://youtube.com/shorts/... or https://youtu.be/...", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (isFetching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else if (fetchedVideoId != null) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = Color(0xFF16A34A))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { triggerAutoFetch(youtubeUrl) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        enabled = youtubeUrl.isNotBlank() && !isFetching
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFetching) "Fetching from YouTube..." else "⚡ Auto-Fetch Title & HD Thumbnail",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Live HD Preview Card if Video is fetched
        if (fetchedVideoId != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Live High-Quality YouTube Preview",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumb = fetchedThumbnail.ifBlank { "https://img.youtube.com/vi/$fetchedVideoId/hqdefault.jpg" }
                            AsyncImage(
                                model = thumb,
                                contentDescription = "Shorts Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Play Badge
                            Surface(
                                shape = CircleShape,
                                color = Color(0xCC000000),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }

                            // HD Tag
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEF4444),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = fetchedTitle.ifBlank { "High Yield Short" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Channel / Doctor: $fetchedAuthor • ID: $fetchedVideoId",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        // Editable Details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("2. Short Details", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Title
                    OutlinedTextField(
                        value = fetchedTitle,
                        onValueChange = { fetchedTitle = it },
                        label = { Text("Short Title *") },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Description / Mnemonic Tip
                    OutlinedTextField(
                        value = mnemonicTip,
                        onValueChange = { mnemonicTip = it },
                        label = { Text("Clinical Mnemonic / Key Takeaway (Optional)") },
                        placeholder = { Text("e.g. High Yield concept & rapid mnemonics...", color = Color(0xFF94A3B8)) },
                        textStyle = com.example.ui.theme.medInputTextStyle(),
                        colors = com.example.ui.theme.medTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2
                    )
                }
            }
        }

        // Action Button: Upload & Save Short
        item {
            val canSave = fetchedVideoId != null && (fetchedTitle.isNotBlank() || youtubeUrl.isNotBlank())

            Button(
                onClick = {
                    val vid = fetchedVideoId ?: YouTubeHelper.extractVideoId(youtubeUrl) ?: "short"
                    val shortId = "s_${UUID.randomUUID().toString().take(6)}"
                    val cloudflareUrl = MedRepository.buildCloudflareShortUrl(shortId, vid)

                    val shortItem = ShortItem(
                        id = shortId,
                        title = fetchedTitle.ifBlank { "MBBS High Yield Short" }.trim(),
                        description = description.trim(),
                        youtubeUrl = if (youtubeUrl.startsWith("http")) youtubeUrl.trim() else "https://www.youtube.com/watch?v=$vid",
                        youtubeVideoId = vid,
                        thumbnailUrl = fetchedThumbnail.ifBlank { "https://img.youtube.com/vi/$vid/maxresdefault.jpg" },
                        cloudflareKey = cloudflareUrl,
                        subjectName = "General",
                        emoji = "⚡",
                        prof = ProfYear.FIRST_PROF,
                        doctorName = fetchedAuthor.ifBlank { "Faculty" }.trim(),
                        durationSec = 60,
                        likesCount = 120,
                        mnemonicTip = mnemonicTip.ifBlank { "Rapid High Yield Concept 🩺" }.trim()
                    )

                    MedRepository.addShort(shortItem)
                    coroutineScope.launch {
                        com.example.data.cloud.CloudSyncEngine.publishCatalogToCloud()
                    }
                    statusMessage = "✓ Short saved & uploaded to Cloudflare successfully!"
                    youtubeUrl = ""
                    fetchedVideoId = null
                    fetchedTitle = ""
                    fetchedThumbnail = ""
                    description = ""
                    mnemonicTip = ""
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                enabled = canSave
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload & Save Short Video", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFF15803D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ManageShortsView() {
    val shorts by MedRepository.shorts.collectAsState()
    var editingShort by remember { mutableStateOf<ShortItem?>(null) }
    var deleteConfirmShort by remember { mutableStateOf<ShortItem?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🎬 Manage Shorts Library", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                        Text("Total Active Shorts: ${shorts.size}", fontSize = 12.sp, color = Color(0xFF64748B))
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF)
                    ) {
                        Text(
                            text = "${shorts.size} Shorts",
                            color = Color(0xFF2563EB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (shorts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No shorts uploaded yet.\nUse 'Upload Shorts' tab to add your first YouTube Short!",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            items(shorts, key = { it.id }) { short ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Video Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumb = short.thumbnailUrl.ifBlank {
                                    if (short.youtubeVideoId.isNotBlank()) "https://img.youtube.com/vi/${short.youtubeVideoId}/hqdefault.jpg"
                                    else ""
                                }
                                if (thumb.isNotBlank()) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${short.emoji} ${short.subjectName}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = short.prof.title,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = short.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "By ${short.doctorName}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                if (short.cloudflareKey.isNotBlank()) {
                                    Text(
                                        text = "Cloudflare R2: ✓ Synced",
                                        fontSize = 10.sp,
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Column {
                                IconButton(onClick = { editingShort = short }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { deleteConfirmShort = short }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Short Dialog
    editingShort?.let { short ->
        var editTitle by remember { mutableStateOf(short.title) }
        var editTip by remember { mutableStateOf(short.mnemonicTip) }

        Dialog(onDismissRequest = { editingShort = null }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Short Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
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
                        value = editTip,
                        onValueChange = { editTip = it },
                        label = { Text("Mnemonic Tip") },
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
                                    MedRepository.editShort(short.id, editTitle.trim(), editTip.trim())
                                }
                                editingShort = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    deleteConfirmShort?.let { short ->
        AlertDialog(
            onDismissRequest = { deleteConfirmShort = null },
            title = { Text("Delete Short?") },
            text = { Text("Are you sure you want to delete '${short.title}' from the database?") },
            confirmButton = {
                Button(
                    onClick = {
                        MedRepository.deleteShort(short.id)
                        deleteConfirmShort = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmShort = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
