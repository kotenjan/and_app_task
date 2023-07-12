package com.example.timemanager

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks")
    suspend fun getTasks(): List<Task>

    @Query("UPDATE tasks SET isRunning = :isRunning WHERE id = :id AND createdTime = :createdTime AND isTemplate = :isTemplate")
    suspend fun updateRunningState(id: Long, createdTime: LocalDateTime, isTemplate: Boolean, isRunning: Boolean)

    @Query("UPDATE tasks SET timeLeft = timeLeft - 1 WHERE isRunning = 1 AND timeLeft > 0 AND isTemplate = 0")
    suspend fun decrementTimeLeftForRunningTasks()

    @Query("UPDATE tasks SET timeLeft = MAX(0, timeLeft - :value) WHERE id = :id AND createdTime = :createdTime AND isTemplate = :isTemplate")
    suspend fun decrementTimeLeft(id: Long, createdTime: LocalDateTime, isTemplate: Boolean, value: Int)

    @Query("UPDATE tasks SET timeLeft = MIN(duration, timeLeft + :value) WHERE id = :id AND createdTime = :createdTime AND isTemplate = :isTemplate")
    suspend fun incrementTimeLeft(id: Long, createdTime: LocalDateTime, isTemplate: Boolean, value: Int)

    @Query("UPDATE tasks SET timeLeft = :value WHERE id = :id AND createdTime = :createdTime AND isTemplate = :isTemplate")
    suspend fun setTimeLeft(id: Long, createdTime: LocalDateTime, isTemplate: Boolean, value: Long)

    @Query("UPDATE tasks SET isDetailVisible = :value WHERE id = :id AND createdTime = :createdTime AND isTemplate = :isTemplate")
    suspend fun setDetail(id: Long, createdTime: LocalDateTime, isTemplate: Boolean, value: Boolean)

    @Query("UPDATE tasks SET isRunning = 0, status = 'FINISHED' WHERE timeLeft <= 0 AND isTemplate = 0")
    suspend fun finishTasksWithNoTimeLeft()

    @Query("SELECT COUNT(*) FROM tasks WHERE isRunning = 1 AND isTemplate = 0")
    suspend fun countRunningTasks(): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun purge(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun prune()

    @Transaction
    suspend fun updateTaskTimes() {
        finishTasksWithNoTimeLeft()
        decrementTimeLeftForRunningTasks()
    }
}