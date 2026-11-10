package com.yswy.dessin

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.yswy.dessin.ui.DessinScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 画面は常に明るい配色(Web版の color-scheme: light)なので、システムバーのアイコンも常に暗い色にする
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val start = debugStartState(this, intent)
        setContent {
            MaterialTheme {
                DessinScreen(start)
            }
        }
    }
}
