package com.example.screentracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenLogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs: SharedPreferences = context.getSharedPreferences("ScreenLog", Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val log = when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("com.example.screentracker.ScreenLogReceiver", "Screen turned off event received")
                "[$timestamp] Screen turned off."
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d("com.example.screentracker.ScreenLogReceiver", "Screen turned on event received")
                "[$timestamp] Screen turned on."
            }
            "com.example.screentracker.LOG_EVENT" -> {
                val logMessage = intent.getStringExtra("log_message") ?: ""
                Log.d("com.example.screentracker.ScreenLogReceiver", "Custom event received: $logMessage")
                "[$timestamp] $logMessage"
            }
            else -> {
                Log.d("com.example.screentracker.ScreenLogReceiver", "Unknown event received")
                "[$timestamp] Unknown event."
            }
        }

        val currentLog = prefs.getString("event_log", "")
        // Write asynchronously to prevent blocking
        CoroutineScope(Dispatchers.IO).launch {
            prefs.edit().putString("event_log", "$currentLog \n $log").commit()
        }
    }
}