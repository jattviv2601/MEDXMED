package com.example.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.data.local.MedXMedDatabase
import com.example.data.local.PomodoroSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class PomodoroMode(val title: String, val minutes: Int, val color: Color, val emoji: String) {
    STUDY_25("25m Focus", 25, Color(0xFF2563EB), "🎯"),
    DEEP_FOCUS_50("50m Deep Study", 50, Color(0xFF7C3AED), "🧠"),
    RAPID_15("15m High-Yield", 15, Color(0xFFEA580C), "⚡"),
    SHORT_BREAK_5("5m Break", 5, Color(0xFF10B981), "☕"),
    LONG_BREAK_15("15m Rest", 15, Color(0xFF059669), "🌿")
}

object PomodoroManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var database: MedXMedDatabase? = null
    private var timerJob: Job? = null
    private var appContext: Context? = null
    private var activeRingtone: android.media.Ringtone? = null

    private val _currentMode = MutableStateFlow(PomodoroMode.STUDY_25)
    val currentMode: StateFlow<PomodoroMode> = _currentMode.asStateFlow()

    private val _timeLeftSeconds = MutableStateFlow(25 * 60)
    val timeLeftSeconds: StateFlow<Int> = _timeLeftSeconds.asStateFlow()

    private val _totalDurationSeconds = MutableStateFlow(25 * 60)
    val totalDurationSeconds: StateFlow<Int> = _totalDurationSeconds.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _sessionTopic = MutableStateFlow("MBBS High-Yield Focus")
    val sessionTopic: StateFlow<String> = _sessionTopic.asStateFlow()

    private val _sessions = MutableStateFlow<List<PomodoroSessionEntity>>(emptyList())
    val sessions: StateFlow<List<PomodoroSessionEntity>> = _sessions.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (database != null) return
        val db = MedXMedDatabase.getDatabase(context)
        database = db

        scope.launch {
            db.pomodoroSessionDao().getAllSessions().collect { list ->
                _sessions.value = list
            }
        }
    }

    fun setTopic(topic: String) {
        _sessionTopic.value = topic.ifBlank { "MBBS High-Yield Focus" }
    }

    fun setMode(mode: PomodoroMode) {
        pauseTimer(autoSave = false)
        _currentMode.value = mode
        _totalDurationSeconds.value = mode.minutes * 60
        _timeLeftSeconds.value = mode.minutes * 60
    }

    private var lastToggleTimestamp = 0L

    fun toggleTimer() {
        val now = System.currentTimeMillis()
        if (now - lastToggleTimestamp < 300L) return // Debounce rapid taps
        lastToggleTimestamp = now

        if (_isRunning.value) {
            pauseTimer(autoSave = true)
        } else {
            startTimer()
        }
    }

    fun startTimer() {
        if (_timeLeftSeconds.value <= 0) {
            _timeLeftSeconds.value = _totalDurationSeconds.value
        }
        _isRunning.value = true
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Main.immediate) {
            while (_isRunning.value && _timeLeftSeconds.value > 0) {
                delay(1000L)
                if (_isRunning.value && _timeLeftSeconds.value > 0) {
                    val next = _timeLeftSeconds.value - 1
                    _timeLeftSeconds.value = next.coerceAtLeast(0)
                    if (next <= 0) {
                        onTimerCompleted()
                        break
                    }
                }
            }
        }
    }

    fun pauseTimer(autoSave: Boolean = true) {
        if (!_isRunning.value) return
        _isRunning.value = false
        timerJob?.cancel()
        timerJob = null

        if (autoSave) {
            val studiedSeconds = _totalDurationSeconds.value - _timeLeftSeconds.value
            if (studiedSeconds >= 10) { // If user studied for at least 10 seconds, auto-save!
                val durationMin = (studiedSeconds / 60).coerceAtLeast(1)
                saveSessionRecord(durationMin)
            }
        }
    }

    fun resetTimer() {
        val studiedSeconds = _totalDurationSeconds.value - _timeLeftSeconds.value
        if (_isRunning.value && studiedSeconds >= 10) {
            saveSessionRecord((studiedSeconds / 60).coerceAtLeast(1))
        }
        _isRunning.value = false
        timerJob?.cancel()
        timerJob = null
        _timeLeftSeconds.value = _totalDurationSeconds.value
    }

    private fun onTimerCompleted() {
        _isRunning.value = false
        timerJob?.cancel()
        timerJob = null
        val fullDurationMin = (_totalDurationSeconds.value / 60).coerceAtLeast(1)
        saveSessionRecord(fullDurationMin)
        _timeLeftSeconds.value = _totalDurationSeconds.value
        triggerAlarmAndVibration()
    }

    fun triggerAlarmAndVibration() {
        val ctx = appContext ?: return
        try {
            // Vibration
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 600, 200, 600, 200, 800), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 600, 200, 600, 200, 800), -1)
            }

            // Alarm / Notification sound
            val alertUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)

            activeRingtone?.stop()
            activeRingtone = android.media.RingtoneManager.getRingtone(ctx, alertUri)
            activeRingtone?.play()

            // Auto stop after 6 seconds so it behaves like a crisp timer alarm
            scope.launch {
                delay(6000L)
                stopAlarm()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlarm() {
        try {
            if (activeRingtone?.isPlaying == true) {
                activeRingtone?.stop()
            }
            activeRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSessionRecord(durationMinutes: Int) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date())

        val session = PomodoroSessionEntity(
            id = "pomo_${UUID.randomUUID().toString().take(8)}",
            title = _sessionTopic.value,
            durationMinutes = durationMinutes,
            completedAtTimestamp = System.currentTimeMillis(),
            formattedDate = formattedDate,
            modeName = _currentMode.value.title
        )

        val db = database ?: return
        scope.launch(Dispatchers.IO) {
            db.pomodoroSessionDao().insertSession(session)
        }
    }

    fun deleteSession(id: String) {
        val db = database ?: return
        scope.launch(Dispatchers.IO) {
            db.pomodoroSessionDao().deleteSessionById(id)
        }
    }

    fun clearAllSessions() {
        val db = database ?: return
        scope.launch(Dispatchers.IO) {
            db.pomodoroSessionDao().clearAllSessions()
        }
    }
}
