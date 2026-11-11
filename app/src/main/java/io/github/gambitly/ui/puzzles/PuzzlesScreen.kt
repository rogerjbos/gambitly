package io.github.gambitly.ui.puzzles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bhlangonijr.chesslib.Side
import io.github.gambitly.ui.board.ChessBoard
import io.github.gambitly.ui.common.CoachBanner
import io.github.gambitly.ui.common.CoachTone
import io.github.gambitly.ui.common.AppTopBar

/** The single instruction shown above the board for the current puzzle state. */
@Composable
private fun PuzzleCoach(ui: PuzzleUi, themeNames: Map<String, String>) {
    val side = if (ui.userSide == Side.WHITE) "White" else "Black"
    // Themes would give the answer away, so they only appear once the puzzle is over.
    val summary = ui.puzzle?.let { puzzle ->
        val themes = puzzle.themeList.mapNotNull { themeNames[it] }.joinToString(", ")
        "Puzzle rating ${puzzle.rating}" + if (themes.isEmpty()) "" else " · $themes"
    }
    when (ui.phase) {
        PuzzlePhase.LOADING -> CoachBanner(CoachTone.WATCH, "Finding a puzzle…")
        PuzzlePhase.NONE_FOUND ->
            CoachBanner(CoachTone.BAD, "No puzzles for this theme", "Pick another theme with the button below the board.")
        PuzzlePhase.SOLVED ->
            if (ui.solutionShown) {
                CoachBanner(CoachTone.WATCH, "That was the solution", summary)
            } else {
                CoachBanner(CoachTone.GOOD, "Solved!", summary)
            }
        PuzzlePhase.SOLVING -> when {
            ui.solutionShown -> CoachBanner(CoachTone.WATCH, "Showing the solution…", "Watch the moves play out.")
            ui.busy && ui.feedback == PuzzleFeedback.CORRECT ->
                CoachBanner(CoachTone.GOOD, "Correct!", "Watch the reply, then find the next move.")
            ui.busy -> CoachBanner(CoachTone.WATCH, "Watch your opponent's move", "The puzzle starts once it has been played.")
            ui.feedback == PuzzleFeedback.WRONG ->
                CoachBanner(CoachTone.BAD, "Not the best move", "Your piece was put back. Try again, or tap Hint.")
            ui.feedback == PuzzleFeedback.HINT ->
                CoachBanner(CoachTone.YOUR_TURN, "Move the highlighted piece", "Tap it, then choose the best square for it.")
            ui.feedback == PuzzleFeedback.CORRECT ->
                CoachBanner(CoachTone.YOUR_TURN, "Correct so far. Find the next move", "You are $side.")
            else ->
                CoachBanner(CoachTone.YOUR_TURN, "Your turn: you are $side", "Find the best move. Tap a piece, then the square to move it to.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PuzzlesScreen(onBack: () -> Unit, viewModel: PuzzlesViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val themeNames = remember { PUZZLE_THEMES.toMap() }
    val canAct = ui.phase == PuzzlePhase.SOLVING && !ui.busy

    Scaffold(topBar = { AppTopBar("Puzzles", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PuzzleCoach(ui, themeNames)
            ChessBoard(
                position = ui.position,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
                flipped = ui.userSide == Side.BLACK,
                interactive = canAct,
                hintSquares = ui.hint,
            )
            val change = ui.ratingChange?.let { if (it >= 0) " (+$it)" else " ($it)" } ?: ""
            Text(
                "Your rating: ${ui.userRating}$change",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ui.phase == PuzzlePhase.SOLVED) {
                    Button(onClick = viewModel::next) { Text("Next puzzle") }
                } else {
                    OutlinedButton(onClick = viewModel::showHint, enabled = canAct) { Text("Hint") }
                    OutlinedButton(onClick = viewModel::showSolution, enabled = canAct) { Text("Show solution") }
                    OutlinedButton(onClick = viewModel::next) { Text("Skip") }
                }
                ThemePicker(themeNames[ui.theme] ?: "All themes", viewModel::setTheme)
            }
        }
    }
}

@Composable
private fun ThemePicker(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("Theme: $current") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PUZZLE_THEMES.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onPick(key)
                    },
                )
            }
        }
    }
}
