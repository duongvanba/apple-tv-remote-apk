package dev.duongvan.atvremote

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.duongvan.atvremote.ui.AtvRemoteTheme
import dev.duongvan.atvremote.ui.DevicesScreen
import dev.duongvan.atvremote.ui.RemoteScreen

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AtvRemoteTheme {
                AppRoot(viewModel)
            }
        }
    }

    /**
     * The physical volume rocker drives the Apple TV while a session is open,
     * so the events must not reach the phone's own volume stream.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val connected = viewModel.state.value.connection is ConnectionState.Connected
        if (connected) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        tick()
                        viewModel.volumeUp()
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        tick()
                        viewModel.volumeDown()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Light tick so a volume press is felt even though nothing moves on screen. */
    private fun tick() {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
private fun AppRoot(viewModel: RemoteViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        // A restored device is only a name and an address: it still has to be
        // connected before the remote can send anything.
        val restored = state.selected
        if (restored == null) viewModel.startScan() else viewModel.select(restored)
    }

    if (state.selected == null) {
        DevicesScreen(state = state, viewModel = viewModel)
    } else {
        RemoteScreen(state = state, viewModel = viewModel)
    }
}
