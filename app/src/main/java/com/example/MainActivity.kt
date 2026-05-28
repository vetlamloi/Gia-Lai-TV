package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import android.content.Intent
import android.net.Uri
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// --------------------------------------------------------------------
// Data Models & State Containers
// --------------------------------------------------------------------

data class ScheduleItem(
    val time: String,
    val program: String,
    val detail: String? = null,
    val isCurrentlyOnAir: Boolean = false
)

sealed interface ScheduleState {
    object Loading : ScheduleState
    data class Success(val list: List<ScheduleItem>, val dateLabel: String, val formattedDate: String) : ScheduleState
    data class Error(val message: String) : ScheduleState
}

// --------------------------------------------------------------------
// ViewModel: Handles Dates, Fetching & JSoup Scraping logic
// --------------------------------------------------------------------

class ScheduleViewModel : ViewModel() {
    private val _calendarState = MutableStateFlow(Calendar.getInstance())
    
    private val _scheduleState = MutableStateFlow<ScheduleState>(ScheduleState.Loading)
    val scheduleState: StateFlow<ScheduleState> = _scheduleState.asStateFlow()

    init {
        // Automatically fetch schedule when system launches
        fetchScheduleForActiveDate()
    }

    fun getSelectedDateFormatted(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(_calendarState.value.time)
    }

    fun getSelectedDateDisplayLabel(): String {
        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN"))
        return sdf.format(_calendarState.value.time)
    }

    fun nextDay() {
        val updated = _calendarState.value.clone() as Calendar
        updated.add(Calendar.DAY_OF_YEAR, 1)
        _calendarState.value = updated
        fetchScheduleForActiveDate()
    }

    fun prevDay() {
        val updated = _calendarState.value.clone() as Calendar
        updated.add(Calendar.DAY_OF_YEAR, -1)
        _calendarState.value = updated
        fetchScheduleForActiveDate()
    }

    fun resetToToday() {
        _calendarState.value = Calendar.getInstance()
        fetchScheduleForActiveDate()
    }

