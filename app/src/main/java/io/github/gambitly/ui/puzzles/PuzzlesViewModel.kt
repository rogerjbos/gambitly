package io.github.gambitly.ui.puzzles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import io.github.gambitly.chess.ChessGame
import io.github.gambitly.chess.GameStatus
import io.github.gambitly.chess.Position
import io.github.gambitly.chess.toUci
import io.github.gambitly.data.ProgressKind
import io.github.gambitly.data.Puzzle
import io.github.gambitly.data.Settings
import io.github.gambitly.data.record
import io.github.gambitly.gambitly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lichess theme keys offered as filters, with display names. The empty key means any theme. */
val PUZZLE_THEMES: List<Pair<String, String>> = listOf(
    "" to "All themes",
    "mateIn1" to "Mate in 1",
    "mateIn2" to "Mate in 2",
    "mateIn3" to "Mate in 3",
    "backRankMate" to "Back-rank mate",
    "smotheredMate" to "Smothered mate",
    "fork" to "Fork",
    "pin" to "Pin",
    "skewer" to "Skewer",
    "discoveredAttack" to "Discovered attack",
    "doubleCheck" to "Double check",
    "hangingPiece" to "Hanging piece",
    "trappedPiece" to "Trapped piece",
    "deflection" to "Deflection",
    "attraction" to "Attraction",
    "sacrifice" to "Sacrifice",
    "clearance" to "Clearance",
    "intermezzo" to "In-between move",
    "quietMove" to "Quiet move",
    "xRayAttack" to "X-ray attack",
    "zugzwang" to "Zugzwang",
    "promotion" to "Promotion",
    "rookEndgame" to "Rook endgame",
    "pawnEndgame" to "Pawn endgame",
)

enum class PuzzlePhase { LOADING, SOLVING, SOLVED, NONE_FOUND }

/** How the user's last action on the current puzzle went. */
enum class PuzzleFeedback { NONE, CORRECT, WRONG, HINT }

data class PuzzleUi(
    val phase: PuzzlePhase = PuzzlePhase.LOADING,
    val puzzle: Puzzle? = null,
    val position: Position = ChessGame().position(),
    val userSide: Side = Side.WHITE,
    val theme: String = "",
    val userRating: Int = Settings.DEFAULT_PUZZLE_RATING,
    /** Rating change from the current puzzle once it has been decided, else null. */
    val ratingChange: Int? = null,
    val feedback: PuzzleFeedback = PuzzleFeedback.NONE,
    val hint: Set<Square> = emptySet(),
    /** True while moves are being played out and input is ignored. */
    val busy: Boolean = false,
    /** True when the puzzle ended by showing the answer rather than by the user solving it. */
    val solutionShown: Boolean = false,
)

class PuzzlesViewModel(app: Application) : AndroidViewModel(app) {
    private val gambitly = app.gambitly
    private val settings = Settings(app)

    private val _ui = MutableStateFlow(PuzzleUi(userRating = settings.puzzleRating))
    val ui: StateFlow<PuzzleUi> = _ui.asStateFlow()

    private var game = ChessGame()
    private var solution: List<String> = emptyList()
    private var index = 0
    /** Whether this puzzle has already counted towards the rating (first attempt only). */
    private var rated = false
    private var job: Job? = null

    init {
        next()
    }

    fun setTheme(theme: String) {
        _ui.update { it.copy(theme = theme) }
        next()
    }

