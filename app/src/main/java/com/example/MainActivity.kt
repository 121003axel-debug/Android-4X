package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// Holo Blue Glow Tap Effect modifier for custom vintage UI feel
@Composable
fun Modifier.holoTap(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(2.dp)
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.5f else 0.0f,
        animationSpec = if (isPressed) tween(60) else tween(220)
    )
    
    return this
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
        .drawBehind {
            if (glowAlpha > 0f) {
                drawRect(
                    color = Color(0xFF33B5E5).copy(alpha = glowAlpha * 0.4f)
                )
                drawRect(
                    color = Color(0xFF33B5E5).copy(alpha = glowAlpha * 0.8f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
}

// ==========================================
// RETROFIT & MOSHI SCHEMAS (GEMINI API)
// ==========================================
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class ImageConfig(
    val aspectRatio: String,
    val imageSize: String
)

data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val imageConfig: ImageConfig? = null,
    val responseModalities: List<String>? = null
)

data class GeminiTool(
    val googleSearch: Map<String, Any>? = null,
    val google_search: Map<String, Any>? = null
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    val groundingMetadata: GroundingMetadata? = null
)

data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

data class GroundingChunk(
    val web: WebSource? = null
)

data class WebSource(
    val uri: String? = null,
    val title: String? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)
}

// ==========================================
// SIMULATED APPLICATIONS & STATES
// ==========================================
enum class AppType(val appName: String) {
    PHONE("Teléfono"),
    PEOPLE("Contactos"),
    BROWSER("Navegador"),
    MESSAGING("Mensajes"),
    CAMERA("Cámara"),
    GALLERY("Galería"),
    CALCULATOR("Calculadora"),
    SETTINGS("Ajustes"),
    MAPS("Mapas"),
    MARKET("Play Store"),
    CLOCK("Reloj"),
    MUSIC("Música"),
    CALENDAR("Calendario"),
    ABOUT("About JB"),
    FILE_MANAGER("Archivos"),
    THEMES("Temas"),
    AI_GENERATOR("Creador IA"),
    SOUND_RECORDER("Grabadora"),
    DOWNLOADS("Descargas"),
    NOTES("Notas"),
    RADIO("Radio FM")
}

data class ChatMessage(
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
)

data class AppNotification(
    val id: Int,
    val sender: String,
    val message: String,
    val appType: AppType
)

data class DownloadItem(
    val name: String,
    val size: String,
    val status: String,
    val progress: Int
)

data class JellyBean(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    val labelName: String
)

data class NoteItem(
    val id: String,
    val title: String,
    val content: String,
    val date: String
)

fun serializeNotes(notes: List<NoteItem>): String {
    return notes.joinToString("::[NOTE_SEP]::") { 
        "${it.id}::[PART]::${it.title}::[PART]::${it.content}::[PART]::${it.date}" 
    }
}

fun deserializeNotes(serialized: String): List<NoteItem> {
    if (serialized.isEmpty()) return emptyList()
    val list = mutableListOf<NoteItem>()
    val notesRaw = serialized.split("::[NOTE_SEP]::")
    for (raw in notesRaw) {
        val parts = raw.split("::[PART]::")
        if (parts.size >= 4) {
            list.add(NoteItem(parts[0], parts[1], parts[2], parts[3]))
        }
    }
    return list
}

// ==========================================
// VIEWMODEL FOR JELLY BEAN SIMULATION
// ==========================================
class JellyBeanViewModel(application: Application) : AndroidViewModel(application) {
    private val _currentTime = MutableStateFlow("12:00")
    val currentTime = _currentTime.asStateFlow()

    private val _currentDate = MutableStateFlow("Lunes, 22 de Junio")
    val currentDate = _currentDate.asStateFlow()

    private val _isLocked = MutableStateFlow(true)
    val isLocked = _isLocked.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen = _isDrawerOpen.asStateFlow()

    private val _isNotificationShadeOpen = MutableStateFlow(false)
    val isNotificationShadeOpen = _isNotificationShadeOpen.asStateFlow()

    private val _activeApp = MutableStateFlow<AppType?>(null)
    val activeApp = _activeApp.asStateFlow()

    private val _recentAppsList = MutableStateFlow<List<AppType>>(emptyList())
    val recentAppsList = _recentAppsList.asStateFlow()

    private val _showRecentAppsOverlay = MutableStateFlow(false)
    val showRecentAppsOverlay = _showRecentAppsOverlay.asStateFlow()

    // Wi-Fi and Signals States
    private val _isWifiEnabled = MutableStateFlow(
        application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE).getBoolean("wifi_enabled", true)
    )
    val isWifiEnabled = _isWifiEnabled.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(
        application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE).getBoolean("bluetooth_enabled", true)
    )
    val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(
        application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE).getBoolean("location_enabled", true)
    )
    val isLocationEnabled = _isLocationEnabled.asStateFlow()

    private val _isDataEnabled = MutableStateFlow(
        application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE).getBoolean("data_enabled", true)
    )
    val isDataEnabled = _isDataEnabled.asStateFlow()

    // Wallpaper Selection (Local and Generated)
    private val _wallpaperSelected = MutableStateFlow<Bitmap?>(null)
    val wallpaperSelected = _wallpaperSelected.asStateFlow()

    private val _themeColorProfile = MutableStateFlow(
        application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE).getString("theme_color", "BLUE") ?: "BLUE"
    )
    val themeColorProfile = _themeColorProfile.asStateFlow()

    fun setThemeColorProfile(profile: String) {
        _themeColorProfile.value = profile
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putString("theme_color", profile).apply()
        updateHashFromState()
    }

    private val _wallpaperPreset = MutableStateFlow(
        application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE).getString("wallpaper_preset", "WAVES") ?: "WAVES"
    )
    val wallpaperPreset = _wallpaperPreset.asStateFlow()

    fun setWallpaperPreset(preset: String) {
        _wallpaperSelected.value = null
        _wallpaperPreset.value = preset
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putString("wallpaper_preset", preset).apply()
        updateHashFromState()
    }

    // Google Folder Popup
    private val _isGoogleFolderOpen = MutableStateFlow(false)
    val isGoogleFolderOpen = _isGoogleFolderOpen.asStateFlow()

    // Quick Settings States (Android 4.1)
    private val _isQuickSettingsActive = MutableStateFlow(false)
    val isQuickSettingsActive = _isQuickSettingsActive.asStateFlow()

    private val _systemBrightness = MutableStateFlow(0.7f)
    val systemBrightness = _systemBrightness.asStateFlow()

    private val _soundProfile = MutableStateFlow("SOUND") // SOUND, VIBRATE, MUTE
    val soundProfile = _soundProfile.asStateFlow()

    private val _isAirplaneModeEnabled = MutableStateFlow(false)
    val isAirplaneModeEnabled = _isAirplaneModeEnabled.asStateFlow()

    private val _ownerName = MutableStateFlow(
        application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE).getString("owner_name", "Administrador Jelly Bean") ?: "Administrador Jelly Bean"
    )
    val ownerName = _ownerName.asStateFlow()

    // NOTES STATE & PERSISTENCE
    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes = _notes.asStateFlow()

    private fun saveNotesToPrefs(list: List<NoteItem>) {
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putString("notes_list", serializeNotes(list)).apply()
    }

    fun defaultNotes() = listOf(
        NoteItem("1", "Bienvenido a Jelly Bean", "Esta es tu nueva aplicación de notas con estilo Holo retro de Android 4.1. ¡Disfrútala!", "25 Jun 2026"),
        NoteItem("2", "Lista de tareas", "- Probar el navegador con páginas reales\n- Ajustar el reloj analógico en el escritorio\n- Probar la cámara con IA", "25 Jun 2026")
    )

    fun addNote(title: String, content: String) {
        val nextId = java.util.UUID.randomUUID().toString()
        val sdf = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es", "ES"))
        val dateStr = sdf.format(java.util.Date())
        val newNote = NoteItem(nextId, title, content, dateStr)
        _notes.value = _notes.value + newNote
        saveNotesToPrefs(_notes.value)
        postNotification("Notas", "Nota guardada: $title", AppType.NOTES)
    }

    fun updateNote(id: String, title: String, content: String) {
        val sdf = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es", "ES"))
        val dateStr = sdf.format(java.util.Date())
        _notes.value = _notes.value.map {
            if (it.id == id) it.copy(title = title, content = content, date = dateStr) else it
        }
        saveNotesToPrefs(_notes.value)
    }

    fun deleteNote(id: String) {
        val note = _notes.value.firstOrNull { it.id == id }
        _notes.value = _notes.value.filter { it.id != id }
        saveNotesToPrefs(_notes.value)
        if (note != null) {
            postNotification("Notas", "Nota eliminada: ${note.title}", AppType.NOTES)
        }
    }

    // HASH ROUTER NAVIGATION SYSTEM
    private val _navigationHash = MutableStateFlow("#/lockscreen")
    val navigationHash = _navigationHash.asStateFlow()

    fun navigateByHash(hash: String) {
        if (hash.isEmpty()) return
        _navigationHash.value = hash
        when {
            hash == "#/lockscreen" -> {
                _isLocked.value = true
                _isDrawerOpen.value = false
                _activeApp.value = null
            }
            hash == "#/home" -> {
                _isLocked.value = false
                _isDrawerOpen.value = false
                _activeApp.value = null
            }
            hash == "#/drawer" -> {
                _isLocked.value = false
                _isDrawerOpen.value = true
                _activeApp.value = null
            }
            hash.startsWith("#/app/") -> {
                val appName = hash.substringAfter("#/app/")
                val targetApp = AppType.entries.firstOrNull { it.name == appName }
                if (targetApp != null) {
                    _isLocked.value = false
                    _isDrawerOpen.value = false
                    _activeApp.value = targetApp
                }
            }
        }
    }

    fun updateHashFromState() {
        val hash = when {
            _isLocked.value -> "#/lockscreen"
            _activeApp.value != null -> "#/app/${_activeApp.value!!.name}"
            _isDrawerOpen.value -> "#/drawer"
            else -> "#/home"
        }
        _navigationHash.value = hash
    }

    // Sound and Dark Theme Settings
    private val _systemSoundsEnabled = MutableStateFlow(true)
    val systemSoundsEnabled = _systemSoundsEnabled.asStateFlow()

    private val _darkModeEnabled = MutableStateFlow(false)
    val darkModeEnabled = _darkModeEnabled.asStateFlow()

    // Analog Clock Widget Draggable Settings
    private val _isAnalogClockWidgetActive = MutableStateFlow(true)
    val isAnalogClockWidgetActive = _isAnalogClockWidgetActive.asStateFlow()

    private val _analogClockWidgetOffset = MutableStateFlow(androidx.compose.ui.geometry.Offset(0f, 0f))
    val analogClockWidgetOffset = _analogClockWidgetOffset.asStateFlow()

    // Battery Widget and Simulated Battery State
    private val _isBatteryWidgetActive = MutableStateFlow(true) // Start active so they can see it initially! Or false, but true makes it immediately visible
    val isBatteryWidgetActive = _isBatteryWidgetActive.asStateFlow()

    private val _batteryWidgetOffset = MutableStateFlow(androidx.compose.ui.geometry.Offset(100f, 250f))
    val batteryWidgetOffset = _batteryWidgetOffset.asStateFlow()

    private val _batteryLevel = MutableStateFlow(78)
    val batteryLevel = _batteryLevel.asStateFlow()

    private val _isBatteryCharging = MutableStateFlow(false)
    val isBatteryCharging = _isBatteryCharging.asStateFlow()

    // Active App Drawer Tab
    private val _activeAppDrawerTab = MutableStateFlow("APPS")
    val activeAppDrawerTab = _activeAppDrawerTab.asStateFlow()

    // Sound Recorder Simulated States
    private val _recorderState = MutableStateFlow("IDLE") // IDLE, RECORDING, PLAYING
    val recorderState = _recorderState.asStateFlow()

    private val _recorderDuration = MutableStateFlow(0)
    val recorderDuration = _recorderDuration.asStateFlow()

    private val _recorderHasRecording = MutableStateFlow(false)
    val recorderHasRecording = _recorderHasRecording.asStateFlow()

    private val _recorderVisualizerHeights = MutableStateFlow(List(12) { 5f })
    val recorderVisualizerHeights = _recorderVisualizerHeights.asStateFlow()

    // Ringtone and Notification Settings
    private val _selectedRingtone = MutableStateFlow("Retro Holo Chime")
    val selectedRingtone = _selectedRingtone.asStateFlow()

    private val _selectedNotificationSound = MutableStateFlow("Simple Tick")
    val selectedNotificationSound = _selectedNotificationSound.asStateFlow()

    fun selectRingtone(ringtone: String) {
        _selectedRingtone.value = ringtone
        playRingtoneMelody(ringtone)
    }

    fun selectNotificationSound(sound: String) {
        _selectedNotificationSound.value = sound
        playNotificationMelody(sound)
    }

    fun playRingtoneMelody(ringtoneName: String) {
        CoroutineScope(Dispatchers.Default).launch {
            val tg = try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_RING, 85)
            } catch (e: Exception) {
                null
            } ?: return@launch

            try {
                when (ringtoneName) {
                    "Retro Holo Chime" -> {
                        val notes = listOf(
                            android.media.ToneGenerator.TONE_DTMF_1,
                            android.media.ToneGenerator.TONE_DTMF_3,
                            android.media.ToneGenerator.TONE_DTMF_5,
                            android.media.ToneGenerator.TONE_DTMF_7
                        )
                        for (note in notes) {
                            tg.startTone(note, 80)
                            delay(120)
                        }
                    }
                    "Andrómeda" -> {
                        val notes = listOf(
                            android.media.ToneGenerator.TONE_DTMF_9,
                            android.media.ToneGenerator.TONE_DTMF_C,
                            android.media.ToneGenerator.TONE_DTMF_B,
                            android.media.ToneGenerator.TONE_DTMF_D
                        )
                        for (note in notes) {
                            tg.startTone(note, 100)
                            delay(150)
                        }
                    }
                    "Jelly Bean Ring" -> {
                        tg.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE, 600)
                    }
                    "Sinfonía de 8 bits" -> {
                        val notes = listOf(
                            android.media.ToneGenerator.TONE_DTMF_1,
                            android.media.ToneGenerator.TONE_DTMF_5,
                            android.media.ToneGenerator.TONE_DTMF_3,
                            android.media.ToneGenerator.TONE_DTMF_7,
                            android.media.ToneGenerator.TONE_DTMF_5,
                            android.media.ToneGenerator.TONE_DTMF_9
                        )
                        for (note in notes) {
                            tg.startTone(note, 70)
                            delay(90)
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                tg.release()
            }
        }
    }

    fun playNotificationMelody(soundName: String) {
        if (!_systemSoundsEnabled.value) return
        if (_soundProfile.value != "SOUND") return
        RetroSoundSynth.playSound(soundName)
    }

    // Simulated Downloads state
    private val _downloads = MutableStateFlow(listOf(
        DownloadItem("jellybean_wallpapers.zip", "12.4 MB", "Completado", 100),
        DownloadItem("Android_4.1_SDK.exe", "148.2 MB", "Completado", 100),
        DownloadItem("messaging_sound_retro.mp3", "1.2 MB", "Completado", 100),
        DownloadItem("HoloLauncher_v1.0.apk", "4.1 MB", "Completado", 100)
    ))
    val downloads = _downloads.asStateFlow()

    fun clearDownloads() {
        _downloads.value = emptyList()
        playSystemSound("click")
    }

    fun startSimulatedDownload(name: String, sizeStr: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val newItem = DownloadItem(name, sizeStr, "Descargando", 0)
            _downloads.value = _downloads.value + newItem
            
            for (progress in 0..100 step 10) {
                delay(200)
                _downloads.value = _downloads.value.map { item ->
                    if (item.name == name) {
                        item.copy(progress = progress, status = if (progress == 100) "Completado" else "Descargando $progress%")
                    } else item
                }
            }
            
            postNotification(
                "Descargas",
                "Descarga completada: $name",
                AppType.DOWNLOADS
            )
            playNotificationMelody(_selectedNotificationSound.value)
        }
    }

    private var toneGenerator: android.media.ToneGenerator? = null

    fun playSystemSound(soundType: String) {
        if (!_systemSoundsEnabled.value) return
        if (_soundProfile.value != "SOUND") return
        RetroSoundSynth.playSound(soundType)
    }

    fun setSystemSoundsEnabled(enabled: Boolean) {
        _systemSoundsEnabled.value = enabled
        playSystemSound("click")
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        _darkModeEnabled.value = enabled
        playSystemSound("click")
    }

    fun setAnalogClockWidgetActive(active: Boolean) {
        _isAnalogClockWidgetActive.value = active
        playSystemSound("click")
    }

    fun setAnalogClockWidgetOffset(offset: androidx.compose.ui.geometry.Offset) {
        _analogClockWidgetOffset.value = offset
    }

    fun setBatteryWidgetActive(active: Boolean) {
        _isBatteryWidgetActive.value = active
        playSystemSound("click")
    }

    fun setBatteryWidgetOffset(offset: androidx.compose.ui.geometry.Offset) {
        _batteryWidgetOffset.value = offset
    }

    fun setBatteryLevel(level: Int) {
        _batteryLevel.value = level.coerceIn(0, 100)
    }

    fun toggleBatteryCharging() {
        _isBatteryCharging.value = !_isBatteryCharging.value
        playSystemSound("click")
    }

    fun setBatteryCharging(charging: Boolean) {
        _isBatteryCharging.value = charging
    }

    fun setActiveAppDrawerTab(tab: String) {
        _activeAppDrawerTab.value = tab
        playSystemSound("click")
    }

    // Recorder Actions
    private var recorderJob: Job? = null
    fun startRecording() {
        if (_recorderState.value != "IDLE") return
        _recorderState.value = "RECORDING"
        _recorderDuration.value = 0
        _recorderHasRecording.value = false
        playSystemSound("click")
        recorderJob = CoroutineScope(Dispatchers.Main).launch {
            while (_recorderState.value == "RECORDING") {
                delay(1000)
                _recorderDuration.value += 1
                // Animate visualizer
                _recorderVisualizerHeights.value = List(12) { (10..45).random().toFloat() }
            }
        }
    }

    fun stopRecording() {
        if (_recorderState.value == "RECORDING") {
            _recorderState.value = "IDLE"
            _recorderHasRecording.value = true
            _recorderVisualizerHeights.value = List(12) { 5f }
            recorderJob?.cancel()
            playSystemSound("click")
        } else if (_recorderState.value == "PLAYING") {
            _recorderState.value = "IDLE"
            _recorderVisualizerHeights.value = List(12) { 5f }
            recorderJob?.cancel()
            playSystemSound("click")
        }
    }

    fun startPlaying() {
        if (_recorderState.value != "IDLE" || !_recorderHasRecording.value) return
        _recorderState.value = "PLAYING"
        playSystemSound("click")
        val maxDur = _recorderDuration.value
        var playDur = 0
        recorderJob = CoroutineScope(Dispatchers.Main).launch {
            while (playDur < maxDur && _recorderState.value == "PLAYING") {
                delay(1000)
                playDur += 1
                _recorderVisualizerHeights.value = List(12) { (5..35).random().toFloat() }
            }
            _recorderState.value = "IDLE"
            _recorderVisualizerHeights.value = List(12) { 5f }
        }
    }

    // Simulated SMS
    private val _smsConversations = MutableStateFlow(
        mapOf(
            "Lyoneidas" to listOf(
                ChatMessage("Lyoneidas", "¡Hola! ¿Me llamas cuando puedas? :-)", false),
                ChatMessage("Yo", "¡Claro! En un rato te marco.", true)
            ),
            "Asistente AI (Gemini)" to listOf(
                ChatMessage("AI", "Hola, soy el Asistente de Jelly Bean 4.1. Pregúntame lo que quieras hoy.", false)
            )
        )
    )
    val smsConversations = _smsConversations.asStateFlow()

    private val _selectedChat = MutableStateFlow<String?>(null)
    val selectedChat = _selectedChat.asStateFlow()

    // Simulated Search Card & Grounding
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading = _searchLoading.asStateFlow()

    private val _searchResultText = MutableStateFlow("")
    val searchResultText = _searchResultText.asStateFlow()

    private val _searchSources = MutableStateFlow<List<WebSource>>(emptyList())
    val searchSources = _searchSources.asStateFlow()

    // Simulated Camera Roll / Gallery
    private val _galleryImages = MutableStateFlow<List<Bitmap>>(emptyList())
    val galleryImages = _galleryImages.asStateFlow()

    private val _cameraPrompt = MutableStateFlow("")
    val cameraPrompt = _cameraPrompt.asStateFlow()

    private val _cameraSelectedSize = MutableStateFlow("1K") // 1K, 2K, 4K
    val cameraSelectedSize = _cameraSelectedSize.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError = _cameraError.asStateFlow()

    private val _cameraGenerating = MutableStateFlow(false)
    val cameraGenerating = _cameraGenerating.asStateFlow()

    // AI Image Generator State
    private val _aiPrompt = MutableStateFlow("")
    val aiPrompt = _aiPrompt.asStateFlow()

    private val _aiSelectedSize = MutableStateFlow("1K") // 512px, 1K, 2K
    val aiSelectedSize = _aiSelectedSize.asStateFlow()

    private val _aiSelectedRatio = MutableStateFlow("1:1") // 1:1, 16:9, 9:16, 4:3, 3:2
    val aiSelectedRatio = _aiSelectedRatio.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError = _aiError.asStateFlow()

    private val _aiGenerating = MutableStateFlow(false)
    val aiGenerating = _aiGenerating.asStateFlow()

    private val _aiResult = MutableStateFlow<Bitmap?>(null)
    val aiResult = _aiResult.asStateFlow()

    fun updateAiPrompt(p: String) {
        _aiPrompt.value = p
    }

    fun setAiSize(s: String) {
        _aiSelectedSize.value = s
    }

    fun setAiRatio(r: String) {
        _aiSelectedRatio.value = r
    }

    fun clearAiResult() {
        _aiResult.value = null
        _aiError.value = null
    }

    // Calculator state
    private val _calcDisplay = MutableStateFlow("0")
    val calcDisplay = _calcDisplay.asStateFlow()
    private var calcFirstOperand = 0.0
    private var calcOperator = ""
    private var isCalcNewNumber = true

    // Play Store/Market installed apps simulation
    private val _installedApps = MutableStateFlow(
        setOf(
            AppType.PHONE, AppType.PEOPLE, AppType.BROWSER, AppType.MESSAGING,
            AppType.CAMERA, AppType.GALLERY, AppType.CALCULATOR, AppType.SETTINGS,
            AppType.MAPS, AppType.MARKET, AppType.CLOCK, AppType.MUSIC,
            AppType.CALENDAR, AppType.ABOUT, AppType.FILE_MANAGER, AppType.THEMES,
            AppType.AI_GENERATOR, AppType.SOUND_RECORDER, AppType.DOWNLOADS,
            AppType.NOTES, AppType.RADIO
        )
    )
    val installedApps = _installedApps.asStateFlow()

    // System notifications shade items
    private val _notifications = MutableStateFlow(
        listOf(
            AppNotification(1, "Lyoneidas", "hey give me a call:-)", AppType.MESSAGING),
            AppNotification(2, "Actualización de Sistema", "Disfruta de Android 4.1.2 Jelly Bean Holo", AppType.ABOUT)
        )
    )
    val notifications = _notifications.asStateFlow()

    // Easter Egg Physic Particles
    private val _jellyBeansParticles = MutableStateFlow<List<JellyBean>>(emptyList())
    val jellyBeansParticles = _jellyBeansParticles.asStateFlow()

    init {
        // Load notes from SharedPreferences
        val notesStr = application.getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .getString("notes_list", "") ?: ""
        if (notesStr.isEmpty()) {
            _notes.value = defaultNotes()
            saveNotesToPrefs(defaultNotes())
        } else {
            _notes.value = deserializeNotes(notesStr)
        }
        updateHashFromState()

        // Start live clock updates
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                val sdfTime = SimpleDateFormat("H:mm", Locale.getDefault())
                val sdfDate = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
                _currentTime.value = sdfTime.format(Date())
                _currentDate.value = sdfDate.format(Date()).replaceFirstChar { it.uppercase() }
                delay(1000)
            }
        }
    }

    // Wi-Fi Toggles
    fun toggleWifi() {
        val next = !_isWifiEnabled.value
        _isWifiEnabled.value = next
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("wifi_enabled", next).apply()
    }
    fun toggleBluetooth() {
        val next = !_isBluetoothEnabled.value
        _isBluetoothEnabled.value = next
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("bluetooth_enabled", next).apply()
    }
    fun toggleLocation() {
        val next = !_isLocationEnabled.value
        _isLocationEnabled.value = next
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("location_enabled", next).apply()
    }
    fun toggleData() {
        val next = !_isDataEnabled.value
        _isDataEnabled.value = next
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("data_enabled", next).apply()
    }

    fun toggleQuickSettingsMode() {
        _isQuickSettingsActive.value = !_isQuickSettingsActive.value
    }

    fun setSystemBrightness(value: Float) {
        _systemBrightness.value = value
    }

    fun toggleSoundProfile() {
        _soundProfile.value = when (_soundProfile.value) {
            "SOUND" -> "VIBRATE"
            "VIBRATE" -> "MUTE"
            else -> "SOUND"
        }
    }

    fun toggleAirplaneMode() {
        val nextMode = !_isAirplaneModeEnabled.value
        _isAirplaneModeEnabled.value = nextMode
        if (nextMode) {
            _isWifiEnabled.value = false
            _isBluetoothEnabled.value = false
            _isDataEnabled.value = false
        } else {
            _isWifiEnabled.value = true
        }
    }

    fun setOwnerName(name: String) {
        _ownerName.value = name
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().putString("owner_name", name).apply()
    }

    fun resetSimulator() {
        getApplication<Application>().getSharedPreferences("jellybean_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
        _ownerName.value = "Administrador Jelly Bean"
        _themeColorProfile.value = "BLUE"
        _wallpaperPreset.value = "WAVES"
        _isWifiEnabled.value = true
        _isBluetoothEnabled.value = true
        _isLocationEnabled.value = true
        _isDataEnabled.value = true
        _isAnalogClockWidgetActive.value = true
        _notes.value = defaultNotes()
        postNotification("Sistema", "El dispositivo ha sido restablecido de fábrica.", AppType.SETTINGS)
        goHome()
    }

    fun unlock() {
        _isLocked.value = false
        updateHashFromState()
        playSystemSound("unlock")
    }

    fun lock() {
        _isLocked.value = true
        _isDrawerOpen.value = false
        _activeApp.value = null
        updateHashFromState()
        playSystemSound("lock")
    }

    fun openDrawer() {
        _isDrawerOpen.value = true
        _isGoogleFolderOpen.value = false
        updateHashFromState()
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
        updateHashFromState()
    }

    fun openGoogleFolder() {
        _isGoogleFolderOpen.value = true
    }

    fun closeGoogleFolder() {
        _isGoogleFolderOpen.value = false
    }

    fun launchApp(app: AppType) {
        _activeApp.value = app
        _isDrawerOpen.value = false
        _isGoogleFolderOpen.value = false
        _isNotificationShadeOpen.value = false
        _showRecentAppsOverlay.value = false
        if (!_recentAppsList.value.contains(app)) {
            _recentAppsList.value = listOf(app) + _recentAppsList.value
        }
        updateHashFromState()
    }

    fun closeActiveApp() {
        _activeApp.value = null
        updateHashFromState()
    }

    fun goHome() {
        _activeApp.value = null
        _isDrawerOpen.value = false
        _isGoogleFolderOpen.value = false
        _showRecentAppsOverlay.value = false
        updateHashFromState()
    }

    fun toggleRecentApps() {
        _showRecentAppsOverlay.value = !_showRecentAppsOverlay.value
    }

    fun removeRecentApp(app: AppType) {
        _recentAppsList.value = _recentAppsList.value.filter { it != app }
    }

    fun clearRecentApps() {
        _recentAppsList.value = emptyList()
        _showRecentAppsOverlay.value = false
    }

    fun toggleNotificationShade() {
        _isNotificationShadeOpen.value = !_isNotificationShadeOpen.value
    }

    fun dismissNotification(id: Int) {
        _notifications.value = _notifications.value.filter { it.id != id }
    }

    fun clearAllNotifications() {
        _notifications.value = emptyList()
    }

    fun selectChat(contact: String?) {
        _selectedChat.value = contact
    }

    fun sendSMS(text: String) {
        val chat = _selectedChat.value ?: return
        if (text.trim().isEmpty()) return

        val userMsg = ChatMessage("Yo", text, true)
        val updatedChatList = (_smsConversations.value[chat] ?: emptyList()) + userMsg
        _smsConversations.value = _smsConversations.value + (chat to updatedChatList)

        if (chat == "Asistente AI (Gemini)") {
            // Trigger Gemini API Assistant Response
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prompt = "Responde brevemente de manera amigable imitando un asistente de Android 4.1 Jelly Bean (con interfaz Holo de color azul). Mantén la respuesta ingeniosa, con un toque nostálgico del año 2012 si viene al caso, y resumida en máximo 3 líneas. Mensaje de usuario: $text"
                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                    )
                    val response = RetrofitClient.apiService.generateContent(
                        model = "gemini-3.5-flash",
                        apiKey = BuildConfig.GEMINI_API_KEY,
                        request = request
                    )
                    val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "No se pudo obtener respuesta del sistema."

                    withContext(Dispatchers.Main) {
                        val updatedList = (_smsConversations.value[chat] ?: emptyList()) + ChatMessage("AI", replyText, false)
                        _smsConversations.value = _smsConversations.value + (chat to updatedList)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val updatedList = (_smsConversations.value[chat] ?: emptyList()) + ChatMessage("AI", "Err: No hay conexión con el núcleo de Jelly Bean AI. (${e.localizedMessage})", false)
                        _smsConversations.value = _smsConversations.value + (chat to updatedList)
                    }
                }
            }
        }
    }

    // AI Grounded Search
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch() {
        val query = _searchQuery.value
        if (query.trim().isEmpty()) return

        _searchLoading.value = true
        _searchResultText.value = ""
        _searchSources.value = emptyList()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Grounding API request structure
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = "Proporciona información estructurada sobre: $query. Por favor, escribe en español.")) )
                    ),
                    tools = listOf(GeminiTool(google_search = emptyMap(), googleSearch = emptyMap()))
                )
                val response = RetrofitClient.apiService.generateContent(
                    model = "gemini-3.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    request = request
                )

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No se recibieron resultados de búsqueda."

                // Grounding Metadata mapping
                val metadata = response.candidates?.firstOrNull()?.groundingMetadata
                val rawSources = metadata?.groundingChunks?.mapNotNull { it.web } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _searchResultText.value = replyText
                    _searchSources.value = rawSources
                    _searchLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _searchResultText.value = "Error al completar la búsqueda de Google. Verifica tu conexión a internet o la configuración de tu Gemini API Key en AI Studio.\nDetalles: ${e.localizedMessage}"
                    _searchLoading.value = false
                }
            }
        }
    }

    // AI Camera Image Generation
    fun updateCameraPrompt(prompt: String) {
        _cameraPrompt.value = prompt
    }

    fun setCameraSize(size: String) {
        _cameraSelectedSize.value = size
    }

    fun generateCameraPhoto() {
        val prompt = _cameraPrompt.value
        if (prompt.trim().isEmpty()) return

        _cameraGenerating.value = true
        _cameraError.value = null
        playSystemSound("camera")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val size = _cameraSelectedSize.value
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        imageConfig = ImageConfig(aspectRatio = "1:1", imageSize = size),
                        responseModalities = listOf("TEXT", "IMAGE")
                    )
                )
                val response = RetrofitClient.apiService.generateContent(
                    model = "gemini-3-pro-image-preview",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    request = request
                )

                var imageB64: String? = null
                response.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                    if (part.inlineData?.mimeType?.startsWith("image/") == true) {
                        imageB64 = part.inlineData.data
                    }
                }

                if (imageB64 != null) {
                    val decodedBytes = Base64.decode(imageB64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            _galleryImages.value = _galleryImages.value + bitmap
                            _cameraPrompt.value = ""
                        } else {
                            _cameraError.value = "Error al decodificar la imagen de IA."
                        }
                        _cameraGenerating.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _cameraError.value = "La API no retornó una imagen en formato inlineData."
                        _cameraGenerating.value = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _cameraError.value = "Fallo en generación: ${e.localizedMessage}"
                    _cameraGenerating.value = false
                }
            }
        }
    }

    fun deleteGalleryImage(bitmap: Bitmap) {
        _galleryImages.value = _galleryImages.value.filter { it != bitmap }
    }

    fun generateAIImage() {
        val prompt = _aiPrompt.value
        if (prompt.trim().isEmpty()) return

        _aiGenerating.value = true
        _aiError.value = null
        _aiResult.value = null

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val size = _aiSelectedSize.value
                val ratio = _aiSelectedRatio.value
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        imageConfig = ImageConfig(aspectRatio = ratio, imageSize = size),
                        responseModalities = listOf("TEXT", "IMAGE")
                    )
                )
                val response = RetrofitClient.apiService.generateContent(
                    model = "gemini-3-pro-image-preview",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    request = request
                )

                var imageB64: String? = null
                response.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                    if (part.inlineData?.mimeType?.startsWith("image/") == true) {
                        imageB64 = part.inlineData.data
                    }
                }

                if (imageB64 != null) {
                    val decodedBytes = Base64.decode(imageB64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            _aiResult.value = bitmap
                            _galleryImages.value = _galleryImages.value + bitmap
                        } else {
                            _aiError.value = "Error al decodificar la imagen de IA."
                        }
                        _aiGenerating.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _aiError.value = "La API no retornó una imagen en formato inlineData."
                        _aiGenerating.value = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _aiError.value = "Fallo en generación: ${e.localizedMessage}"
                    _aiGenerating.value = false
                }
            }
        }
    }

    fun setAsWallpaper(bitmap: Bitmap) {
        _wallpaperSelected.value = bitmap
    }

    // Calculator operations logic
    fun handleCalcKey(key: String) {
        val current = _calcDisplay.value
        when (key) {
            "C" -> {
                _calcDisplay.value = "0"
                calcFirstOperand = 0.0
                calcOperator = ""
                isCalcNewNumber = true
            }
            "+", "-", "×", "÷" -> {
                calcFirstOperand = current.toDoubleOrNull() ?: 0.0
                calcOperator = key
                isCalcNewNumber = true
            }
            "=" -> {
                val secondOperand = current.toDoubleOrNull() ?: 0.0
                val result = when (calcOperator) {
                    "+" -> calcFirstOperand + secondOperand
                    "-" -> calcFirstOperand - secondOperand
                    "×" -> calcFirstOperand * secondOperand
                    "÷" -> if (secondOperand != 0.0) calcFirstOperand / secondOperand else Double.NaN
                    else -> secondOperand
                }
                _calcDisplay.value = if (result % 1 == 0.0) {
                    result.toInt().toString()
                } else {
                    result.toString()
                }
                calcOperator = ""
                isCalcNewNumber = true
            }
            else -> {
                if (isCalcNewNumber || current == "0") {
                    _calcDisplay.value = key
                    isCalcNewNumber = false
                } else {
                    _calcDisplay.value = current + key
                }
            }
        }
    }

    // Play Store app installation simulation
    fun installAppInStore(app: AppType) {
        _installedApps.value = _installedApps.value + app
    }

    fun uninstallAppInStore(app: AppType) {
        if (app != AppType.ABOUT && app != AppType.SETTINGS) {
            _installedApps.value = _installedApps.value - app
            if (_activeApp.value == app) {
                _activeApp.value = null
            }
        }
    }

    // Gravity Easter egg particles setup
    fun spawnJellyBeans() {
        val list = mutableListOf<JellyBean>()
        val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan, Color(0xFFFF8A00), Color(0xFFC133FF))
        val names = listOf("Cherry", "Limón", "Mora", "Plátano", "Sandía", "Holo Blue", "Naranja", "Uva")
        for (i in 0 until 40) {
            list.add(
                JellyBean(
                    id = i,
                    x = Random.nextFloat() * 400 + 100,
                    y = Random.nextFloat() * 600 + 100,
                    vx = (Random.nextFloat() - 0.5f) * 12,
                    vy = (Random.nextFloat() - 0.5f) * 12,
                    color = colors.random(),
                    size = Random.nextFloat() * 25 + 25,
                    labelName = names.random()
                )
            )
        }
        _jellyBeansParticles.value = list
    }

    fun clearJellyBeans() {
        _jellyBeansParticles.value = emptyList()
    }

    fun updateJellyBeansPhysics(width: Float, height: Float) {
        val updated = _jellyBeansParticles.value.map { bean ->
            bean.x += bean.vx
            bean.y += bean.vy

            // Friction & Gravity
            bean.vy += 0.25f // Simulated gravity pull

            // Walls bounces
            if (bean.x - bean.size < 0) {
                bean.x = bean.size
                bean.vx = -bean.vx * 0.75f
            } else if (bean.x + bean.size > width) {
                bean.x = width - bean.size
                bean.vx = -bean.vx * 0.75f
            }

            if (bean.y - bean.size < 0) {
                bean.y = bean.size
                bean.vy = -bean.vy * 0.75f
            } else if (bean.y + bean.size > height) {
                bean.y = height - bean.size
                bean.vy = -bean.vy * 0.75f
            }

            bean
        }
        _jellyBeansParticles.value = updated
    }

    // Notification Ticker state for status bar
    private val _notificationTicker = MutableStateFlow<AppNotification?>(null)
    val notificationTicker = _notificationTicker.asStateFlow()

    fun postNotification(sender: String, message: String, appType: AppType) {
        val nextId = (_notifications.value.maxOfOrNull { it.id } ?: 0) + 1
        val newNotif = AppNotification(nextId, sender, message, appType)
        _notifications.value = _notifications.value + newNotif

        playSystemSound("notification")

        // Trigger the ticker
        _notificationTicker.value = newNotif
        CoroutineScope(Dispatchers.Main).launch {
            delay(4000)
            if (_notificationTicker.value?.id == nextId) {
                _notificationTicker.value = null
            }
        }
    }

    // ==========================================
    // CLOCK APP SIMULATED STATE
    // ==========================================
    data class AlarmItem(val id: Int, val time: String, val label: String, val isEnabled: Boolean)

    private val _alarms = MutableStateFlow(
        listOf(
            AlarmItem(1, "07:30 AM", "Despertador Diario", true),
            AlarmItem(2, "08:15 AM", "Gimnasio", false),
            AlarmItem(3, "09:00 AM", "Standup Meeting", false)
        )
    )
    val alarms = _alarms.asStateFlow()

    fun toggleAlarm(id: Int) {
        _alarms.value = _alarms.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
    }

    fun addAlarm(time: String, label: String) {
        val nextId = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1
        _alarms.value = _alarms.value + AlarmItem(nextId, time, label, true)
        postNotification("Reloj", "Alarma configurada: $time - $label", AppType.CLOCK)
    }

    fun removeAlarm(id: Int) {
        _alarms.value = _alarms.value.filter { it.id != id }
    }

    // ==========================================
    // MUSIC PLAYER SIMULATED STATE
    // ==========================================
    data class MusicTrack(val id: Int, val title: String, val artist: String, val durationSec: Int)

    private val _musicTracksState = MutableStateFlow(
        listOf(
            MusicTrack(1, "Ursa Major (Jelly Bean Retro)", "Google Retro Band", 145),
            MusicTrack(2, "Bossa de la Sonda", "Androides del Espacio", 182),
            MusicTrack(3, "Holo Blue Symphony", "Duarte & Sola", 210),
            MusicTrack(4, "Sweet Marshmallow Beat", "Key Lime Pi", 160)
        )
    )
    val musicTracksState = _musicTracksState.asStateFlow()
    val musicTracks: List<MusicTrack> get() = _musicTracksState.value

    fun addMusicTrack(title: String, artist: String, durationSec: Int) {
        val nextId = _musicTracksState.value.size + 1
        val newTrack = MusicTrack(nextId, title, artist, durationSec)
        _musicTracksState.value = _musicTracksState.value + newTrack
        postNotification("Play Music", "Importada exitosamente: $title", AppType.MUSIC)
    }

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex = _currentTrackIndex.asStateFlow()

    private val _isMusicPlaying = MutableStateFlow(false)
    val isMusicPlaying = _isMusicPlaying.asStateFlow()

    private val _musicProgressSec = MutableStateFlow(0)
    val musicProgressSec = _musicProgressSec.asStateFlow()

    private var musicJob: Job? = null

    fun playPauseMusic() {
        val playing = !_isMusicPlaying.value
        _isMusicPlaying.value = playing
        if (playing) {
            startMusicTimer()
        } else {
            musicJob?.cancel()
        }
    }

    fun nextTrack() {
        _currentTrackIndex.value = (_currentTrackIndex.value + 1) % musicTracks.size
        _musicProgressSec.value = 0
        if (_isMusicPlaying.value) {
            startMusicTimer()
        }
    }

    fun prevTrack() {
        _currentTrackIndex.value = (_currentTrackIndex.value - 1 + musicTracks.size) % musicTracks.size
        _musicProgressSec.value = 0
        if (_isMusicPlaying.value) {
            startMusicTimer()
        }
    }

    fun seekMusic(sec: Int) {
        val total = musicTracks[_currentTrackIndex.value].durationSec
        _musicProgressSec.value = sec.coerceIn(0, total)
    }

    private fun startMusicTimer() {
        musicJob?.cancel()
        musicJob = CoroutineScope(Dispatchers.Main).launch {
            while (_isMusicPlaying.value) {
                delay(1000)
                val total = musicTracks[_currentTrackIndex.value].durationSec
                if (_musicProgressSec.value >= total) {
                    _currentTrackIndex.value = (_currentTrackIndex.value + 1) % musicTracks.size
                    _musicProgressSec.value = 0
                } else {
                    _musicProgressSec.value += 1
                }
            }
        }
    }

    // ==========================================
    // CALENDAR APP SIMULATED STATE
    // ==========================================
    data class CalendarEvent(val id: Int, val day: Int, val title: String, val time: String)

    private val _calendarEvents = MutableStateFlow(
        listOf(
            CalendarEvent(1, 15, "Google I/O Jelly Bean Release", "10:00 AM"),
            CalendarEvent(2, 23, "Reunión de Desarrolladores", "04:30 PM"),
            CalendarEvent(3, 4, "Cumpleaños de Android", "12:00 PM")
        )
    )
    val calendarEvents = _calendarEvents.asStateFlow()

    fun addCalendarEvent(day: Int, title: String, time: String) {
        val nextId = (_calendarEvents.value.maxOfOrNull { it.id } ?: 0) + 1
        _calendarEvents.value = _calendarEvents.value + CalendarEvent(nextId, day, title, time)
        postNotification("Calendario", "Comida/Evento guardado día $day: $title", AppType.CALENDAR)
    }

    fun deleteCalendarEvent(id: Int) {
        _calendarEvents.value = _calendarEvents.value.filter { it.id != id }
    }
}

