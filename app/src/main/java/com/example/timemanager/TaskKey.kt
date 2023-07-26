package com.example.timemanager

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
@Parcelize
data class TaskKey(val id: Long, val createdTime: LocalDateTime, val isTemplate: Boolean) : Parcelable {

    private fun compareTask(other: Task): Boolean {
        return (other.id == id) && (other.createdTime == createdTime) && (other.isTemplate == isTemplate)
    }

    private fun compareKey(other: TaskKey): Boolean {
        return (other.id == id) && (other.createdTime == createdTime) && (other.isTemplate == isTemplate)
    }

    override fun equals(other: Any?): Boolean {
        if (other is Task) return compareTask(other)
        if (other is TaskKey) return compareKey(other)
        return false
    }

    override fun hashCode(): Int {
        return 42 * id.hashCode() + 42 * createdTime.hashCode() + 42 * isTemplate.hashCode()
    }
}
