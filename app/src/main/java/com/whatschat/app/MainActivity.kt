package com.whatschat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.whatschat.app.ui.navigation.WhatsChatNavGraph
import com.whatschat.app.ui.theme.WhatsChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhatsChatTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    WhatsChatNavGraph()
                }
            }
        }
    }
}
