package com.crylo.ringcut

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crylo.ringcut.data.Source
import com.crylo.ringcut.ui.EditorScreen
import com.crylo.ringcut.ui.EditorViewModel
import com.crylo.ringcut.ui.HomeScreen
import com.crylo.ringcut.ui.RingcutTheme
import com.crylo.ringcut.ui.TonesViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A file shared into the app, waiting to be opened in the editor. */
    private val shared = mutableStateOf<Source?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            RingcutTheme {
                val editor: EditorViewModel = viewModel()
                val tones: TonesViewModel = viewModel()
                val editorState by editor.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val resources = LocalResources.current
                val showMessage: (Int) -> Unit = { res -> scope.launch { snackbar.showSnackbar(resources.getString(res)) } }

                val pending by shared
                LaunchedEffect(pending) {
                    pending?.let {
                        editor.open(it)
                        shared.value = null
                    }
                }

                if (editorState.source != null) {
                    EditorScreen(editor, snackbar, onClose = editor::close, onMessage = showMessage)
                } else {
                    HomeScreen(tones, snackbar, onOpen = editor::open, onMessage = showMessage)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        shared.value = Source.from(this, uri, isVideo = intent.type?.startsWith("video/") == true)
    }
}
