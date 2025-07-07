package com.example.keyspaceexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    val viewModel = KeyspaceViewModel(KeyspaceRepository(RedisService()))

    val DarkColorScheme = darkColorScheme(
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.DarkGray,
        onSurface = Color.White,
        primary = Color.Yellow
    )

    @Composable
    fun KeyspaceExplorerTheme(
        darkTheme: Boolean = true,
        content: @Composable () -> Unit
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = androidx.compose.material3.Typography(),
            content = content
        )
    }

    object Instance {
        var context: ComponentActivity? = null
        const val batchSize = 45
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        Instance.context = this
        super.onCreate(savedInstanceState)
        setContent {
            KeyspaceExplorerTheme (darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KeyspaceScreen(viewModel)
                }
            }
        }
    }
}
