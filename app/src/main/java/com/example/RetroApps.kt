package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

// ==========================================
// 1. RETRO CLOCK APPLICATION
// ==========================================
@Composable
fun RetroClockActivity(viewModel: JellyBeanViewModel) {
    var activeTab by remember { mutableStateOf(0) } // 0: Alarms, 1: Stopwatch, 2: Timer
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Holo Blue Tabs header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .border(BorderStroke(1.dp, Color(0xFF222222)))
        ) {
            val tabs = listOf("ALARMAS", "CRONÓMETRO", "TEMPORIZADOR")
            tabs.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = index }
                        .padding(vertical = 12.dp)
                        .drawBehind {
                            if (activeTab == index) {
                                drawLine(
                                    color = Color(0xFF33B5E5),
                                    start = Offset(0f, this.size.height),
                                    end = Offset(this.size.width, this.size.height),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (activeTab == index) Color(0xFF33B5E5) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (activeTab) {
                0 -> ClockAlarmsTab(viewModel)
                1 -> ClockStopwatchTab()
                2 -> ClockTimerTab()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockAlarmsTab(viewModel: JellyBeanViewModel) {
    val alarms by viewModel.alarms.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newTimeStr by remember { mutableStateOf("07:00 AM") }
    var newLabelStr by remember { mutableStateOf("Despertar") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(alarms) { alarm ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    border = BorderStroke(1.dp, if (alarm.isEnabled) Color(0xFF33B5E5).copy(alpha = 0.4f) else Color(0xFF2E2E32)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alarm.time,
                                color = if (alarm.isEnabled) Color(0xFF33B5E5) else Color.Gray,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Light
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = alarm.label,
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Holo switch representation
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(26.dp)
                                    .background(Color(0xFF222222))
                                    .border(1.dp, if (alarm.isEnabled) Color(0xFF33B5E5) else Color.Gray)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable { viewModel.toggleAlarm(alarm.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (alarm.isEnabled) Color(0xFF33B5E5) else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "ON",
                                            color = if (alarm.isEnabled) Color.Black else Color.Gray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (!alarm.isEnabled) Color(0xFF444444) else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "OFF",
                                            color = if (!alarm.isEnabled) Color.White else Color.Gray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            IconButton(onClick = { viewModel.removeAlarm(alarm.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Borrar alarma",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1F)),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, Color(0xFF33B5E5)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .testTag("add_alarm_button")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF33B5E5))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "NUEVA ALARMA", color = Color.White)
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(text = "Nueva Alarma", color = Color(0xFF33B5E5)) },
            text = {
                Column {
                    Text(text = "Hora de Alarma:", color = Color.Gray, fontSize = 12.sp)
                    TextField(
                        value = newTimeStr,
                        onValueChange = { newTimeStr = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF141414),
                            unfocusedContainerColor = Color(0xFF141414),
                            focusedIndicatorColor = Color(0xFF33B5E5)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Etiqueta:", color = Color.Gray, fontSize = 12.sp)
                    TextField(
                        value = newLabelStr,
                        onValueChange = { newLabelStr = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF141414),
                            unfocusedContainerColor = Color(0xFF141414),
                            focusedIndicatorColor = Color(0xFF33B5E5)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addAlarm(newTimeStr, newLabelStr)
                        showAddDialog = false
                    }
                ) {
                    Text("GUARDAR", color = Color(0xFF33B5E5))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = Color(0xFF141414),
            textContentColor = Color.White
        )
    }
}

@Composable
fun ClockStopwatchTab() {
    var isRunning by remember { mutableStateOf(false) }
    var timeElapsedMs by remember { mutableStateOf(0L) }
    var lapList by remember { mutableStateOf(listOf<String>()) }
    
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val startTime = System.currentTimeMillis() - timeElapsedMs
            while (isRunning) {
                timeElapsedMs = System.currentTimeMillis() - startTime
                delay(30)
            }
        }
    }

    val minutes = (timeElapsedMs / 60000) % 60
    val seconds = (timeElapsedMs / 1000) % 60
    val millis = (timeElapsedMs / 10) % 100
    val displayStr = String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // Circular dial stopwatch display
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(Color(0xFF0D0D0D), CircleShape)
                .border(2.dp, Color(0xFF33B5E5), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayStr,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "CRONÓMETRO",
                    color = Color(0xFF33B5E5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (isRunning) {
                        lapList = lapList + "Vuelta ${lapList.size + 1}: $displayStr"
                    } else {
                        // Reset
                        timeElapsedMs = 0L
                        lapList = emptyList()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E32)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = if (isRunning) "VUELTA" else "REINICIAR", color = Color.White)
            }

            Button(
                onClick = { isRunning = !isRunning },
                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else Color(0xFF33B5E5)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = if (isRunning) "PARAR" else "INICIAR", color = if (isRunning) Color.White else Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Laps scrollable list
        Text(
            text = "REGISTRO DE VUELTAS",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(Color(0xFF0F0F0F))
                .border(1.dp, Color(0xFF222222))
        ) {
            if (lapList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Sin vueltas anotadas", color = Color.DarkGray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    items(lapList.asReversed()) { lap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = lap.substringBefore(":"), color = Color(0xFF33B5E5), fontSize = 14.sp)
                            Text(text = lap.substringAfter(":").trim(), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClockTimerTab() {
    var isRunning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(180) } // Default 3 mins
    val totalSeconds = 180

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning && secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
            if (secondsLeft == 0) {
                isRunning = false
            }
        }
    }

    val displayMins = secondsLeft / 60
    val displaySecs = secondsLeft % 60
    val fraction = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds else 0f

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background track
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFF1E1E1E),
                    radius = size.width * 0.45f,
                    style = Stroke(width = 6.dp.toPx())
                )
                // Timed progress
                drawArc(
                    color = Color(0xFF33B5E5),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", displayMins, displaySecs),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "MINUTOS RESTANTES",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Preset buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(60, 180, 300, 600).forEach { secs ->
                Button(
                    onClick = {
                        isRunning = false
                        secondsLeft = secs
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1F)),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color(0xFF33B5E5).copy(alpha = 0.5f))
                ) {
                    Text(text = "${secs / 60}m", color = Color(0xFF33B5E5))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    isRunning = false
                    secondsLeft = 180
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E32)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "REINICIAR", color = Color.White)
            }

            Button(
                onClick = { isRunning = !isRunning },
                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else Color(0xFF33B5E5)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = if (isRunning) "PAUSAR" else "COMENZAR", color = if (isRunning) Color.White else Color.Black)
            }
        }
    }
}


// ==========================================
// 2. RETRO PLAY MUSIC APPLICATION
// ==========================================
@Composable
fun RetroMusicActivity(viewModel: JellyBeanViewModel) {
    val currentTrackIndex by viewModel.currentTrackIndex.collectAsState()
    val isPlaying by viewModel.isMusicPlaying.collectAsState()
    val progressSec by viewModel.musicProgressSec.collectAsState()

    val currentTrack = viewModel.musicTracks[currentTrackIndex]
    val totalProgressFraction = progressSec.toFloat() / currentTrack.durationSec

    // Rotating vinyl animation
    val infiniteTransition = rememberInfiniteTransition()
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Track visual and rotation
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF101010))
                    .rotate(if (isPlaying) rotationAngle else 0f)
                    .border(2.dp, Color(0xFFFFBB33), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl CD grooves drawing
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    drawCircle(color = Color.Black)
                    // Concentric rings
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = w * 0.42f, style = Stroke(width = 1f))
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = w * 0.35f, style = Stroke(width = 1f))
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = w * 0.28f, style = Stroke(width = 1f))
                    // Album art label
                    drawCircle(color = Color(0xFFFFBB33), radius = w * 0.16f)
                    drawCircle(color = Color(0xFF222222), radius = w * 0.04f)
                }

                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metadata text
            Text(
                text = currentTrack.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentTrack.artist,
                color = Color(0xFF33B5E5),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // Action Waveform Jumper
        HoloAudioWaveform(isPlaying = isPlaying)

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val progressMins = progressSec / 60
                val progressSecs = progressSec % 60
                val totalMins = currentTrack.durationSec / 60
                val totalSecs = currentTrack.durationSec % 60

                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", progressMins, progressSecs),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", totalMins, totalSecs),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Slider(
                value = progressSec.toFloat(),
                onValueChange = { viewModel.seekMusic(it.toInt()) },
                valueRange = 0f..currentTrack.durationSec.toFloat(),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF33B5E5),
                    inactiveTrackColor = Color(0xFF333333),
                    thumbColor = Color(0xFF33B5E5)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Media panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.size(54.dp),
                onClick = { viewModel.prevTrack() }
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Pista anterior",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF33B5E5), CircleShape)
                    .clickable { viewModel.playPauseMusic() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Reproducir pausa",
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                modifier = Modifier.size(54.dp),
                onClick = { viewModel.nextTrack() }
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Siguiente pista",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun HoloAudioWaveform(isPlaying: Boolean) {
    val barCount = 12
    val heights = remember { mutableStateListOf(*Array(barCount) { 15f }) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                for (i in 0 until barCount) {
                    heights[i] = kotlin.random.Random.nextFloat() * 45f + 10f
                }
                delay(120)
            }
        } else {
            for (i in 0 until barCount) {
                heights[i] = 12f
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(heights[i].dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF33B5E5), Color(0xFF0099CC))
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}