    fun fetchScheduleForActiveDate() {
        val targetDateStr = getSelectedDateFormatted()
        val displayLabel = getSelectedDateDisplayLabel()
        
        viewModelScope.launch {
            _scheduleState.value = ScheduleState.Loading
            try {
                // Fetch and parse webpage asynchronously on IO thread
                val scrapedList = withContext(Dispatchers.IO) {
                    val url = "https://gialaitv.vn/lich-phat-song/?ngay=$targetDateStr&kenh=1"
                    val doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(10000)
                        .get()

                    val contentDiv = doc.select(".lichphatsong .content").first()
                    val parsedList = mutableListOf<ScheduleItem>()
                    
                    if (contentDiv != null) {
                        val rawHtml = contentDiv.html()
                        // Split entries by <br> break elements
                        val lines = rawHtml.split(Regex("(?i)<br\\s*/?>"))
                        
                        for (line in lines) {
                            val cleanLine = Jsoup.parse(line).text().trim()
                            if (cleanLine.isEmpty()) continue
                            
                            // Skip the localized date line to avoid duplicating header calendar labels
                            if (cleanLine.contains("Thứ ") && cleanLine.contains("/")) {
                                continue
                            }

                            // Match time formats like "06h30" or "06:30"
                            val timeMatch = Regex("^(\\d{1,2}[h:]\\d{2})\\s*(.*)$").find(cleanLine)
                            if (timeMatch != null) {
                                val time = timeMatch.groupValues[1]
                                val programName = timeMatch.groupValues[2].trim()
                                parsedList.add(ScheduleItem(time = time, program = programName))
                            } else {
                                // Subprogram descriptions / labels that follow a time entry
                                if (parsedList.isNotEmpty()) {
                                    val lastIdx = parsedList.size - 1
                                    val previousItem = parsedList[lastIdx]
                                    parsedList[lastIdx] = previousItem.copy(
                                        detail = if (previousItem.detail == null) {
                                            cleanLine
                                        } else {
                                            "${previousItem.detail} | $cleanLine"
                                        }
                                    )
                                } else {
                                    parsedList.add(ScheduleItem(time = "--:--", program = cleanLine))
                                }
                            }
                        }
                    }
                    parsedList
                }

                // Check if the current selected day is today
                val isToday = isCalendarToday(_calendarState.value)
                val processedList = calculateCurrentOnAirStatus(scrapedList, isToday)

                if (processedList.isEmpty()) {
                    _scheduleState.value = ScheduleState.Error("Không có lịch phát sóng cho ngày này.")
                } else {
                    _scheduleState.value = ScheduleState.Success(processedList, displayLabel, targetDateStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _scheduleState.value = ScheduleState.Error("Không thể tải lịch: ${e.localizedMessage ?: "Lỗi kết nối"}")
            }
        }
    }

    private fun isCalendarToday(cal: Calendar): Boolean {
        val today = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun calculateCurrentOnAirStatus(list: List<ScheduleItem>, isSelectedToday: Boolean): List<ScheduleItem> {
        if (!isSelectedToday || list.isEmpty()) return list

        val now = Calendar.getInstance()
        val curHr = now.get(Calendar.HOUR_OF_DAY)
        val curMin = now.get(Calendar.MINUTE)
        val curTotalMinutes = curHr * 60 + curMin

        fun parseTimeToMinutes(timeStr: String): Int? {
            val cleaned = timeStr.trim().lowercase(Locale.US).replace(":", "h")
            val match = Regex("(\\d{1,2})h(\\d{2})").find(cleaned) ?: return null
            val h = match.groupValues[1].toInt()
            val m = match.groupValues[2].toInt()
            return h * 60 + m
        }

        val res = list.mapIndexed { idx, item ->
            val startMins = parseTimeToMinutes(item.time)
            val endMins = if (idx + 1 < list.size) {
                parseTimeToMinutes(list[idx + 1].time)
            } else {
                parseTimeToMinutes(list[0].time)?.let { firstStart ->
                    firstStart + 24 * 60
                } ?: (24 * 60)
            }

            val onAir = if (startMins != null && endMins != null) {
                if (endMins < startMins) {
                    curTotalMinutes >= startMins || curTotalMinutes < (endMins % (24 * 60))
                } else {
                    curTotalMinutes in startMins until endMins
                }
            } else {
                false
            }

            item.copy(isCurrentlyOnAir = onAir)
        }

        // Fallback closest past item if no exact future matches
        if (res.none { it.isCurrentlyOnAir }) {
            var closestIdx = -1
            var minDiff = Int.MAX_VALUE
            for ((index, item) in list.withIndex()) {
                val tMins = parseTimeToMinutes(item.time) ?: continue
                val diff = curTotalMinutes - tMins
                if (diff in 0 until minDiff) {
                    minDiff = diff
                    closestIdx = index
                }
            }
            if (closestIdx != -1) {
                return res.mapIndexed { idx, it -> it.copy(isCurrentlyOnAir = idx == closestIdx) }
            }
        }

        return res
    }
}

// --------------------------------------------------------------------
// Main Activity Setup & Immersive Windows Config
// --------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trustAllSSL()
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF070A12))
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        LiveStreamScreen()
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun trustAllSSL() {
        try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                }
            )
            val sc = javax.net.ssl.SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// --------------------------------------------------------------------
