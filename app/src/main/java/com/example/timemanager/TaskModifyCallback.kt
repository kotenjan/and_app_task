package com.example.timemanager

interface TaskModifyCallback {
    fun onModifyTask(task: Task)

    fun onCopyTask(task: Task)
}