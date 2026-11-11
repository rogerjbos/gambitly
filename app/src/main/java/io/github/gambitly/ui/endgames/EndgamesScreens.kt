package io.github.gambitly.ui.endgames

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import io.github.gambitly.data.Endgame
import io.github.gambitly.ui.board.ChessBoard
import io.github.gambitly.ui.common.CoachBanner
import io.github.gambitly.ui.common.CoachTone
import io.github.gambitly.ui.common.AppTopBar
import io.github.gambitly.ui.common.MoveStrip

private fun Endgame.goalText(): String {
    val side = if (userSide == "b") "Black" else "White"
    return when (goal) {
        "draw" -> "$side to hold the draw for $moveLimit moves"
        "promote" -> "$side to promote the pawn within $moveLimit moves"
        else -> "$side to checkmate within $moveLimit moves"
    }
}

@Composable
fun EndgamesScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, viewModel: EndgamesViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Endgames", onBack) }) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ui.endgames.groupBy { it.category }.forEach { (category, positions) ->
                item(key = category) {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                positions.forEach { endgame ->
                    item(key = endgame.id) {
                        ListItem(
                            headlineContent = { Text(endgame.title) },
                            supportingContent = { Text(endgame.goalText()) },
                            trailingContent = {
                                if (endgame.id in ui.completed) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onOpen(endgame.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun EndgameTrainerScreen(onBack: () -> Unit, viewModel: EndgameTrainerViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val endgame = ui.endgame
    val position = ui.position
    val inProgress = ui.outcome == EndgameOutcome.IN_PROGRESS
    val userTurn = position.sideToMove == ui.userSide && position.status == GameStatus.ONGOING

    Scaffold(topBar = { AppTopBar(endgame?.title ?: "Endgame", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val used = endgame?.let { "Moves used: ${ui.movesUsed} of ${it.moveLimit}" }
            when {
                endgame == null -> CoachBanner(CoachTone.WATCH, "Loading…")
                ui.outcome == EndgameOutcome.SUCCESS ->
                    CoachBanner(CoachTone.GOOD, ui.message ?: "Done", "Tap Try again to repeat it, or go back for another position.")
                ui.outcome == EndgameOutcome.FAILED ->
                    CoachBanner(CoachTone.BAD, ui.message ?: "Not this time", "Read the tip below the board, then tap Try again.")
                ui.engineFailed ->
                    CoachBanner(CoachTone.BAD, "The chess engine stopped working", "Tap Restart to try again.")
                ui.thinking -> CoachBanner(CoachTone.WATCH, "Stockfish is replying…", used)
                else -> CoachBanner(CoachTone.YOUR_TURN, "Your move: ${endgame.goalText()}", used)
            }
            ChessBoard(
                position = position,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
                flipped = ui.userSide == Side.BLACK,
                interactive = inProgress && userTurn && !ui.thinking,
            )
            MoveStrip(
                position.sanHistory,
                Modifier.padding(horizontal = 16.dp),
                blackStarts = endgame?.fen?.split(' ')?.getOrNull(1) == "b",
            )
            if (endgame != null) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Tip", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(endgame.hint, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (inProgress) {
                    OutlinedButton(onClick = viewModel::undo, enabled = position.lastMove != null && !ui.thinking) { Text("Undo") }
                    OutlinedButton(onClick = viewModel::restart) { Text("Restart") }
                } else {
                    Button(onClick = viewModel::restart) { Text("Try again") }
                }
            }
        }
    }
}
