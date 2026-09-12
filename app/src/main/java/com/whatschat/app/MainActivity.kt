package com.whatschat.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.whatschat.app.ui.navigation.PendingCallArgs
import com.whatschat.app.ui.navigation.WhatsChatNavGraph
import com.whatschat.app.ui.theme.WhatsChatTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_OPEN_CALL = "openCall"

        /** An intent that, when opened, deep-links straight into [com.whatschat.app.ui.screens.call.CallScreen] to join an already-ringing call. */
        fun incomingCallIntent(
            context: Context,
            callId: String,
            callerId: String,
            callerName: String,
            callerPhoto: String,
            isVideo: Boolean
        ): Intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CALL, true)
            putExtra("callId", callId)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callerPhoto", callerPhoto)
            putExtra("isVideo", isVideo)
        }

        private fun pendingCallFrom(intent: Intent?): PendingCallArgs? {
            if (intent?.getBooleanExtra(EXTRA_OPEN_CALL, false) != true) return null
            val callId = intent.getStringExtra("callId") ?: return null
            val callerId = intent.getStringExtra("callerId") ?: return null
            return PendingCallArgs(
                otherUid = callerId,
                name = intent.getStringExtra("callerName").orEmpty(),
                photo = intent.getStringExtra("callerPhoto").orEmpty(),
                callId = callId,
                isVideo = intent.getBooleanExtra("isVideo", false)
            )
        }
    }

    private val pendingCallState = mutableStateOf<PendingCallArgs?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingCallState.value = pendingCallFrom(intent)
        setContent {
            WhatsChatTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    WhatsChatNavGraph(
                        pendingCall = pendingCallState.value,
                        onPendingCallConsumed = { pendingCallState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingCallState.value = pendingCallFrom(intent)
    }
}
