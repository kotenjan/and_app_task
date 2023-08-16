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
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class TaskViewModel(private val application: Application) : AndroidViewModel(application) {

    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }
    private val updateMutex = Mutex()
    private val tasks: MutableMap<Task, Task> = mutableMapOf()
    private var stoppedRunning = false
    private val sound: TaskSoundSystem by lazy { TaskSoundSystem(application) }
    val tasksLiveData = MutableLiveData<List<Task>>()

    fun getTask(task: Task): Task {
        return tasks[task]!!
    }

    private fun addOldTasks(task: Task, newTasks: MutableList<Task>, newTasksSet: HashSet<Task>) {
        val targetDay = LocalDate.now()
        val threshold = minOf(ChronoUnit.DAYS.between(task.createdTime.toLocalDate(), targetDay), Variables.APP_HISTORY_SPAN)

        task.getOldTasks(targetDay, threshold).forEach { oldTask ->
            if (oldTask !in newTasksSet) {
                newTasks.add(oldTask)
            }
        }
    }

    fun getTasks(displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {

            val newTasks = taskDao.getTasks() as MutableList
            val newTasksSet = HashSet(newTasks)
            val currentDate = LocalDate.now()

            newTasks.filter {
                it.isTemplate && it.createdTime < currentDate.atStartOfDay()
            }.forEach {
                task -> addOldTasks(task, newTasks, newTasksSet)
            }

            val tasksToDelete = newTasks.filter {
                it.status == TaskStatus.FINISHED && it.createdTime < currentDate.minusDays(Variables.APP_HISTORY_SPAN).atStartOfDay()
            }

            taskDao.delete(tasksToDelete)
            addTasks(newTasks, displayDay)
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
            if (task.isTemplate && task.createdTime < LocalDate.now().atStartOfDay()) {
                val action = Variables.ACTION_ADD_MULTIPLE
                val newTasks: MutableList<Task> = mutableListOf()
                addOldTasks(task, newTasks, hashSetOf())
                update(action = action, newTasks = newTasks, displayDay = displayDay)
            }
            val action = Variables.ACTION_ADD
            update(action = action, currentTask = task, displayDay = displayDay)
        }
    }

    private fun addTasks(newTasks: List<Task>, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_ADD_MULTIPLE
            update(action = action, newTasks = newTasks, displayDay = displayDay)
        }
    }

    fun deleteTask(task: Task, displayDay: LocalDate, deleteAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            sound.notifyTaskFinished()
            val action = Variables.ACTION_REMOVE
            val value = (if (deleteAll) 1 else 0).toLong()
            update(action = action, currentTask = task, displayDay = displayDay, value = value)
        }
    }

    fun updateRunningTasks(displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_TIME_DECREASE
            update(action = action, displayDay = displayDay)
        }
    }

    fun setTaskDetail(task: Task, displayDay: LocalDate, detailVisible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_SET_DETAIL
            val value = (if (detailVisible) 1 else 0).toLong()
            update(action = action, currentTask = task, displayDay = displayDay, value = value)
        }
    }

    fun modifyTime(task: Task, value: Long, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_MODIFY_TIME
            update(action = action, currentTask = task, value = value, displayDay = displayDay)
        }
    }

    fun setTime(task: Task, value: Long, displayDay: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_SET_TIME
            update(action = action, currentTask = task, value = value, displayDay = displayDay)
        }
    }

    fun modifyRunningState(task: Task, displayDay: LocalDate, fromSeekBar: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = Variables.ACTION_SET_RUNNING_STATE
            update(action = action, currentTask = task, displayDay = displayDay, value = if (fromSeekBar) 1L else 0L)
        }
    }

    private fun dateAtTime(date: LocalDate, dateTime: LocalDateTime): LocalDateTime {
        return date.atTime(dateTime.toLocalTime())
    }

    private fun getOrCreate(task: Task): Task {
        if (!task.isTemplate) {
            return tasks[task]?.copy() ?: task.copy()
        }
        return task.copy(isTemplate = false)
    }

    private fun insertDatabaseTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.insert(task)
        }
    }

    private fun removeDatabaseTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.deleteAllWithId(task.id)
        }
    }

    private fun currentTaskTimeLeft(task: Task): Long {
        return abs(Duration.between(task.startTime, LocalDateTime.now()).seconds)
    }

    private suspend fun update(action: String, currentTask: Task? = null, newTasks: List<Task>? = null, value: Long = 0, displayDay: LocalDate) {
        updateMutex.withLock {

            when(action) {
                Variables.ACTION_TIME_DECREASE -> {
                    tasks.entries.forEach { (_, task) ->
                        if (task.isRunning && task.status == TaskStatus.REMAINING) {
                            val timeLeft = maxOf(task.timeLeftOnStart - currentTaskTimeLeft(task), 0)
                            lateinit var updatedTask: Task

                            if (timeLeft <= 0) {
                                updatedTask = task.copy(timeLeft = 0, status = TaskStatus.FINISHED)
                                insertDatabaseTask(updatedTask)
                            } else {
                                updatedTask = task.copy(timeLeft = timeLeft)
                            }

                            tasks[updatedTask] = updatedTask
                        }
                    }
                }
                Variables.ACTION_ADD -> {
                    currentTask!!.let {task ->
                        tasks[task] = task
                        insertDatabaseTask(task)
                    }
                }
                Variables.ACTION_ADD_MULTIPLE -> {
                    newTasks!!.let { tasksToAdd ->
                        tasks.putAll(tasksToAdd.associateWith { task -> task })
                    }
                }
                Variables.ACTION_REMOVE -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)

                        if (value == 0L) {
                            task.status = TaskStatus.FINISHED
                            tasks[task] = task

                            insertDatabaseTask(task)
                        } else {
                            tasks.keys.removeIf { key -> key.id == task.id }

                            removeDatabaseTask(task)
                        }

                        if (task.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_REMOVE, task = task, value = value)
                        }
                    }
                }
                Variables.ACTION_SET_TIME -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)

                        task.timeLeft = value
                        task.startTime = LocalDateTime.now().minusSeconds(1)
                        task.timeLeftOnStart = task.timeLeft
                        tasks[task] = task

                        insertDatabaseTask(task)

                        if (task.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_NOTIFICATION_SET_TIME, task = task)
                        }
                    }
                }
                Variables.ACTION_MODIFY_TIME -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)
                        val timeLeft = minOf(task.duration, maxOf(value + task.timeLeft, 0))

                        task.timeLeft = timeLeft
                        task.startTime = LocalDateTime.now().minusSeconds(1)
                        task.timeLeftOnStart = task.timeLeft
                        tasks[task] = task

                        insertDatabaseTask(task)

                        if (task.isRunning) {
                            sendIntentToNotificationService(Variables.ACTION_NOTIFICATION_SET_TIME, task = task)
                        }
                    }
                }
                Variables.ACTION_SET_RUNNING_STATE -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)

                        var newRunningState = task.status == TaskStatus.REMAINING && !task.isRunning

                        if (value == 1L) {
                            if (newRunningState) {
                                newRunningState = stoppedRunning
                            }
                            stoppedRunning = task.isRunning && !newRunningState
                        }

                        task.isRunning = newRunningState
                        task.startTime = LocalDateTime.now().minusSeconds(1)
                        task.timeLeftOnStart = task.timeLeft
                        tasks[task] = task

                        insertDatabaseTask(task)

                        if (task.timeLeft > 0) {
                            if (newRunningState) {
                                sendIntentToNotificationService(Variables.ACTION_ADD, task)
                            } else {
                                sendIntentToNotificationService(Variables.ACTION_REMOVE, task = task)
                            }
                        } else {
                            sound.notifyTaskFinished()
                        }
                    }
                }
                Variables.ACTION_SET_DETAIL -> {
                    currentTask!!.let {
                        val task = getOrCreate(it)
                        task.isDetailVisible = value == 1L
                        tasks[task] = task

                        insertDatabaseTask(task)
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

        var estimatedStartTime = maxOf(currentTime, today.atStartOfDay())

        val pause = Duration.ofMinutes(1)

        val (variableTasks, fixedTasks) = segregateTasksBasedOnFixedTime(tasks)

        val (variableIterator, fixedIterator) = getSortedIterators(variableTasks, fixedTasks, today)

        var nextElementVariable: Task? = getNextElement(variableIterator)
        var nextElementFixed: Task? = getNextElement(fixedIterator)

        val sortedTasks: MutableList<Task> = mutableListOf()

        while ((nextElementFixed != null) || (nextElementVariable != null)) {

            var result: Task? = null

            if (nextElementVariable == null && nextElementFixed != null){
                nextElementFixed.estimatedStartTime = maxOf(nextElementFixed.createdTime, currentTime, estimatedStartTime)
                result = nextElementFixed
                nextElementFixed = getNextElement(fixedIterator)
            }
            else if (nextElementVariable != null && (nextElementFixed == null || estimatedStartTime.plusSeconds(nextElementVariable.timeLeft).plus(pause) <= nextElementFixed.createdTime)){
                nextElementVariable.estimatedStartTime = estimatedStartTime
                result = nextElementVariable
                nextElementVariable = getNextElement(variableIterator)
            }
            else if (nextElementFixed != null){
                nextElementFixed.estimatedStartTime = maxOf(nextElementFixed.createdTime, currentTime, estimatedStartTime)
                result = nextElementFixed
                nextElementFixed = getNextElement(fixedIterator)
            }

            result?.let {
                sortedTasks.add(it)
                estimatedStartTime = maxOf(it.estimatedStartTime.plusSeconds(it.timeLeft).plus(pause), estimatedStartTime)
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
            val (tasksToday, tasksTomorrow) = sortedTasks.partition { it.estimatedStartTime.plusSeconds(it.timeLeft) < today.plusDays(1).atStartOfDay() }

            remainingTasks = tasksTomorrow + followingTasks

            tasksToReturn = tasksToday

            today = today.plusDays(1)

        } while (!today.isAfter(endDay))

        return tasksToReturn
    }
}
