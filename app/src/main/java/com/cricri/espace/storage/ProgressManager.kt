package com.cricri.espace.storage

import android.content.Context

class ProgressManager(context: Context) {

    private val prefs = context.getSharedPreferences("cricri_espace_progress", Context.MODE_PRIVATE)

    fun getUnlockedLevel(): Int {
        return prefs.getInt("unlocked_level", 1).coerceIn(1, 99)
    }

    fun getStars(level: Int): Int {
        return prefs.getInt("stars_$level", 0).coerceIn(0, 3)
    }

    fun getBestScore(level: Int): Int {
        return prefs.getInt("score_$level", 0)
    }

    fun getTotalStars(): Int {
        var total = 0
        for (i in 1..99) {
            total += getStars(i)
        }
        return total
    }

    fun recordLevelWin(level: Int, score: Int, stars: Int) {
        val editor = prefs.edit()
        val prevStars = getStars(level)
        if (stars > prevStars) {
            editor.putInt("stars_$level", stars.coerceIn(1, 3))
        }
        val prevScore = getBestScore(level)
        if (score > prevScore) {
            editor.putInt("score_$level", score)
        }
        val currentUnlocked = getUnlockedLevel()
        if (level >= currentUnlocked && level < 99) {
            editor.putInt("unlocked_level", level + 1)
        }
        editor.apply()
    }
}
