package io.github.gambitly.data

import android.content.Context
import kotlin.math.pow
import kotlin.math.roundToInt

/** Small per-user values that do not warrant a database table. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The user's puzzle rating, on the same scale as Lichess puzzle ratings. */
    var puzzleRating: Int
        get() = prefs.getInt(KEY_PUZZLE_RATING, DEFAULT_PUZZLE_RATING)
        set(value) = prefs.edit().putInt(KEY_PUZZLE_RATING, value).apply()

    /** Index into Strength.LEVELS last chosen for free play. */
    var playLevel: Int
        get() = prefs.getInt(KEY_PLAY_LEVEL, DEFAULT_PLAY_LEVEL)
        set(value) = prefs.edit().putInt(KEY_PLAY_LEVEL, value).apply()

    /** Whether the user last chose Black in free play. */
    var playAsBlack: Boolean
        get() = prefs.getBoolean(KEY_PLAY_AS_BLACK, false)
        set(value) = prefs.edit().putBoolean(KEY_PLAY_AS_BLACK, value).apply()

    companion object {
        const val DEFAULT_PUZZLE_RATING = 1000
        const val DEFAULT_PLAY_LEVEL = 2
        private const val KEY_PUZZLE_RATING = "puzzle_rating"
        private const val KEY_PLAY_LEVEL = "play_level"
        private const val KEY_PLAY_AS_BLACK = "play_as_black"
        private const val K_FACTOR = 32

        /** Elo update for one puzzle attempt, kept within the range of puzzles the app ships. */
        fun updatedRating(userRating: Int, puzzleRating: Int, solved: Boolean): Int {
            val expected = 1.0 / (1.0 + 10.0.pow((puzzleRating - userRating) / 400.0))
            val change = K_FACTOR * ((if (solved) 1.0 else 0.0) - expected)
            return (userRating + change).roundToInt().coerceIn(600, 2400)
        }
    }
}