// ==========================================
// MAIN ACTIVITY ENTRY POINT
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF33B5E5), // Holo Blue
                    background = Color.Black,
                    surface = Color(0xFF1A1A1A)
                )
            ) {
                val viewModel: JellyBeanViewModel = viewModel()
                JellyBeanSimulatorView(viewModel)
            }
        }
    }
}

// ==========================================
// CENTRAL RETRO SIMULATOR VIEWPORT
// ==========================================
// ==========================================
// CENTRAL RETRO SIMULATOR VIEWPORT
// ==========================================
@Composable
fun ScreenContent(
    viewModel: JellyBeanViewModel,
    isLocked: Boolean,
    isNotificationShadeOpen: Boolean,
    activeApp: AppType?,
    isDrawerOpen: Boolean,
    showRecentsOverlay: Boolean,
    wallpaperBmp: android.graphics.Bitmap?,
    wallpaperPreset: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("jelly_bean_simulator")
    ) {
        // Desktop Wallpaper (Generated, Classic Android 4.1 wave, or Solid/Gradient Preset)
        if (wallpaperBmp != null) {
            Image(
                bitmap = wallpaperBmp!!.asImageBitmap(),
                contentDescription = "Fondo de Pantalla",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            when (wallpaperPreset) {
                "SOLID" -> {
                    // Solid Retro deep blue/charcoal color
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1219)))
                }
                "SOLID_BLACK" -> {
                    // Pure solid black wallpaper
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000)))
                }
                "GRADIENT" -> {
                    // Classic dark blue/black linear gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF0F1826), Color(0xFF000000))
                                )
                            )
                    )
                }
                "NEXUS" -> {
                    // Classic Nexus tile mosaic pattern
                    NexusMosaicWallpaper(modifier = Modifier.fillMaxSize())
                }
                else -> { // "WAVES"
                    val themeColorProfile by viewModel.themeColorProfile.collectAsState()
                    JellyBeanWaveWallpaper(themeProfile = themeColorProfile, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Deep blue vignette overlay to replicate retro gloss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color(0x9A000A1F)),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 1.2f
                        )
                    )
                }
        )

        // Simulated OS Interface (Status Bar + Desktop Canvas + Sub-panels)
        Column(modifier = Modifier.fillMaxSize()) {
            HoloStatusBar(viewModel)

            Box(modifier = Modifier.weight(1f)) {
                if (isLocked) {
                    JellyBeanLockScreen(viewModel)
                } else {
                    JellyBeanDesktop(viewModel)

                    // Overlay Drawer
                    if (isDrawerOpen) {
                        JellyBeanAppDrawer(viewModel)
                    }

                    // Overlay App window with zoom-in opening animations
                    var lastActiveApp by remember { mutableStateOf<AppType?>(null) }
                    if (activeApp != null) {
                        lastActiveApp = activeApp
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = activeApp != null,
                        enter = scaleIn(initialScale = 0.82f, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200)),
                        exit = scaleOut(targetScale = 0.82f, animationSpec = tween(220, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(150))
                    ) {
                        lastActiveApp?.let { app ->
                            JellyBeanAppWindow(app, viewModel)
                        }
                    }
                }

                // Recent Apps Task Manager Overlay
                if (showRecentsOverlay) {
                    RecentAppsOverlay(viewModel)
                }
            }

            // Android Legacy Navigation Softkeys
            SoftNavigationKeys(viewModel)
        }

        // Pull down slide notification Shade
        AnimatedVisibility(
            visible = isNotificationShadeOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            NotificationShade(viewModel)
        }
    }
}

@Composable
fun JellyBeanSimulatorView(viewModel: JellyBeanViewModel) {
    val isLocked by viewModel.isLocked.collectAsState()
    val isNotificationShadeOpen by viewModel.isNotificationShadeOpen.collectAsState()
    val activeApp by viewModel.activeApp.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val showRecentsOverlay by viewModel.showRecentAppsOverlay.collectAsState()
    val wallpaperBmp by viewModel.wallpaperSelected.collectAsState()
    val wallpaperPreset by viewModel.wallpaperPreset.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060608)),
        contentAlignment = Alignment.Center
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val showFullBezel = screenWidth >= 400.dp && screenHeight >= 760.dp

        if (showFullBezel) {
            // Elegant background pattern for wide screens representing a workbench
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Soft horizontal scanlines in desk background
                        val scanlineSpacing = 16.dp.toPx()
                        for (y in 0..size.height.toInt() step scanlineSpacing.toInt()) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.015f),
                                start = Offset(0f, y.toFloat()),
                                end = Offset(size.width, y.toFloat()),
                                strokeWidth = 1f
                            )
                        }
                        // Radial ambient blue back-light glow behind the physical device mockup
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF1B314B).copy(alpha = 0.35f), Color.Transparent),
                                radius = size.width * 0.5f
                            ),
                            center = Offset(size.width / 2, size.height / 2)
                        )
                    }
            )

            // Physical Side Keys and Chassis
            Box(
                modifier = Modifier.size(width = 380.dp, height = 744.dp),
                contentAlignment = Alignment.Center
            ) {
                // Physical Volume Rocker on the left side
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-4).dp, y = (-40).dp)
                        .width(4.dp)
                        .height(80.dp)
                        .background(Color(0xFF222224), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .border(1.dp, Color(0xFF3C3C40), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .clickable {
                            viewModel.playSystemSound("click")
                            viewModel.postNotification("Audio", "Volumen del dispositivo ajustado", AppType.SETTINGS)
                        }
                ) {}

                // Physical Power Key on the right side
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 4.dp, y = (-100).dp)
                        .width(4.dp)
                        .height(45.dp)
                        .background(Color(0xFF222224), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .border(1.dp, Color(0xFF3C3C40), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .clickable {
                            viewModel.playSystemSound("click")
                            if (isLocked) viewModel.unlock() else viewModel.lock()
                        }
                ) {}

                // Phone Bezel Frame
                Card(
                    modifier = Modifier
                        .size(width = 370.dp, height = 730.dp)
                        .shadow(24.dp, RoundedCornerShape(44.dp)),
                    shape = RoundedCornerShape(44.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111113)),
                    border = BorderStroke(2.5.dp, Color(0xFF28282C))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Bezel: Speaker grill + front-facing camera
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Speaker capsule
                            Box(
                                modifier = Modifier
                                    .width(62.dp)
                                    .height(5.dp)
                                    .background(Color(0xFF222225), RoundedCornerShape(2.5.dp))
                                    .border(0.5.dp, Color(0xFF09090A), RoundedCornerShape(2.5.dp))
                            )
                            // Front Camera Lens
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 72.dp)
                                    .size(7.dp)
                                    .background(Color(0xFF040C14), CircleShape)
                                    .border(1.dp, Color(0xFF1B3252), CircleShape)
                            )
                        }

                        // Inner Screen Area
                        Box(
                            modifier = Modifier
                                .width(346.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black)
                        ) {
                            ScreenContent(
                                viewModel = viewModel,
                                isLocked = isLocked,
                                isNotificationShadeOpen = isNotificationShadeOpen,
                                activeApp = activeApp,
                                isDrawerOpen = isDrawerOpen,
                                showRecentsOverlay = showRecentsOverlay,
                                wallpaperBmp = wallpaperBmp,
                                wallpaperPreset = wallpaperPreset
                            )
                        }

                        // Bottom Bezel: Pulsing Retro LED Indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "bezel_led")
                            val ledAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.25f,
                                targetValue = 0.85f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "led_alpha"
                            )
                            // Subtle blue breath notification indicator (classic galaxy nexus style)
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .shadow(4.dp, CircleShape)
                                    .background(Color(0xFF33B5E5).copy(alpha = ledAlpha), CircleShape)
                                    .border(0.5.dp, Color(0xFF33B5E5).copy(alpha = 0.8f), CircleShape)
                            )
                        }
                    }
                }
            }
        } else {
            // Full screen mode for small/compact screens
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                ScreenContent(
                    viewModel = viewModel,
                    isLocked = isLocked,
                    isNotificationShadeOpen = isNotificationShadeOpen,
                    activeApp = activeApp,
                    isDrawerOpen = isDrawerOpen,
                    showRecentsOverlay = showRecentsOverlay,
                    wallpaperBmp = wallpaperBmp,
                    wallpaperPreset = wallpaperPreset
                )
            }
        }
    }
}

@Composable
fun JellyBeanWaveWallpaper(themeProfile: String = "BLUE", modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val bgColors = when (themeProfile) {
            "PURPLE" -> listOf(Color(0xFF11021C), Color(0xFF0A0216), Color(0xFF010005))
            "GREEN" -> listOf(Color(0xFF021204), Color(0xFF010902), Color(0xFF000300))
            "RED" -> listOf(Color(0xFF160101), Color(0xFF0F0101), Color(0xFF050000))
            "AMBER" -> listOf(Color(0xFF1C1201), Color(0xFF120C01), Color(0xFF050300))
            else -> listOf(Color(0xFF0F041C), Color(0xFF030D22), Color(0xFF01040A))
        }

        val waveAColors = when (themeProfile) {
            "PURPLE" -> listOf(Color(0x2ABA68C8), Color(0x00BA68C8))
            "GREEN" -> listOf(Color(0x2A1B9A2F), Color(0x001B9A2F))
            "RED" -> listOf(Color(0x2A9C27B0), Color(0x009C27B0))
            "AMBER" -> listOf(Color(0x2AD81B60), Color(0x00D81B60))
            else -> listOf(Color(0x2A6A1B9A), Color(0x006A1B9A))
        }

        val waveBColors = when (themeProfile) {
            "PURPLE" -> listOf(Color(0x2F7B1FA2), Color(0x007B1FA2))
            "GREEN" -> listOf(Color(0x3500E676), Color(0x0000E676))
            "RED" -> listOf(Color(0x35FF1744), Color(0x00FF1744))
            "AMBER" -> listOf(Color(0x35FFAB00), Color(0x00FFAB00))
            else -> listOf(Color(0x3500B0FF), Color(0x0000B0FF))
        }

        val waveCColors = when (themeProfile) {
            "PURPLE" -> listOf(Color(0x2033B5E5), Color(0x0033B5E5))
            "GREEN" -> listOf(Color(0x20FFD600), Color(0x00FFD600))
            "RED" -> listOf(Color(0x20FF9100), Color(0x00FF9100))
            "AMBER" -> listOf(Color(0x20FF3D00), Color(0x00FF3D00))
            else -> listOf(Color(0x20FF8F00), Color(0x00FF8F00))
        }

        val waveDColors = when (themeProfile) {
            "PURPLE" -> listOf(Color(0x00EA80FC), Color(0x44E040FB), Color(0x66D500F9), Color(0x44E040FB), Color(0x00EA80FC))
            "GREEN" -> listOf(Color(0x00B2FF59), Color(0x4400E676), Color(0x6600C853), Color(0x4400E676), Color(0x00B2FF59))
            "RED" -> listOf(Color(0x00FF8A80), Color(0x44FF5252), Color(0x66FF1744), Color(0x44FF5252), Color(0x00FF8A80))
            "AMBER" -> listOf(Color(0x00FFE082), Color(0x44FFD54F), Color(0x66FFC107), Color(0x44FFD54F), Color(0x00FFE082))
            else -> listOf(Color(0x0000E5FF), Color(0x4400E5FF), Color(0x6633B5E5), Color(0x4400E5FF), Color(0x0000E5FF))
        }

        val haloColor = when (themeProfile) {
            "PURPLE" -> Color(0xFFEA80FC)
            "GREEN" -> Color(0xFFFFD600)
            "RED" -> Color(0xFFFF9100)
            "AMBER" -> Color(0xFFFF3D00)
            else -> Color(0xFFFFA000)
        }

        val coreColor = when (themeProfile) {
            "PURPLE" -> Color(0xFFD500F9)
            "GREEN" -> Color(0xFF00E676)
            "RED" -> Color(0xFFFF1744)
            "AMBER" -> Color(0xFFFFC107)
            else -> Color(0xFF33B5E5)
        }

        // 1. Base Gradient
        drawRect(brush = Brush.verticalGradient(colors = bgColors))

        // 2. Overlapping sweeping waves (Bezier Curves with gradients)
        // Wave A
        val pathA = Path().apply {
            moveTo(0f, height * 0.45f)
            cubicTo(
                width * 0.35f, height * 0.25f,
                width * 0.65f, height * 0.85f,
                width, height * 0.55f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = pathA,
            brush = Brush.verticalGradient(
                colors = waveAColors,
                startY = height * 0.3f,
                endY = height
            )
        )

        // Wave B
        val pathB = Path().apply {
            moveTo(0f, height * 0.65f)
            cubicTo(
                width * 0.4f, height * 0.45f,
                width * 0.6f, height * 0.9f,
                width, height * 0.75f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = pathB,
            brush = Brush.verticalGradient(
                colors = waveBColors,
                startY = height * 0.4f,
                endY = height
            )
        )

        // Wave C
        val pathC = Path().apply {
            moveTo(0f, height * 0.15f)
            cubicTo(
                width * 0.3f, height * 0.05f,
                width * 0.7f, height * 0.45f,
                width, height * 0.25f
            )
            lineTo(width, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(
            path = pathC,
            brush = Brush.verticalGradient(
                colors = waveCColors,
                startY = 0f,
                endY = height * 0.4f
            )
        )

        // Wave D
        val pathD = Path().apply {
            moveTo(0f, height * 0.52f)
            cubicTo(
                width * 0.35f, height * 0.42f,
                width * 0.65f, height * 0.72f,
                width, height * 0.58f
            )
            lineTo(width, height * 0.65f)
            cubicTo(
                width * 0.65f, height * 0.79f,
                width * 0.35f, height * 0.49f,
                0f, height * 0.59f
            )
            close()
        }
        drawPath(
            path = pathD,
            brush = Brush.linearGradient(
                colors = waveDColors,
                start = Offset(0f, height * 0.5f),
                end = Offset(width, height * 0.62f)
            )
        )

        // 3. Floating bokeh particles
        val particles = listOf(
            Offset(width * 0.12f, height * 0.25f) to 22f,
            Offset(width * 0.28f, height * 0.62f) to 34f,
            Offset(width * 0.45f, height * 0.41f) to 15f,
            Offset(width * 0.68f, height * 0.18f) to 28f,
            Offset(width * 0.78f, height * 0.72f) to 40f,
            Offset(width * 0.89f, height * 0.38f) to 18f,
            Offset(width * 0.05f, height * 0.85f) to 30f,
            Offset(width * 0.55f, height * 0.82f) to 25f
        )

        particles.forEach { (pos, radius) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(haloColor.copy(alpha = 0.24f), Color.Transparent),
                    center = pos,
                    radius = radius * 1.5f
                ),
                center = pos,
                radius = radius * 1.5f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.3f), Color.Transparent),
                    center = pos,
                    radius = radius
                ),
                center = pos,
                radius = radius
            )
        }
    }
}

