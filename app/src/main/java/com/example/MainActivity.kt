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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.lifecycle.ViewModel
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
    ABOUT("About JB")
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

// ==========================================
// VIEWMODEL FOR JELLY BEAN SIMULATION
// ==========================================
class JellyBeanViewModel : ViewModel() {
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
    private val _isWifiEnabled = MutableStateFlow(true)
    val isWifiEnabled = _isWifiEnabled.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(true)
    val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(true)
    val isLocationEnabled = _isLocationEnabled.asStateFlow()

    private val _isDataEnabled = MutableStateFlow(true)
    val isDataEnabled = _isDataEnabled.asStateFlow()

    // Wallpaper Selection (Local and Generated)
    private val _wallpaperSelected = MutableStateFlow<Bitmap?>(null)
    val wallpaperSelected = _wallpaperSelected.asStateFlow()

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

    private val _ownerName = MutableStateFlow("Administrador Jelly Bean")
    val ownerName = _ownerName.asStateFlow()

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
            AppType.CALENDAR, AppType.ABOUT
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
    fun toggleWifi() { _isWifiEnabled.value = !_isWifiEnabled.value }
    fun toggleBluetooth() { _isBluetoothEnabled.value = !_isBluetoothEnabled.value }
    fun toggleLocation() { _isLocationEnabled.value = !_isLocationEnabled.value }
    fun toggleData() { _isDataEnabled.value = !_isDataEnabled.value }

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
    }

    fun unlock() {
        _isLocked.value = false
    }

    fun lock() {
        _isLocked.value = true
        _isDrawerOpen.value = false
        _activeApp.value = null
    }

    fun openDrawer() {
        _isDrawerOpen.value = true
        _isGoogleFolderOpen.value = false
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
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
    }

    fun closeActiveApp() {
        _activeApp.value = null
    }

    fun goHome() {
        _activeApp.value = null
        _isDrawerOpen.value = false
        _isGoogleFolderOpen.value = false
        _showRecentAppsOverlay.value = false
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

    val musicTracks = listOf(
        MusicTrack(1, "Ursa Major (Jelly Bean Retro)", "Google Retro Band", 145),
        MusicTrack(2, "Bossa de la Sonda", "Androides del Espacio", 182),
        MusicTrack(3, "Holo Blue Symphony", "Duarte & Sola", 210),
        MusicTrack(4, "Sweet Marshmallow Beat", "Key Lime Pi", 160)
    )

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
@Composable
fun JellyBeanSimulatorView(viewModel: JellyBeanViewModel) {
    val isLocked by viewModel.isLocked.collectAsState()
    val isNotificationShadeOpen by viewModel.isNotificationShadeOpen.collectAsState()
    val activeApp by viewModel.activeApp.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val showRecentsOverlay by viewModel.showRecentAppsOverlay.collectAsState()
    val wallpaperBmp by viewModel.wallpaperSelected.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("jelly_bean_simulator")
    ) {
        // Desktop Wallpaper (Generated or Classic Android 4.1 wave)
        if (wallpaperBmp != null) {
            Image(
                bitmap = wallpaperBmp!!.asImageBitmap(),
                contentDescription = "Fondo de Pantalla",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Retro Default Wallpaper
            Image(
                painter = painterResource(id = R.drawable.img_jelly_bean_wallpaper),
                contentDescription = "Retro Jelly Bean Default Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.9f
            )
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

                    // Overlay App window
                    activeApp?.let { app ->
                        JellyBeanAppWindow(app, viewModel)
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
        if (isNotificationShadeOpen) {
            NotificationShade(viewModel)
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
                Icon(
                    imageVector = if (currentTicker.appType == AppType.MESSAGING) Icons.Default.ChatBubble else Icons.Default.Info,
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
                    val hasMessaging = notifications.any { it.appType == AppType.MESSAGING }
                    val hasAbout = notifications.any { it.appType == AppType.ABOUT }
                    
                    if (hasMessaging) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "SMS Msg",
                            tint = Color(0xFF33B5E5),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (hasAbout) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "System Update",
                            tint = Color(0xFF33B5E5),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.VolumeMute,
                        contentDescription = "Silent",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(14.dp)
                    )
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
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(10.dp)
                    .border(1.dp, Color(0xFF33B5E5), RoundedCornerShape(1.dp))
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.85f)
                        .background(Color(0xFF33B5E5))
                )
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
                modifier = Modifier.testTag("lock_screen_unlock_button")
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

        val distToUnlock = (coercedOffset - Offset(targetDistancePx, 0f)).getDistance()
        val distToCamera = (coercedOffset - Offset(-targetDistancePx, 0f)).getDistance()
        val distToSearch = (coercedOffset - Offset(0f, -targetDistancePx)).getDistance()

        val targetSelectionThreshold = with(density) { 40.dp.toPx() }
        val isUnlockHighlighted = distToUnlock < targetSelectionThreshold
        val isCameraHighlighted = distToCamera < targetSelectionThreshold
        val isSearchHighlighted = distToSearch < targetSelectionThreshold

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

// 3. Classic Jelly Bean Desktop Launcher with Icons and Search Box
@Composable
fun JellyBeanDesktop(viewModel: JellyBeanViewModel) {
    val isFolderOpen by viewModel.isGoogleFolderOpen.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
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
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockIconItem(
                    app = AppType.PHONE,
                    onClick = { viewModel.launchApp(AppType.PHONE) }
                )
                DockIconItem(
                    app = AppType.PEOPLE,
                    onClick = { viewModel.launchApp(AppType.PEOPLE) }
                )

                // App Drawer trigger
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0xFF33B5E5), CircleShape)
                        .clickable { viewModel.openDrawer() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row {
                            Box(modifier = Modifier.size(5.dp).background(Color.White))
                            Spacer(modifier = Modifier.width(3.dp))
                            Box(modifier = Modifier.size(5.dp).background(Color.White))
                            Spacer(modifier = Modifier.width(3.dp))
                            Box(modifier = Modifier.size(5.dp).background(Color.White))
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row {
                            Box(modifier = Modifier.size(5.dp).background(Color.White))
                            Spacer(modifier = Modifier.width(3.dp))
                            Box(modifier = Modifier.size(5.dp).background(Color.White))
                            Spacer(modifier = Modifier.width(3.dp))
                            Box(modifier = Modifier.size(5.dp).background(Color.White))
                        }
                    }
                }

                DockIconItem(
                    app = AppType.MESSAGING,
                    onClick = { viewModel.launchApp(AppType.MESSAGING) }
                )
                DockIconItem(
                    app = AppType.BROWSER,
                    onClick = { viewModel.launchApp(AppType.BROWSER) }
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
                color = Color(0xFF33B5E5),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .drawBehind {
                        drawLine(
                            color = Color(0xFF33B5E5),
                            start = Offset(0f, size.height + 8.dp.toPx()),
                            end = Offset(size.width, size.height + 8.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "WIDGETS",
                color = Color(0x66FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
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
    }
}

@Composable
fun AppDrawerGridItem(app: AppType, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
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
                        IconButton(onClick = { viewModel.toggleNotificationShade() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
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
                icon = Icons.Default.BatteryChargingFull,
                title = "Batería",
                status = "78% (Carga)",
                active = true,
                activeColor = Color(0xFF66BB6A),
                modifier = Modifier.weight(1f),
                onClick = {}
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.launchApp(item.appType) },
        colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
        border = BorderStroke(1.dp, Color(0x08FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.appType == AppType.MESSAGING) Icons.Default.ChatBubble else Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF33B5E5),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.sender, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = item.message, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { viewModel.dismissNotification(item.id) }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Eliminar", tint = Color.Gray, modifier = Modifier.size(16.dp))
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (!inCall) {
            Text(
                text = dialString.ifEmpty { "Escribe un número..." },
                color = if (dialString.isEmpty()) Color.DarkGray else Color(0xFF33B5E5),
                fontSize = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 20.dp)
            )

            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { valChar ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.3f)
                                    .background(Color(0x19FFFFFF), CircleShape)
                                    .border(1.dp, Color(0x3333B5E5), CircleShape)
                                    .clickable { dialString += valChar },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = valChar, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (dialString.isNotEmpty()) dialString = dialString.dropLast(1) }) {
                    Icon(imageVector = Icons.Default.Backspace, contentDescription = "Delete char", tint = Color.Gray)
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0xFF669900), CircleShape)
                        .clickable { if (dialString.isNotEmpty()) inCall = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = "Llamar", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        } else {
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
    val query by viewModel.searchQuery.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val resultText by viewModel.searchResultText.collectAsState()
    val sources by viewModel.searchSources.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text(text = "Busca en Google Grounded...", color = Color.Gray, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1D1D1D),
                    unfocusedContainerColor = Color(0xFF1D1D1D),
                    focusedIndicatorColor = Color(0xFF33B5E5)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.performSearch() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                border = BorderStroke(1.dp, Color(0xFF33B5E5)),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar", tint = Color(0xFF33B5E5))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.2f))
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF33B5E5))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Buscando en Google con Gemini AI...", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else if (resultText.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x19FFFFFF)),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF99CC00))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Respuesta de Búsqueda Conectada", color = Color(0xFF99CC00), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = resultText, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }

                if (sources.isNotEmpty()) {
                    item {
                        Text(text = "Fuentes y Referencias:", color = Color(0xFF33B5E5), fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    items(sources) { webSource ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = Color(0xFF33B5E5), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = webSource.title ?: "Página Web", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = webSource.uri ?: "", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(imageVector = Icons.Default.Explore, contentDescription = null, tint = Color(0x22FFFFFF), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Usa la barra para buscar información real en tiempo real.", color = Color.DarkGray, fontSize = 13.sp, textAlign = TextAlign.Center)
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
                onClick = { viewModel.generateCameraPhoto() },
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
    val scoreText by viewModel.calcDisplay.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color(0xFF0F0F0F), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0x3333B5E5), RoundedCornerShape(4.dp))
                .padding(12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(text = scoreText, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        val matrix = listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "-"),
            listOf("C", "0", "=", "+")
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            matrix.forEach { entries ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    entries.forEach { cell ->
                        val isOperator = cell in listOf("÷", "×", "-", "+", "=")
                        val btnBg = if (cell == "C") Color(0x33FF3300) else if (isOperator) Color(0xFF222830) else Color(0x22FFFFFF)
                        val btnBorderColor = if (isOperator) Color(0xFF33B5E5) else Color(0x33FFFFFF)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.2f)
                                .background(btnBg, RoundedCornerShape(4.dp))
                                .border(1.dp, btnBorderColor, RoundedCornerShape(4.dp))
                                .clickable { viewModel.handleCalcKey(cell) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = cell, color = if (isOperator) Color(0xFF33B5E5) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

enum class SettingsOption {
    WIFI, BRIGHTNESS, USERS, APPS, STORAGE, BATTERY, SCHEDULE, BACKUP, DATETIME, SECURITY, ACCESSIBILITY, ABOUT
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
    var powerSaverEnabled by remember { mutableStateOf(false) }
    var aboutClickCount by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resettingProgress by remember { mutableStateOf(false) }

    val settingsItems = listOf(
        SettingsItem(SettingsOption.WIFI, "Wi-Fi y Redes", "Activar, simular redes inalámbricas", "wifi"),
        SettingsItem(SettingsOption.BRIGHTNESS, "Brillo y Pantalla", "Fondos, brillo de pantalla y rotación", "pantalla"),
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
                    onClick = { screenState = "root" },
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
                    "perfiles" -> "PERFILES DE USUARIO"
                    "apps" -> "APLICACIONES"
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
                "pantalla" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
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
                                    .padding(vertical = 10.dp),
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
                            .padding(16.dp),
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
                                    color = if (powerSaverEnabled) Color(0xFFFF9426) else Color(0xFF669900),
                                    startAngle = -90f,
                                    sweepAngle = 360f * 0.78f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("78%", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                                Text("ESTABLE", color = if (powerSaverEnabled) Color(0xFFFF9426) else Color(0xFF669900), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                            border = BorderStroke(1.dp, Color(0xFF2E2E32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("INFORMACIÓN FÍSICA Y DE SALUD", color = Color(0xFF33B5E5), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                RowInfoLabel("Estado de carga:", "Descargándose (Simulada)")
                                RowInfoLabel("Salud de Batería:", "Excelente / Estable")
                                RowInfoLabel("Voltaje:", "3.84 V")
                                RowInfoLabel("Temperatura:", "31.2 °C")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
                        viewModel.postNotification("Sistema", "Simulando restablecimiento de fábrica...", AppType.SETTINGS)
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
    val playStoreRecommendations = listOf(
        AppType.MAPS to "Consigue cartografía GPS.",
        AppType.PHONE to "Marcador de llamadas clásica.",
        AppType.BROWSER to "Navega Google Search retro.",
        AppType.CALCULATOR to "Hacer cuentas de forma fácil.",
        AppType.CAMERA to "Carrete fotográfico por IA."
    )

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Text(
            text = "Aplicaciones Recomendadas",
            color = Color(0xFF99CC00),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playStoreRecommendations) { item ->
                val alreadyInstalled = installedApps.contains(item.first)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x19FFFFFF)),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF33B5E5).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Apps, contentDescription = null, tint = Color(0xFF33B5E5))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.first.appName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = item.second, color = Color.Gray, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                if (alreadyInstalled) {
                                    viewModel.uninstallAppInStore(item.first)
                                } else {
                                    viewModel.installAppInStore(item.first)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (alreadyInstalled) Color(0xFFCC0000) else Color(0xFF669900))
                        ) {
                            Text(text = if (alreadyInstalled) "Quitar" else "Instalar", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
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
        IconButton(onClick = {
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
        }) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = "Soft key Back",
                tint = Color(0xFF666666),
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = { viewModel.goHome() }) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Soft key Home",
                tint = Color(0xFF666666),
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(onClick = { viewModel.toggleRecentApps() }) {
            Icon(
                imageVector = Icons.Default.AspectRatio,
                contentDescription = "Soft key Recents layout switcher",
                tint = Color(0xFF666666),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RecentAppsOverlay(viewModel: JellyBeanViewModel) {
    val recents by viewModel.recentAppsList.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE0050505))
            .clickable { viewModel.toggleRecentApps() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(260.dp)
                .background(Color(0xFA0C0C0F))
                .border(BorderStroke(1.dp, Color(0xFF33B5E5)))
                .padding(14.dp)
                .clickable(enabled = false) {}
        ) {
            Text(
                text = "Recientes:",
                color = Color(0xFF33B5E5),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (recents.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = "No hay apps abiertas recientemente.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recents) { app ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.launchApp(app) },
                            colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
                            border = BorderStroke(1.dp, Color(0x1AFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFF33B5E5).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF33B5E5), modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = app.appName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.removeRecentApp(app) }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Borrar", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.clearRecentApps() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E0909)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Cerrar Todas", color = Color.Red, fontSize = 12.sp)
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
            .clickable { onClick() }
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
fun DockIconItem(app: AppType, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        JellyBeanAppIcon(
            app = app,
            modifier = Modifier.size(54.dp)
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
                Image(
                    painter = painterResource(id = R.drawable.img_phone_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        AppType.BROWSER -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_browser_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        AppType.MESSAGING -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_messages_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        AppType.CAMERA -> {
            Box(
                modifier = modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_camera_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
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

                    // Pin in center
                    drawCircle(color = Color(0xFF33B5E5), radius = 3.dp.toPx(), center = Offset(w * 0.5f, h * 0.52f))
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
            .clickable { onClick() }
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
