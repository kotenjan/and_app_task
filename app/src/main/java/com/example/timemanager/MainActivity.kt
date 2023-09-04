package com.example.timemanager

import android.annotation.SuppressLint
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity(), TaskModifyCallback {

    private lateinit var addButton: Button
    private lateinit var settingsButton: ImageView
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var dayLayout: LinearLayout
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var selectedViewButton: DayButton
    private lateinit var oldestDate: LocalDate
    private lateinit var newestDate: LocalDate
    private var displayDay: LocalDate? = null
    private val dayButtonMap: MutableMap<LocalDate, DayButton> = mutableMapOf()
    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object : Runnable {
        override fun run() {
            removeLastButtonForDate()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(runnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
    }

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> extras?.getParcelable(key, T::class.java)
        else -> @Suppress("DEPRECATION") extras?.getParcelable(key) as? T
    }

    private fun setNumberPickerRange(picker: NumberPicker, low: Int, high: Int){
        picker.minValue = low
        picker.maxValue = high
    }

    @SuppressLint("InflateParams")
    private fun settingsPopup(){

        val inflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.settings_popup, null, false)
        val width = LinearLayout.LayoutParams.WRAP_CONTENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = true
        val popupWindow = PopupWindow(popupView, width, height, focusable)

        val from: NumberPicker = popupView.findViewById(R.id.from)
        val to: NumberPicker = popupView.findViewById(R.id.to)

        setNumberPickerRange(from, 0, 22)
        setNumberPickerRange(to, 2, 24)

        taskViewModel.getTaskTimeRange().let { (fromValue, toValue) ->
            println(toValue)
            from.value = fromValue.hour
            if (toValue.minute > 0) {
                to.value = 24
            } else {
                to.value = toValue.hour
            }
        }

        from.setOnValueChangedListener { _, _, newVal ->
            from.value = maxOf(0, minOf(to.value - 2, newVal))
        }

        to.setOnValueChangedListener { _, _, newVal ->
            to.value = minOf(24, maxOf(from.value + 2, newVal))
        }

        popupWindow.showAtLocation(dayLayout, Gravity.CENTER, 0, 0)

        popupWindow.setOnDismissListener {
            val newFrom = LocalTime.of(from.value, 0)
            val newTo = if (to.value < 24) LocalTime.of(to.value, 0) else LocalTime.of(23, 59, 59)
            taskViewModel.modifyTaskTimeRange(newFrom, newTo, displayDay)
        }
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

        newestDate = if (::newestDate.isInitialized) { maxOf(newestDate, day) } else { day }
        oldestDate = if (::oldestDate.isInitialized) { minOf(oldestDate, day) } else { day }

        val buttonDayView = LayoutInflater.from(this).inflate(R.layout.button_day, dayLayout, false)
        val button = buttonDayView.findViewById<TextView>(R.id.dayButton)
        val background = buttonDayView.findViewById<LinearLayout>(R.id.background)

        val formatterTime = DateTimeFormatter.ofPattern("E dd/MM")
        button.text = day.format(formatterTime)

        button.setOnClickListener {
            dayButtonClick(day)
        }

        dayButtonMap[day] = DayButton(button, background)
        dayLayout.addView(buttonDayView, dayLayout.childCount - 2)
    }

    private fun removeLastButtonForDate() {

        val newOldestDate = LocalDate.now()

        while (newOldestDate > oldestDate) {
            val day = oldestDate

            if (displayDay == oldestDate) {
                dayButtonClick(newOldestDate)
            }

            dayButtonMap[day]?.let { info ->
                dayLayout.removeView(info.button.parent as View)
                dayButtonMap.remove(day)
            }

            oldestDate = newOldestDate

            addButtonForDate(newestDate.plusDays(1))
        }
    }

    private fun addButtonForFinishedTasks(){
        val buttonFinishedView = LayoutInflater.from(this).inflate(R.layout.button_day, dayLayout, false)
        val button = buttonFinishedView.findViewById<TextView>(R.id.dayButton)
        val background = buttonFinishedView.findViewById<LinearLayout>(R.id.background)

        val finishedButton = DayButton(button, background)

        button.text = getString(R.string.finished)

        button.setOnClickListener {
            finishedButtonClick(finishedButton)
        }

        dayLayout.addView(buttonFinishedView, dayLayout.childCount - 1)
    }

    private fun addButtonForMoreDays(){
        val buttonMoreView = LayoutInflater.from(this).inflate(R.layout.button_day, dayLayout, false)
        val button = buttonMoreView.findViewById<TextView>(R.id.dayButton)

        button.text = getString(R.string.add_more)

        button.setOnClickListener {
            for (i in 0..6) {
                addButtonForDate(newestDate.plusDays(1))
            }
        }

        dayLayout.addView(buttonMoreView, dayLayout.childCount - 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentDate = LocalDate.now()
        registerReceiver(notificationReceiver, IntentFilter(Variables.MAIN_ACTIVITY_INTENT))
        setContentView(R.layout.activity_main)
        addButton = findViewById(R.id.addButton)
        settingsButton = findViewById(R.id.settings)
        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        taskRecyclerView = findViewById(R.id.task_recycler_view)
        taskRecyclerView.layoutManager = LinearLayoutManager(this)
        dayLayout = findViewById(R.id.dayLayout)

        addButtonForFinishedTasks()
        addButtonForMoreDays()

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
                action = Variables.ACTION_CREATE
            }
            addTaskResultLauncher.launch(createTaskIntent)
        }

        settingsButton.setOnClickListener {
            settingsPopup()
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
            val data: Intent = result.data ?: return@registerForActivityResult
            val task = data.parcelable<Task>(Variables.TASK) ?: return@registerForActivityResult
            val id = data.getLongExtra(Variables.ID, -1)
            if (id != -1L) {
                taskViewModel.deleteTask(task.copy(id=id), displayDay, true)
            }
            taskViewModel.addTask(task, displayDay)
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

    override fun onCopyTask(task: Task) {
        val createTaskIntent = Intent(this, CreateTaskActivity::class.java).apply {
            action = Variables.ACTION_ADD
            putExtra(Variables.TASK, task)
        }
        modifyTaskResultLauncher.launch(createTaskIntent)
    }
}