// Core Live TV & Schedule Composable Flow
// --------------------------------------------------------------------

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LiveStreamScreen(
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val streamUrl = "https://tv.gialaitv.vn/tv.m3u8"
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Track selector to support dynamic quality adaptation based on bandwidth capacity
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(false) // ensures Adaptive Bitrate Streaming (ABR) functions automatically
            )
        }
    }

    // Player instances configured with the track selector for auto adaptive video streaming
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build().apply {
                val mediaItem = MediaItem.fromUri(streamUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<Boolean>(false) }
    var isMuted by remember { mutableStateOf(false) }
    var resizeModeState by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var showLandscapeSchedulePanel by remember { mutableStateOf(false) }
    var showSocialMenu by remember { mutableStateOf(false) }

    // Control fading timers
    LaunchedEffect(showPlayerControls) {
        if (showPlayerControls) {
            delay(4000)
            showPlayerControls = false
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    val playerListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isPlaying = playbackState == Player.STATE_READY && exoPlayer.playWhenReady
                if (playbackState == Player.STATE_READY) {
                    playbackError = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
                isBuffering = false
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        exoPlayer.addListener(playerListener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!playbackError) exoPlayer.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    val onRetryPlayback = {
        playbackError = false
        isBuffering = true
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    if (isLandscape) {
        // LANDSCAPE: Panoramic Immersive Full Screen Player Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Player Box (Left/Whole screen)
            Box(
                modifier = Modifier
                    .weight(if (showLandscapeSchedulePanel) 0.65f else 1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showPlayerControls = !showPlayerControls
                    }
            ) {
                VideoPlayerRenderer(
                    exoPlayer = exoPlayer,
                    resizeModeState = resizeModeState,
                    playbackError = playbackError
                )

                AnimatedPlayerControls(
                    showControls = showPlayerControls,
                    playbackError = playbackError,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    resizeModeState = resizeModeState,
                    isLandscape = true,
                    scheduleShownInLandscape = showLandscapeSchedulePanel,
                    onTogglePlay = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    },
                    onToggleMute = { isMuted = !isMuted },
                    onToggleAspect = {
                        resizeModeState = if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onToggleLandscapeSchedule = {
                        showLandscapeSchedulePanel = !showLandscapeSchedulePanel
                    }
                )

                BufferingSpinnerOverlay(isBuffering = isBuffering, playbackError = playbackError)
                PlaybackErrorOverlay(playbackError = playbackError, onRetry = onRetryPlayback)
            }

            // Slidable Schedule panel (Right sidebar)
            AnimatedVisibility(
                visible = showLandscapeSchedulePanel,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.35f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0E17))
                        .border(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    ScheduleSection(
                        viewModel = viewModel,
                        isCompactHorizontal = true,
                        onClosePanel = { showLandscapeSchedulePanel = false }
                    )
                }
            }
        }
    } else {
        // PORTRAIT: GTV Logo & Channel Name at the top, then Video Stream (16:9), and Schedule detail feed at the bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A12))
        ) {
            // GTV Channel Header (Image 1 Logo + Official name + Menu & Share buttons)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1322)) // refined dark slate backdrop
                    .padding(top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Modern Menu button for social networks
                    IconButton(
                        onClick = { showSocialMenu = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Mạng xã hội GTV",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center Logo
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_gtv_header_logo),
                            contentDescription = "Gia Lai TV Logo",
                            modifier = Modifier.height(34.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            try {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Xem kênh truyền hình Gia Lai TV (GTV) trực tuyến chất lượng cao tại: https://gialaitv.vn hoặc theo dõi luồng trực tiếp: $streamUrl"
                                    )
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ kênh Gia Lai TV")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Chia sẻ kênh GTV",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "BÁO VÀ PHÁT THANH, TRUYỀN HÌNH GIA LAI",
                    color = Color(0xFFD1D5DB),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 16:9 Video Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showPlayerControls = !showPlayerControls
                    }
            ) {
                VideoPlayerRenderer(
                    exoPlayer = exoPlayer,
                    resizeModeState = resizeModeState,
                    playbackError = playbackError
                )

                AnimatedPlayerControls(
                    showControls = showPlayerControls,
                    playbackError = playbackError,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    resizeModeState = resizeModeState,
                    isLandscape = false,
                    scheduleShownInLandscape = false,
                    onTogglePlay = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    },
                    onToggleMute = { isMuted = !isMuted },
                    onToggleAspect = {
                        resizeModeState = if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onToggleLandscapeSchedule = {}
                )

                BufferingSpinnerOverlay(isBuffering = isBuffering, playbackError = playbackError)
                PlaybackErrorOverlay(playbackError = playbackError, onRetry = onRetryPlayback)
            }

            // Lower schedule list filling the rest of the canvas with solid cards
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                ScheduleSection(
                    viewModel = viewModel,
                    isCompactHorizontal = false,
                    onClosePanel = {}
                )
            }
        }
    }

    SocialMediaMenuOverlay(
        visible = showSocialMenu,
        onDismiss = { showSocialMenu = false },
        context = context
    )
}

