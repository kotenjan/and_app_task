package com.example.timemanager

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDateTime

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Delete
    fun delete(tasks: List<Task>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET isRunning = 0, timeLeft = :timeLeft WHERE id = :id AND createdTime = :createdTime AND isTemplate = 0")
    suspend fun stopTaskFromNotification(id: Long, createdTime: LocalDateTime, timeLeft: Long)

    @Query("UPDATE tasks SET status = 'FINISHED', isRunning = 0, timeLeft = 0 WHERE id = :id AND createdTime = :createdTime AND isTemplate = 0")
    suspend fun finishTaskFromNotification(id: Long, createdTime: LocalDateTime)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteAllWithId(taskId: Long)

    @Query("SELECT * FROM tasks")
    suspend fun getTasks(): List<Task>
}