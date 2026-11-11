package io.github.gambitly.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

enum class GameStatus { ONGOING, WHITE_WINS, BLACK_WINS, STALEMATE, DRAW }

/** Immutable snapshot of a game, safe to hand to Compose. */
data class Position(
    val fen: String,
    /** 64 entries indexed by [Square.ordinal] (A1 = 0, H8 = 63). */
    val pieces: List<Piece>,
    val sideToMove: Side,
    val legalMoves: List<Move>,
    val lastMove: Move?,
    /** Square of the king that is in check, if any. */
    val checkSquare: Square?,
    val status: GameStatus,
    val sanHistory: List<String>,
) {
    fun pieceAt(square: Square): Piece = pieces[square.ordinal]
}

/** Mutable game wrapper over chesslib. Not thread-safe; confine to one coroutine context. */
class ChessGame(val startFen: String = START_FEN) {
    private val board = Board().apply { loadFromFen(startFen) }
    private val played = MoveList(startFen)

    val sideToMove: Side get() = board.sideToMove
    val fen: String get() = board.fen
    val moveCount: Int get() = played.size

    /** Moves played so far in UCI notation, for the engine's `position ... moves` command. */
    fun uciMoves(): List<String> = played.map { it.toUci() }

    fun play(move: Move): Boolean {
        if (move !in board.legalMoves()) return false
        board.doMove(move)
        played.add(move)
        return true
    }

    fun playUci(uci: String): Boolean = play(Move(uci, board.sideToMove))

    fun playSan(san: String): Boolean {
        val move = sanToMove(san) ?: return false
        return play(move)
    }

    /** Resolves a SAN move in the current position without playing it. */
    fun sanToMove(san: String): Move? = try {
        MoveList(board.fen).apply { addSanMove(san, true, true) }.last
    } catch (e: Exception) {
        null
    }

    fun undo(): Boolean {
        if (played.isEmpty()) return false
        board.undoMove()
        played.removeLast()
        return true
    }

    fun position(): Position {
        val status = when {
            board.isMated -> if (board.sideToMove == Side.WHITE) GameStatus.BLACK_WINS else GameStatus.WHITE_WINS
            board.isStaleMate -> GameStatus.STALEMATE
            board.isDraw -> GameStatus.DRAW
            else -> GameStatus.ONGOING
        }
        return Position(
            fen = board.fen,
            pieces = board.boardToArray().take(64),
            sideToMove = board.sideToMove,
            legalMoves = if (status == GameStatus.ONGOING) board.legalMoves() else emptyList(),
            lastMove = played.lastOrNull(),
            checkSquare = if (board.isKingAttacked) board.getKingSquare(board.sideToMove) else null,
            status = status,
            sanHistory = played.toSanArray().toList(),
        )
    }
}

fun Move.toUci(): String {
    val promo = if (promotion == Piece.NONE) "" else promotion.fenSymbol.lowercase()
    return from.value().lowercase() + to.value().lowercase() + promo
}
