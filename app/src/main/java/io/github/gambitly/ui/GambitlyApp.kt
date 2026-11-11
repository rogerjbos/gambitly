package io.github.gambitly.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import io.github.gambitly.R
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.gambitly.ui.about.AboutScreen
import io.github.gambitly.ui.classics.ClassicTrainerScreen
import io.github.gambitly.ui.classics.ClassicsScreen
import io.github.gambitly.ui.endgames.EndgameTrainerScreen
import io.github.gambitly.ui.endgames.EndgamesScreen
import io.github.gambitly.ui.openings.OpeningTrainerScreen
import io.github.gambitly.ui.openings.OpeningsScreen
import io.github.gambitly.ui.play.PlayScreen
import io.github.gambitly.ui.puzzles.PuzzlesScreen

object Routes {
    const val HOME = "home"
    const val OPENINGS = "openings"
    const val PUZZLES = "puzzles"
    const val CLASSICS = "classics"
    const val ENDGAMES = "endgames"
    const val PLAY = "play"
    const val ABOUT = "about"
}

private data class Mode(val title: String, val blurb: String, val route: String)

private val modes = listOf(
    Mode("Openings", "Learn named opening lines move by move", Routes.OPENINGS),
    Mode("Puzzles", "Tactics by theme and difficulty", Routes.PUZZLES),
    Mode("Classic games", "Guess the masters' moves", Routes.CLASSICS),
    Mode("Endgames", "Basic checkmates and key positions", Routes.ENDGAMES),
    Mode("Free play", "A full game against Stockfish", Routes.PLAY),
)

private val idArgument = listOf(navArgument("id") { type = NavType.IntType })

@Composable
fun GambitlyApp() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(onOpen = { nav.navigate(it) }) }
        composable(Routes.PLAY) { PlayScreen(onBack = back) }
        composable(Routes.OPENINGS) {
            OpeningsScreen(onBack = back, onOpen = { nav.navigate("${Routes.OPENINGS}/$it") })
        }
        composable("${Routes.OPENINGS}/{id}", arguments = idArgument) { OpeningTrainerScreen(onBack = back) }
        composable(Routes.PUZZLES) { PuzzlesScreen(onBack = back) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
        composable(Routes.CLASSICS) {
            ClassicsScreen(onBack = back, onOpen = { nav.navigate("${Routes.CLASSICS}/$it") })
        }
        composable("${Routes.CLASSICS}/{id}", arguments = idArgument) { ClassicTrainerScreen(onBack = back) }
        composable(Routes.ENDGAMES) {
            EndgamesScreen(onBack = back, onOpen = { nav.navigate("${Routes.ENDGAMES}/$it") })
        }
        composable("${Routes.ENDGAMES}/{id}", arguments = idArgument) { EndgameTrainerScreen(onBack = back) }
    }
}

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(Modifier.size(72.dp))
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Gambitly", style = MaterialTheme.typography.headlineLarge)
                    Text("Hammer your chess into shape", style = MaterialTheme.typography.bodyMedium)
                }
            }
            modes.forEach { mode ->
                Card(
                    onClick = { onOpen(mode.route) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(mode.title, style = MaterialTheme.typography.titleMedium)
                        Text(mode.blurb, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            TextButton(onClick = { onOpen(Routes.ABOUT) }) { Text("About and credits") }
        }
    }
}

/** The launcher artwork as an in-app badge: the icon's foreground over its background. */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(22))) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
        // Adaptive-icon foregrounds carry wide margins; zoom in so the artwork fills the badge.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Gambitly logo",
            modifier = Modifier
                .fillMaxSize()
                .scale(1.4f),
        )
    }
}
