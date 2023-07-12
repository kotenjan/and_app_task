package com.example.timemanager

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import kotlin.coroutines.CoroutineContext

class NotificationService : Service(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private val channelId = "NotificationChannel"
    private val notificationId = 1

    private lateinit var builder: NotificationCompat.Builder
    private lateinit var manager: NotificationManagerCompat

    private val taskDatabase: TaskDatabase by lazy { TaskDatabase.getDatabase(application) }
    private val taskDao: TaskDao by lazy { taskDatabase.taskDao() }

    private val binder = Binder()

    private var isUpdateRunning = false
    private var isWatchRunning = false

    private fun startUpdate() {
        if (!isUpdateRunning) {
            launch {
                isUpdateRunning = true
                try {
                    while (isActive) {
                        taskDao.updateTaskTimes()
                        delay(1000L)
                    }
                } finally {
                    isUpdateRunning = false
                }
            }
        }
    }

    private fun startWatch() {
        if (!isWatchRunning) {
            launch {
                isWatchRunning = true
                try {
                    while (isActive) {
                        delay(6400L)
                        watch()
                    }
                } finally {
                    isWatchRunning = false
                }
            }
        }
    }

    private suspend fun watch() {
        if (taskDao.countRunningTasks() == 0) {
            kill()
        }
    }

    private fun kill(){
        cancel()
        manager.cancel(notificationId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("NotificationService", "Service start command received")

        startUpdate()
        startWatch()

        return START_STICKY
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
        val serviceChannel = NotificationChannel(channelId, "Notification Channel", NotificationManager.IMPORTANCE_DEFAULT)
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
    }

    private fun setButtonFunctions(notificationBigLayout: RemoteViews) {
        val nextIntent = Intent(this, NotificationService::class.java).apply { action = "ACTION_NOTIFICATION_FORWARD" }
        val pendingNextIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.notification_duration_forward, pendingNextIntent)

        val previousIntent = Intent(this, NotificationService::class.java).apply { action = "ACTION_NOTIFICATION_BACK" }
        val pendingPreviousIntent = PendingIntent.getService(this, 1, previousIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.notification_duration_back, pendingPreviousIntent)

        val playIntent = Intent(this, NotificationService::class.java).apply { action = "ACTION_PLAY" }
        val pendingPlayIntent = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.notification_play, pendingPlayIntent)

        val previousTaskIntent = Intent(this, NotificationService::class.java).apply { action = "ACTION_PREVIOUS" }
        val pendingPreviousTaskIntent = PendingIntent.getService(this, 3, previousTaskIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.previous_task, pendingPreviousTaskIntent)

        val nextTaskIntent = Intent(this, NotificationService::class.java).apply { action = "ACTION_NEXT" }
        val nextPreviousTaskIntent = PendingIntent.getService(this, 4, nextTaskIntent, PendingIntent.FLAG_IMMUTABLE)
        notificationBigLayout.setOnClickPendingIntent(R.id.next_task, nextPreviousTaskIntent)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(update: Triple<Task, Int, Int>) {

        val intent = Intent("task_duration_update")
        intent.putExtra("updated_duration", update.first.timeLeft.toString())
        intent.putExtra("task_id", update.first.id.toString())
        sendBroadcast(intent)
        println("SENT ${update.first.timeLeft}")

        val notificationLayout = RemoteViews(packageName, R.layout.notification_layout)
        val notificationBigLayout = RemoteViews(packageName, R.layout.notification_big_layout)

        setButtonFunctions(notificationBigLayout)

        if (update.first.timeLeft > 0) {
            val timeText = timeLeft(update.first.timeLeft)
            notificationLayout.setTextViewText(R.id.notification_countdown, timeText)
            notificationBigLayout.setTextViewText(R.id.notification_countdown, timeText)
        } else {
            notificationLayout.setTextViewText(R.id.notification_countdown, "Finished!")
            notificationBigLayout.setTextViewText(R.id.notification_countdown, "Finished!")
        }

        if (update.first.isRunning){
            notificationBigLayout.setImageViewResource(R.id.notification_play, R.drawable.ic_pause)
        } else {
            notificationBigLayout.setImageViewResource(R.id.notification_play, R.drawable.ic_play)
        }

        if (update.third > 1){
            notificationBigLayout.setViewVisibility(R.id.task_counter_view, View.VISIBLE)
            notificationBigLayout.setTextViewText(R.id.task_counter, "${update.second + 1}/${update.third}")
        } else {
            notificationBigLayout.setViewVisibility(R.id.task_counter_view, View.GONE)
        }

        builder.color = Color.parseColor(update.first.color)
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
