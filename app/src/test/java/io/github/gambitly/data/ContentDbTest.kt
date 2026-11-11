package io.github.gambitly.data

import io.github.gambitly.chess.ChessGame
import io.github.gambitly.chess.GameStatus
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Replays every item in the shipped content database through the rules engine. */
class ContentDbTest {
    companion object {
        private lateinit var db: Connection

        @BeforeClass
        @JvmStatic
        fun open() {
            val file = File("src/main/assets/content.db")
            assertTrue("run tools/build_content.py first: ${file.absolutePath}", file.exists())
            db = DriverManager.getConnection("jdbc:sqlite:${file.path}")
        }

        @AfterClass
        @JvmStatic
        fun close() = db.close()
    }

    private fun <T> query(sql: String, row: (java.sql.ResultSet) -> T): List<T> =
        db.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(row(rs)) }
            }
        }

    @Test
    fun everyOpeningLineIsLegal() {
        val lines = query("SELECT name, moves, plies FROM openings") {
            Triple(it.getString(1), it.getString(2), it.getInt(3))
        }
        assertTrue(lines.size > 3000)
        for ((name, moves, plies) in lines) {
            val game = ChessGame()
            val sans = moves.split(' ')
            assertEquals(name, plies, sans.size)
            sans.forEach { assertTrue("$name: $it in '$moves'", game.playSan(it)) }
        }
    }

    @Test
    fun everyPuzzleSolutionIsLegal() {
        data class Row(val id: String, val fen: String, val moves: String, val rating: Int, val themes: String)
        val puzzles = query("SELECT id, fen, moves, rating, themes FROM puzzles") {
            Row(it.getString(1), it.getString(2), it.getString(3), it.getInt(4), it.getString(5))
        }
        assertTrue(puzzles.size > 4000)
        for (p in puzzles) {
            val game = ChessGame(p.fen)
            val moves = p.moves.split(' ')
            // Set-up move plus an odd number of solution plies, so the solver moves last.
            assertEquals("${p.id}: move count", 0, moves.size % 2)
            moves.forEach { assertTrue("${p.id}: $it", game.playUci(it)) }
            assertTrue(p.id, p.themes.startsWith(" ") && p.themes.endsWith(" "))
            if (" mate " in p.themes) {
                assertNotEquals("${p.id} should end in mate", GameStatus.ONGOING, game.position().status)
            }
        }
    }

    @Test
    fun everyClassicGameIsLegalAndMatchesItsResult() {
        data class Row(val title: String, val moves: String, val result: String, val hero: String)
        val games = query("SELECT title, moves, result, hero FROM classics") {
            Row(it.getString(1), it.getString(2), it.getString(3), it.getString(4))
        }
        assertEquals(22, games.size)
        for (g in games) {
            val game = ChessGame()
            g.moves.split(' ').forEach { assertTrue("${g.title}: $it", game.playSan(it)) }
            assertTrue(g.title, g.hero == "w" || g.hero == "b")
            // The guessed side is the winner.
            assertEquals(g.title, if (g.hero == "w") "1-0" else "0-1", g.result)
            when (game.position().status) {
                GameStatus.WHITE_WINS -> assertEquals(g.title, "1-0", g.result)
                GameStatus.BLACK_WINS -> assertEquals(g.title, "0-1", g.result)
                else -> Unit // most games end by resignation
            }
        }
    }

    @Test
    fun endgamesAreWellFormed() {
        data class Row(val title: String, val fen: String, val side: String, val goal: String, val limit: Int)
        val endgames = query("SELECT title, fen, userSide, goal, moveLimit FROM endgames") {
            Row(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5))
        }
        assertTrue(endgames.isNotEmpty())
        for (e in endgames) {
            val position = ChessGame(e.fen).position()
            assertEquals(e.title, GameStatus.ONGOING, position.status)
            assertTrue(e.title, position.legalMoves.isNotEmpty())
            assertTrue(e.title, e.side in setOf("w", "b"))
            assertTrue(e.title, e.goal in setOf("mate", "promote", "draw"))
            assertTrue(e.title, e.limit > 0)
        }
    }
}
