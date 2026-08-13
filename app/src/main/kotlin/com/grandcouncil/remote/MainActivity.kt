package com.grandcouncil.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grandcouncil.remote.ui.MainScreen
import com.grandcouncil.remote.ui.theme.GrandCouncilTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrandCouncilTheme {
                MainScreen()
            }
        }
    }
}
