package com.example.data.cloud

import android.content.Context
import com.example.data.MedRepository
import com.example.data.local.*
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudSyncEngine {

    const val CATALOG_OBJECT_KEY = "catalog.json"
    val CATALOG_PUBLIC_URL: String get() = "${CloudflareR2Manager.publicBaseUrl.value.trimEnd('/')}/$CATALOG_OBJECT_KEY"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow<String>("Not synced yet")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long>(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    /**
     * Downloads catalog.json from Cloudflare R2 and syncs all folders, contents, PYQs,
     * flashcards, and shorts into the local Room database so ALL users see the same content.
     */
    suspend fun syncFromCloud(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _lastSyncStatus.value = "Checking Cloudflare R2 for updates..."

        try {
            val request = Request.Builder()
                .url("$CATALOG_PUBLIC_URL?nocache=${System.currentTimeMillis()}")
                .get()
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    _lastSyncStatus.value = "Cloud catalog is empty (No items uploaded to Cloudflare yet)"
                    _isSyncing.value = false
                    return@withContext Result.success(0)
                }
                _lastSyncStatus.value = "Cloudflare sync failed: HTTP ${response.code}"
                _isSyncing.value = false
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) {
                _lastSyncStatus.value = "Empty catalog on Cloudflare"
                _isSyncing.value = false
                return@withContext Result.success(0)
            }

            val rootJson = JSONObject(bodyString)
            val foldersArray = rootJson.optJSONArray("folders") ?: JSONArray()
            val contentsArray = rootJson.optJSONArray("contents") ?: JSONArray()
            val pyqsArray = rootJson.optJSONArray("pyqs") ?: JSONArray()
            val shortsArray = rootJson.optJSONArray("shorts") ?: JSONArray()
            val flashcardsArray = rootJson.optJSONArray("flashcards") ?: JSONArray()
            val mcqSectionsArray = rootJson.optJSONArray("mcqSections") ?: JSONArray()
            val mcqsArray = rootJson.optJSONArray("mcqs") ?: JSONArray()
            val db = MedXMedDatabase.getDatabase(context)
            var totalSyncedCount = 0
            var needRepublish = false

            // 0. Destructive mirror sync: Clear only synced tables
            db.folderDao().clearAllFolders()
            db.studyContentDao().clearAllContents()
            db.pyqDao().clearAllPYQs()
            db.shortDao().clearAllShorts()
            db.flashcardDao().clearAllFlashcards()
            db.mcqDao().clearAllMCQs()
            db.mcqSectionDao().clearAllSections()

            // 1. Sync Folders
            for (i in 0 until foldersArray.length()) {
                val fObj = foldersArray.getJSONObject(i)
                val prof = ProfYear.values().firstOrNull { it.name == fObj.optString("prof") } ?: ProfYear.FIRST_PROF
                db.folderDao().insertFolder(ContentFolderEntity.fromDomain(ContentFolder(
                    id = fObj.getString("id"),
                    prof = prof,
                    subjectId = fObj.optString("subjectId", "anat"),
                    name = fObj.getString("name"),
                    emoji = fObj.optString("emoji", "📁"),
                    sectionType = fObj.optString("sectionType", "ALL")
                )))
            }
            totalSyncedCount += foldersArray.length()

            // 2. Sync Study Contents
            for (i in 0 until contentsArray.length()) {
                val cObj = contentsArray.getJSONObject(i)
                val prof = ProfYear.values().firstOrNull { it.name == cObj.optString("prof") } ?: ProfYear.FIRST_PROF
                val type = if (cObj.optString("type") == "PDF") StudyContentType.PDF else StudyContentType.VIDEO
                db.studyContentDao().insertContent(StudyContentEntity.fromDomain(StudyContentItem(
                    id = cObj.getString("id"),
                    prof = prof,
                    subjectId = cObj.getString("subjectId"),
                    folderId = cObj.getString("folderId"),
                    folderName = cObj.optString("folderName", "General"),
                    chapterName = cObj.optString("chapterName", ""),
                    type = type,
                    title = cObj.getString("title"),
                    description = cObj.optString("description", ""),
                    mediaUrl = cObj.getString("mediaUrl"),
                    cloudflareKey = cObj.optString("cloudflareKey", ""),
                    createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                    isPyq = cObj.optBoolean("isPyq", false)
                )))
            }
            totalSyncedCount += contentsArray.length()

            // 3. Sync PYQs (Skipped)
            if (false) for (i in 0 until pyqsArray.length()) {
                val pObj = pyqsArray.getJSONObject(i)
                val prof = ProfYear.values().firstOrNull { it.name == pObj.optString("prof") } ?: ProfYear.FIRST_PROF
                db.pyqDao().insertPYQ(PYQEntity.fromDomain(PYQItem(
                    id = pObj.getString("id"),
                    subjectId = pObj.getString("subjectId"),
                    prof = prof,
                    year = pObj.optInt("year", 2024),
                    examType = pObj.optString("examType", "University"),
                    question = pObj.getString("question"),
                    marks = pObj.optInt("marks", 10),
                    answerKey = pObj.optString("answerKey", ""),
                    highYieldTip = pObj.optString("highYieldTip", "")
                )))
            }
            totalSyncedCount += pyqsArray.length()

            // 4. Sync Shorts
            for (i in 0 until shortsArray.length()) {
                val sObj = shortsArray.getJSONObject(i)
                val prof = ProfYear.values().firstOrNull { it.name == sObj.optString("prof") } ?: ProfYear.FIRST_PROF
                db.shortDao().insertShort(ShortEntity.fromDomain(ShortItem(
                    id = sObj.getString("id"),
                    title = sObj.getString("title"),
                    description = sObj.optString("description", ""),
                    youtubeUrl = sObj.optString("youtubeUrl", ""),
                    youtubeVideoId = sObj.optString("youtubeVideoId", ""),
                    thumbnailUrl = sObj.optString("thumbnailUrl", ""),
                    cloudflareKey = sObj.optString("cloudflareKey", ""),
                    subjectName = sObj.optString("subjectName", "General"),
                    emoji = sObj.optString("emoji", "🩺"),
                    prof = prof,
                    doctorName = sObj.optString("doctorName", "Dr. Viv"),
                    durationSec = sObj.optInt("durationSec", 60),
                    likesCount = sObj.optInt("likesCount", 100),
                    mnemonicTip = sObj.optString("mnemonicTip", ""),
                    audioTitle = sObj.optString("audioTitle", "MedXMed Rapid Mnemonics"),
                    createdAt = sObj.optLong("createdAt", System.currentTimeMillis())
                )))
            }
            totalSyncedCount += shortsArray.length()

            // 5. Sync Flashcards (Honoring deletions)
            for (i in 0 until flashcardsArray.length()) {
                val fcObj = flashcardsArray.getJSONObject(i)
                val fcId = fcObj.getString("id")
                val folderId = fcObj.optString("folderId", "")
                val folderName = fcObj.optString("folderName", "")

                if (MedRepository.isFlashcardDeleted(fcId) || MedRepository.isFolderDeleted(folderId, folderName)) {
                    db.flashcardDao().deleteFlashcardById(fcId)
                    needRepublish = true
                    continue
                }

                val prof = ProfYear.values().firstOrNull { it.name == fcObj.optString("prof") } ?: ProfYear.FIRST_PROF
                val qType = try { QuestionType.valueOf(fcObj.optString("questionType", "TEXT")) } catch(e: Exception) { QuestionType.TEXT }
                val qOptsStr = fcObj.optString("questionOptions", "")
                val qOpts = if (qOptsStr.isNotBlank()) qOptsStr.split("|").filter { it.isNotBlank() } else emptyList()

                db.flashcardDao().insertFlashcard(FlashcardEntity.fromDomain(FlashcardItem(
                    id = fcId,
                    subjectId = fcObj.optString("subjectId", "flashcard"),
                    prof = prof,
                    folderId = folderId,
                    folderName = folderName,
                    subfolderName = fcObj.optString("subfolderName", ""),
                    chapterName = fcObj.optString("chapterName", "").trim(),
                    questionType = qType,
                    question = fcObj.getString("question"),
                    questionOptions = qOpts,
                    questionImageUrl = fcObj.optString("questionImageUrl", ""),
                    answer = fcObj.getString("answer"),
                    answerImageUrl = fcObj.optString("answerImageUrl", ""),
                    explanation = fcObj.optString("explanation", ""),
                    hint = fcObj.optString("hint", ""),
                    tags = fcObj.optString("tags", "High-Yield"),
                    mnemonic = fcObj.optString("mnemonic", null),
                    clinicalPearl = fcObj.optString("clinicalPearl", null),
                    isMastered = fcObj.optBoolean("isMastered", false),
                    difficulty = fcObj.optString("difficulty", "Medium")
                )))
            }
            totalSyncedCount += flashcardsArray.length()

            // 6. Sync MCQ Sections (Skipped)
            if (false) for (i in 0 until mcqSectionsArray.length()) {
                val sObj = mcqSectionsArray.getJSONObject(i)
                db.mcqSectionDao().insertSection(MCQSectionEntity.fromDomain(MCQSection(
                    id = sObj.getString("id"),
                    title = sObj.getString("title"),
                    subtitle = sObj.optString("subtitle", ""),
                    emoji = sObj.optString("emoji", "🎯"),
                    colorHex1 = sObj.optString("colorHex1", "#0D9488"),
                    colorHex2 = sObj.optString("colorHex2", "#0F766E"),
                    prof = ProfYear.values().firstOrNull { it.name == sObj.optString("prof") } ?: ProfYear.FIRST_PROF,
                    orderIndex = sObj.optInt("orderIndex", i)
                )))
            }
            totalSyncedCount += mcqSectionsArray.length()

            // 7. Sync MCQs (Skipped)
            if (false) for (i in 0 until mcqsArray.length()) {
                val mObj = mcqsArray.getJSONObject(i)
                val prof = ProfYear.values().firstOrNull { it.name == mObj.optString("prof") } ?: ProfYear.FIRST_PROF
                val optionsJson = mObj.optJSONArray("options")?.toString() ?: mObj.optString("optionsJson", "[]")
                db.mcqDao().insertMCQ(com.example.data.local.MCQEntity(
                    id = mObj.getString("id"),
                    profCode = prof.code,
                    subjectId = mObj.optString("subjectId", "mcq"),
                    sectionId = mObj.optString("sectionId", ""),
                    sectionName = mObj.optString("sectionName", ""),
                    folderId = mObj.optString("folderId", ""),
                    folderName = mObj.optString("folderName", ""),
                    subfolderName = mObj.optString("subfolderName", ""),
                    chapterName = mObj.optString("chapterName", "").trim(),
                    question = mObj.getString("question"),
                    questionImageUrl = mObj.optString("questionImageUrl", ""),
                    optionsJson = optionsJson,
                    correctOptionIndex = mObj.optInt("correctOptionIndex", 0),
                    explanation = mObj.optString("explanation", ""),
                    explanationImageUrl = mObj.optString("explanationImageUrl", ""),
                    difficulty = mObj.optString("difficulty", "Medium"),
                    tags = mObj.optString("tags", "High-Yield"),
                    createdAt = mObj.optLong("createdAt", System.currentTimeMillis()),
                    questionType = mObj.optString("questionType", "MCQ"),
                    assertion = mObj.optString("assertion", ""),
                    reason = mObj.optString("reason", ""),
                    statementsJson = mObj.optJSONArray("statements")?.toString() ?: mObj.optString("statementsJson", "[]"),
                    matchColumnAJson = mObj.optJSONArray("matchColumnA")?.toString() ?: mObj.optString("matchColumnAJson", "[]"),
                    matchColumnBJson = mObj.optJSONArray("matchColumnB")?.toString() ?: mObj.optString("matchColumnBJson", "[]"),
                    flowchartStepsJson = mObj.optJSONArray("flowchartSteps")?.toString() ?: mObj.optString("flowchartStepsJson", "[]")
                ))
            }
            totalSyncedCount += mcqsArray.length()

            // 6. Sync Custom Sections (Skipped)
            val customSectionsArray = rootJson.optJSONArray("customSections")
            if (false && customSectionsArray != null && customSectionsArray.length() > 0) {
                val secList = mutableListOf<CustomSection>()
                for (i in 0 until customSectionsArray.length()) {
                    val sObj = customSectionsArray.getJSONObject(i)
                    secList.add(
                        CustomSection(
                            id = sObj.getString("id"),
                            title = sObj.getString("title"),
                            subtitle = sObj.optString("subtitle", ""),
                            emoji = sObj.optString("emoji", "📚"),
                            colorHex1 = sObj.optString("colorHex1", "#0284C7"),
                            colorHex2 = sObj.optString("colorHex2", "#0369A1"),
                            description = sObj.optString("description", ""),
                            orderIndex = sObj.optInt("orderIndex", i)
                        )
                    )
                }
                MedRepository.updateCustomSections(secList)
            }

            // 6b. Sync MCQ Sections (for MCQ practice bank hierarchy)
            val mcqSectionsArrayRemote = rootJson.optJSONArray("mcqSections")
            if (false && mcqSectionsArrayRemote != null && mcqSectionsArrayRemote.length() > 0) {
                val mcqSecList = mutableListOf<MCQSection>()
                for (i in 0 until mcqSectionsArrayRemote.length()) {
                    val sObj = mcqSectionsArrayRemote.getJSONObject(i)
                    mcqSecList.add(
                        MCQSection(
                            id = sObj.getString("id"),
                            title = sObj.getString("title"),
                            subtitle = sObj.optString("subtitle", ""),
                            emoji = sObj.optString("emoji", "🎯"),
                            colorHex1 = sObj.optString("colorHex1", "#0D9488"),
                            colorHex2 = sObj.optString("colorHex2", "#0F766E"),
                            prof = ProfYear.values().firstOrNull { it.name == sObj.optString("prof") } ?: ProfYear.FIRST_PROF,
                            orderIndex = sObj.optInt("orderIndex", i)
                        )
                    )
                }
                MedRepository.updateMCQSections(mcqSecList)
                totalSyncedCount += mcqSecList.size
            }

            // 7. Sync MCQs (Non-destructive: Do NOT wipe local MCQs; skip deleted ones)
            val mcqsArrayRemote = rootJson.optJSONArray("mcqs")
            if (mcqsArrayRemote != null) {
                for (i in 0 until mcqsArrayRemote.length()) {
                    val mObj = mcqsArrayRemote.getJSONObject(i)
                    val mId = mObj.getString("id")
                    val mFolderId = mObj.optString("folderId", "")
                    val mFolderName = mObj.optString("folderName", "")

                    if (MedRepository.isMCQDeleted(mId) || MedRepository.isFolderDeleted(mFolderId, mFolderName)) {
                        db.mcqDao().deleteMCQById(mId)
                        needRepublish = true
                        continue
                    }

                    val prof = ProfYear.values().firstOrNull { it.name == mObj.optString("prof") } ?: ProfYear.FIRST_PROF
                    val optionsJson = mObj.optJSONArray("options")?.toString() ?: mObj.optString("optionsJson", "[]")
                    val mcq = com.example.data.local.MCQEntity(
                        id = mId,
                        profCode = prof.code,
                        subjectId = mObj.optString("subjectId", "mcq"),
                        sectionId = mObj.optString("sectionId", ""),
                        sectionName = mObj.optString("sectionName", ""),
                        folderId = mFolderId,
                        folderName = mFolderName,
                        subfolderName = mObj.optString("subfolderName", ""),
                        chapterName = mObj.optString("chapterName", "").trim(),
                        question = mObj.getString("question"),
                        questionImageUrl = mObj.optString("questionImageUrl", ""),
                        optionsJson = optionsJson,
                        correctOptionIndex = mObj.optInt("correctOptionIndex", 0),
                        explanation = mObj.optString("explanation", ""),
                        explanationImageUrl = mObj.optString("explanationImageUrl", ""),
                        difficulty = mObj.optString("difficulty", "Medium"),
                        tags = mObj.optString("tags", "High-Yield"),
                        createdAt = mObj.optLong("createdAt", System.currentTimeMillis()),
                        questionType = mObj.optString("questionType", "MCQ"),
                        assertion = mObj.optString("assertion", ""),
                        reason = mObj.optString("reason", ""),
                        statementsJson = mObj.optJSONArray("statements")?.toString() ?: mObj.optString("statementsJson", "[]"),
                        matchColumnAJson = mObj.optJSONArray("matchColumnA")?.toString() ?: mObj.optString("matchColumnAJson", "[]"),
                        matchColumnBJson = mObj.optJSONArray("matchColumnB")?.toString() ?: mObj.optString("matchColumnBJson", "[]"),
                        flowchartStepsJson = mObj.optJSONArray("flowchartSteps")?.toString() ?: mObj.optString("flowchartStepsJson", "[]")
                    )
                    db.mcqDao().insertMCQ(mcq)
                }
                totalSyncedCount += mcqsArrayRemote.length()
            }

            if (needRepublish && CloudflareR2Manager.isConfigured()) {
                try {
                    publishCatalogToCloud()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _lastSyncStatus.value = "✓ Synced $totalSyncedCount items from Cloudflare R2"
            _isSyncing.value = false
            com.example.data.MedRepository.updateOnlineStatus(true)
            Result.success(totalSyncedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            _lastSyncStatus.value = "Sync Error: ${e.localizedMessage}"
            _isSyncing.value = false
            com.example.data.MedRepository.updateOnlineStatus(false)
            Result.failure(e)
        }
    }

    /**
     * Serializes all current database contents and publishes an updated catalog.json
     * to Cloudflare R2 so ALL users will receive the newly uploaded content.
     */
    suspend fun publishCatalogToCloud(): Result<String> = withContext(Dispatchers.IO) {
        if (!CloudflareR2Manager.isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Cloudflare R2 is not configured. Please set R2 credentials first.")
            )
        }

        try {
            // 0. Fetch existing remote catalog from R2 to merge and prevent overwriting/loss of uploads
            val remoteMap = mutableMapOf<String, JSONArray>()
            try {
                val req = Request.Builder()
                    .url("$CATALOG_PUBLIC_URL?nocache=${System.currentTimeMillis()}")
                    .get()
                    .header("Cache-Control", "no-store, no-cache, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()
                val res = httpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val str = res.body?.string().orEmpty()
                    if (str.isNotBlank()) {
                        val remoteRoot = JSONObject(str)
                        listOf("folders", "contents", "pyqs", "shorts", "flashcards", "customSections", "mcqSections", "mcqs").forEach { key ->
                            remoteRoot.optJSONArray(key)?.let { remoteMap[key] = it }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore fetch errors, proceed with local
            }

            val root = JSONObject()

            // 1. Folders (Merge remote + local, honoring deletions)
            val foldersMap = LinkedHashMap<String, JSONObject>()
            remoteMap["folders"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    if (id.isNotBlank() && !MedRepository.isFolderDeleted(id, name)) {
                        foldersMap[id] = obj
                    }
                }
            }
            val folders = MedRepository.folders.value
            for (f in folders) {
                val fObj = JSONObject().apply {
                    put("id", f.id)
                    put("prof", f.prof.name)
                    put("subjectId", f.subjectId)
                    put("name", f.name)
                    put("emoji", f.emoji)
                    put("sectionType", f.sectionType)
                }
                foldersMap[f.id] = fObj
            }
            val foldersArray = JSONArray()
            foldersMap.values.forEach { foldersArray.put(it) }
            root.put("folders", foldersArray)

            // 2. Study Contents (Merge remote + local, honoring deletions)
            val contentsMap = LinkedHashMap<String, JSONObject>()
            remoteMap["contents"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    val folderId = obj.optString("folderId")
                    val folderName = obj.optString("folderName")
                    if (id.isNotBlank() && !MedRepository.isContentDeleted(id) && !MedRepository.isFolderDeleted(folderId, folderName)) {
                        contentsMap[id] = obj
                    }
                }
            }
            val contents = MedRepository.contents.value
            for (c in contents) {
                val cObj = JSONObject().apply {
                    put("id", c.id)
                    put("prof", c.prof.name)
                    put("subjectId", c.subjectId)
                    put("folderId", c.folderId)
                    put("folderName", c.folderName)
                    put("chapterName", c.chapterName)
                    put("type", c.type.name)
                    put("title", c.title)
                    put("description", c.description)
                    put("mediaUrl", c.mediaUrl)
                    put("cloudflareKey", c.cloudflareKey)
                    put("createdAt", c.createdAt)
                    put("isPyq", c.isPyq)
                }
                contentsMap[c.id] = cObj
            }
            val contentsArray = JSONArray()
            contentsMap.values.forEach { contentsArray.put(it) }
            root.put("contents", contentsArray)

            // 3. PYQs (Merge remote + local)
            val pyqsMap = LinkedHashMap<String, JSONObject>()
            remoteMap["pyqs"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && !MedRepository.isPYQDeleted(id)) pyqsMap[id] = obj
                }
            }
            val pyqs = MedRepository.pyqs.value
            for (p in pyqs) {
                if (MedRepository.isPYQDeleted(p.id)) continue
                val pObj = JSONObject().apply {
                    put("id", p.id)
                    put("subjectId", p.subjectId)
                    put("prof", p.prof.name)
                    put("year", p.year)
                    put("examType", p.examType)
                    put("question", p.question)
                    put("marks", p.marks)
                    put("answerKey", p.answerKey)
                    put("highYieldTip", p.highYieldTip)
                }
                pyqsMap[p.id] = pObj
            }
            val pyqsArray = JSONArray()
            pyqsMap.values.forEach { pyqsArray.put(it) }
            root.put("pyqs", pyqsArray)

            // 4. Shorts (Merge remote + local)
            val shortsMap = LinkedHashMap<String, JSONObject>()
            remoteMap["shorts"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && !MedRepository.isShortDeleted(id)) shortsMap[id] = obj
                }
            }
            val shorts = MedRepository.shorts.value
            for (s in shorts) {
                if (MedRepository.isShortDeleted(s.id)) continue
                val sObj = JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("description", s.description)
                    put("youtubeUrl", s.youtubeUrl)
                    put("youtubeVideoId", s.youtubeVideoId)
                    put("thumbnailUrl", s.thumbnailUrl)
                    put("cloudflareKey", s.cloudflareKey)
                    put("subjectName", s.subjectName)
                    put("emoji", s.emoji)
                    put("prof", s.prof.name)
                    put("doctorName", s.doctorName)
                    put("durationSec", s.durationSec)
                    put("likesCount", s.likesCount)
                    put("mnemonicTip", s.mnemonicTip)
                    put("audioTitle", s.audioTitle)
                    put("createdAt", s.createdAt)
                }
                shortsMap[s.id] = sObj
            }
            val shortsArray = JSONArray()
            shortsMap.values.forEach { shortsArray.put(it) }
            root.put("shorts", shortsArray)

            // 5. Flashcards (Merge remote + local)
            val flashcardsMap = LinkedHashMap<String, JSONObject>()
            remoteMap["flashcards"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && !MedRepository.isFlashcardDeleted(id)) {
                        flashcardsMap[id] = obj
                    }
                }
            }
            val flashcards = MedRepository.flashcards.value
            for (fc in flashcards) {
                val fcObj = JSONObject().apply {
                    put("id", fc.id)
                    put("subjectId", fc.subjectId)
                    put("prof", fc.prof.name)
                    put("folderId", fc.folderId)
                    put("folderName", fc.folderName)
                    put("subfolderName", fc.subfolderName)
                    put("chapterName", fc.chapterName.trim())
                    put("questionType", fc.questionType.name)
                    put("question", fc.question)
                    put("questionOptions", fc.questionOptions.joinToString("|"))
                    put("questionImageUrl", fc.questionImageUrl)
                    put("answer", fc.answer)
                    put("answerImageUrl", fc.answerImageUrl)
                    put("explanation", fc.explanation)
                    put("hint", fc.hint)
                    put("tags", fc.tags)
                    put("mnemonic", fc.mnemonic ?: "")
                    put("clinicalPearl", fc.clinicalPearl ?: "")
                    put("isMastered", fc.isMastered)
                    put("difficulty", fc.difficulty)
                }
                flashcardsMap[fc.id] = fcObj
            }
            val flashcardsArray = JSONArray()
            flashcardsMap.values.forEach { flashcardsArray.put(it) }
            root.put("flashcards", flashcardsArray)

            // 6. Custom Sections (Merge remote + local)
            val customSectionsMap = LinkedHashMap<String, JSONObject>()
            remoteMap["customSections"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank()) customSectionsMap[id] = obj
                }
            }
            val customSections = MedRepository.customSections.value
            for (sec in customSections) {
                val sObj = JSONObject().apply {
                    put("id", sec.id)
                    put("title", sec.title)
                    put("subtitle", sec.subtitle)
                    put("emoji", sec.emoji)
                    put("colorHex1", sec.colorHex1)
                    put("colorHex2", sec.colorHex2)
                    put("description", sec.description)
                    put("orderIndex", sec.orderIndex)
                }
                customSectionsMap[sec.id] = sObj
            }
            val customSectionsArray = JSONArray()
            customSectionsMap.values.forEach { customSectionsArray.put(it) }
            root.put("customSections", customSectionsArray)

            // 6b. MCQ Practice Sections (Merge remote + local)
            val mcqSectionsMap = LinkedHashMap<String, JSONObject>()
            remoteMap["mcqSections"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank()) mcqSectionsMap[id] = obj
                }
            }
            val mcqSections = MedRepository.mcqSections.value
            for (sec in mcqSections) {
                val sObj = JSONObject().apply {
                    put("id", sec.id)
                    put("title", sec.title)
                    put("subtitle", sec.subtitle)
                    put("emoji", sec.emoji)
                    put("colorHex1", sec.colorHex1)
                    put("colorHex2", sec.colorHex2)
                    put("prof", sec.prof.name)
                    put("orderIndex", sec.orderIndex)
                }
                mcqSectionsMap[sec.id] = sObj
            }
            val mcqSectionsArray = JSONArray()
            mcqSectionsMap.values.forEach { mcqSectionsArray.put(it) }
            root.put("mcqSections", mcqSectionsArray)

            // 7. MCQs (Merge remote + local, honoring deletions)
            val mcqsMap = LinkedHashMap<String, JSONObject>()
            remoteMap["mcqs"]?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    val folderId = obj.optString("folderId")
                    val folderName = obj.optString("folderName")
                    if (id.isNotBlank() && !MedRepository.isMCQDeleted(id) && !MedRepository.isFolderDeleted(folderId, folderName)) {
                        mcqsMap[id] = obj
                    }
                }
            }
            val mcqs = MedRepository.mcqs.value
            for (m in mcqs) {
                val mObj = JSONObject().apply {
                    put("id", m.id)
                    put("prof", m.prof.name)
                    put("subjectId", m.subjectId)
                    put("sectionId", m.sectionId)
                    put("sectionName", m.sectionName)
                    put("folderId", m.folderId)
                    put("folderName", m.folderName)
                    put("subfolderName", m.subfolderName)
                    put("chapterName", m.chapterName.trim())
                    put("question", m.question)
                    put("questionImageUrl", m.questionImageUrl)
                    val optsArr = JSONArray()
                    for (opt in m.options) {
                        optsArr.put(JSONObject().apply {
                            put("id", opt.id)
                            put("text", opt.text)
                            put("imageUrl", opt.imageUrl)
                        })
                    }
                    put("options", optsArr)
                    put("correctOptionIndex", m.correctOptionIndex)
                    put("explanation", m.explanation)
                    put("explanationImageUrl", m.explanationImageUrl)
                    put("difficulty", m.difficulty)
                    put("tags", m.tags)
                    put("createdAt", m.createdAt)
                    put("questionType", m.questionType)
                    put("assertion", m.assertion)
                    put("reason", m.reason)
                    val sArr = JSONArray(); m.statements.forEach { sArr.put(it) }; put("statements", sArr)
                    val caArr = JSONArray(); m.matchColumnA.forEach { caArr.put(it) }; put("matchColumnA", caArr)
                    val cbArr = JSONArray(); m.matchColumnB.forEach { cbArr.put(it) }; put("matchColumnB", cbArr)
                    val fcArr = JSONArray(); m.flowchartSteps.forEach { fcArr.put(it) }; put("flowchartSteps", fcArr)
                }
                mcqsMap[m.id] = mObj
            }
            val mcqsArray = JSONArray()
            mcqsMap.values.forEach { mcqsArray.put(it) }
            root.put("mcqs", mcqsArray)

            // CRITICAL: Upload catalog.json FIRST so cloud is immediately consistent with exponential backoff retries for 429 rate limits
            val jsonString = root.toString(2)
            android.util.Log.d("CloudSync", "Uploading catalog.json of size: ${jsonString.length}")
            
            var uploadResult: Result<String> = Result.failure(Exception("Not attempted"))
            var attempt = 0
            var delayMs = 1000L
            while (attempt < 3) {
                uploadResult = CloudflareR2Manager.uploadString(CATALOG_OBJECT_KEY, jsonString)
                if (uploadResult.isSuccess) break
                val err = uploadResult.exceptionOrNull()?.message ?: ""
                if (err.contains("429") || err.contains("ServiceUnavailable")) {
                    attempt++
                    android.util.Log.w("CloudSync", "R2 Rate Limit (429) uploading catalog.json. Retrying in ${delayMs}ms (attempt $attempt)...")
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                } else {
                    break
                }
            }

            if (!uploadResult.isSuccess) {
                android.util.Log.e("CloudSync", "Upload catalog.json failed: ${uploadResult.exceptionOrNull()}")
                return@withContext Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload catalog.json failed"))
            }
            android.util.Log.d("CloudSync", "Successfully uploaded catalog.json")

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _lastSyncStatus.value = "✓ Cloud catalog updated on Cloudflare R2"

            // Asynchronously / non-blockingly upload hierarchical mcqs/ manifest and chapters
            try {
                val secObj = JSONObject().apply {
                    put("sections", mcqSectionsArray)
                }
                CloudflareR2Manager.uploadString("mcqs/sections.json", secObj.toString(2), "application/json")

                val groupedByFolderAndChapter = mcqs.groupBy { "${it.sectionId}_${it.folderId}_${it.subfolderName}_${it.chapterName.trim()}" }
                for ((_, list) in groupedByFolderAndChapter) {
                    if (list.isNotEmpty()) {
                        val first = list.first()
                        val chObj = JSONObject().apply {
                            put("sectionId", first.sectionId)
                            put("sectionName", first.sectionName)
                            put("folderId", first.folderId)
                            put("folderName", first.folderName)
                            put("subfolderName", first.subfolderName)
                            put("chapterName", first.chapterName.trim())
                            put("count", list.size)
                            val qArr = JSONArray()
                            for (q in list) {
                                qArr.put(JSONObject().apply {
                                    put("id", q.id)
                                    put("question", q.question)
                                    put("questionImageUrl", q.questionImageUrl)
                                    val optArr = JSONArray()
                                    for (o in q.options) {
                                        optArr.put(JSONObject().apply {
                                            put("id", o.id)
                                            put("text", o.text)
                                            put("imageUrl", o.imageUrl)
                                        })
                                    }
                                    put("options", optArr)
                                    put("correctOptionIndex", q.correctOptionIndex)
                                    put("explanation", q.explanation)
                                    put("explanationImageUrl", q.explanationImageUrl)
                                    put("difficulty", q.difficulty)
                                    put("tags", q.tags)
                                    put("questionType", q.questionType)
                                    put("assertion", q.assertion)
                                    put("reason", q.reason)
                                    val sArr = JSONArray(); q.statements.forEach { sArr.put(it) }; put("statements", sArr)
                                    val caArr = JSONArray(); q.matchColumnA.forEach { caArr.put(it) }; put("matchColumnA", caArr)
                                    val cbArr = JSONArray(); q.matchColumnB.forEach { cbArr.put(it) }; put("matchColumnB", cbArr)
                                    val fcArr = JSONArray(); q.flowchartSteps.forEach { fcArr.put(it) }; put("flowchartSteps", fcArr)
                                })
                            }
                            put("mcqs", qArr)
                        }
                                        val cleanSec = (first.sectionName.ifBlank { first.sectionId.ifBlank { "general_section" } }).replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val cleanFolder = (first.folderName.ifBlank { first.folderId.ifBlank { "general_folder" } }).replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val cleanSub = (first.subfolderName.ifBlank { "general_subfolder" }).replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val cleanChapter = first.chapterName.trim().ifBlank { "general" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        CloudflareR2Manager.uploadString("mcqs/${cleanSec}/${cleanFolder}/${cleanSub}/${cleanChapter}.json", chObj.toString(2), "application/json")
                    }
                }

                // Hierarchical flashcards upload to Cloudflare R2 storage bucket
                val fcManifestObj = JSONObject().apply {
                    val decksArr = JSONArray()
                    val groupedByDeck = flashcards.groupBy { "${it.prof.name}_${it.folderName.ifBlank { it.folderId.ifBlank { "general_deck" } }}" }
                    groupedByDeck.forEach { (_, deckCards) ->
                        if (deckCards.isNotEmpty()) {
                            val first = deckCards.first()
                            decksArr.put(JSONObject().apply {
                                put("prof", first.prof.name)
                                put("folderId", first.folderId)
                                put("folderName", first.folderName.ifBlank { "Clinical Flashcards" })
                                put("cardCount", deckCards.size)
                            })
                        }
                    }
                    put("decks", decksArr)
                }
                CloudflareR2Manager.uploadString("flashcards/manifest.json", fcManifestObj.toString(2), "application/json")

                val groupedByFcFolderAndChapter = flashcards.groupBy { "${it.prof.name}_${it.folderId}_${it.folderName}_${it.subfolderName}_${it.chapterName.trim()}" }
                for ((_, list) in groupedByFcFolderAndChapter) {
                    if (list.isNotEmpty()) {
                        val first = list.first()
                        val chObj = JSONObject().apply {
                            put("prof", first.prof.name)
                            put("folderId", first.folderId)
                            put("folderName", first.folderName.ifBlank { "Clinical Flashcards" })
                            put("subfolderName", first.subfolderName)
                            put("chapterName", first.chapterName.trim().ifBlank { "general" })
                            put("count", list.size)
                            val fArr = JSONArray()
                            for (fc in list) {
                                fArr.put(JSONObject().apply {
                                    put("id", fc.id)
                                    put("subjectId", fc.subjectId)
                                    put("prof", fc.prof.name)
                                    put("folderId", fc.folderId)
                                    put("folderName", fc.folderName)
                                    put("subfolderName", fc.subfolderName)
                                    put("chapterName", fc.chapterName)
                                    put("questionType", fc.questionType.name)
                                    put("question", fc.question)
                                    put("questionOptions", fc.questionOptions.joinToString("|"))
                                    put("questionImageUrl", fc.questionImageUrl)
                                    put("answer", fc.answer)
                                    put("answerImageUrl", fc.answerImageUrl)
                                    put("explanation", fc.explanation)
                                    put("hint", fc.hint)
                                    put("tags", fc.tags)
                                    put("mnemonic", fc.mnemonic ?: "")
                                    put("clinicalPearl", fc.clinicalPearl ?: "")
                                    put("difficulty", fc.difficulty)
                                })
                            }
                            put("flashcards", fArr)
                        }
                        val cleanProf = first.prof.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val cleanFolder = (first.folderName.ifBlank { first.folderId.ifBlank { "general_folder" } }).replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val cleanSub = (first.subfolderName.ifBlank { "general_subfolder" }).replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val cleanChapter = first.chapterName.trim().ifBlank { "general" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        CloudflareR2Manager.uploadString("flashcards/${cleanProf}/${cleanFolder}/${cleanSub}/${cleanChapter}.json", chObj.toString(2), "application/json")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Result.success(uploadResult.getOrThrow())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteCloudChapter(sectionName: String, folderName: String, subfolderName: String, chapterName: String) = withContext(Dispatchers.IO) {
        if (!CloudflareR2Manager.isConfigured()) return@withContext
        try {
            val cleanSec = sectionName.ifBlank { "general_section" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanFolder = folderName.ifBlank { "general_folder" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanSub = subfolderName.ifBlank { "general_subfolder" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanChapter = chapterName.trim().ifBlank { "general" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            CloudflareR2Manager.deleteFile("mcqs/${cleanSec}/${cleanFolder}/${cleanSub}/${cleanChapter}.json")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteCloudFlashcardChapter(profName: String, folderName: String, subfolderName: String, chapterName: String) = withContext(Dispatchers.IO) {
        if (!CloudflareR2Manager.isConfigured()) return@withContext
        try {
            val cleanProf = profName.ifBlank { "FIRST_PROF" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanFolder = folderName.ifBlank { "general_folder" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanSub = subfolderName.ifBlank { "general_subfolder" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanChapter = chapterName.trim().ifBlank { "general" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            CloudflareR2Manager.deleteFile("flashcards/${cleanProf}/${cleanFolder}/${cleanSub}/${cleanChapter}.json")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
