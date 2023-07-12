package com.example.timemanager

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CalendarView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class CreateTaskActivity : ComponentActivity() {

    private var selectedDate = LocalDate.now()
    private var currentTime = LocalDateTime.now()
    var intervalDays = 0
    private var createdTime = currentTime

    @SuppressLint("InflateParams")
    private fun repeatingPopup(checkBoxRepeat: CheckBox, textViewRepeat: TextView){
        if (checkBoxRepeat.isChecked) {
            val inflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.interval_popup, null)
            val width = LinearLayout.LayoutParams.WRAP_CONTENT
            val height = LinearLayout.LayoutParams.WRAP_CONTENT
            val focusable = true
            val popupWindow = PopupWindow(popupView, width, height, focusable)
            val repeatD: NumberPicker = popupView.findViewById(R.id.repeatD)
            setNumberPickerRange(repeatD, 1, 30)

            if (intervalDays != 0){
                repeatD.value = intervalDays
            }

            popupWindow.showAtLocation(checkBoxRepeat, Gravity.CENTER, 0, 0)

            val cancelButton: Button = popupView.findViewById(R.id.cancelButton)
            val okButton: Button = popupView.findViewById(R.id.okButton)
            var ok = false

            cancelButton.setOnClickListener {
                popupWindow.dismiss()
            }
            okButton.setOnClickListener {
                intervalDays = repeatD.value
                ok = true
                popupWindow.dismiss()
            }

            popupWindow.setOnDismissListener {
                checkBoxRepeat.isChecked = ok
                if (ok){
                    if (intervalDays == 1){
                        textViewRepeat.text = "Every day"
                    } else {
                        textViewRepeat.text = "Every $intervalDays days"
                    }
                } else{
                    textViewRepeat.text = ""
                }
            }
        } else {
            intervalDays = 0
            textViewRepeat.text = ""
        }
    }

    @SuppressLint("InflateParams")
    private fun fixedTimePopup(checkBoxHasFixedTime: CheckBox, textViewHasFixedTime: TextView){
        if (checkBoxHasFixedTime.isChecked) {
            val formatterTime = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            val inflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.datetime_popup, null, false)
            val width = LinearLayout.LayoutParams.WRAP_CONTENT
            val height = LinearLayout.LayoutParams.WRAP_CONTENT
            val focusable = true
            val popupWindow = PopupWindow(popupView, width, height, focusable)
            val fixedPickerHL: NumberPicker = popupView.findViewById(R.id.fixedHL)
            val fixedPickerML: NumberPicker = popupView.findViewById(R.id.fixedML)
            val fixedPickerMR: NumberPicker = popupView.findViewById(R.id.fixedMR)
            val fixedCalendarView = popupView.findViewById<CalendarView>(R.id.calendarView)
            val cancelButton: Button = popupView.findViewById(R.id.cancelButton)
            val okButton: Button = popupView.findViewById(R.id.okButton)

            setNumberPickerRange(fixedPickerHL, 0, 23)
            setNumberPickerRange(fixedPickerML, 0, 5)
            setNumberPickerRange(fixedPickerMR, 0, 9)

            fixedPickerHL.value = createdTime.hour
            fixedPickerML.value = createdTime.minute / 10
            fixedPickerMR.value = createdTime.minute % 10

            fixedCalendarView.minDate = System.currentTimeMillis()

            fixedCalendarView.date = selectedDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            popupWindow.showAtLocation(checkBoxHasFixedTime, Gravity.CENTER, 0, 0)

            var ok = false

            fixedCalendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            }

            cancelButton.setOnClickListener {
                popupWindow.dismiss()
            }
            okButton.setOnClickListener {
                createdTime = getSelectedDateTime(selectedDate, fixedPickerHL, fixedPickerML, fixedPickerMR)
                ok = true
                popupWindow.dismiss()
            }

            popupWindow.setOnDismissListener {
                checkBoxHasFixedTime.isChecked = ok
                if (ok){
                    textViewHasFixedTime.text = "${createdTime.format(formatterTime)}"
                } else{
                    textViewHasFixedTime.text = ""
                }
            }
        } else {
            createdTime = currentTime
            textViewHasFixedTime.text = ""
        }
    }

    private fun fillLayoutWithCircles(layout: LinearLayout, visibleColorCircleHolder: VisibleColorCircleHolder) {
        val picker = ColorPicker()
        val context = layout.context
        var rowLayout: LinearLayout? = null
        val colorGradient = picker.generateColorGradient()
        val randomIndex = Random.nextInt(colorGradient.size)

        colorGradient.forEachIndexed { index, color ->
            if (index % 3 == 0) {
                rowLayout = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(50, 0, 50, 50)  // set padding here
                    gravity = Gravity.CENTER    // set gravity here
                    orientation = LinearLayout.HORIZONTAL
                }
                layout.addView(rowLayout)
            }

            val circleView = createCircleView(context, color, picker, visibleColorCircleHolder, randomIndex == index)
            rowLayout?.addView(circleView)
        }
    }

    @SuppressLint("InflateParams")
    private fun createCircleView(
        context: Context,
        color: String,
        picker: ColorPicker,
        visibleColorCircleHolder: VisibleColorCircleHolder,
        randomIndex: Boolean
    ): View {
        val circleView = LayoutInflater.from(context).inflate(R.layout.circle, null)

        // set layout params to have weight 1
        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        circleView.layoutParams = params

        val innerCircle: View = circleView.findViewById(R.id.inner_circle)
        val outerCircle: View = circleView.findViewById(R.id.outer_circle)


        val shapeDrawable = ContextCompat.getDrawable(context, R.drawable.circle) as GradientDrawable
        shapeDrawable.setColor(Color.parseColor(picker.darkenColor(picker.lightenColor(color, Variables.WHITE_PERCENTAGE), Variables.BLACK_PERCENTAGE_FRONT))) // set the color
        innerCircle.background = shapeDrawable

        if (randomIndex) {
            setColor(visibleColorCircleHolder, outerCircle, color)
        }

        innerCircle.setOnClickListener {
            setColor(visibleColorCircleHolder, outerCircle, color)
        }

        return circleView
    }

    private fun setColor(visibleColorCircleHolder: VisibleColorCircleHolder, outerCircle: View, color: String) {
        visibleColorCircleHolder.view?.visibility = View.INVISIBLE
        outerCircle.visibility = View.VISIBLE
        visibleColorCircleHolder.view = outerCircle
        visibleColorCircleHolder.currentColor = color
    }

    private fun setNumberPickerRange(picker: NumberPicker, low: Int, high: Int){
        picker.minValue = low
        picker.maxValue = high
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_create_task)

        val createTaskButton: Button = findViewById(R.id.createTaskButton)
        val taskPickerHL: NumberPicker = findViewById(R.id.taskHL)
        val taskPickerML: NumberPicker = findViewById(R.id.taskML)
        val taskPickerMR: NumberPicker = findViewById(R.id.taskMR)
        val titleEditText: EditText = findViewById(R.id.taskTitleEditText)
        val priorityBar: SeekBar = findViewById(R.id.priorityBar)
        val checkBoxRepeat: CheckBox = findViewById(R.id.checkBoxRepeat)
        val checkBoxHasFixedTime: CheckBox = findViewById(R.id.checkBoxHasFixedTime)
        val textViewRepeat: TextView = findViewById(R.id.textViewRepeat)
        val textViewHasFixedTime: TextView = findViewById(R.id.textViewHasFixedTime)
        val colorPickerLayout: LinearLayout = findViewById(R.id.color_picker_layout)
        val visibleColorCircleHolder = VisibleColorCircleHolder(null, "#3094F0")

        setNumberPickerRange(taskPickerHL, 0, 23)
        setNumberPickerRange(taskPickerML, 0, 5)
        setNumberPickerRange(taskPickerMR, 0, 9)

        val transitionSet = TransitionSet()
        transitionSet.addTransition(ChangeBounds())
        transitionSet.addTransition(Fade())
        transitionSet.duration = 200
        transitionSet.ordering = TransitionSet.ORDERING_TOGETHER

        textViewRepeat.setOnClickListener {
            repeatingPopup(checkBoxRepeat, textViewRepeat)
        }

        checkBoxRepeat.setOnCheckedChangeListener { _, _ ->
            repeatingPopup(checkBoxRepeat, textViewRepeat)
        }

        textViewHasFixedTime.setOnClickListener {
            fixedTimePopup(checkBoxHasFixedTime, textViewHasFixedTime)
        }

        checkBoxHasFixedTime.setOnCheckedChangeListener { _, _ ->
            fixedTimePopup(checkBoxHasFixedTime, textViewHasFixedTime)
        }

        fillLayoutWithCircles(colorPickerLayout, visibleColorCircleHolder)

        createTaskButton.setOnClickListener {

            //TODO: add time constraints

            currentTime = LocalDateTime.now()

            val text = titleEditText.text.toString()
            Log.d("CreateTaskActivity", "Title: $text")
            val duration = Duration.ofMinutes((taskPickerHL.value * 60 + taskPickerML.value * 10 + taskPickerMR.value).toLong())
            Log.d("CreateTaskActivity", "Duration: $duration")
            val priority = priorityBar.progress
            Log.d("CreateTaskActivity", "Priority: $priority")
            Log.d("CreateTaskActivity", "Repeat Days: $intervalDays")
            val fixedTime = checkBoxHasFixedTime.isChecked
            Log.d("CreateTaskActivity", "Has Fixed Time: $fixedTime")

            Log.d("CreateTaskActivity", "Start time: $createdTime")

            val task = Task(
                id = System.currentTimeMillis(),
                text = text,
                isTemplate = intervalDays != 0,
                duration = duration.seconds,
                priority = priority,
                intervalDays = intervalDays,
                fixedTime = fixedTime,
                createdTime = createdTime,
                startTime = createdTime,
                timeLeft = duration.seconds,
                isRunning = false,
                color = visibleColorCircleHolder.currentColor,
                isDetailVisible = false,
                status = TaskStatus.REMAINING,
            )

            val returnIntent = Intent()
            returnIntent.putExtra("task", task)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
    }

    private fun getSelectedDateTime(
        selectedDate: LocalDate,
        fixedPickerHL: NumberPicker,
        fixedPickerML: NumberPicker,
        fixedPickerMR: NumberPicker
    ): LocalDateTime {

        val selectedHour: Int = fixedPickerHL.value
        val selectedMinuteTens: Int = fixedPickerML.value * 10
        val selectedMinuteOnes: Int = fixedPickerMR.value
        val selectedMinute: Int = selectedMinuteTens + selectedMinuteOnes
        val selectedTime: LocalTime = LocalTime.of(selectedHour, selectedMinute)

        return LocalDateTime.of(selectedDate, selectedTime)
    }
}
