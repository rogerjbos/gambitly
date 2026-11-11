package io.github.gambitly.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gambitly.chess.GameStatus
import io.github.gambitly.chess.Position
import io.github.gambitly.engine.Strength
import com.github.bhlangonijr.chesslib.Side

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = { actions() },
    )
}

/** Horizontally scrolling move list ("1. e4 e5 2. Nf3 ..."), kept scrolled to the latest move. */
@Composable
fun MoveStrip(sanHistory: List<String>, modifier: Modifier = Modifier, firstMoveNumber: Int = 1, blackStarts: Boolean = false) {
    val scroll = rememberScrollState()
    LaunchedEffect(sanHistory.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(modifier = modifier.horizontalScroll(scroll)) {
        if (sanHistory.isEmpty()) {
            Text(" ", style = MaterialTheme.typography.bodyLarge)
        }
        sanHistory.forEachIndexed { i, san ->
            val ply = i + if (blackStarts) 1 else 0
            val number = firstMoveNumber + ply / 2
            val prefix = when {
                ply % 2 == 0 -> "$number. "
                i == 0 -> "$number… "
                else -> ""
            }
            Text(
                text = prefix + san,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (i == sanHistory.lastIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

fun Position.statusText(): String = when (status) {
    GameStatus.WHITE_WINS -> "Checkmate — White wins"
    GameStatus.BLACK_WINS -> "Checkmate — Black wins"
    GameStatus.STALEMATE -> "Draw by stalemate"
    GameStatus.DRAW -> "Draw"
    GameStatus.ONGOING -> {
        val side = if (sideToMove == Side.WHITE) "White" else "Black"
        if (checkSquare != null) "$side to move — check" else "$side to move"
    }
}

/** Button showing the current engine level; picking a different one calls [onPick]. */
@Composable
fun LevelPicker(levelIndex: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("Level ${Strength.LEVELS[levelIndex].label}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Strength.LEVELS.forEachIndexed { index, level ->
                DropdownMenuItem(
                    text = { Text(level.label) },
                    onClick = {
                        open = false
                        if (index != levelIndex) onPick(index)
                    },
                )
            }
        }
    }
}

/** What the coach banner is telling the user; drives its colour and icon. */
enum class CoachTone {
    /** The user must act now. */
    YOUR_TURN,

    /** Nothing to do: something is being played or loaded. */
    WATCH,

    /** The last action was right, or the exercise is complete. */
    GOOD,

    /** The last action was wrong, or the exercise was failed. */
    BAD,
}

/**
 * The one place on every practice screen that says what to do next. [title] is the
 * instruction or result; [detail] adds how to do it. The height is kept steady so
 * the board below does not jump as the text changes.
 */
@Composable
fun CoachBanner(tone: CoachTone, title: String, detail: String? = null, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content, icon) = when (tone) {
        CoachTone.YOUR_TURN -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, Icons.Filled.TouchApp)
        CoachTone.WATCH -> Triple(colors.surfaceVariant, colors.onSurfaceVariant, Icons.Filled.Visibility)
        CoachTone.GOOD -> Triple(colors.primaryContainer, colors.onPrimaryContainer, Icons.Filled.CheckCircle)
        CoachTone.BAD -> Triple(colors.errorContainer, colors.onErrorContainer, Icons.Filled.ErrorOutline)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 84.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
