package com.whatschat.app.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.whatschat.app.MainActivity
import com.whatschat.app.R
import com.whatschat.app.data.model.Call
import com.whatschat.app.data.model.CallStatus
import com.whatschat.app.data.repository.CallRepository
import com.whatschat.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a Firestore listener for incoming calls alive independent of any
 * screen being on-screen, so a call can ring even while the app is
 * backgrounded. It cannot survive the user force-stopping the app or an
 * aggressive OEM battery manager (e.g. MIUI) killing background apps — that
 * still needs a real push (FCM + a server-side Cloud Function) to fully
 * close, which is left as a future step (see README).
 */
class CallListenerService : Service() {

    companion object {
        private const val TAG = "CallListenerService"
        private const val LISTENING_CHANNEL_ID = "call_listener"
        private const val INCOMING_CALL_CHANNEL_ID = "incoming_call"
        private const val LISTENING_NOTIFICATION_ID = 1001
        private const val INCOMING_CALL_NOTIFICATION_ID = 1002

        const val ACTION_DECLINE = "com.whatschat.app.action.DECLINE_CALL"
        const val EXTRA_CALL_ID = "callId"

        /**
         * Best-effort: on some Android versions/OEMs, starting a foreground
         * service can throw synchronously (e.g. background-start
         * restrictions) — this runs on the UI thread when called from
         * [com.whatschat.app.ui.navigation.WhatsChatNavGraph], so an
         * uncaught exception here would crash the whole app on launch.
         * Background call ringing is a nice-to-have, never worth that.
         */
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, CallListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "Failed to start the call-listening service", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallListenerService::class.java)) }
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> stopRinging() }
    private val serviceScope = CoroutineScope(SupervisorJob() + exceptionHandler)
    private var listenJob: Job? = null
    private var ringingCallId: String? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val callRepository = CallRepository()
    private val userRepository = UserRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val startResult = runCatching {
            createNotificationChannels()
            startForeground(LISTENING_NOTIFICATION_ID, listeningNotification())
        }
        startResult.onFailure { Log.w(TAG, "Failed to become a foreground service", it) }

        if (startResult.isFailure) {
            // Couldn't become a foreground service on this device — bail out
            // quietly rather than risk a crash loop; background call ringing
            // just won't be available until this is diagnosed further.
            stopSelf()
            return
        }

        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            if (intent?.action == ACTION_DECLINE) {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                if (callId != null) {
                    serviceScope.launch { callRepository.updateStatus(callId, CallStatus.DECLINED) }
                }
                stopRinging()
            } else {
                ensureListening()
            }
        }
        return START_STICKY
    }

    private fun ensureListening() {
        if (listenJob != null) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            stopSelf()
            return
        }
        listenJob = serviceScope.launch {
            callRepository.observeIncomingCalls(uid).collect { call ->
                if (call == null) {
                    stopRinging()
                } else if (call.callId != ringingCallId) {
                    ringingCallId = call.callId
                    showIncomingCallNotification(call)
                }
            }
        }
    }

    private suspend fun showIncomingCallNotification(call: Call) {
        val caller = userRepository.getUser(call.callerId)
        val callerName = caller?.name?.ifBlank { null } ?: "Someone"

        val fullScreenIntent = MainActivity.incomingCallIntent(
            context = this,
            callId = call.callId,
            callerId = call.callerId,
            callerName = callerName,
            callerPhoto = caller?.photoUrl.orEmpty(),
            isVideo = call.isVideo
        )
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, call.callId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, CallListenerService::class.java).apply {
            action = ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, call.callId)
        }
        val declinePendingIntent = PendingIntent.getService(
            this, call.callId.hashCode() + 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (call.isVideo) "Incoming video call" else "Incoming call"
        val notification = NotificationCompat.Builder(this, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Accept", fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
        startRinging()
    }

    private fun startRinging() {
        stopRingtoneOnly()
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@CallListenerService, uri)
                isLooping = true
                prepare()
                start()
            }
        }
        val pattern = longArrayOf(0, 800, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopRingtoneOnly() {
        runCatching {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        }
        ringtonePlayer = null
    }

    private fun stopRinging() {
        ringingCallId = null
        stopRingtoneOnly()
        vibrator?.cancel()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    private fun listeningNotification(): Notification =
        NotificationCompat.Builder(this, LISTENING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WhatsChat")
            .setContentText("Listening for calls")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                LISTENING_CHANNEL_ID, "Call listener", NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Keeps WhatsChat ready to ring for incoming calls" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                INCOMING_CALL_CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when someone calls you on WhatsChat"
                setSound(null, null) // ringtone is played manually so it can be looped/stopped precisely
                enableVibration(false) // vibration is triggered manually alongside the ringtone
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        listenJob?.cancel()
        serviceScope.cancel()
        stopRinging()
    }
}
