package com.example.timemanager

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TaskAdapter(
    private val context: Context,
    private val taskViewModel: TaskViewModel,
    private val displayDay: LocalDate
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>(), CoroutineScope {

    private val job = Job()
    override val coroutineContext = Dispatchers.IO + job
    private val delay = Delay()
    private var tasks: List<Task> = listOf()

    init {
        start()
    }

    fun refresh(refreshedTasks: List<Task>) {
        val diffCallback = TaskDiffCallback(tasks, refreshedTasks)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        tasks = refreshedTasks

        diffResult.dispatchUpdatesTo(this)
    }

    private fun start() {
        launch {

            taskViewModel.loadTasks(displayDay)

            while (isActive) {
                taskViewModel.updateRunningTasks(displayDay)
                delay.delayToNextSecond(offsetMillis = 0)
            }
        }
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
            detail.visibility = if (task.isDetailVisible) View.VISIBLE else View.GONE
            taskProgress.max = task.duration.toInt()
            taskProgress.progress = task.duration.minus(task.timeLeft).toInt()

            taskLayout.setOnClickListener {
                task.isDetailVisible = !task.isDetailVisible
                setDetail(task)
                taskViewModel.setTaskDetail(task, displayDay, task.isDetailVisible)
            }

            backButton.setOnClickListener {
                taskViewModel.modifyTime(task, 30, displayDay)
            }

            forwardButton.setOnClickListener {
                taskViewModel.modifyTime(task, -30, displayDay)
            }

            playButton.setOnClickListener {
                if (task.isRunning) {
                    stopTask(task, changeButton = true)
                } else {
                    startTask(task, changeButton = true)
                }
            }

            removeButton.setOnClickListener{
                taskViewModel.deleteTask(task, displayDay, false)
            }

            taskProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                var running = true

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        taskViewModel.setTime(task, task.duration - progress, displayDay)
                    }
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
        }

        private fun startTask(task: Task, changeButton: Boolean){
            if (!task.isRunning && task.timeLeft > 0){

                task.isRunning = true

                taskViewModel.setRunningState(task, 1, displayDay)

                if (changeButton) {
                    playButton.setImageResource(R.drawable.ic_pause)
                }
            } else {
                stopTask(task, changeButton)
            }
        }

        private fun stopTask(task: Task, changeButton: Boolean){

            task.isRunning = false

            taskViewModel.setRunningState(task, 0, displayDay)

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
        return tasks.size
    }
}
