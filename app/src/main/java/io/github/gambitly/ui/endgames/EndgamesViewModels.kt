package io.github.gambitly.ui.endgames

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import io.github.gambitly.chess.ChessGame
import io.github.gambitly.chess.GameStatus
import io.github.gambitly.chess.Position
import io.github.gambitly.data.Endgame
import io.github.gambitly.data.ProgressKind
import io.github.gambitly.data.record
import io.github.gambitly.gambitly
import io.github.gambitly.engine.Strength
import io.github.gambitly.engine.VsEngineSession
import io.github.gambitly.engine.VsEngineState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EndgamesListUi(
    val endgames: List<Endgame> = emptyList(),
    /** Ids of positions the user has completed at least once. */
    val completed: Set<Int> = emptySet(),
)

class EndgamesViewModel(app: Application) : AndroidViewModel(app) {
    private val endgames = MutableStateFlow<List<Endgame>>(emptyList())

    private val completed = app.gambitly.progress.observeKind(ProgressKind.ENDGAME)
        .map { rows -> rows.filter { it.completions > 0 }.mapNotNull { it.itemId.toIntOrNull() }.toSet() }

    val ui: StateFlow<EndgamesListUi> = combine(endgames, completed) { list, done -> EndgamesListUi(list, done) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EndgamesListUi())

    init {
        viewModelScope.launch { endgames.value = app.gambitly.content.endgames() }
    }
}

enum class EndgameOutcome { IN_PROGRESS, SUCCESS, FAILED }

data class EndgameTrainerUi(
    val endgame: Endgame? = null,
    val position: Position = ChessGame().position(),
    val userSide: Side = Side.WHITE,
    val thinking: Boolean = false,
    val engineFailed: Boolean = false,
    /** The user's own moves made so far. */
    val movesUsed: Int = 0,
    val outcome: EndgameOutcome = EndgameOutcome.IN_PROGRESS,
    val message: String? = null,
)

/** The user plays a set position against the engine at full strength until the goal is met or missed. */
class EndgameTrainerViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val gambitly = app.gambitly
    private val endgameId: Int = checkNotNull(handle["id"])

    private val _ui = MutableStateFlow(EndgameTrainerUi())
    val ui: StateFlow<EndgameTrainerUi> = _ui.asStateFlow()

    private var session: VsEngineSession? = null
    private var sessionJob: Job? = null
    private var userMovesFirst = true
    private var startingQueens = 0

    init {
        viewModelScope.launch {
            val endgame = gambitly.content.endgame(endgameId) ?: return@launch
            _ui.update { it.copy(endgame = endgame) }
            restart()
        }
    }

    fun restart() {
        val endgame = _ui.value.endgame ?: return
        sessionJob?.cancel()
        session?.stop()
        val game = ChessGame(endgame.fen)
        val userSide = if (endgame.userSide == "b") Side.BLACK else Side.WHITE
        userMovesFirst = game.sideToMove == userSide
        startingQueens = queens(game.position(), userSide)
        val started = VsEngineSession(viewModelScope, gambitly.engine, game, userSide, DEFENDER)
        session = started
        _ui.update { EndgameTrainerUi(endgame = endgame, position = game.position(), userSide = userSide) }
        sessionJob = viewModelScope.launch { started.state.collect(::onState) }
    }

    fun onMove(move: Move) {
        if (_ui.value.outcome == EndgameOutcome.IN_PROGRESS) session?.userMove(move)
    }

    fun undo() {
        if (_ui.value.outcome == EndgameOutcome.IN_PROGRESS) session?.undo()
    }

    private fun onState(state: VsEngineState) {
        val current = _ui.value
        val endgame = current.endgame ?: return
        if (current.outcome != EndgameOutcome.IN_PROGRESS) return
        val position = state.position
        val plies = position.sanHistory.size
        val movesUsed = if (userMovesFirst) (plies + 1) / 2 else plies / 2
        val (outcome, message) = if (state.thinking || state.engineFailed) {
            EndgameOutcome.IN_PROGRESS to current.message
        } else {
            judge(endgame, state, movesUsed)
        }
        _ui.update {
            it.copy(position = position, thinking = state.thinking, engineFailed = state.engineFailed, movesUsed = movesUsed, outcome = outcome, message = message)
        }
        if (outcome != EndgameOutcome.IN_PROGRESS) {
            viewModelScope.launch {
                gambitly.progress.record(ProgressKind.ENDGAME, endgameId.toString(), success = outcome == EndgameOutcome.SUCCESS)
            }
        }
    }

    /** Decides whether the goal has been reached or missed, once the engine has replied. */
    private fun judge(endgame: Endgame, state: VsEngineState, movesUsed: Int): Pair<EndgameOutcome, String?> {
        val position = state.position
        val userSide = _ui.value.userSide
        val userWon = position.status == (if (userSide == Side.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS)
        val userLost = position.status == (if (userSide == Side.WHITE) GameStatus.BLACK_WINS else GameStatus.WHITE_WINS)
        val drawn = position.status == GameStatus.DRAW || position.status == GameStatus.STALEMATE
        val outOfMoves = movesUsed >= endgame.moveLimit && position.sideToMove == userSide
        // The engine's own evaluation: positive means it is winning.
        val engineWinning = state.lastReply?.let { (it.mateIn ?: 0) > 0 || (it.scoreCp ?: 0) >= LOST_THRESHOLD_CP } ?: false

        return when (endgame.goal) {
            "draw" -> when {
                userLost -> EndgameOutcome.FAILED to "Checkmate — the draw slipped away"
                drawn -> EndgameOutcome.SUCCESS to "Draw — well defended"
                userWon -> EndgameOutcome.SUCCESS to "You even won it"
                engineWinning -> EndgameOutcome.FAILED to "The position is now lost — try again"
                outOfMoves -> EndgameOutcome.SUCCESS to "Held for ${endgame.moveLimit} moves — the draw is secure"
                else -> EndgameOutcome.IN_PROGRESS to null
            }
            "promote" -> when {
                userWon -> EndgameOutcome.SUCCESS to "Checkmate"
                queens(position, userSide) > startingQueens && position.sideToMove == userSide ->
                    EndgameOutcome.SUCCESS to "Promoted safely — the rest is technique"
                drawn -> EndgameOutcome.FAILED to "Drawn — the win slipped away"
                userLost -> EndgameOutcome.FAILED to "Checkmate"
                outOfMoves -> EndgameOutcome.FAILED to "Out of moves — try again"
                else -> EndgameOutcome.IN_PROGRESS to null
            }
            else -> when { // "mate"
                userWon -> EndgameOutcome.SUCCESS to "Checkmate in $movesUsed moves"
                drawn -> EndgameOutcome.FAILED to
                    if (position.status == GameStatus.STALEMATE) "Stalemate — that is only a draw" else "Drawn — the win slipped away"
                userLost -> EndgameOutcome.FAILED to "Checkmate"
                outOfMoves -> EndgameOutcome.FAILED to "Out of moves — try again"
                else -> EndgameOutcome.IN_PROGRESS to null
            }
        }
    }

    private fun queens(position: Position, side: Side): Int =
        position.pieces.count { it == Piece.make(side, PieceType.QUEEN) }

    private companion object {
        /** Full strength with a short think: these positions have few pieces and are searched deeply at once. */
        val DEFENDER = Strength("Endgame", moveTimeMs = 500)
        const val LOST_THRESHOLD_CP = 300
    }
}
