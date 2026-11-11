package io.github.gambitly.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.gambitly.ui.common.AppTopBar

private const val SOURCE_URL = "https://github.com/rogerjbos/gambitly"

private val credits = listOf(
    "Chess engine" to "Stockfish 16, by the Stockfish developers. GNU General Public License v3.",
    "Openings" to "lichess-org/chess-openings. Public domain (CC0).",
    "Puzzles" to "Lichess puzzle database. Public domain (CC0).",
    "Classic games" to "Historical game scores, which are in the public domain.",
    "Rules and move generation" to "chesslib by Ben-Hur Carlos Vieira Langoni Junior. Apache License 2.0.",
    "Piece graphics" to "The “cburnett” set by Colin M.L. Burnett. GPLv2 or later.",
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(topBar = { AppTopBar("About", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Gambitly is free software, released under the GNU General Public License v3. " +
                    "You may use, study, share and change it; the source code is available under the same licence.",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { uriHandler.openUri(SOURCE_URL) }) { Text("Source code: github.com/rogerjbos/gambitly") }
            credits.forEach { (what, who) ->
                Column {
                    Text(what, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(who, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