// ==========================================
// 3. RETRO CALENDAR/AGENDA APPLICATION
// ==========================================
@Composable
fun RetroCalendarActivity(viewModel: JellyBeanViewModel) {
    val allEvents by viewModel.calendarEvents.collectAsState()
    var selectedDay by remember { mutableStateOf(23) } // Default 23rd
    var showAddEventDialog by remember { mutableStateOf(false) }
    var eventTitle by remember { mutableStateOf("") }
    var eventTime by remember { mutableStateOf("10:00 AM") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Month heading
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .border(BorderStroke(1.dp, Color(0xFF222222)))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "JUNIO DE 2026",
                color = Color(0xFF33B5E5),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Days Grid Calendar
        // June 2026 starts on Monday (1). 30 days total
        val dayHeaders = listOf("LU", "MA", "MI", "JU", "VI", "SA", "DO")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dayHeaders.forEach { header ->
                Text(
                    text = header,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            items(30) { index ->
                val dayNum = index + 1
                val hasEvents = allEvents.any { it.day == dayNum }
                val isSelected = selectedDay == dayNum

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(3.dp)
                        .background(
                            if (isSelected) Color(0xFF33B5E5).copy(alpha = 0.2f) else Color(0xFF0B0B0D),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            when {
                                isSelected -> Color(0xFF33B5E5)
                                hasEvents -> Color(0xFF0099CC).copy(alpha = 0.5f)
                                else -> Color(0xFF1F1F24)
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { selectedDay = dayNum },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = dayNum.toString(),
                            color = if (isSelected) Color(0xFF33B5E5) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (hasEvents) {
                            Spacer(modifier = Modifier.height(2.dp))
                            // Dot event alarm pointer
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color(0xFF33B5E5), CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // Selected Day agenda header details
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .border(BorderStroke(1.dp, Color(0xFF222222)))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "AGENDA DEL DÍA $selectedDay",
                color = Color(0xFF33B5E5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            IconButton(
                onClick = { showAddEventDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir evento",
                    tint = Color(0xFF33B5E5)
                )
            }
        }

        // Day Events List
        val filteredEvents = allEvents.filter { it.day == selectedDay }
        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Sin eventos registrados", color = Color.DarkGray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                items(filteredEvents) { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .drawBehind {
                                drawLine(
                                    color = Color(0xFF1A1A1A),
                                    start = Offset(0f, this.size.height),
                                    end = Offset(this.size.width, this.size.height),
                                    strokeWidth = 1f
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = Color(0xFFFFBB33),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = event.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(text = event.time, color = Color.Gray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.deleteCalendarEvent(event.id) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Borrar evento", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    if (showAddEventDialog) {
        AlertDialog(
            onDismissRequest = { showAddEventDialog = false },
            title = { Text(text = "Nuevo Evento - Día $selectedDay", color = Color(0xFF33B5E5)) },
            text = {
                Column {
                    Text(text = "Título del evento:", color = Color.Gray, fontSize = 12.sp)
                    TextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF141414),
                            unfocusedContainerColor = Color(0xFF141414),
                            focusedIndicatorColor = Color(0xFF33B5E5)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Hora de evento:", color = Color.Gray, fontSize = 12.sp)
                    TextField(
                        value = eventTime,
                        onValueChange = { eventTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF141414),
                            unfocusedContainerColor = Color(0xFF141414),
                            focusedIndicatorColor = Color(0xFF33B5E5)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (eventTitle.isNotBlank()) {
                            viewModel.addCalendarEvent(selectedDay, eventTitle, eventTime)
                        }
                        showAddEventDialog = false
                    }
                ) {
                    Text("GUARDAR", color = Color(0xFF33B5E5))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEventDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = Color(0xFF141414),
            textContentColor = Color.White
        )
    }
}
