package io.github.gambitly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.gambitly.ui.GambitlyApp
import io.github.gambitly.ui.theme.GambitlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GambitlyTheme {
                GambitlyApp()
            }
        }
    }
}
