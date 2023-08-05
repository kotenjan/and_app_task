package com.example.timemanager

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.graphics.Color
import android.media.AudioManager.STREAM_NOTIFICATION
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

class NotificationService : Service(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }
    private val channelId = "NotificationChannel"
    private val notificationId = 1
    private lateinit var builder: NotificationCompat.Builder
    private lateinit var manager: NotificationManagerCompat
    private val binder = Binder()
    private val delay = Delay()
    private val runningTasks: MutableList<Task> = mutableListOf()
    private var isUpdateRunning = false
    private var runningTaskIndex = 0
    private val picker = ColorPicker()
    private val updateMutex = Mutex()

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> extras?.getParcelable(key, T::class.java)
        else -> @Suppress("DEPRECATION") extras?.getParcelable(key) as? T
    }

    init {
        startUpdate()
    }

    private suspend fun decreaseRunningTaskTime() {

        val iterator = runningTasks.iterator()

        while (iterator.hasNext()) {
            val task = iterator.next()
            task.timeLeft -= 1
            if (task.timeLeft <= 0) {
                taskDao.finishTaskFromNotification(task.id, task.createdTime)
                iterator.remove()
            } else {
                taskDao.insert(task)
            }
        }
    }

    private fun startUpdate() {
        if (!isUpdateRunning) {
            launch {
                isUpdateRunning = true
                try {
                    while (isActive) {
                        if (runningTasks.size > 0) {
                            onStartCommand(null, 0, 0)
                        }
                        delay.delayToNextSecond()
                    }
                } finally {
                    isUpdateRunning = false
                }
            }
        }
    }

    private fun sendIntentToMainActivity(actionName: String, task: Task? = null, value: Long? = null) {
        launch {
            val intent = Intent(Variables.MAIN_ACTIVITY_INTENT).apply {
                putExtra(Variables.ACTION_NAME, actionName)
                task?.let {
                    putExtra(Variables.TASK, it)
                }
                value?.let {
                    putExtra(Variables.VALUE, it)
                }
            }
            sendBroadcast(intent)
        }
    }

    private fun kill(){
        cancel()
        manager.cancel(notificationId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun update(intent: Intent?) {
        updateMutex.withLock {
            intent?.let {
                when(it.action) {
                    Variables.ACTION_ADD -> {
                        intent.parcelable<Task>(Variables.TASK)!!.let { task ->
                            task.timeLeft = intent.getLongExtra(Variables.VALUE, 0L)
                            val existingTask = runningTasks.find { instance -> instance == task }
                            if (existingTask == null) {
                                runningTasks.add(task)
                            }
                            existingTask?.timeLeft = task.timeLeft
                        }
                    }
                    Variables.ACTION_REMOVE -> {
                        intent.parcelable<Task>(Variables.TASK)!!.let { key ->
                            taskDao.stopTaskFromNotification(key.id, key.createdTime, key.timeLeft)
                            if (intent.getLongExtra(Variables.VALUE, 0L) == 0L){
                                runningTasks.removeIf {task -> key == task }
                            } else {
                                runningTasks.removeIf {task -> key.id == task.id }
                            }
                            if (runningTasks.size == 0) {
                                kill()
                                return
                            }
                            runningTaskIndex %= maxOf(runningTasks.size, 1)
                        }
                    }
                    Variables.ACTION_NOTIFICATION_SET_TIME -> {
                        intent.parcelable<Task>(Variables.TASK)!!.let {key ->
                            runningTasks.forEach { task ->
                                if (key == task) { task.timeLeft = intent.getLongExtra(Variables.VALUE, 0L) }
                            }
                        }
                    }
                    Variables.ACTION_NOTIFICATION_FORWARD -> {
                        val task = runningTasks[runningTaskIndex]
                        task.timeLeft = maxOf(task.timeLeft - 30, 0)
                        taskDao.insert(task)
                        sendIntentToMainActivity(Variables.ACTION_SET_TIME, task = task, value = task.timeLeft)
                    }
                    Variables.ACTION_NOTIFICATION_BACK -> {
                        val task = runningTasks[runningTaskIndex]
                        task.timeLeft = minOf(task.timeLeft + 30, task.duration)
                        taskDao.insert(task)
                        sendIntentToMainActivity(Variables.ACTION_SET_TIME, task = task, value = task.timeLeft)
                    }
                    Variables.ACTION_NOTIFICATION_PLAY -> {
                        val key = runningTasks[runningTaskIndex]
                        sendIntentToMainActivity(Variables.ACTION_START, task = key)
                        taskDao.stopTaskFromNotification(key.id, key.createdTime, key.timeLeft)
                        runningTasks.removeAt(runningTaskIndex)
                        if (runningTasks.size == 0) {
                            kill()
                            return
                        }
                        runningTaskIndex %= maxOf(runningTasks.size, 1)
                    }
                    Variables.ACTION_NOTIFICATION_PREVIOUS -> {
                        val size = runningTasks.size
                        if (size > 0) {
                            runningTaskIndex = (runningTaskIndex + size - 1) % size
                        }
                    }
                    Variables.ACTION_NOTIFICATION_NEXT -> {
                        val size = runningTasks.size
                        if (size > 0) {
                            runningTaskIndex = (runningTaskIndex + 1) % size
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported action: ${it.action}")
                }
            } ?: run {
                decreaseRunningTaskTime()
            }
            if (runningTasks.size > 0) {
                updateNotification(runningTasks[runningTaskIndex], runningTaskIndex, runningTasks.size)
            } else {
                kill()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        launch { update(intent) }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        builder = buildNotification()
        manager = NotificationManagerCompat.from(this)
        startForeground(notificationId, builder.build())
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(channelId, "Notification Channel", NotificationManager.IMPORTANCE_LOW)
        serviceChannel.setSound(null, null)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    private fun buildNotification(): NotificationCompat.Builder {
        val notificationLayout = RemoteViews(packageName, R.layout.notification_layout)
        val notificationBigLayout = RemoteViews(packageName, R.layout.notification_big_layout)

        setButtonFunctions(notificationBigLayout)

        notificationLayout.setTextViewText(R.id.notification_countdown, "100")
        notificationBigLayout.setTextViewText(R.id.notification_countdown, "100")
        notificationLayout.setTextViewText(R.id.notification_title, "My Title")
        notificationBigLayout.setTextViewText(R.id.notification_title, "My Title")

        return NotificationCompat.Builder(this, channelId)
            .setColor(ContextCompat.getColor(this, R.color.defaultTaskColor))
            .setColorized(true)
            .setSmallIcon(R.drawable.ic_timer)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(notificationLayout)
            .setCustomBigContentView(notificationBigLayout)
            .setOnlyAlertOnce(true)
            .setSound(null)
    }

    private fun setButtonFunctions(notificationBigLayout: RemoteViews) {
        val nextIntent = Intent(this, NotificationService::class.java).apply { action = Variables.ACTION_NOTIFICATION_FORWARD }
        val pendingNextIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.notification_duration_forward, pendingNextIntent)

        val previousIntent = Intent(this, NotificationService::class.java).apply { action = Variables.ACTION_NOTIFICATION_BACK }
        val pendingPreviousIntent = PendingIntent.getService(this, 1, previousIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.notification_duration_back, pendingPreviousIntent)

        val playIntent = Intent(this, NotificationService::class.java).apply { action = Variables.ACTION_NOTIFICATION_PLAY }
        val pendingPlayIntent = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.notification_play, pendingPlayIntent)

        val previousTaskIntent = Intent(this, NotificationService::class.java).apply { action = Variables.ACTION_NOTIFICATION_PREVIOUS }
        val pendingPreviousTaskIntent = PendingIntent.getService(this, 3, previousTaskIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.previous_task, pendingPreviousTaskIntent)

        val nextTaskIntent = Intent(this, NotificationService::class.java).apply { action = Variables.ACTION_NOTIFICATION_NEXT }
        val nextPreviousTaskIntent = PendingIntent.getService(this, 4, nextTaskIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.next_task, nextPreviousTaskIntent)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(task: Task?, position: Int, count: Int) {

        task?.let {
            val intent = Intent("task_duration_update")
            intent.putExtra("updated_duration", it.timeLeft.toString())
            intent.putExtra("task_id", it.id.toString())
            sendBroadcast(intent)
        }

        val notificationLayout = RemoteViews(packageName, R.layout.notification_layout)
        val notificationBigLayout = RemoteViews(packageName, R.layout.notification_big_layout)

        setButtonFunctions(notificationBigLayout)

        if ((task?.timeLeft ?: 0) > 0) {
            val timeText = timeLeft(task!!.timeLeft)
            notificationLayout.setTextViewText(R.id.notification_countdown, timeText)
            notificationBigLayout.setTextViewText(R.id.notification_countdown, timeText)
        } else {
            notificationLayout.setTextViewText(R.id.notification_countdown, "Finished!")
            notificationBigLayout.setTextViewText(R.id.notification_countdown, "Finished!")
        }

        if (task?.isRunning == true){
            notificationBigLayout.setImageViewResource(R.id.notification_play, R.drawable.ic_pause)
        } else {
            notificationBigLayout.setImageViewResource(R.id.notification_play, R.drawable.ic_play)
        }

        if (count > 1){
            notificationBigLayout.setViewVisibility(R.id.task_counter_view, View.VISIBLE)
            notificationBigLayout.setTextViewText(R.id.task_counter, "${position + 1}/${count}")
        } else {
            notificationBigLayout.setViewVisibility(R.id.task_counter_view, View.GONE)
        }

        task?.let { builder.color = Color.parseColor(picker.darkenColor(picker.lightenColor(task.color, Variables.WHITE_PERCENTAGE), Variables.BLACK_PERCENTAGE_FRONT)) }
        builder.setCustomContentView(notificationLayout)
        builder.setCustomBigContentView(notificationBigLayout)

        manager.notify(notificationId, builder.build())
    }

    private fun timeLeft(timeLeft: Long): String {

        val hours = timeLeft / 3600
        val minutes = (timeLeft % 3600) / 60
        val seconds = timeLeft % 60

        return if (hours > 0) {
            String.format("%02d:%02d", hours, minutes)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}