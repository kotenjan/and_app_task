package com.example.timemanager

import kotlin.math.roundToInt
import kotlin.random.Random

class ColorPicker {

    fun darkenColor(hexColor: String, percentage: Int): String {
        require(hexColor.length == 7 && hexColor[0] == '#') {
            "Color should be in #RRGGBB format"
        }
        require(percentage in 0..100) {
            "Percentage should be between 0 and 100"
        }

        val r = hexColor.substring(1, 3).toInt(16)
        val g = hexColor.substring(3, 5).toInt(16)
        val b = hexColor.substring(5, 7).toInt(16)

        val factor = (100 - percentage) / 100.0

        val newR = (r * factor).roundToInt().coerceIn(0..255)
        val newG = (g * factor).roundToInt().coerceIn(0..255)
        val newB = (b * factor).roundToInt().coerceIn(0..255)

        return String.format("#%02X%02X%02X", newR, newG, newB)
    }

    fun lightenColor(hexColor: String, percentage: Int): String {

        require(hexColor.length == 7 && hexColor[0] == '#') {
            "Color should be in #RRGGBB format"
        }
        require(percentage in 0..100) {
            "Percentage should be between 0 and 100"
        }

        val r = hexColor.substring(1, 3).toInt(16)
        val g = hexColor.substring(3, 5).toInt(16)
        val b = hexColor.substring(5, 7).toInt(16)

        val factor = percentage / 100.0

        val newR = ((255 - r) * factor + r).roundToInt().coerceIn(0..255)
        val newG = ((255 - g) * factor + g).roundToInt().coerceIn(0..255)
        val newB = ((255 - b) * factor + b).roundToInt().coerceIn(0..255)

        return String.format("#%02X%02X%02X", newR, newG, newB)
    }

    fun generateColorGradient(): MutableList<String> {

        val step = Variables.COLOR_STEP
        val start = Variables.COLOR_START

        val colors: MutableList<String> = mutableListOf()

        for (r in 255 downTo start step step) {
            for (g in start..255 step step) {
                colors.add("#" + r.toString(16).padStart(2, '0') + g.toString(16).padStart(2, '0') + "00")
            }
        }

        for (g in 255 downTo start step step) {
            for (b in start..255 step step) {
                colors.add("#00" + g.toString(16).padStart(2, '0') + b.toString(16).padStart(2, '0'))
            }
        }

        for (b in 255 downTo start step step) {
            for (r in start..255 step step) {
                colors.add("#" + r.toString(16).padStart(2, '0') + "00" + b.toString(16).padStart(2, '0'))
            }
        }

        return colors
    }
}