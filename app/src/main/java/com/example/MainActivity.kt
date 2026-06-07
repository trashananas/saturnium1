package com.example

import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val database by lazy { SaturniumDatabase.getDatabase(this) }
    private val repository by lazy { SaturniumRepository(database.saturniumDao()) }
    
    private val viewModel: SaturniumViewModel by viewModels {
        SaturniumViewModelFactory(repository, this)
    }

    // Broadcast receiver to refresh ringing status if triggered by AlarmManager
    private val ringReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateRingingStateFromPrefs()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Уведомления для будильников включены!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Пожалуйста, включите уведомления, чтобы не пропустить будильник.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check/request notifications on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                val isRinging by viewModel.isRinging.collectAsStateWithLifecycle()
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(ImmersiveBg, ImmersiveInnerSurface)
                                )
                            )
                            .padding(innerPadding)
                    ) {
                        SaturniumAppScreen(viewModel = viewModel)

                        // Alarm Ring overlay
                        if (isRinging) {
                            RingingAlarmOverlay(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Register receiver for interactive ringing update
        registerReceiver(
            ringReceiver,
            IntentFilter("com.example.saturnium.ALARM_RING_UPDATE"),
            RECEIVER_NOT_EXPORTED
        )
        viewModel.verifyAlarmActive()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(ringReceiver)
    }
}

@Composable
fun SaturniumAppScreen(viewModel: SaturniumViewModel) {
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()
    val activeDayConfigs by viewModel.activeDayConfigs.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val nextAlarmString by viewModel.nextAlarmString.collectAsStateWithLifecycle()

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var selectedDayToEdit by remember { mutableStateOf<CycleDayConfig?>(null) }

    if (activeCycle == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = IceBlueAccent)
        }
        return
    }

    val currentCycleSafe = activeCycle!!

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // Core Space Header
        item {
            SpaceHeaderSection(
                cycle = currentCycleSafe,
                nextAlarmString = nextAlarmString,
                onTriggerSimulation = { viewModel.triggerInstantSimulation() },
                onSelectTemplate = { showTemplateDialog = true },
                onSelectStartDate = { showDatePickerDialog = true }
            )
        }

        // Horizontal Preset Shift Cards
        item {
            PresetsQuickRow(onApplyPreset = { templateId ->
                viewModel.applyPresetTemplate(templateId)
            })
        }

        // Custom Adaptive Column Calendar Widget
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ImmersiveInnerSurface)
                    .border(1.dp, ImmersiveBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                // Calendar navigation toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.selectMonth(-1) },
                        modifier = Modifier.testTag("prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Предыдущий месяц",
                            tint = IceBlueAccent
                        )
                    }

                    Text(
                        text = viewModel.getRussianMonthYearLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        color = IceBlueAccent,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { viewModel.selectMonth(1) },
                        modifier = Modifier.testTag("next_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Следующий месяц",
                            tint = IceBlueAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Headers representation: N columns for N cycle days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 1..currentCycleSafe.cycleLength) {
                        val config = activeDayConfigs.find { it.dayIndex == i }
                        val isWork = config?.isWorkDay ?: false
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isWork) IndigoPrimary.copy(alpha = 0.25f) else OffRedBg.copy(alpha = 0.5f))
                                .border(1.dp, if (isWork) IndigoPrimary.copy(alpha = 0.5f) else OffRedText.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Днь $i",
                                    fontSize = if (currentCycleSafe.cycleLength > 6) 10.sp else 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (isWork) "Р" else "В",
                                    fontSize = if (currentCycleSafe.cycleLength > 6) 10.sp else 12.sp,
                                    color = if (isWork) IceBlueAccent else OffRedText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom calendar grid
                val cells = viewModel.getCalendarCells()
                val columnCount = currentCycleSafe.cycleLength

                if (cells.isNotEmpty() && columnCount > 0) {
                    val rowsCount = cells.size / columnCount
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (r in 0 until rowsCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (c in 0 until columnCount) {
                                    val cell = cells[r * columnCount + c]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.1f)
                                            .padding(horizontal = 2.dp)
                                    ) {
                                        if (!cell.isBlank) {
                                            val cellBg = when {
                                                cell.isToday -> Brush.verticalGradient(
                                                    colors = listOf(IndigoPrimary, ImmersiveCardBg)
                                                )
                                                cell.isWorkDay -> Brush.verticalGradient(
                                                    colors = listOf(IndigoPrimary.copy(alpha = 0.25f), IndigoPrimary.copy(alpha = 0.10f))
                                                )
                                                else -> Brush.verticalGradient(
                                                    colors = listOf(OffRedBg.copy(alpha = 0.35f), OffRedBg.copy(alpha = 0.15f))
                                                )
                                            }

                                            val borderCol = when {
                                                cell.isToday -> IceBlueAccent
                                                cell.isWorkDay -> IndigoPrimary.copy(alpha = 0.40f)
                                                else -> OffRedText.copy(alpha = 0.25f)
                                            }

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .border(
                                                        width = if (cell.isToday) 2.dp else 1.dp,
                                                        color = borderCol,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(cellBg)
                                                    .clickable {
                                                        // Toggle shift on click, or load detailed config in bottom list
                                                        val targetConfig = activeDayConfigs.find { it.dayIndex == cell.dayIndexInCycle }
                                                        if (targetConfig != null) {
                                                            selectedDayToEdit = targetConfig
                                                        }
                                                    }
                                                    .testTag("calendar_cell_${cell.dayOfMonth}"),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            text = cell.dayOfMonth.toString(),
                                                            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = if (currentCycleSafe.cycleLength > 6) 12.sp else 14.sp,
                                                            color = if (cell.isToday) IceBlueAccent else if (cell.isWorkDay) TextPrimary else OffRedText
                                                        )
                                                        
                                                        // Mini indicator for Alarm
                                                        if (cell.alarmEnabled && !cell.isBlank) {
                                                            Icon(
                                                                imageVector = Icons.Default.Notifications,
                                                                contentDescription = "Будильник",
                                                                tint = if (cell.isToday) IceBlueAccent else if (cell.isWorkDay) IceBlueAccent else OffRedText,
                                                                modifier = Modifier.size(if (currentCycleSafe.cycleLength > 6) 8.dp else 11.dp)
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
                    }
                }
            }
        }

        // Shift Days Editor Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Настройки дней цикла: ${currentCycleSafe.cycleLength} дн.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                // Buttons to shorten or lengthen the cycle dynamically
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.changeCycleLength(currentCycleSafe.cycleLength - 1) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(ImmersiveCardBg, CircleShape),
                        enabled = currentCycleSafe.cycleLength > 1
                    ) {
                        Icon(Icons.Default.Remove, "Shorten cycle", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { viewModel.changeCycleLength(currentCycleSafe.cycleLength + 1) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(ImmersiveCardBg, CircleShape),
                        enabled = currentCycleSafe.cycleLength < 14
                    ) {
                        Icon(Icons.Default.Add, "Lengthen cycle", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // List of Day adjusters
        items(activeDayConfigs, key = { it.id }) { dayConfig ->
            DayConfigCard(
                config = dayConfig,
                onToggleAlarm = { viewModel.toggleAlarmForDay(dayConfig) },
                onToggleWork = { viewModel.toggleWorkDayForDay(dayConfig) },
                onEditClick = { selectedDayToEdit = dayConfig }
            )
        }
    }

    // Modal dialog for selecting template cycle
    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("Выбор графика работы", color = TextPrimary) },
            containerColor = ImmersiveSurface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val presets = listOf(
                        Pair(1, "График 3 через 1 (Стандарт)"),
                        Pair(2, "График 2 через 2"),
                        Pair(3, "Смена: День-Ночь-Выходной"),
                        Pair(4, "График 1 через 1"),
                        Pair(5, "Обычный 5 через 2 (Пятидневка)")
                    )

                    presets.forEach { (id, label) ->
                        Button(
                            onClick = {
                                viewModel.applyPresetTemplate(id)
                                showTemplateDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentCycleSafe.name.contains(label.substring(0, 5))) IceBlueAccent else ImmersiveCardBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, color = if (currentCycleSafe.name.contains(label.substring(0, 5))) Color(0xFF002D6E) else TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text("Закрыть", color = IceBlueAccent)
                }
            }
        )
    }

    // Modal date picker for anchor start date (using Native Android DatePickerDialog wrapped elegantly)
    if (showDatePickerDialog) {
        val calendarContext = LocalContext.current
        val currentCalendar = Calendar.getInstance().apply {
            timeInMillis = currentCycleSafe.startDateMillis
        }

        LaunchedEffect(Unit) {
            DatePickerDialog(
                calendarContext,
                { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                    val selectedCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    viewModel.setCycleStartDate(selectedCal.timeInMillis)
                    showDatePickerDialog = false
                },
                currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnDismissListener { showDatePickerDialog = false }
                show()
            }
        }
    }

    // Dialogue to rename shift day, adjusting its hours & minutes directly
    if (selectedDayToEdit != null) {
        val editingConfig = selectedDayToEdit!!
        var tempName by remember(editingConfig.id) { mutableStateOf(editingConfig.shiftName) }
        var tempHour by remember(editingConfig.id) { mutableIntStateOf(editingConfig.alarmHour) }
        var tempMinute by remember(editingConfig.id) { mutableIntStateOf(editingConfig.alarmMinute) }

        AlertDialog(
            onDismissRequest = { selectedDayToEdit = null },
            title = { Text("Настройка Смены — День ${editingConfig.dayIndex}", color = TextPrimary) },
            containerColor = ImmersiveSurface,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Название смены") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ImmersiveInnerSurface,
                            unfocusedContainerColor = ImmersiveInnerSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = IceBlueAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Text("Время будильника для этой смены:", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Hour selector column
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Часы", color = IceBlueAccent, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { tempHour = (tempHour - 1 + 24) % 24 }) {
                                        Icon(Icons.Default.KeyboardArrowDown, "Dec hr", tint = TextPrimary)
                                    }
                                    Text(String.format("%02d", tempHour), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { tempHour = (tempHour + 1) % 24 }) {
                                        Icon(Icons.Default.KeyboardArrowUp, "Inc hr", tint = TextPrimary)
                                    }
                                }
                            }

                            Text(":", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))

                            // Minute selector column
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Минуты", color = IceBlueAccent, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { tempMinute = (tempMinute - 5 + 60) % 60 }) {
                                        Icon(Icons.Default.KeyboardArrowDown, "Dec min", tint = TextPrimary)
                                    }
                                    Text(String.format("%02d", tempMinute), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { tempMinute = (tempMinute + 5) % 60 }) {
                                        Icon(Icons.Default.KeyboardArrowUp, "Inc min", tint = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDayConfiguration(
                            editingConfig.copy(
                                shiftName = tempName,
                                alarmHour = tempHour,
                                alarmMinute = tempMinute
                            )
                        )
                        selectedDayToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IceBlueAccent)
                ) {
                    Text("Сохранить", color = Color(0xFF002D6E))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDayToEdit = null }) {
                    Text("Отмена", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun SpaceHeaderSection(
    cycle: ShiftCycle,
    nextAlarmString: String,
    onTriggerSimulation: () -> Unit,
    onSelectTemplate: () -> Unit,
    onSelectStartDate: () -> Unit
) {
    val dateStr = remember(cycle.startDateMillis) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
        sdf.format(Date(cycle.startDateMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ImmersiveSurface, ImmersiveInnerSurface)
                )
            )
            .border(1.dp, ImmersiveBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = "🪐 Saturnium",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = IceBlueAccent
                )
                Text(
                    text = "Космический график смен",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Canvas Drawing of Saturn
            SaturnCanvas(
                modifier = Modifier
                    .size(60.dp)
                    .clickable { onTriggerSimulation() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large high-contrast Card showing next alarm in cosmic alignment style
        Surface(
            color = ImmersiveInnerSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(ImmersiveCardBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Будильник",
                        tint = IceBlueAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "СЛЕДУЮЩИЙ БУДИЛЬНИК",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = nextAlarmString,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Details (Anchor information)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = cycle.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "Старт: $dateStr",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Calendar trigger
                IconButton(
                    onClick = onSelectStartDate,
                    modifier = Modifier
                        .size(36.dp)
                        .background(ImmersiveCardBg, CircleShape)
                ) {
                    Icon(Icons.Default.DateRange, "Начало графика", tint = IceBlueAccent, modifier = Modifier.size(18.dp))
                }

                // Templates trigger
                IconButton(
                    onClick = onSelectTemplate,
                    modifier = Modifier
                        .size(36.dp)
                        .background(ImmersiveCardBg, CircleShape)
                ) {
                    Icon(Icons.Default.Settings, "Шаблоны", tint = IceBlueAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SaturnCanvas(modifier: Modifier = Modifier) {
    // Endless planet rotation/tilt anim
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)
        val radius = width * 0.28f

        // Draw Saturn's back rings (drawn first so planet clips front rings!)
        drawRingBack(this, center, radius)

        // Draw Planet Sphere with gradient mapping to #3F51B5 -> #A5C8FF
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(IndigoPrimary, ActiveGradientEnd),
                start = Offset(center.x - radius, center.y + radius),
                end = Offset(center.x + radius, center.y - radius)
            ),
            radius = radius,
            center = center,
            alpha = 1.0f
        )
        // Shading overlay to give planet 3D sphere look
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xAA070913)),
                center = Offset(center.x - radius/2, center.y - radius/2),
                radius = radius * 1.5f
            ),
            radius = radius,
            center = center
        )

        // Draw Saturn's front rings
        drawRingFront(this, center, radius)
    }
}

fun drawRingBack(drawScope: androidx.compose.ui.graphics.drawscope.DrawScope, center: Offset, radius: Float) {
    // Saturn rings tilted (rotation is simulated by visual ellipse)
    drawScope.apply {
        drawOval(
            color = IceBlueAccent,
            topLeft = Offset(center.x - radius * 1.8f, center.y - radius * 0.4f),
            size = Size(radius * 3.6f, radius * 0.8f),
            style = Stroke(width = radius * 0.15f),
            alpha = 0.7f
        )
    }
}

fun drawRingFront(drawScope: androidx.compose.ui.graphics.drawscope.DrawScope, center: Offset, radius: Float) {
    drawScope.apply {
        // Encircle the front with a thinner secondary neon ring to make it appear 3D
        drawArc(
            color = ActiveGradientEnd,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.7f, center.y - radius * 0.38f),
            size = Size(radius * 3.4f, radius * 0.76f),
            style = Stroke(width = radius * 0.08f),
            alpha = 0.85f
        )
    }
}

