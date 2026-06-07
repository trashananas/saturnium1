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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val ringtone = android.media.RingtoneManager.getRingtone(this, uri)
                val title = ringtone.getTitle(this) ?: "Пользовательский рингтон"
                getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("selected_ringtone_uri", uri.toString())
                    .putString("selected_ringtone_name", title)
                    .apply()
                viewModel.refreshRingtoneName()
                Toast.makeText(this, "Мелодия успешно изменена!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show on top of lock screen when alarm is ringing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

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
                        SaturniumAppScreen(
                            viewModel = viewModel,
                            onLaunchRingtonePicker = {
                                val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Задать мелодию")
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    val existingUriStr = getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
                                        .getString("selected_ringtone_uri", null)
                                    if (!existingUriStr.isNullOrEmpty()) {
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(existingUriStr))
                                    }
                                }
                                try {
                                    ringtonePickerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Выбор мелодии не поддерживается на данном устройстве", Toast.LENGTH_LONG).show()
                                }
                            },
                            onResetRingtoneToDefault = {
                                getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("selected_ringtone_uri", "")
                                    .putString("selected_ringtone_name", "По умолчанию (Системный)")
                                    .apply()
                                viewModel.refreshRingtoneName()
                                Toast.makeText(this@MainActivity, "Установлена мелодия по умолчанию", Toast.LENGTH_SHORT).show()
                            }
                        )

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaturniumAppScreen(
    viewModel: SaturniumViewModel,
    onLaunchRingtonePicker: () -> Unit,
    onResetRingtoneToDefault: () -> Unit
) {
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()
    val activeDayConfigs by viewModel.activeDayConfigs.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val nextAlarmString by viewModel.nextAlarmString.collectAsStateWithLifecycle()
    val selectedRingtoneName by viewModel.selectedRingtoneName.collectAsStateWithLifecycle()
    
    val allCyclicReminders by viewModel.allCyclicReminders.collectAsStateWithLifecycle()
    val allDateExclusions by viewModel.allDateExclusions.collectAsStateWithLifecycle()

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showRingtoneOptionDialog by remember { mutableStateOf(false) }
    var selectedDayToEdit by remember { mutableStateOf<CycleDayConfig?>(null) }
    var selectedCalendarCellForDetails by remember { mutableStateOf<CalendarCell?>(null) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    val customWeekSelections = remember { mutableStateListOf(true, false, true, false, true, false, false) }

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
                selectedRingtoneName = selectedRingtoneName,
                onTriggerSimulation = { viewModel.triggerInstantSimulation() },
                onSelectTemplate = { showTemplateDialog = true },
                onSelectStartDate = { showDatePickerDialog = true },
                onSelectRingtone = { showRingtoneOptionDialog = true }
            )
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
                        val customName = config?.shiftName ?: "День $i"
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isWork) IndigoPrimary.copy(alpha = 0.25f) else OffRedBg.copy(alpha = 0.5f))
                                .border(1.dp, if (isWork) IndigoPrimary.copy(alpha = 0.5f) else OffRedText.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable {
                                    if (config != null) {
                                        selectedDayToEdit = config
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = customName,
                                    fontSize = if (currentCycleSafe.cycleLength > 5) 9.sp else 11.sp,
                                    color = if (isWork) IceBlueAccent else OffRedText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                Text(
                                    text = "Днь $i",
                                    fontSize = if (currentCycleSafe.cycleLength > 5) 8.sp else 9.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

                                            val cellDateCal = Calendar.getInstance().apply {
                                                timeInMillis = cell.dateMillis
                                            }
                                            val cellYear = cellDateCal.get(Calendar.YEAR)
                                            val cellMonth = cellDateCal.get(Calendar.MONTH)
                                            val cellDateStr = String.format("%04d-%02d-%02d", cellYear, cellMonth + 1, cell.dayOfMonth)
                                            
                                            val isCellExcluded = allDateExclusions.any { it.dateStr == cellDateStr }
                                            
                                            val cellCyclicRemindersCount = allCyclicReminders.count { r ->
                                                if (!r.isEnabled) return@count false
                                                if (r.type == "CYCLE_DAY") {
                                                    r.targetCycleDay == cell.dayIndexInCycle
                                                } else { // INTERVAL
                                                    val start = r.startDateMillis ?: 0L
                                                    val days = AlarmHelper.getDaysBetween(start, cell.dateMillis)
                                                    val rInterval = r.intervalDays ?: 1
                                                    days >= 0 && days % rInterval == 0
                                                }
                                            }
                                            val hasCellCyclic = cellCyclicRemindersCount > 0

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
                                                    .combinedClickable(
                                                        onClick = {
                                                            val targetConfig = activeDayConfigs.find { it.dayIndex == cell.dayIndexInCycle }
                                                            if (targetConfig != null) {
                                                                selectedDayToEdit = targetConfig
                                                            }
                                                        },
                                                        onLongClick = {
                                                            selectedCalendarCellForDetails = cell
                                                        }
                                                    )
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
                                                        
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            // Regular Alarm status: active, excluded, or none
                                                            if (cell.alarmEnabled && !cell.isBlank) {
                                                                if (isCellExcluded) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.NotificationsOff,
                                                                        contentDescription = "Регулярный отключен на сегодня",
                                                                        tint = Color.Gray,
                                                                        modifier = Modifier.size(if (currentCycleSafe.cycleLength > 6) 9.dp else 12.dp)
                                                                    )
                                                                } else {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Notifications,
                                                                        contentDescription = "Регулярный будильник",
                                                                        tint = if (cell.isToday) IceBlueAccent else if (cell.isWorkDay) IceBlueAccent else OffRedText,
                                                                        modifier = Modifier.size(if (currentCycleSafe.cycleLength > 6) 9.dp else 12.dp)
                                                                    )
                                                                }
                                                            }
                                                            
                                                            // Cyclic reminders indicator
                                                            if (hasCellCyclic && !cell.isBlank) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Star,
                                                                    contentDescription = "Циклическое напоминание",
                                                                    tint = Color(0xFFFFD700),
                                                                    modifier = Modifier.size(if (currentCycleSafe.cycleLength > 6) 9.dp else 12.dp)
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
                        enabled = currentCycleSafe.cycleLength < 21
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

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Циклические напоминания",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = { showAddReminderDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = IceBlueAccent),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAlarm,
                        contentDescription = "Добавить",
                        tint = Color(0xFF002D6E),
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("Создать", color = Color(0xFF002D6E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (allCyclicReminders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ImmersiveCardBg)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Нет созданных напоминаний. Нажмите 'Создать', чтобы настроить.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(allCyclicReminders) { reminder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ImmersiveCardBg)
                        .border(1.dp, ImmersiveBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reminder.label,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        val typeText = if (reminder.type == "CYCLE_DAY") {
                            "Каждый день ${reminder.targetCycleDay} в цикле"
                        } else {
                            "Каждые ${reminder.intervalDays} дн., начиная с x"
                        }
                        Text(
                            text = typeText,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = String.format("Время: %02d:%02d", reminder.hour, reminder.minute),
                            color = IceBlueAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    IconButton(
                        onClick = { viewModel.deleteCyclicReminder(reminder) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить напоминание",
                            tint = OffRedText
                        )
                    }
                }
            }
        }
    }

    // Modal dialog for selecting template cycle
    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { 
                Text(
                    "Настройка графика работы", 
                    color = TextPrimary, 
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ) 
            },
            containerColor = ImmersiveSurface,
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Custom Weekly Calendar Builder Section
                    Text(
                        text = "Свой недельный график (Пн-Вс)",
                        style = MaterialTheme.typography.titleMedium,
                        color = IceBlueAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    
                    Text(
                        text = "Выберите дни, в которые вы работаете (выбранные дни станут рабочими сменами):",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    val russianDaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        russianDaysShort.forEachIndexed { index, dayName ->
                            val isSelected = customWeekSelections[index]
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) IceBlueAccent else ImmersiveCardBg)
                                    .clickable {
                                        customWeekSelections[index] = !isSelected
                                    }
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.Transparent else ImmersiveBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayName,
                                    color = if (isSelected) Color(0xFF002D6E) else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = {
                            viewModel.applyCustomWeeklyCycle(customWeekSelections.toList())
                            showTemplateDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IceBlueAccent
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Применить недельный график", 
                            color = Color(0xFF002D6E), 
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ImmersiveBorder)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Ready Presets Section
                    Text(
                        text = "Готовые шаблоны",
                        style = MaterialTheme.typography.titleMedium,
                        color = IceBlueAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    val presets = listOf(
                        Pair(1, "График 3 через 1 (Стандарт)"),
                        Pair(6, "График 1 через 3 (1р / 3в)"),
                        Pair(2, "График 2 через 2"),
                        Pair(3, "Смена: День-Ночь-Выходной"),
                        Pair(4, "График 1 через 1"),
                        Pair(5, "Обычный 5 через 2 (Пятидневка)")
                    )

                    presets.forEach { (id, label) ->
                        val isSelected = currentCycleSafe.name.contains(label.substring(0, 5))
                        Button(
                            onClick = {
                                viewModel.applyPresetTemplate(id)
                                showTemplateDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IceBlueAccent
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Выбран",
                                        tint = Color(0xFF002D6E),
                                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    label, 
                                    color = Color(0xFF002D6E),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text("Закрыть", color = IceBlueAccent, fontWeight = FontWeight.Bold)
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
                                    IconButton(onClick = { tempMinute = (tempMinute - 1 + 60) % 60 }) {
                                        Icon(Icons.Default.KeyboardArrowDown, "Dec min", tint = TextPrimary)
                                    }
                                    Text(String.format("%02d", tempMinute), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { tempMinute = (tempMinute + 1) % 60 }) {
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

    if (showRingtoneOptionDialog) {
        AlertDialog(
            onDismissRequest = { showRingtoneOptionDialog = false },
            title = { Text("Мелодия Будильника", color = TextPrimary) },
            containerColor = ImmersiveSurface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Текущий рингтон:\n$selectedRingtoneName",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            showRingtoneOptionDialog = false
                            onLaunchRingtonePicker()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = IceBlueAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выбрать новую мелодию...", color = TextPrimary)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showRingtoneOptionDialog = false
                            onResetRingtoneToDefault()
                        },
                        border = BorderStroke(1.dp, IceBlueAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Вернуть системную по умолчанию", color = IceBlueAccent)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRingtoneOptionDialog = false }) {
                    Text("Закрыть", color = IceBlueAccent)
                }
            }
        )
    }

    if (selectedCalendarCellForDetails != null) {
        val cell = selectedCalendarCellForDetails!!
        val cellDateCal = Calendar.getInstance().apply {
            timeInMillis = cell.dateMillis
        }
        val cellYear = cellDateCal.get(Calendar.YEAR)
        val cellMonth = cellDateCal.get(Calendar.MONTH)
        val cellDay = cell.dayOfMonth
        
        val monthLabelShort = when (cellMonth) {
            0 -> "января"
            1 -> "февраля"
            2 -> "марта"
            3 -> "апреля"
            4 -> "мая"
            5 -> "июня"
            6 -> "июля"
            7 -> "августа"
            8 -> "сентября"
            9 -> "октября"
            10 -> "ноября"
            11 -> "декабря"
            else -> ""
        }
        val dayOfWeekLabel = when (cellDateCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Понедельник"
            Calendar.TUESDAY -> "Вторник"
            Calendar.WEDNESDAY -> "Среда"
            Calendar.THURSDAY -> "Четверг"
            Calendar.FRIDAY -> "Пятница"
            Calendar.SATURDAY -> "Суббота"
            Calendar.SUNDAY -> "Воскресенье"
            else -> "Неизвестно"
        }
        val dateTitle = "$cellDay $monthLabelShort $cellYear"
        val cellDateStr = String.format("%04d-%02d-%02d", cellYear, cellMonth + 1, cellDay)
        
        val isExcluded = allDateExclusions.any { it.dateStr == cellDateStr }
        val matchedConfig = activeDayConfigs.find { it.dayIndex == cell.dayIndexInCycle }

        val matchedReminders = allCyclicReminders.filter { r ->
            if (!r.isEnabled) false
            else if (r.type == "CYCLE_DAY") {
                r.targetCycleDay == cell.dayIndexInCycle
            } else {
                val start = r.startDateMillis ?: 0L
                val days = AlarmHelper.getDaysBetween(start, cell.dateMillis)
                val rInterval = r.intervalDays ?: 1
                days >= 0 && days % rInterval == 0
            }
        }

        AlertDialog(
            onDismissRequest = { selectedCalendarCellForDetails = null },
            title = {
                Column {
                    Text(dateTitle, color = IceBlueAccent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(dayOfWeekLabel, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            },
            containerColor = ImmersiveSurface,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Все будильники на этот день:", color = IceBlueAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    var hasAlarms = false
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Regular Alarm
                        if (matchedConfig != null && matchedConfig.alarmEnabled) {
                            hasAlarms = true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ImmersiveInnerSurface)
                                    .border(1.dp, ImmersiveBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Регулярный сменный",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${matchedConfig.shiftName} (День ${matchedConfig.dayIndex})",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = String.format("Время: %02d:%02d", matchedConfig.alarmHour, matchedConfig.alarmMinute),
                                        color = IceBlueAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isExcluded) {
                                        Text(
                                            text = "ОТКЛЮЧЕН НА ЭТОТ ДЕНЬ",
                                            color = OffRedText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        viewModel.toggleDateExclusion(cellDateStr, !isExcluded)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isExcluded) IceBlueAccent else OffRedText.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isExcluded) "Включить" else "Отгул/Выкл",
                                        color = if (isExcluded) Color(0xFF002D6E) else OffRedText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        // Cyclic reminders
                        matchedReminders.forEach { r ->
                            hasAlarms = true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ImmersiveInnerSurface)
                                    .border(1.dp, ImmersiveBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Циклическое напоминание",
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = r.label,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = String.format("Время: %02d:%02d", r.hour, r.minute),
                                        color = IceBlueAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700)
                                )
                            }
                        }
                        
                        if (!hasAlarms) {
                            Text(
                                text = "Будильников на этот день нет.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCalendarCellForDetails = null }) {
                    Text("Закрыть", color = IceBlueAccent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAddReminderDialog) {
        var reminderLabel by remember { mutableStateOf("Мое Напоминание") }
        var reminderType by remember { mutableStateOf("CYCLE_DAY") }
        var targetCycleDay by remember { mutableStateOf(1) }
        var intervalDays by remember { mutableStateOf(3) }
        
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var startDateMillis by remember { mutableStateOf(today.timeInMillis) }
        var selectedHour by remember { mutableStateOf(8) }
        var selectedMinute by remember { mutableStateOf(0) }
        
        var showReminderDatePicker by remember { mutableStateOf(false) }

        if (showReminderDatePicker) {
            val datePickerContext = LocalContext.current
            val dpCal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
            LaunchedEffect(Unit) {
                android.app.DatePickerDialog(
                    datePickerContext,
                    { _: android.widget.DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                        val selectedCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        startDateMillis = selectedCal.timeInMillis
                        showReminderDatePicker = false
                    },
                    dpCal.get(Calendar.YEAR),
                    dpCal.get(Calendar.MONTH),
                    dpCal.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setOnDismissListener { showReminderDatePicker = false }
                }.show()
            }
        }

        AlertDialog(
            onDismissRequest = { showAddReminderDialog = false },
            title = {
                Text(
                    "Новое циклическое напоминание",
                    color = IceBlueAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            containerColor = ImmersiveSurface,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = reminderLabel,
                        onValueChange = { reminderLabel = it },
                        label = { Text("Название напоминания", color = IceBlueAccent) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IceBlueAccent,
                            unfocusedBorderColor = ImmersiveBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Тип периодичности:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { reminderType = "CYCLE_DAY" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (reminderType == "CYCLE_DAY") IceBlueAccent else ImmersiveCardBg
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "День цикла",
                                color = if (reminderType == "CYCLE_DAY") Color(0xFF002D6E) else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { reminderType = "INTERVAL" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (reminderType == "INTERVAL") IceBlueAccent else ImmersiveCardBg
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Интервал (дн)",
                                color = if (reminderType == "INTERVAL") Color(0xFF002D6E) else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (reminderType == "CYCLE_DAY") {
                        Column {
                            Text(
                                "Вызывать в день цикла #: $targetCycleDay",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = targetCycleDay.toFloat(),
                                onValueChange = { targetCycleDay = it.toInt() },
                                valueRange = 1f..currentCycleSafe.cycleLength.toFloat(),
                                steps = if (currentCycleSafe.cycleLength > 1) currentCycleSafe.cycleLength - 2 else 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = IceBlueAccent,
                                    activeTrackColor = IceBlueAccent,
                                    inactiveTrackColor = ImmersiveBorder
                                )
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Повторять каждые (дн):", color = TextPrimary, fontSize = 14.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (intervalDays > 1) intervalDays-- },
                                        modifier = Modifier.background(ImmersiveCardBg, RoundedCornerShape(4.dp)).size(32.dp)
                                    ) {
                                        Text("-", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                                    }
                                    Text("$intervalDays", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    IconButton(
                                        onClick = { intervalDays++ },
                                        modifier = Modifier.background(ImmersiveCardBg, RoundedCornerShape(4.dp)).size(32.dp)
                                    ) {
                                        Text("+", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }

                            val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
                            val startLabel = dateFormat.format(Date(startDateMillis))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ImmersiveCardBg)
                                    .clickable { showReminderDatePicker = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Дата начала отсчета", color = TextSecondary, fontSize = 11.sp)
                                    Text(startLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.DateRange, contentDescription = "Календарь", tint = IceBlueAccent)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ImmersiveBorder)
                    )

                    Text(
                        "Выберите время (прокрутка валиком):",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelPicker(
                            range = 0..23,
                            selectedValue = selectedHour,
                            onValueChange = { selectedHour = it },
                            label = "Часы"
                        )
                        
                        Text(":", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)

                        WheelPicker(
                            range = 0..59,
                            selectedValue = selectedMinute,
                            onValueChange = { selectedMinute = it },
                            label = "Минуты"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCyclicReminder(
                            label = reminderLabel,
                            type = reminderType,
                            targetCycleDay = if (reminderType == "CYCLE_DAY") targetCycleDay else null,
                            intervalDays = if (reminderType == "INTERVAL") intervalDays else null,
                            startDateMillis = if (reminderType == "INTERVAL") startDateMillis else null,
                            hour = selectedHour,
                            minute = selectedMinute
                        )
                        showAddReminderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IceBlueAccent)
                ) {
                    Text("Создать", color = Color(0xFF002D6E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminderDialog = false }) {
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
    selectedRingtoneName: String,
    onTriggerSimulation: () -> Unit,
    onSelectTemplate: () -> Unit,
    onSelectStartDate: () -> Unit,
    onSelectRingtone: () -> Unit
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cycle.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Старт: $dateStr",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { onSelectRingtone() }
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Мелодия",
                        tint = IceBlueAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedRingtoneName,
                        fontSize = 11.sp,
                        color = IceBlueAccent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ringtone selection trigger
                IconButton(
                    onClick = onSelectRingtone,
                    modifier = Modifier
                        .size(36.dp)
                        .background(ImmersiveCardBg, CircleShape)
                ) {
                    Icon(Icons.Default.MusicNote, "Мелодия", tint = IceBlueAccent, modifier = Modifier.size(18.dp))
                }

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
        Triple(6, "1 через 3", "1р / 3в"),
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

@Composable
fun WheelPicker(
    range: IntRange,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    val size = range.last - range.first + 1
    // Place initial positioning in target repetition block
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedValue + size * 4)
    
    LaunchedEffect(selectedValue) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val currentCentered = visibleItems.minByOrNull {
            val centerOffset = layoutInfo.viewportEndOffset / 2
            val itemCenter = it.offset + (it.size / 2)
            kotlin.math.abs(itemCenter - centerOffset)
        }
        val currentVal = currentCentered?.let { range.first + (it.index % size) }
        if (currentVal != selectedValue) {
            listState.scrollToItem(selectedValue + size * 4)
        }
    }
    
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val centerOffset = layoutInfo.viewportEndOffset / 2
                val closest = visibleItems.minByOrNull {
                    val itemCenter = it.offset + (it.size / 2)
                    kotlin.math.abs(itemCenter - centerOffset)
                }
                closest?.let {
                    val index = it.index % size
                    onValueChange(range.first + index)
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(text = label, color = IceBlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .height(130.dp)
                .width(64.dp)
                .background(ImmersiveInnerSurface, RoundedCornerShape(12.dp))
                .border(2.dp, IceBlueAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(IceBlueAccent.copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, IceBlueAccent.copy(alpha = 0.3f)))
            )
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 46.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(size * 10) { index ->
                    val value = range.first + (index % size)
                    val isSelected = value == selectedValue
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable {
                                onValueChange(value)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", value),
                            color = if (isSelected) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isSelected) 20.sp else 16.sp
                        )
                    }
                }
            }
        }
    }
}
