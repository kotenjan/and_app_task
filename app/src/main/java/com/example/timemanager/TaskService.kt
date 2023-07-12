package com.example.timemanager

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

import android.util.Log
import kotlin.coroutines.CoroutineContext

class TaskService : Service(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startUpdate()
        startWatch()
        return START_STICKY
    }

    private fun startUpdate() = launch {
        while (isActive) {
            taskDao.updateTaskTimes()
            delay(1000L)
        }
    }

    private fun startWatch() = launch {
        while (isActive) {
            delay(6400L)
            watch()
        }
    }

    private suspend fun watch() {
        if (taskDao.countRunningTasks() == 0) {
            kill()
        }
    }

    private fun kill() {
        cancel()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
