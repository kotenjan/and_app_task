package com.example.timemanager

import android.app.Activity
import android.content.Intent
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
    private val tag = "MainActivity"

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> extras?.getParcelable(key, T::class.java)
        else -> @Suppress("DEPRECATION") extras?.getParcelable(key) as? T
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
        setContentView(R.layout.activity_main)
        displayDay = LocalDate.now()
        addButton = findViewById(R.id.addButton)
        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        taskViewModel.deleteAll()
        taskRecyclerView = findViewById(R.id.task_recycler_view)
        taskRecyclerView.layoutManager = LinearLayoutManager(this)
        dayLayout = findViewById(R.id.dayLayout)

        for (i in 0 until 14){
            createDayView(LocalDate.now().plusDays(i.toLong()))
        }

        taskViewModel.tasksLiveData.observe(this, Observer { refreshedTasks ->
            taskAdapter.refresh(refreshedTasks)
        })

        addButton.setOnClickListener {
            val createTaskIntent = Intent(this, CreateTaskActivity::class.java).apply { action = "ACTION_START" }
            resultLauncher.launch(createTaskIntent)
        }

        displayTasks()
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val task = data?.parcelable<Task>("task")
            task?.let { taskViewModel.addTask(it) }
        }
    }

    private fun displayTasks() {
        taskAdapter = TaskAdapter(this, taskViewModel, displayDay)
        taskRecyclerView.adapter = taskAdapter
    }
}

