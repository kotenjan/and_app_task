package com.example.timemanager

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity(), TaskModifyCallback {

    private lateinit var addButton: Button
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var dayLayout: LinearLayout
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var selectedViewButton: DayButton
    private var displayDay: LocalDate? = null
    private val dayButtonMap: MutableMap<LocalDate, DayButton> = mutableMapOf()

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

    private fun selectButton(button: DayButton) {
        button.background.setBackgroundResource(R.drawable.create_button_shadow)
        button.button.setBackgroundResource(R.drawable.create_button)
        button.button.setTextColor(Color.WHITE)

        selectedViewButton = button
    }

    private fun unselectButton() {
        if (::selectedViewButton.isInitialized) {
            selectedViewButton.background.setBackgroundResource(R.drawable.day)
            selectedViewButton.button.setBackgroundResource(R.drawable.day)
            selectedViewButton.button.setTextColor(Color.parseColor("#2c2a28"))
        }
    }

    private fun dayButtonClick(day: LocalDate) {
        val selectedButton = dayButtonMap[day] ?: return

        unselectButton()
        selectButton(selectedButton)

        displayDay = day
        displayTasks()
    }

    private fun finishedButtonClick(finishedButton: DayButton) {

        unselectButton()
        selectButton(finishedButton)

        displayDay = null
        displayTasks()
    }

    private fun addButtonForDate(day: LocalDate){
        val buttonDayView = LayoutInflater.from(this).inflate(R.layout.button_day, dayLayout, false)
        val button = buttonDayView.findViewById<TextView>(R.id.dayButton)
        val background = buttonDayView.findViewById<LinearLayout>(R.id.background)

        val formatterTime = DateTimeFormatter.ofPattern("dd/MM")
        button.text = day.format(formatterTime)

        button.setOnClickListener {
            dayButtonClick(day)
        }

        dayButtonMap[day] = DayButton(button, background)
        dayLayout.addView(buttonDayView)
    }

    fun removeButtonForDate(day: LocalDate) {
        dayButtonMap[day]?.let { info ->
            dayLayout.removeView(info.button.parent as View)
            dayButtonMap.remove(day)
        }
    }

    private fun addButtonForFinishedTasks(){
        val buttonFinishedView = LayoutInflater.from(this).inflate(R.layout.button_day, dayLayout, false)
        val button = buttonFinishedView.findViewById<TextView>(R.id.dayButton)
        val background = buttonFinishedView.findViewById<LinearLayout>(R.id.background)

        val finishedButton = DayButton(button, background)

        button.text = "FINISHED"

        button.setOnClickListener {
            finishedButtonClick(finishedButton)
        }

        dayLayout.addView(buttonFinishedView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentDate = LocalDate.now()
        registerReceiver(notificationReceiver, IntentFilter(Variables.MAIN_ACTIVITY_INTENT))
        setContentView(R.layout.activity_main)
        addButton = findViewById(R.id.addButton)
        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        taskRecyclerView = findViewById(R.id.task_recycler_view)
        taskRecyclerView.layoutManager = LinearLayoutManager(this)
        dayLayout = findViewById(R.id.dayLayout)

        addButtonForFinishedTasks()

        for (i in 0 until 8){
            addButtonForDate(currentDate.plusDays(i.toLong()))
        }

        selectButton(dayButtonMap[currentDate]!!)

        displayDay = currentDate

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

    override fun onModifyTask(task: Task) {
        val createTaskIntent = Intent(this, CreateTaskActivity::class.java).apply {
            action = Variables.ACTION_MODIFY
            putExtra(Variables.TASK, task)
        }
        modifyTaskResultLauncher.launch(createTaskIntent)
    }
}

