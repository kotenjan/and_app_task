package com.example.timemanager

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class NotificationViewModel(application: Application): CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    var position: Int = 0
    var count: Int = 0

    private var tasks: List<Task> = listOf()
    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }

    fun getTask(): Task? {
        return tasks.getOrNull(position)
    }

    fun updateTasks() {
        launch {
            tasks = taskDao.getTasks().sortedWith(compareBy({ it.id }, { it.createdTime }, { it.isTemplate }))
            count = tasks.size
        }
    }

    fun forward() {
        launch {
            getTask()?.let { taskDao.incrementTimeLeft(it.id, it.createdTime, isTemplate = false, -30) }
        }
    }

    fun back() {
        launch {
            getTask()?.let { taskDao.incrementTimeLeft(it.id, it.createdTime, isTemplate = false, 30) }
        }
    }

    fun updateRunningState() {
        launch {
            getTask()?.let {

            }
        }
    }

    fun previous() {
        launch {
            position = (count + position - 1) % count
        }
    }

    fun next() {
        launch {
            position = (position + 1) % count
        }
    }

    //suspend fun updateTaskTimes() {
    //    withContext(Dispatchers.IO) {
    //        taskDao.updateTaskTimes()
    //    }
    //}

    suspend fun tasksRunning(): Boolean {
        return withContext(Dispatchers.IO) {
            taskDao.countRunningTasks() > 0
        }
    }

}