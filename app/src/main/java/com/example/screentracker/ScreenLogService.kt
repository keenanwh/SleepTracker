package com.example.screentracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenLogService : Service() {

    private lateinit var screenLogReceiver: ScreenLogReceiver

    override fun onCreate() {
        super.onCreate()

        Log.d("com.example.screentracker.ScreenLogService", "Service onCreate called")

        // Create a notification channel for the foreground service
        val channel = NotificationChannel(
            "ScreenLogServiceChannel",
            "Screen Log Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        if (manager == null) {
            Log.e("com.example.screentracker.ScreenLogService", "NotificationManager is null, cannot create notification channel")
            stopSelf()
            return
        }
        manager.createNotificationChannel(channel)

        // Create a notification for the foreground service
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "ScreenLogServiceChannel")
            .setContentTitle("Screen Tracker Service")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentText("Tracking screen usage logs...")
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentIntent(pendingIntent)
            .build()

        // Start the service in the foreground
        try {
            startForeground(1, notification)
            Log.d("com.example.screentracker.ScreenLogService", "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e("com.example.screentracker.ScreenLogService", "Failed to start foreground service: ${e.message}")
            stopSelf()
            return
        }

        // Register the BroadcastReceiver
        screenLogReceiver = ScreenLogReceiver()
        val intentFilter = IntentFilter().apply {
            addAction("com.example.screentracker.LOG_EVENT")
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }

        try {
            registerReceiver(screenLogReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
            Log.d("com.example.screentracker.ScreenLogService", "BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Log.e("com.example.screentracker.ScreenLogService", "Failed to register BroadcastReceiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenLogReceiver)
            Log.d("com.example.screentracker.ScreenLogService", "BroadcastReceiver unregistered successfully")
        } catch (e: Exception) {
            Log.e("com.example.screentracker.ScreenLogService", "Failed to unregister BroadcastReceiver: ${e.message}")
        }

        // Schedule a restart using WorkManager
        scheduleServiceRestart(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("com.example.screentracker.ScreenLogService", "Service onStartCommand called with intent: $intent")
        return START_STICKY
    }
}