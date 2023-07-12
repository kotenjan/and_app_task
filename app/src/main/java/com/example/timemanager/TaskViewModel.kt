package com.example.timemanager

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    val tasksLiveData: MutableLiveData<List<Task>> = MutableLiveData()

    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }

    fun loadTasks(refresh: Boolean) {
        if (refresh) {
            viewModelScope.launch(Dispatchers.IO) {
                val tasks = taskDao.getTasks()
                withContext(Dispatchers.Main) {
                    tasksLiveData.value = tasks
                }
            }
        }
    }

    fun insertTask(task: Task, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.insert(task)
            loadTasks(refresh)
        }
    }

    fun updateTask(task: Task, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.update(task)
            loadTasks(refresh)
        }
    }

    fun removeTask(task: Task, refresh: Boolean, removeAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (removeAll) {
                taskDao.purge(task.id)
            } else {
                taskDao.delete(task)
            }
            loadTasks(refresh)
        }
    }

    fun insertAll(tasks: List<Task>, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            tasks.forEach {
                insertTask(it, false)
            }
            loadTasks(refresh)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.prune()
        }
    }

    fun updateRunningState(task: Task, isRunning: Boolean, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            task.isRunning = isRunning
            taskDao.updateRunningState(task.id, task.createdTime, isTemplate = false, isRunning)
            loadTasks(refresh)
        }
    }

    fun decrementTimeLeft(task: Task, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("TimeLeft", "${maxOf(0, task.timeLeft - 30).toInt()}, ${task.timeLeft - 30}")
            taskDao.decrementTimeLeft(task.id, task.createdTime, isTemplate = false, 30)
            loadTasks(refresh)
        }
    }

    fun incrementTimeLeft(task: Task, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.incrementTimeLeft(task.id, task.createdTime, isTemplate = false, 30)
            loadTasks(refresh)
        }
    }

    fun setDetail(task: Task, value: Boolean, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.isTemplate) {
                task.isTemplate = false
                taskDao.insert(task)
            } else {
                taskDao.setDetail(task.id, task.createdTime, isTemplate = false, value)
            }
            loadTasks(refresh)
        }
    }

    fun setTimeLeft(task: Task, value: Long, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.setTimeLeft(task.id, task.createdTime, isTemplate = false, value)
            loadTasks(refresh)
        }
    }
}
