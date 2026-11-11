package io.github.gambitly.ui.classics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import io.github.gambitly.chess.ChessGame
import io.github.gambitly.chess.Position
import io.github.gambitly.data.Classic
import io.github.gambitly.data.ProgressKind
import io.github.gambitly.data.record
import io.github.gambitly.gambitly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClassicsListUi(
    val games: List<Classic> = emptyList(),
    /** Best "moves matched" percentage per game id, for games played to the end. */
    val best: Map<Int, Int> = emptyMap(),
)

class ClassicsViewModel(app: Application) : AndroidViewModel(app) {
    private val games = MutableStateFlow<List<Classic>>(emptyList())

    val ui: StateFlow<ClassicsListUi> = app.gambitly.progress.observeKind(ProgressKind.CLASSIC)
        .map { rows -> rows.mapNotNull { row -> row.itemId.toIntOrNull()?.let { it to row.bestPercent } }.toMap() }
        .let { best ->
            kotlinx.coroutines.flow.combine(games, best) { list, scores -> ClassicsListUi(list, scores) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClassicsListUi())

    init {
        viewModelScope.launch { games.value = app.gambitly.content.classics() }
    }
}

data class ClassicTrainerUi(
    val game: Classic? = null,
    val position: Position = ChessGame().position(),
    val userSide: Side = Side.WHITE,
    /** Guesses that matched the move actually played. */
    val matched: Int = 0,
    /** Guesses made so far. */
    val guessed: Int = 0,
    val finished: Boolean = false,
    /** Whether the last guess matched the game, or null before the first guess. */
    val lastGuessHit: Boolean? = null,
    /** The move actually played at the last guess, in SAN. */
    val lastSan: String? = null,
    /** True while moves are being played out and input is ignored. */
    val busy: Boolean = false,
)

/**
 * "Guess the move": the user gets one guess for each of their side's moves, then
 * the move actually played is made either way and the game continues.
 */
class ClassicTrainerViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val gambitly = app.gambitly
    private val classicId: Int = checkNotNull(handle["id"])

    private val _ui = MutableStateFlow(ClassicTrainerUi())
    val ui: StateFlow<ClassicTrainerUi> = _ui.asStateFlow()

    private var moves: List<String> = emptyList()
    private var game = ChessGame()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val classic = gambitly.content.classic(classicId) ?: return@launch
            moves = classic.sanMoves
            _ui.update { it.copy(game = classic) }
            restart(if (classic.hero == "b") Side.BLACK else Side.WHITE)
        }
    }

    fun restart(side: Side = _ui.value.userSide) {
        job?.cancel()
        game = ChessGame()
        _ui.update { ClassicTrainerUi(game = it.game, position = game.position(), userSide = side, busy = true) }
        job = viewModelScope.launch { advance() }
    }

    fun onMove(move: Move) {
        val state = _ui.value
        if (state.busy || state.finished || game.sideToMove != state.userSide) return
        val san = moves.getOrNull(game.moveCount) ?: return
        val actual = game.sanToMove(san) ?: return
        val hit = move == actual
        game.play(actual)
        _ui.update {
            it.copy(
                position = game.position(),
                matched = it.matched + if (hit) 1 else 0,
                guessed = it.guessed + 1,
                lastGuessHit = hit,
                lastSan = san,
                busy = true,
            )
        }
        job = viewModelScope.launch {
            // Leave a missed move on screen a little longer so it can be taken in.
            if (!hit) delay(MISS_PAUSE_MS)
            advance()
        }
    }

    /** Plays the other side's moves until it is the user's turn or the game ends. */
    private suspend fun advance() {
        while (game.moveCount < moves.size && game.sideToMove != _ui.value.userSide) {
            delay(REPLY_DELAY_MS)
            game.playSan(moves[game.moveCount])
            _ui.update { it.copy(position = game.position()) }
        }
        if (game.moveCount >= moves.size) {
            val state = _ui.value
            val percent = if (state.guessed == 0) 0 else state.matched * 100 / state.guessed
            _ui.update { it.copy(finished = true, busy = false) }
            if (state.guessed > 0) {
                gambitly.progress.record(ProgressKind.CLASSIC, classicId.toString(), success = true, percent = percent)
            }
        } else {
            _ui.update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val REPLY_DELAY_MS = 600L
        const val MISS_PAUSE_MS = 900L
    }
}
