package io.github.gambitly.ui.openings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bhlangonijr.chesslib.Side
import io.github.gambitly.chess.GameStatus
import io.github.gambitly.chess.sanInWords
import io.github.gambitly.ui.board.ChessBoard
import io.github.gambitly.ui.common.CoachBanner
import io.github.gambitly.ui.common.CoachTone
import io.github.gambitly.ui.common.AppTopBar
import io.github.gambitly.ui.common.LevelPicker
import io.github.gambitly.ui.common.MoveStrip
import io.github.gambitly.ui.common.statusText

private fun Side.label() = if (this == Side.WHITE) "White" else "Black"

private fun Side.other() = if (this == Side.WHITE) Side.BLACK else Side.WHITE

private data class Coach(val tone: CoachTone, val title: String, val detail: String?)

/** Turns the trainer state into the one instruction shown above the board. */
private fun coachFor(ui: OpeningTrainerUi, userTurn: Boolean): Coach {
    val shown = ui.shownSan?.let { "${sanInWords(it)} ($it)" }
    return when (ui.phase) {
        TrainerPhase.PLAY_ON -> when {
            ui.engineFailed -> Coach(CoachTone.BAD, "The chess engine stopped working", "Tap Back to the line, then try again.")
            ui.thinking -> Coach(CoachTone.WATCH, "Stockfish is thinking…", null)
            ui.position.status != GameStatus.ONGOING -> Coach(CoachTone.GOOD, ui.position.statusText(), "Tap Back to the line to practise it again.")
            else -> Coach(CoachTone.YOUR_TURN, "Your move", "The book line is over. Play on as in a normal game.")
        }
        TrainerPhase.COMPLETE -> when {
            ui.mode == TrainerMode.LEARN ->
                Coach(CoachTone.GOOD, "That is the whole line", "Next, try it from memory without the arrows.")
            ui.mistakes == 0 ->
                Coach(CoachTone.GOOD, "Line complete, no mistakes", "Play on against Stockfish from here, or go back and pick another line.")
            else ->
                Coach(CoachTone.GOOD, "Line complete, with ${ui.mistakes} ${if (ui.mistakes == 1) "slip" else "slips"}", "Practise it again until you can play it cleanly.")
        }
        TrainerPhase.BOOK -> when {
            ui.opening == null -> Coach(CoachTone.WATCH, "Loading…", null)
            !userTurn -> Coach(
                CoachTone.WATCH,
                if (ui.feedback == Feedback.CORRECT) "Correct!" else "Watch ${ui.userSide.other().label()}'s move",
                "${ui.userSide.other().label()} is playing the book reply.",
            )
            ui.mode == TrainerMode.LEARN && shown == null -> Coach(CoachTone.YOUR_TURN, "Your move", null)
            ui.mode == TrainerMode.LEARN -> if (ui.feedback == Feedback.WRONG) {
                Coach(CoachTone.BAD, "That is a different move", "Follow the arrow: $shown.")
            } else {
                Coach(CoachTone.YOUR_TURN, "Your move: $shown", "Follow the arrow. Tap the piece, then the square it points to.")
            }
            shown != null ->
                Coach(CoachTone.BAD, "The book move is $shown", "Play the move shown by the arrow to continue.")
            ui.feedback == Feedback.WRONG ->
                Coach(CoachTone.BAD, "Not the book move", "Try again, or tap Show move to see it.")
            else -> Coach(
                CoachTone.YOUR_TURN,
                "Your move: play ${ui.userSide.label()}'s move ${ui.pliesPlayed / 2 + 1} from memory",
                "Tap a piece, then the square to move it to. Stuck? Tap Show move.",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpeningTrainerScreen(onBack: () -> Unit, viewModel: OpeningTrainerViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val opening = ui.opening
    val position = ui.position
    val userTurn = position.sideToMove == ui.userSide && position.status == GameStatus.ONGOING
    val coach = coachFor(ui, userTurn)
    val otherSide = ui.userSide.other()

    Scaffold(topBar = { AppTopBar(opening?.family ?: "Opening", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(opening?.variation ?: "", style = MaterialTheme.typography.titleMedium)
                val moves = ((opening?.plies ?: 0) + 1) / 2
                val progress = when (ui.phase) {
                    TrainerPhase.BOOK -> "You are ${ui.userSide.label()} · move ${ui.pliesPlayed / 2 + 1} of $moves"
                    TrainerPhase.COMPLETE -> "You are ${ui.userSide.label()} · all $moves moves played"
                    TrainerPhase.PLAY_ON -> "You are ${ui.userSide.label()} · playing on against Stockfish"
                }
                Text(progress, style = MaterialTheme.typography.bodyMedium)
            }
            if (ui.phase != TrainerPhase.PLAY_ON) {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    SegmentedButton(
                        selected = ui.mode == TrainerMode.LEARN,
                        onClick = { if (ui.mode != TrainerMode.LEARN) viewModel.restart(mode = TrainerMode.LEARN) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Learn (guided)") }
                    SegmentedButton(
                        selected = ui.mode == TrainerMode.PRACTISE,
                        onClick = { if (ui.mode != TrainerMode.PRACTISE) viewModel.restart(mode = TrainerMode.PRACTISE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Practise (memory)") }
                }
            }
            CoachBanner(coach.tone, coach.title, coach.detail)
            ChessBoard(
                position = position,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
                flipped = ui.userSide == Side.BLACK,
                interactive = userTurn && !ui.thinking && ui.phase != TrainerPhase.COMPLETE,
                arrow = ui.shownMove?.let { it.from to it.to },
            )
            MoveStrip(position.sanHistory, Modifier.padding(horizontal = 16.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (ui.phase) {
                    TrainerPhase.BOOK -> {
                        if (ui.mode == TrainerMode.PRACTISE) {
                            OutlinedButton(
                                onClick = viewModel::showMove,
                                enabled = userTurn && ui.shownMove == null,
                            ) { Text("Show move") }
                        }
                        OutlinedButton(onClick = { viewModel.restart() }) { Text("Start over") }
                        OutlinedButton(onClick = { viewModel.restart(otherSide) }) { Text("Switch to ${otherSide.label()}") }
                    }
                    TrainerPhase.COMPLETE -> {
                        if (ui.mode == TrainerMode.LEARN) {
                            Button(onClick = { viewModel.restart(mode = TrainerMode.PRACTISE) }) { Text("Practise from memory") }
                            OutlinedButton(onClick = { viewModel.restart() }) { Text("Watch again") }
                        } else {
                            Button(onClick = { viewModel.playOn() }) { Text("Play on vs Stockfish") }
                            OutlinedButton(onClick = { viewModel.restart() }) { Text("Practise again") }
                        }
                        OutlinedButton(onClick = { viewModel.restart(otherSide) }) { Text("Switch to ${otherSide.label()}") }
                    }
                    TrainerPhase.PLAY_ON -> {
                        OutlinedButton(onClick = viewModel::undo, enabled = !ui.thinking) { Text("Undo") }
                        LevelPicker(ui.levelIndex) { viewModel.playOn(it) }
                        OutlinedButton(onClick = { viewModel.restart() }) { Text("Back to the line") }
                    }
                }
            }
        }
    }
}
