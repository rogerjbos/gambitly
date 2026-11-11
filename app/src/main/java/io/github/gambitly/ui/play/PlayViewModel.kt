package io.github.gambitly.ui.play

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import io.github.gambitly.chess.ChessGame
import io.github.gambitly.data.Settings
import io.github.gambitly.gambitly
import io.github.gambitly.engine.Strength
import io.github.gambitly.engine.VsEngineSession
import io.github.gambitly.engine.VsEngineState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class PlaySetup(val userSide: Side, val levelIndex: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = app.gambitly.engine
    private val settings = Settings(app)

    private val _setup = MutableStateFlow(
        PlaySetup(
            userSide = if (settings.playAsBlack) Side.BLACK else Side.WHITE,
            levelIndex = settings.playLevel.coerceIn(Strength.LEVELS.indices),
        ),
    )
    val setup: StateFlow<PlaySetup> = _setup.asStateFlow()

    private val session = MutableStateFlow(newSession(_setup.value))

    val state: StateFlow<VsEngineState> = session
        .flatMapLatest { it.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.value.state.value)

    fun onMove(move: Move) = session.value.userMove(move)

    fun undo() = session.value.undo()

    /** Starts a fresh game; changing side or level also restarts. */
    fun newGame(setup: PlaySetup = _setup.value) {
        session.value.stop()
        _setup.value = setup
        settings.playLevel = setup.levelIndex
        settings.playAsBlack = setup.userSide == Side.BLACK
        session.value = newSession(setup)
    }

    private fun newSession(setup: PlaySetup) =
        VsEngineSession(viewModelScope, engine, ChessGame(), setup.userSide, Strength.LEVELS[setup.levelIndex])
}
