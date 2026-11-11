package io.github.gambitly.ui.openings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import io.github.gambitly.chess.ChessGame
import io.github.gambitly.chess.Position
import io.github.gambitly.data.Opening
import io.github.gambitly.data.ProgressKind
import io.github.gambitly.data.record
import io.github.gambitly.gambitly
import io.github.gambitly.engine.Strength
import io.github.gambitly.engine.VsEngineSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrainerMode {
    /** Guided: the move to play is named and drawn as an arrow. Nothing is scored. */
    LEARN,

    /** From memory: no help unless asked for, and a clean run marks the line as known. */
    PRACTISE,
}

enum class TrainerPhase {
    /** Following the book line. */
    BOOK,

    /** Line finished; waiting for the user to choose what next. */
    COMPLETE,

    /** Playing on against the engine from the end of the line. */
    PLAY_ON,
}

enum class Feedback { NONE, CORRECT, WRONG }

data class OpeningTrainerUi(
    val opening: Opening? = null,
    val position: Position = ChessGame().position(),
    val userSide: Side = Side.WHITE,
    val mode: TrainerMode = TrainerMode.LEARN,
    val phase: TrainerPhase = TrainerPhase.BOOK,
    /** Plies of the line played so far. */
    val pliesPlayed: Int = 0,
    /** Wrong tries and requested hints in this run (practise mode only). */
    val mistakes: Int = 0,
    /** How the user's last try went. */
    val feedback: Feedback = Feedback.NONE,
    /** The book move being shown to the user, with its SAN; null when they must recall it. */
    val shownMove: Move? = null,
    val shownSan: String? = null,
    val thinking: Boolean = false,
    val engineFailed: Boolean = false,
    val levelIndex: Int = 3,
)

class OpeningTrainerViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val gambitly = app.gambitly
    private val openingId: Int = checkNotNull(handle["id"])

    private val _ui = MutableStateFlow(OpeningTrainerUi())
    val ui: StateFlow<OpeningTrainerUi> = _ui.asStateFlow()

    private var line: List<String> = emptyList()
    private var game = ChessGame()
    private var missesHere = 0
    private var userMoved = false
    private var bookJob: Job? = null
    private var session: VsEngineSession? = null
    private var sessionJob: Job? = null

    init {
        viewModelScope.launch {
            val opening = gambitly.content.opening(openingId) ?: return@launch
            line = opening.sanMoves
            // Lines the user has already played cleanly open straight in practise mode.
            val known = (gambitly.progress.find(ProgressKind.OPENING, openingId.toString())?.completions ?: 0) > 0
            _ui.update { it.copy(opening = opening) }
            restart(Side.WHITE, if (known) TrainerMode.PRACTISE else TrainerMode.LEARN)
        }
    }

    /** Starts the line again from move one. */
    fun restart(side: Side = _ui.value.userSide, mode: TrainerMode = _ui.value.mode) {
        bookJob?.cancel()
        sessionJob?.cancel()
        session?.stop()
        session = null
        game = ChessGame()
        missesHere = 0
        userMoved = false
        _ui.update {
            OpeningTrainerUi(
                opening = it.opening, position = game.position(), userSide = side, mode = mode, levelIndex = it.levelIndex,
            )
        }
        advance()
    }

    fun onMove(move: Move) {
        val state = _ui.value
        when (state.phase) {
            TrainerPhase.PLAY_ON -> session?.userMove(move)
            TrainerPhase.COMPLETE -> Unit
            TrainerPhase.BOOK -> {
                if (game.sideToMove != state.userSide) return
                val san = line.getOrNull(game.moveCount) ?: return
                val expected = game.sanToMove(san) ?: return
                if (move == expected) {
                    game.play(move)
                    missesHere = 0
                    userMoved = true
                    _ui.update {
                        it.copy(
                            position = game.position(), pliesPlayed = game.moveCount,
                            feedback = Feedback.CORRECT, shownMove = null, shownSan = null,
                        )
                    }
                    advance()
                } else if (state.mode == TrainerMode.LEARN) {
                    // The arrow is already showing; just say it was a different move.
                    _ui.update { it.copy(feedback = Feedback.WRONG) }
                } else {
                    missesHere++
                    val reveal = missesHere >= 2
                    _ui.update {
                        it.copy(
                            mistakes = it.mistakes + 1,
                            feedback = Feedback.WRONG,
                            shownMove = if (reveal) expected else null,
                            shownSan = if (reveal) san else null,
                        )
                    }
                }
            }
        }
    }

    /** Reveals the book move in practise mode; it counts as a slip, so the line is not marked as known. */
    fun showMove() {
        val state = _ui.value
        if (state.phase != TrainerPhase.BOOK || game.sideToMove != state.userSide || state.shownMove != null) return
        val san = line.getOrNull(game.moveCount) ?: return
        val expected = game.sanToMove(san) ?: return
        missesHere = 2
        _ui.update {
            it.copy(mistakes = it.mistakes + 1, feedback = Feedback.NONE, shownMove = expected, shownSan = san)
        }
    }

    /** Continues from the end of the line as a normal game against the engine. */
    fun playOn(levelIndex: Int = _ui.value.levelIndex) {
        if (_ui.value.phase == TrainerPhase.BOOK) return
        sessionJob?.cancel()
        session?.stop()
        val started = VsEngineSession(viewModelScope, gambitly.engine, game, _ui.value.userSide, Strength.LEVELS[levelIndex])
        session = started
        _ui.update {
            it.copy(phase = TrainerPhase.PLAY_ON, levelIndex = levelIndex, feedback = Feedback.NONE, shownMove = null, shownSan = null)
        }
        sessionJob = viewModelScope.launch {
            started.state.collect { s -> _ui.update { it.copy(position = s.position, thinking = s.thinking, engineFailed = s.engineFailed) } }
        }
    }

    fun undo() {
        session?.undo()
    }

    /** Plays the opponent's book moves until it is the user's turn or the line ends. */
    private fun advance() {
        bookJob = viewModelScope.launch {
            while (game.moveCount < line.size && game.sideToMove != _ui.value.userSide) {
                delay(REPLY_DELAY_MS)
                game.playSan(line[game.moveCount])
                _ui.update { it.copy(position = game.position(), pliesPlayed = game.moveCount) }
            }
            if (game.moveCount >= line.size) {
                val state = _ui.value
                _ui.update { it.copy(phase = TrainerPhase.COMPLETE, shownMove = null, shownSan = null) }
                if (userMoved && state.mode == TrainerMode.PRACTISE) {
                    gambitly.progress.record(ProgressKind.OPENING, openingId.toString(), success = state.mistakes == 0)
                }
            } else if (_ui.value.mode == TrainerMode.LEARN) {
                // Guided mode always shows the move to play.
                val san = line[game.moveCount]
                _ui.update { it.copy(shownMove = game.sanToMove(san), shownSan = san) }
            }
        }
    }

    private companion object {
        const val REPLY_DELAY_MS = 600L
    }
}
