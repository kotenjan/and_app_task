package com.example.timemanager

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private lateinit var addButton: Button
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var dayLayout: LinearLayout
    private lateinit var displayDay: LocalDate
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskAdapter: TaskAdapter

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
                            taskViewModel.setRunningState(key, 0, displayDay)
                        }
                    }
                    Variables.ACTION_SET_TIME -> {
                        intent.parcelable<Task>(Variables.TASK)!!.let {key ->
                            intent.getLongExtra(Variables.VALUE, 0L)
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported action: $actionName")
                }
            }
        }
    }

    private fun createDayView(day: LocalDate){
        val buttonDayView = LayoutInflater.from(this).inflate(R.layout.button_day, dayLayout, false)
        val button = buttonDayView.findViewById<TextView>(R.id.dayButton)

        val formatterTime = DateTimeFormatter.ofPattern("dd/MM")
        button.text = "${day.format(formatterTime)}"

        button.setOnClickListener {
            println(day.format(formatterTime))
            displayDay = day
            displayTasks()
        }

        dayLayout.addView(buttonDayView)
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

        for (i in 0 until 14){
            createDayView(LocalDate.now().plusDays(i.toLong()))
        }

        taskViewModel.tasksLiveData.observe(this) { refreshedTasks ->
            taskAdapter.refresh(refreshedTasks)
        }

        addButton.setOnClickListener {
            val createTaskIntent = Intent(this, CreateTaskActivity::class.java).apply { action = Variables.ACTION_START }
            resultLauncher.launch(createTaskIntent)
        }

        displayTasks()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val task = data?.parcelable<Task>("task")
            task?.let { taskViewModel.addTask(it, displayDay) }
        }
    }

    private fun displayTasks() {
        taskAdapter = TaskAdapter(this, taskViewModel, displayDay)
        taskRecyclerView.adapter = taskAdapter
    }
}

