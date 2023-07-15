package com.example.timemanager

import kotlinx.coroutines.delay
import kotlin.time.Duration

class Delay() {

    suspend fun delayToNextSecond(offsetMillis: Long = 0) {
        delay(1000 - (System.currentTimeMillis() + offsetMillis) % 1000)
    }

}