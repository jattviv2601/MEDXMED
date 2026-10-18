package com.example.data.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CloudflareR2Manager {

    const val DEFAULT_ACCOUNT_ID = "47c74c37316a8f37a67ff5adf59d7686"
    const val DEFAULT_BUCKET_NAME = "medxrattamaar"
    const val DEFAULT_PUBLIC_BASE_URL = "https://pub-a0c1c1a64a7f45b6b844455c74ed370d.r2.dev"
    const val DEFAULT_ACCESS_KEY = "36fa991cdc915a3222459da45435dcf6"
    const val DEFAULT_SECRET_KEY = "76da39de3deb929ef93a394b1ffc6c73ed815ff35e09c4b4d776483943dfa046"

    // Backward compatibility constants
    const val ACCOUNT_ID = DEFAULT_ACCOUNT_ID
    const val BUCKET_NAME = DEFAULT_BUCKET_NAME
    const val PUBLIC_BASE_URL = DEFAULT_PUBLIC_BASE_URL

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var sharedPrefs: android.content.SharedPreferences? = null

    private val _accountId = MutableStateFlow(DEFAULT_ACCOUNT_ID)
    val accountId: StateFlow<String> = _accountId.asStateFlow()

    private val _accessKeyId = MutableStateFlow(DEFAULT_ACCESS_KEY)
    val accessKeyId: StateFlow<String> = _accessKeyId.asStateFlow()

    private val _secretAccessKey = MutableStateFlow(DEFAULT_SECRET_KEY)
    val secretAccessKey: StateFlow<String> = _secretAccessKey.asStateFlow()

    private val _bucketName = MutableStateFlow(DEFAULT_BUCKET_NAME)
    val bucketName: StateFlow<String> = _bucketName.asStateFlow()

    private val _publicBaseUrl = MutableStateFlow(DEFAULT_PUBLIC_BASE_URL)
    val publicBaseUrl: StateFlow<String> = _publicBaseUrl.asStateFlow()

    val isUploading = MutableStateFlow(false)
    val uploadProgress = MutableStateFlow(0f)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("medxmed_r2_prefs", Context.MODE_PRIVATE)
        sharedPrefs = prefs
        _accountId.value = prefs.getString("r2_account_id", DEFAULT_ACCOUNT_ID)?.trim() ?: DEFAULT_ACCOUNT_ID
        _accessKeyId.value = prefs.getString("r2_access_key_id", DEFAULT_ACCESS_KEY)?.trim() ?: DEFAULT_ACCESS_KEY
        _secretAccessKey.value = prefs.getString("r2_secret_access_key", DEFAULT_SECRET_KEY)?.trim() ?: DEFAULT_SECRET_KEY
        _bucketName.value = prefs.getString("r2_bucket_name", DEFAULT_BUCKET_NAME)?.trim() ?: DEFAULT_BUCKET_NAME
        _publicBaseUrl.value = prefs.getString("r2_public_base_url", DEFAULT_PUBLIC_BASE_URL)?.trim() ?: DEFAULT_PUBLIC_BASE_URL
    }

    fun isConfigured(): Boolean {
        return _accountId.value.isNotBlank() &&
               _accessKeyId.value.isNotBlank() &&
               _secretAccessKey.value.isNotBlank() &&
               _bucketName.value.isNotBlank()
    }

    fun saveCredentials(
        accountId: String,
        accessKeyId: String,
        secretAccessKey: String,
        bucketName: String = _bucketName.value,
        publicBaseUrl: String = _publicBaseUrl.value
    ) {
        _accountId.value = accountId.trim()
        _accessKeyId.value = accessKeyId.trim()
        _secretAccessKey.value = secretAccessKey.trim()
        _bucketName.value = bucketName.trim().ifBlank { DEFAULT_BUCKET_NAME }
        _publicBaseUrl.value = publicBaseUrl.trim().ifBlank { DEFAULT_PUBLIC_BASE_URL }.trimEnd('/')

        sharedPrefs?.edit()
            ?.putString("r2_account_id", _accountId.value)
            ?.putString("r2_access_key_id", _accessKeyId.value)
            ?.putString("r2_secret_access_key", _secretAccessKey.value)
            ?.putString("r2_bucket_name", _bucketName.value)
            ?.putString("r2_public_base_url", _publicBaseUrl.value)
            ?.apply()
    }

    /**
     * Tests connectivity and permissions against Cloudflare R2 bucket.
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val accId = _accountId.value.trim()
        val accKey = _accessKeyId.value.trim()
        val secKey = _secretAccessKey.value.trim()
        val bName = _bucketName.value.trim()

        if (accId.isBlank() || accKey.isBlank() || secKey.isBlank() || bName.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Account ID, Access Key, Secret Key, and Bucket must not be empty."))
        }

        try {
            val pingKey = ".r2_health_ping.txt"
            val pingContent = "ping-${System.currentTimeMillis()}"
            val uploadRes = uploadString(pingKey, pingContent, "text/plain")
            if (uploadRes.isSuccess) {
                // Delete test ping
                deleteFile(pingKey)
                Result.success("Connected to bucket '$bName' successfully!")
            } else {
                val err = uploadRes.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                Result.failure(Exception("Cloudflare R2 Test Failed: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads bytes or file stream directly to Cloudflare R2 bucket using AWS S3 SigV4.
     * Returns the public CDN URL on success (e.g. https://pub-...r2.dev/path/file.pdf).
     */
    suspend fun uploadFile(
        context: Context,
        fileUri: Uri,
        objectKey: String,
        mimeType: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        isUploading.value = true
        uploadProgress.value = 0f
        return try {
            uploadFileInternal(context, fileUri, objectKey, mimeType) { p ->
                uploadProgress.value = p
                onProgress(p)
            }
        } finally {
            isUploading.value = false
        }
    }

    /**
     * Compresses the image (downsampling & JPEG 80% compression) before uploading to Cloudflare R2.
     * Prevents oversized uploads, reduces bandwidth, and ensures fast mobile loading.
     */
    suspend fun uploadCompressedImage(
        context: Context,
        imageUri: Uri,
        objectKey: String,
        maxDimension: Int = 1280,
        quality: Int = 80,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        isUploading.value = true
        uploadProgress.value = 0f
        val tempCompressedFile = File(context.cacheDir, "comp_img_${System.currentTimeMillis()}.jpg")
        try {
            // Step 1: Decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            var inSampleSize = 1
            val outW = options.outWidth
            val outH = options.outHeight
            if (outW > maxDimension || outH > maxDimension) {
                val halfW = outW / 2
                val halfH = outH / 2
                while ((halfW / inSampleSize) >= maxDimension || (halfH / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // Step 2: Decode bitmap with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Could not decode selected image"))

            // Step 3: Compress to temp file
            tempCompressedFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            bitmap.recycle()

            val tempUri = Uri.fromFile(tempCompressedFile)
            val uploadRes = uploadFileInternal(context, tempUri, objectKey, "image/jpeg") { p ->
                uploadProgress.value = p
                onProgress(p)
            }
            uploadRes
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempCompressedFile.exists()) {
                tempCompressedFile.delete()
            }
            isUploading.value = false
        }
    }


    private suspend fun uploadFileInternal(
        context: Context,
        fileUri: Uri,
        objectKey: String,
        mimeType: String,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val accId = _accountId.value.trim()
        val accKey = _accessKeyId.value.trim()
        val secKey = _secretAccessKey.value.trim()
        val bName = _bucketName.value.trim()
        val pubBase = _publicBaseUrl.value.trim().trimEnd('/')

        if (accId.isBlank() || accKey.isBlank() || secKey.isBlank() || bName.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Cloudflare R2 credentials not fully configured. Please set them in Admin Settings.")
            )
        }

        // Cache the Uri to a local temporary file to guarantee exact Content-Length.
        // Cloudflare R2 and AWS S3 PUT reject chunked uploads without Content-Length.
        val tempFile = File(context.cacheDir, "r2_upload_${System.currentTimeMillis()}.tmp")
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(fileUri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream for selected file"))

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val exactFileSize = tempFile.length()
            if (exactFileSize <= 0) {
                return@withContext Result.failure(IllegalStateException("Selected file is empty (0 bytes)."))
            }

            val cleanKey = objectKey.trimStart('/')
            val endpointHost = "$accId.r2.cloudflarestorage.com"
            val encodedKey = uriEncode(cleanKey)
            val requestPath = "/$bName/$encodedKey"
            val endpointUrl = "https://$endpointHost$requestPath"

            // AWS SigV4 Date Formats
            val now = Date()
            val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(now)
            val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(now)

            // Headers to Sign (Must be sorted)
            val headersToSign = sortedMapOf(
                "host" to endpointHost,
                "x-amz-content-sha256" to "UNSIGNED-PAYLOAD",
                "x-amz-date" to amzDate
            )

            // Canonical Request
            val canonicalHeaders = headersToSign.entries.joinToString("") { "${it.key}:${it.value}\n" }
            val signedHeaders = headersToSign.keys.joinToString(";")
            val canonicalRequest = "PUT\n$requestPath\n\n$canonicalHeaders\n$signedHeaders\nUNSIGNED-PAYLOAD"

            // String to Sign
            val algorithm = "AWS4-HMAC-SHA256"
            val credentialScope = "$dateStamp/auto/s3/aws4_request"
            val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

            // Signing Key
            val signingKey = getSignatureKey(secKey, dateStamp, "auto", "s3")
            val signature = hmacSha256Hex(signingKey, stringToSign)

            val authHeader = "$algorithm Credential=$accKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            // Custom RequestBody with known exact Content-Length and real-time streaming progress
            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()

                override fun contentLength(): Long = exactFileSize

                override fun writeTo(sink: BufferedSink) {
                    tempFile.inputStream().use { input ->
                        input.source().use { source ->
                            val buffer = okio.Buffer()
                            var totalBytesRead = 0L
                            var readCount: Long
                            // 64KB buffer for smooth progress updates
                            while (source.read(buffer, 65536L).also { readCount = it } != -1L) {
                                sink.write(buffer, readCount)
                                totalBytesRead += readCount
                                val progress = (totalBytesRead.toFloat() / exactFileSize).coerceIn(0f, 1f)
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(endpointUrl)
                .put(requestBody)
                .header("Host", endpointHost)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", "UNSIGNED-PAYLOAD")
                .header("Authorization", authHeader)
                .header("Content-Type", mimeType)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val publicUrl = "$pubBase/$cleanKey"
                onProgress(1f)
                Result.success(publicUrl)
            } else {
                val errorMsg = response.body?.string().orEmpty()
                val friendlyError = when (response.code) {
                    403 -> "HTTP 403 Forbidden: Invalid Cloudflare Access Key or Secret Key."
                    404 -> "HTTP 404 Not Found: Bucket '$bName' does not exist in account."
                    else -> "Cloudflare R2 returned HTTP ${response.code}: $errorMsg"
                }
                Result.failure(Exception(friendlyError))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Uploads a raw text/JSON string directly to Cloudflare R2 bucket (used for catalog.json sync).
     */
    suspend fun uploadString(
        objectKey: String,
        content: String,
        mimeType: String = "application/json"
    ): Result<String> = withContext(Dispatchers.IO) {
        val accId = _accountId.value.trim()
        val accKey = _accessKeyId.value.trim()
        val secKey = _secretAccessKey.value.trim()
        val bName = _bucketName.value.trim()
        val pubBase = _publicBaseUrl.value.trim().trimEnd('/')

        if (accId.isBlank() || accKey.isBlank() || secKey.isBlank() || bName.isBlank()) {
            return@withContext Result.failure(IllegalStateException("R2 credentials not fully configured"))
        }

        try {
            val cleanKey = objectKey.trimStart('/')
            val endpointHost = "$accId.r2.cloudflarestorage.com"
            val encodedKey = uriEncode(cleanKey)
            val requestPath = "/$bName/$encodedKey"
            val endpointUrl = "https://$endpointHost$requestPath"

            val now = Date()
            val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(now)
            val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(now)

            val payloadHash = sha256Hex(content)

            val headersToSign = sortedMapOf(
                "host" to endpointHost,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate
            )

            val canonicalHeaders = headersToSign.entries.joinToString("") { "${it.key}:${it.value}\n" }
            val signedHeaders = headersToSign.keys.joinToString(";")
            val canonicalRequest = "PUT\n$requestPath\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

            val algorithm = "AWS4-HMAC-SHA256"
            val credentialScope = "$dateStamp/auto/s3/aws4_request"
            val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

            val signingKey = getSignatureKey(secKey, dateStamp, "auto", "s3")
            val signature = hmacSha256Hex(signingKey, stringToSign)

            val authHeader = "$algorithm Credential=$accKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            val body = RequestBody.create(mimeType.toMediaTypeOrNull(), content)
            val request = Request.Builder()
                .url(endpointUrl)
                .put(body)
                .header("Host", endpointHost)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader)
                .header("Content-Type", mimeType)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("$pubBase/$cleanKey")
            } else {
                val errorMsg = response.body?.string().orEmpty()
                Result.failure(Exception("R2 HTTP ${response.code}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an object from the Cloudflare R2 bucket.
     */
    suspend fun deleteFile(objectKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val accId = _accountId.value.trim()
        val accKey = _accessKeyId.value.trim()
        val secKey = _secretAccessKey.value.trim()
        val bName = _bucketName.value.trim()

        if (accId.isBlank() || accKey.isBlank() || secKey.isBlank() || bName.isBlank()) {
            return@withContext Result.failure(IllegalStateException("R2 credentials not set"))
        }

        try {
            val cleanKey = objectKey.trimStart('/')
            val endpointHost = "$accId.r2.cloudflarestorage.com"
            val encodedKey = uriEncode(cleanKey)
            val requestPath = "/$bName/$encodedKey"
            val endpointUrl = "https://$endpointHost$requestPath"

            val now = Date()
            val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(now)
            val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(now)

            val payloadHash = sha256Hex("")

            val headersToSign = sortedMapOf(
                "host" to endpointHost,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate
            )

            val canonicalHeaders = headersToSign.entries.joinToString("") { "${it.key}:${it.value}\n" }
            val signedHeaders = headersToSign.keys.joinToString(";")
            val canonicalRequest = "DELETE\n$requestPath\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

            val algorithm = "AWS4-HMAC-SHA256"
            val credentialScope = "$dateStamp/auto/s3/aws4_request"
            val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

            val signingKey = getSignatureKey(secKey, dateStamp, "auto", "s3")
            val signature = hmacSha256Hex(signingKey, stringToSign)

            val authHeader = "$algorithm Credential=$accKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            val request = Request.Builder()
                .url(endpointUrl)
                .delete()
                .header("Host", endpointHost)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("R2 DELETE HTTP ${response.code}: ${response.body?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Percent-encodes each path segment for RFC 3986 compliance in AWS SigV4.
     */
    private fun uriEncode(path: String): String {
        return path.split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) ""
            else URLEncoder.encode(segment, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~")
        }
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }
}
