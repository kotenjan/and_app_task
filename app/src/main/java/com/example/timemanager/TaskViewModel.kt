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
    private val tasks: MutableMap<Task, Task> = mutableMapOf()
    val tasksLiveData = MutableLiveData<List<Task>>()

    private suspend fun getTasks(): List<Task> {
        return withContext(Dispatchers.IO) {
            taskDao.getTasks()
        }
    }

    private fun sendIntentToNotificationService(actionName: String, task: Task? = null, value: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = application.applicationContext

            val intent = Intent(context, NotificationService::class.java).apply {
                action = actionName
                task?.let {
                    putExtra(Variables.TASK, it)
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

    fun modifyRunningState(task: Task, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_SET_RUNNING_STATE
            update(action, task, displayDay = displayDay)
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
                        //val key = TaskKey(task.id, date, false)
                        if (newTask !in tasks) {
                            update(Variables.ACTION_ADD, newTask, displayDay = displayDay)
                        }
                    }
            }
            update(Variables.ACTION_ADD, task, displayDay = displayDay)
        }
    }

    private fun getOrCreate(task: Task): Task {
        if (!task.isTemplate) {
            return tasks[task]?.copy() ?: task.copy()
        }
        return task.copy(isTemplate = false)
    }

    private suspend fun update(action: String, currentTask: Task? = null, value: Long = 0, displayDay: LocalDate) {
        updateMutex.withLock {

            when(action) {
                Variables.ACTION_TIME_DECREASE -> {
                    tasks.entries.forEach { entry ->
                        val task = entry.value
                        if (task.isRunning && task.status == TaskStatus.REMAINING) {
                            val timeLeft = maxOf(task.timeLeft - 1, 0)

                            val updatedTask = if (timeLeft <= 0) {
                                sendIntentToNotificationService(Variables.ACTION_REMOVE, task = entry.key)
                                task.copy(timeLeft = timeLeft, status = TaskStatus.FINISHED)
                            } else {
                                task.copy(timeLeft = timeLeft)
                            }

                            tasks[entry.key] = updatedTask
                        }
                    }
                }
                Variables.ACTION_ADD -> {
                    currentTask!!.let {
                        tasks[it] = it
                    }
                }
                Variables.ACTION_REMOVE -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)

                        if (value != 1L) {
                            task.status = TaskStatus.FINISHED
                            tasks[task] = task
                        } else {
                            tasks.keys.removeIf { key -> key.id == task.id }
                        }

                        if (task.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_REMOVE, task = task, value = value) //TODO: implement value in notification
                        }
                    }
                }
                Variables.ACTION_SET_TIME -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)

                        task.timeLeft = value
                        tasks[task] = task

                        if (task.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_NOTIFICATION_SET_TIME, task = task, value = value)
                        }
                    }
                }
                Variables.ACTION_MODIFY_TIME -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)
                        val timeLeft = minOf(it.duration, maxOf(value + task.timeLeft, 0))

                        task.timeLeft = timeLeft
                        tasks[task] = task

                        if (task.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_NOTIFICATION_SET_TIME, task = task, value = timeLeft)
                        }
                    }
                }
                Variables.ACTION_SET_RUNNING_STATE -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)
                        val isRunning = task.timeLeft > 0 && task.status == TaskStatus.REMAINING && !task.isRunning

                        task.isRunning = isRunning
                        tasks[task] = task

                        if (isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_ADD, task, value = task.timeLeft)
                        } else {
                            sendIntentToNotificationService(Variables.ACTION_REMOVE, task = task)
                        }
                    }
                }
                Variables.ACTION_SET_DETAIL -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)
                        task.isDetailVisible = !task.isDetailVisible
                        tasks[task] = task
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