@Composable
fun PresetsQuickRow(onApplyPreset: (Int) -> Unit) {
    val presets = listOf(
        Triple(1, "3 через 1", "3р / 1в"),
        Triple(2, "2 через 2", "2р / 2в"),
        Triple(3, "День/Ночь", "День / Ночь / 2в"),
        Triple(5, "5 через 2", "5р / 2в")
    )
    
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { (id, title, desc) ->
            Card(
                onClick = { onApplyPreset(id) },
                colors = CardDefaults.cardColors(containerColor = ImmersiveCardBg),
                modifier = Modifier
                    .width(108.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, ImmersiveBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = IceBlueAccent,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = desc,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DayConfigCard(
    config: CycleDayConfig,
    onToggleAlarm: () -> Unit,
    onToggleWork: () -> Unit,
    onEditClick: () -> Unit
) {
    val timeLabel = String.format("%02d:%02d", config.alarmHour, config.alarmMinute)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (config.isWorkDay) IndigoPrimary.copy(alpha = 0.40f) else OffRedText.copy(alpha = 0.20f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = ImmersiveSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circle denoting Work / Off
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (config.isWorkDay) IndigoPrimary.copy(alpha = 0.25f) else OffRedBg.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { onToggleWork() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (config.isWorkDay) Icons.Default.WorkOutline else Icons.Default.Bed,
                        contentDescription = "Роль дня",
                        tint = if (config.isWorkDay) IceBlueAccent else OffRedText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Name and status
                Column(
                    modifier = Modifier.widthIn(max = 140.dp)
                ) {
                    Text(
                        text = "День ${config.dayIndex}",
                        fontSize = 11.sp,
                        color = IceBlueAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config.shiftName,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (config.isWorkDay) "Рядовая смена" else "Выходной день",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            // Alarm controller row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Clickable alarm time
                if (config.alarmEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ImmersiveInnerSurface),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (config.isWorkDay) IndigoPrimary.copy(alpha = 0.40f) else OffRedText.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .clickable { onEditClick() }
                    ) {
                        Text(
                            text = timeLabel,
                            fontWeight = FontWeight.Bold,
                            color = if (config.isWorkDay) IceBlueAccent else OffRedText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = "ВЫКЛ",
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { onEditClick() }
                            .padding(end = 4.dp)
                    )
                }

                // Edit Button
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, "Редактировать время", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }

                // Switch for Alarm enable
                Switch(
                    checked = config.alarmEnabled,
                    onCheckedChange = { onToggleAlarm() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = IceBlueAccent,
                        checkedTrackColor = IndigoPrimary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = ImmersiveInnerSurface
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}

/**
 * Stunning Interactive Overlay ringing when alarm fires!
 * Saturn sphere is animated and user must perform slide unlock to clear the ringing state.
 */
@Composable
fun RingingAlarmOverlay(
    viewModel: SaturniumViewModel,
    modifier: Modifier = Modifier
) {
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()
    val activeDayConfigs by viewModel.activeDayConfigs.collectAsStateWithLifecycle()
    val now = Calendar.getInstance()

    // Determine currently running shift
    val shiftDayIndex = activeCycle?.let {
        AlarmHelper.getDayIndexForDate(it.startDateMillis, now.timeInMillis, it.cycleLength)
    } ?: 1
    val shiftConfig = activeDayConfigs.find { it.dayIndex == shiftDayIndex }
    val shiftName = shiftConfig?.shiftName ?: "Ваша смена"

    // Endless pulse and rotation animations
    val infiniteTransition = rememberInfiniteTransition()
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Gesture tracking for Slide-To-Unlock
    val sliderWidth = 300.dp
    val sliderWidthPx = 300 * 3f // approximate
    var offsetX by remember { mutableStateOf(0f) }
    val maxDragDistance = 220f // absolute drag threshold to trigger cancel

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ImmersiveBg, IndigoPrimary.copy(alpha = 0.4f), ImmersiveInnerSurface)
                )
            )
            .clickable(enabled = false) { /* Prevent pass-through click */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Cosmic alarm subtitle
            Text(
                text = "🪐 SATURNIUM WAKE-UP 🪐",
                fontSize = 12.sp,
                color = IceBlueAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pulse Ring container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Outer celestial pulse wave
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.15f))
                        .border(1.dp, IceBlueAccent.copy(alpha = 0.3f), CircleShape)
                )

                // Large Glowing Saturn core
                Canvas(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(spinRotation)
                ) {
                    val r = size.width * 0.28f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Rings
                    drawRingBack(this, center, r)
                    
                    // Sphere with gradient mapping to #3F51B5 -> #A5C8FF
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(IndigoPrimary, ActiveGradientEnd),
                            start = Offset(center.x - r, center.y + r),
                            end = Offset(center.x + r, center.y - r)
                        ),
                        radius = r
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color(0xAA0B0C10)),
                            center = Offset(center.x - r/2, center.y - r/2),
                            radius = r * 1.5f
                        ),
                        radius = r
                    )

                    drawRingFront(this, center, r)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Alarm main message
            Text(
                text = "ПОРА НА СМЕНУ!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Text(
                text = shiftName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = IceBlueAccent
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Slidable unlock trigger bar
            Box(
                modifier = Modifier
                    .width(sliderWidth)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(ImmersiveInnerSurface)
                    .border(1.dp, IndigoPrimary.copy(alpha = 0.40f), RoundedCornerShape(32.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Sliding track invite text
                Text(
                    text = "Прoведи для отключения >>>",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Sliding handle (The active glowing moon)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(IceBlueAccent, ActiveGradientEnd)
                            )
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (offsetX >= maxDragDistance) {
                                        viewModel.dismissAlarm()
                                    } else {
                                        offsetX = 0f
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val targetX = offsetX + dragAmount.x
                                    offsetX = targetX.coerceIn(0f, maxDragDistance + 20f)
                                }
                            )
                        }
                        .testTag("alarm_slider_handle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Слайдер",
                        tint = Color(0xFF002D6E),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Fallback direct emergency mute button in case sliding gets tricky
            TextButton(
                onClick = { viewModel.dismissAlarm() },
                modifier = Modifier.testTag("emergency_mute_button")
            ) {
                Text(
                    text = "ПРОПУСТИТЬ СИГНАЛ",
                    fontSize = 11.sp,
                    color = OffRedText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
