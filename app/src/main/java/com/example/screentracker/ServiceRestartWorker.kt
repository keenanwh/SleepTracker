package com.example.screentracker

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

// Worker class to restart the service
class ServiceRestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val serviceIntent = Intent(applicationContext, ScreenLogService::class.java)
        applicationContext.startForegroundService(serviceIntent)
        return Result.success()
    }
}