// --------------------------------------------------------------------
// Sub Composables: Video Render, Controls overlays, & Shimmer views
// --------------------------------------------------------------------

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerRenderer(
    exoPlayer: ExoPlayer,
    resizeModeState: Int,
    playbackError: Boolean
) {
    if (!playbackError) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = resizeModeState
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeModeState
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AnimatedPlayerControls(
    showControls: Boolean,
    playbackError: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    resizeModeState: Int,
    isLandscape: Boolean,
    scheduleShownInLandscape: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleAspect: () -> Unit,
    onToggleLandscapeSchedule: () -> Unit
) {
    AnimatedVisibility(
        visible = showControls && !playbackError,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        ) {
            // Header: Channel Name, Subtitles, LIVE flashing status tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GIA LAI TV",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Báo và Phát thanh, Truyền hình Gia Lai",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .background(Color(0xFFE50914), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(5.dp),
                        shape = CircleShape,
                        color = Color.White
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Centered Overlay Control Circle
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onTogglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom Buttons Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume Controls
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onToggleMute() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isMuted) "🔇 Tắt tiếng" else "🔊 Bật tiếng",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Aspect screen ratio and optional Calendar Trigger in Landscape mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLandscape) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (scheduleShownInLandscape) Color(0xFFE50914) 
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .clickable { onToggleLandscapeSchedule() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Lịch phát sóng",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onToggleAspect() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Aspect: FIT" else "Aspect: FULL",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BufferingSpinnerOverlay(isBuffering: Boolean, playbackError: Boolean) {
    if (isBuffering && !playbackError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(45.dp),
                    color = Color(0xFFE50914),
                    strokeWidth = 3.5.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Đang kết nối luồng LIVE...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun PlaybackErrorOverlay(playbackError: Boolean, onRetry: () -> Unit) {
    if (playbackError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0E18))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Playback connection issues",
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Lỗi đường truyền phát trực tuyến",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Vui lòng xem lại kết nối thiết bị của bạn hoặc thử mở lại luồng.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry stream load",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Thử lại",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// UI: TV "Daily Schedule" list implementation
// --------------------------------------------------------------------

@Composable
fun ScheduleSection(
    viewModel: ScheduleViewModel,
    isCompactHorizontal: Boolean,
    onClosePanel: () -> Unit
) {
    val state by viewModel.scheduleState.collectAsState()
    val scrollState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Schedule Section Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp, 16.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LỊCH PHÁT SÓNG GTV",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            if (isCompactHorizontal) {
                IconButton(
                    onClick = onClosePanel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Controller over Days
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.prevDay() },
                modifier = Modifier.size(32.dp)
            ) {
                Text("◀", color = Color.White, fontSize = 11.sp)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (state) {
                        is ScheduleState.Success -> (state as ScheduleState.Success).dateLabel
                        else -> viewModel.getSelectedDateDisplayLabel()
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Chạm xem ngày khác",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }

            IconButton(
                onClick = { viewModel.nextDay() },
                modifier = Modifier.size(32.dp)
            ) {
                Text("▶", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // State machine matching different scraping outcomes
        when (val activeState = state) {
            is ScheduleState.Loading -> {
                ShimmerLoadingPlaceholder()
            }
            is ScheduleState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Alert logo",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = activeState.message,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.fetchScheduleForActiveDate() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Tải lại lịch", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
            is ScheduleState.Success -> {
                val list = activeState.list
                
                // Automatically scroll and center on the item that is currently live!
                LaunchedEffect(list) {
                    val onAirIdx = list.indexOfFirst { it.isCurrentlyOnAir }
                    if (onAirIdx != -1) {
                        scrollState.animateScrollToItem(onAirIdx)
                    }
                }

                LazyColumn(
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(list) { item ->
                        ScheduleProgramCard(item = item, isCompact = isCompactHorizontal)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleProgramCard(item: ScheduleItem, isCompact: Boolean) {
    val cardBg = if (item.isCurrentlyOnAir) Color(0xFF231114) else Color(0xFF141A2E)
    val cardBorderColor = if (item.isCurrentlyOnAir) Color(0xFFE50914).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.04f)
    val timeBg = if (item.isCurrentlyOnAir) Color(0xFFE50914) else Color(0xFF1E2640)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isCompact) {
            // Custom Timeline effect on Left Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (item.isCurrentlyOnAir) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (item.isCurrentlyOnAir) Color(0xFFE50914) else Color.White.copy(alpha = 0.25f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(36.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = cardBorderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCurrentlyOnAir) 6.dp else 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time Badge Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(timeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.time,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Text Titles Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Text(
                            text = item.program,
                            color = if (item.isCurrentlyOnAir) Color(0xFFFFA5A5) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (item.isCurrentlyOnAir) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (item.isCurrentlyOnAir) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ĐANG PHÁT",
                                color = Color(0xFFE50914),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFE50914), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.detail != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.detail,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerLoadingPlaceholder() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        repeat(5) {
            val pulsingBg = Color.White.copy(alpha = 0.05f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pulsingBg)
                    .padding(10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(45.dp, 20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}

@Composable
fun SocialMediaMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    context: Context = LocalContext.current
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() } // Closes when tapping outer dimmed region
        ) {
            // Main menu card sliding from the bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.80f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF0F1322))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(enabled = true, onClick = {}) // prevent clicks passing through
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp, 20.dp)
                                    .background(Color(0xFFE50914), RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MẠNG XÃ HỘI GTV",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.06f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng Menu",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Theo dõi các kênh thông tin chính thức của Báo và PT-TH Gia Lai",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Category Website
                        item {
                            SocialGroupSection(
                                title = "TRANG TIN CHÍNH THỨC",
                                color = Color(0xFF009688)
                            ) {
                                SocialLinkItem(
                                    label = "Website GTV Gia Lai",
                                    desc = "Cập nhật tin tức thời sự trực tuyến 24/7",
                                    url = "https://gialaitv.vn/",
                                    color = Color(0xFF009688),
                                    context = context
                                )
                            }
                        }

                        // Category Facebook
                        item {
                            SocialGroupSection(
                                title = "TRANG FACEBOOK",
                                color = Color(0xFF1877F2)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SocialLinkItem(
                                        label = "Báo và PT-TH Gia Lai (Fanpage chính)",
                                        desc = "Truyền hình Gia Lai Fanpage",
                                        url = "https://www.facebook.com/baovaphatthanhtruyenhinhgialai",
                                        color = Color(0xFF1877F2),
                                        context = context
                                    )
                                    SocialLinkItem(
                                        label = "Tin tức & Giải trí Gia Lai - GTV",
                                        desc = "Trang tin tức nhanh chóng & giải trí hấp dẫn",
                                        url = "https://www.facebook.com/baovaptthgialai",
                                        color = Color(0xFF1877F2),
                                        context = context
                                    )
                                }
                            }
                        }

                        // Category Youtube
                        item {
                            SocialGroupSection(
                                title = "KÊNH YOUTUBE",
                                color = Color(0xFFFF0000)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SocialLinkItem(
                                        label = "Báo và PT-TH Gia Lai",
                                        desc = "Kênh thời sự chính thức GTV",
                                        url = "https://www.youtube.com/@BaovaPT-THGiaLai",
                                        color = Color(0xFFFF0000),
                                        context = context
                                    )
                                    SocialLinkItem(
                                        label = "Gia Lai TV",
                                        desc = "Kênh tổng hợp tin tức đời sống pháp luật",
                                        url = "https://www.youtube.com/@baovaptthgialai",
                                        color = Color(0xFFFF0000),
                                        context = context
                                    )
                                }
                            }
                        }

                        // Category TikTok
                        item {
                            SocialGroupSection(
                                title = "KÊNH TIKTOK CHÍNH THỨC",
                                color = Color(0xFFFE2C55)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SocialLinkItem(
                                        label = "@baovaptthgialai",
                                        desc = "Tin tức thời sự nóng hổi ngắn gọn",
                                        url = "https://www.tiktok.com/@baovaptthgialai",
                                        color = Color(0xFFFE2C55),
                                        context = context
                                    )
                                    SocialLinkItem(
                                        label = "@baoptthgialai",
                                        desc = "Nhịp sống đời thường & Phóng sự ngắn",
                                        url = "https://www.tiktok.com/@baoptthgialai",
                                        color = Color(0xFFFE2C55),
                                        context = context
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Gia Lai TV © 2026 - Bản quyền thuộc về Đài Phát thanh và Truyền hình Gia Lai",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SocialGroupSection(
    title: String,
    color: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

@Composable
fun SocialLinkItem(
    label: String,
    desc: String,
    url: String,
    color: Color,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.03f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Mở",
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