@Composable
fun NexusMosaicWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Background dark gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF030A16), Color(0xFF0A0210))
            )
        )

        // Draw some retro diagonal mosaic tiles
        val colors = listOf(
            Color(0x1A33B5E5), // Blue
            Color(0x1A99CC00), // Green
            Color(0x1AFFBB33), // Yellow
            Color(0x1AFF4444), // Red
            Color(0x1AAA66CC)  // Purple
        )

        val cols = 8
        val rows = 12
        val cellW = w / cols
        val cellH = h / rows

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val hash = (i * 31 + j * 17)
                if (hash % 3 == 0) {
                    val colorIndex = (hash % colors.size)
                    val baseColor = colors[colorIndex]

                    val sizeFactor = 0.4f + (hash % 5) * 0.12f
                    val tileW = cellW * sizeFactor
                    val tileH = cellH * sizeFactor

                    val x = i * cellW + (hash % 7) * (cellW / 10f)
                    val y = j * cellH + (hash % 11) * (cellH / 15f)

                    drawRect(
                        color = baseColor,
                        topLeft = Offset(x, y),
                        size = Size(tileW, tileH)
                    )

                    drawRect(
                        color = baseColor.copy(alpha = baseColor.alpha * 1.5f),
                        topLeft = Offset(x + tileW * 0.2f, y + tileH * 0.2f),
                        size = Size(tileW * 0.6f, tileH * 0.6f)
                    )
                }
            }
        }
    }
}

// ==========================================
// MAIN COGNITIVE COMPONENTS
// ==========================================

