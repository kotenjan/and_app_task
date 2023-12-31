package com.example.timemanager

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.TypeConverters
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Parcelize
@Entity(tableName = "tasks", primaryKeys = ["id", "createdTime", "isTemplate"])
data class Task(
    val id: Long,
    val text: String,
    var isTemplate: Boolean, // If true then this task with repetition that is being copied on repeating days
    val duration: Long, // how long does it last
    val priority: Int,
    val intervalDays: Int, // When does it repeat with respect to createdTime
    val fixedTime: Boolean, // Does it have fixed time or is it sorted by priority
    @TypeConverters(Converter::class)
    val createdTime: LocalDateTime,
    val color: String,
    @TypeConverters(Converter::class)
    var estimatedStartTime: LocalDateTime, // Assigned during task sorting. The planned start of task
    var timeLeft: Long, // The task progress. How much time is left
    @TypeConverters(Converter::class)
    var startTime: LocalDateTime, // Helps with accuracy of timeLeft computation
    var timeLeftOnStart: Long, // Task progress. How much time was left after task start
    var isRunning: Boolean,
    var isDetailVisible: Boolean, // UI
    @TypeConverters(Converter::class)
    var status: TaskStatus,
): Parcelable {

    fun isOnToday(targetDay: LocalDate): Boolean{

        if (createdTime.isAfter(targetDay.plusDays(1).atStartOfDay())){
            return false
        }
        if (intervalDays == -1){
            return createdTime.dayOfMonth == targetDay.dayOfMonth
        }
        if (intervalDays == 0){
            return createdTime.toLocalDate().isEqual(LocalDate.now())
        }
        return (ChronoUnit.DAYS.between(createdTime.toLocalDate(), targetDay) % intervalDays) == 0L
    }

    fun getOldTasks(targetDay: LocalDate, threshold: Long): List<Task> {
        if (intervalDays < 1) return emptyList()

        val start = ChronoUnit.DAYS.between(createdTime.toLocalDate(), targetDay) % intervalDays
        val tasks = mutableListOf<Task>()

        for (day in start..threshold step intervalDays.toLong()) {
            tasks.add(this.copy(
                createdTime = LocalDateTime.of(targetDay.minusDays(day), createdTime.toLocalTime()),
                status = TaskStatus.REMAINING,
                isTemplate = false
            ))
        }

        return tasks
    }


    private fun compareTask(other: Task): Boolean {
        return (other.id == id) && (other.createdTime == createdTime) && (other.isTemplate == isTemplate)
    }

    override fun equals(other: Any?): Boolean {
        if (other is Task) return compareTask(other)
        return false
    }

    override fun hashCode(): Int {
        return 42 * id.hashCode() + 42 * createdTime.hashCode() + 42 * isTemplate.hashCode()
    }
}