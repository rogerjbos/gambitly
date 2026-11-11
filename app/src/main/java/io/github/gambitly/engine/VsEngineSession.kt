package io.github.gambitly.engine

import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import io.github.gambitly.chess.ChessGame
import io.github.gambitly.chess.GameStatus
import io.github.gambitly.chess.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class VsEngineState(
    val position: Position,
    val thinking: Boolean,
    /** The engine's latest reply with its evaluation (from the engine's side), or null before its first move. */
    val lastReply: EngineMove? = null,
    /** True when the engine process could not be run; its move is still owed. */
    val engineFailed: Boolean = false,
)

/**
 * A game between the user and the engine, continuing from whatever state [game]
 * is in. Shared by free play, "play on" after an opening line, and endgame practice.
 * Must be used from a single-threaded scope (the main dispatcher).
 */
class VsEngineSession(
    private val scope: CoroutineScope,
    private val engine: UciEngine,
    private val game: ChessGame,
    val userSide: Side,
    private val strength: Strength,
) {
    private val _state = MutableStateFlow(VsEngineState(game.position(), thinking = false))
    val state: StateFlow<VsEngineState> = _state.asStateFlow()
    private var engineJob: Job? = null
    private var lastReply: EngineMove? = null

    init {
        engineMoveIfDue()
    }

    fun userMove(move: Move) {
        if (_state.value.thinking || game.sideToMove != userSide) return
        if (!game.play(move)) return
        publish(thinking = false)
        engineMoveIfDue()
    }

    /** Takes back the user's last move (and the engine's reply to it). */
    fun undo() {
        engineJob?.cancel()
        if (!game.undo()) return
        if (game.sideToMove != userSide) game.undo()
        lastReply = null
        publish(thinking = false)
        engineMoveIfDue()
    }

    fun stop() {
        engineJob?.cancel()
    }

    private fun engineMoveIfDue() {
        if (game.sideToMove == userSide || game.position().status != GameStatus.ONGOING) return
        publish(thinking = true)
        engineJob = scope.launch {
            val started = System.currentTimeMillis()
            val reply = try {
                engine.bestMove(game.startFen, game.uciMoves(), strength)
            } catch (e: IOException) {
                publish(thinking = false, engineFailed = true)
                return@launch
            }
            // Shallow levels answer instantly; a short pause lets the user see their own move land.
            delay((MIN_REPLY_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            reply.uci?.let { game.playUci(it) }
            lastReply = reply
            publish(thinking = false)
        }
    }

    private fun publish(thinking: Boolean, engineFailed: Boolean = false) {
        _state.value = VsEngineState(game.position(), thinking, lastReply, engineFailed)
    }

    private companion object {
        const val MIN_REPLY_MS = 400L
    }
}