// 1. Classic Cyan Status Bar with Retro status icons
@Composable
fun HoloStatusBar(viewModel: JellyBeanViewModel) {
    val time by viewModel.currentTime.collectAsState()
    val wifiEnabled by viewModel.isWifiEnabled.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val ticker by viewModel.notificationTicker.collectAsState()
    val soundProfile by viewModel.soundProfile.collectAsState()
    val simulatedBatteryLevel by viewModel.batteryLevel.collectAsState()
    val simulatedBatteryCharging by viewModel.isBatteryCharging.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(Color.Black)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Notification Icons or Ticker
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentTicker = ticker
            if (currentTicker != null) {
                val tickerIcon = when (currentTicker.appType) {
                    AppType.MESSAGING -> Icons.Default.ChatBubble
                    AppType.CLOCK -> Icons.Default.AccessTime
                    AppType.CALENDAR -> Icons.Default.CalendarToday
                    AppType.SETTINGS -> Icons.Default.Settings
                    AppType.ABOUT -> Icons.Default.Info
                    else -> Icons.Default.Notifications
                }
                Icon(
                    imageVector = tickerIcon,
                    contentDescription = null,
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${currentTicker.sender}: ${currentTicker.message}",
                    color = Color(0xFF33B5E5),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Normal
                )
            } else {
                if (notifications.isNotEmpty()) {
                    notifications.map { it.appType }.distinct().forEach { appType ->
                        val icon = when (appType) {
                            AppType.MESSAGING -> Icons.Default.ChatBubble
                            AppType.CLOCK -> Icons.Default.AccessTime
                            AppType.CALENDAR -> Icons.Default.CalendarToday
                            AppType.SETTINGS -> Icons.Default.Settings
                            AppType.ABOUT -> Icons.Default.Info
                            else -> Icons.Default.Notifications
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = appType.appName,
                            tint = Color(0xFF33B5E5),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    val soundIcon = when (soundProfile) {
                        "SOUND" -> null
                        "VIBRATE" -> Icons.Default.Vibration
                        else -> Icons.Default.VolumeMute
                    }
                    if (soundIcon != null) {
                        Icon(
                            imageVector = soundIcon,
                            contentDescription = "Sound Mode",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { viewModel.toggleNotificationShade() }
        ) {
            Icon(
                imageVector = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = "Wifi",
                tint = if (wifiEnabled) Color(0xFF33B5E5) else Color(0x66FFFFFF),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.SignalCellular4Bar,
                contentDescription = "Signal",
                tint = Color(0xFF33B5E5),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (simulatedBatteryCharging) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Cargando",
                        tint = Color(0xFF99CC00),
                        modifier = Modifier.size(11.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(10.dp)
                        .border(1.dp, if (simulatedBatteryCharging) Color(0xFF99CC00) else Color(0xFF33B5E5), RoundedCornerShape(1.dp))
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((simulatedBatteryLevel / 100f).coerceIn(0f, 1f))
                            .background(if (simulatedBatteryCharging) Color(0xFF99CC00) else Color(0xFF33B5E5))
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = time,
                color = Color(0xFF33B5E5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

// 2. Jelly Bean Radial Lockscreens
@Composable
fun JellyBeanLockScreen(viewModel: JellyBeanViewModel) {
    val time by viewModel.currentTime.collectAsState()
    val date by viewModel.currentDate.collectAsState()
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetDistancePx = with(density) { 100.dp.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val circleSizeMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radial"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 68.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = 2.sp
            )
            Text(
                text = date,
                color = Color(0xCCFFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.unlock() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A33B5E5)),
                border = BorderStroke(1.5.dp, Color(0xFF33B5E5)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .testTag("lock_screen_unlock_button")
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { viewModel.unlock() },
                            onTap = { viewModel.unlock() }
                        )
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Unlock icon",
                        tint = Color(0xFF33B5E5),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DESBLOQUEAR",
                        color = Color(0xFF33B5E5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        val currentDistance = dragOffset.getDistance()
        val coercedOffset = if (currentDistance > targetDistancePx) {
            dragOffset * (targetDistancePx / currentDistance)
        } else {
            dragOffset
        }

        val isUnlockHighlighted = coercedOffset.x > targetDistancePx * 0.6f && kotlin.math.abs(coercedOffset.y) < targetDistancePx * 0.5f
        val isCameraHighlighted = coercedOffset.x < -targetDistancePx * 0.6f && kotlin.math.abs(coercedOffset.y) < targetDistancePx * 0.5f
        val isSearchHighlighted = coercedOffset.y < -targetDistancePx * 0.6f && kotlin.math.abs(coercedOffset.x) < targetDistancePx * 0.5f

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val centerPt = Offset(size.width / 2f, size.height / 2f)
                
                // 1. Concentric ripples
                if (isDragging) {
                    val rippleRadius1 = (50f + (targetDistancePx * circleSizeMultiplier)) % targetDistancePx
                    val rippleRadius2 = (150f + (targetDistancePx * circleSizeMultiplier)) % targetDistancePx
                    val rippleRadius3 = (targetDistancePx * circleSizeMultiplier) % targetDistancePx
                    
                    drawCircle(
                        color = Color(0x3333B5E5),
                        radius = rippleRadius1,
                        center = centerPt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0x1133B5E5),
                        radius = rippleRadius2,
                        center = centerPt,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0x4D33B5E5),
                        radius = rippleRadius3,
                        center = centerPt,
                        style = Stroke(width = 1.dp.toPx())
                    )
                } else {
                    drawCircle(
                        color = Color(0x2233B5E5),
                        radius = targetDistancePx * 0.4f * circleSizeMultiplier,
                        center = centerPt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0x11FFFFFF),
                        radius = targetDistancePx * 0.7f * circleSizeMultiplier,
                        center = centerPt,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 2. Ring of outer dots
                val dotCount = 36
                for (i in 0 until dotCount) {
                    val angle = (i * 2 * Math.PI / dotCount).toFloat()
                    val dotX = centerPt.x + targetDistancePx * kotlin.math.cos(angle)
                    val dotY = centerPt.y + targetDistancePx * kotlin.math.sin(angle)
                    
                    val dotPt = Offset(dotX, dotY)
                    val distToHandle = (dotPt - (centerPt + coercedOffset)).getDistance()
                    
                    val dotColor = when {
                        distToHandle < 100f -> Color(0xFF33B5E5).copy(alpha = 0.9f)
                        distToHandle < 180f -> Color(0xFF33B5E5).copy(alpha = 0.5f)
                        else -> Color.White.copy(alpha = 0.25f)
                    }
                    val dotRadius = if (distToHandle < 100f) 5f else 3f
                    
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = dotPt
                    )
                }

                // 3. Line track connecting center to active handle drag offset
                if (isDragging) {
                    drawLine(
                        color = Color(0x4433B5E5),
                        start = centerPt,
                        end = centerPt + coercedOffset,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Left Target - Camera
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCameraHighlighted) Color(0xCC33B5E5) else Color(0x33000000)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isCameraHighlighted) Color(0xFF33B5E5) else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Swipe lock left for Camera",
                    tint = if (isCameraHighlighted) Color.White else Color.White.copy(alpha = if (isDragging) 0.8f else 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Right Target - Unlock
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-16).dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlockHighlighted) Color(0xCC33B5E5) else Color(0x33000000)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isUnlockHighlighted) Color(0xFF33B5E5) else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Swipe lock right to Unlock",
                    tint = if (isUnlockHighlighted) Color.White else Color.White.copy(alpha = if (isDragging) 0.8f else 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Top Target - Google Search / Browser
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSearchHighlighted) Color(0xCC33B5E5) else Color(0x33000000)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSearchHighlighted) Color(0xFF33B5E5) else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Swipe lock up for Google Search",
                    tint = if (isSearchHighlighted) Color.White else Color.White.copy(alpha = if (isDragging) 0.8f else 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Center Ring Handle
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            with(density) { coercedOffset.x.toDp().roundToPx() },
                            with(density) { coercedOffset.y.toDp().roundToPx() }
                        )
                    }
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF23232C), Color(0xFF101014)),
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = if (isDragging) Color(0xFF33B5E5) else Color.White.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDragEnd = {
                                if (isUnlockHighlighted) {
                                    viewModel.unlock()
                                } else if (isCameraHighlighted) {
                                    viewModel.unlock()
                                    viewModel.launchApp(AppType.CAMERA)
                                } else if (isSearchHighlighted) {
                                    viewModel.unlock()
                                    viewModel.launchApp(AppType.BROWSER)
                                }
                                dragOffset = Offset.Zero
                                isDragging = false
                            },
                            onDragCancel = {
                                dragOffset = Offset.Zero
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUnlockHighlighted) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = "Drag Padlock Key",
                    tint = if (isDragging) Color(0xFF33B5E5) else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun JellyBeanBatteryWidget(
    level: Int,
    isCharging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(145.dp)
            .height(105.dp)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x990A0A0C)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Classic 4.1 battery vertical icon
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer shell
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Draw battery tip (top)
                    val tipHeight = 5.dp.toPx()
                    val tipWidth = w * 0.4f
                    drawRect(
                        color = Color(0xFFCCCCCC),
                        topLeft = Offset((w - tipWidth) / 2f, 0f),
                        size = Size(tipWidth, tipHeight)
                    )
                    
                    // Draw battery outer body
                    drawRect(
                        color = Color(0xFFCCCCCC),
                        topLeft = Offset(0f, tipHeight),
                        size = Size(w, h - tipHeight),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    // Draw fill inside
                    val fillMaxHeight = h - tipHeight - 6.dp.toPx()
                    val fillHeight = fillMaxHeight * (level / 100f)
                    val fillWidth = w - 6.dp.toPx()
                    val fillColor = if (isCharging) Color(0xFF99CC00) else if (level <= 15) Color(0xFFFF4444) else Color(0xFF33B5E5)
                    
                    if (fillHeight > 0) {
                        drawRect(
                            color = fillColor,
                            topLeft = Offset(3.dp.toPx(), tipHeight + 3.dp.toPx() + (fillMaxHeight - fillHeight)),
                            size = Size(fillWidth, fillHeight)
                        )
                    }
                }
                
                // Lightning bolt icon if charging
                if (isCharging) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Cargando",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Percentage and info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$level%",
                    color = if (isCharging) Color(0xFF99CC00) else Color(0xFF33B5E5),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = if (isCharging) "CARGANDO" else "BATERÍA",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 3. Classic Jelly Bean Desktop Launcher with Icons and Search Box
@Composable
fun JellyBeanDesktop(viewModel: JellyBeanViewModel) {
    val isFolderOpen by viewModel.isGoogleFolderOpen.collectAsState()
    val isClockActive by viewModel.isAnalogClockWidgetActive.collectAsState()
    val widgetOffset by viewModel.analogClockWidgetOffset.collectAsState()
    val isBatteryWidgetActive by viewModel.isBatteryWidgetActive.collectAsState()
    val batteryWidgetOffset by viewModel.batteryWidgetOffset.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isBatteryCharging by viewModel.isBatteryCharging.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isClockActive) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            widgetOffset.x.toInt(),
                            widgetOffset.y.toInt()
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.setAnalogClockWidgetOffset(
                                androidx.compose.ui.geometry.Offset(
                                    widgetOffset.x + dragAmount.x,
                                    widgetOffset.y + dragAmount.y
                                )
                            )
                        }
                    }
                    .size(130.dp)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                JellyBeanAnalogClock(modifier = Modifier.fillMaxSize())
            }
        }

        if (isBatteryWidgetActive) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            batteryWidgetOffset.x.toInt(),
                            batteryWidgetOffset.y.toInt()
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.setBatteryWidgetOffset(
                                androidx.compose.ui.geometry.Offset(
                                    batteryWidgetOffset.x + dragAmount.x,
                                    batteryWidgetOffset.y + dragAmount.y
                                )
                            )
                        }
                    }
                    .background(Color.Transparent)
            ) {
                JellyBeanBatteryWidget(
                    level = batteryLevel,
                    isCharging = isBatteryCharging,
                    onClick = {
                        viewModel.launchApp(AppType.SETTINGS)
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
        ) {
            // Retro Google Search Bar Widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .height(48.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                    .clickable { viewModel.launchApp(AppType.BROWSER) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Google",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Google Speech Mic retro Search",
                        tint = Color(0xFF33B5E5),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Digital Clock + Dynamic Weather desktop widget
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val time by viewModel.currentTime.collectAsState()
                val date by viewModel.currentDate.collectAsState()

                Text(
                    text = time,
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "$date | 24°C Despejado",
                    color = Color(0xFF33B5E5),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DesktopIconItem(
                    app = AppType.CAMERA,
                    onClick = { viewModel.launchApp(AppType.CAMERA) }
                )

                DesktopIconItem(
                    app = AppType.AI_GENERATOR,
                    onClick = { viewModel.launchApp(AppType.AI_GENERATOR) }
                )

                GoogleAppsFolderIcon(onClick = { viewModel.openGoogleFolder() })
            }

            Spacer(modifier = Modifier.weight(1f))

            // Holo Dock Separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF33B5E5), Color.Transparent)
                        )
                    )
            )

            // Bottom dock containing essential shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x06FFFFFF),
                                Color(0x16FFFFFF),
                                Color(0x02FFFFFF)
                            )
                        )
                    )
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Phone (Teléfono)
                DockIconItem(
                    app = AppType.PHONE,
                    onClick = { viewModel.launchApp(AppType.PHONE) }
                )

                // 2. Messages (Mensajes)
                DockIconItem(
                    app = AppType.MESSAGING,
                    onClick = { viewModel.launchApp(AppType.MESSAGING) }
                )

                // 3. App Drawer (Cajón de aplicaciones) with custom animations
                AnimatedDockItemContainer(onClick = { viewModel.openDrawer() }) { isPressed ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (isPressed) {
                                        listOf(Color(0xFF33B5E5).copy(alpha = 0.5f), Color(0xFF102A35))
                                    } else {
                                        listOf(Color(0x33FFFFFF), Color(0x11FFFFFF))
                                    }
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPressed) Color(0xFF33B5E5) else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row {
                                Box(modifier = Modifier.size(5.dp).background(if (isPressed) Color(0xFF33B5E5) else Color.White))
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(modifier = Modifier.size(5.dp).background(if (isPressed) Color(0xFF33B5E5) else Color.White))
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(modifier = Modifier.size(5.dp).background(if (isPressed) Color(0xFF33B5E5) else Color.White))
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row {
                                Box(modifier = Modifier.size(5.dp).background(if (isPressed) Color(0xFF33B5E5) else Color.White))
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(modifier = Modifier.size(5.dp).background(if (isPressed) Color(0xFF33B5E5) else Color.White))
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(modifier = Modifier.size(5.dp).background(if (isPressed) Color(0xFF33B5E5) else Color.White))
                            }
                        }
                    }
                }

                // 4. Browser (Navegador)
                DockIconItem(
                    app = AppType.BROWSER,
                    onClick = { viewModel.launchApp(AppType.BROWSER) }
                )

                // 5. Camera (Cámara)
                DockIconItem(
                    app = AppType.CAMERA,
                    onClick = { viewModel.launchApp(AppType.CAMERA) }
                )
            }
        }

        if (isFolderOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable { viewModel.closeGoogleFolder() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(280.dp)
                        .clickable(enabled = false) {},
                    colors = CardDefaults.cardColors(containerColor = Color(0xE00D0D0D)),
                    border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Google Apps",
                            color = Color(0xFF33B5E5),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        GridFolderApps(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleAppsFolderIcon(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .drawBehind {
                    drawCircle(
                        color = Color(0x33FFFFFF),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0x1A33B5E5),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Google Apps",
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GridFolderApps(viewModel: JellyBeanViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FolderAppItem(app = AppType.MAPS, onClick = { viewModel.launchApp(AppType.MAPS) })
        FolderAppItem(app = AppType.MARKET, onClick = { viewModel.launchApp(AppType.MARKET) })
        FolderAppItem(app = AppType.SETTINGS, onClick = { viewModel.launchApp(AppType.SETTINGS) })
    }
}

@Composable
fun FolderAppItem(app: AppType, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        JellyBeanAppIcon(
            app = app,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.appName,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// 5. App Drawer grid listing all Apps
@Composable
fun JellyBeanAppDrawer(viewModel: JellyBeanViewModel) {
    val installedApps by viewModel.installedApps.collectAsState()
    val activeTab by viewModel.activeAppDrawerTab.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFA000000))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "APLICACIONES",
                color = if (activeTab == "APPS") Color(0xFF33B5E5) else Color(0x66FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .holoTap(shape = RoundedCornerShape(4.dp), onClick = { viewModel.setActiveAppDrawerTab("APPS") })
                    .drawBehind {
                        if (activeTab == "APPS") {
                            drawLine(
                                color = Color(0xFF33B5E5),
                                start = Offset(0f, size.height + 8.dp.toPx()),
                                end = Offset(size.width, size.height + 8.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    .padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "WIDGETS",
                color = if (activeTab == "WIDGETS") Color(0xFF33B5E5) else Color(0x66FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .holoTap(shape = RoundedCornerShape(4.dp), onClick = { viewModel.setActiveAppDrawerTab("WIDGETS") })
                    .drawBehind {
                        if (activeTab == "WIDGETS") {
                            drawLine(
                                color = Color(0xFF33B5E5),
                                start = Offset(0f, size.height + 8.dp.toPx()),
                                end = Offset(size.width, size.height + 8.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { viewModel.launchApp(AppType.MARKET) }) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = "Ir a Play Store",
                    tint = Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        if (activeTab == "APPS") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(installedApps.toList()) { app ->
                    AppDrawerGridItem(app = app, onClick = { viewModel.launchApp(app) })
                }
            }
        } else {
            // Widgets Tab
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    "Toca un widget para añadirlo o quitarlo del escritorio principal:",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val isClockActive by viewModel.isAnalogClockWidgetActive.collectAsState()
                val isBatteryActive by viewModel.isBatteryWidgetActive.collectAsState()
                val batteryLevel by viewModel.batteryLevel.collectAsState()
                val isBatteryCharging by viewModel.isBatteryCharging.collectAsState()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Analog Clock widget item card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clickable {
                                val nextState = !isClockActive
                                viewModel.setAnalogClockWidgetActive(nextState)
                                viewModel.playSystemSound("tick")
                                if (nextState) {
                                    viewModel.postNotification(
                                        "Ajustes de Inicio",
                                        "Widget de reloj analógico añadido al escritorio",
                                        AppType.CLOCK
                                    )
                                } else {
                                    viewModel.postNotification(
                                        "Ajustes de Inicio",
                                        "Widget de reloj analógico quitado del escritorio",
                                        AppType.CLOCK
                                    )
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isClockActive) Color(0x2233B5E5) else Color(0xFF161616)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isClockActive) Color(0xFF33B5E5) else Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mini Analog Clock preview
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                JellyBeanAnalogClock(modifier = Modifier.fillMaxSize())
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Reloj Analógico",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isClockActive) "ACTIVO (Toca para quitar)" else "INACTIVO (Toca para añadir)",
                                    color = if (isClockActive) Color(0xFF33B5E5) else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Static Search Bar widget preview card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mini Google search preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .background(Color(0x11FFFFFF), RoundedCornerShape(2.dp))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Google",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color(0xFF33B5E5),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Buscador de Google",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "FIJO EN INICIO",
                                    color = Color(0xFF99CC00),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clickable {
                                val nextState = !isBatteryActive
                                viewModel.setBatteryWidgetActive(nextState)
                                viewModel.playSystemSound("tick")
                                if (nextState) {
                                    viewModel.postNotification(
                                        "Ajustes de Inicio",
                                        "Widget de batería añadido al escritorio",
                                        AppType.SETTINGS
                                    )
                                } else {
                                    viewModel.postNotification(
                                        "Ajustes de Inicio",
                                        "Widget de batería quitado del escritorio",
                                        AppType.SETTINGS
                                    )
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBatteryActive) Color(0x2233B5E5) else Color(0xFF161616)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isBatteryActive) Color(0xFF33B5E5) else Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mini Battery preview
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val tipHeight = 4.dp.toPx()
                                        val tipWidth = w * 0.4f
                                        drawRect(
                                            color = Color(0xFFCCCCCC),
                                            topLeft = Offset((w - tipWidth) / 2f, 0f),
                                            size = Size(tipWidth, tipHeight)
                                        )
                                        drawRect(
                                            color = Color(0xFFCCCCCC),
                                            topLeft = Offset(0f, tipHeight),
                                            size = Size(w, h - tipHeight),
                                            style = Stroke(width = 1.5.dp.toPx())
                                        )
                                        val fillMaxH = h - tipHeight - 4.dp.toPx()
                                        val fillH = fillMaxH * (batteryLevel / 100f)
                                        val fillW = w - 4.dp.toPx()
                                        drawRect(
                                            color = if (isBatteryCharging) Color(0xFF99CC00) else Color(0xFF33B5E5),
                                            topLeft = Offset(2.dp.toPx(), tipHeight + 2.dp.toPx() + (fillMaxH - fillH)),
                                            size = Size(fillW, fillH)
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Control de Batería",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBatteryActive) "ACTIVO (Toca para quitar)" else "INACTIVO (Toca para añadir)",
                                    color = if (isBatteryActive) Color(0xFF33B5E5) else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AppDrawerGridItem(app: AppType, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .holoTap(shape = RoundedCornerShape(8.dp), onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        JellyBeanAppIcon(
            app = app,
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.appName,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// 6. Sliding Notification Curtain
@Composable
fun NotificationShade(viewModel: JellyBeanViewModel) {
    val date by viewModel.currentDate.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val wifiEnabled by viewModel.isWifiEnabled.collectAsState()
    val bluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val locationEnabled by viewModel.isLocationEnabled.collectAsState()
    val dataEnabled by viewModel.isDataEnabled.collectAsState()

    val isQuickSettingsActive by viewModel.isQuickSettingsActive.collectAsState()
    val systemBrightness by viewModel.systemBrightness.collectAsState()
    val soundProfile by viewModel.soundProfile.collectAsState()
    val airplaneMode by viewModel.isAirplaneModeEnabled.collectAsState()
    val ownerName by viewModel.ownerName.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { viewModel.toggleNotificationShade() },
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f) // Expanded height to comfortably fit 9 tiles on compact screens
                .clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = Color(0xFB0F0F16)),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF33B5E5))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = currentTime,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            text = date.uppercase(),
                            color = Color(0xFF33B5E5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.toggleQuickSettingsMode() }) {
                            Icon(
                                imageVector = if (isQuickSettingsActive) Icons.Default.List else Icons.Default.Settings,
                                contentDescription = if (isQuickSettingsActive) "Ver Notificaciones" else "Ver Accesos Rápidos",
                                tint = Color(0xFF33B5E5)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Vintage Holo Back button to close the notification shade
                        Button(
                            onClick = { viewModel.toggleNotificationShade() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                            border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Cerrar",
                                    tint = Color(0xFF33B5E5),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "ATRÁS",
                                    color = Color(0xFF33B5E5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF33B5E5).copy(alpha = 0.25f))
                )

                if (isQuickSettingsActive) {
                    QuickSettingsGrid(
                        viewModel = viewModel,
                        wifiEnabled = wifiEnabled,
                        bluetoothEnabled = bluetoothEnabled,
                        systemBrightness = systemBrightness,
                        soundProfile = soundProfile,
                        airplaneMode = airplaneMode,
                        ownerName = ownerName,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF07070B))
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ShadeToggleItem(
                            icon = Icons.Default.Wifi,
                            label = "Wi-Fi",
                            active = wifiEnabled,
                            onToggle = { viewModel.toggleWifi() }
                        )
                        ShadeToggleItem(
                            icon = Icons.Default.Bluetooth,
                            label = "Bluetooth",
                            active = bluetoothEnabled,
                            onToggle = { viewModel.toggleBluetooth() }
                        )
                        ShadeToggleItem(
                            icon = Icons.Default.GpsFixed,
                            label = "GPS",
                            active = locationEnabled,
                            onToggle = { viewModel.toggleLocation() }
                        )
                        ShadeToggleItem(
                            icon = Icons.Default.SignalCellular4Bar,
                            label = "Datos",
                            active = dataEnabled,
                            onToggle = { viewModel.toggleData() }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.12f))
                    )

                    if (notifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No hay notificaciones recientes.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(notifications) { item ->
                                NotificationRowItem(item, viewModel)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { viewModel.clearAllNotifications() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15151A)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5))
                            ) {
                                Text(text = "Borrar Todo", color = Color(0xFF33B5E5), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickSettingsGrid(
    viewModel: JellyBeanViewModel,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    systemBrightness: Float,
    soundProfile: String,
    airplaneMode: Boolean,
    ownerName: String,
    modifier: Modifier = Modifier
) {
    var showBrightnessDialog by remember { mutableStateOf(false) }
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isBatteryCharging by viewModel.isBatteryCharging.collectAsState()

    if (showBrightnessDialog) {
        AlertDialog(
            onDismissRequest = { showBrightnessDialog = false },
            title = {
                Text(
                    text = "Brillo de Pantalla", 
                    color = Color(0xFF33B5E5), 
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.BrightnessLow, contentDescription = null, tint = Color.Gray)
                        Text(
                            text = "${(systemBrightness * 100).toInt()}%", 
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(imageVector = Icons.Default.BrightnessHigh, contentDescription = null, tint = Color(0xFF33B5E5))
                    }
                    Slider(
                        value = systemBrightness,
                        onValueChange = { viewModel.setSystemBrightness(it) },
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF33B5E5),
                            thumbColor = Color(0xFF33B5E5),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrightnessDialog = false }) {
                    Text("Aceptar", color = Color(0xFF33B5E5))
                }
            },
            containerColor = Color(0xFF15151A),
            textContentColor = Color.White
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickSettingsTile(
                icon = Icons.Default.Person,
                title = ownerName,
                status = "Usuario",
                active = true,
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            QuickSettingsTile(
                icon = Icons.Default.BrightnessMedium,
                title = "Brillo",
                status = "${(systemBrightness * 100).toInt()}%",
                active = true,
                modifier = Modifier.weight(1f),
                onClick = { showBrightnessDialog = true }
            )
            QuickSettingsTile(
                icon = Icons.Default.Settings,
                title = "Ajustes",
                status = "Sistema",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.launchApp(AppType.SETTINGS)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickSettingsTile(
                icon = Icons.Default.Wifi,
                title = "Wi-Fi",
                status = if (wifiEnabled) "HoloNet_5G" else "Desactivado",
                active = wifiEnabled,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleWifi() }
            )
            QuickSettingsTile(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth",
                status = if (bluetoothEnabled) "Activado" else "Desactivado",
                active = bluetoothEnabled,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleBluetooth() }
            )
            val soundIcon = when (soundProfile) {
                "SOUND" -> Icons.Default.VolumeUp
                "VIBRATE" -> Icons.Default.Vibration
                else -> Icons.Default.VolumeMute
            }
            val soundLabel = when (soundProfile) {
                "SOUND" -> "Sonido"
                "VIBRATE" -> "Vibración"
                else -> "Silencio"
            }
            QuickSettingsTile(
                icon = soundIcon,
                title = "Sonido",
                status = soundLabel,
                active = soundProfile == "SOUND" || soundProfile == "VIBRATE",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleSoundProfile() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickSettingsTile(
                icon = if (isBatteryCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                title = "Batería",
                status = "$batteryLevel%${if (isBatteryCharging) " (Cargando)" else ""}",
                active = true,
                activeColor = if (isBatteryCharging) Color(0xFF99CC00) else Color(0xFF33B5E5),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleBatteryCharging() }
            )
            QuickSettingsTile(
                icon = Icons.Default.AirplanemodeActive,
                title = "Modo Avión",
                status = if (airplaneMode) "Activado" else "Desactivado",
                active = airplaneMode,
                activeColor = Color(0xFFFFB300),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleAirplaneMode() }
            )
            val locationEnabled by viewModel.isLocationEnabled.collectAsState()
            QuickSettingsTile(
                icon = Icons.Default.GpsFixed,
                title = "GPS",
                status = if (locationEnabled) "Activado" else "Desactivado",
                active = locationEnabled,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleLocation() }
            )
        }
    }
}

@Composable
fun QuickSettingsTile(
    icon: ImageVector,
    title: String,
    status: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF33B5E5),
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1B24)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (active) activeColor.copy(alpha = 0.6f) else Color(0x33FFFFFF)
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) activeColor else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = status,
                    color = if (active) activeColor.copy(alpha = 0.85f) else Color.DarkGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ShadeToggleItem(icon: ImageVector, label: String, active: Boolean, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onToggle() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(if (active) Color(0x3333B5E5) else Color(0x19FFFFFF), CircleShape)
                .border(1.dp, if (active) Color(0xFF33B5E5) else Color(0x44FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF33B5E5) else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = if (active) Color(0xFF33B5E5) else Color.Gray, fontSize = 11.sp)
    }
}

@Composable
fun NotificationRowItem(item: AppNotification, viewModel: JellyBeanViewModel) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = Color(0x1C1C24)),
        border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = if (isExpanded) 0.5f else 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (item.appType) {
                    AppType.MESSAGING -> Icons.Default.ChatBubble
                    AppType.CLOCK -> Icons.Default.AccessTime
                    AppType.CALENDAR -> Icons.Default.CalendarToday
                    AppType.SETTINGS -> Icons.Default.Settings
                    AppType.ABOUT -> Icons.Default.Info
                    else -> Icons.Default.Notifications
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sender,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isExpanded) {
                        Text(
                            text = item.message,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Expand / Collapse Chevron
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Dismiss Button
                IconButton(
                    onClick = { viewModel.dismissNotification(item.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Eliminar",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.message,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 36.dp, end = 4.dp, bottom = 8.dp)
                )

                // Quick action buttons in Holo Blue styling
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Open/Launch the main app associated with this notification
                    OutlinedButton(
                        onClick = {
                            viewModel.launchApp(item.appType)
                            viewModel.toggleNotificationShade()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF33B5E5)),
                        border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = "ABRIR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Contextual quick actions matching the specific app type
                    when (item.appType) {
                        AppType.MESSAGING -> {
                            OutlinedButton(
                                onClick = {
                                    viewModel.launchApp(AppType.MESSAGING)
                                    viewModel.toggleNotificationShade()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF33B5E5)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(text = "RESPONDER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.launchApp(AppType.PHONE)
                                    viewModel.toggleNotificationShade()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF33B5E5)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(text = "LLAMAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        AppType.CLOCK -> {
                            OutlinedButton(
                                onClick = {
                                    viewModel.launchApp(AppType.CLOCK)
                                    viewModel.toggleNotificationShade()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF33B5E5)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(text = "VER ALARMAS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        AppType.CALENDAR -> {
                            OutlinedButton(
                                onClick = {
                                    viewModel.launchApp(AppType.CALENDAR)
                                    viewModel.toggleNotificationShade()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF33B5E5)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(text = "VER AGENDA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        AppType.SETTINGS -> {
                            OutlinedButton(
                                onClick = {
                                    viewModel.launchApp(AppType.SETTINGS)
                                    viewModel.toggleNotificationShade()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF33B5E5)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(text = "CONFIGURAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// ==========================================
// CENTRAL APPLICATION WINDOW CONTAINER
// ==========================================
@Composable
fun JellyBeanAppWindow(app: AppType, viewModel: JellyBeanViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF0F0F0F))
                    .drawBehind {
                        drawLine(
                            color = Color(0xFF33B5E5),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.closeActiveApp() }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retro Back icon", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = app.appName,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF000000))
            ) {
                when (app) {
                    AppType.PHONE -> DialerActivity(viewModel)
                    AppType.PEOPLE -> PeopleActivity(viewModel)
                    AppType.BROWSER -> BrowserActivity(viewModel)
                    AppType.MESSAGING -> MessagingActivity(viewModel)
                    AppType.CAMERA -> AISpecialCameraActivity(viewModel)
                    AppType.GALLERY -> GalleryActivity(viewModel)
                    AppType.CALCULATOR -> CalculatorActivity(viewModel)
                    AppType.SETTINGS -> SettingsActivity(viewModel)
                    AppType.MAPS -> RetroMapsActivity(viewModel)
                    AppType.MARKET -> RetroMarketActivity(viewModel)
                    AppType.CLOCK -> RetroClockActivity(viewModel)
                    AppType.MUSIC -> RetroMusicActivity(viewModel)
                    AppType.CALENDAR -> RetroCalendarActivity(viewModel)
                    AppType.ABOUT -> AboutJellyBeanActivity(viewModel)
                    AppType.FILE_MANAGER -> FileManagerActivity(viewModel)
                    AppType.THEMES -> ThemesActivity(viewModel)
                    AppType.AI_GENERATOR -> AIGeneratorActivity(viewModel)
                    AppType.SOUND_RECORDER -> SoundRecorderActivity(viewModel)
                    AppType.DOWNLOADS -> DownloadsActivity(viewModel)
                    AppType.NOTES -> NotesActivity(viewModel)
                    AppType.RADIO -> RadioActivity(viewModel)
                }
            }
        }
    }
}

// ==========================================
// SUB-ACTIVITIES IMPLEMENTATIONS (HOLO STYLE)
// ==========================================

@Composable
fun DialerActivity(viewModel: JellyBeanViewModel) {
    var dialString by remember { mutableStateOf("") }
    var inCall by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Teclado") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161616))
    ) {
        // Top Navigation Tabs (Image 5)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
        ) {
            val tabs = listOf("Teclado", "Recientes", "Favoritos", "Contactos")
            tabs.forEach { tab ->
                val isSelected = activeTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = tab }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color(0xFF33B5E5) else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(2.dp)
                            .background(if (isSelected) Color(0xFF33B5E5) else Color.Transparent)
                    )
                }
            }
        }

        if (!inCall) {
            if (activeTab == "Teclado") {
                // Dialer Display Screen (Image 5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F0F))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dialString.ifEmpty { "Introduce número" },
                        color = if (dialString.isEmpty()) Color.DarkGray else Color.White,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (dialString.isNotEmpty()) {
                        IconButton(
                            onClick = { dialString = "" },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Limpiar todo", tint = Color.Gray)
                        }
                    }
                }

                Divider(color = Color(0xFF2B2B2B), thickness = 1.dp)

                // Keypad grid (Image 5)
                val keys = listOf(
                    listOf("1" to "", "2" to "A B C", "3" to "D E F"),
                    listOf("4" to "G H I", "5" to "J K L", "6" to "M N O"),
                    listOf("7" to "P Q R S", "8" to "T U V", "9" to "W X Y Z"),
                    listOf("*" to "", "0" to "+", "#" to "")
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    keys.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEach { (num, alpha) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.6f)
                                        .background(Color(0xFF1C1C1C))
                                        .clickable { dialString += num }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = num,
                                            color = Color.White,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                        if (alpha.isNotEmpty()) {
                                            Text(
                                                text = alpha,
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Normal,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Call Dock (Image 5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222222))
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (dialString.isNotEmpty()) dialString = dialString.dropLast(1) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Backspace, contentDescription = "Borrar", tint = Color.LightGray)
                    }

                    Box(
                        modifier = Modifier
                            .size(70.dp, 44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF669900))
                            .clickable { if (dialString.isNotEmpty()) inCall = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Llamar", tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    IconButton(
                        onClick = {
                            if (dialString.isNotEmpty()) {
                                activeTab = "Contactos"
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Añadir Contacto", tint = Color.LightGray)
                    }
                }
            } else {
                // Secondary tabs contents
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Historial y Contactos Jelly Bean",
                        color = Color(0xFF33B5E5),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    if (activeTab == "Contactos") {
                        val contacts = listOf("Abuela", "Carlos Gómez", "Lyoneidas", "Mamá", "Pedro Martínez", "Asistente AI (Gemini)")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(contacts) { person ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF33B5E5))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(text = person, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Móvil", color = Color.LightGray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No hay llamadas recientes ni favoritos en esta simulación.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 40.dp)
                        )
                    }
                }
            }
        } else {
            // Calling state UI
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = dialString, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Llamando por simulación...", color = Color.Gray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(80.dp))

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0xFFCC0000), CircleShape)
                        .clickable { inCall = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Colgar", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun PeopleActivity(viewModel: JellyBeanViewModel) {
    val contacts = listOf("Abuela", "Carlos Gómez", "Lyoneidas", "Mamá", "Pedro Martínez", "Asistente AI (Gemini)")
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(contacts) { person ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.selectChat(person)
                        viewModel.launchApp(AppType.MESSAGING)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF33B5E5).copy(alpha = 0.2f), CircleShape)
                        .border(1.dp, Color(0xFF33B5E5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = person.take(1), color = Color(0xFF33B5E5), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = person, color = Color.White, fontSize = 16.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
    }
}

@Composable
fun BrowserActivity(viewModel: JellyBeanViewModel) {
    var urlInput by remember { mutableStateOf("https://www.wikipedia.org") }
    var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF151515))) {
        // Top browser URL bar (replicating 2012 Holo styled top bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151515))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back/Forward Navigation
            IconButton(
                onClick = {
                    webViewInstance?.goBack()
                },
                enabled = canGoBack,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Back",
                    tint = if (canGoBack) Color(0xFF33B5E5) else Color.DarkGray,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    webViewInstance?.goForward()
                },
                enabled = canGoForward,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Forward",
                    tint = if (canGoForward) Color(0xFF33B5E5) else Color.DarkGray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // URL input field
            BasicTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color(0xFF33B5E5)),
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Ir / Go button (Arrow icon)
            IconButton(
                onClick = {
                    val target = urlInput.trim()
                    if (target.isNotEmpty()) {
                        val formattedUrl = if (target.startsWith("http://") || target.startsWith("https://")) {
                            target
                        } else if (target.contains(".") && !target.contains(" ")) {
                            "https://$target"
                        } else {
                            "https://www.google.com/search?q=${java.net.URLEncoder.encode(target, "UTF-8")}"
                        }
                        urlInput = formattedUrl
                        webViewInstance?.loadUrl(formattedUrl)
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Go",
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = {
                    webViewInstance?.reload()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Real WebView View
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                android.webkit.WebView(context).apply {
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            url?.let { urlInput = it }
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            url?.let { urlInput = it }
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(urlInput)
                    webViewInstance = this
                }
            },
            update = { webView ->
                webViewInstance = webView
            },
            modifier = Modifier.fillMaxSize().weight(1f)
        )
    }
}

@Composable
fun OldBrowserActivity(viewModel: JellyBeanViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val resultText by viewModel.searchResultText.collectAsState()
    val sources by viewModel.searchSources.collectAsState()

    var currentUrl by remember { mutableStateOf("www.androidpolice.com") }
    var inSearchMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFE5E5E5))) {
        // Top Browser Navigation Bar (Identical to Image 6)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151515))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / Favicon (pink-blue globe like Image 6)
            Canvas(modifier = Modifier.size(20.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00B0FF), Color(0xFFE040FB))
                    ),
                    radius = size.width / 2f
                )
                // Globe grid lines
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(0f, size.height/2f), end = Offset(size.width, size.height/2f))
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(size.width/2f, 0f), end = Offset(size.width/2f, size.height))
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Address/URL TextField
            BasicTextField(
                value = if (inSearchMode) query else currentUrl,
                onValueChange = {
                    inSearchMode = true
                    viewModel.updateSearchQuery(it)
                },
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color(0xFF33B5E5)),
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Stacked Cards Tab Switcher Icon (Image 6)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(1.5.dp, Color.White, RoundedCornerShape(2.dp))
                    .clickable {
                        inSearchMode = false
                        currentUrl = "www.androidpolice.com"
                        viewModel.updateSearchQuery("")
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    Box(modifier = Modifier.size(14.dp, 2.dp).background(Color.White))
                    Box(modifier = Modifier.size(14.dp, 2.dp).background(Color.White))
                    Box(modifier = Modifier.size(14.dp, 2.dp).background(Color.White))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action button (Search / More)
            IconButton(
                onClick = {
                    if (inSearchMode && query.isNotEmpty()) {
                        viewModel.performSearch()
                    } else {
                        inSearchMode = false
                        currentUrl = "www.androidpolice.com"
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (inSearchMode) Icons.Default.Search else Icons.Default.MoreVert,
                    contentDescription = "Opciones",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Web Content View
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF33B5E5))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Cargando página...", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else if (inSearchMode && resultText.isNotEmpty()) {
            // Google AI Search Result View
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111))
                    .padding(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x19FFFFFF)),
                        border = BorderStroke(1.dp, Color(0xFF33B5E5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF33B5E5))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Búsqueda Google AI", color = Color(0xFF33B5E5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = resultText, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
                
                if (sources.isNotEmpty()) {
                    item {
                        Text(text = "Enlaces encontrados:", color = Color(0xFF33B5E5), fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    items(sources) { webSource ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = webSource.title ?: "Página Web", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = webSource.uri ?: "", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else {
            // AUTHENTIC MOCKUP OF IMAGE 6 (www.androidpolice.com in 2012)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // 1. Android Police Header Logo Banner
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F2537))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "ANDROID POLICE",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = "LOOKING AFTER ALL THINGS ANDROID",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Little green Android robot controller
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF99CC00), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("AP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. Nav categories bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E354A))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val tags = listOf("News", "Tutorials", "Tips", "Reviews", "Mods", "Apps/Games", "Devices", "About")
                        tags.forEach { tag ->
                            Text(
                                text = tag,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3. Toyota 2012 Corolla Advert
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0F0F0))
                            .padding(8.dp)
                            .border(1.dp, Color(0xFFDCDCDC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 24.dp)
                                    .background(Color(0xFF33B5E5), RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("TOYOTA", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("2012 Toyota Corolla", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Start at value. Arrive at valuable.", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                    }
                }

                // 4. Main Article
                item {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Book Giveaway #14: Use Your Web Developing Skills To Create Android Apps With \"Building Android Apps With HTML, CSS, And JavaScript\"",
                            color = Color(0xFF1A0DAB),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Posted by Artem Russakovskii",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "You're already a web developer, master of HTML, CSS, and JavaScript. You have a great idea for an Android App, but your particular skill set doesn't help you create that app. Or does it? O'Reilly media just released a 176 page 2nd edition of \"Building Android Apps With HTML, CSS, And JavaScript\" by Jonathan Stark and Brian Albers that explains how to do just that.",
                            color = Color.Black,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Book cover representation with turkey bird (Image 6)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(Color(0xFFFDFDFD))
                                .border(1.dp, Color(0xFFCCCCCC))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // O'Reilly Book Design
                                Box(
                                    modifier = Modifier
                                        .width(95.dp)
                                        .fillMaxHeight()
                                        .background(Color.White)
                                        .border(1.5.dp, Color.Black)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                        Text("O'REILLY", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Bird Drawing
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(55.dp)
                                                .border(0.5.dp, Color.Gray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.size(35.dp)) {
                                                drawOval(
                                                    color = Color(0xFF8D6E63),
                                                    topLeft = Offset(5f, 15f),
                                                    size = Size(25.dp.toPx(), 18.dp.toPx())
                                                )
                                                drawLine(
                                                    color = Color.Black,
                                                    start = Offset(5f, 22f),
                                                    end = Offset(-5f, 24f),
                                                    strokeWidth = 2f
                                                )
                                                drawLine(color = Color.Black, start = Offset(15f, 30f), end = Offset(12f, 40f), strokeWidth = 2f)
                                                drawLine(color = Color.Black, start = Offset(25f, 30f), end = Offset(28f, 40f), strokeWidth = 2f)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Building Android Apps",
                                            color = Color.Black,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 9.sp
                                        )
                                        Text(
                                            text = "with HTML, CSS, & JS",
                                            color = Color.Gray,
                                            fontSize = 6.sp,
                                            lineHeight = 7.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "O'Reilly Media",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "¡Sorteo Activo!",
                                        color = Color(0xFF669900),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Comenta abajo para participar por una copia física gratis de este libro.",
                                        color = Color.DarkGray,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
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
fun MessagingActivity(viewModel: JellyBeanViewModel) {
    val conversations by viewModel.smsConversations.collectAsState()
    val selectedChat by viewModel.selectedChat.collectAsState()
    var messageText by remember { mutableStateOf("") }

    if (selectedChat == null) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(conversations.keys.toList()) { chatName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectChat(chatName) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF99CC00).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFF99CC00), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = chatName.take(1), color = Color(0xFF99CC00), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = chatName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = conversations[chatName]?.lastOrNull()?.text ?: "",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.selectChat(null) }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close chat", tint = Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = selectedChat!!, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            val msgs = conversations[selectedChat] ?: emptyList()
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(msgs) { msg ->
                    val bubbleColor = if (msg.isUser) Color(0xFF1D1B28) else Color(0x3333B5E5)
                    val alignment = if (msg.isUser) Alignment.End else Alignment.Start

                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bubbleColor),
                            border = BorderStroke(1.dp, if (msg.isUser) Color(0x33FFFFFF) else Color(0x3333B5E5))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = msg.sender, color = if (msg.isUser) Color.White else Color(0xFF33B5E5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = msg.text, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070707))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text(text = "Escribe un mensaje de texto...", color = Color.Gray, fontSize = 13.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF15151A),
                        unfocusedContainerColor = Color(0xFF15151A),
                        focusedIndicatorColor = Color(0xFF33B5E5)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.sendSMS(messageText)
                        messageText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15151F)),
                    border = BorderStroke(1.dp, Color(0xFF33B5E5))
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Enviar", tint = Color(0xFF33B5E5))
                }
            }
        }
    }
}

@Composable
fun AISpecialCameraActivity(viewModel: JellyBeanViewModel) {
    val prompt by viewModel.cameraPrompt.collectAsState()
    val size by viewModel.cameraSelectedSize.collectAsState()
    val loading by viewModel.cameraGenerating.collectAsState()
    val errorMsg by viewModel.cameraError.collectAsState()
    val rollHistory by viewModel.galleryImages.collectAsState()

    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    var cameraPermissionGranted by remember { mutableStateOf(false) }

    if (showCameraPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDialog = false },
            title = { Text("Permiso de Cámara", color = Color(0xFF33B5E5)) },
            text = { Text("La aplicación de cámara requiere acceso al hardware de la cámara del teléfono para capturar y revelar imágenes.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        cameraPermissionGranted = true
                        showCameraPermissionDialog = false
                        viewModel.postNotification("Cámara", "Permiso de cámara concedido", AppType.CAMERA)
                        viewModel.generateCameraPhoto()
                    }
                ) {
                    Text("PERMITIR", color = Color(0xFF33B5E5), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCameraPermissionDialog = false
                        viewModel.postNotification("Cámara", "Permiso de cámara denegado", AppType.CAMERA)
                    }
                ) {
                    Text("DENEGAR", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E22)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.DarkGray, RoundedCornerShape(8.dp))
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF33B5E5))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Revelando carrete con IA...", color = Color.White, fontSize = 12.sp)
                }
            } else if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (rollHistory.isNotEmpty()) {
                Image(
                    bitmap = rollHistory.last().asImageBitmap(),
                    contentDescription = "Last Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Camera, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Sensor de Cámara de IA listo", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Resolución:", color = Color.White, fontSize = 12.sp)
            listOf("1K", "2K", "4K").forEach { tag ->
                val active = size == tag
                Box(
                    modifier = Modifier
                        .clickable { viewModel.setCameraSize(tag) }
                        .background(if (active) Color(0x3333B5E5) else Color.Transparent, RoundedCornerShape(4.dp))
                        .border(1.dp, if (active) Color(0xFF33B5E5) else Color.DarkGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = tag, color = if (active) Color(0xFF33B5E5) else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = prompt,
                onValueChange = { viewModel.updateCameraPrompt(it) },
                placeholder = { Text("Escribe qué fotografiar...", color = Color.Gray, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1D1D1D),
                    unfocusedContainerColor = Color(0xFF1D1D1D),
                    focusedIndicatorColor = Color(0xFF33B5E5)
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (!cameraPermissionGranted) {
                        showCameraPermissionDialog = true
                    } else {
                        viewModel.generateCameraPhoto()
                    }
                },
                enabled = !loading,
                modifier = Modifier
                    .size(54.dp)
                    .background(if (loading) Color.DarkGray else Color(0xFF33B5E5), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Capturar", tint = Color.Black)
            }
        }
    }
}

@Composable
fun AIGeneratorActivity(viewModel: JellyBeanViewModel) {
    val prompt by viewModel.aiPrompt.collectAsState()
    val size by viewModel.aiSelectedSize.collectAsState()
    val ratio by viewModel.aiSelectedRatio.collectAsState()
    val loading by viewModel.aiGenerating.collectAsState()
    val errorMsg by viewModel.aiError.collectAsState()
    val aiResult by viewModel.aiResult.collectAsState()

    var showSavedMessage by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Creative Icon and Title Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF33B5E5),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CREADOR DE IMÁGENES IA",
                color = Color(0xFF33B5E5),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Image Canvas Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Color(0xFF151515), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF33B5E5),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Imaginando con IA...",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else if (errorMsg != null) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = Color(0xFFFF4444),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = Color(0xFFFF4444),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (aiResult != null) {
                Image(
                    bitmap = aiResult!!.asImageBitmap(),
                    contentDescription = "AI Generated Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "La obra de arte aparecerá aquí",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions if result exists
        if (aiResult != null && !loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Set as Wallpaper
                Button(
                    onClick = {
                        viewModel.setAsWallpaper(aiResult!!)
                        viewModel.postNotification(
                            "Creador IA",
                            "Fondo de pantalla actualizado con tu imagen de IA",
                            AppType.AI_GENERATOR
                        )
                        showSavedMessage = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33B5E5)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Usar de Fondo", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Saved to Gallery confirmation / indicator
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x3399CC00),
                        disabledContainerColor = Color(0x1199CC00)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF99CC00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardado en Galería", color = Color(0xFF99CC00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Reset Button
                IconButton(
                    onClick = {
                        viewModel.clearAiResult()
                        showSavedMessage = false
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF2E2E32), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Nueva imagen",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Text input field for Prompt
        Text(
            text = "PROMPT DE LA IMAGEN",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        TextField(
            value = prompt,
            onValueChange = { viewModel.updateAiPrompt(it) },
            placeholder = { Text("Escribe una descripción creativa (ej: Un astronauta retro en la luna)...", color = Color.Gray, fontSize = 12.sp) },
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1D1D1D),
                unfocusedContainerColor = Color(0xFF1D1D1D),
                focusedIndicatorColor = Color(0xFF33B5E5),
                unfocusedIndicatorColor = Color.DarkGray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(4.dp),
            singleLine = false,
            maxLines = 3
        )

        // Configuration Row: Aspect Ratio
        Text(
            text = "RELACIÓN DE ASPECTO",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1:1", "16:9", "9:16", "4:3", "3:2").forEach { r ->
                val active = ratio == r
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setAiRatio(r) }
                        .background(
                            if (active) Color(0x3333B5E5) else Color(0xFF1D1D1D),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (active) Color(0xFF33B5E5) else Color.DarkGray,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = r,
                        color = if (active) Color(0xFF33B5E5) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Configuration Row: Resolution Size
        Text(
            text = "RESOLUCIÓN MÁXIMA",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("512px", "1K", "2K").forEach { s ->
                val active = size == s
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setAiSize(s) }
                        .background(
                            if (active) Color(0x3333B5E5) else Color(0xFF1D1D1D),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (active) Color(0xFF33B5E5) else Color.DarkGray,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s,
                        color = if (active) Color(0xFF33B5E5) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Generate Action Button
        Button(
            onClick = { viewModel.generateAIImage() },
            enabled = !loading && prompt.trim().isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF33B5E5),
                disabledContainerColor = Color(0xFF1D1D1D)
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (!loading && prompt.trim().isNotEmpty()) Color.Black else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GENERAR IMAGEN CON IA",
                    color = if (!loading && prompt.trim().isNotEmpty()) Color.Black else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GalleryActivity(viewModel: JellyBeanViewModel) {
    val images by viewModel.galleryImages.collectAsState()
    var selectedImg by remember { mutableStateOf<Bitmap?>(null) }

    if (selectedImg == null) {
        if (images.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Usa la Cámara de IA para tomar fotos.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images) { item ->
                    Image(
                        bitmap = item.asImageBitmap(),
                        contentDescription = "Galería item",
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { selectedImg = item },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = selectedImg!!.asImageBitmap(),
                contentDescription = "Detalle",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        viewModel.setAsWallpaper(selectedImg!!)
                        selectedImg = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1A1A)),
                    border = BorderStroke(1.dp, Color(0xFF33B5E5))
                ) {
                    Text(text = "Fijar Fondo", color = Color(0xFF33B5E5))
                }
                Button(
                    onClick = {
                        viewModel.deleteGalleryImage(selectedImg!!)
                        selectedImg = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2222))
                ) {
                    Text(text = "Eliminar", color = Color.Red)
                }
                Button(
                    onClick = { selectedImg = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
                ) {
                    Text(text = "Atrás", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CalculatorActivity(viewModel: JellyBeanViewModel) {
    val displayVal by viewModel.calcDisplay.collectAsState()
    var formulaHistory by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1F))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Authentic dual-line calculator screen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F12), RoundedCornerShape(4.dp))
                .border(1.5.dp, Color(0xFF33B5E5).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formulaHistory.ifEmpty { " " },
                color = Color.Gray,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayVal,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Holo Scientific Function panel row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("sin", "cos", "tan", "ln").forEach { func ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(Color(0xFF102835), RoundedCornerShape(2.dp))
                        .border(0.5.dp, Color(0xFF33B5E5).copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .clickable {
                            if (displayVal == "0") {
                                viewModel.handleCalcKey(func)
                            } else {
                                formulaHistory = "$func($displayVal)"
                                viewModel.handleCalcKey(func)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = func, color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Standard Keyboard Layout
        val keyboard = listOf(
            listOf("7", "8", "9", "÷", "DEL"),
            listOf("4", "5", "6", "×", "("),
            listOf("1", "2", "3", "-", ")"),
            listOf("C", "0", ".", "+", "=")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            keyboard.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { cell ->
                        val isOp = cell in listOf("÷", "×", "-", "+", "=")
                        val isSpecial = cell in listOf("C", "DEL", "(", ")")
                        
                        val btnBg = when {
                            cell == "=" -> Color(0xFF33B5E5)
                            isOp -> Color(0xFF222830)
                            isSpecial -> Color(0xFF2B2B2B)
                            else -> Color(0xFF3B3B3E)
                        }
                        
                        val btnTint = when {
                            cell == "=" -> Color.Black
                            isOp -> Color(0xFF33B5E5)
                            isSpecial -> Color(0xFFFF9426)
                            else -> Color.White
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(btnBg)
                                .border(1.dp, if (isOp) Color(0xFF33B5E5).copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable {
                                    if (cell == "C") {
                                        formulaHistory = ""
                                        viewModel.handleCalcKey("C")
                                    } else if (cell == "=") {
                                        formulaHistory = displayVal
                                        viewModel.handleCalcKey("=")
                                    } else {
                                        viewModel.handleCalcKey(cell)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cell,
                                color = btnTint,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotesActivity(viewModel: JellyBeanViewModel) {
    val notesList by viewModel.notes.collectAsState()
    var selectedNote by remember { mutableStateOf<NoteItem?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(14.dp)
    ) {
        if (selectedNote == null && !isEditing) {
            // Notes list view
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIS NOTAS HOLO",
                    color = Color(0xFF33B5E5),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Button(
                    onClick = {
                        selectedNote = null
                        editTitle = ""
                        editContent = ""
                        isEditing = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF102835)),
                    border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add note", tint = Color(0xFF33B5E5), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AÑADIR", color = Color(0xFF33B5E5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (notesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No hay notas guardadas", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(notesList) { note ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedNote = note
                                    editTitle = note.title
                                    editContent = note.content
                                    isEditing = true
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, Color(0xFF333333))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind {
                                        // Left neon vertical strip
                                        drawRect(
                                            color = Color(0xFF33B5E5),
                                            topLeft = Offset.Zero,
                                            size = Size(4.dp.toPx(), size.height)
                                        )
                                    }
                                    .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = note.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.content,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = note.date, color = Color.Gray, fontSize = 10.sp)
                                }
                                IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF4444).copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Edit/Create Note Editor
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedNote == null) "NUEVA NOTA" else "EDITAR NOTA",
                    color = Color(0xFFFF9426),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            selectedNote = null
                            isEditing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("CANCELAR", color = Color.White, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            val title = editTitle.ifBlank { "Sin título" }
                            val content = editContent.ifBlank { "" }
                            if (selectedNote == null) {
                                viewModel.addNote(title, content)
                            } else {
                                viewModel.updateNote(selectedNote!!.id, title, content)
                            }
                            selectedNote = null
                            isEditing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF102835)),
                        border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("GUARDAR", color = Color(0xFF33B5E5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = { Text("Título de la nota", color = Color.Gray) },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF33B5E5),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedLabelColor = Color(0xFF33B5E5)
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )

            OutlinedTextField(
                value = editContent,
                onValueChange = { editContent = it },
                label = { Text("Contenido...", color = Color.Gray) },
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF33B5E5),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedLabelColor = Color(0xFF33B5E5)
                ),
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
fun RadioActivity(viewModel: JellyBeanViewModel) {
    var frequency by remember { mutableStateOf(105.5f) }
    var isPlaying by remember { mutableStateOf(false) }
    var signalStrength by remember { mutableStateOf(4) }
    val presets = listOf(88.3f, 91.5f, 95.5f, 98.1f, 102.3f, 105.5f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151515))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App header banner
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Radio, contentDescription = "Radio icon", tint = Color(0xFF33B5E5), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("RADIO FM HOLO", color = Color(0xFF33B5E5), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Simulador de ondas analógicas", color = Color.Gray, fontSize = 11.sp)
            }
        }

        // FM tuning station display card
        Card(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            border = BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isPlaying) "REPRODUCIENDO" else "RADIO APAGADA",
                        color = if (isPlaying) Color(0xFF99CC00) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", frequency),
                        color = if (isPlaying) Color(0xFF33B5E5) else Color.Gray.copy(alpha = 0.6f),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "MHz",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Signal bars
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 1..5) {
                            val active = isPlaying && i <= signalStrength
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height((i * 4).dp)
                                    .background(if (active) Color(0xFF33B5E5) else Color.DarkGray)
                            )
                        }
                    }
                }
            }
        }

        // Interactive Tuner slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("87.5 MHz", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("Búsqueda Manual", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("108.0 MHz", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = frequency,
                onValueChange = {
                    frequency = Math.round(it * 10f) / 10f
                    if (!isPlaying) isPlaying = true
                    signalStrength = (2..5).random()
                },
                valueRange = 87.5f..108.0f,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF33B5E5),
                    inactiveTrackColor = Color(0xFF333333),
                    thumbColor = Color(0xFF33B5E5)
                )
            )
        }

        // Radio control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    if (frequency > 87.5f) {
                        frequency = Math.round((frequency - 0.1f) * 10f) / 10f
                        signalStrength = (2..5).random()
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Tuning left", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(if (isPlaying) Color(0xFF33B5E5) else Color(0xFF2E2E2E), CircleShape)
                    .clickable {
                        isPlaying = !isPlaying
                        if (isPlaying) {
                            viewModel.postNotification("Radio FM", "Sintonizando ${String.format(java.util.Locale.US, "%.1f", frequency)} MHz", AppType.RADIO)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = if (isPlaying) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    if (frequency < 108.0f) {
                        frequency = Math.round((frequency + 0.1f) * 10f) / 10f
                        signalStrength = (2..5).random()
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Tuning right", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        // Favoritos / Presets grid
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("MIS ESTACIONES FAVORITAS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.take(4).forEach { station ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(Color(0xFF222222), RoundedCornerShape(2.dp))
                            .border(1.dp, if (frequency == station && isPlaying) Color(0xFF33B5E5) else Color(0xFF444444), RoundedCornerShape(2.dp))
                            .clickable {
                                frequency = station
                                isPlaying = true
                                signalStrength = (4..5).random()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${station}",
                            color = if (frequency == station && isPlaying) Color(0xFF33B5E5) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

enum class SettingsOption {
    WIFI, BRIGHTNESS, SOUND, USERS, APPS, STORAGE, BATTERY, SCHEDULE, BACKUP, DATETIME, SECURITY, ACCESSIBILITY, ABOUT
}

data class SettingsItem(
    val option: SettingsOption,
    val title: String,
    val subtitle: String,
    val screenState: String
)

data class WifiConnection(val name: String, val signal: Int, val isConnected: Boolean)

@Composable
fun SettingsIconDraw(option: SettingsOption, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minD = size.minDimension

        when (option) {
            SettingsOption.WIFI -> {
                val color = Color(0xFFFF9426)
                drawCircle(color = color, radius = minD * 0.08f, center = Offset(w * 0.5f, h * 0.8f))
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.35f, h * 0.55f),
                    size = Size(w * 0.3f, h * 0.3f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.2f, h * 0.4f),
                    size = Size(w * 0.6f, h * 0.6f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.05f, h * 0.25f),
                    size = Size(w * 0.9f, h * 0.9f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            SettingsOption.BRIGHTNESS -> {
                val color = Color(0xFFDCDCDC)
                drawCircle(color = color, radius = minD * 0.2f, center = Offset(w * 0.5f, h * 0.5f))
                val rayCount = 10
                for (i in 0 until rayCount) {
                    val angle = i * (360.0 / rayCount)
                    val rad = Math.toRadians(angle)
                    val innerX = (w * 0.5f + Math.cos(rad) * (minD * 0.25f)).toFloat()
                    val innerY = (h * 0.5f + Math.sin(rad) * (minD * 0.25f)).toFloat()
                    val outerX = (w * 0.5f + Math.cos(rad) * (minD * 0.42f)).toFloat()
                    val outerY = (h * 0.5f + Math.sin(rad) * (minD * 0.42f)).toFloat()
                    drawLine(
                        color = color,
                        start = Offset(innerX, innerY),
                        end = Offset(outerX, outerY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            SettingsOption.SOUND -> {
                val color = Color(0xFF33B5E5)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.25f, h * 0.38f)
                    lineTo(w * 0.45f, h * 0.38f)
                    lineTo(w * 0.65f, h * 0.2f)
                    lineTo(w * 0.65f, h * 0.8f)
                    lineTo(w * 0.45f, h * 0.62f)
                    lineTo(w * 0.25f, h * 0.62f)
                    close()
                }
                drawPath(path = path, color = color)
                drawArc(
                    color = color,
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.45f, h * 0.3f),
                    size = Size(w * 0.35f, h * 0.4f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            SettingsOption.USERS -> {
                val color = Color(0xFF7CB342)
                drawCircle(
                    color = color,
                    radius = minD * 0.4f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.12f),
                    end = Offset(w * 0.5f, h * 0.88f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.12f, h * 0.5f),
                    end = Offset(w * 0.88f, h * 0.5f),
                    strokeWidth = 2.dp.toPx()
                )
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(w * 0.15f, h * 0.15f),
                    size = Size(w * 0.7f, h * 0.7f)
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(w * 0.15f, h * 0.15f),
                    size = Size(w * 0.7f, h * 0.7f)
                )
            }
            SettingsOption.APPS -> {
                val color = Color(0xFF0099CC)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.76f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawCircle(color = Color.Black.copy(alpha = 0.8f), radius = minD * 0.2f, center = Offset(w * 0.5f, h * 0.48f))
                drawCircle(color = Color.White, radius = minD * 0.05f, center = Offset(w * 0.42f, h * 0.48f))
                drawCircle(color = Color.White, radius = minD * 0.05f, center = Offset(w * 0.58f, h * 0.48f))
                drawLine(
                    color = Color.Black.copy(alpha = 0.8f),
                    start = Offset(w * 0.38f, h * 0.3f),
                    end = Offset(w * 0.32f, h * 0.2f),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.8f),
                    start = Offset(w * 0.62f, h * 0.3f),
                    end = Offset(w * 0.68f, h * 0.2f),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.8f),
                    topLeft = Offset(w * 0.32f, h * 0.7f),
                    size = Size(w * 0.36f, h * 0.14f)
                )
            }
            SettingsOption.STORAGE -> {
                val color = Color(0xFF33B5E5)
                for (i in 0 until 3) {
                    val topY = h * 0.18f + i * (h * 0.24f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.15f, topY),
                        size = Size(w * 0.7f, h * 0.15f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.6f),
                        start = Offset(w * 0.25f, topY + h * 0.07f),
                        end = Offset(w * 0.45f, topY + h * 0.07f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            SettingsOption.BATTERY -> {
                val color = Color(0xFF669900)
                drawRect(
                    color = color,
                    topLeft = Offset(w * 0.25f, h * 0.18f),
                    size = Size(w * 0.5f, h * 0.64f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawRect(
                    color = color,
                    topLeft = Offset(w * 0.4f, h * 0.08f),
                    size = Size(w * 0.2f, h * 0.1f)
                )
                drawRect(
                    color = color,
                    topLeft = Offset(w * 0.3f, h * 0.35f),
                    size = Size(w * 0.4f, h * 0.43f)
                )
            }
            SettingsOption.SCHEDULE -> {
                val color = Color(0xFF0099CC)
                drawCircle(
                    color = color,
                    radius = minD * 0.36f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(color = color, radius = minD * 0.06f, center = Offset(w * 0.5f, h * 0.5f))
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.5f),
                    end = Offset(w * 0.5f, h * 0.26f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.5f),
                    end = Offset(w * 0.7f, h * 0.5f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.Black,
                    start = Offset(w * 0.5f, h * 0.05f),
                    end = Offset(w * 0.5f, h * 0.2f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            SettingsOption.BACKUP -> {
                val color = Color(0xFFCC0000)
                drawArc(
                    color = color,
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                val path = Path().apply {
                    moveTo(w * 0.62f, h * 0.1f)
                    lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.56f, h * 0.34f)
                    close()
                }
                drawPath(path = path, color = color)
            }
            SettingsOption.DATETIME -> {
                val color = Color(0xFF669900)
                drawCircle(
                    color = color,
                    radius = minD * 0.38f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(color = color, radius = minD * 0.05f, center = Offset(w * 0.5f, h * 0.5f))
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.5f),
                    end = Offset(w * 0.36f, h * 0.3f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.5f),
                    end = Offset(w * 0.68f, h * 0.36f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                for (angle in 0..360 step 90) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x1 = (w * 0.5f + Math.cos(rad) * (minD * 0.28f)).toFloat()
                    val y1 = (h * 0.5f + Math.sin(rad) * (minD * 0.28f)).toFloat()
                    val x2 = (w * 0.5f + Math.cos(rad) * (minD * 0.34f)).toFloat()
                    val y2 = (h * 0.5f + Math.sin(rad) * (minD * 0.34f)).toFloat()
                    drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 2.dp.toPx())
                }
            }
            SettingsOption.SECURITY -> {
                val color = Color(0xFFFF4444)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.22f, h * 0.42f),
                    size = Size(w * 0.56f, h * 0.44f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.32f, h * 0.18f),
                    size = Size(w * 0.36f, h * 0.42f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(color = Color.Black, radius = minD * 0.05f, center = Offset(w * 0.5f, h * 0.6f))
                drawLine(
                    color = Color.Black,
                    start = Offset(w * 0.5f, h * 0.6f),
                    end = Offset(w * 0.5f, h * 0.74f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            SettingsOption.ACCESSIBILITY -> {
                val color = Color(0xFFAA66CC)
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.9f)
                    lineTo(w * 0.35f, h * 0.74f)
                    quadraticTo(w * 0.16f, h * 0.62f, w * 0.25f, h * 0.46f)
                    lineTo(w * 0.38f, h * 0.24f)
                    quadraticTo(w * 0.42f, h * 0.18f, w * 0.45f, h * 0.24f)
                    lineTo(w * 0.52f, h * 0.15f)
                    quadraticTo(w * 0.55f, h * 0.11f, w * 0.58f, h * 0.15f)
                    lineTo(w * 0.64f, h * 0.22f)
                    quadraticTo(w * 0.67f, h * 0.18f, w * 0.70f, h * 0.22f)
                    lineTo(w * 0.76f, h * 0.36f)
                    quadraticTo(w * 0.80f, h * 0.32f, w * 0.81f, h * 0.4f)
                    lineTo(w * 0.64f, h * 0.76f)
                    close()
                }
                drawPath(path = path, color = color)
            }
            SettingsOption.ABOUT -> {
                drawCircle(
                    color = Color(0xFF747474),
                    radius = minD * 0.42f,
                    center = Offset(w * 0.5f, h * 0.5f)
                )
                drawCircle(
                    color = Color(0xFFFF9426),
                    radius = minD * 0.07f,
                    center = Offset(w * 0.5f, h * 0.32f)
                )
                drawRoundRect(
                    color = Color(0xFFFF9426),
                    topLeft = Offset(w * 0.44f, h * 0.44f),
                    size = Size(w * 0.12f, h * 0.34f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun SettingsOptionRow(
    option: SettingsOption,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(Color.Black)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconDraw(
            option = option,
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(8.dp))
                .padding(6.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF666666),
            modifier = Modifier.size(18.dp)
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
fun HoloSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(80.dp)
            .height(28.dp)
            .background(Color(0xFF1F1F1F), RoundedCornerShape(2.dp))
            .border(1.5.dp, Color(0xFF444444), RoundedCornerShape(2.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (checked) {
                // Left is active cyan slider
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF33B5E5))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SÍ", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF3F3F3F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("", color = Color.White, fontSize = 11.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF3F3F3F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("", color = Color.White, fontSize = 11.sp)
                }
                // Right is inactive dark gray slider
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF121212))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("NO", color = Color(0xFF666666), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsActivity(viewModel: JellyBeanViewModel) {
    var screenState by remember { mutableStateOf("root") }
    val wifiEnabled by viewModel.isWifiEnabled.collectAsState()
    val bluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val locationEnabled by viewModel.isLocationEnabled.collectAsState()
    
    // Wifi Connections State
    val wifiNetworks = remember {
        mutableStateListOf(
            WifiConnection("HoloNet_Home_5G", 3, true),
            WifiConnection("Cafeteria_Jelly_Bean", 2, false),
            WifiConnection("Android_Secure_Access", 3, false),
            WifiConnection("GoogleGuest_Speedy", 1, false)
        )
    }
    val initialOwnerName by viewModel.ownerName.collectAsState()
    var nicknameInput by remember { mutableStateOf(initialOwnerName) }
    LaunchedEffect(initialOwnerName) {
        nicknameInput = initialOwnerName
    }
    val systemBrightness by viewModel.systemBrightness.collectAsState()
    val simulatedBatteryLevel by viewModel.batteryLevel.collectAsState()
    val simulatedBatteryCharging by viewModel.isBatteryCharging.collectAsState()
    var powerSaverEnabled by remember { mutableStateOf(false) }
    var aboutClickCount by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resettingProgress by remember { mutableStateOf(false) }
    var selectedAppForInfo by remember { mutableStateOf<AppType?>(null) }

    val settingsItems = listOf(
        SettingsItem(SettingsOption.WIFI, "Wi-Fi y Redes", "Activar, simular redes inalámbricas", "wifi"),
        SettingsItem(SettingsOption.BRIGHTNESS, "Brillo y Pantalla", "Fondos, brillo de pantalla y rotación", "pantalla"),
        SettingsItem(SettingsOption.SOUND, "Sonido y Volumen", "Efectos, tonos de llamada y multimedia", "sonido"),
        SettingsItem(SettingsOption.USERS, "Perfiles de Usuario", "Nombre del propietario y apodos", "perfiles"),
        SettingsItem(SettingsOption.APPS, "Aplicaciones y Notificaciones", "Ver espacio, desinstalar recursos", "apps"),
        SettingsItem(SettingsOption.STORAGE, "Almacenamiento del Dispositivo", "Comprobar memoria ocupada y de sistema", "almacenamiento"),
        SettingsItem(SettingsOption.BATTERY, "Batería y Energía", "Nivel de energía, temperatura y estado de salud", "bateria"),
        SettingsItem(SettingsOption.SCHEDULE, "Programación de Encendido/Apagado", "Temporizar apagados programados automáticos", "encendido"),
        SettingsItem(SettingsOption.BACKUP, "Copia de Seguridad y Restauración", "Copias automáticas, reiniciar dispositivo", "copia"),
        SettingsItem(SettingsOption.DATETIME, "Fecha y Hora", "Formato de 12/24 horas, zonas horarias", "fecha"),
        SettingsItem(SettingsOption.SECURITY, "Seguridad y Bloqueo", "Pantalla de bloqueo radial, PIN de sistema", "seguridad"),
        SettingsItem(SettingsOption.ACCESSIBILITY, "Accesibilidad", "Modificar escala de fuentes, gestos táctiles", "accesibilidad"),
        SettingsItem(SettingsOption.ABOUT, "Información del Teléfono", "Versión de Jelly Bean, easter egg, compilación", "about")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Holo Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F12))
                .border(BorderStroke(1.dp, Color(0xFF222228)))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (screenState != "root") {
                IconButton(
                    onClick = {
                        if (screenState == "app_info") {
                            screenState = "apps"
                        } else {
                            screenState = "root"
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color(0xFF33B5E5))
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = when (screenState) {
                    "root" -> "AJUSTES DEL SISTEMA"
                    "wifi" -> "WI-FI Y CONEXIONES"
                    "pantalla" -> "BRILLO Y PANTALLA"
                    "sonido" -> "SONIDO Y VOLUMEN"
                    "perfiles" -> "PERFILES DE USUARIO"
                    "apps" -> "APLICACIONES"
                    "app_info" -> "INFO. DE LA APLICACIÓN"
                    "almacenamiento" -> "ALMACENAMIENTO"
                    "bateria" -> "BATERÍA Y ENERGÍA"
                    "encendido" -> "ENCENDIDO PROGRAMADO"
                    "copia" -> "COPIA Y RESTAURACIÓN"
                    "fecha" -> "FECHA Y HORA"
                    "security" -> "SEGURIDAD Y BLOQUEO"
                    "accesibilidad" -> "ACCESIBILIDAD"
                    else -> "INFORMACIÓN DEL TELÉFONO"
                },
                color = Color(0xFF33B5E5),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (screenState) {
                "root" -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(settingsItems) { item ->
                            SettingsOptionRow(
                                option = item.option,
                                title = item.title,
                                subtitle = item.subtitle,
                                onClick = { screenState = item.screenState }
                            )
                        }
                    }
                }
                "wifi" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                            border = BorderStroke(1.dp, Color(0xFF2E2E32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Habilitar Wi-Fi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(if (wifiEnabled) "Encendido / Buscando redes..." else "Desactivado", color = Color.Gray, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = wifiEnabled,
                                    onCheckedChange = { viewModel.toggleWifi() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF33B5E5),
                                        checkedTrackColor = Color(0xFF0099CC)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                            border = BorderStroke(1.dp, Color(0xFF2E2E32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Activar Bluetooth", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(if (bluetoothEnabled) "Visibilidad activada para dispositivos" else "Buscador apagado", color = Color.Gray, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = bluetoothEnabled,
                                    onCheckedChange = { viewModel.toggleBluetooth() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF33B5E5),
                                        checkedTrackColor = Color(0xFF0099CC)
                                    )
                                )
                            }
                        }

                        if (wifiEnabled) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("REDES DISPONIBLES SIMULADAS", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            
                            wifiNetworks.forEachIndexed { index, net ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Simulate connection toggle
                                            for (i in wifiNetworks.indices) {
                                                wifiNetworks[i] = wifiNetworks[i].copy(isConnected = i == index)
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = if (net.isConnected) Color(0xFFFF9426) else Color.DarkGray,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(net.name, color = Color.White, fontSize = 15.sp, fontWeight = if (net.isConnected) FontWeight.Bold else FontWeight.Normal)
                                            if (net.isConnected) {
                                                Text("Dirección IP asignada: 192.168.1.154", color = Color(0xFF33B5E5), fontSize = 11.sp)
                                            } else {
                                                Text("Conexión segura (WPA2)", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    if (net.isConnected) {
                                        Text("CONECTADO", color = Color(0xFF669900), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                            }
                        }
                    }
                }
                "sonido" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text("EFECTOS Y VOLUMEN DE SISTEMA", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        // System Sounds switch
                        val systemSoundsEnabled by viewModel.systemSoundsEnabled.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sonidos del sistema (Touch Sounds)", color = Color.White, fontSize = 15.sp)
                                Text("Reproducir sonidos nostálgicos al tocar botones", color = Color.Gray, fontSize = 12.sp)
                            }
                            HoloSwitch(
                                checked = systemSoundsEnabled,
                                onCheckedChange = { viewModel.setSystemSoundsEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                        Spacer(modifier = Modifier.height(24.dp))

                        Text("CONTROLES DE VOLUMEN SIMULADOS", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Tono de llamada
                        var ringtoneVol by remember { mutableStateOf(0.8f) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Volumen de tono de llamada", color = Color.White, fontSize = 14.sp)
                                Slider(
                                    value = ringtoneVol,
                                    onValueChange = { ringtoneVol = it },
                                    colors = SliderDefaults.colors(activeTrackColor = Color(0xFF33B5E5), thumbColor = Color(0xFF33B5E5))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.playSystemSound("ringtone") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("PROBAR", color = Color(0xFF33B5E5), fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sonidos multimedia
                        var mediaVol by remember { mutableStateOf(0.7f) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Volumen multimedia (Juegos/Música)", color = Color.White, fontSize = 14.sp)
                                Slider(
                                    value = mediaVol,
                                    onValueChange = { mediaVol = it },
                                    colors = SliderDefaults.colors(activeTrackColor = Color(0xFF33B5E5), thumbColor = Color(0xFF33B5E5))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.playSystemSound("multimedia") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("PROBAR", color = Color(0xFF33B5E5), fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Notificaciones
                        var notifVol by remember { mutableStateOf(0.6f) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Volumen de notificaciones", color = Color.White, fontSize = 14.sp)
                                Slider(
                                    value = notifVol,
                                    onValueChange = { notifVol = it },
                                    colors = SliderDefaults.colors(activeTrackColor = Color(0xFF33B5E5), thumbColor = Color(0xFF33B5E5))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.playSystemSound("notification") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("PROBAR", color = Color(0xFF33B5E5), fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                        Spacer(modifier = Modifier.height(24.dp))

                        Text("MELODÍAS Y TONOS JELLY BEAN", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Ringtone selector dropdown
                        val selectedRingtone by viewModel.selectedRingtone.collectAsState()
                        var ringtoneMenuExpanded by remember { mutableStateOf(false) }
                        val ringtoneList = listOf("Retro Holo Chime", "Andrómeda", "Jelly Bean Ring", "Sinfonía de 8 bits")

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Tono de llamada predeterminado", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1E1E), RoundedCornerShape(2.dp))
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(2.dp))
                                    .clickable { ringtoneMenuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedRingtone, color = Color.White, fontSize = 14.sp)
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF33B5E5))
                                }
                            }
                            DropdownMenu(
                                expanded = ringtoneMenuExpanded,
                                onDismissRequest = { ringtoneMenuExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .background(Color(0xFF222222))
                                    .border(1.dp, Color.DarkGray)
                            ) {
                                ringtoneList.forEach { rt ->
                                    DropdownMenuItem(
                                        text = { Text(rt, color = Color.White) },
                                        onClick = {
                                            viewModel.selectRingtone(rt)
                                            ringtoneMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Notification Sound selector dropdown
                        val selectedNotifSound by viewModel.selectedNotificationSound.collectAsState()
                        var notifMenuExpanded by remember { mutableStateOf(false) }
                        val notifSoundList = listOf("Simple Tick", "Jelly Bean Ack", "Double Beep", "Bubble Pop", "Ceres (Classic)", "Pixie Dust", "Teardrop")

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Tono de notificación de sistema", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1E1E), RoundedCornerShape(2.dp))
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(2.dp))
                                    .clickable { notifMenuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedNotifSound, color = Color.White, fontSize = 14.sp)
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF33B5E5))
                                }
                            }
                            DropdownMenu(
                                expanded = notifMenuExpanded,
                                onDismissRequest = { notifMenuExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .background(Color(0xFF222222))
                                    .border(1.dp, Color.DarkGray)
                            ) {
                                notifSoundList.forEach { ns ->
                                    DropdownMenuItem(
                                        text = { Text(ns, color = Color.White) },
                                        onClick = {
                                            viewModel.selectNotificationSound(ns)
                                            notifMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "pantalla" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text("CONTROL DE BRILLO", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LightMode, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Slider(
                                value = systemBrightness,
                                onValueChange = { viewModel.setSystemBrightness(it) },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFF33B5E5),
                                    thumbColor = Color(0xFF33B5E5)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${(systemBrightness * 100).toInt()}%", color = Color.White, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Girar pantalla automáticamente", color = Color.White, fontSize = 15.sp)
                                Text("Rotación mediante acelerómetro física", color = Color.Gray, fontSize = 12.sp)
                            }
                            Checkbox(
                                checked = true,
                                onCheckedChange = {},
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF33B5E5))
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Vintage Dark Mode Toggle
                        val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Modo Oscuro (Holo Dark)", color = Color.White, fontSize = 15.sp)
                                Text("Ahorra energía e intensifica el negro de fondo", color = Color.Gray, fontSize = 12.sp)
                            }
                            HoloSwitch(
                                checked = darkModeEnabled,
                                onCheckedChange = { viewModel.setDarkModeEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("PALETA DE COLOR HOLO (TINTA DE INTERFAZ)", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(Color(0xFF33B5E5), Color(0xFF669900), Color(0xFFFF4444), Color(0xFFAA66CC)).forEach { themeColor ->
                                Box(
                                    modifier = Modifier
                                        .size(45.dp)
                                        .background(themeColor, RoundedCornerShape(22.dp))
                                        .border(2.dp, Color.White, RoundedCornerShape(22.dp))
                                        .clickable {
                                            viewModel.postNotification(
                                                "Ajustes del Sistema",
                                                "Tema cambiado al color clásico",
                                                AppType.SETTINGS
                                            )
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                        Text("SELECCIÓN DE FONDO DE PANTALLA", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        val wallpaperOptions = listOf(
                            Triple("WAVES", "Ondas Holo (Clásico)", "Ondas dinámicas de la era original"),
                            Triple("SOLID", "Holo Sólido", "Azul carbón oscuro minimalista"),
                            Triple("SOLID_BLACK", "Negro Puro", "Fondo negro sólido para ahorrar batería"),
                            Triple("GRADIENT", "Holo Degradado", "Degradado vertical de azul a negro"),
                            Triple("NEXUS", "Mosaico Nexus", "Cuadrículas y mosaicos de luz de Nexus")
                        )

                        val activePreset by viewModel.wallpaperPreset.collectAsState()
                        val customBmp by viewModel.wallpaperSelected.collectAsState()

                        if (customBmp != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0x3333B5E5)),
                                border = BorderStroke(1.5.dp, Color(0xFF33B5E5)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            bitmap = customBmp!!.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(50.dp, 75.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text("Fondo de Galería", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("Imagen seleccionada del usuario", color = Color.LightGray, fontSize = 11.sp)
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.setWallpaperPreset("WAVES") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("Quitar", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        wallpaperOptions.forEach { (presetKey, presetTitle, presetDesc) ->
                            val isSelected = customBmp == null && activePreset == presetKey
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.setWallpaperPreset(presetKey)
                                        viewModel.postNotification(
                                            "Ajustes del Sistema",
                                            "Fondo de pantalla cambiado a: $presetTitle",
                                            AppType.SETTINGS
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0x2233B5E5) else Color(0x11FFFFFF)
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF33B5E5) else Color(0xFF2E2E32)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp, 75.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    ) {
                                        when (presetKey) {
                                            "SOLID" -> {
                                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1219)))
                                            }
                                            "SOLID_BLACK" -> {
                                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000)))
                                            }
                                            "GRADIENT" -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(Color(0xFF0F1826), Color(0xFF000000))
                                                            )
                                                        )
                                                )
                                            }
                                            "NEXUS" -> {
                                                NexusMosaicWallpaper(modifier = Modifier.fillMaxSize())
                                            }
                                            else -> {
                                                val themeColorProfile by viewModel.themeColorProfile.collectAsState()
                                                JellyBeanWaveWallpaper(themeProfile = themeColorProfile, modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = presetTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = presetDesc, color = Color.Gray, fontSize = 11.sp)
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setWallpaperPreset(presetKey)
                                            viewModel.postNotification(
                                                "Ajustes del Sistema",
                                                "Fondo de pantalla cambiado a: $presetTitle",
                                                AppType.SETTINGS
                                            )
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF33B5E5),
                                            unselectedColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                "perfiles" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(Color(0xFF2E2E32), CircleShape)
                                .border(2.dp, Color(0xFF33B5E5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF33B5E5), modifier = Modifier.size(54.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        TextField(
                            value = nicknameInput,
                            onValueChange = { nicknameInput = it },
                            label = { Text("Nombre del Propietario", color = Color(0xFF33B5E5)) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF141416),
                                unfocusedContainerColor = Color(0xFF141416),
                                focusedIndicatorColor = Color(0xFF33B5E5)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.setOwnerName(nicknameInput)
                                viewModel.postNotification("Ajustes", "Perfil actualizado: $nicknameInput", AppType.SETTINGS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1F)),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("GUARDAR Y ACTUALIZAR", color = Color(0xFF33B5E5))
                        }
                    }
                }
                "apps" -> {
                    val installedApps by viewModel.installedApps.collectAsState()
                    val allSimulatedApps = AppType.values()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Text("APLICACIONES INSTALADAS", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        items(allSimulatedApps) { app ->
                            val alreadyInstalled = installedApps.contains(app)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .holoTap(
                                        shape = RoundedCornerShape(4.dp),
                                        onClick = {
                                            selectedAppForInfo = app
                                            screenState = "app_info"
                                        }
                                    )
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    JellyBeanAppIcon(app = app, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(app.appName, color = Color.White, fontSize = 15.sp)
                                        Text(if (app == AppType.ABOUT || app == AppType.SETTINGS) "Sistema" else "Guardado en Almacenamiento Interno", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }

                                if (app != AppType.ABOUT && app != AppType.SETTINGS) {
                                    Button(
                                        onClick = {
                                            if (alreadyInstalled) {
                                                viewModel.uninstallAppInStore(app)
                                            } else {
                                                viewModel.installAppInStore(app)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (alreadyInstalled) Color(0xFF442222) else Color(0xFF224422)
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = if (alreadyInstalled) "Quitar" else "Instalar",
                                            color = if (alreadyInstalled) Color.Red else Color.Green,
                                            fontSize = 11.sp
                                        )
                                    }
                                } else {
                                    Text("Bloqueado", color = Color.DarkGray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                        }
                    }
                }
                "app_info" -> {
                    val app = selectedAppForInfo
                    if (app != null) {
                        val installedApps by viewModel.installedApps.collectAsState()
                        val alreadyInstalled = installedApps.contains(app)
                        var isForceStopped by remember(app) { mutableStateOf(false) }
                        var appDataCleared by remember(app) { mutableStateOf(false) }
                        var appCacheCleared by remember(app) { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // Header with Icon, Name, Version
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                            ) {
                                JellyBeanAppIcon(app = app, modifier = Modifier.size(52.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(app.appName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("versión 1.2.${app.name.length}", color = Color.Gray, fontSize = 12.sp)
                                }
                            }

                            // Force Stop and Uninstall Buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isForceStopped = true
                                        viewModel.postNotification(
                                            "Ajustes",
                                            "Se forzó la detención de ${app.appName}",
                                            AppType.SETTINGS
                                        )
                                        viewModel.playSystemSound("click")
                                    },
                                    enabled = !isForceStopped,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF252528),
                                        disabledContainerColor = Color(0xFF161618)
                                    ),
                                    border = BorderStroke(1.dp, if (isForceStopped) Color.DarkGray else Color.Gray),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "Forzar detención",
                                        color = if (isForceStopped) Color.DarkGray else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (app != AppType.ABOUT && app != AppType.SETTINGS) {
                                    Button(
                                        onClick = {
                                            viewModel.playSystemSound("click")
                                            if (alreadyInstalled) {
                                                viewModel.uninstallAppInStore(app)
                                                screenState = "apps"
                                            } else {
                                                viewModel.installAppInStore(app)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252528)),
                                        border = BorderStroke(1.dp, Color.Gray),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (alreadyInstalled) "Desinstalar" else "Instalar",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF161618)),
                                        border = BorderStroke(1.dp, Color.DarkGray),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Desactivar", color = Color.DarkGray, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Almacenamiento Section
                            Text(
                                "ALMACENAMIENTO",
                                color = Color(0xFF33B5E5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)),
                                border = BorderStroke(1.dp, Color(0xFF222226)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val appSize = (app.name.length * 1.4f).coerceAtLeast(2.4f)
                                    val dataSize = if (appDataCleared) 0.0f else (app.appName.length * 0.3f).coerceAtLeast(0.8f)
                                    val totalSize = appSize + dataSize

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Text("Total", color = Color.White, fontSize = 13.sp)
                                        Text(String.format(Locale.US, "%.2f MB", totalSize), color = Color.White, fontSize = 13.sp)
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Text("Aplicación", color = Color.Gray, fontSize = 12.sp)
                                        Text(String.format(Locale.US, "%.2f MB", appSize), color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Text("Datos de la app", color = Color.Gray, fontSize = 12.sp)
                                        Text(String.format(Locale.US, "%.2f MB", dataSize), color = Color.Gray, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            appDataCleared = true
                                            viewModel.playSystemSound("click")
                                        },
                                        enabled = !appDataCleared,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F24)),
                                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Borrar datos", color = if (appDataCleared) Color.Gray else Color.White, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Cache Section
                            Text(
                                "CACHÉ",
                                color = Color(0xFF33B5E5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)),
                                border = BorderStroke(1.dp, Color(0xFF222226)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val cacheSize = if (appCacheCleared) 0 else (app.appName.length * 16 + 12)

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Text("Caché", color = Color.White, fontSize = 13.sp)
                                        Text("${cacheSize} KB", color = Color.White, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            appCacheCleared = true
                                            viewModel.playSystemSound("click")
                                        },
                                        enabled = !appCacheCleared,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F24)),
                                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Borrar caché", color = if (appCacheCleared) Color.Gray else Color.White, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Permisos Section
                            Text(
                                "PERMISOS DE LA APLICACIÓN",
                                color = Color(0xFF33B5E5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)),
                                border = BorderStroke(1.dp, Color(0xFF222226)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val permissions = when (app) {
                                        AppType.CAMERA -> listOf("Acceso a Cámara de Fotos", "Almacenamiento Local")
                                        AppType.MUSIC -> listOf("Acceso a Red / Internet", "Almacenamiento Local")
                                        AppType.AI_GENERATOR -> listOf("Acceso a Red / Internet", "Almacenamiento Local")
                                        AppType.SOUND_RECORDER -> listOf("Acceso a Micrófono de Sistema", "Almacenamiento Local")
                                         AppType.DOWNLOADS -> listOf("Acceso a Red / Internet", "Almacenamiento Local")
                                        AppType.BROWSER -> listOf("Acceso a Red / Internet")
                                        AppType.SETTINGS -> listOf("Configuración del Sistema", "Modificar Estado de Red")
                                        else -> listOf("Almacenamiento Local")
                                    }

                                    permissions.forEach { perm ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier.size(6.dp).background(Color(0xFF33B5E5), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(perm, color = Color.LightGray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "almacenamiento" -> {
                    var cacheMb by remember { mutableIntStateOf(42) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("ALMACENAMIENTO INTERNO", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Segmented bar representation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(Color(0xFF1E1E24), RoundedCornerShape(4.dp))
                                .border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxHeight().weight(0.18f).background(Color(0xFF33B5E5))) // Apps
                                Box(modifier = Modifier.fillMaxHeight().weight(0.08f).background(Color(0xFFFF9426))) // Picture
                                Box(modifier = Modifier.fillMaxHeight().weight(0.12f).background(Color(0xFF669900))) // Audio
                                Box(modifier = Modifier.fillMaxHeight().weight(0.62f).background(Color(0xFF242426))) // Free
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", color = Color.White, fontSize = 14.sp)
                            Text("16.0 GB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ocupado", color = Color.LightGray, fontSize = 13.sp)
                            Text("5.7 GB", color = Color.LightGray, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("CATEGORÍAS DE USO", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        StorageRowItem("Aplicaciones", "2.81 GB", Color(0xFF33B5E5))
                        StorageRowItem("Fotos y Videos", "1.20 GB", Color(0xFFFF9426))
                        StorageRowItem("Archivos de Audio", "580 MB", Color(0xFF669900))
                        StorageRowItem("Memoria Cache simulada", "$cacheMb MB", Color.Gray)

                        Spacer(modifier = Modifier.height(30.dp))

                        Button(
                            onClick = {
                                if (cacheMb > 0) {
                                    cacheMb = 0
                                    viewModel.postNotification("Limpiador de disco", "Se liberaron recursos de cache con éxito.", AppType.SETTINGS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F24)),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("BORRAR CACHÉ DISCO", color = Color(0xFF33B5E5))
                        }
                    }
                }
                "bateria" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.size(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sizeWidth = size.width
                                val sizeHeight = size.height

                                drawCircle(
                                    color = Color(0xFF15151A),
                                    radius = sizeWidth * 0.45f,
                                    style = Stroke(width = 6.dp.toPx())
                                )
                                drawArc(
                                    color = if (simulatedBatteryCharging) Color(0xFF99CC00) else if (powerSaverEnabled) Color(0xFFFF9426) else Color(0xFF33B5E5),
                                    startAngle = -90f,
                                    sweepAngle = 360f * (simulatedBatteryLevel / 100f),
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$simulatedBatteryLevel%", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (simulatedBatteryCharging) "CARGANDO" else if (powerSaverEnabled) "AHORRO ENERGÍA" else "ESTABLE",
                                    color = if (simulatedBatteryCharging) Color(0xFF99CC00) else if (powerSaverEnabled) Color(0xFFFF9426) else Color(0xFF33B5E5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                            border = BorderStroke(1.dp, Color(0xFF2E2E32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("INFORMACIÓN FÍSICA Y DE SALUD", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                RowInfoLabel("Estado de carga:", if (simulatedBatteryCharging) "Conectado / Cargándose" else "Desconectado / Descargándose")
                                RowInfoLabel("Salud de Batería:", "Excelente / Estable")
                                RowInfoLabel("Voltaje:", "${3.7f + (simulatedBatteryLevel / 100f) * 0.5f} V")
                                RowInfoLabel("Temperatura:", if (simulatedBatteryCharging) "34.5 °C" else "30.1 °C")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls
                        Text("SIMULACIÓN TÁCTIL DE ENERGÍA", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        
                        // 1. Level Slider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0C0C0E), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(4.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Nivel de batería simulado", color = Color.White, fontSize = 14.sp)
                                Text("$simulatedBatteryLevel%", color = Color(0xFF33B5E5), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = simulatedBatteryLevel.toFloat(),
                                onValueChange = { viewModel.setBatteryLevel(it.toInt()) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFF33B5E5),
                                    thumbColor = Color(0xFF33B5E5),
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Plug in Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0C0C0E), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(4.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Simular Conexión (Cargador)", color = Color.White, fontSize = 15.sp)
                                Text(if (simulatedBatteryCharging) "Conectado a la corriente" else "Desconectado", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = simulatedBatteryCharging,
                                onCheckedChange = { viewModel.setBatteryCharging(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF99CC00),
                                    checkedTrackColor = Color(0xFF99CC00).copy(alpha = 0.5f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0C0C0E), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(4.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Ahorro de energía clásico", color = Color.White, fontSize = 15.sp)
                                Text("Reduce procesos en segundo plano", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = powerSaverEnabled,
                                onCheckedChange = { powerSaverEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF9426),
                                    checkedTrackColor = Color(0xFFFF9426).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
                "encendido" -> {
                    var powOnEnabled by remember { mutableStateOf(false) }
                    var powOffEnabled by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("HORARIOS DE SISTEMA AUTOMÁTICOS", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Encendido programado", color = Color.White, fontSize = 16.sp)
                                Text("Lunes a Viernes a las 07:00 AM", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(checked = powOnEnabled, onCheckedChange = { powOnEnabled = it })
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Apagado programado", color = Color.White, fontSize = 16.sp)
                                Text("Diariamente a las 11:30 PM", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(checked = powOffEnabled, onCheckedChange = { powOffEnabled = it })
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    }
                }
                "copia" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("COPIA DE SEGURIDAD EN LA NUBE", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))

                        var backupToggle by remember { mutableStateOf(true) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Copia de seguridad", color = Color.White, fontSize = 15.sp)
                                Text("Guardas redes Wi-Fi, claves e historiales", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(checked = backupToggle, onCheckedChange = { backupToggle = it })
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.postNotification("Copia de seguridad", "Sincronizando estado con el servidor...", AppType.SETTINGS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1D22)),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("RESPALDAR AHORA", color = Color(0xFF33B5E5))
                        }

                        Spacer(modifier = Modifier.height(42.dp))
                        Text("RESTAURACIÓN COMPLETA", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1E1E)),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color.Red),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("RESTABLECER DATOS DE FÁBRICA", color = Color.Red)
                        }
                    }
                }
                "fecha" -> {
                    var format24h by remember { mutableStateOf(false) }
                    var autoTime by remember { mutableStateOf(true) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("CONFIGURACIÓN DE ENTRADA DE FECHA Y HORA", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Zona horaria de red automática", color = Color.White, fontSize = 15.sp)
                                Text("Sincroniza la hora con el satélite", color = Color.Gray, fontSize = 11.sp)
                            }
                            Checkbox(checked = autoTime, onCheckedChange = { autoTime = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF33B5E5)))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Formato de 24 horas", color = Color.White, fontSize = 15.sp)
                                Text("Muestra la hora en formato 24:00", color = Color.Gray, fontSize = 11.sp)
                            }
                            Checkbox(checked = format24h, onCheckedChange = { format24h = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF33B5E5)))
                        }
                    }
                }
                "seguridad" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("PROTECCIÓN GENERAL Y SEGURIDAD", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Bloqueo de pantalla radial", color = Color.White, fontSize = 15.sp)
                                Text("Simula bloqueo Jelly Bean Holo circular", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF33B5E5)))
                        }
                    }
                }
                "accesibilidad" -> {
                    var speechToggle by remember { mutableStateOf(false) }
                    var zoomToggle by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("CARACTERÍSTICAS DE FACILIDAD DE ACCESO", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Texto grande", color = Color.White, fontSize = 15.sp)
                                Text("Agranda fuentes en sistema", color = Color.Gray, fontSize = 12.sp)
                            }
                            Checkbox(checked = zoomToggle, onCheckedChange = { zoomToggle = it })
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Mock TalkBack", color = Color.White, fontSize = 15.sp)
                                Text("Retroalimentación por voz simulada", color = Color.Gray, fontSize = 12.sp)
                            }
                            Checkbox(checked = speechToggle, onCheckedChange = { speechToggle = it })
                        }
                    }
                }
                "about" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Información del Teléfono", color = Color(0xFF33B5E5), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            AboutRowInfo(key = "Modelo", value = "AI Studio Jelly Bean Sim")
                            
                            // 5 Click behavior to launch easter egg
                            AboutRowInfo(
                                key = "Versión de Android", 
                                value = "4.1.2 Jelly Bean (Haz clic 5 veces)", 
                                isClickable = true, 
                                onClick = { 
                                    aboutClickCount += 1
                                    if (aboutClickCount >= 5) {
                                        aboutClickCount = 0
                                        viewModel.launchApp(AppType.ABOUT)
                                    } else {
                                        viewModel.postNotification("Easter Egg", "Haz clic ${5 - aboutClickCount} veces más", AppType.SETTINGS)
                                    }
                                }
                            )
                            AboutRowInfo(key = "Número de compilación", value = "JZO54K")
                            AboutRowInfo(key = "Kernel de compilación", value = "3.4.0-gd6ba5c2 Android Build Server")
                            AboutRowInfo(key = "Plataforma Base", value = "Arm-v7a Multi Processor")
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Advertencia crítica", color = Color.Red) },
            text = { Text("¿Estás seguro que deseas realizar una simulación de restablecimiento de fábrica? Se borrarán todos tus datos locales configurados.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetSimulator()
                    }
                ) {
                    Text("CONFIRMAR Y RESETEAR", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = Color(0xFF141416)
        )
    }
}

@Composable
fun StorageRowItem(category: String, size: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(category, color = Color.White, fontSize = 14.sp)
        }
        Text(size, color = Color.Gray, fontSize = 14.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
}

@Composable
fun RowInfoLabel(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AboutRowInfo(key: String, value: String, isClickable: Boolean = false, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable) { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Text(text = key, color = Color.White, fontSize = 14.sp)
        Text(text = value, color = if (isClickable) Color(0xFF33B5E5) else Color.Gray, fontSize = 12.sp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

@Composable
fun RetroMapsActivity(viewModel: JellyBeanViewModel) {
    var queryText by remember { mutableStateOf("") }
    var detailText by remember { mutableStateOf("") }
    var locationSearchState by remember { mutableStateOf("idle") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = { Text("Buscar ubicación (ej. París)...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1B1B1B)
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (queryText.isNotEmpty()) {
                        locationSearchState = "loading"
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val promptText = "Proporciona una breve descripción histórica y geográfica de: $queryText. Sé muy breve (máximo 4 líneas)."
                                val request = GeminiRequest(
                                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText))))
                                )
                                val response = RetrofitClient.apiService.generateContent(
                                    model = "gemini-3.5-flash",
                                    apiKey = BuildConfig.GEMINI_API_KEY,
                                    request = request
                                )

                                withContext(Dispatchers.Main) {
                                    detailText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                        ?: "No se obtuvieron detalles geográficos."
                                    locationSearchState = "results"
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    detailText = "Error al conectar con la cartografía AI: ${e.localizedMessage}"
                                    locationSearchState = "results"
                                }
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                border = BorderStroke(1.dp, Color(0xFF33B5E5))
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar ubicación", tint = Color(0xFF33B5E5))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF2E2E2E))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridStep = 80f
                for (x in 0..size.width.toInt() step gridStep.toInt()) {
                    drawLine(Color(0x15FFFFFF), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
                }
                for (y in 0..size.height.toInt() step gridStep.toInt()) {
                    drawLine(Color(0x15FFFFFF), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
                }

                drawLine(Color(0xFF444444), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 24f)
                drawLine(Color(0xFF555555), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 24f)

                drawCircle(Color(0xFF33B5E5), size.minDimension / 15f, center = Offset(size.width / 2, size.height / 2))
                drawCircle(Color.White, size.minDimension / 35f, center = Offset(size.width / 2, size.height / 2))
            }

            if (locationSearchState == "loading") {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xBB000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF33B5E5))
                }
            } else if (locationSearchState == "results") {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEB151515)),
                    border = BorderStroke(1.dp, Color(0xFF33B5E5))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Ubicación: " + queryText.uppercase(), color = Color(0xFF33B5E5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = detailText, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RetroMarketActivity(viewModel: JellyBeanViewModel) {
    val installedApps by viewModel.installedApps.collectAsState()
    
    val storeApps = listOf(
        Triple(AppType.MAPS, "Maps", "Consigue cartografía GPS."),
        Triple(AppType.PHONE, "Dialer", "Marcador de llamadas clásica."),
        Triple(AppType.BROWSER, "Navegador", "Navega Google Search retro."),
        Triple(AppType.CALCULATOR, "Calculadora", "Hacer cuentas de forma fácil."),
        Triple(AppType.CAMERA, "Cámara IA", "Carrete fotográfico por IA."),
        Triple(AppType.MUSIC, "Música", "Reproductor Retro."),
        Triple(AppType.CLOCK, "Reloj", "Alarma y cronómetro."),
        Triple(AppType.AI_GENERATOR, "Creador IA", "Genera imágenes increíbles usando Inteligencia Artificial.")
    )

    var selectedCategory by remember { mutableStateOf("Apps") }
    var appToDetail by remember { mutableStateOf<Triple<AppType, String, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151515))
    ) {
        // Play Store Header Bar (Image 7 style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Play store bag icon drawing
                Canvas(modifier = Modifier.size(26.dp)) {
                    val w = size.width
                    val h = size.height
                    
                    val handlePath = Path().apply {
                        moveTo(w * 0.35f, h * 0.35f)
                        quadraticTo(w * 0.5f, h * 0.05f, w * 0.65f, h * 0.35f)
                    }
                    drawPath(
                        path = handlePath,
                        color = Color.White,
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val bagPath = Path().apply {
                        moveTo(w * 0.16f, h * 0.32f)
                        lineTo(w * 0.84f, h * 0.32f)
                        lineTo(w * 0.88f, h * 0.92f)
                        lineTo(w * 0.12f, h * 0.92f)
                        close()
                    }
                    drawPath(path = bagPath, color = Color.White)

                    val playPath = Path().apply {
                        moveTo(w * 0.4f, h * 0.48f)
                        lineTo(w * 0.65f, h * 0.62f)
                        lineTo(w * 0.4f, h * 0.76f)
                        close()
                    }
                    drawPath(
                        path = playPath,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFFFF4444))
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google Play",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Top Banner Area (Diagonal Carbon Texture / Multi-color gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF333333), Color(0xFF1E1E1E))
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "7 DAYS TO PLAY",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic
                    )
                    Text(
                        text = "New 25¢ Deals Every Day",
                        color = Color(0xFF99CC00),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Now 41 CD Art representation
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE04000))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "NOW",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "41",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Split view - Categories left, recommendations right
        Row(modifier = Modifier.fillMaxSize()) {
            // Left list of categories
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .background(Color(0xFF222222))
                    .padding(vertical = 4.dp)
            ) {
                val categories = listOf(
                    "Apps" to Color(0xFF6E6E72),
                    "Games" to Color(0xFF99CC00),
                    "Music" to Color(0xFFFFBB33),
                    "Books" to Color(0xFF0099CC),
                    "Movies" to Color(0xFFFF4444)
                )

                categories.forEach { (cat, color) ->
                    val isSelected = selectedCategory == cat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = cat }
                            .background(if (isSelected) Color(0xFF2E2E2E) else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Left indicator color block
                            Box(
                                modifier = Modifier
                                    .size(width = 4.dp, height = 24.dp)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cat,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Text(
                            text = ">",
                            color = Color.DarkGray,
                            fontSize = 11.sp
                        )
                    }
                    Divider(color = Color(0xFF151515), thickness = 1.dp)
                }
            }

            // Right side grid/list (Featured contents)
            Column(
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                Text(
                    text = "Play 49¢ Apps & Games",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(storeApps) { appTriple ->
                        val isInstalled = installedApps.contains(appTriple.first)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF282828))
                                .border(0.5.dp, Color.White.copy(alpha = 0.1f))
                                .clickable { appToDetail = appTriple }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            JellyBeanAppIcon(app = appTriple.first, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appTriple.second,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isInstalled) "Instalado" else "Disponible",
                                    color = if (isInstalled) Color(0xFF99CC00) else Color.LightGray,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "49¢",
                                color = Color(0xFF99CC00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal popup detail dialog
    appToDetail?.let { appTriple ->
        val alreadyInstalled = installedApps.contains(appTriple.first)
        AlertDialog(
            onDismissRequest = { appToDetail = null },
            containerColor = Color(0xFF222222),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyBeanAppIcon(app = appTriple.first, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = appTriple.second, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(text = appTriple.third, color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Desarrollador: Google Inc.", color = Color.Gray, fontSize = 11.sp)
                    Text(text = "Precio: 49¢ (Oferta Jelly Bean)", color = Color(0xFF99CC00), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (alreadyInstalled) {
                            viewModel.uninstallAppInStore(appTriple.first)
                        } else {
                            viewModel.installAppInStore(appTriple.first)
                        }
                        appToDetail = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (alreadyInstalled) Color(0xFFCC0000) else Color(0xFF669900))
                ) {
                    Text(text = if (alreadyInstalled) "Quitar" else "Instalar", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { appToDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(text = "Cancelar", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun RedJellyBeanMascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val mascotPath = Path().apply {
            moveTo(w * 0.28f, h * 0.38f)
            
            // Left Antenna
            lineTo(w * 0.18f, h * 0.27f)
            cubicTo(w * 0.13f, h * 0.21f, w * 0.17f, h * 0.16f, w * 0.22f, h * 0.22f)
            lineTo(w * 0.31f, h * 0.34f)
            
            // Top head curve
            cubicTo(w * 0.45f, h * 0.27f, w * 0.65f, h * 0.26f, w * 0.76f, h * 0.35f)
            
            // Right Antenna
            lineTo(w * 0.83f, h * 0.19f)
            cubicTo(w * 0.88f, h * 0.13f, w * 0.92f, h * 0.17f, w * 0.87f, h * 0.26f)
            lineTo(w * 0.82f, h * 0.38f)
            
            // Body right bulb
            cubicTo(w * 0.98f, h * 0.46f, w * 0.95f, h * 0.74f, w * 0.82f, h * 0.82f)
            
            // Bottom indent (kidney bean)
            cubicTo(w * 0.65f, h * 0.85f, w * 0.42f, h * 0.77f, w * 0.24f, h * 0.84f)
            
            // Body left bulb
            cubicTo(w * 0.05f, h * 0.82f, w * 0.08f, h * 0.46f, w * 0.28f, h * 0.38f)
            
            close()
        }

        // Drop shadow
        drawPath(
            path = mascotPath,
            color = Color.Black.copy(alpha = 0.22f)
        )

        // Thick black border
        drawPath(
            path = mascotPath,
            color = Color.Black,
            style = Stroke(width = 8.dp.toPx(), join = StrokeJoin.Round)
        )

        // Vibrant gradient red fill
        drawPath(
            path = mascotPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF2C2C), Color(0xFFC20000)),
                center = Offset(w * 0.45f, h * 0.5f),
                radius = w * 0.55f
            )
        )

        // Glossy Highlights
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.02f))
            ),
            topLeft = Offset(w * 0.22f, h * 0.34f),
            size = Size(w * 0.45f, h * 0.16f)
        )

        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.14f))
            ),
            topLeft = Offset(w * 0.25f, h * 0.72f),
            size = Size(w * 0.45f, h * 0.08f)
        )

        // Left smiling happy eye
        val leftEye = Path().apply {
            moveTo(w * 0.32f, h * 0.55f)
            quadraticTo(w * 0.38f, h * 0.47f, w * 0.44f, h * 0.55f)
        }
        drawPath(
            path = leftEye,
            color = Color.Black,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Right smiling happy eye
        val rightEye = Path().apply {
            moveTo(w * 0.56f, h * 0.52f)
            quadraticTo(w * 0.62f, h * 0.44f, w * 0.68f, h * 0.52f)
        }
        drawPath(
            path = rightEye,
            color = Color.Black,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Smiley mouth
        val mouth = Path().apply {
            moveTo(w * 0.43f, h * 0.65f)
            quadraticTo(w * 0.50f, h * 0.73f, w * 0.57f, h * 0.65f)
        }
        drawPath(
            path = mouth,
            color = Color.Black,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AboutJellyBeanActivity(viewModel: JellyBeanViewModel) {
    var stepState by remember { mutableStateOf("jelly_bean") }
    val particles by viewModel.jellyBeansParticles.collectAsState()

    var layoutWidth by remember { mutableStateOf(100f) }
    var layoutHeight by remember { mutableStateOf(100f) }

    LaunchedEffect(stepState) {
        if (stepState == "particles") {
            viewModel.spawnJellyBeans()
            while (true) {
                viewModel.updateJellyBeansPhysics(layoutWidth, layoutHeight)
                delay(16)
            }
        } else {
            viewModel.clearJellyBeans()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    viewModel.updateJellyBeansPhysics(layoutWidth, layoutHeight)
                }
            }
            .drawBehind {
                layoutWidth = size.width
                layoutHeight = size.height
            }
    ) {
        if (stepState == "jelly_bean") {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RedJellyBeanMascot(
                    modifier = Modifier
                        .size(170.dp)
                        .scaleAnimation { stepState = "particles" }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Android 4.1.2",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Jelly Bean",
                    color = Color(0xFF33B5E5),
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "¡Manten presionado el caramelo!",
                    color = Color.DarkGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                particles.forEach { bean ->
                    drawCircle(
                        color = bean.color,
                        center = Offset(bean.x, bean.y),
                        radius = bean.size
                    )
                    drawOval(
                        color = Color.White.copy(alpha = 0.5f),
                        topLeft = Offset(bean.x - bean.size * 0.4f, bean.y - bean.size * 0.7f),
                        size = Size(bean.size * 0.6f, bean.size * 0.35f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Button(
                    onClick = { stepState = "jelly_bean" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Regresar", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FileManagerActivity(viewModel: JellyBeanViewModel) {
    var currentFolder by remember { mutableStateOf("sdcard0") }
    var selectedFileDetails by remember { mutableStateOf<String?>(null) }

    val rootFolders = listOf("DCIM", "Descargas", "Música", "Fotos", "Documentos", "Sistema")
    val folderContents = mapOf(
        "DCIM" to listOf(
            "IMG_20120713_082215.jpg" to "Imagen JPEG - 1.2 MB",
            "VID_20120715_193102.mp4" to "Video MPEG4 - 15.4 MB"
        ),
        "Descargas" to listOf(
            "jellybean_wallpaper.png" to "Imagen PNG - 845 KB",
            "playstore_update.apk" to "Paquete de Android - 4.1 MB",
            "reporte_trimestre.pdf" to "Documento PDF - 1.2 MB"
        ),
        "Música" to listOf(
            "01_jelly_bean_groove.mp3" to "Audio MP3 - 5.1 MB",
            "android_4.1_system_ringtone.mp3" to "Audio MP3 - 1.8 MB"
        ),
        "Fotos" to listOf(
            "retro_nexus_4.jpg" to "Imagen JPEG - 2.1 MB",
            "holo_blue_palette.png" to "Imagen PNG - 512 KB"
        ),
        "Documentos" to listOf(
            "guia_retro_nexus.txt" to "Archivo de texto - 12 KB",
            "nota_asistente.txt" to "Archivo de texto - 4 KB"
        ),
        "Sistema" to listOf(
            "build.prop" to "Archivo de configuración - 8 KB",
            "hosts" to "Archivo de hosts - 2 KB"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // App title/header bar (retro Jelly Bean grey theme)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF333333))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0xFF33B5E5),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Gestor de archivos",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Breadcrumbs & Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF262626))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentFolder != "sdcard0") {
                IconButton(
                    onClick = { currentFolder = "sdcard0" },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Regresar",
                        tint = Color.LightGray
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = "sdcard0" + if (currentFolder != "sdcard0") "  >  $currentFolder" else "",
                color = Color(0xFF33B5E5),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Divider(color = Color(0xFF141414), thickness = 1.dp)

        // Contents
        if (currentFolder == "sdcard0") {
            // Root Folders
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(rootFolders) { folder ->
                    val fileCount = folderContents[folder]?.size ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentFolder = folder }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Carpeta",
                            tint = Color(0xFF33B5E5),
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = folder,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$fileCount elementos",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Divider(color = Color(0xFF2D2D2D), thickness = 0.5.dp)
                }
            }
        } else {
            // Subfolder contents
            val files = folderContents[currentFolder] ?: emptyList()
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Carpeta vacía", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(files) { (fileName, fileDesc) ->
                        val icon = when {
                            fileName.endsWith(".mp3") -> Icons.Default.AudioFile
                            fileName.endsWith(".jpg") || fileName.endsWith(".png") -> Icons.Default.Image
                            fileName.endsWith(".apk") -> Icons.Default.Android
                            else -> Icons.Default.Description
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFileDetails = "$fileName\n$fileDesc" }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Archivo",
                                tint = Color(0xFF99CC00),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = fileName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = fileDesc,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Divider(color = Color(0xFF2D2D2D), thickness = 0.5.dp)
                    }
                }
            }
        }

        // Simulated Storage Stats bar at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2E2E2E))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Almacenamiento interno", color = Color.LightGray, fontSize = 12.sp)
                Text("11.2 GB / 16.0 GB libres", color = Color.LightGray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { 0.3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF33B5E5),
                trackColor = Color(0xFF151515)
            )
        }
    }

    // Details alert dialog
    if (selectedFileDetails != null) {
        AlertDialog(
            onDismissRequest = { selectedFileDetails = null },
            containerColor = Color(0xFF2C2C2C),
            title = {
                Text(text = "Propiedades de archivo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = selectedFileDetails ?: "", color = Color.LightGray, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = { selectedFileDetails = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33B5E5))
                ) {
                    Text(text = "Aceptar", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun ThemesActivity(viewModel: JellyBeanViewModel) {
    val currentTheme by viewModel.themeColorProfile.collectAsState()

    val themes = listOf(
        Triple("BLUE", "Holo Azul Clásico", Color(0xFF33B5E5)),
        Triple("PURPLE", "Holo Violeta Aura", Color(0xFFBA68C8)),
        Triple("GREEN", "Holo Esmeralda", Color(0xFF99CC00)),
        Triple("RED", "Holo Carmesí", Color(0xFFFF4444)),
        Triple("AMBER", "Holo Atardecer", Color(0xFFFFBB33))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161616))
    ) {
        // Theme title header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = Color(0xFF33B5E5),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Personalización Holo",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Header explanatory banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(14.dp)
        ) {
            Text(
                text = "Cambia el perfil de color del sistema. Afectará las ondas dinámicas del fondo, los elementos activos y el estilo general.",
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Themes Lists
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(themes) { (profileKey, profileName, accentColor) ->
                val isSelected = currentTheme == profileKey
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setThemeColorProfile(profileKey) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0x33333333) else Color(0x19FFFFFF)
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accentColor else Color.DarkGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(accentColor, accentColor.copy(alpha = 0.2f))
                                        )
                                    )
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = profileName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Estilo Holo " + profileKey.lowercase().replaceFirstChar { it.uppercase() },
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live interactive widget preview card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Vista previa interactiva",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val currentAccent = themes.find { it.first == currentTheme }?.third ?: Color(0xFF33B5E5)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Activar Bluetooth", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = currentAccent,
                            checkedTrackColor = currentAccent.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = currentAccent),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Acción primaria", color = Color.White, fontSize = 11.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(currentAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✔", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

fun Modifier.scaleAnimation(onHold: () -> Unit) = this.pointerInput(Unit) {
    detectDragGestures(
        onDrag = { _, _ -> },
        onDragEnd = { onHold() }
    )
}

// ==========================================
// SYSTEM AUXILIARY LEVEL LAYOUTS & WIDGETS
// ==========================================

@Composable
fun SoftNavigationKeys(viewModel: JellyBeanViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.Black),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Soft Key: Back
        Box(
            modifier = Modifier
                .size(72.dp, 44.dp)
                .holoTap(
                    shape = RoundedCornerShape(6.dp),
                    onClick = {
                        viewModel.closeGoogleFolder()
                        val isDrawerOpen = viewModel.isDrawerOpen.value
                        val isShadeOpen = viewModel.isNotificationShadeOpen.value
                        val activeApp = viewModel.activeApp.value

                        if (isShadeOpen) {
                            viewModel.toggleNotificationShade()
                        } else if (isDrawerOpen) {
                            viewModel.closeDrawer()
                        } else if (activeApp != null) {
                            viewModel.closeActiveApp()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = "Soft key Back",
                tint = Color(0xFFDCDCDC),
                modifier = Modifier.size(26.dp)
            )
        }

        // Soft Key: Home
        Box(
            modifier = Modifier
                .size(72.dp, 44.dp)
                .holoTap(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { viewModel.goHome() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Soft key Home",
                tint = Color(0xFFDCDCDC),
                modifier = Modifier.size(28.dp)
            )
        }

        // Soft Key: Recents (Overlapping dual-squares mimicry)
        Box(
            modifier = Modifier
                .size(72.dp, 44.dp)
                .holoTap(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { viewModel.toggleRecentApps() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Soft key Recents layout switcher",
                tint = Color(0xFFDCDCDC),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun RecentAppCard(
    app: AppType,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX)
    val alpha = (1f - (Math.abs(animatedOffsetX) / 320f)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(animatedOffsetX.toInt(), 0) }
            .graphicsLayer(alpha = alpha)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (Math.abs(offsetX) > 130f) {
                            offsetX = if (offsetX > 0) 450f else -450f
                            onDismiss()
                        } else {
                            offsetX = 0f
                        }
                    },
                    onDragCancel = {
                        offsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                    }
                )
            }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .width(280.dp)
                .height(115.dp)
                .clickable { onLaunch() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161B)),
            border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0F))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JellyBeanAppIcon(
                        app = app,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = app.appName.uppercase(),
                        color = Color(0xFF33B5E5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDismiss() }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0F0F12))
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF33B5E5).copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                .border(0.5.dp, Color(0xFF33B5E5).copy(alpha = 0.4f), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (app) {
                                    AppType.CLOCK -> Icons.Default.Schedule
                                    AppType.CAMERA -> Icons.Default.CameraAlt
                                    AppType.BROWSER -> Icons.Default.Language
                                    AppType.SETTINGS -> Icons.Default.Settings
                                    AppType.MUSIC -> Icons.Default.MusicNote
                                    AppType.AI_GENERATOR -> Icons.Default.AutoAwesome
                                    AppType.SOUND_RECORDER -> Icons.Default.Mic
                                    AppType.DOWNLOADS -> Icons.Default.CloudDownload
                                    else -> Icons.Default.Apps
                                },
                                contentDescription = null,
                                tint = Color(0xFF33B5E5),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Instancia Activa",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ID Proceso: ${app.name.hashCode().coerceAtLeast(1000) % 99999}",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentAppsOverlay(viewModel: JellyBeanViewModel) {
    val recents by viewModel.recentAppsList.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { viewModel.toggleRecentApps() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .padding(vertical = 48.dp)
                .clickable(enabled = false) {}, // prevent click-through
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "APLICACIONES RECIENTES",
                color = Color(0xFF33B5E5),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (recents.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay aplicaciones recientes",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(recents, key = { it.name }) { app ->
                        RecentAppCard(
                            app = app,
                            onLaunch = {
                                viewModel.launchApp(app)
                                viewModel.toggleRecentApps()
                            },
                            onDismiss = {
                                viewModel.removeRecentApp(app)
                                viewModel.playSystemSound("click")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clear all button styled in classic Holo red/gray
                Button(
                    onClick = {
                        viewModel.clearRecentApps()
                        viewModel.playSystemSound("click")
                        viewModel.toggleRecentApps()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1010)),
                    border = BorderStroke(1.dp, Color(0xFFFF4444).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        text = "CERRAR TODAS",
                        color = Color(0xFFFF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopIconItem(app: AppType, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .holoTap(shape = RoundedCornerShape(8.dp), onClick = onClick)
            .padding(8.dp)
    ) {
        JellyBeanAppIcon(
            app = app,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.appName,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnimatedDockItemContainer(
    onClick: () -> Unit,
    content: @Composable BoxScope.(isPressed: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.22f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dock_scale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.65f else 0.0f,
        animationSpec = tween(durationMillis = 80),
        label = "dock_glow"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(62.dp)
    ) {
        // Holo blue radial glow behind the icon when pressed
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF33B5E5).copy(alpha = glowAlpha), Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width / 1.5f
                            )
                        )
                    }
            )
        }

        content(isPressed)
    }
}

@Composable
fun DockIconItem(app: AppType, onClick: () -> Unit) {
    AnimatedDockItemContainer(onClick = onClick) { isPressed ->
        JellyBeanAppIcon(
            app = app,
            modifier = Modifier.size(52.dp)
        )
    }
}

@Composable
fun JellyBeanAppIcon(app: AppType, modifier: Modifier = Modifier) {
    when (app) {
        AppType.PHONE -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Shadow
                    drawContext.canvas.save()
                    drawContext.canvas.translate(2.dp.toPx(), 2.dp.toPx())
                    val shadowHandsetPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.20f, h * 0.24f)
                        quadraticTo(w * 0.34f, h * 0.12f, w * 0.42f, h * 0.18f)
                        lineTo(w * 0.32f, h * 0.28f)
                        quadraticTo(w * 0.46f, h * 0.48f, w * 0.64f, h * 0.64f)
                        lineTo(w * 0.74f, h * 0.54f)
                        quadraticTo(w * 0.82f, h * 0.62f, w * 0.70f, h * 0.76f)
                        quadraticTo(w * 0.44f, h * 0.82f, w * 0.20f, h * 0.56f)
                        quadraticTo(w * 0.08f, h * 0.36f, w * 0.20f, h * 0.24f)
                        close()
                    }
                    drawPath(path = shadowHandsetPath, color = Color.Black.copy(alpha = 0.25f))
                    drawContext.canvas.restore()

                    // Glowing Cyan Handset receiver
                    val handsetPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.20f, h * 0.24f)
                        quadraticTo(w * 0.34f, h * 0.12f, w * 0.42f, h * 0.18f)
                        lineTo(w * 0.32f, h * 0.28f)
                        quadraticTo(w * 0.46f, h * 0.48f, w * 0.64f, h * 0.64f)
                        lineTo(w * 0.74f, h * 0.54f)
                        quadraticTo(w * 0.82f, h * 0.62f, w * 0.70f, h * 0.76f)
                        quadraticTo(w * 0.44f, h * 0.82f, w * 0.20f, h * 0.56f)
                        quadraticTo(w * 0.08f, h * 0.36f, w * 0.20f, h * 0.24f)
                        close()
                    }

                    drawPath(
                        path = handsetPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF008CCF), Color(0xFF005FAA))
                        )
                    )

                    // Ambient highlight gloss line across the upper edge of receiver
                    val highlightPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.21f, h * 0.26f)
                        quadraticTo(w * 0.30f, h * 0.18f, w * 0.38f, h * 0.22f)
                        quadraticTo(w * 0.44f, h * 0.35f, w * 0.54f, h * 0.48f)
                        quadraticTo(w * 0.62f, h * 0.58f, w * 0.68f, h * 0.64f)
                    }
                    drawPath(
                        path = highlightPath,
                        color = Color.White.copy(alpha = 0.5f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Little sound speaker slots in top lobe (classic details)
                    drawCircle(color = Color(0x33000000), radius = 1.dp.toPx(), center = Offset(w * 0.28f, h * 0.24f))
                    drawCircle(color = Color(0x33000000), radius = 1.dp.toPx(), center = Offset(w * 0.32f, h * 0.22f))
                    drawCircle(color = Color(0x33000000), radius = 1.dp.toPx(), center = Offset(w * 0.30f, h * 0.26f))
                }
            }
        }
        AppType.BROWSER -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val r = w * 0.44f

                    // Shadow behind the globe
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.3f),
                        radius = r + 1.dp.toPx(),
                        center = Offset(cx, cy + 2.dp.toPx())
                    )

                    // Base Globe Gradient (Blue/Cyan/Ocean)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00B2EE), Color(0xFF005F9E), Color(0xFF002F6C)),
                            center = Offset(cx - r * 0.2f, cy - r * 0.2f),
                            radius = r * 1.5f
                        ),
                        radius = r,
                        center = Offset(cx, cy)
                    )

                    // Clip to globe circle for lines
                    val globePath = androidx.compose.ui.graphics.Path().apply {
                        addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(globePath)

                    // Horizontal glowing latitude lines (with gaps, similar to grid / continent texture in browser.png)
                    val linesCount = 8
                    for (i in 1..linesCount) {
                        val fraction = i.toFloat() / (linesCount + 1)
                        val y = cy - r + (r * 2 * fraction)
                        val dist = Math.abs(fraction - 0.5f) * 2f
                        val thickness = (4.dp.toPx() * (1f - dist * 0.5f)).coerceAtLeast(1.5.dp.toPx())
                        
                        drawLine(
                            color = Color(0xCCFFFFFF),
                            start = Offset(cx - r, y),
                            end = Offset(cx + r, y),
                            strokeWidth = thickness
                        )
                    }

                    // Curved vertical longitude arcs (left and right)
                    drawOval(
                        color = Color(0x88FFFFFF),
                        style = Stroke(width = 2.dp.toPx()),
                        topLeft = Offset(cx - r * 0.5f, cy - r),
                        size = Size(r, r * 2)
                    )
                    drawOval(
                        color = Color(0x55FFFFFF),
                        style = Stroke(width = 1.dp.toPx()),
                        topLeft = Offset(cx - r * 0.8f, cy - r),
                        size = Size(r * 1.6f, r * 2)
                    )

                    // Bright cyan/pink light leak glow at the top rim like browser.png
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF007F).copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(cx, cy - r * 0.9f),
                            radius = r * 0.8f
                        ),
                        radius = r,
                        center = Offset(cx, cy)
                    )

                    // Ambient glossy/glassy lighting reflection on top right hemisphere
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)
                        ),
                        topLeft = Offset(cx - r * 0.6f, cy - r * 0.8f),
                        size = Size(r * 1.2f, r * 0.6f)
                    )

                    drawContext.canvas.restore()

                    // Glow outer outline ring
                    drawCircle(
                        color = Color(0x33FFFFFF),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
        AppType.MESSAGING -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Shadow behind the speech bubble
                    val shadowPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.12f, h * 0.12f + 2.dp.toPx())
                        lineTo(w * 0.88f, h * 0.12f + 2.dp.toPx())
                        lineTo(w * 0.88f, h * 0.72f + 2.dp.toPx())
                        lineTo(w * 0.32f, h * 0.72f + 2.dp.toPx())
                        lineTo(w * 0.20f, h * 0.88f + 2.dp.toPx())
                        lineTo(w * 0.20f, h * 0.72f + 2.dp.toPx())
                        lineTo(w * 0.12f, h * 0.72f + 2.dp.toPx())
                        close()
                    }
                    drawPath(path = shadowPath, color = Color.Black.copy(alpha = 0.25f))

                    // Green Speech bubble with rounded corners
                    val bubbleRect = Rect(w * 0.08f, h * 0.10f, w * 0.92f, h * 0.70f)
                    val bubblePath = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(RoundRect(bubbleRect, CornerRadius(8.dp.toPx(), 8.dp.toPx())))
                        // Pointer at bottom left
                        moveTo(w * 0.28f, h * 0.70f)
                        lineTo(w * 0.22f, h * 0.88f)
                        quadraticTo(w * 0.24f, h * 0.78f, w * 0.40f, h * 0.70f)
                        close()
                    }

                    drawPath(
                        path = bubblePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF99CC00), Color(0xFF669900))
                        )
                    )

                    // Outer stroke of the green bubble
                    drawPath(
                        path = bubblePath,
                        color = Color(0xFF7CA300),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Smiley face (from messaging.png)
                    val smileyColor = Color.White
                    drawCircle(color = smileyColor, radius = 2.5.dp.toPx(), center = Offset(w * 0.36f, h * 0.34f))
                    drawCircle(color = smileyColor, radius = 2.5.dp.toPx(), center = Offset(w * 0.64f, h * 0.34f))

                    val smilePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.28f, h * 0.45f)
                        quadraticTo(w * 0.5f, h * 0.62f, w * 0.72f, h * 0.45f)
                        quadraticTo(w * 0.5f, h * 0.55f, w * 0.28f, h * 0.45f)
                        close()
                    }
                    drawPath(path = smilePath, color = smileyColor)
                }
            }
        }
        AppType.CAMERA -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Shadow
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(w * 0.08f, h * 0.26f),
                        size = Size(w * 0.84f, h * 0.62f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Metallic top chassis (silver cap)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFE2E2E2), Color(0xFFB0B0B0))
                        ),
                        topLeft = Offset(w * 0.08f, h * 0.22f),
                        size = Size(w * 0.84f, h * 0.24f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Shutter button (metallic on top-left)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFF0F0F0), Color(0xFF999999))
                        ),
                        topLeft = Offset(w * 0.16f, h * 0.16f),
                        size = Size(w * 0.18f, h * 0.08f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // Red/amber LED window on top-right (sensor/flash indicator)
                    drawRoundRect(
                        color = Color(0xFFEAEAEA),
                        topLeft = Offset(w * 0.66f, h * 0.28f),
                        size = Size(w * 0.16f, h * 0.08f),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )

                    // Textured bottom body (black leather style)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF28282B), Color(0xFF141416))
                        ),
                        topLeft = Offset(w * 0.08f, h * 0.44f),
                        size = Size(w * 0.84f, h * 0.42f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Main central camera lens barrel (outer ring)
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF555555), Color(0xFF222222))
                        ),
                        radius = w * 0.25f,
                        center = Offset(w * 0.5f, h * 0.58f)
                    )
                    drawCircle(
                        color = Color(0xFF121212),
                        radius = w * 0.22f,
                        center = Offset(w * 0.5f, h * 0.58f)
                    )

                    // Glass lens element (gorgeous deep blue / cyan radial gradient reflecting camera.png)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00A3FF), Color(0xFF003C9E), Color(0xFF000830)),
                            center = Offset(w * 0.46f, h * 0.54f),
                            radius = w * 0.18f
                        ),
                        radius = w * 0.18f,
                        center = Offset(w * 0.5f, h * 0.58f)
                    )

                    // Camera glass specular reflection / glare (angled white soft glare ellipse)
                    drawOval(
                        color = Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(w * 0.40f, h * 0.48f),
                        size = Size(w * 0.14f, w * 0.07f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = w * 0.03f,
                        center = Offset(w * 0.60f, h * 0.66f)
                    )
                }
            }
        }
        AppType.DOWNLOADS -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Outer dark circle (metallic gradient)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF3C3D42), Color(0xFF1E1F22))
                        ),
                        radius = w * 0.45f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    // Inner circle
                    drawCircle(
                        color = Color(0xFF111113),
                        radius = w * 0.35f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    // Green Arrow
                    val arrowWidth = w * 0.16f
                    val arrowHeadSize = w * 0.32f
                    val cx = w * 0.5f
                    val topY = h * 0.28f
                    val arrowLength = h * 0.32f
                    val shaftEndY = topY + arrowLength

                    // Arrow shaft (green line/rectangle)
                    drawLine(
                        color = Color(0xFF99CC00),
                        start = Offset(cx, topY),
                        end = Offset(cx, shaftEndY),
                        strokeWidth = arrowWidth,
                        cap = StrokeCap.Butt
                    )

                    // Arrow head (triangle)
                    val headPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - arrowHeadSize * 0.5f, shaftEndY)
                        lineTo(cx + arrowHeadSize * 0.5f, shaftEndY)
                        lineTo(cx, shaftEndY + arrowHeadSize * 0.5f)
                        close()
                    }
                    drawPath(
                        path = headPath,
                        color = Color(0xFF99CC00)
                    )

                    // Horizontal line under arrow
                    drawLine(
                        color = Color(0xFF99CC00),
                        start = Offset(cx - arrowHeadSize * 0.6f, shaftEndY + arrowHeadSize * 0.8f),
                        end = Offset(cx + arrowHeadSize * 0.6f, shaftEndY + arrowHeadSize * 0.8f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        AppType.SETTINGS -> {
            Box(
                modifier = modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2E2E2E))
                    .border(2.5.dp, Color(0xFF00E5FF), RoundedCornerShape(6.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SliderTrackDraw(knobPosition = 0.65f)
                    SliderTrackDraw(knobPosition = 0.35f)
                    SliderTrackDraw(knobPosition = 0.80f)
                }
            }
        }
        AppType.CALCULATOR -> {
            Box(
                modifier = modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF4A4A4D))
                    .border(1.dp, Color(0xFF6E6E72), RoundedCornerShape(6.dp))
                    .padding(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF303032), RoundedCornerShape(3.dp))
                                .border(0.5.dp, Color(0xFF5E5E62), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.width(10.dp).height(2.5.dp).background(Color.White))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxWidth()
                                .background(Color(0xFF303032), RoundedCornerShape(3.dp))
                                .border(0.5.dp, Color(0xFF5E5E62), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.width(10.dp).height(2.5.dp).background(Color.White))
                                Box(modifier = Modifier.width(2.5.dp).height(10.dp).background(Color.White))
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF3A3A3D), RoundedCornerShape(3.dp))
                            .border(0.5.dp, Color(0xFF5E5E62), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.width(12.dp).height(2.5.dp).background(Color.White))
                            Box(modifier = Modifier.width(12.dp).height(2.5.dp).background(Color.White))
                        }
                    }
                }
            }
        }
        AppType.MARKET -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    val handlePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.35f, h * 0.35f)
                        quadraticTo(w * 0.5f, h * 0.05f, w * 0.65f, h * 0.35f)
                    }
                    drawPath(
                        path = handlePath,
                        color = Color(0xFFC0C0C0),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val bagPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.16f, h * 0.32f)
                        lineTo(w * 0.84f, h * 0.32f)
                        lineTo(w * 0.88f, h * 0.92f)
                        lineTo(w * 0.12f, h * 0.92f)
                        close()
                    }
                    drawPath(
                        path = bagPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFAFAFA), Color(0xFFD4D4D4))
                        )
                    )
                    drawPath(
                        path = bagPath,
                        color = Color(0xFFB0B0B0),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )

                    val cw = w * 0.5f
                    val ch = h * 0.62f
                    val side = w * 0.22f

                    val p1 = Offset(cw - side * 0.4f, ch - side * 0.5f)
                    val p2 = Offset(cw + side * 0.6f, ch)
                    val p3 = Offset(cw - side * 0.4f, ch + side * 0.5f)

                    val playPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }
                    drawPath(
                        path = playPath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF99CC00),
                                Color(0xFFFFBB33),
                                Color(0xFFFF4444)
                            ),
                            start = p1,
                            end = p2
                        )
                    )
                }
            }
        }
        AppType.PEOPLE -> {
            Box(
                modifier = modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF00AAFF), Color(0xFF0055BB))
                        )
                    )
                    .border(1.dp, Color(0xFF33B5E5), RoundedCornerShape(6.dp))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(3.5f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(14.dp)
                                    .background(Color.White, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0x22FFFFFF), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.2f)),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(width = 6.dp, height = 3.dp)
                                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }
        }
        AppType.GALLERY -> {
            Box(
                modifier = modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF333333))
                    .border(2.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8800CC), Color(0xFFFF4444), Color(0xFFFFBB33))
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(0.5.dp, Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }
        AppType.MAPS -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val s1 = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.15f, h * 0.25f)
                        lineTo(w * 0.45f, h * 0.15f)
                        lineTo(w * 0.45f, h * 0.75f)
                        lineTo(w * 0.15f, h * 0.85f)
                        close()
                    }
                    drawPath(
                        path = s1,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF99CC00), Color(0xFF749A00))
                        )
                    )

                    val s2 = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.45f, h * 0.15f)
                        lineTo(w * 0.75f, h * 0.28f)
                        lineTo(w * 0.75f, h * 0.88f)
                        lineTo(w * 0.45f, h * 0.75f)
                        close()
                    }
                    drawPath(
                        path = s2,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFFBB33), Color(0xFFCC9629))
                        )
                    )

                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.2f, h * 0.5f),
                        end = Offset(w * 0.7f, h * 0.45f),
                        strokeWidth = 3f
                    )

                    drawOval(
                        color = Color.Black.copy(alpha = 0.3f),
                        topLeft = Offset(w * 0.52f, h * 0.65f),
                        size = Size(w * 0.16f, h * 0.07f)
                    )

                    val pinX = w * 0.6f
                    val pinY = h * 0.46f
                    val r = w * 0.16f

                    val pinPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pinX, pinY + r * 1.2f)
                        cubicTo(
                            pinX - r, pinY + r * 0.4f,
                            pinX - r, pinY - r * 0.4f,
                            pinX, pinY - r
                        )
                        cubicTo(
                            pinX + r, pinY - r * 0.4f,
                            pinX + r, pinY + r * 0.4f,
                            pinX, pinY + r * 1.2f
                        )
                        close()
                    }
                    drawPath(path = pinPath, color = Color(0xFFFF4444))

                    drawCircle(
                        color = Color.White,
                        radius = r * 0.4f,
                        center = Offset(pinX, pinY)
                    )
                }
            }
        }
        AppType.CLOCK -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val r = w * 0.44f

                    // Shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.3f),
                        radius = r,
                        center = Offset(cx, cy + 1.5.dp.toPx())
                    )

                    // Outer clean white bezel/rim
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFCFCFC), Color(0xFFE5E5E8))
                        ),
                        radius = r,
                        center = Offset(cx, cy)
                    )

                    // Inner slate-gray clock face
                    val faceRadius = r * 0.88f
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF2C2D32), Color(0xFF1E1E22))
                        ),
                        radius = faceRadius,
                        center = Offset(cx, cy)
                    )

                    // White hour tick marks (subtle)
                    for (angle in 0 until 360 step 30) {
                        val rad = Math.toRadians(angle.toDouble())
                        val length = if (angle % 90 == 0) 3.dp.toPx() else 1.5.dp.toPx()
                        val thickness = if (angle % 90 == 0) 1.5.dp.toPx() else 1.dp.toPx()
                        val startDist = faceRadius - length - 2.dp.toPx()
                        val endDist = faceRadius - 2.dp.toPx()

                        drawLine(
                            color = Color(0x88FFFFFF),
                            start = Offset(cx + (Math.cos(rad) * startDist).toFloat(), cy + (Math.sin(rad) * startDist).toFloat()),
                            end = Offset(cx + (Math.cos(rad) * endDist).toFloat(), cy + (Math.sin(rad) * endDist).toFloat()),
                            strokeWidth = thickness
                        )
                    }

                    // Hour Hand: white, pointing towards 10 o'clock (angle 210 degrees)
                    val hrAngle = 210.0
                    val hrRad = Math.toRadians(hrAngle)
                    val hrLen = faceRadius * 0.5f
                    drawLine(
                        color = Color.White,
                        start = Offset(cx - (Math.cos(hrRad) * 4.dp.toPx()).toFloat(), cy - (Math.sin(hrRad) * 4.dp.toPx()).toFloat()),
                        end = Offset(cx + (Math.cos(hrRad) * hrLen).toFloat(), cy + (Math.sin(hrRad) * hrLen).toFloat()),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Minute Hand: white, pointing towards 2 o'clock (angle 330 degrees)
                    val minAngle = 330.0
                    val minRad = Math.toRadians(minAngle)
                    val minLen = faceRadius * 0.75f
                    drawLine(
                        color = Color.White,
                        start = Offset(cx - (Math.cos(minRad) * 4.dp.toPx()).toFloat(), cy - (Math.sin(minRad) * 4.dp.toPx()).toFloat()),
                        end = Offset(cx + (Math.cos(minRad) * minLen).toFloat(), cy + (Math.sin(minRad) * minLen).toFloat()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Second Hand: bright neon orange/red, pointing towards 12 o'clock (90 degrees)
                    val secAngle = 90.0
                    val secRad = Math.toRadians(secAngle)
                    val secLen = faceRadius * 0.85f
                    drawLine(
                        color = Color(0xFFFF4444),
                        start = Offset(cx - (Math.cos(secRad) * 6.dp.toPx()).toFloat(), cy - (Math.sin(secRad) * 6.dp.toPx()).toFloat()),
                        end = Offset(cx + (Math.cos(secRad) * secLen).toFloat(), cy + (Math.sin(secRad) * secLen).toFloat()),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Center pin (white caps)
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = Color(0xFFFF4444), radius = 1.dp.toPx(), center = Offset(cx, cy))

                    if (false) {

                    // Clock metal bells
                    drawCircle(color = Color(0xFF666666), radius = w * 0.12f, center = Offset(w * 0.22f, h * 0.22f))
                    drawCircle(color = Color(0xFF666666), radius = w * 0.12f, center = Offset(w * 0.78f, h * 0.22f))

                    // Clock body
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2C2C2C), Color(0xFF141414))
                        ),
                        radius = w * 0.42f,
                        center = Offset(w * 0.5f, h * 0.52f)
                    )

                    // Holo Blue Outer Ring
                    drawCircle(
                        color = Color(0xFF33B5E5),
                        radius = w * 0.40f,
                        center = Offset(w * 0.5f, h * 0.52f),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Clock ticks
                    for (angle in 0 until 360 step 30) {
                        val rad = Math.toRadians(angle.toDouble())
                        val x1 = (w * 0.5f + Math.cos(rad) * (w * 0.33f)).toFloat()
                        val y1 = (h * 0.52f + Math.sin(rad) * (h * 0.33f)).toFloat()
                        val x2 = (w * 0.5f + Math.cos(rad) * (w * 0.38f)).toFloat()
                        val y2 = (h * 0.52f + Math.sin(rad) * (h * 0.38f)).toFloat()
                        drawLine(color = Color(0xFF888888), start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 1.dp.toPx())
                    }

                    // Hour hand (pointing to 10)
                    val hrRad = Math.toRadians(210.0)
                    val hrX = (w * 0.5f + Math.cos(hrRad) * (w * 0.2f)).toFloat()
                    val hrY = (h * 0.52f + Math.sin(hrRad) * (h * 0.2f)).toFloat()
                    drawLine(color = Color.White, start = Offset(w * 0.5f, h * 0.52f), end = Offset(hrX, hrY), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)

                    // Minute hand (pointing to 10 mins post hour)
                    val minRad = Math.toRadians(300.0)
                    val minX = (w * 0.5f + Math.cos(minRad) * (w * 0.3f)).toFloat()
                    val minY = (h * 0.52f + Math.sin(minRad) * (h * 0.3f)).toFloat()
                    drawLine(color = Color.White, start = Offset(w * 0.5f, h * 0.52f), end = Offset(minX, minY), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)

                    }
                }
            }
        }
        AppType.MUSIC -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Orange Play Music style square base
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFE04000), Color(0xFFC03000))
                        ),
                        cornerRadius = CornerRadius(w * 0.12f, h * 0.12f)
                    )

                    // Vinyl grooves
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.6f),
                        radius = w * 0.4f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = w * 0.35f,
                        center = Offset(w * 0.5f, h * 0.5f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = w * 0.28f,
                        center = Offset(w * 0.5f, h * 0.5f),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Gold center
                    drawCircle(
                        color = Color(0xFFFFBB33),
                        radius = w * 0.15f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    // Center hole
                    drawCircle(
                        color = Color(0xFF222222),
                        radius = w * 0.04f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    // Music Note
                    val notePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.48f, h * 0.35f)
                        lineTo(w * 0.62f, h * 0.28f)
                        lineTo(w * 0.62f, h * 0.52f)
                        quadraticTo(w * 0.5f, h * 0.55f, w * 0.44f, h * 0.6f)
                        quadraticTo(w * 0.38f, h * 0.65f, w * 0.44f, h * 0.7f)
                        quadraticTo(w * 0.52f, h * 0.72f, w * 0.56f, h * 0.64f)
                        lineTo(w * 0.56f, h * 0.38f)
                        lineTo(w * 0.48f, h * 0.42f)
                        close()
                    }
                    drawPath(path = notePath, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        AppType.CALENDAR -> {
            Box(
                modifier = modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEFEFEF))
                    .border(1.dp, Color(0xFFB0B0B0), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Red or Blue Header Band
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(Color(0xFF33B5E5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.size(2.dp, 4.dp).background(Color.White))
                            Box(modifier = Modifier.size(2.dp, 4.dp).background(Color.White))
                            Box(modifier = Modifier.size(2.dp, 4.dp).background(Color.White))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "23", // Classic Jelly Bean calendar day placeholder icon
                            color = Color(0xFF333333),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
        AppType.ABOUT -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF33B5E5).copy(alpha = 0.4f), Color.Transparent),
                            radius = w * 0.45f
                        ),
                        radius = w * 0.45f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    val jbWidth = w * 0.56f
                    val jbHeight = h * 0.42f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFF4444), Color(0xFF880000))
                        ),
                        topLeft = Offset(w * 0.51f - jbWidth / 2f, h * 0.51f - jbHeight / 2f),
                        size = Size(jbWidth, jbHeight),
                        cornerRadius = CornerRadius(w * 0.18f, h * 0.18f)
                    )

                    drawOval(
                        color = Color.White.copy(alpha = 0.5f),
                        topLeft = Offset(w * 0.28f, h * 0.36f),
                        size = Size(w * 0.24f, h * 0.08f)
                    )

                    drawCircle(color = Color.Black, radius = 5f, center = Offset(w * 0.41f, h * 0.52f))
                    drawCircle(color = Color.Black, radius = 5f, center = Offset(w * 0.59f, h * 0.52f))

                    drawLine(
                        color = Color(0xFFFF4444),
                        start = Offset(w * 0.44f, h * 0.35f),
                        end = Offset(w * 0.34f, h * 0.22f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFF4444),
                        start = Offset(w * 0.56f, h * 0.35f),
                        end = Offset(w * 0.66f, h * 0.22f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        AppType.FILE_MANAGER -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    val folderPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.12f, h * 0.22f)
                        lineTo(w * 0.42f, h * 0.22f)
                        lineTo(w * 0.52f, h * 0.35f)
                        lineTo(w * 0.88f, h * 0.35f)
                        lineTo(w * 0.88f, h * 0.82f)
                        lineTo(w * 0.12f, h * 0.82f)
                        close()
                    }
                    
                    drawPath(
                        path = folderPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF424242), Color(0xFF212121))
                        )
                    )
                    
                    val pocketPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.12f, h * 0.48f)
                        lineTo(w * 0.88f, h * 0.48f)
                        lineTo(w * 0.88f, h * 0.82f)
                        lineTo(w * 0.12f, h * 0.82f)
                        close()
                    }
                    
                    drawPath(
                        path = pocketPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF33B5E5).copy(alpha = 0.8f), Color(0xFF0099CC).copy(alpha = 0.8f))
                        )
                    )
                    
                    drawPath(
                        path = folderPath,
                        color = Color.White.copy(alpha = 0.2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
        AppType.THEMES -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            radius = w * 0.48f
                        ),
                        radius = w * 0.48f
                    )
                    
                    val colors = listOf(Color(0xFF33B5E5), Color(0xFF99CC00), Color(0xFFFFBB33), Color(0xFFFF4444), Color(0xFFBA68C8))
                    val angles = listOf(0f, 72f, 144f, 216f, 288f)
                    
                    for (i in colors.indices) {
                        val rad = Math.toRadians(angles[i].toDouble())
                        val cx = (w * 0.5f + Math.cos(rad) * (w * 0.24f)).toFloat()
                        val cy = (h * 0.5f + Math.sin(rad) * (h * 0.24f)).toFloat()
                        drawCircle(
                            color = colors[i],
                            radius = w * 0.15f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = w * 0.15f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }
        }
        AppType.AI_GENERATOR -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Glowing Holo background circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF33B5E5).copy(alpha = 0.4f), Color.Transparent),
                            radius = w * 0.48f
                        ),
                        radius = w * 0.48f
                    )

                    // Base glass dark/cyan button
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF2C2C2C), Color(0xFF151515))
                        ),
                        radius = w * 0.38f
                    )

                    // Holo cyan ring
                    drawCircle(
                        color = Color(0xFF33B5E5),
                        radius = w * 0.38f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // AutoAwesome AI Sparkles inside the orb
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        AppType.SOUND_RECORDER -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Cassette tape background
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF3A3A3A), Color(0xFF1C1C1C))
                        ),
                        topLeft = Offset(w * 0.1f, h * 0.22f),
                        size = Size(w * 0.8f, h * 0.56f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    
                    // Cyan cassette accent labels
                    drawRect(
                        color = Color(0xFF33B5E5),
                        topLeft = Offset(w * 0.2f, h * 0.35f),
                        size = Size(w * 0.6f, h * 0.14f)
                    )
                    
                    // Left and right tape wheels
                    drawCircle(color = Color.Black, radius = w * 0.09f, center = Offset(w * 0.35f, h * 0.53f))
                    drawCircle(color = Color.Black, radius = w * 0.09f, center = Offset(w * 0.65f, h * 0.53f))
                    
                    // Gear teeth in wheels
                    drawCircle(color = Color.LightGray, radius = w * 0.035f, center = Offset(w * 0.35f, h * 0.53f))
                    drawCircle(color = Color.LightGray, radius = w * 0.035f, center = Offset(w * 0.65f, h * 0.53f))
                }
            }
        }
        AppType.NOTES -> {
            Box(
                modifier = modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFEA70))
                    .border(1.5.dp, Color(0xFFE5C100), RoundedCornerShape(4.dp))
                    .padding(5.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top red binder strip
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFFFF4444)))
                    // Notepad horizontal ruling lines
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color(0x33000000)))
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color(0x33000000)))
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color(0x33000000)))
                }
            }
        }
        AppType.RADIO -> {
            Box(
                modifier = modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF333333))
                    .border(2.dp, Color(0xFF555555), RoundedCornerShape(6.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Radio speaker grille slots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(4) {
                            Box(modifier = Modifier.width(3.dp).height(12.dp).background(Color.Black))
                        }
                    }
                    // Tuning frequency bar
                    Box(
                        modifier = Modifier.fillMaxWidth().height(6.dp).background(Color.Black),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(Color(0xFF33B5E5)))
                    }
                }
            }
        }
    }
}

