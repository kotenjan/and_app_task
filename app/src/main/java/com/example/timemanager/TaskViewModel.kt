package com.example.timemanager

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class TaskViewModel(private val application: Application) : AndroidViewModel(application) {

    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }
    private val updateMutex = Mutex()
    private val tasks: MutableMap<TaskKey, Task> = mutableMapOf()
    val tasksLiveData = MutableLiveData<List<Task>>()

    private suspend fun getTasks(): List<Task> {
        return withContext(Dispatchers.IO) {
            taskDao.getTasks()
        }
    }

    private fun sendIntentToNotificationService(actionName: String, task: Task? = null, taskKey: TaskKey? = null, value: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = application.applicationContext

            val intent = Intent(context, NotificationService::class.java).apply {
                action = actionName
                task?.let {
                    putExtra(Variables.TASK, it)
                }
                taskKey?.let {
                    putExtra(Variables.KEY, it)
                }
                value?.let {
                    putExtra(Variables.VALUE, it)
                }
            }
            context.startService(intent)
        }
    }

    fun addTask(task: Task, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_ADD
            update(action, task, displayDay = displayDay)
        }
    }

    fun deleteTask(task: Task, displayDay: LocalDate, deleteAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_REMOVE
            val value = (if (deleteAll) 1 else 0).toLong()
            update(action, task, displayDay = displayDay, value = value)
        }
    }

    fun updateRunningTasks(displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_TIME_DECREASE
            update(action, displayDay = displayDay)
        }
    }

    fun setTaskDetail(task: Task, displayDay: LocalDate, detailVisible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_SET_DETAIL
            val value = (if (detailVisible) 1 else 0).toLong()
            update(action, task, displayDay = displayDay, value = value)
        }
    }

    fun modifyTime(task: Task, value: Long, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_MODIFY_TIME
            update(action, task, value, displayDay = displayDay)
        }
    }

    fun setTime(task: Task, value: Long, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_SET_TIME
            update(action, task, value, displayDay = displayDay)
        }
    }

    fun setRunningState(task: Task, value: Long, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_SET_RUNNING_STATE
            update(action, task, value, displayDay = displayDay)
        }
    }

    private fun dateAtTime(date: LocalDate, dateTime: LocalDateTime): LocalDateTime {
        return date.atTime(dateTime.toLocalTime())
    }

    suspend fun loadTasks(displayDay: LocalDate) {

        val databaseTasks: List<Task> = getTasks()
        val today = LocalDate.now()

        databaseTasks.forEach { task ->
            if (task.isTemplate) {
                val intervalDays = Duration.ofDays(task.intervalDays.toLong())
                generateSequence(task.createdTime) { it + intervalDays }
                    .takeWhile { it.toLocalDate() < today }
                    .forEach { createdTime ->
                        val date = dateAtTime(createdTime.toLocalDate(), task.createdTime)
                        val newTask = task.copy(createdTime = date, isTemplate = false)
                        val key = TaskKey(task.id, date, false)
                        if (key !in tasks) {
                            update(Variables.ACTION_ADD, newTask, displayDay = displayDay)
                        }
                    }
            }
            update(Variables.ACTION_ADD, task, displayDay = displayDay)
        }
    }

    private suspend fun update(action: String, currentTask: Task? = null, value: Long = 0, displayDay: LocalDate) {
        updateMutex.withLock {

            when(action) {
                Variables.ACTION_TIME_DECREASE -> {
                    tasks.forEach { (key, value) ->
                        if (value.isRunning && value.status == TaskStatus.REMAINING) {
                            val timeLeft = maxOf(value.timeLeft - 1, 0)
                            tasks[key] = value.copy(timeLeft = timeLeft)
                            if (timeLeft <= 0) {
                                tasks[key]!!.status = TaskStatus.FINISHED
                                sendIntentToNotificationService(Variables.ACTION_REMOVE, taskKey = key)
                            }
                        }
                    }
                }
                Variables.ACTION_ADD -> {
                    currentTask!!.let {
                        val taskKey = TaskKey(it.id, it.createdTime, it.isTemplate)
                        tasks[taskKey] = it
                    }
                }
                Variables.ACTION_REMOVE -> {
                    currentTask!!.let {
                        val removeAll = value == 1L
                        val taskKey = TaskKey(it.id, it.createdTime, isTemplate = false)
                        if (!removeAll) {
                            tasks[taskKey] = it.copy(status = TaskStatus.FINISHED, isTemplate = false)
                        } else {
                            tasks.keys.retainAll { key -> key.id != it.id }
                        }
                        if (currentTask.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_REMOVE, taskKey = taskKey)
                        }
                    }
                }
                Variables.ACTION_SET_TIME -> {
                    currentTask!!.let {
                        val taskKey = TaskKey(it.id, it.createdTime, isTemplate = false)
                        tasks[taskKey] = it.copy(timeLeft = value, isTemplate = false)
                        if (currentTask.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_NOTIFICATION_SET_TIME, taskKey = taskKey, value = value)
                        }
                    }
                }
                Variables.ACTION_MODIFY_TIME -> {
                    currentTask!!.let {
                        val taskKey = TaskKey(it.id, it.createdTime, isTemplate = false)
                        val timeLeft = minOf(it.duration, maxOf(value + (tasks[taskKey]?.timeLeft ?: it.timeLeft), 0))
                        tasks[taskKey] = it.copy(timeLeft = timeLeft, isTemplate = false)
                        if (currentTask.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_NOTIFICATION_SET_TIME, taskKey = taskKey, value = timeLeft)
                        }
                    }
                }
                Variables.ACTION_SET_RUNNING_STATE -> {
                    currentTask!!.let {
                        val isRunning = value == 1L
                        val taskKey = TaskKey(it.id, it.createdTime, isTemplate = false)
                        tasks[taskKey]?.let {task ->
                            task.isRunning = isRunning
                        } ?: run {
                            tasks[taskKey] = it.copy(isRunning = isRunning)
                        }
                        if (isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_ADD, it, value = tasks[taskKey]!!.timeLeft)
                        } else {
                            sendIntentToNotificationService(Variables.ACTION_REMOVE, taskKey = taskKey)
                        }
                    }
                }
                Variables.ACTION_SET_DETAIL -> {
                    currentTask!!.let {
                        val isDetailVisible = value == 1L
                        val taskKey = TaskKey(it.id, it.createdTime, isTemplate = false)
                        tasks[taskKey]?.let {task ->
                            task.isDetailVisible = isDetailVisible
                        } ?: run {
                            tasks[taskKey] = it.copy(isDetailVisible = isDetailVisible, isTemplate = false)
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported action: $action")
            }

            val sortedTasks = getSortedTasksForDay(tasks.values.toSet(), LocalDate.now(), displayDay)

            withContext(Dispatchers.Main){
                tasksLiveData.value = sortedTasks
            }
        }
    }

    //----------------------------------------------- Task sorting -----------------------------------------------//

    private fun splitTasks(tasks: List<Task>, today: LocalDate): Pair<List<Task>, List<Task>> {
        return tasks.partition { task -> !task.createdTime.toLocalDate().isAfter(today) }
    }

    private fun generateDayTasks(
        repeatingTasks: List<Task>,
        today: LocalDate
    ): List<Task> {
        return repeatingTasks.filter { it.isOnToday(today) }.map { it.copy(createdTime = dateAtTime(today, it.createdTime), status = TaskStatus.REMAINING, isTemplate = false) }
    }

    private fun sort(tasks: List<Task>, today: LocalDate): List<Task> {
        val currentTime = LocalDateTime.now()

        var startTime = maxOf(currentTime, today.atStartOfDay())

        val pause = Duration.ofMinutes(15)

        val (variableTasks, fixedTasks) = segregateTasksBasedOnFixedTime(tasks)

        val (variableIterator, fixedIterator) = getSortedIterators(variableTasks, fixedTasks, today)

        var nextElementVariable: Task? = getNextElement(variableIterator)
        var nextElementFixed: Task? = getNextElement(fixedIterator)

        val sortedTasks: MutableList<Task> = mutableListOf()

        while ((nextElementFixed != null) || (nextElementVariable != null)) {

            var result: Task? = null

            if (nextElementVariable == null && nextElementFixed != null){
                nextElementFixed.startTime = maxOf(nextElementFixed.createdTime, currentTime, startTime)
                result = nextElementFixed
                nextElementFixed = getNextElement(fixedIterator)
            }
            else if (nextElementVariable != null && (nextElementFixed == null || startTime.plusSeconds(nextElementVariable.timeLeft).plus(pause) <= nextElementFixed.createdTime)){
                nextElementVariable.startTime = startTime
                result = nextElementVariable
                nextElementVariable = getNextElement(variableIterator)
            }
            else if (nextElementFixed != null){
                nextElementFixed.startTime = maxOf(nextElementFixed.createdTime, currentTime, startTime)
                result = nextElementFixed
                nextElementFixed = getNextElement(fixedIterator)
            }

            result?.let {
                sortedTasks.add(it)
                startTime = maxOf(it.startTime.plusSeconds(it.timeLeft).plus(pause), startTime)
            }
        }
        return sortedTasks
    }

    private fun segregateTasksBasedOnFixedTime(tasks: List<Task>): Pair<List<Task>, List<Task>> {

        val variableTasks = tasks.filter { !it.fixedTime }
        val fixedTasks = tasks.filter { it.fixedTime }

        return Pair(variableTasks, fixedTasks)
    }

    private fun getSortedIterators(variableTasks: List<Task>, fixedTasks: List<Task>, today: LocalDate): Pair<Iterator<Task>, Iterator<Task>> {
        val variableIterator = variableTasks.sortedWith(
            compareByDescending<Task> {
                it.priority + Duration.between(it.createdTime.toLocalDate().atStartOfDay(), today.atStartOfDay()).toDays()
            }.thenBy { it.createdTime }.thenBy { it.id }
        ).iterator()
        val fixedIterator = fixedTasks.sortedWith(compareBy { it.createdTime }).iterator()
        return Pair(variableIterator, fixedIterator)
    }

    private fun getNextElement(iterator: Iterator<Task>): Task? {
        return if (iterator.hasNext()) iterator.next().copy() else null
    }

    //----------------------------------------------- Controller function for task sorting -----------------------------------------------//

    private fun getSortedTasksForDay(tasks: Set<Task>, startDay: LocalDate, endDay: LocalDate): List<Task> {

        var today = startDay
        var tasksToReturn: List<Task>
        val (templateTasks, nonTemplateTasks) = tasks.partition { it.isTemplate }
        var remainingTasks: List<Task> = nonTemplateTasks

        do {

            remainingTasks = remainingTasks.filter { it.status == TaskStatus.REMAINING }

            val addedTasks = generateDayTasks(templateTasks, today).subtract(nonTemplateTasks.toSet()).toList()
            val (currentTasks, followingTasks) = splitTasks(remainingTasks + addedTasks, today)
            val sortedTasks = sort(currentTasks, today)
            val (tasksToday, tasksTomorrow) = sortedTasks.partition { it.startTime.plusSeconds(it.timeLeft) < today.plusDays(1).atStartOfDay() }

            remainingTasks = tasksTomorrow + followingTasks

            tasksToReturn = tasksToday

            today = today.plusDays(1)

        } while (!today.isAfter(endDay))

        return tasksToReturn
    }
}
