// Filename: MainActivity.kt

package com.example.screentracker

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import java.util.concurrent.TimeUnit
import androidx.work.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.screentracker.ui.theme.ScreenTrackerTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private lateinit var screenOnReceiver: BroadcastReceiver
    private val cachedLog = mutableStateOf(listOf<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the com.example.screentracker.ScreenLogService
        val serviceIntent = Intent(this, ScreenLogService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            ScreenTrackerTheme {
                MainScreen(
                    cachedLog = cachedLog
                )
            }
        }

        // Set up BroadcastReceiver to listen for ACTION_SCREEN_ON
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    refreshLogAndState(context) // Refresh the log when the screen is turned on
                }
            }
        }

        // Register the receiver for screen on action
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOnReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the BroadcastReceiver to avoid memory leaks
        unregisterReceiver(screenOnReceiver)
    }

    override fun onResume() {
        super.onResume()
        refreshLogAndState(this) // Refresh the log when the activity becomes visible
    }

    fun refreshLogAndState(context: Context?) {
        val prefs: SharedPreferences = context?.getSharedPreferences("ScreenLog", MODE_PRIVATE) ?: return
        val logString = prefs.getString("event_log", "")?.trim()
        val updatedLogString = logString?.lines()?.takeLast(1000)?.joinToString("\n") ?: ""

        // Save the updated log back to SharedPreferences
        prefs.edit().putString("event_log", updatedLogString).apply()

        // Update the cachedLog state to keep the UI in sync
        cachedLog.value = updatedLogString.lines().takeLast(1000)
    }
}

// Function to schedule the restart of the service using WorkManager
fun scheduleServiceRestart(context: Context) {
    val restartWorkRequest = OneTimeWorkRequestBuilder<ServiceRestartWorker>()
        .setInitialDelay(1, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueue(restartWorkRequest)
}

@Composable
fun MainScreen(cachedLog: State<List<String>>) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(cachedLog.value) {
        coroutineScope.launch {
            listState.animateScrollToItem(cachedLog.value.size - 1)
        }
    }

    val totalSleepDurationInSeconds = remember { mutableLongStateOf(0L) }

    // State variables to hold the selected start and end timestamps
    var startTimestamp by remember { mutableStateOf<Long?>(null) }
    var endTimestamp by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .border(2.dp, Color.Gray)
                .padding(8.dp)
                .weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth().fillMaxHeight().height(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(cachedLog.value) { logEntry ->
                    val timestamp = extractTimestamp(logEntry)
                    val isSelected = (timestamp == startTimestamp || timestamp == endTimestamp)

                    // Highlight the log entry if it's selected
                    Text(
                        text = logEntry,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (startTimestamp == null && endTimestamp == null) {
                                    // First click: Set start timestamp
                                    startTimestamp = timestamp
                                } else if (startTimestamp != null && endTimestamp == null) {
                                    // Second click: Set end timestamp, ensure it's after start
                                    if (timestamp != null && timestamp > startTimestamp!!) {
                                        endTimestamp = timestamp
                                    } else {
                                        // If clicked timestamp is before start, swap them
                                        val temp = startTimestamp
                                        startTimestamp = timestamp
                                        endTimestamp = temp
                                    }
                                    val totalDuration = calculateSleepDurationFromSelectedEntries(
                                        startTimestamp!!,
                                        endTimestamp!!
                                    )
                                    val screenOnDuration = calculateScreenOnDurationBetweenEntries(
                                        cachedLog.value,
                                        startTimestamp!!,
                                        endTimestamp!!
                                    )
                                    totalSleepDurationInSeconds.longValue =
                                        totalDuration - screenOnDuration
                                } else {
                                    // If both are set, reset both timestamps to start fresh
                                    startTimestamp = timestamp
                                    endTimestamp = null
                                }
                            }
                            .then(
                                if (isSelected) Modifier.background(Color.LightGray)
                                else Modifier
                            )
                            .padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val formattedSleepDuration = formatDurationInSecondsToHHMMSS(totalSleepDurationInSeconds.longValue)
        Text(text = "Total Sleep Duration: $formattedSleepDuration",
            color = Color.White)
    }
}

fun calculateSleepDurationFromSelectedEntries(startTimestamp: Long, endTimestamp: Long): Long {
    return if (startTimestamp < endTimestamp) {
        (endTimestamp - startTimestamp) / 1000 // Convert to seconds
    } else {
        0L // Return 0 if the start time is after the end time (invalid selection)
    }
}

fun calculateScreenOnDurationBetweenEntries(logEntries: List<String>, startTimestamp: Long, endTimestamp: Long): Long {
    var screenOnDuration = 0L
    var lastScreenOnTime: Long? = null
    var countingScreenOn = false

    for (logEntry in logEntries) {
        val timestamp = extractTimestamp(logEntry)
        if (timestamp != null && timestamp in startTimestamp..endTimestamp) {
            if (logEntry.contains("Screen turned on", ignoreCase = true)) {
                lastScreenOnTime = timestamp
                countingScreenOn = true
            } else if (logEntry.contains("Screen turned off", ignoreCase = true) && lastScreenOnTime != null) {
                // Automatically switch to the next 'screen on' entry if applicable
                if (countingScreenOn) {
                    screenOnDuration += (timestamp - lastScreenOnTime) / 1000 // Convert to seconds
                }
                lastScreenOnTime = null
                countingScreenOn = false
            }
        }
    }

    // If the range ends while the screen is still on, add the remaining time
    if (lastScreenOnTime != null && countingScreenOn) {
        screenOnDuration += (endTimestamp - lastScreenOnTime) / 1000 // Convert to seconds
    }

    return screenOnDuration
}

fun formatDurationInSecondsToHHMMSS(totalSeconds: Long): String {
    val usLocale: Locale = Locale("en", "US")

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return String.format(usLocale, "%02d:%02d:%02d", hours, minutes, seconds)
}

// Helper function to extract timestamp from log entry
fun extractTimestamp(logEntry: String): Long? {
    return try {
        // Using regex to extract the timestamp between square brackets
        val usLocale: Locale = Locale("en", "US")
        val regex = "\\[(.*?)]".toRegex()
        val matchResult = regex.find(logEntry)

        if (matchResult != null) {
            val timestampString = matchResult.groupValues[1].trim()

            // Ensure the timestamp matches the expected format before parsing
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", usLocale)
            dateFormat.parse(timestampString)?.time
        } else {
            null // No timestamp found in the expected format
        }
    } catch (e: Exception) {
        // Add some logging to understand the error (if needed)
        println("Error extracting timestamp: ${e.message}")
        null
    }
}