package com.example.timemanager

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity(), TaskModifyCallback {

    private lateinit var addButton: Button
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var dayLayout: LinearLayout
    private lateinit var displayDay: LocalDate
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskAdapter: TaskAdapter
    private var dayLayoutList: MutableList<LocalDate> = mutableListOf()
    private var currentButton: TextView? = null
    private var currentBackground: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> extras?.getParcelable(key, T::class.java)
        else -> @Suppress("DEPRECATION") extras?.getParcelable(key) as? T
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                when (val actionName = it.getStringExtra(Variables.ACTION_NAME)) {
                    Variables.ACTION_START -> {
                        intent.parcelable<Task>(Variables.TASK)!!.let {key ->
                            taskViewModel.modifyRunningState(key, displayDay)
                        }
                    }
                    Variables.ACTION_SET_TIME -> {
                        intent.parcelable<Task>(Variables.TASK)!!.let {key ->
                            taskViewModel.setTime(key, intent.getLongExtra(Variables.VALUE, 0L), displayDay)
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported action: $actionName")
                }
            }
        }
    }

    private fun clickFirstDay() {

        val currentDay = dayLayoutList[0]

        if (displayDay <= currentDay) {
            val firstView = dayLayout.getChildAt(0)

            val button = firstView.findViewById<TextView>(R.id.dayButton)
            val background = firstView.findViewById<LinearLayout>(R.id.background)

            dayButtonClick(button, background, currentDay)
        }
    }

    private fun dayButtonClick(button: TextView, background: LinearLayout, day: LocalDate) {
        currentBackground?.setBackgroundResource(R.drawable.day)
        currentButton?.setBackgroundResource(R.drawable.day)
        currentButton?.setTextColor(Color.parseColor("#2c2a28"))

        background.setBackgroundResource(R.drawable.create_button_shadow)
        button.setBackgroundResource(R.drawable.create_button)
        button.setTextColor(Color.WHITE)

        currentBackground = background
        currentButton = button

        displayDay = day
        displayTasks()
    }

    private fun createDayView(day: LocalDate){
        val buttonDayView = LayoutInflater.from(this).inflate(R.layout.button_day, dayLayout, false)
        val button = buttonDayView.findViewById<TextView>(R.id.dayButton)
        val background = buttonDayView.findViewById<LinearLayout>(R.id.background)

        val formatterTime = DateTimeFormatter.ofPattern("dd/MM")
        button.text = day.format(formatterTime)

        button.setOnClickListener {
            dayButtonClick(button, background, day)
        }

        dayLayout.addView(buttonDayView)
        dayLayoutList.add(day)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(notificationReceiver, IntentFilter(Variables.MAIN_ACTIVITY_INTENT))
        setContentView(R.layout.activity_main)
        displayDay = LocalDate.now()
        addButton = findViewById(R.id.addButton)
        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        taskRecyclerView = findViewById(R.id.task_recycler_view)
        taskRecyclerView.layoutManager = LinearLayoutManager(this)
        dayLayout = findViewById(R.id.dayLayout)

        for (i in 0 until 8){
            createDayView(displayDay.plusDays(i.toLong()))
        }

        clickFirstDay()

        taskViewModel.tasksLiveData.observe(this) { refreshedTasks ->
            taskAdapter.refresh(refreshedTasks)
        }

        addButton.setOnClickListener {
            val createTaskIntent = Intent(this, CreateTaskActivity::class.java).apply {
                action = Variables.ACTION_ADD
            }
            addTaskResultLauncher.launch(createTaskIntent)
        }

        displayTasks()
        scheduleMidnightUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
    }

    private val addTaskResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val task = data?.parcelable<Task>(Variables.TASK)
            task?.let { taskViewModel.addTask(it, displayDay) }
        }
    }

    private val modifyTaskResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val task = data?.parcelable<Task>(Variables.TASK)
            task?.let { taskViewModel.addTask(it, displayDay) }
        }
    }

    private fun displayTasks() {
        taskAdapter = TaskAdapter(this, taskViewModel, displayDay, this)
        taskRecyclerView.adapter = taskAdapter
    }

    private fun scheduleMidnightUpdate() {
        val nextMidnight = dayLayoutList[0].plusDays(1).atStartOfDay().plusSeconds(1)
        val delay = Duration.between(LocalDateTime.now(), nextMidnight).toMillis()

        handler.postDelayed({
            dayLayout.removeViewAt(0)
            dayLayoutList.removeAt(0)

            createDayView(dayLayoutList[0].plusDays(7))
            clickFirstDay()
            scheduleMidnightUpdate()
        }, delay)
    }

    override fun onModifyTask(task: Task) {
        val createTaskIntent = Intent(this, CreateTaskActivity::class.java).apply {
            action = Variables.ACTION_MODIFY
            putExtra(Variables.TASK, task)
        }
        modifyTaskResultLauncher.launch(createTaskIntent)
    }
}

