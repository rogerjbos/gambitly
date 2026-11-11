package io.github.gambitly.ui.board

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import io.github.gambitly.R
import io.github.gambitly.chess.Position
import kotlin.math.roundToInt

private val LightSquare = Color(0xFFF0D9B5)
private val DarkSquare = Color(0xFFB58863)
private val LastMoveTint = Color(0x669BC700)
private val SelectedTint = Color(0x8014551E)
private val TargetTint = Color(0x5014551E)
private val CheckTint = Color(0xB3E53935)
private val HintTint = Color(0xFF2E7DD1)
private val ArrowTint = Color(0xD92E7DD1)

@DrawableRes
fun Piece.drawableRes(): Int? = when (this) {
    Piece.WHITE_KING -> R.drawable.piece_white_king
    Piece.WHITE_QUEEN -> R.drawable.piece_white_queen
    Piece.WHITE_ROOK -> R.drawable.piece_white_rook
    Piece.WHITE_BISHOP -> R.drawable.piece_white_bishop
    Piece.WHITE_KNIGHT -> R.drawable.piece_white_knight
    Piece.WHITE_PAWN -> R.drawable.piece_white_pawn
    Piece.BLACK_KING -> R.drawable.piece_black_king
    Piece.BLACK_QUEEN -> R.drawable.piece_black_queen
    Piece.BLACK_ROOK -> R.drawable.piece_black_rook
    Piece.BLACK_BISHOP -> R.drawable.piece_black_bishop
    Piece.BLACK_KNIGHT -> R.drawable.piece_black_knight
    Piece.BLACK_PAWN -> R.drawable.piece_black_pawn
    else -> null
}

/**
 * Interactive chess board. Supports tap-tap and drag moves; only moves in
 * [Position.legalMoves] can be made, and promotions prompt for the piece.
 *
 * @param flipped true to show the board from Black's side.
 * @param interactive false to ignore input (e.g. while the computer is thinking).
 * @param hintSquares squares to ring, used by trainers to point at a piece.
 * @param arrow a move to draw as an arrow (from, to), used to show the move to play.
 */
