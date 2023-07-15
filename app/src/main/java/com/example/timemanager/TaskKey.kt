package com.example.timemanager

import java.time.LocalDateTime

data class TaskKey(val id: Long, val createdTime: LocalDateTime, val isTemplate: Boolean)
