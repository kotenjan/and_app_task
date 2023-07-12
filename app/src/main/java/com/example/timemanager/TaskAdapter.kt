package com.example.timemanager

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TaskAdapter(
    private val context: Context,
    private val taskViewModel: TaskViewModel,
    private val displayDay: LocalDate
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>(), CoroutineScope {

    private val tag = "TaskAdapter"
    private val job = Job()
    override val coroutineContext = Dispatchers.IO + job
    private var tasks: List<Task> = listOf()

    private fun logTask(it: Task) {
        Log.d(tag, "    [ ${it.id}, ${it.text}, duration: ${it.duration}, time left: ${it.timeLeft}, created time: ${it.createdTime}, start time: ${it.startTime}, priority: ${it.priority}, fixed: ${it.fixedTime}, template: ${it.isTemplate}, running: ${it.isRunning}, status: ${it.status} ]")
    }

    private fun logTaskLists(message: String, tasksList: List<List<Task>>) {
        Log.d(tag, "$message:")
        tasksList.forEachIndexed { index, tasks ->
            Log.d(tag, "$index:")
            tasks.forEach {
                logTask(it)
            }
        }
        Log.d(tag, "-------------------------------------------------------------")
    }

    init {
        Log.d(tag, "Generating view of $displayDay")
        start()
    }

    private fun start() {
        launch {
            while (isActive) {
                taskViewModel.loadTasks(true)
                delay(1000)
            }
        }
    }

    private fun startServices() {
        Log.d(tag, "Starting countdown service")
        context.startService(Intent(context, NotificationService::class.java))
    }

    @Synchronized
    private fun reassignTasks(updatedTasks: List<Task>) {
        tasks = updatedTasks
    }

    fun update(updatedTasks: List<Task>) {

        val sortedTasks = getSortedTasksForDay(updatedTasks, LocalDate.now(), displayDay)

        val diffCallback = TaskDiffCallback(tasks, sortedTasks)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        reassignTasks(sortedTasks)

        diffResult.dispatchUpdatesTo(this)
    }

    private inner class TaskDiffCallback(
        private val oldTasks: List<Task>,
        private val newTasks: List<Task>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldTasks.size
        override fun getNewListSize(): Int = newTasks.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldTasks[oldItemPosition] == newTasks[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldTask = oldTasks[oldItemPosition]
            val newTask = newTasks[newItemPosition]
            return oldTask.timeLeft == newTask.timeLeft && oldTask.startTime == newTask.startTime
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val newTask = newTasks[newItemPosition]
            return if (oldTasks[oldItemPosition] == newTask) newTask else null
        }
    }

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val detail: LinearLayout = view.findViewById(R.id.detail)
        private val taskLayout: LinearLayout = view.findViewById(R.id.foreground)
        private val taskProgress: SeekBar = view.findViewById(R.id.progress_bar)
        private val taskTimeLeft: TextView = view.findViewById(R.id.task_time_left)
        private val backButton: ImageButton = view.findViewById(R.id.back)
        private val playButton: ImageButton = view.findViewById(R.id.play)
        private val removeButton: ImageButton = view.findViewById(R.id.remove)
        private val forwardButton: ImageButton = view.findViewById(R.id.forward)
        private val taskTitle = view.findViewById<TextView>(R.id.title)
        private val taskTimeText = view.findViewById<TextView>(R.id.time_text)
        private val formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val foreground: LinearLayout = view.findViewById(R.id.foreground)
        private val background: LinearLayout = view.findViewById(R.id.background)
        private val control: LinearLayout = view.findViewById(R.id.control)
        private val picker: ColorPicker = ColorPicker()

        private fun expand(view: View) {

            view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val height = view.measuredHeight

            view.layoutParams.height = 0
            view.visibility = View.VISIBLE

            val animation = object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                    view.layoutParams.height = if (interpolatedTime == 1f) {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    } else {
                        (height * interpolatedTime).toInt()
                    }
                    view.requestLayout()
                }
            }

            animation.duration = (height / view.context.resources.displayMetrics.density).toLong()
            view.startAnimation(animation)

            view.startAnimation(animation)
        }

        private fun collapse(view: View) {
            val actualHeight = view.measuredHeight

            val animation = object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                    if (interpolatedTime == 1f) {
                        view.visibility = View.GONE
                    } else {
                        view.layoutParams.height = actualHeight - (actualHeight * interpolatedTime).toInt()
                        view.requestLayout()
                    }
                }
            }

            animation.duration = (actualHeight / view.context.resources.displayMetrics.density).toLong()
            view.startAnimation(animation)
        }

        private fun setDetail(task: Task) {
            if (task.isDetailVisible) {
                expand(detail)
            } else {
                collapse(detail)
            }
        }

        private fun changeBackgroundColor(color: String, layout: LinearLayout, black: Int, drawable: Int) {
            val shapeDrawable = ContextCompat.getDrawable(context, drawable) as GradientDrawable
            shapeDrawable.setColor(Color.parseColor(picker.darkenColor(picker.lightenColor(color, Variables.WHITE_PERCENTAGE), black)))
            layout.background = shapeDrawable
        }

        private fun changeControlColor(color: String, layout: LinearLayout, drawable: Int) {
            val layerDrawable = ContextCompat.getDrawable(context, drawable) as LayerDrawable

            val shapeDrawable = layerDrawable.getDrawable(1) as GradientDrawable
            shapeDrawable.setColor(Color.parseColor(picker.darkenColor(picker.lightenColor(color, Variables.WHITE_PERCENTAGE), Variables.BLACK_PERCENTAGE_CONTROL)))
            layerDrawable.setDrawableByLayerId(layerDrawable.getId(1), shapeDrawable)

            layout.background = layerDrawable
        }

        fun bind(task: Task) {
            taskTitle.text = task.text

            changeBackgroundColor(task.color, foreground, Variables.BLACK_PERCENTAGE_FRONT, R.drawable.task)
            changeBackgroundColor(task.color, background, Variables.BLACK_PERCENTAGE_BACK, R.drawable.task_shadow)
            changeControlColor(task.color, control, R.drawable.task_control)

            playButton.setImageResource(if (task.isRunning) R.drawable.ic_pause else R.drawable.ic_play)

            updateTask(task)

            detail.visibility = if (task.isDetailVisible) View.VISIBLE else View.GONE

            taskProgress.max = task.duration.toInt()
            taskProgress.progress = task.duration.minus(task.timeLeft).toInt()

            taskLayout.setOnClickListener {
                task.isDetailVisible = !task.isDetailVisible
                setDetail(task)
                taskViewModel.setDetail(task, value = task.isDetailVisible, refresh = false)
            }

            backButton.setOnClickListener {
                taskViewModel.incrementTimeLeft(task, refresh = true)
            }

            forwardButton.setOnClickListener {
                taskViewModel.decrementTimeLeft(task, refresh = true)
            }

            playButton.setOnClickListener {
                if (task.isRunning) {
                    stopTask(task, changeButton = true)
                } else {
                    startTask(task, changeButton = true)
                }
            }

            removeButton.setOnClickListener{

            }

            taskProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                var running = true

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    taskViewModel.setTimeLeft(task, task.duration - progress, refresh = true)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    running = task.isRunning
                    stopTask(task, changeButton = false)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    if (running) {
                        startTask(task, changeButton = false)
                    }
                }
            })

            updateTask(task)

            Log.d("$tag-update", "Task bound")
            logTask(task)
        }

        private fun startTask(task: Task, changeButton: Boolean){
            if (!task.isRunning && task.timeLeft > 0){
                taskViewModel.updateRunningState(task, isRunning = true, refresh = true)
                startServices()
                if (changeButton) {
                    playButton.setImageResource(R.drawable.ic_pause)
                }
            } else {
                stopTask(task, changeButton)
            }
        }

        private fun stopTask(task: Task, changeButton: Boolean){
            taskViewModel.updateRunningState(task, isRunning = false, refresh = false)
            if (changeButton) {
                playButton.setImageResource(R.drawable.ic_play)
            }
        }

        fun updateTask(task: Task) {

            val totalSeconds = task.timeLeft
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            taskTimeText.text = "${task.startTime.format(formatterTime)} - ${task.startTime.plusSeconds(task.timeLeft).format(formatterTime)}"

            taskTimeLeft.text = if (hours > 0) {
                String.format("%02d:%02d", hours, minutes)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
            println(String.format("%02d:%02d", minutes, seconds))
            taskProgress.progress = task.duration.minus(task.timeLeft).toInt()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.task_item, parent, false)
        return TaskViewHolder(view)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        job.cancel()
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            val task = payloads[0] as Task
            holder.updateTask(task)
        } else {
            holder.bind(tasks[position])
        }
    }

    override fun getItemCount(): Int {
        Log.d("$tag-update", "Item count: ${tasks.size}")
        return tasks.size
    }

    //----------------------------------------------- Task sorting -----------------------------------------------//

    private fun splitTasks(tasks: List<Task>, today: LocalDate): Pair<List<Task>, List<Task>> {
        return tasks.partition { task -> !task.createdTime.toLocalDate().isAfter(today) }
    }

    private fun getTasks(tasks: List<Task>, today: LocalDate): Triple<List<Task>, List<Task>, List<Task>> {
        logTaskLists("getTasks called with tasks and date $today", listOf(tasks))

        val (templateTasks, nonTemplateTasks) = tasks.partition { it.isTemplate }
        logTaskLists("getTasks partitioned tasks into templateTasks and nonTemplateTasks", listOf(templateTasks, nonTemplateTasks))

        val (finishedTasks, remainingTasks) = nonTemplateTasks.partition { it.status == TaskStatus.FINISHED }
        logTaskLists("getTasks partitioned nonTemplateTasks into finishedTasks and remainingTasks", listOf(finishedTasks, remainingTasks))

        val addedTasks = generateDayTasks(templateTasks, finishedTasks.toSet(), today).subtract(remainingTasks.toSet()).toList()
        logTaskLists("getTasks generated addedTasks", listOf(addedTasks.toList()))

        taskViewModel.insertAll(addedTasks.onEach { it.isTemplate = false }, false)
        logTaskLists("getTasks resulted in remaining tasks, template tasks, and finished tasks", listOf(remainingTasks + addedTasks, templateTasks, finishedTasks))

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
            .map { it.task(newCreatedTime = dateAtTime(today, it.createdTime), newStatus = TaskStatus.REMAINING) }
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

    private fun getSortedTasksForDay(tasks: List<Task>, startDay: LocalDate, endDay: LocalDate): List<Task> {
        logTaskLists("getSortedTasksForDay called with tasks and dates $startDay to $endDay", listOf(tasks))

        var today = startDay
        var tasksToReturn: List<Task>
        var (remainingTasks, templateTasks, finishedTasks) = getTasks(tasks, today)
        logTaskLists("getSortedTasksForDay received remainingTasks, templateTasks, and finishedTasks from getTasks", listOf(remainingTasks, templateTasks, finishedTasks))

        do {
            val (currentTasks, followingTasks) = splitTasks(remainingTasks, today)
            logTaskLists("getSortedTasksForDay split remainingTasks into currentTasks and followingTasks", listOf(currentTasks, followingTasks))

            val addedTasks = generateDayTasks(templateTasks, finishedTasks.toSet(), today).subtract(currentTasks.toSet())
            logTaskLists("getSortedTasksForDay generated addedTasks", listOf(addedTasks.toList()))

            val sortedTasks = sort(currentTasks + addedTasks, today)
            logTaskLists("getSortedTasksForDay sorted currentTasks and addedTasks into sortedTasks", listOf(sortedTasks))

            val (tasksToday, tasksTomorrow) = sortedTasks.partition { it.startTime.plusSeconds(it.timeLeft) < today.plusDays(1).atStartOfDay() }
            logTaskLists("getSortedTasksForDay partitioned sortedTasks into tasksToday and tasksTomorrow", listOf(tasksToday, tasksTomorrow))

            remainingTasks = tasksTomorrow + followingTasks
            logTaskLists("getSortedTasksForDay updated remainingTasks", listOf(remainingTasks))

            tasksToReturn = tasksToday
            today = today.plusDays(1)
        } while (!today.isAfter(endDay))

        logTaskLists("getSortedTasksForDay resulted in tasksToReturn tasks", listOf(tasksToReturn))
        return tasksToReturn
    }
}
