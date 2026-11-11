package io.github.gambitly.chess

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessGameTest {
    @Test
    fun sanAndUciRoundTrip() {
        val game = ChessGame()
        assertTrue(game.playSan("e4"))
        assertTrue(game.playUci("e7e5"))
        assertTrue(game.playSan("Nf3"))
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), game.uciMoves())
        val pos = game.position()
        assertEquals(listOf("e4", "e5", "Nf3"), pos.sanHistory)
        assertEquals(Side.BLACK, pos.sideToMove)
        assertEquals(Piece.WHITE_KNIGHT, pos.pieceAt(Square.F3))
        assertEquals(GameStatus.ONGOING, pos.status)
    }

    @Test
    fun illegalMovesAreRejected() {
        val game = ChessGame()
        assertFalse(game.playUci("e2e5"))
        assertFalse(game.playSan("Qh5"))
        assertEquals(0, game.moveCount)
    }

    @Test
    fun foolsMateIsDetected() {
        val game = ChessGame()
        listOf("f3", "e5", "g4", "Qh4#").forEach { assertTrue(it, game.playSan(it)) }
        val pos = game.position()
        assertEquals(GameStatus.BLACK_WINS, pos.status)
        assertEquals(Square.E1, pos.checkSquare)
        assertTrue(pos.legalMoves.isEmpty())
    }

    @Test
    fun promotionUsesUciSuffix() {
        val game = ChessGame("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        assertEquals(4, game.position().legalMoves.count { it.from == Square.A7 })
        assertTrue(game.playUci("a7a8q"))
        assertEquals(listOf("a7a8q"), game.uciMoves())
        assertEquals(Piece.WHITE_QUEEN, game.position().pieceAt(Square.A8))
    }

    @Test
    fun stalemateAndUndo() {
        val game = ChessGame("7k/5Q2/8/8/8/8/8/K7 w - - 0 1")
        assertTrue(game.playUci("f7g6"))
        assertEquals(GameStatus.STALEMATE, game.position().status)
        assertTrue(game.undo())
        assertEquals(GameStatus.ONGOING, game.position().status)
        assertNull(game.position().lastMove)
    }
}
