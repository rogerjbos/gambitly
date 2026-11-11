package io.github.gambitly.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.gambitly.chess.ChessGame
import io.github.gambitly.chess.START_FEN
import io.github.gambitly.gambitly
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UciEngineTest {
    private val engine = InstrumentationRegistry.getInstrumentation().targetContext.gambitly.engine

    @Test
    fun returnsLegalMoveFromStartPositionAtEveryLevel() = runBlocking {
        for (level in Strength.LEVELS) {
            val started = System.currentTimeMillis()
            val result = engine.bestMove(START_FEN, listOf("e2e4"), level)
            val elapsed = System.currentTimeMillis() - started
            val game = ChessGame().apply { playUci("e2e4") }
            assertNotNull(level.label, result.uci)
            assertTrue("${level.label}: ${result.uci} is illegal", game.playUci(result.uci!!))
            assertTrue("${level.label} took $elapsed ms", elapsed < level.moveTimeMs + 3000)
        }
    }

    @Test
    fun findsMateInOne() = runBlocking {
        // Back-rank mate: Ra8#.
        val result = engine.bestMove("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", emptyList(), Strength.MAX)
        assertEquals("a1a8", result.uci)
        assertEquals(1, result.mateIn)
    }

    @Test
    fun reportsNoMoveWhenMated() = runBlocking {
        val result = engine.bestMove("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1", emptyList(), Strength.MAX)
        assertNull(result.uci)
    }
}
