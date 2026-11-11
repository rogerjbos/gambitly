package io.github.gambitly

import android.app.Application
import android.content.Context
import io.github.gambitly.data.ContentDao
import io.github.gambitly.data.ContentDatabase
import io.github.gambitly.data.ProgressDao
import io.github.gambitly.data.UserDatabase
import io.github.gambitly.engine.UciEngine
import java.io.File

class GambitlyApplication : Application() {
    /** Shared engine; the process is only spawned on first use. */
    val engine: UciEngine by lazy {
        UciEngine(File(applicationInfo.nativeLibraryDir, UciEngine.EXECUTABLE_NAME))
    }

    val content: ContentDao by lazy { ContentDatabase.open(this).dao() }

    val progress: ProgressDao by lazy { UserDatabase.open(this).progress() }
}

val Context.gambitly: GambitlyApplication get() = applicationContext as GambitlyApplication
