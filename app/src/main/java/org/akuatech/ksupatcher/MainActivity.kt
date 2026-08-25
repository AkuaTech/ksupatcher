package org.akuatech.ksupatcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akuatech.ksupatcher.ui.KsuPatcherNavGraph
import org.akuatech.ksupatcher.ui.theme.KsuPatcherTheme
import org.akuatech.ksupatcher.viewmodel.MainViewModel
import org.akuatech.ksupatcher.viewmodel.RootStatus

class MainActivity : ComponentActivity() {
    private var pendingUri by mutableStateOf<Uri?>(null)
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        resolveIntent(intent)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val state by mainViewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (state.themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            KsuPatcherTheme(darkTheme = darkTheme) {
                LifecycleResumeEffect(Unit) {
                    if (state.rootStatus != RootStatus.GRANTED) {
                        mainViewModel.refreshRootStatus()
                    }
                    onPauseOrDispose { }
                }
                KsuPatcherNavGraph(
                    viewModel = mainViewModel,
                    pendingUri = pendingUri,
                    pendingRoute = pendingRoute
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resolveIntent(intent)
    }

    private fun resolveIntent(intent: Intent?) {
        intent?.getStringExtra("route")?.let {
            pendingRoute = it
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme == "ksupatcher") {
            pendingRoute = uri.host ?: "install"
            return
        }
        pendingUri = uri
        pendingRoute = when (intent.component?.className) {
            "org.akuatech.ksupatcher.FlashZipAlias" -> "flash"
            else -> if (intent.type.orEmpty().contains("zip") ||
                (uri.lastPathSegment ?: "").endsWith(".zip")) "flash" else "install"
        }
    }
}
