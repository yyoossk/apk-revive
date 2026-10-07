package com.yyoossk.apkrevive

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yyoossk.apkrevive.ui.ReviveApp
import com.yyoossk.apkrevive.ui.ReviveTheme
import com.yyoossk.apkrevive.ui.TermsScreen

class MainActivity : ComponentActivity() {
    private val vm: ReviveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        setContent {
            ReviveTheme {
                var accepted by remember { mutableStateOf(prefs.getBoolean("terms_v1", false)) }
                if (!accepted) {
                    TermsScreen(onAccept = {
                        prefs.edit().putBoolean("terms_v1", true).apply()
                        accepted = true
                    })
                } else {
                    val screen by vm.screen.collectAsStateWithLifecycle()
                    ReviveApp(screen, vm)
                }
            }
        }
    }
}