@Composable
fun SliderTrackDraw(knobPosition: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRoundRect(
                color = Color(0xFF141414),
                topLeft = Offset(0f, h * 0.35f),
                size = Size(w, h * 0.3f),
                cornerRadius = CornerRadius(h * 0.15f, h * 0.15f)
            )

            drawRoundRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(0f, h * 0.35f),
                size = Size(w * knobPosition, h * 0.3f),
                cornerRadius = CornerRadius(h * 0.15f, h * 0.15f)
            )

            val knobX = w * knobPosition
            val knobRadius = h * 0.42f
            
            drawCircle(
                color = Color(0xFFD0D0D4),
                radius = knobRadius,
                center = Offset(knobX, h * 0.5f)
            )
            drawCircle(
                color = Color(0xFF00B0FF),
                radius = knobRadius * 0.52f,
                center = Offset(knobX, h * 0.5f)
            )
        }
    }
}

@Composable
fun DesktopIconItem(appName: String, drawableRes: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .holoTap(shape = RoundedCornerShape(8.dp), onClick = onClick)
            .padding(8.dp)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = appName,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = appName,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DockIconItem(
    appName: String,
    drawableRes: Int? = null,
    iconVector: ImageVector? = null,
    iconBgColor: Color = Color.Gray,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        if (drawableRes != null) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = appName,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else if (iconVector != null) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(iconBgColor.copy(alpha = 0.5f), iconBgColor, Color(0xFF101010))
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = appName,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun JellyBeanAnalogClock(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF33B5E5)
) {
    var calendar by remember { mutableStateOf(java.util.Calendar.getInstance()) }
    
    // Smooth ticker
    LaunchedEffect(Unit) {
        while (true) {
            calendar = java.util.Calendar.getInstance()
            delay(1000)
        }
    }
    
    val hour = calendar.get(java.util.Calendar.HOUR)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    val second = calendar.get(java.util.Calendar.SECOND)
    
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = size.minDimension / 2 * 0.9f
        val center = Offset(w / 2, h / 2)
        
        // Face background - retro semi-transparent dark circle
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = r,
            center = center
        )
        // Thin glowing cyan border
        drawCircle(
            color = tint.copy(alpha = 0.8f),
            radius = r,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        
        // Hour dial ticks (12 hour markers)
        for (i in 0 until 12) {
            val angle = i * 30.0
            val rad = Math.toRadians(angle)
            val outerX = (center.x + Math.cos(rad) * r).toFloat()
            val outerY = (center.y + Math.sin(rad) * r).toFloat()
            
            val innerLen = if (i % 3 == 0) r * 0.82f else r * 0.88f
            val innerX = (center.x + Math.cos(rad) * innerLen).toFloat()
            val innerY = (center.y + Math.sin(rad) * innerLen).toFloat()
            
            drawLine(
                color = if (i % 3 == 0) tint else Color.White.copy(alpha = 0.6f),
                start = Offset(innerX, innerY),
                end = Offset(outerX, outerY),
                strokeWidth = if (i % 3 == 0) 2.5.dp.toPx() else 1.5.dp.toPx()
            )
        }
        
        // Draw Hour Hand
        val hourAngle = (hour + minute / 60f) * 30.0 - 90.0
        val hourRad = Math.toRadians(hourAngle)
        val hourLen = r * 0.52f
        val hourX = (center.x + Math.cos(hourRad) * hourLen).toFloat()
        val hourY = (center.y + Math.sin(hourRad) * hourLen).toFloat()
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(hourX, hourY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        
        // Draw Minute Hand
        val minAngle = (minute + second / 60f) * 6.0 - 90.0
        val minRad = Math.toRadians(minAngle)
        val minLen = r * 0.75f
        val minX = (center.x + Math.cos(minRad) * minLen).toFloat()
        val minY = (center.y + Math.sin(minRad) * minLen).toFloat()
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(minX, minY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        
        // Draw Second Hand (Classic Red / Orange)
        val secAngle = second * 6.0 - 90.0
        val secRad = Math.toRadians(secAngle)
        val secLen = r * 0.82f
        val secX = (center.x + Math.cos(secRad) * secLen).toFloat()
        val secY = (center.y + Math.sin(secRad) * secLen).toFloat()
        drawLine(
            color = Color(0xFFFF4444),
            start = center,
            end = Offset(secX, secY),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round
        )
        
        // Center Pin with glowing core
        drawCircle(color = Color(0xFFFF4444), radius = 4.dp.toPx(), center = center)
        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = center)
    }
}

@Composable
fun SoundRecorderActivity(viewModel: JellyBeanViewModel) {
    val state by viewModel.recorderState.collectAsState()
    val duration by viewModel.recorderDuration.collectAsState()
    val hasRecording by viewModel.recorderHasRecording.collectAsState()
    val visualizerHeights by viewModel.recorderVisualizerHeights.collectAsState()

    var showMicPermissionDialog by remember { mutableStateOf(false) }
    var micPermissionGranted by remember { mutableStateOf(false) }

    if (showMicPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showMicPermissionDialog = false },
            title = { Text("Permiso de Micrófono", color = Color(0xFF33B5E5)) },
            text = { Text("La grabadora de voz requiere acceso al micrófono del teléfono para grabar tus audios y guardarlos en cinta.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        micPermissionGranted = true
                        showMicPermissionDialog = false
                        viewModel.postNotification("Grabadora", "Permiso de micrófono concedido", AppType.SOUND_RECORDER)
                        viewModel.startRecording()
                    }
                ) {
                    Text("PERMITIR", color = Color(0xFF33B5E5), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMicPermissionDialog = false
                        viewModel.postNotification("Grabadora", "Permiso de micrófono denegado", AppType.SOUND_RECORDER)
                    }
                ) {
                    Text("DENEGAR", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E22)
        )
    }

    // Rotación de carretes de cinta para la animación de cassette
    val infiniteTransition = rememberInfiniteTransition()
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151515))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App title banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color(0xFF33B5E5), size = size * 0.5f, topLeft = Offset(size.width * 0.25f, size.height * 0.25f))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("GRABADORA DE VOZ", color = Color(0xFF33B5E5), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Simulador de cinta magnética Holo", color = Color.Gray, fontSize = 11.sp)
            }
        }

        // Cassette visual design card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            border = BorderStroke(1.5.dp, Color(0xFF444444)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Glass gloss lines
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(0f, 0f),
                        end = Offset(w, h),
                        strokeWidth = 8f
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top cassette brand label
                    Text(
                        text = "TDK  D90  HOLO HIGH BIAS",
                        color = Color(0xFF33B5E5).copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Tape window with wheels
                    Row(
                        modifier = Modifier
                            .width(180.dp)
                            .height(60.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Wheel
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    if (state == "RECORDING" || state == "PLAYING") {
                                        rotationZ = rotationAngle
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = Color(0xFF222222), radius = size.width * 0.48f)
                                drawCircle(color = Color.LightGray, radius = size.width * 0.25f)
                                // Teeth spokes
                                for (i in 0 until 6) {
                                    val angle = i * 60.0
                                    val rad = Math.toRadians(angle)
                                    val rx = (size.width / 2 + Math.cos(rad) * (size.width * 0.38f)).toFloat()
                                    val ry = (size.height / 2 + Math.sin(rad) * (size.height * 0.38f)).toFloat()
                                    drawLine(color = Color.Black, start = Offset(size.width / 2, size.height / 2), end = Offset(rx, ry), strokeWidth = 3f)
                                }
                            }
                        }

                        // Center magnetic tape background strip
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(30.dp)
                                .background(Color(0xFF1E1510)), // tape brown
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "JB-4.1",
                                color = Color.Gray.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Right Wheel
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    if (state == "RECORDING" || state == "PLAYING") {
                                        rotationZ = rotationAngle
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = Color(0xFF222222), radius = size.width * 0.48f)
                                drawCircle(color = Color.LightGray, radius = size.width * 0.25f)
                                // Teeth spokes
                                for (i in 0 until 6) {
                                    val angle = i * 60.0
                                    val rad = Math.toRadians(angle)
                                    val rx = (size.width / 2 + Math.cos(rad) * (size.width * 0.38f)).toFloat()
                                    val ry = (size.height / 2 + Math.sin(rad) * (size.height * 0.38f)).toFloat()
                                    drawLine(color = Color.Black, start = Offset(size.width / 2, size.height / 2), end = Offset(rx, ry), strokeWidth = 3f)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Audio LED Meter visualizer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        visualizerHeights.forEach { hVal ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(6.dp)
                                    .height(hVal.dp)
                                    .background(
                                        if (state == "RECORDING") Color(0xFFFF4444)
                                        else if (state == "PLAYING") Color(0xFF99CC00)
                                        else Color.DarkGray,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Timer duration and state layout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val mins = duration / 60
            val secs = duration % 60
            Text(
                text = String.format("%02d:%02d", mins, secs),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (state) {
                    "RECORDING" -> "• GRABANDO EN DIRECTO"
                    "PLAYING" -> "REPRODUCIENDO GRABACIÓN"
                    else -> if (hasRecording) "GRABACIÓN COMPLETADA" else "LISTO PARA GRABAR"
                },
                color = when (state) {
                    "RECORDING" -> Color(0xFFFF4444)
                    "PLAYING" -> Color(0xFF99CC00)
                    else -> Color(0xFF33B5E5)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Audio controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // DETENER button (Holo Styled Square)
            Button(
                onClick = { viewModel.stopRecording() },
                enabled = state != "IDLE",
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E2E2E),
                    disabledContainerColor = Color(0xFF1A1A1A)
                ),
                border = BorderStroke(1.dp, if (state != "IDLE") Color.White else Color.DarkGray),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.size(54.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(if (state != "IDLE") Color.White else Color.DarkGray)
                )
            }

            // GRABAR button (Big red circle)
            Button(
                onClick = {
                    if (!micPermissionGranted) {
                        showMicPermissionDialog = true
                    } else {
                        viewModel.startRecording()
                    }
                },
                enabled = state == "IDLE",
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A1212),
                    disabledContainerColor = Color(0xFF1A1A1A)
                ),
                border = BorderStroke(2.dp, if (state == "IDLE") Color(0xFFFF4444) else Color.DarkGray),
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (state == "IDLE") Color(0xFFFF4444) else Color.DarkGray)
                )
            }

            // REPRODUCIR button (Triangle play)
            Button(
                onClick = { viewModel.startPlaying() },
                enabled = state == "IDLE" && hasRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B3212),
                    disabledContainerColor = Color(0xFF1A1A1A)
                ),
                border = BorderStroke(2.dp, if (state == "IDLE" && hasRecording) Color(0xFF99CC00) else Color.DarkGray),
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = if (state == "IDLE" && hasRecording) Color(0xFF99CC00) else Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun DownloadsActivity(viewModel: JellyBeanViewModel) {
    val downloadsList by viewModel.downloads.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var inputFileName by remember { mutableStateOf("jelly_bean_ota_update.zip") }
    var inputFileSize by remember { mutableStateOf("45.2 MB") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151515))
            .padding(16.dp)
    ) {
        // App title banner with Holo theme styling
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                JellyBeanAppIcon(app = AppType.DOWNLOADS, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("DESCARGAS", color = Color(0xFF33B5E5), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Gestor de descargas de Jelly Bean 4.1", color = Color.Gray, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions Row: Download New or Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nueva Descarga",
                    tint = Color(0xFF33B5E5),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nueva Descarga", color = Color.White, fontSize = 12.sp)
            }

            Button(
                onClick = { viewModel.clearDownloads() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                border = BorderStroke(1.dp, Color.DarkGray),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Limpiar Todo",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Limpiar Lista", color = Color.LightGray, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Downloads List
        if (downloadsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No hay descargas activas", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(downloadsList) { download ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                        border = BorderStroke(
                            1.dp,
                            if (download.progress < 100) Color(0xFF33B5E5) else Color(0xFF444444)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (download.progress < 100) Icons.Default.Downloading else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (download.progress < 100) Color(0xFF33B5E5) else Color(0xFF99CC00),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = download.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = download.size,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Linear Progress Bar or Status Text
                            if (download.progress < 100) {
                                LinearProgressIndicator(
                                    progress = { download.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = Color(0xFF33B5E5),
                                    trackColor = Color.DarkGray,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = download.status,
                                    color = if (download.progress < 100) Color(0xFF33B5E5) else Color(0xFF99CC00),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (download.progress < 100) "${download.progress}%" else "Listo",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Download Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    "Simular Descarga",
                    color = Color(0xFF33B5E5),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Introduce el nombre del archivo para iniciar una descarga simulada con el motor Holo:",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = inputFileName,
                        onValueChange = { inputFileName = it },
                        label = { Text("Nombre del archivo") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF33B5E5),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputFileSize,
                        onValueChange = { inputFileSize = it },
                        label = { Text("Tamaño estimado") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF33B5E5),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        viewModel.startSimulatedDownload(inputFileName, inputFileSize)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                    border = BorderStroke(1.dp, Color(0xFF33B5E5))
                ) {
                    Text("Descargar", color = Color(0xFF33B5E5))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.LightGray
        )
    }
}
