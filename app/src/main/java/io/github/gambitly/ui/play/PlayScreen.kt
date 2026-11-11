package io.github.gambitly.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bhlangonijr.chesslib.Side
import io.github.gambitly.chess.GameStatus
import io.github.gambitly.ui.board.ChessBoard
import io.github.gambitly.ui.common.CoachBanner
import io.github.gambitly.ui.common.CoachTone
import io.github.gambitly.ui.common.AppTopBar
import io.github.gambitly.ui.common.LevelPicker
import io.github.gambitly.ui.common.MoveStrip
import io.github.gambitly.ui.common.statusText

@Composable
fun PlayScreen(onBack: () -> Unit, viewModel: PlayViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val position = state.position
    val userTurn = position.sideToMove == setup.userSide && position.status == GameStatus.ONGOING

    Scaffold(topBar = { AppTopBar("Free play", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sideName = if (setup.userSide == Side.WHITE) "White" else "Black"
            when {
                state.engineFailed ->
                    CoachBanner(CoachTone.BAD, "The chess engine stopped working", "Tap New game to try again.")
                state.thinking -> CoachBanner(CoachTone.WATCH, "Stockfish is thinking…")
                position.status != GameStatus.ONGOING ->
                    CoachBanner(CoachTone.GOOD, position.statusText(), "Tap New game to play again.")
                else -> CoachBanner(
                    CoachTone.YOUR_TURN,
                    if (position.checkSquare != null) "Your move: you are in check" else "Your move: you are $sideName",
                    "Tap a piece, then the square to move it to. Dots show where it can go.",
                )
            }
            ChessBoard(
                position = position,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
                flipped = setup.userSide == Side.BLACK,
                interactive = userTurn && !state.thinking,
            )
            MoveStrip(position.sanHistory, Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::undo, enabled = position.lastMove != null) { Text("Undo") }
                OutlinedButton(onClick = { viewModel.newGame() }) { Text("New game") }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LevelPicker(setup.levelIndex) { viewModel.newGame(setup.copy(levelIndex = it)) }
                OutlinedButton(
                    onClick = {
                        val other = if (setup.userSide == Side.WHITE) Side.BLACK else Side.WHITE
                        viewModel.newGame(setup.copy(userSide = other))
                    },
                ) {
                    Text(if (setup.userSide == Side.WHITE) "Playing White" else "Playing Black")
                }
            }
        }
    }
}