    /** Loads another puzzle near the user's rating. */
    fun next() {
        job?.cancel()
        val previousId = _ui.value.puzzle?.id ?: ""
        val theme = _ui.value.theme
        _ui.update {
            it.copy(
                phase = PuzzlePhase.LOADING, feedback = PuzzleFeedback.NONE, hint = emptySet(),
                ratingChange = null, solutionShown = false,
            )
        }
        job = viewModelScope.launch {
            val rating = settings.puzzleRating
            // Widen the window until something matches; rare themes have few puzzles at some levels.
            val puzzle = RATING_WINDOWS.firstNotNullOfOrNull { window ->
                gambitly.content.randomPuzzle(theme, rating - window, rating + window, previousId)
            }
            if (puzzle == null) {
                _ui.update { it.copy(phase = PuzzlePhase.NONE_FOUND, puzzle = null) }
                return@launch
            }
            game = ChessGame(puzzle.fen)
            solution = puzzle.uciMoves
            index = 0
            rated = false
            // The solver has the side that does NOT move first: the first move is the opponent's.
            val userSide = if (game.sideToMove == Side.WHITE) Side.BLACK else Side.WHITE
            _ui.update {
                it.copy(
                    phase = PuzzlePhase.SOLVING, puzzle = puzzle, position = game.position(),
                    userSide = userSide, userRating = rating, busy = true,
                )
            }
            playOpponentMove()
        }
    }

    fun onMove(move: Move) {
        val state = _ui.value
        if (state.phase != PuzzlePhase.SOLVING || state.busy || game.sideToMove != state.userSide) return
        val correct = move.toUci() == solution[index] || deliversMate(move)
        if (!correct) {
            settle(solved = false)
            _ui.update { it.copy(feedback = PuzzleFeedback.WRONG, hint = emptySet()) }
            return
        }
        game.play(move)
        index++
        if (index >= solution.size || game.position().status != GameStatus.ONGOING) {
            settle(solved = true)
            _ui.update {
                it.copy(phase = PuzzlePhase.SOLVED, position = game.position(), feedback = PuzzleFeedback.CORRECT, hint = emptySet())
            }
        } else {
            _ui.update { it.copy(position = game.position(), feedback = PuzzleFeedback.CORRECT, hint = emptySet(), busy = true) }
            job = viewModelScope.launch { playOpponentMove() }
        }
    }

    /** Highlights the piece to move; asking for help counts as a miss. */
    fun showHint() {
        val state = _ui.value
        if (state.phase != PuzzlePhase.SOLVING || state.busy) return
        settle(solved = false)
        val from = Square.fromValue(solution[index].substring(0, 2).uppercase())
        _ui.update { it.copy(hint = setOf(from), feedback = PuzzleFeedback.HINT) }
    }

    /** Gives up on the puzzle and plays the rest of the solution out on the board. */
    fun showSolution() {
        val state = _ui.value
        if (state.phase != PuzzlePhase.SOLVING || state.busy) return
        settle(solved = false)
        _ui.update { it.copy(busy = true, hint = emptySet(), feedback = PuzzleFeedback.NONE, solutionShown = true) }
        job = viewModelScope.launch {
            while (index < solution.size) {
                delay(SOLUTION_STEP_MS)
                game.playUci(solution[index])
                index++
                _ui.update { it.copy(position = game.position()) }
            }
            _ui.update { it.copy(phase = PuzzlePhase.SOLVED, busy = false) }
        }
    }

    private suspend fun playOpponentMove() {
        delay(OPPONENT_DELAY_MS)
        game.playUci(solution[index])
        index++
        _ui.update { it.copy(position = game.position(), busy = false) }
    }

    /** Any move that mates is accepted, even if it is not the listed solution. */
    private fun deliversMate(move: Move): Boolean {
        if (!game.play(move)) return false
        val mated = game.position().status.let { it == GameStatus.WHITE_WINS || it == GameStatus.BLACK_WINS }
        game.undo()
        return mated
    }

    /** Applies the rating change and records progress, once per puzzle. */
    private fun settle(solved: Boolean) {
        if (rated) return
        rated = true
        val puzzle = _ui.value.puzzle ?: return
        val before = settings.puzzleRating
        val after = Settings.updatedRating(before, puzzle.rating, solved)
        settings.puzzleRating = after
        _ui.update { it.copy(userRating = after, ratingChange = after - before) }
        viewModelScope.launch { gambitly.progress.record(ProgressKind.PUZZLE, puzzle.id, success = solved) }
    }

    private companion object {
        const val OPPONENT_DELAY_MS = 600L
        const val SOLUTION_STEP_MS = 900L
        val RATING_WINDOWS = listOf(150, 300, 600, 3000)
    }
}
