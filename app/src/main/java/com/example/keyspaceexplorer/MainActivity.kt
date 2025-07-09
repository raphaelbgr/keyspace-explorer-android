package com.example.keyspaceexplorer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

class MainActivity : ComponentActivity() {

    private val viewModel = KeyspaceViewModel(KeyspaceRepository())

    private val DarkColorScheme = darkColorScheme(
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
        var activity: Activity? = null
        var context: Context? = null
        const val batchSize = 45
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        Instance.activity = this
        Instance.context = this
        super.onCreate(savedInstanceState)
        setContent {
            KeyspaceExplorerTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .ignoreHover(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KeyspaceScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncMatches()
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_HOVER_MOVE ||
            ev?.action == MotionEvent.ACTION_HOVER_ENTER ||
            ev?.action == MotionEvent.ACTION_HOVER_EXIT) {
            return true // ignora
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}

@SuppressLint("ReturnFromAwaitPointerEventScope")
fun Modifier.ignoreHover(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Enter || event.type == PointerEventType.Exit || event.type.toString()
                    .contains("Hover", true)
            ) { continue }
        }
    }
}