@Composable
fun ChessBoard(
    position: Position,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    interactive: Boolean = true,
    hintSquares: Set<Square> = emptySet(),
    arrow: Pair<Square, Square>? = null,
) {
    var selected by remember(position.fen) { mutableStateOf<Square?>(null) }
    var dragging by remember(position.fen) { mutableStateOf<Square?>(null) }
    var dragOffset by remember(position.fen) { mutableStateOf(Offset.Zero) }
    var pendingPromotion by remember(position.fen) { mutableStateOf<List<Move>?>(null) }
    val currentOnMove by rememberUpdatedState(onMove)
    val textMeasurer = rememberTextMeasurer()

    fun targetsOf(from: Square?): List<Square> =
        if (from == null) emptyList() else position.legalMoves.filter { it.from == from }.map { it.to }

    fun attempt(from: Square, to: Square): Boolean {
        val candidates = position.legalMoves.filter { it.from == from && it.to == to }
        when {
            candidates.isEmpty() -> return false
            candidates.size == 1 -> currentOnMove(candidates.first())
            else -> pendingPromotion = candidates
        }
        return true
    }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val boardPx = constraints.maxWidth.toFloat()
        val cell = boardPx / 8f
        val cellDp = with(LocalDensity.current) { cell.toDp() }

        fun topLeft(square: Square): Offset {
            val file = square.file.ordinal
            val rank = square.rank.ordinal
            val col = if (flipped) 7 - file else file
            val row = if (flipped) rank else 7 - rank
            return Offset(col * cell, row * cell)
        }

        fun squareAt(point: Offset): Square? {
            val col = (point.x / cell).toInt()
            val row = (point.y / cell).toInt()
            if (col !in 0..7 || row !in 0..7 || point.x < 0 || point.y < 0) return null
            val file = if (flipped) 7 - col else col
            val rank = if (flipped) row else 7 - row
            return Square.squareAt(rank * 8 + file)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(position.fen, flipped, interactive, boardPx) {
                    if (!interactive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val square = squareAt(down.position) ?: return@awaitEachGesture
                        val previous = selected
                        if (previous != null && previous != square && attempt(previous, square)) {
                            selected = null
                            return@awaitEachGesture
                        }
                        val piece = position.pieceAt(square)
                        if (piece == Piece.NONE || piece.pieceSide != position.sideToMove) {
                            selected = null
                            return@awaitEachGesture
                        }
                        selected = square
                        dragging = square
                        dragOffset = Offset.Zero
                        var last = down.position
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            last = change.position
                            if (!change.pressed) break
                            dragOffset = last - down.position
                            change.consume()
                        }
                        dragging = null
                        dragOffset = Offset.Zero
                        val drop = squareAt(last)
                        if (drop != null && drop != square) {
                            if (attempt(square, drop)) selected = null
                        } else if (previous == square) {
                            selected = null // second tap on the same piece deselects
                        }
                    }
                },
        ) {
            val cellSize = Size(cell, cell)
            for (index in 0 until 64) {
                val square = Square.squareAt(index)
                val dark = (square.file.ordinal + square.rank.ordinal) % 2 == 0
                drawRect(if (dark) DarkSquare else LightSquare, topLeft(square), cellSize)
            }
            position.lastMove?.let {
                drawRect(LastMoveTint, topLeft(it.from), cellSize)
                drawRect(LastMoveTint, topLeft(it.to), cellSize)
            }
            selected?.let { drawRect(SelectedTint, topLeft(it), cellSize) }
            position.checkSquare?.let {
                drawCircle(CheckTint, cell * 0.48f, topLeft(it) + Offset(cell / 2, cell / 2))
            }
            for (target in targetsOf(selected)) {
                val centre = topLeft(target) + Offset(cell / 2, cell / 2)
                if (position.pieceAt(target) == Piece.NONE) {
                    drawCircle(TargetTint, cell * 0.16f, centre)
                } else {
                    drawCircle(TargetTint, cell * 0.44f, centre, style = Stroke(cell * 0.08f))
                }
            }
            for (hint in hintSquares) {
                val inset = cell * 0.04f
                drawRect(
                    HintTint,
                    topLeft(hint) + Offset(inset, inset),
                    Size(cell - 2 * inset, cell - 2 * inset),
                    style = Stroke(cell * 0.08f),
                )
            }
            // Coordinates: files along the bottom edge, ranks down the left edge.
            val labelStyle = TextStyle(fontSize = (cell * 0.2f).toSp(), fontWeight = FontWeight.SemiBold)
            for (i in 0 until 8) {
                val file = if (flipped) 7 - i else i
                val rank = if (flipped) i else 7 - i
                val fileDark = (file + (if (flipped) 7 else 0)) % 2 == 0
                val rankDark = ((if (flipped) 7 else 0) + rank) % 2 == 0
                val fileLabel = textMeasurer.measure(
                    ('a' + file).toString(),
                    labelStyle.copy(color = if (fileDark) LightSquare else DarkSquare),
                )
                drawText(
                    fileLabel,
                    topLeft = Offset(
                        (i + 1) * cell - fileLabel.size.width - cell * 0.05f,
                        8 * cell - fileLabel.size.height - cell * 0.02f,
                    ),
                )
                drawText(
                    textMeasurer,
                    ('1' + rank).toString(),
                    topLeft = Offset(cell * 0.05f, i * cell + cell * 0.02f),
                    style = labelStyle.copy(color = if (rankDark) LightSquare else DarkSquare),
                )
            }
        }

        for (index in 0 until 64) {
            val square = Square.squareAt(index)
            val res = position.pieceAt(square).drawableRes() ?: continue
            val isDragged = dragging == square
            val base = topLeft(square)
            val at = if (isDragged) base + dragOffset else base
            Image(
                painter = painterResource(res),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                    .size(cellDp)
                    .zIndex(if (isDragged) 1f else 0f),
            )
        }

        // Drawn above the pieces; it has no pointer input, so touches still reach the board.
        if (arrow != null) {
            Canvas(Modifier.fillMaxSize()) {
                val half = Offset(cell / 2, cell / 2)
                val start = topLeft(arrow.first) + half
                val tip = topLeft(arrow.second) + half
                val direction = tip - start
                val length = direction.getDistance()
                if (length > 0f) {
                    val unit = direction / length
                    val normal = Offset(-unit.y, unit.x)
                    val headLength = cell * 0.42f
                    val headBase = tip - unit * headLength
                    drawLine(ArrowTint, start + unit * (cell * 0.2f), headBase, strokeWidth = cell * 0.17f)
                    val head = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(headBase.x + normal.x * cell * 0.24f, headBase.y + normal.y * cell * 0.24f)
                        lineTo(headBase.x - normal.x * cell * 0.24f, headBase.y - normal.y * cell * 0.24f)
                        close()
                    }
                    drawPath(head, ArrowTint)
                }
            }
        }
    }

    pendingPromotion?.let { options ->
        AlertDialog(
            onDismissRequest = { pendingPromotion = null },
            confirmButton = {},
            title = { Text("Promote to") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { move ->
                        val res = move.promotion.drawableRes() ?: return@forEach
                        Image(
                            painter = painterResource(res),
                            contentDescription = move.promotion.pieceType.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clickable {
                                    pendingPromotion = null
                                    selected = null
                                    currentOnMove(move)
                                },
                        )
                    }
                }
            },
        )
    }
}
