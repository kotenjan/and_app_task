package com.example.timemanager

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class NotificationService : Service(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private val channelId = "NotificationChannel"
    private val notificationId = 1
    private val notificationViewModel: NotificationViewModel by lazy { NotificationViewModel(application) }
    private lateinit var builder: NotificationCompat.Builder
    private lateinit var manager: NotificationManagerCompat
    private val binder = Binder()
    private val delay = Delay()
    private var isUpdateRunning = false

    private fun startUpdate() {
        if (!isUpdateRunning) {
            launch {
                isUpdateRunning = true
                try {
                    while (isActive) {
                        //notificationViewModel.updateTaskTimes()
                        notificationViewModel.updateTasks()
                        updateNotification(notificationViewModel.getTask(), notificationViewModel.position, notificationViewModel.count)
                        delay.delayToNextSecond(100)
                    }
                } finally {
                    isUpdateRunning = false
                }
            }
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

        intent?.let {
            when(it.action) {
                "ACTION_NOTIFICATION_FORWARD" -> {
                    notificationViewModel.forward()
                }
                "ACTION_NOTIFICATION_BACK" -> {
                    notificationViewModel.back()
                }
                "ACTION_PLAY" -> {
                    notificationViewModel.updateRunningState()
                }
                "ACTION_PREVIOUS" -> {
                    // Handle previous task action
                }
                "ACTION_NEXT" -> {
                    // Handle next task action
                }
                "ACTION_SET_TIME" -> {

                }
                "ACTION_START" -> {
                    startUpdate()
                }
                else -> {
                    // Handle other actions
                }
            }
        }

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

        task?.let { builder.color = Color.parseColor(task.color) }
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