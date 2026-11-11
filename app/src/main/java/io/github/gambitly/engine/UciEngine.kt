package io.github.gambitly.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

/** How strongly the engine should play. Null fields leave that limit off. */
data class Strength(
    val label: String,
    /** Stockfish "Skill Level" 0–20; used for the weakest levels, below the Elo range. */
    val skillLevel: Int? = null,
    /** Target rating via UCI_LimitStrength; Stockfish 16 accepts 1320–3190. */
    val elo: Int? = null,
    /** Fixed search depth; keeps the beginner levels shallow and instant. */
    val depth: Int? = null,
    val moveTimeMs: Int = 600,
) {
    companion object {
        val LEVELS = listOf(
            Strength("1 · Beginner", skillLevel = 0, depth = 1),
            Strength("2 · Novice", skillLevel = 2, depth = 2),
            Strength("3 · Casual", skillLevel = 5, depth = 4),
            Strength("4 · Club (1500)", elo = 1500),
            Strength("5 · Strong club (1800)", elo = 1800),
            Strength("6 · Expert (2100)", elo = 2100),
            Strength("7 · Master (2500)", elo = 2500, moveTimeMs = 1000),
            Strength("8 · Maximum", moveTimeMs = 1500),
        )
        val MAX = LEVELS.last()
    }
}

data class EngineMove(
    /** Best move in UCI notation, or null when the side to move has no legal move. */
    val uci: String?,
    /** Evaluation in centipawns from the side to move's point of view, if reported. */
    val scoreCp: Int? = null,
    /** Moves to mate (negative when the side to move is being mated), if reported. */
    val mateIn: Int? = null,
)

/**
 * Drives a Stockfish process over the UCI text protocol. One instance is shared
 * app-wide; calls are serialised, so concurrent callers queue.
 */
class UciEngine(private val executable: File) {
    private val mutex = Mutex()
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    /**
     * Searches the position reached from [startFen] after [uciMoves].
     * The search is bounded by the strength's depth or move time, so it is left
     * to finish even if the caller is cancelled; that keeps the protocol in sync.
     */
    suspend fun bestMove(startFen: String, uciMoves: List<String>, strength: Strength): EngineMove =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    search(startFen, uciMoves, strength)
                } catch (e: IOException) {
                    // The process died (e.g. killed while backgrounded): restart once.
                    shutdown()
                    search(startFen, uciMoves, strength)
                }
            }
        }

    private fun search(startFen: String, uciMoves: List<String>, strength: Strength): EngineMove {
        ensureStarted()
        send("setoption name Skill Level value ${strength.skillLevel ?: 20}")
        send("setoption name UCI_LimitStrength value ${strength.elo != null}")
        strength.elo?.let { send("setoption name UCI_Elo value $it") }
        val moves = if (uciMoves.isEmpty()) "" else " moves " + uciMoves.joinToString(" ")
        send("position fen $startFen$moves")
        send(if (strength.depth != null) "go depth ${strength.depth}" else "go movetime ${strength.moveTimeMs}")

        var scoreCp: Int? = null
        var mateIn: Int? = null
        while (true) {
            val line = readLine()
            val tokens = line.split(' ')
            if (tokens.first() == "info") {
                val at = tokens.indexOf("score")
                if (at >= 0 && at + 2 < tokens.size) {
                    val value = tokens[at + 2].toIntOrNull()
                    when (tokens[at + 1]) {
                        "cp" -> { scoreCp = value; mateIn = null }
                        "mate" -> { mateIn = value; scoreCp = null }
                    }
                }
            } else if (tokens.first() == "bestmove") {
                val move = tokens.getOrNull(1)?.takeUnless { it == "(none)" }
                return EngineMove(move, scoreCp, mateIn)
            }
        }
    }

    private fun ensureStarted() {
        if (process?.isAlive == true) return
        shutdown()
        val started = ProcessBuilder(executable.absolutePath).redirectErrorStream(true).start()
        process = started
        reader = started.inputStream.bufferedReader()
        writer = started.outputStream.bufferedWriter()
        send("uci")
        awaitLine("uciok")
        send("setoption name Threads value ${Runtime.getRuntime().availableProcessors().coerceIn(1, 2)}")
        send("setoption name Hash value 32")
        send("isready")
        awaitLine("readyok")
    }

    private fun send(command: String) {
        val out = writer ?: throw IOException("engine not running")
        out.write(command)
        out.newLine()
        out.flush()
    }

    private fun readLine(): String = reader?.readLine() ?: throw IOException("engine closed its output")

    private fun awaitLine(expected: String) {
        while (readLine() != expected) Unit
    }

    private fun shutdown() {
        runCatching { writer?.apply { write("quit\n"); flush() } }
        process?.destroy()
        process = null
        reader = null
        writer = null
    }

    suspend fun close() = mutex.withLock { withContext(Dispatchers.IO) { shutdown() } }

    companion object {
        /** File name the native build gives the engine executable inside the APK's lib folder. */
        const val EXECUTABLE_NAME = "libstockfish.so"
    }
}
