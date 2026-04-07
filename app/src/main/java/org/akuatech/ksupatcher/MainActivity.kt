package org.akuatech.ksupatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akuatech.ksupatcher.ui.KsuPatcherNavGraph
import org.akuatech.ksupatcher.ui.theme.KsuPatcherTheme
import org.akuatech.ksupatcher.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                KsuPatcherNavGraph(viewModel = mainViewModel)
            }
        }
    }
}
