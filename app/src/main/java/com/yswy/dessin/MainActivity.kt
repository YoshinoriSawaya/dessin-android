package com.yswy.dessin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.yswy.dessin.ui.DessinScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = debugStartState(this, intent)
        setContent {
            MaterialTheme {
                DessinScreen(start)
            }
        }
    }
}
