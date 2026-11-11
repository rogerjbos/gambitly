package io.github.gambitly.chess

private val pieceNames = mapOf('K' to "king", 'Q' to "queen", 'R' to "rook", 'B' to "bishop", 'N' to "knight")
private val targetSquare = Regex("[a-h][1-8]")

/**
 * Spells a SAN move out for players who do not read notation yet,
 * e.g. "Nf3" -> "knight to f3", "exd5" -> "pawn takes on d5", "O-O" -> "castle kingside".
 */
fun sanInWords(san: String): String {
    if (san.startsWith("O-O-O")) return "castle queenside"
    if (san.startsWith("O-O")) return "castle kingside"
    val piece = pieceNames[san.first()] ?: "pawn"
    val target = targetSquare.findAll(san).lastOrNull()?.value ?: return san
    val action = if ('x' in san) "takes on" else "to"
    val promotion = san.substringAfter('=', "").firstOrNull()?.let { pieceNames[it] }
    return "$piece $action $target" + if (promotion != null) ", promoting to a $promotion" else ""
}
