package com.example.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.AlarmService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class CalendarCell(
    val dateMillis: Long = 0,
    val dayOfMonth: Int = 0,
    val dayOfWeekName: String = "",
    val dayIndexInCycle: Int = 0,
    val isWorkDay: Boolean = false,
    val alarmHour: Int = 0,
    val alarmMinute: Int = 0,
    val alarmEnabled: Boolean = false,
    val isCurrentMonth: Boolean = false,
    val isToday: Boolean = false,
    val isBlank: Boolean = true,
    val shiftName: String = ""
)

class SaturniumViewModel(
    private val repository: SaturniumRepository,
    private val context: Context
) : ViewModel() {

    private val TAG = "SaturniumViewModel"

    // Exposed states
    val activeCycle: StateFlow<ShiftCycle?> = repository.activeCycle.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val activeDayConfigs: StateFlow<List<CycleDayConfig>> = repository.activeDayConfigs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current month being viewed in custom calendar
    private val _currentMonth = MutableStateFlow<Calendar>(Calendar.getInstance())
    val currentMonth: StateFlow<Calendar> = _currentMonth.asStateFlow()

    // Alarm ringing state (ringing simulation/real)
    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    // Next alarm scheduled visual string
    private val _nextAlarmString = MutableStateFlow("Загрузка...")
    val nextAlarmString: StateFlow<String> = _nextAlarmString.asStateFlow()

    // Selected ringtone name
    private val _selectedRingtoneName = MutableStateFlow("По умолчанию (Системный)")
    val selectedRingtoneName: StateFlow<String> = _selectedRingtoneName.asStateFlow()

    val allCyclicReminders: StateFlow<List<CyclicReminder>> = repository.allCyclicReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allDateExclusions: StateFlow<List<DateExclusion>> = repository.allDateExclusions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Automatically check if database is empty on start, and install a default "3 через 1" shift
        viewModelScope.launch {
            val currentCycle = repository.getActiveCycleDirect()
            if (currentCycle == null) {
                Log.d(TAG, "No cycle found on startup. Initializing default 3-through-1 template...")
                initializeDefaultCycle()
            } else {
                updateAlarmSchedule()
            }
        }
        
        // Sync scheduled alarm label
        checkNextAlarmString()

        // Keep local ringing state in sync with shared preferences updates
        updateRingingStateFromPrefs()

        // Sync selected ringtone name on launch
        refreshRingtoneName()
    }

    fun updateRingingStateFromPrefs() {
        val prefs = context.getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
        _isRinging.value = prefs.getBoolean("is_ringing", false)
    }

    fun refreshRingtoneName() {
        val prefs = context.getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
        _selectedRingtoneName.value = prefs.getString("selected_ringtone_name", "По умолчанию (Системный)") ?: "По умолчанию (Системный)"
    }

    fun verifyAlarmActive() {
        checkNextAlarmString()
        updateRingingStateFromPrefs()
        refreshRingtoneName()
    }

    private fun checkNextAlarmString() {
        _nextAlarmString.value = AlarmHelper.getNextAlarmLabel(context)
    }

    /**
     * Reschedules alarm based on current active database values.
     */
    private fun updateAlarmSchedule() {
        viewModelScope.launch {
            val cycle = repository.getActiveCycleDirect()
            val configs = cycle?.let { repository.getDayConfigsDirect(it.id) } ?: emptyList()
            AlarmHelper.scheduleNextAlarm(context, cycle, configs)
            checkNextAlarmString()
        }
    }

    /**
     * Navigation for month selection in custom calendar
     */
    fun selectMonth(offset: Int) {
        val newCal = Calendar.getInstance().apply {
            timeInMillis = _currentMonth.value.timeInMillis
            add(Calendar.MONTH, offset)
        }
        _currentMonth.value = newCal
    }

    /**
     * Initialize standard 3 через 1 cycle today.
     */
    private suspend fun initializeDefaultCycle() {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val cycle = ShiftCycle(
            name = "График 3 через 1",
            startDateMillis = todayStart,
            cycleLength = 4
        )

        val days = listOf(
            CycleDayConfig(cycleId = 0, dayIndex = 1, isWorkDay = true, shiftName = "Смена 1", alarmEnabled = true, alarmHour = 7, alarmMinute = 0),
            CycleDayConfig(cycleId = 0, dayIndex = 2, isWorkDay = true, shiftName = "Смена 2", alarmEnabled = true, alarmHour = 7, alarmMinute = 0),
            CycleDayConfig(cycleId = 0, dayIndex = 3, isWorkDay = true, shiftName = "Смена 3", alarmEnabled = true, alarmHour = 7, alarmMinute = 0),
            CycleDayConfig(cycleId = 0, dayIndex = 4, isWorkDay = false, shiftName = "Выходной", alarmEnabled = false, alarmHour = 9, alarmMinute = 0)
        )

        repository.replaceCycleAndDays(cycle, days)
        updateAlarmSchedule()
    }

    /**
     * Set a cycle start date (anchor date).
     */
    fun setCycleStartDate(dateMillis: Long) {
        viewModelScope.launch {
            val current = repository.getActiveCycleDirect() ?: return@launch
            val updated = current.copy(startDateMillis = dateMillis)
            repository.updateCycle(updated)
            updateAlarmSchedule()
        }
    }

    /**
     * Set details for a specific day in the cycle
     */
    fun updateDayConfiguration(config: CycleDayConfig) {
        viewModelScope.launch {
            repository.updateDayConfig(config)
            updateAlarmSchedule()
        }
    }

    /**
     * Toggle work/alarm properties directly from list items or cards
     */
    fun toggleAlarmForDay(config: CycleDayConfig) {
        updateDayConfiguration(config.copy(alarmEnabled = !config.alarmEnabled))
    }

    fun toggleWorkDayForDay(config: CycleDayConfig) {
        val isWorkNow = !config.isWorkDay
        updateDayConfiguration(config.copy(
            isWorkDay = isWorkNow
        ))
    }

    fun addCyclicReminder(
        label: String,
        type: String,
        targetCycleDay: Int? = null,
        intervalDays: Int? = null,
        startDateMillis: Long? = null,
        hour: Int,
        minute: Int
    ) {
        viewModelScope.launch {
            repository.insertCyclicReminder(
                CyclicReminder(
                    label = label,
                    type = type,
                    targetCycleDay = targetCycleDay,
                    intervalDays = intervalDays,
                    startDateMillis = startDateMillis,
                    hour = hour,
                    minute = minute,
                    isEnabled = true
                )
            )
            updateAlarmSchedule()
        }
    }

    fun deleteCyclicReminder(reminder: CyclicReminder) {
        viewModelScope.launch {
            repository.deleteCyclicReminder(reminder)
            updateAlarmSchedule()
        }
    }

    fun toggleDateExclusion(dateStr: String, exclude: Boolean) {
        viewModelScope.launch {
            if (exclude) {
                repository.insertDateExclusion(DateExclusion(dateStr))
            } else {
                repository.deleteDateExclusionByDate(dateStr)
            }
            updateAlarmSchedule()
        }
    }

    /**
     * Applies preset templates for shifts.
     */
    fun applyPresetTemplate(templateType: Int) {
        viewModelScope.launch {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val cycle: ShiftCycle
            val days: List<CycleDayConfig>

            when (templateType) {
                1 -> { // 3 through 1
                    cycle = ShiftCycle(name = "График 3 через 1", startDateMillis = todayStart, cycleLength = 4)
                    days = listOf(
                        CycleDayConfig(cycleId = 0, dayIndex = 1, isWorkDay = true, shiftName = "Смена 1", alarmEnabled = true, alarmHour = 7, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 2, isWorkDay = true, shiftName = "Смена 2", alarmEnabled = true, alarmHour = 7, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 3, isWorkDay = true, shiftName = "Смена 3", alarmEnabled = true, alarmHour = 7, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 4, isWorkDay = false, shiftName = "Выходной", alarmEnabled = false, alarmHour = 9, alarmMinute = 0)
                    )
                }
                2 -> { // 2 through 2
                    cycle = ShiftCycle(name = "График 2 через 2", startDateMillis = todayStart, cycleLength = 4)
                    days = listOf(
                        CycleDayConfig(cycleId = 0, dayIndex = 1, isWorkDay = true, shiftName = "Смена 1", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 2, isWorkDay = true, shiftName = "Смена 2", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 3, isWorkDay = false, shiftName = "Выходной 1", alarmEnabled = false, alarmHour = 9, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 4, isWorkDay = false, shiftName = "Выходной 2", alarmEnabled = false, alarmHour = 9, alarmMinute = 0)
                    )
                }
                3 -> { // Day Night Off Off
                    cycle = ShiftCycle(name = "Смена: День-Ночь-Выходной-Выходной", startDateMillis = todayStart, cycleLength = 4)
                    days = listOf(
                        CycleDayConfig(cycleId = 0, dayIndex = 1, isWorkDay = true, shiftName = "Дневная смена", alarmEnabled = true, alarmHour = 7, alarmMinute = 30),
                        CycleDayConfig(cycleId = 0, dayIndex = 2, isWorkDay = true, shiftName = "Ночная смена", alarmEnabled = true, alarmHour = 16, alarmMinute = 30),
                        CycleDayConfig(cycleId = 0, dayIndex = 3, isWorkDay = false, shiftName = "Отсыпной", alarmEnabled = false, alarmHour = 10, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 4, isWorkDay = false, shiftName = "Выходной", alarmEnabled = false, alarmHour = 10, alarmMinute = 0)
                    )
                }
                4 -> { // Day/Night/Evening/Off: 4 through 4 but let's make a standard 1 through 1
                    cycle = ShiftCycle(name = "График 1 через 1", startDateMillis = todayStart, cycleLength = 2)
                    days = listOf(
                        CycleDayConfig(cycleId = 0, dayIndex = 1, isWorkDay = true, shiftName = "Рабочая смена", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 2, isWorkDay = false, shiftName = "Выходной", alarmEnabled = false, alarmHour = 9, alarmMinute = 0)
                    )
                }
                5 -> { // 5 through 2 (Standard Week)
                    cycle = ShiftCycle(name = "График 5 через 2", startDateMillis = todayStart, cycleLength = 7)
                    days = listOf(
                        CycleDayConfig(cycleId = 0, dayIndex = 1, isWorkDay = true, shiftName = "Понедельник", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 2, isWorkDay = true, shiftName = "Вторник", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 3, isWorkDay = true, shiftName = "Среда", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 4, isWorkDay = true, shiftName = "Четверг", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 5, isWorkDay = true, shiftName = "Пятница", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 6, isWorkDay = false, shiftName = "Суббота (Вых)", alarmEnabled = false, alarmHour = 10, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 7, isWorkDay = false, shiftName = "Воскресенье (Вых)", alarmEnabled = false, alarmHour = 10, alarmMinute = 0)
                    )
                }
                6 -> { // 1 through 3 (Opposite of 3 through 1)
                    cycle = ShiftCycle(name = "График 1 через 3", startDateMillis = todayStart, cycleLength = 4)
                    days = listOf(
                        CycleDayConfig(cycleId = 0, dayIndex = 1, isWorkDay = true, shiftName = "Смена", alarmEnabled = true, alarmHour = 8, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 2, isWorkDay = false, shiftName = "Выходной 1", alarmEnabled = false, alarmHour = 9, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 3, isWorkDay = false, shiftName = "Выходной 2", alarmEnabled = false, alarmHour = 9, alarmMinute = 0),
                        CycleDayConfig(cycleId = 0, dayIndex = 4, isWorkDay = false, shiftName = "Выходной 3", alarmEnabled = false, alarmHour = 9, alarmMinute = 0)
                    )
                }
                else -> return@launch
            }

            repository.replaceCycleAndDays(cycle, days)
            updateAlarmSchedule()
        }
    }

    /**
     * Increase or decrease the actual cycle length dynamically!
     * If they want a custom 5-day cycle or something.
     */
    fun changeCycleLength(newLength: Int) {
        if (newLength !in 1..21) return
        viewModelScope.launch {
            val current = repository.getActiveCycleDirect() ?: return@launch
            val oldLength = current.cycleLength
            if (oldLength == newLength) return@launch

            val oldDays = repository.getDayConfigsDirect(current.id)
            val newDays = mutableListOf<CycleDayConfig>()

            for (i in 1..newLength) {
                val existing = oldDays.find { it.dayIndex == i }
                if (existing != null) {
                    newDays.add(existing)
                } else {
                    newDays.add(
                        CycleDayConfig(
                            cycleId = current.id,
                            dayIndex = i,
                            isWorkDay = true,
                            shiftName = "День $i",
                            alarmEnabled = true,
                            alarmHour = 8,
                            alarmMinute = 0
                        )
                    )
                }
            }

            val updatedCycle = current.copy(cycleLength = newLength)
            repository.replaceCycleAndDays(updatedCycle, newDays)
            updateAlarmSchedule()
        }
    }

    /**
     * Force dismiss the alarm and stop the AlarmService
     */
    fun dismissAlarm() {
        try {
            val stopIntent = Intent(context, AlarmService::class.java)
            context.stopService(stopIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AlarmService", e)
        }
        
        context.getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_ringing", false)
            .apply()
        _isRinging.value = false
    }

    /**
     * Simulate an immediate ring by launching the full AlarmService
     */
    fun triggerInstantSimulation() {
        try {
            val serviceIntent = Intent(context, AlarmService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AlarmService for simulation", e)
        }
        Log.d(TAG, "Simulated ringing state triggered via AlarmService!")
    }

    /**
     * Set a custom weekly calendar (7 days total, starting on Monday)
     */
    fun applyCustomWeeklyCycle(selectedDays: List<Boolean>) {
        viewModelScope.launch {
            // Find the nearest preceding Monday to align Day 1 of the cycle with Monday
            val mondayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            }.timeInMillis

            val cycle = ShiftCycle(
                name = "Свой недельный график Пн-Вс",
                startDateMillis = mondayCal,
                cycleLength = 7
            )

            val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val fullDayNames = listOf("Понедельник", "Вторник", "Среда", "Ччетверг", "Пятница", "Суббота", "Воскресенье")
            
            val days = (0 until 7).map { i ->
                val isWork = selectedDays.getOrElse(i) { false }
                CycleDayConfig(
                    cycleId = 0,
                    dayIndex = i + 1,
                    isWorkDay = isWork,
                    shiftName = if (isWork) "Смена (${dayNames[i]})" else "Выходной (${dayNames[i]})",
                    alarmEnabled = isWork,
                    alarmHour = 8,
                    alarmMinute = 0
                )
            }

            repository.replaceCycleAndDays(cycle, days)
            updateAlarmSchedule()
        }
    }

    // Helper functions for calendar view translation
    fun getRussianMonthYearLabel(): String {
        val monthNames = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        val month = _currentMonth.value.get(Calendar.MONTH)
        val year = _currentMonth.value.get(Calendar.YEAR)
        return "${monthNames[month]} $year"
    }

    private fun getDayOfWeekShortName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.MONDAY -> "Пн"
            Calendar.TUESDAY -> "Вт"
            Calendar.WEDNESDAY -> "Ср"
            Calendar.THURSDAY -> "Чт"
            Calendar.FRIDAY -> "Пт"
            Calendar.SATURDAY -> "Сб"
            Calendar.SUNDAY -> "Вс"
            else -> ""
        }
    }

    /**
     * Generates custom elements representing days for the layout.
     */
    fun getCalendarCells(): List<CalendarCell> {
        val cycle = activeCycle.value ?: return emptyList()
        val configs = activeDayConfigs.value
        val monthCal = _currentMonth.value

        val cells = mutableListOf<CalendarCell>()
        
        // Target month calendar setup
        val cal = Calendar.getInstance().apply {
            timeInMillis = monthCal.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentMonthValue = cal.get(Calendar.MONTH)
        val currentYearValue = cal.get(Calendar.YEAR)
        
        val n = cycle.cycleLength
        if (n <= 0) return emptyList()
        
        // Find shift day column for the first day of the target month
        val firstDayCycleIndex = AlarmHelper.getDayIndexForDate(cycle.startDateMillis, cal.timeInMillis, n)
        val startColumn = firstDayCycleIndex - 1 // 0-based column index
        
        // Add blank padding cells at startup
        for (i in 0 until startColumn) {
            cells.add(CalendarCell(isBlank = true))
        }
        
        val todayCal = Calendar.getInstance()
        
        // Add all days of the month
        for (day in 1..totalDaysInMonth) {
            val dateCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYearValue)
                set(Calendar.MONTH, currentMonthValue)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 12) // Noon to guarantee DST robustness
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val dateMillis = dateCal.timeInMillis
            val dayIndex = AlarmHelper.getDayIndexForDate(cycle.startDateMillis, dateMillis, n)
            val config = configs.find { it.dayIndex == dayIndex }
            
            val isToday = todayCal.get(Calendar.YEAR) == currentYearValue &&
                          todayCal.get(Calendar.MONTH) == currentMonthValue &&
                          todayCal.get(Calendar.DAY_OF_MONTH) == day
                          
            cells.add(
                CalendarCell(
                    dateMillis = dateMillis,
                    dayOfMonth = day,
                    dayOfWeekName = getDayOfWeekShortName(dateCal.get(Calendar.DAY_OF_WEEK)),
                    dayIndexInCycle = dayIndex,
                    isWorkDay = config?.isWorkDay ?: false,
                    alarmHour = config?.alarmHour ?: 8,
                    alarmMinute = config?.alarmMinute ?: 0,
                    alarmEnabled = config?.alarmEnabled ?: false,
                    isCurrentMonth = true,
                    isToday = isToday,
                    isBlank = false,
                    shiftName = config?.shiftName ?: ""
                )
            )
        }
        
        // Pad the remainder of the last row
        val totalCells = cells.size
        val remainder = totalCells % n
        if (remainder != 0) {
            val endPadding = n - remainder
            for (i in 0 until endPadding) {
                cells.add(CalendarCell(isBlank = true))
            }
        }
        
        return cells
    }
}

class SaturniumViewModelFactory(
    private val repository: SaturniumRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SaturniumViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SaturniumViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
