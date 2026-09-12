package com.whatschat.app.ui.navigation

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.whatschat.app.data.model.Call
import com.whatschat.app.data.model.CallStatus
import com.whatschat.app.data.repository.CallRepository
import com.whatschat.app.data.repository.ChatRepository
import com.whatschat.app.data.repository.UserRepository
import com.whatschat.app.data.service.CallListenerService
import com.whatschat.app.ui.screens.auth.LoginScreen
import com.whatschat.app.ui.screens.auth.RegisterScreen
import com.whatschat.app.ui.screens.call.CallScreen
import com.whatschat.app.ui.screens.chat.ChatScreen
import com.whatschat.app.ui.screens.chatlist.ChatListScreen
import com.whatschat.app.ui.screens.filter.SelfieFilterScreen
import com.whatschat.app.ui.screens.profile.ProfileScreen
import com.whatschat.app.ui.screens.qr.QrScannerScreen
import com.whatschat.app.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Deep-links straight to an already-ringing call, e.g. from the incoming-call notification's Accept action. */
data class PendingCallArgs(
    val otherUid: String,
    val name: String,
    val photo: String,
    val callId: String,
    val isVideo: Boolean
)

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CHAT_LIST = "chatList"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val QR_SCAN = "qrScan"
    const val CHAT = "chat/{chatId}?name={name}&photo={photo}"
    const val CALL = "call/{otherUid}?name={name}&photo={photo}&callId={callId}&isVideo={isVideo}"
    const val FILTERS = "filters/{chatId}"

    fun chat(chatId: String, name: String, photo: String) =
        "chat/$chatId?name=${Uri.encode(name)}&photo=${Uri.encode(photo)}"

    fun filters(chatId: String) = "filters/$chatId"

    fun call(otherUid: String, name: String, photo: String, callId: String = "", isVideo: Boolean = false) =
        "call/$otherUid?name=${Uri.encode(name)}&photo=${Uri.encode(photo)}&callId=${Uri.encode(callId)}&isVideo=$isVideo"
}

@Composable
fun WhatsChatNavGraph(
    navController: NavHostController = rememberNavController(),
    pendingCall: PendingCallArgs? = null,
    onPendingCallConsumed: () -> Unit = {}
) {
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) Routes.CHAT_LIST else Routes.LOGIN
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val callRepository = remember { CallRepository() }
    val chatRepository = remember { ChatRepository() }
    val userRepository = remember { UserRepository() }

    var currentUid by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }

    // Keeps a foreground service listening for incoming calls whenever someone is
    // signed in, so a call can ring even while the app isn't the one on screen.
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUid = firebaseAuth.currentUser?.uid
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                CallListenerService.start(context)
                coroutineScope.launch { userRepository.updateTimeZone(uid, TimeZone.getDefault().id) }
            } else {
                CallListenerService.stop(context)
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(pendingCall) {
        val call = pendingCall ?: return@LaunchedEffect
        if (FirebaseAuth.getInstance().currentUser != null) {
            navController.navigate(Routes.call(call.otherUid, call.name, call.photo, call.callId, call.isVideo))
        }
        onPendingCallConsumed()
    }

    // A single, app-wide incoming-call listener + prompt, independent of which
    // screen is on top — previously this only lived on the chat list screen, so
    // a call went unnoticed in-app whenever you were inside a conversation (or
    // anywhere else) when it came in.
    var incomingCall by remember { mutableStateOf<Call?>(null) }
    var callerName by remember { mutableStateOf("") }
    var callerPhoto by remember { mutableStateOf("") }

    LaunchedEffect(currentUid) {
        val uid = currentUid
        incomingCall = null
        if (uid != null) {
            callRepository.observeIncomingCalls(uid).collect { incomingCall = it }
        }
    }

    LaunchedEffect(incomingCall?.callId) {
        val callerId = incomingCall?.callerId
        if (callerId == null) {
            callerName = ""
            callerPhoto = ""
        } else {
            val caller = userRepository.getUser(callerId)
            callerName = caller?.name.orEmpty()
            callerPhoto = caller?.photoUrl.orEmpty()
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val alreadyOnCallScreen = currentBackStackEntry?.destination?.route == Routes.CALL

    if (incomingCall != null && !alreadyOnCallScreen) {
        val call = incomingCall
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (call?.isVideo == true) "Incoming video call" else "Incoming call") },
            text = { Text((callerName.ifBlank { "Someone" }) + " is calling you") },
            confirmButton = {
                TextButton(onClick = {
                    if (call != null) {
                        incomingCall = null
                        navController.navigate(
                            Routes.call(call.callerId, callerName.ifBlank { "Unknown" }, callerPhoto, call.callId, call.isVideo)
                        )
                    }
                }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (call != null) {
                        incomingCall = null
                        coroutineScope.launch { callRepository.updateStatus(call.callId, CallStatus.DECLINED) }
                    }
                }) { Text("Decline") }
            }
        )
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onOpenChat = { chatId, name, photo ->
                    navController.navigate(Routes.chat(chatId, name, photo))
                },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onScanQr = { navController.navigate(Routes.QR_SCAN) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.QR_SCAN) {
            QrScannerScreen(
                onScanned = { uid ->
                    val myUid = FirebaseAuth.getInstance().currentUser?.uid
                    if (myUid != null) {
                        coroutineScope.launch {
                            val user = userRepository.getUser(uid)
                            val newChatId = chatRepository.getOrCreateChat(myUid, uid)
                            navController.popBackStack()
                            navController.navigate(
                                Routes.chat(newChatId, user?.name ?: "Unknown", user?.photoUrl.orEmpty())
                            )
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("photo") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            val photo = backStackEntry.arguments?.getString("photo").orEmpty()
            ChatScreen(
                chatId = chatId,
                otherUserName = name,
                otherUserPhoto = photo,
                onBack = { navController.popBackStack() },
                onStartCall = { otherUid, otherName, otherPhoto, isVideo ->
                    navController.navigate(Routes.call(otherUid, otherName, otherPhoto, isVideo = isVideo))
                },
                onOpenFilters = { chatId -> navController.navigate(Routes.filters(chatId)) }
            )
        }

        composable(
            route = Routes.FILTERS,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val filterChatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            SelfieFilterScreen(
                chatId = filterChatId,
                onSent = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CALL,
            arguments = listOf(
                navArgument("otherUid") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("photo") { type = NavType.StringType; defaultValue = "" },
                navArgument("callId") { type = NavType.StringType; defaultValue = "" },
                navArgument("isVideo") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val otherUid = backStackEntry.arguments?.getString("otherUid").orEmpty()
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            val photo = backStackEntry.arguments?.getString("photo").orEmpty()
            val callId = backStackEntry.arguments?.getString("callId").orEmpty()
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            CallScreen(
                otherUid = otherUid,
                otherUserName = name,
                otherUserPhoto = photo,
                existingCallId = callId.ifBlank { null },
                isVideoCall = isVideo,
                onCallEnded = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
