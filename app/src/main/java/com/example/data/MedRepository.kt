package com.example.data

import android.content.Context
import com.example.data.local.*
import com.example.model.*
import com.example.util.FlashcardUtils
import com.example.data.cloud.CloudSyncEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object MedRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: MedXMedDatabase? = null
    private val jsonSerializer = Json { ignoreUnknownKeys = true }

    // User Profile state
    private var sharedPrefs: android.content.SharedPreferences? = null

    private val _userName = MutableStateFlow("Dr. Viv")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userAvatar = MutableStateFlow("👨‍⚕️")
    val userAvatar: StateFlow<String> = _userAvatar.asStateFlow()

    private val _userPhotoUri = MutableStateFlow<String?>(null)
    val userPhotoUri: StateFlow<String?> = _userPhotoUri.asStateFlow()

    private val _isOnline = MutableStateFlow(true) // Default to true or check network
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _currentTheme = MutableStateFlow(0) // 0: White, 1: Neon, 2: Study
    val currentTheme: StateFlow<Int> = _currentTheme.asStateFlow()

    private val _bookmarkedMCQs = MutableStateFlow<List<MCQBookmark>>(emptyList())
    val bookmarkedMCQs: StateFlow<List<MCQBookmark>> = _bookmarkedMCQs.asStateFlow()

    private val _quizHistory = MutableStateFlow<List<MCQQuizHistory>>(emptyList())
    val quizHistory: StateFlow<List<MCQQuizHistory>> = _quizHistory.asStateFlow()

    // Hidden Items (Sections, Folders, Subfolders, Chapters)
    private val _hiddenItemKeys = MutableStateFlow<Set<String>>(emptySet())
    val hiddenItemKeys: StateFlow<Set<String>> = _hiddenItemKeys.asStateFlow()

    fun toggleHideItem(key: String) {
        val current = _hiddenItemKeys.value
        val updated = if (current.contains(key)) current - key else current + key
        _hiddenItemKeys.value = updated
        sharedPrefs?.edit()?.putStringSet("hidden_items_keys", updated)?.apply()
    }

    fun isItemHidden(key: String): Boolean {
        return _hiddenItemKeys.value.contains(key)
    }

    // App Info & Settings (About Us, Notice, Privacy, Refer, Rating)
    private val defaultAboutUs = "MedXMed is a dedicated MBBS learning & revision platform designed by doctors for medical students. Access high-yield clinical notes, flashcards, past exam questions (PYQs), interactive MCQs, and video lectures all in one place."
    private val defaultNotice = "All MBBS study modules, question banks, and interactive MCQ sessions are actively updated and synchronized."
    private val defaultPrivacy = "MedXMed Privacy Policy: Your data is stored locally. We do not collect it."

    private val defaultPrivacyUrl = "https://sites.google.com/view/medxmed-privacy-policy/home"
    private val defaultRefer = "Study MBBS smartly with MedXMed App! Practice high-yield MCQs, PYQs, flashcards, and notes."
    private val defaultReferUrl = "https://play.google.com/store/apps/details?id=com.example.medxmed"
    private val defaultRatingUrl = "https://play.google.com/store/apps/details?id=com.example.medxmed"

    private val _aboutUsText = MutableStateFlow(defaultAboutUs)
    val aboutUsText: StateFlow<String> = _aboutUsText.asStateFlow()

    private val _aboutUsUrl = MutableStateFlow("")
    val aboutUsUrl: StateFlow<String> = _aboutUsUrl.asStateFlow()

    private val _noticeText = MutableStateFlow(defaultNotice)
    val noticeText: StateFlow<String> = _noticeText.asStateFlow()

    private val _noticeUrl = MutableStateFlow("")
    val noticeUrl: StateFlow<String> = _noticeUrl.asStateFlow()

    private val _privacyText = MutableStateFlow(defaultPrivacy)
    val privacyText: StateFlow<String> = _privacyText.asStateFlow()

    private val _privacyUrl = MutableStateFlow(defaultPrivacyUrl)
    val privacyUrl: StateFlow<String> = _privacyUrl.asStateFlow()

    private val _referText = MutableStateFlow(defaultRefer)
    val referText: StateFlow<String> = _referText.asStateFlow()

    private val _referUrl = MutableStateFlow(defaultReferUrl)
    val referUrl: StateFlow<String> = _referUrl.asStateFlow()

    private val _ratingUrl = MutableStateFlow(defaultRatingUrl)
    val ratingUrl: StateFlow<String> = _ratingUrl.asStateFlow()

    fun updateAppInfoSettings(
        aboutText: String,
        aboutUrl: String,
        noticeTextVal: String,
        noticeUrlVal: String,
        privacyTextVal: String,
        privacyUrlVal: String,
        referTextVal: String,
        referUrlVal: String,
        ratingUrlVal: String
    ) {
        _aboutUsText.value = aboutText
        _aboutUsUrl.value = aboutUrl
        _noticeText.value = noticeTextVal
        _noticeUrl.value = noticeUrlVal
        _privacyText.value = privacyTextVal
        _privacyUrl.value = privacyUrlVal
        _referText.value = referTextVal
        _referUrl.value = referUrlVal
        _ratingUrl.value = ratingUrlVal

        sharedPrefs?.edit()
            ?.putString("about_us_text", aboutText)
            ?.putString("about_us_url", aboutUrl)
            ?.putString("notice_text", noticeTextVal)
            ?.putString("notice_url", noticeUrlVal)
            ?.putString("privacy_text", privacyTextVal)
            ?.putString("privacy_url", privacyUrlVal)
            ?.putString("refer_text", referTextVal)
            ?.putString("refer_url", referUrlVal)
            ?.putString("rating_url", ratingUrlVal)
            ?.apply()
    }

    fun updateTheme(themeIndex: Int) {
        _currentTheme.value = themeIndex
        sharedPrefs?.edit()?.putInt("app_theme", themeIndex)?.apply()
    }

    fun updateOnlineStatus(isOnline: Boolean) {
        _isOnline.value = isOnline
    }

    fun checkNetworkOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)
            val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            _isOnline.value = hasInternet
            hasInternet
        } catch (e: Exception) {
            _isOnline.value = false
            false
        }
    }

    fun clearLocalTempPdfFiles(context: Context) {
        try {
            context.cacheDir.listFiles { _, name -> name.endsWith(".pdf") || name.startsWith("pdf_") || name.startsWith("temp_") }?.forEach {
                it.delete()
            }
        } catch (_: Exception) {}
    }

    fun updateProfile(name: String, avatar: String, photoUri: String? = null) {
        _userName.value = name.ifBlank { "Dr. Student" }
        _userAvatar.value = avatar
        _userPhotoUri.value = photoUri
        sharedPrefs?.edit()
            ?.putString("user_name", _userName.value)
            ?.putString("user_avatar", avatar)
            ?.putString("user_photo_uri", photoUri ?: "")
            ?.apply()
    }

    val allSubjects: List<Subject> = listOf(
        // 1st Prof
        Subject("anat", "Anatomy", "🦴", ProfYear.FIRST_PROF, "Gross Anatomy, Embryology, Histology", 28, 142),
        Subject("physio", "Physiology", "🧪", ProfYear.FIRST_PROF, "General, CVS, CNS, Renal, Endocrine", 24, 120),
        Subject("biochem", "Biochemistry", "🧬", ProfYear.FIRST_PROF, "Metabolism, Molecular Genetics", 20, 98),

        // 2nd Prof
        Subject("path", "Pathology", "🔬", ProfYear.SECOND_PROF, "General Pathology, Hematology, Systemic", 32, 160),
        Subject("pharm", "Pharmacology", "💊", ProfYear.SECOND_PROF, "Autonomic, CNS, CVS, Chemotherapy", 30, 155),
        Subject("micro", "Microbiology", "🧫", ProfYear.SECOND_PROF, "Bacteriology, Virology, Immunology", 26, 135),

        // 3rd Prof I
        Subject("opht", "Ophthalmology", "👁️", ProfYear.THIRD_PROF_1, "Cornea, Cataract, Glaucoma, Retina", 18, 85),
        Subject("ent", "ENT", "👂", ProfYear.THIRD_PROF_1, "Otology, Rhinology, Larynx, Head & Neck", 16, 80),
        Subject("psm", "Community Medicine", "📊", ProfYear.THIRD_PROF_1, "Epidemiology, Biostats, Health Programs", 25, 110),
        Subject("fmt", "Forensic Medicine", "⚖️", ProfYear.THIRD_PROF_1, "Thanatology, Toxicology, Legal", 15, 75),

        // 3rd Prof II (Final Prof)
        Subject("med", "General Medicine", "🩺", ProfYear.THIRD_PROF_2, "Cardiology, Neurology, Pulmonology", 40, 220),
        Subject("surg", "General Surgery", "🔪", ProfYear.THIRD_PROF_2, "Trauma, GI, Urology, Orthopedics", 38, 205),
        Subject("obg", "Obstetrics & Gynecology", "👶", ProfYear.THIRD_PROF_2, "Obstetrics, Labor, Gynecology", 30, 150),
        Subject("peds", "Pediatrics", "🍼", ProfYear.THIRD_PROF_2, "Neonatology, Growth & Milestones", 22, 115)
    )

    // Reactive StateFlows for UI layer - starts empty (all fake samples removed)
    const val CLOUDFLARE_R2_BASE_URL = "https://pub-a0c1c1a64a7f45b6b844455c74ed370d.r2.dev"
    const val CLOUDFLARE_BUCKET_NAME = "medxrattamaar"

    private val _folders = MutableStateFlow<List<ContentFolder>>(emptyList())
    val folders: StateFlow<List<ContentFolder>> = _folders.asStateFlow()

    private val _contents = MutableStateFlow<List<StudyContentItem>>(emptyList())
    val contents: StateFlow<List<StudyContentItem>> = _contents.asStateFlow()

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    private val _cachedOfflineNotes = MutableStateFlow<List<NoteItem>>(emptyList())
    val cachedOfflineNotes: StateFlow<List<NoteItem>> = _cachedOfflineNotes.asStateFlow()

    private val _flashcards = MutableStateFlow<List<FlashcardItem>>(emptyList())
    val flashcards: StateFlow<List<FlashcardItem>> = _flashcards.asStateFlow()

    private val _savedOfflineFlashcards = MutableStateFlow<List<FlashcardItem>>(emptyList())
    val savedOfflineFlashcards: StateFlow<List<FlashcardItem>> = _savedOfflineFlashcards.asStateFlow()

    private val _pyqs = MutableStateFlow<List<PYQItem>>(emptyList())
    val pyqs: StateFlow<List<PYQItem>> = _pyqs.asStateFlow()

    private val _lectures = MutableStateFlow<List<LectureItem>>(emptyList())
    val lectures: StateFlow<List<LectureItem>> = _lectures.asStateFlow()

    private val _shorts = MutableStateFlow<List<ShortItem>>(emptyList())
    val shorts: StateFlow<List<ShortItem>> = _shorts.asStateFlow()

    private val _customSections = MutableStateFlow<List<CustomSection>>(emptyList())
    val customSections: StateFlow<List<CustomSection>> = _customSections.asStateFlow()

    private val _mcqSections = MutableStateFlow<List<MCQSection>>(emptyList())
    val mcqSections: StateFlow<List<MCQSection>> = _mcqSections.asStateFlow()

    private val _mcqs = MutableStateFlow<List<MCQItem>>(emptyList())
    val mcqs: StateFlow<List<MCQItem>> = _mcqs.asStateFlow()

    fun initialize(context: Context) {
        if (database != null) return
        sharedPrefs = context.getSharedPreferences("medxmed_prefs", Context.MODE_PRIVATE)
        _userName.value = sharedPrefs?.getString("user_name", "Dr. Viv") ?: "Dr. Viv"
        _userAvatar.value = sharedPrefs?.getString("user_avatar", "👨‍⚕️") ?: "👨‍⚕️"
        _currentTheme.value = sharedPrefs?.getInt("app_theme", 0) ?: 0
        val savedPhoto = sharedPrefs?.getString("user_photo_uri", null)
        if (!savedPhoto.isNullOrBlank()) {
            _userPhotoUri.value = savedPhoto
        }

        // Load Bookmarks and Quiz History
        val savedBookmarksJson = sharedPrefs?.getString("mcq_bookmarks_json", null)
        if (!savedBookmarksJson.isNullOrBlank()) {
            try {
                _bookmarkedMCQs.value = jsonSerializer.decodeFromString(savedBookmarksJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val savedHistoryJson = sharedPrefs?.getString("mcq_quiz_history_json", null)
        if (!savedHistoryJson.isNullOrBlank()) {
            try {
                _quizHistory.value = jsonSerializer.decodeFromString(savedHistoryJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val savedHidden = sharedPrefs?.getStringSet("hidden_items_keys", emptySet()) ?: emptySet()
        _hiddenItemKeys.value = savedHidden

        // Load App Info Settings (About Us, Notice, Privacy, Refer, Rating)
        _aboutUsText.value = sharedPrefs?.getString("about_us_text", defaultAboutUs) ?: defaultAboutUs
        _aboutUsUrl.value = sharedPrefs?.getString("about_us_url", "") ?: ""
        _noticeText.value = sharedPrefs?.getString("notice_text", defaultNotice) ?: defaultNotice
        _noticeUrl.value = sharedPrefs?.getString("notice_url", "") ?: ""
        _privacyText.value = sharedPrefs?.getString("privacy_text", defaultPrivacy) ?: defaultPrivacy
        val loadedPrivacyUrl = sharedPrefs?.getString("privacy_url", null)
        _privacyUrl.value = if (loadedPrivacyUrl.isNullOrBlank()) defaultPrivacyUrl else loadedPrivacyUrl
        _referText.value = sharedPrefs?.getString("refer_text", defaultRefer) ?: defaultRefer
        _referUrl.value = sharedPrefs?.getString("refer_url", defaultReferUrl) ?: defaultReferUrl
        _ratingUrl.value = sharedPrefs?.getString("rating_url", defaultRatingUrl) ?: defaultRatingUrl

        // Initialize Cloudflare R2
        com.example.data.cloud.CloudflareR2Manager.initialize(context)

        val db = MedXMedDatabase.getDatabase(context)
        database = db

        // Bind Room DAOs to StateFlows
        repositoryScope.launch {
            db.folderDao().getAllFolders()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list -> _folders.value = list }
        }

        repositoryScope.launch {
            db.noteDao().getAllNotes()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list ->
                    if (list.isNotEmpty() || _contents.value.none { it.type == StudyContentType.PDF }) {
                        _notes.value = list
                    }
                }
        }

        repositoryScope.launch {
            db.noteDao().getCachedOfflineNotes()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list -> _cachedOfflineNotes.value = list }
        }

        repositoryScope.launch {
            db.studyContentDao().getAllContents()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list ->
                    _contents.value = list
                    // Also populate _notes and _lectures for cross-compatibility
                    val notesList = list.filter { it.type == StudyContentType.PDF }.map {
                        NoteItem(
                            id = it.id,
                            subjectId = it.subjectId,
                            prof = it.prof,
                            title = it.title,
                            chapter = if (it.chapterName.isNotBlank()) it.chapterName else it.folderName,
                            pagesCount = 10,
                            readMinutes = 15,
                            highYieldSummary = it.description.ifBlank { "High-Yield MBBS lecture notes and clinical companion for ${it.title}." },
                            pdfMockUrl = it.mediaUrl,
                            isCachedOffline = true
                        )
                    }
                    val lecturesList = list.filter { it.type == StudyContentType.VIDEO }.map {
                        LectureItem(
                            id = it.id,
                            subjectId = it.subjectId,
                            prof = it.prof,
                            title = it.title,
                            chapter = if (it.chapterName.isNotBlank()) it.chapterName else it.folderName,
                            faculty = "Faculty",
                            duration = "Video",
                            videoUrl = it.mediaUrl
                        )
                    }
                    if (notesList.isNotEmpty()) {
                        _notes.value = notesList
                        repositoryScope.launch {
                            db.noteDao().insertNotes(notesList.map { NoteEntity.fromDomain(it) })
                        }
                    }
                    _lectures.value = lecturesList
                }
        }

        repositoryScope.launch {
            db.flashcardDao().getAllFlashcards()
                .map { entities -> FlashcardUtils.sortFlashcardsSerially(entities.map { it.toDomain() }) }
                .collect { list -> 
                    _flashcards.value = list 
                    ensureFoldersExistForFlashcards(list)
                }
        }

        repositoryScope.launch {
            db.flashcardDao().getSavedOfflineFlashcards()
                .map { entities -> FlashcardUtils.sortFlashcardsSerially(entities.map { it.toDomain() }) }
                .collect { list -> _savedOfflineFlashcards.value = list }
        }

        repositoryScope.launch {
            db.pyqDao().getAllPYQs()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list -> _pyqs.value = list }
        }

        repositoryScope.launch {
            db.shortDao().getAllShorts()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list -> _shorts.value = list }
        }

        repositoryScope.launch {
            db.mcqSectionDao().getAllSections()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list -> _mcqSections.value = list }
        }

        repositoryScope.launch {
            db.mcqDao().getAllMCQs()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list ->
                    _mcqs.value = list
                    ensureFoldersExistForMCQs(list)
                }
        }

        // Automatic background sync from Cloudflare R2 on startup so ALL users get updated content
        repositoryScope.launch {
            purgeAdminExcludedData()
            delay(1200L)
            com.example.data.cloud.CloudSyncEngine.syncFromCloud(context)
        }
    }

        fun triggerCloudSync(context: Context) {
        repositoryScope.launch {
            com.example.data.cloud.CloudSyncEngine.syncFromCloud(context)
        }
    }

    private var catalogPublishJob: kotlinx.coroutines.Job? = null

    fun triggerCatalogPublish() {
        catalogPublishJob?.cancel()
        catalogPublishJob = repositoryScope.launch {
            kotlinx.coroutines.delay(1500L)
            if (com.example.data.cloud.CloudflareR2Manager.isConfigured()) {
                com.example.data.cloud.CloudSyncEngine.publishCatalogToCloud()
            }
        }
    }

    fun buildCloudflareUrl(prof: ProfYear, subjectId: String, folderName: String, fileName: String): String {
        val cleanProf = prof.code
        val cleanSubject = subjectId.trim().replace(" ", "_")
        val cleanFolder = folderName.trim().replace(" ", "_")
        val cleanFile = fileName.trim().replace(" ", "_")
        val baseUrl = com.example.data.cloud.CloudflareR2Manager.publicBaseUrl.value.trimEnd('/')
        return "$baseUrl/$cleanProf/$cleanSubject/$cleanFolder/$cleanFile"
    }

    fun buildCloudflarePyqUrl(folderName: String, chapterName: String = "", fileName: String): String {
        val cleanFolder = folderName.trim().replace(" ", "_")
        val cleanChapter = chapterName.trim().replace(" ", "_")
        val cleanFile = fileName.trim().replace(" ", "_")
        val baseUrl = com.example.data.cloud.CloudflareR2Manager.publicBaseUrl.value.trimEnd('/')
        return if (cleanChapter.isNotBlank()) {
            "$baseUrl/pyq/$cleanFolder/$cleanChapter/$cleanFile"
        } else {
            "$baseUrl/pyq/$cleanFolder/$cleanFile"
        }
    }

    fun recordDeletedFolder(folderId: String, folderName: String) {
        sharedPrefs?.let { prefs ->
            val deletedFolderIds = prefs.getStringSet("deleted_folder_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            val deletedFolderNames = prefs.getStringSet("deleted_folder_names", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (folderId.isNotBlank()) deletedFolderIds.add(folderId)
            if (folderName.isNotBlank()) deletedFolderNames.add(folderName.trim().lowercase())
            prefs.edit()
                .putStringSet("deleted_folder_ids", deletedFolderIds)
                .putStringSet("deleted_folder_names", deletedFolderNames)
                .apply()
        }
    }

    fun recordDeletedContent(contentId: String) {
        sharedPrefs?.let { prefs ->
            val deletedContentIds = prefs.getStringSet("deleted_content_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (contentId.isNotBlank()) deletedContentIds.add(contentId)
            prefs.edit()
                .putStringSet("deleted_content_ids", deletedContentIds)
                .apply()
        }
    }

    fun recordDeletedMCQ(mcqId: String) {
        sharedPrefs?.let { prefs ->
            val deletedMCQIds = prefs.getStringSet("deleted_mcq_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (mcqId.isNotBlank()) deletedMCQIds.add(mcqId)
            prefs.edit()
                .putStringSet("deleted_mcq_ids", deletedMCQIds)
                .apply()
        }
    }

    fun recordDeletedFlashcard(flashcardId: String) {
        sharedPrefs?.let { prefs ->
            val deletedFlashcardIds = prefs.getStringSet("deleted_flashcard_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (flashcardId.isNotBlank()) deletedFlashcardIds.add(flashcardId)
            prefs.edit()
                .putStringSet("deleted_flashcard_ids", deletedFlashcardIds)
                .apply()
        }
    }

    fun recordDeletedPYQ(pyqId: String) {
        sharedPrefs?.let { prefs ->
            val set = prefs.getStringSet("deleted_pyq_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (pyqId.isNotBlank()) set.add(pyqId)
            prefs.edit().putStringSet("deleted_pyq_ids", set).apply()
        }
    }

    fun recordDeletedShort(shortId: String) {
        sharedPrefs?.let { prefs ->
            val set = prefs.getStringSet("deleted_short_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (shortId.isNotBlank()) set.add(shortId)
            prefs.edit().putStringSet("deleted_short_ids", set).apply()
        }
    }

    fun isFolderDeleted(folderId: String, folderName: String): Boolean {
        val prefs = sharedPrefs ?: return false
        val deletedFolderIds = prefs.getStringSet("deleted_folder_ids", emptySet()) ?: emptySet()
        val deletedFolderNames = prefs.getStringSet("deleted_folder_names", emptySet()) ?: emptySet()
        return (folderId.isNotBlank() && folderId in deletedFolderIds) ||
               (folderName.isNotBlank() && folderName.trim().lowercase() in deletedFolderNames)
    }

    fun isContentDeleted(contentId: String): Boolean {
        val prefs = sharedPrefs ?: return false
        val deletedContentIds = prefs.getStringSet("deleted_content_ids", emptySet()) ?: emptySet()
        return contentId in deletedContentIds
    }

    fun isMCQDeleted(mcqId: String): Boolean {
        val prefs = sharedPrefs ?: return false
        val deletedMCQIds = prefs.getStringSet("deleted_mcq_ids", emptySet()) ?: emptySet()
        return mcqId in deletedMCQIds
    }

    fun isFlashcardDeleted(flashcardId: String): Boolean {
        val prefs = sharedPrefs ?: return false
        val deletedFlashcardIds = prefs.getStringSet("deleted_flashcard_ids", emptySet()) ?: emptySet()
        return flashcardId in deletedFlashcardIds
    }

    fun isPYQDeleted(pyqId: String): Boolean {
        val prefs = sharedPrefs ?: return false
        val set = prefs.getStringSet("deleted_pyq_ids", emptySet()) ?: emptySet()
        return pyqId in set
    }

    fun isShortDeleted(shortId: String): Boolean {
        val prefs = sharedPrefs ?: return false
        val set = prefs.getStringSet("deleted_short_ids", emptySet()) ?: emptySet()
        return shortId in set
    }

    fun clearDeletedRecord(id: String, name: String = "") {
        sharedPrefs?.let { prefs ->
            val deletedFolderIds = prefs.getStringSet("deleted_folder_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            val deletedFolderNames = prefs.getStringSet("deleted_folder_names", emptySet())?.toMutableSet() ?: mutableSetOf()
            val deletedContentIds = prefs.getStringSet("deleted_content_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            val deletedMCQIds = prefs.getStringSet("deleted_mcq_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (id.isNotBlank()) {
                deletedFolderIds.remove(id)
                deletedContentIds.remove(id)
                deletedMCQIds.remove(id)
            }
            if (name.isNotBlank()) {
                deletedFolderNames.remove(name.trim().lowercase())
            }
            prefs.edit()
                .putStringSet("deleted_folder_ids", deletedFolderIds)
                .putStringSet("deleted_folder_names", deletedFolderNames)
                .putStringSet("deleted_content_ids", deletedContentIds)
                .putStringSet("deleted_mcq_ids", deletedMCQIds)
                .apply()
        }
    }

    fun createFolder(folder: ContentFolder) {
        clearDeletedRecord(folder.id, folder.name)
        _folders.update { list -> (list.filterNot { it.id == folder.id || it.name.trim().equals(folder.name.trim(), ignoreCase = true) } + folder) }
        val db = database ?: return
        repositoryScope.launch {
            db.folderDao().insertFolder(ContentFolderEntity.fromDomain(folder))
            triggerCatalogPublish()
        }
    }

    fun renameFolder(folderId: String, newName: String, newEmoji: String) {
        _folders.update { list ->
            list.map { if (it.id == folderId) it.copy(name = newName, emoji = newEmoji) else it }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.folderDao().renameFolder(folderId, newName, newEmoji)
            triggerCatalogPublish()
        }
    }

    private fun deleteR2AssetIfPresent(url: String?) {
        if (url.isNullOrBlank()) return
        val r2Base = com.example.data.cloud.CloudflareR2Manager.PUBLIC_BASE_URL.trimEnd('/')
        val publicBaseUrl = com.example.data.cloud.CloudflareR2Manager.publicBaseUrl.value.trimEnd('/')
        val objectKey = when {
            url.startsWith(r2Base) -> url.substring(r2Base.length)
            url.startsWith(publicBaseUrl) -> url.substring(publicBaseUrl.length)
            else -> null
        }
        if (objectKey != null) {
            repositoryScope.launch {
                com.example.data.cloud.CloudflareR2Manager.deleteFile(objectKey)
            }
        }
    }

    fun deleteFolder(folderId: String, folderName: String = "", triggerPublish: Boolean = true) {
        val targetFolder = _folders.value.firstOrNull { it.id == folderId }
        val targetName = (if (folderName.isNotBlank()) folderName else (targetFolder?.name ?: "")).trim()

        _folders.update { list ->
            list.filterNot { f -> f.id == folderId || (targetName.isNotBlank() && f.name.trim().equals(targetName, ignoreCase = true)) }
        }
        _contents.update { list ->
            list.filterNot { c -> c.folderId == folderId || (targetName.isNotBlank() && c.folderName.trim().equals(targetName, ignoreCase = true)) }
        }
        _mcqs.update { list ->
            list.filterNot { m -> m.folderId == folderId || (targetName.isNotBlank() && m.folderName.trim().equals(targetName, ignoreCase = true)) }
        }
        _flashcards.update { list ->
            list.filterNot { fc -> fc.folderId == folderId || (targetName.isNotBlank() && fc.folderName.trim().equals(targetName, ignoreCase = true)) }
        }

        recordDeletedFolder(folderId, targetName)

        val db = database ?: return
        repositoryScope.launch {
            db.folderDao().deleteFolderById(folderId)
            db.studyContentDao().deleteContentsByFolderId(folderId)
            db.mcqDao().deleteMCQsByFolderId(folderId)
            if (targetName.isNotBlank()) {
                db.folderDao().deleteFoldersByName(targetName)
                db.studyContentDao().deleteContentsByFolderName(targetName)
                db.mcqDao().deleteMCQsByFolderName(targetName)
                db.flashcardDao().deleteFlashcardsByFolderName(targetName)
            }
            if (triggerPublish) {
                triggerCatalogPublish()
            }
        }
    }

    fun deleteAllFolders() {
        val allFolders = _folders.value
        if (allFolders.isEmpty()) return
        
        allFolders.forEach { deleteFolder(it.id, it.name, triggerPublish = false) }
        
        // Final sync after all deletions
        triggerCatalogPublish()
    }

    fun addStudyContent(item: StudyContentItem) {
        clearDeletedRecord(item.id)
        _contents.update { listOf(item) + it.filterNot { c -> c.id == item.id } }
        val db = database ?: return
        repositoryScope.launch {
            db.studyContentDao().insertContent(StudyContentEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    fun editStudyContent(id: String, newTitle: String, newDesc: String) {
        _contents.update { list ->
            list.map { if (it.id == id) it.copy(title = newTitle, description = newDesc) else it }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.studyContentDao().updateContent(id, newTitle, newDesc)
            triggerCatalogPublish()
        }
    }

    fun updateStudyContentMediaUrl(id: String, newUrl: String) {
        _contents.update { list ->
            list.map { if (it.id == id) it.copy(mediaUrl = newUrl, cloudflareKey = newUrl) else it }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.studyContentDao().updateMediaUrl(id, newUrl)
            triggerCatalogPublish()
        }
    }

    fun deleteStudyContent(item: StudyContentItem) {
        recordDeletedContent(item.id)
        _contents.update { it.filterNot { c -> c.id == item.id } }
        _notes.update { it.filterNot { n -> n.id == item.id } }
        _lectures.update { it.filterNot { l -> l.id == item.id } }
        val db = database ?: return
        repositoryScope.launch {
            db.studyContentDao().deleteContentById(item.id)
            db.noteDao().deleteNoteById(item.id)
            db.lectureDao().deleteLectureById(item.id)
            
            deleteR2AssetIfPresent(item.mediaUrl)
            deleteR2AssetIfPresent(item.cloudflareKey)
            
            triggerCatalogPublish()
        }
    }

    fun addNote(item: NoteItem) {
        _notes.update { listOf(item) + it }
        val db = database ?: return
        repositoryScope.launch {
            db.noteDao().insertNote(NoteEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    private fun ensureFoldersExistForFlashcards(cards: List<FlashcardItem>) {
        if (cards.isEmpty()) return
        val currentFolderNames = _folders.value.map { it.name.trim().lowercase() }.toSet()
        val currentFolderIds = _folders.value.map { it.id }.toSet()
        val toCreate = mutableListOf<ContentFolder>()

        for (card in cards) {
            val fName = card.folderName.trim().ifBlank { "Clinical Flashcards" }
            val fId = card.folderId.trim().ifBlank { "f_${fName.hashCode()}" }
            if (!currentFolderNames.contains(fName.lowercase()) && !currentFolderIds.contains(fId)) {
                if (toCreate.none { it.name.equals(fName, ignoreCase = true) || it.id == fId }) {
                    toCreate.add(
                        ContentFolder(
                            id = fId,
                            prof = card.prof,
                            subjectId = "flashcard",
                            name = fName,
                            emoji = "📇",
                            sectionType = "FLASHCARD"
                        )
                    )
                }
            }
        }
        toCreate.forEach { createFolder(it) }
    }

    private fun ensureFoldersExistForMCQs(mcqList: List<MCQItem>) {
        if (mcqList.isEmpty()) return
        val currentFolderNames = _folders.value.map { it.name.trim().lowercase() }.toSet()
        val currentFolderIds = _folders.value.map { it.id }.toSet()

        val toCreate = mutableListOf<ContentFolder>()
        for (item in mcqList) {
            val fName = item.folderName.trim()
            val fId = item.folderId.trim()
            if (fName.isNotBlank() && !currentFolderNames.contains(fName.lowercase()) &&
                !toCreate.any { it.name.equals(fName, ignoreCase = true) }) {
                toCreate.add(
                    ContentFolder(
                        id = if (fId.isNotBlank() && !currentFolderIds.contains(fId)) fId else "f_mcq_${System.currentTimeMillis()}_${toCreate.size}",
                        prof = item.prof,
                        subjectId = "mcq",
                        name = fName,
                        emoji = "🎯",
                        sectionType = "MCQ"
                    )
                )
            }
        }
        toCreate.forEach { createFolder(it) }
    }

    fun addMCQ(item: MCQItem) {
        clearDeletedRecord(item.id)
        ensureFoldersExistForMCQs(listOf(item))
        _mcqs.update { current -> listOf(item) + current.filterNot { it.id == item.id } }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqDao().insertMCQ(MCQEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    fun addMCQs(items: List<MCQItem>) {
        if (items.isEmpty()) return
        items.forEach { clearDeletedRecord(it.id) }
        ensureFoldersExistForMCQs(items)
        _mcqs.update { current ->
            val newIds = items.map { it.id }.toSet()
            items + current.filterNot { newIds.contains(it.id) }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqDao().insertMCQs(items.map { MCQEntity.fromDomain(it) })
            triggerCatalogPublish()
        }
    }

    fun updateMCQ(item: MCQItem) {
        _mcqs.update { list -> list.map { if (it.id == item.id) item else it } }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqDao().insertMCQ(MCQEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    fun deleteMCQ(id: String) {
        recordDeletedMCQ(id)
        val target = _mcqs.value.firstOrNull { it.id == id }
        _mcqs.update { it.filterNot { m -> m.id == id } }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqDao().deleteMCQById(id)
            if (target != null) {
                deleteR2AssetIfPresent(target.questionImageUrl)
                deleteR2AssetIfPresent(target.explanationImageUrl)
            }
            triggerCatalogPublish()
        }
    }

    fun deleteMCQsByIds(ids: List<String>) {
        ids.forEach { recordDeletedMCQ(it) }
        repositoryScope.launch {
            database?.mcqDao()?.deleteMCQsByIds(ids)
            _mcqs.update { it.filterNot { m -> ids.contains(m.id) } }
            triggerCatalogPublish()
        }
    }

    fun deleteMCQsByChapter(folderId: String, folderName: String = "", subfolderName: String = "", chapterName: String) {
        _mcqs.update { it.filterNot { m ->
            (m.folderId == folderId || (folderName.isNotBlank() && m.folderName.equals(folderName, ignoreCase = true))) &&
            (subfolderName.isBlank() || m.subfolderName.equals(subfolderName, ignoreCase = true)) &&
            m.chapterName.equals(chapterName, ignoreCase = true)
        } }
        val db = database ?: return
        repositoryScope.launch {
            if (folderName.isNotBlank()) {
                db.mcqDao().deleteMCQsByFolderAndChapter(folderId, folderName, chapterName)
            } else {
                db.mcqDao().deleteMCQsByChapter(folderId, chapterName)
            }
            triggerCatalogPublish()
        }
    }

    fun deleteMCQsBySubfolder(folderId: String, folderName: String = "", subfolderName: String) {
        _mcqs.update { it.filterNot { m ->
            (m.folderId == folderId || (folderName.isNotBlank() && m.folderName.equals(folderName, ignoreCase = true))) &&
            m.subfolderName.equals(subfolderName, ignoreCase = true)
        } }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqDao().deleteMCQsBySubfolder(folderId, folderName, subfolderName)
            triggerCatalogPublish()
        }
    }

    fun deleteMCQsByFolder(folderId: String, folderName: String = "") {
        val targetFolder = _folders.value.firstOrNull { it.id == folderId }
        val targetName = (targetFolder?.name ?: folderName).trim()
        recordDeletedFolder(folderId, targetName)
        _mcqs.update { it.filterNot { m -> m.folderId == folderId || (targetName.isNotBlank() && m.folderName.equals(targetName, ignoreCase = true)) } }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqDao().deleteMCQsByFolderId(folderId)
            if (targetName.isNotBlank()) {
                db.mcqDao().deleteMCQsByFolderName(targetName)
            }
            triggerCatalogPublish()
        }
    }

    fun deleteAllMCQs() {
        val mcqFolderIds = _folders.value.filter {
            it.sectionType.equals("MCQ", ignoreCase = true) ||
            it.subjectId.equals("mcq", ignoreCase = true) ||
            it.name.contains("MCQ", ignoreCase = true) ||
            _mcqSections.value.any { sec -> sec.id == it.subjectId }
        }.map { it.id }.toSet()

        _folders.update { it.filterNot { f -> mcqFolderIds.contains(f.id) } }
        _mcqs.value = emptyList()
        val db = database ?: return
        repositoryScope.launch {
            mcqFolderIds.forEach { db.folderDao().deleteFolderById(it) }
            db.mcqDao().clearAllMCQs()
            triggerCatalogPublish()
        }
    }

    fun addMCQSection(section: MCQSection) {
        _mcqSections.update { current ->
            (current.filterNot { it.id == section.id } + section).sortedBy { it.orderIndex }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqSectionDao().insertSection(MCQSectionEntity.fromDomain(section))
            triggerCatalogPublish()
        }
    }

    fun updateMCQSection(section: MCQSection) {
        addMCQSection(section)
    }

    fun deleteMCQSection(sectionId: String) {
        val target = _mcqSections.value.firstOrNull { it.id == sectionId }
        val sectionName = target?.title ?: ""
        _mcqSections.update { it.filterNot { s -> s.id == sectionId } }

        val sectionFolders = _folders.value.filter { f ->
            f.subjectId == sectionId || (sectionName.isNotBlank() && f.subjectId.equals(sectionName, ignoreCase = true))
        }
        val sectionFolderIds = sectionFolders.map { it.id }.toSet()

        _folders.update { it.filterNot { f -> sectionFolderIds.contains(f.id) } }
        _mcqs.update { it.filterNot { m ->
            m.sectionId == sectionId ||
            (sectionName.isNotBlank() && m.sectionName.equals(sectionName, ignoreCase = true)) ||
            sectionFolderIds.contains(m.folderId)
        } }
        val db = database ?: return
        repositoryScope.launch {
            db.mcqSectionDao().deleteSectionById(sectionId)
            db.mcqDao().deleteMCQsBySectionId(sectionId)
            sectionFolderIds.forEach { fId ->
                db.folderDao().deleteFolderById(fId)
                db.mcqDao().deleteMCQsByFolderId(fId)
            }
            triggerCatalogPublish()
        }
    }

    fun updateMCQSections(sections: List<MCQSection>) {
        _mcqSections.value = sections
        val db = database ?: return
        repositoryScope.launch {
            db.mcqSectionDao().insertSections(sections.map { MCQSectionEntity.fromDomain(it) })
        }
    }

    // MCQ Bookmarks & History
    fun toggleBookmark(mcq: MCQItem, selectedOptionIndex: Int? = null) {
        val current = _bookmarkedMCQs.value
        val exists = current.any { it.id == mcq.id }
        if (exists) {
            removeBookmark(mcq.id)
        } else {
            val bm = MCQBookmark(
                id = mcq.id,
                question = mcq.question,
                options = mcq.options,
                correctOptionIndex = mcq.correctOptionIndex,
                userSelectedOptionIndex = selectedOptionIndex,
                explanation = mcq.explanation,
                explanationImageUrl = mcq.explanationImageUrl,
                chapterName = mcq.chapterName,
                folderName = mcq.folderName,
                tags = mcq.tags,
                bookmarkedAt = System.currentTimeMillis(),
                questionType = mcq.questionType,
                assertion = mcq.assertion,
                reason = mcq.reason,
                statements = mcq.statements,
                matchColumnA = mcq.matchColumnA,
                matchColumnB = mcq.matchColumnB,
                flowchartSteps = mcq.flowchartSteps
            )
            val updated = listOf(bm) + current
            _bookmarkedMCQs.value = updated
            saveBookmarksToPrefs(updated)
        }
    }

    fun removeBookmark(mcqId: String) {
        val updated = _bookmarkedMCQs.value.filterNot { it.id == mcqId }
        _bookmarkedMCQs.value = updated
        saveBookmarksToPrefs(updated)
    }

    fun clearAllBookmarks() {
        _bookmarkedMCQs.value = emptyList()
        sharedPrefs?.edit()?.remove("mcq_bookmarks_json")?.apply()
    }

    fun isBookmarked(mcqId: String): Boolean {
        return _bookmarkedMCQs.value.any { it.id == mcqId }
    }

    private fun saveBookmarksToPrefs(list: List<MCQBookmark>) {
        try {
            val serialized = jsonSerializer.encodeToString(list)
            sharedPrefs?.edit()?.putString("mcq_bookmarks_json", serialized)?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveQuizHistory(record: MCQQuizHistory) {
        val updated = listOf(record) + _quizHistory.value
        _quizHistory.value = updated
        try {
            val serialized = jsonSerializer.encodeToString(updated)
            sharedPrefs?.edit()?.putString("mcq_quiz_history_json", serialized)?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteQuizHistory(recordId: String) {
        val updated = _quizHistory.value.filterNot { it.id == recordId }
        _quizHistory.value = updated
        try {
            val serialized = jsonSerializer.encodeToString(updated)
            sharedPrefs?.edit()?.putString("mcq_quiz_history_json", serialized)?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllQuizHistory() {
        _quizHistory.value = emptyList()
        sharedPrefs?.edit()?.remove("mcq_quiz_history_json")?.apply()
    }


    fun addFlashcard(item: FlashcardItem) {
        ensureFoldersExistForFlashcards(listOf(item))
        _flashcards.update { current ->
            FlashcardUtils.sortFlashcardsSerially(listOf(item) + current)
        }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().insertFlashcard(FlashcardEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    fun addFlashcards(items: List<FlashcardItem>) {
        if (items.isEmpty()) return
        ensureFoldersExistForFlashcards(items)
        _flashcards.update { current ->
            FlashcardUtils.sortFlashcardsSerially(items + current)
        }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().insertFlashcards(items.map { FlashcardEntity.fromDomain(it) })
            triggerCatalogPublish()
        }
    }

    fun addPYQ(item: PYQItem) {
        _pyqs.update { listOf(item) + it }
        val db = database ?: return
        repositoryScope.launch {
            db.pyqDao().insertPYQ(PYQEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    fun addLecture(item: LectureItem) {
        _lectures.update { listOf(item) + it }
        val db = database ?: return
        repositoryScope.launch {
            db.lectureDao().insertLecture(LectureEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    fun addShort(item: ShortItem) {
        _shorts.update { listOf(item) + it }
        val db = database ?: return
        repositoryScope.launch {
            db.shortDao().insertShort(ShortEntity.fromDomain(item))
            triggerCatalogPublish()
        }
    }

    fun editShort(id: String, newTitle: String, newDesc: String) {
        _shorts.update { list ->
            list.map { if (it.id == id) it.copy(title = newTitle, description = newDesc) else it }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.shortDao().updateShort(id, newTitle, newDesc)
            triggerCatalogPublish()
        }
    }

    fun deleteShort(id: String) {
        recordDeletedShort(id)
        _shorts.update { it.filterNot { s -> s.id == id } }
        val db = database ?: return
        repositoryScope.launch {
            db.shortDao().deleteShortById(id)
            triggerCatalogPublish()
        }
    }

    fun deletePYQ(id: String) {
        recordDeletedPYQ(id)
        _pyqs.update { it.filterNot { p -> p.id == id } }
        val db = database ?: return
        repositoryScope.launch {
            db.pyqDao().deletePYQById(id)
            triggerCatalogPublish()
        }
    }

    fun editFlashcard(id: String, newQuestion: String, newAnswer: String, newHint: String) {
        _flashcards.update { list ->
            list.map { if (it.id == id) it.copy(question = newQuestion, answer = newAnswer, hint = newHint) else it }
        }
        val target = _flashcards.value.firstOrNull { it.id == id } ?: return
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().insertFlashcard(FlashcardEntity.fromDomain(target))
            triggerCatalogPublish()
        }
    }

    fun editFlashcardFull(
        id: String,
        newQuestion: String,
        newAnswer: String,
        newExplanation: String,
        newHint: String,
        newTags: String,
        newQuestionImg: String,
        newAnswerImg: String
    ) {
        _flashcards.update { list ->
            list.map {
                if (it.id == id) it.copy(
                    question = newQuestion,
                    answer = newAnswer,
                    explanation = newExplanation,
                    hint = newHint,
                    tags = newTags,
                    questionImageUrl = newQuestionImg,
                    answerImageUrl = newAnswerImg
                ) else it
            }
        }
        val target = _flashcards.value.firstOrNull { it.id == id } ?: return
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().insertFlashcard(FlashcardEntity.fromDomain(target))
            triggerCatalogPublish()
        }
    }

    fun deleteFlashcard(id: String) {
        recordDeletedFlashcard(id)
        val targetCard = _flashcards.value.firstOrNull { it.id == id }
        _flashcards.update { it.filterNot { f -> f.id == id } }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().deleteFlashcardById(id)
            if (targetCard != null) {
                deleteR2AssetIfPresent(targetCard.questionImageUrl)
                deleteR2AssetIfPresent(targetCard.answerImageUrl)
            }
            triggerCatalogPublish()
        }
    }

    fun deleteFlashcardsByFolder(folderName: String) {
        val cardsToDelete = _flashcards.value.filter { it.folderName.equals(folderName, ignoreCase = true) }
        cardsToDelete.forEach { recordDeletedFlashcard(it.id) }
        _flashcards.update { it.filterNot { f -> f.folderName.equals(folderName, ignoreCase = true) } }
        val targetFolder = _folders.value.firstOrNull { it.name.equals(folderName, ignoreCase = true) }
        if (targetFolder != null) {
            deleteFolder(targetFolder.id)
        }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().deleteFlashcardsByFolderName(folderName)
            cardsToDelete.forEach { card ->
                deleteR2AssetIfPresent(card.questionImageUrl)
                deleteR2AssetIfPresent(card.answerImageUrl)
            }
            triggerCatalogPublish()
        }
    }

    fun deleteFlashcardsByChapter(folderName: String, subfolderName: String, chapterName: String, profName: String = "FIRST_PROF") {
        val cardsToDelete = _flashcards.value.filter {
            it.folderName.equals(folderName, ignoreCase = true) &&
            (subfolderName.isBlank() || it.subfolderName.equals(subfolderName, ignoreCase = true)) &&
            it.chapterName.equals(chapterName, ignoreCase = true)
        }
        cardsToDelete.forEach { recordDeletedFlashcard(it.id) }
        _flashcards.update {
            it.filterNot { f ->
                f.folderName.equals(folderName, ignoreCase = true) &&
                (subfolderName.isBlank() || f.subfolderName.equals(subfolderName, ignoreCase = true)) &&
                f.chapterName.equals(chapterName, ignoreCase = true)
            }
        }
        val db = database ?: return
        repositoryScope.launch {
            cardsToDelete.forEach {
                db.flashcardDao().deleteFlashcardById(it.id)
                deleteR2AssetIfPresent(it.questionImageUrl)
                deleteR2AssetIfPresent(it.answerImageUrl)
            }
            CloudSyncEngine.deleteCloudFlashcardChapter(profName, folderName, subfolderName, chapterName)
            triggerCatalogPublish()
        }
    }

    fun renameFlashcardFolder(oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank()) return
        _flashcards.update { list ->
            list.map {
                if (it.folderName.equals(oldName, ignoreCase = true)) it.copy(folderName = newName.trim())
                else it
            }
        }
        val matchingFolder = _folders.value.firstOrNull { it.name.equals(oldName, ignoreCase = true) }
        if (matchingFolder != null) {
            renameFolder(matchingFolder.id, newName.trim(), matchingFolder.emoji)
        }
        val db = database ?: return
        repositoryScope.launch {
            val updated = _flashcards.value.filter { it.folderName.equals(newName.trim(), ignoreCase = true) }
            db.flashcardDao().insertFlashcards(updated.map { FlashcardEntity.fromDomain(it) })
            triggerCatalogPublish()
        }
    }

    fun renameFlashcardChapter(folderName: String, subfolderName: String, oldChapter: String, newChapter: String) {
        if (oldChapter.isBlank() || newChapter.isBlank()) return
        _flashcards.update { list ->
            list.map {
                if (it.folderName.equals(folderName, ignoreCase = true) &&
                    (subfolderName.isBlank() || it.subfolderName.equals(subfolderName, ignoreCase = true)) &&
                    it.chapterName.equals(oldChapter, ignoreCase = true)
                ) {
                    it.copy(chapterName = newChapter.trim())
                } else it
            }
        }
        val db = database ?: return
        repositoryScope.launch {
            val updated = _flashcards.value.filter {
                it.folderName.equals(folderName, ignoreCase = true) &&
                it.chapterName.equals(newChapter.trim(), ignoreCase = true)
            }
            db.flashcardDao().insertFlashcards(updated.map { FlashcardEntity.fromDomain(it) })
            triggerCatalogPublish()
        }
    }

    fun deleteAllFlashcards() {
        _flashcards.update { emptyList() }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().clearAllFlashcards()
            triggerCatalogPublish()
        }
    }

    fun buildCloudflareShortUrl(shortId: String, videoId: String): String {
        return "$CLOUDFLARE_R2_BASE_URL/shorts/${shortId}_${videoId}.mp4"
    }

    fun toggleFlashcardMastered(id: String) {
        val currentList = _flashcards.value
        val target = currentList.firstOrNull { it.id == id } ?: return
        val updated = !target.isMastered
        _flashcards.update { list ->
            list.map { if (it.id == id) it.copy(isMastered = updated) else it }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().updateMastery(id, updated)
        }
    }

    fun toggleFlashcardSavedOffline(id: String) {
        val currentList = _flashcards.value
        val target = currentList.firstOrNull { it.id == id } ?: return
        val updated = !target.isSavedOffline
        _flashcards.update { list ->
            list.map { if (it.id == id) it.copy(isSavedOffline = updated) else it }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().updateSavedOffline(id, updated)
        }
    }

    fun toggleDeckSavedOffline(folderName: String) {
        val currentList = _flashcards.value
        val deckCards = currentList.filter { it.folderName.equals(folderName, ignoreCase = true) }
        if (deckCards.isEmpty()) return
        val allSaved = deckCards.all { it.isSavedOffline }
        val newSavedState = !allSaved
        _flashcards.update { list ->
            list.map {
                if (it.folderName.equals(folderName, ignoreCase = true)) it.copy(isSavedOffline = newSavedState)
                else it
            }
        }
        val db = database ?: return
        repositoryScope.launch {
            db.flashcardDao().updateDeckSavedOffline(folderName, newSavedState)
        }
    }

    fun isDeckSavedOffline(folderName: String): Boolean {
        val deckCards = _flashcards.value.filter { it.folderName.equals(folderName, ignoreCase = true) }
        return deckCards.isNotEmpty() && deckCards.any { it.isSavedOffline }
    }

    fun isFlashcardSavedOffline(id: String): Boolean {
        return _flashcards.value.firstOrNull { it.id == id }?.isSavedOffline ?: false
    }


    fun deleteItem(type: String, id: String) {
        val db = database
        when (type) {
            "flashcard" -> {
                recordDeletedFlashcard(id)
                _flashcards.update { it.filterNot { item -> item.id == id } }
                db?.let { repositoryScope.launch { it.flashcardDao().deleteFlashcardById(id); triggerCatalogPublish() } }
            }
            "lecture" -> {
                recordDeletedContent(id)
                _lectures.update { it.filterNot { item -> item.id == id } }
                db?.let { repositoryScope.launch { it.lectureDao().deleteLectureById(id); triggerCatalogPublish() } }
            }
            "note" -> {
                recordDeletedContent(id)
                _notes.update { it.filterNot { item -> item.id == id } }
                db?.let { repositoryScope.launch { it.noteDao().deleteNoteById(id); triggerCatalogPublish() } }
            }
            "pyq" -> {
                recordDeletedPYQ(id)
                _pyqs.update { it.filterNot { item -> item.id == id } }
                db?.let { repositoryScope.launch { it.pyqDao().deletePYQById(id); triggerCatalogPublish() } }
            }
            "short" -> {
                recordDeletedShort(id)
                _shorts.update { it.filterNot { item -> item.id == id } }
                db?.let { repositoryScope.launch { it.shortDao().deleteShortById(id); triggerCatalogPublish() } }
            }
        }
    }

    fun getSubjectsForProf(prof: ProfYear): List<Subject> {
        return allSubjects.filter { it.prof == prof }
    }

    fun addCustomSection(section: CustomSection) {
        _customSections.update { current ->
            (current.filterNot { it.id == section.id } + section).sortedBy { it.orderIndex }
        }
        triggerCatalogPublish()
    }

    fun updateCustomSection(section: CustomSection) {
        addCustomSection(section)
    }

    fun deleteStudyContent(id: String) {
        recordDeletedContent(id)
        val item = _contents.value.firstOrNull { it.id == id }
        if (item != null) {
            deleteStudyContent(item)
        } else {
            _contents.update { it.filterNot { c -> c.id == id } }
            val db = database ?: return
            repositoryScope.launch {
                db.studyContentDao().deleteContentById(id)
                triggerCatalogPublish()
            }
        }
    }

    fun deleteCustomSection(sectionId: String) {
        _customSections.update { it.filterNot { s -> s.id == sectionId } }
        // Also remove any folders / contents associated with this sectionId
        _folders.update { it.filterNot { f -> f.subjectId == sectionId } }
        _contents.update { it.filterNot { c -> c.subjectId == sectionId } }
        val db = database ?: return
        repositoryScope.launch {
            db.folderDao().deleteFoldersBySubjectId(sectionId)
            db.studyContentDao().deleteContentsBySubjectId(sectionId)
            triggerCatalogPublish()
        }
    }

    fun updateCustomSections(sections: List<CustomSection>) {
        _customSections.value = sections
    }

    fun deleteAllCustomSections() {
        val currentSections = _customSections.value
        val sectionIds = currentSections.map { it.id }.toSet()
        _customSections.value = emptyList()
        _folders.update { it.filterNot { f -> sectionIds.contains(f.subjectId) } }
        _contents.update { it.filterNot { c -> sectionIds.contains(c.subjectId) } }
        val db = database ?: return
        repositoryScope.launch {
            sectionIds.forEach { sId ->
                db.folderDao().deleteFoldersBySubjectId(sId)
                db.studyContentDao().deleteContentsBySubjectId(sId)
            }
            triggerCatalogPublish()
        }
    }

    fun deleteAllPYQs() {
        val pyqContents = _contents.value.filter { it.isPyq || it.subjectId.equals("pyq", ignoreCase = true) }
        val pyqIds = pyqContents.map { it.id }.toSet()
        _pyqs.value = emptyList()
        _contents.update { it.filterNot { c -> c.isPyq || c.subjectId.equals("pyq", ignoreCase = true) } }
        _folders.update { it.filterNot { f -> f.subjectId.equals("pyq", ignoreCase = true) || f.sectionType.equals("PYQ", ignoreCase = true) || f.name.contains("PYQ", ignoreCase = true) } }
        val db = database ?: return
        repositoryScope.launch {
            db.pyqDao().clearAllPYQs()
            pyqIds.forEach { db.studyContentDao().deleteContentById(it) }
            db.studyContentDao().deleteContentsBySubjectId("pyq")
            db.folderDao().deleteFoldersBySubjectId("pyq")
            triggerCatalogPublish()
        }
    }

    fun purgeAdminExcludedData() {
        deleteAllCustomSections()
        deleteAllPYQs()
        deleteAllFlashcards()
        deleteAllMCQs()
        _mcqSections.value = emptyList()
        _bookmarkedMCQs.value = emptyList()
        _quizHistory.value = emptyList()
        sharedPrefs?.edit()?.remove("mcq_bookmarks_json")?.apply()
        sharedPrefs?.edit()?.remove("mcq_quiz_history_json")?.apply()
        val db = database ?: return
        repositoryScope.launch {
            db.mcqSectionDao().clearAllSections()
            db.flashcardDao().clearAllFlashcards()
            db.pyqDao().clearAllPYQs()
            db.mcqDao().clearAllMCQs()
            db.folderDao().deleteFoldersBySubjectId("flashcard")
            db.folderDao().deleteFoldersBySubjectId("mcq")
            db.folderDao().deleteFoldersBySubjectId("pyq")
            triggerCatalogPublish()
        }
    }
}
