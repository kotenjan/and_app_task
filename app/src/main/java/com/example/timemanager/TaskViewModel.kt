package com.example.timemanager

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }
    private val updateMutex = Mutex()
    private val tasks: MutableList<Task> = mutableListOf()
    val tasksLiveData = MutableLiveData<List<Task>>()

    private suspend fun getTasks(): List<Task> {
        return withContext(Dispatchers.IO) {
            taskDao.getTasks()
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            update(Variables.ACTION_ADD, task)
            //taskDao.insert(task)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.prune()
        }
    }

    fun updateRunningTasks() {
        viewModelScope.launch(Dispatchers.IO) { update(Variables.ACTION_TIME_DECREASE) }
    }

    fun loadTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { update(Variables.ACTION_LOAD_TASK, task) }
    }

    fun modifyTime(task: Task, value: Long) {
        viewModelScope.launch(Dispatchers.IO) { update(Variables.ACTION_MODIFY_TIME, task, value) }
    }

    fun setTime(task: Task, value: Long) {
        viewModelScope.launch(Dispatchers.IO) { update(Variables.ACTION_SET_TIME, task, value) }
    }

    fun setRunningState(task: Task, value: Long) {
        viewModelScope.launch(Dispatchers.IO) { update(Variables.ACTION_SET_RUNNING_STATE, task, value) }
    }

    suspend fun loadTasks() {

        val databaseTasks: List<Task> = getTasks()
        val remainingTasks = databaseTasks.filter { !it.isTemplate }.toSet()

        getTasks().forEach {
            if (it.isTemplate && it !in remainingTasks && it.createdTime.toLocalDate() < LocalDate.now() && it.createdTime.toLocalDate() > LocalDate.now().minusDays(8)) {
                update(Variables.ACTION_ADD, it.copy(isTemplate = false))
            }
            update(Variables.ACTION_ADD, it)
        }
    }

    private suspend fun update(action: String, currentTask: Task? = null, value: Long = 0) {
        updateMutex.withLock {

            val updatedTasks: MutableMap<TaskKey, Task> = (tasksLiveData.value?: listOf()).map { it.copy() }.associateBy { TaskKey(it.id, it.createdTime, it.isTemplate) }.toMutableMap()

            when(action) {
                Variables.ACTION_TIME_DECREASE -> {
                    updatedTasks.forEach { (_, value) ->
                        if (value.isRunning) { value.timeLeft = maxOf(value.timeLeft - 1, 0) }
                    }
                }
                Variables.ACTION_ADD -> {
                    currentTask!!.let {
                        updatedTasks[TaskKey(it.id, it.createdTime, it.isTemplate)] = it
                    }
                }
                Variables.ACTION_REMOVE -> {
                    currentTask!!.let {
                        updatedTasks.remove(TaskKey(it.id, it.createdTime, isTemplate = false))
                        updatedTasks.remove(TaskKey(it.id, it.createdTime, isTemplate = true))
                    }
                }
                Variables.ACTION_SET_TIME -> {
                    currentTask!!.let {
                        val foundTask = updatedTasks[TaskKey(it.id, it.createdTime, isTemplate = false)]
                        foundTask?.timeLeft = value
                    }
                }
                Variables.ACTION_MODIFY_TIME -> {
                    currentTask!!.let {
                        val foundTask = updatedTasks[TaskKey(it.id, it.createdTime, isTemplate = false)]
                        foundTask?.let { task -> task.timeLeft = minOf(task.duration, maxOf(value + task.timeLeft, 0)) }
                    }
                }
                Variables.ACTION_SET_RUNNING_STATE -> {
                    currentTask!!.let {
                        it.isRunning = value == 1L
                        updatedTasks[TaskKey(it.id, it.createdTime, it.isTemplate)]!!.isRunning = value == 1L
                    }
                }
                Variables.ACTION_LOAD_TASK -> {
                    currentTask!!.let {
                        if (it.isTemplate) {
                            updatedTasks[TaskKey(it.id, it.createdTime, false)] = it.copy(isTemplate = false)
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported action: $action")
            }

            val sortedTasks = getSortedTasksForDay(updatedTasks.values.toSet(), LocalDate.now(), LocalDate.now())

            withContext(Dispatchers.Main){
                tasksLiveData.value = sortedTasks
            }
        }
    }

    //----------------------------------------------- Task sorting -----------------------------------------------//

    private fun splitTasks(tasks: List<Task>, today: LocalDate): Pair<List<Task>, List<Task>> {
        return tasks.partition { task -> !task.createdTime.toLocalDate().isAfter(today) }
    }

    private fun getTasks(tasks: Set<Task>, today: LocalDate): Triple<List<Task>, List<Task>, List<Task>> {
        val (templateTasks, nonTemplateTasks) = tasks.partition { it.isTemplate }
        val (finishedTasks, remainingTasks) = nonTemplateTasks.partition { it.status == TaskStatus.FINISHED }
        val addedTasks = generateDayTasks(templateTasks, finishedTasks.toSet(), today).subtract(remainingTasks.toSet()).toList()
        return Triple(remainingTasks + addedTasks, templateTasks, finishedTasks)
    }

    private fun dateAtTime(date: LocalDate, dateTime: LocalDateTime): LocalDateTime {
        return date.atTime(dateTime.toLocalTime())
    }

    private fun generateDayTasks(
        repeatingTasks: List<Task>,
        finishedTasks: Set<Task>,
        today: LocalDate
    ): List<Task> {
        return repeatingTasks.filter { it.isOnToday(today) && !finishedTasks.contains(it) }
            .map { it.copy(createdTime = dateAtTime(today, it.createdTime), status = TaskStatus.REMAINING) }
    }

    private fun sort(tasks: List<Task>, today: LocalDate): List<Task> {
        var startTime = maxOf(LocalDateTime.now(), today.atStartOfDay())

        val pause = Duration.ofMinutes(15)

        val (variableTasks, fixedTasks) = segregateTasksBasedOnFixedTime(tasks)

        val (variableIterator, fixedIterator) = getSortedIterators(variableTasks, fixedTasks, today)

        var nextElementVariable: Task? = getNextElement(variableIterator)
        var nextElementFixed: Task? = getNextElement(fixedIterator)

        val sortedTasks: MutableList<Task> = mutableListOf()

        while ((nextElementFixed != null) || (nextElementVariable != null)) {

            var result: Task? = null

            if (nextElementVariable == null && nextElementFixed != null){
                nextElementFixed.startTime = nextElementFixed.createdTime
                result = nextElementFixed
                nextElementFixed = getNextElement(fixedIterator)
            }
            else if (nextElementVariable != null && (nextElementFixed == null || startTime.plusSeconds(nextElementVariable.timeLeft).plus(pause) <= nextElementFixed.createdTime)){
                nextElementVariable.startTime = startTime
                result = nextElementVariable
                nextElementVariable = getNextElement(variableIterator)
            }
            else if (nextElementFixed != null){
                nextElementFixed.startTime = nextElementFixed.createdTime
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
        return if (iterator.hasNext()) iterator.next() else null
    }

    //----------------------------------------------- Controller function for task sorting -----------------------------------------------//

    private fun getSortedTasksForDay(tasks: Set<Task>, startDay: LocalDate, endDay: LocalDate): List<Task> {

        var today = startDay
        var tasksToReturn: List<Task>
        var (remainingTasks, templateTasks, finishedTasks) = getTasks(tasks, today)

        do {
            val (currentTasks, followingTasks) = splitTasks(remainingTasks, today)
            val addedTasks = generateDayTasks(templateTasks, finishedTasks.toSet(), today).subtract(currentTasks.toSet())
            val sortedTasks = sort(currentTasks + addedTasks, today)
            val (tasksToday, tasksTomorrow) = sortedTasks.partition { it.startTime.plusSeconds(it.timeLeft) < today.plusDays(1).atStartOfDay() }
            remainingTasks = tasksTomorrow + followingTasks
            tasksToReturn = tasksToday
            today = today.plusDays(1)
        } while (!today.isAfter(endDay))

        return tasksToReturn
    }
}
