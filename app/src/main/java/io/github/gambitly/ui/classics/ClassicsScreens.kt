package io.github.gambitly.ui.classics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import io.github.gambitly.ui.board.ChessBoard
import io.github.gambitly.chess.sanInWords
import io.github.gambitly.ui.common.CoachBanner
import io.github.gambitly.ui.common.CoachTone
import io.github.gambitly.ui.common.AppTopBar
import io.github.gambitly.ui.common.MoveStrip

@Composable
fun ClassicsScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, viewModel: ClassicsViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Classic games", onBack) }) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(ui.games, key = { it.id }) { game ->
                ListItem(
                    headlineContent = { Text(game.title) },
                    supportingContent = {
                        Text("${game.white} – ${game.black}\n${game.event}, ${game.year}")
                    },
                    trailingContent = {
                        ui.best[game.id]?.let { Text("Best $it%", color = MaterialTheme.colorScheme.primary) }
                    },
                    modifier = Modifier.clickable { onOpen(game.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassicTrainerScreen(onBack: () -> Unit, viewModel: ClassicTrainerViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val game = ui.game
    val sideName = if (ui.userSide == Side.WHITE) "White" else "Black"

    Scaffold(topBar = { AppTopBar(game?.title ?: "Classic game", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (game != null) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "${game.white} – ${game.black}, ${game.event} ${game.year}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(game.intro, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val lastMove = ui.lastSan?.let { "${sanInWords(it)} ($it)" }
            when {
                game == null -> CoachBanner(CoachTone.WATCH, "Loading…")
                ui.finished -> {
                    val percent = if (ui.guessed == 0) 0 else ui.matched * 100 / ui.guessed
                    CoachBanner(
                        CoachTone.GOOD,
                        "Game over: you matched ${ui.matched} of ${ui.guessed} moves ($percent%)",
                        "Result ${game.result}. Start again, or guess for the other side.",
                    )
                }
                ui.busy && ui.lastGuessHit == true ->
                    CoachBanner(CoachTone.GOOD, "Yes! The game move was $lastMove", "Watch the reply…")
                ui.busy && ui.lastGuessHit == false ->
                    CoachBanner(CoachTone.BAD, "The game move was $lastMove", "It has been played for you. Watch the reply…")
                ui.busy -> CoachBanner(CoachTone.WATCH, "Watch the game", "You will be asked to guess $sideName's moves.")
                else -> CoachBanner(
                    CoachTone.YOUR_TURN,
                    "What did $sideName play here?",
                    "Make your guess on the board. You get one try, then the real move is played.",
                )
            }
            ChessBoard(
                position = ui.position,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
                flipped = ui.userSide == Side.BLACK,
                interactive = !ui.busy && !ui.finished,
            )
            MoveStrip(ui.position.sanHistory, Modifier.padding(horizontal = 16.dp))
            Text(
                "Matched ${ui.matched} of ${ui.guessed} guesses so far",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val otherSide = if (ui.userSide == Side.WHITE) Side.BLACK else Side.WHITE
                OutlinedButton(onClick = { viewModel.restart() }) { Text("Start again") }
                OutlinedButton(onClick = { viewModel.restart(otherSide) }) {
                    Text("Guess as ${if (otherSide == Side.WHITE) "White" else "Black"}")
                }
            }
        }
    }
}
