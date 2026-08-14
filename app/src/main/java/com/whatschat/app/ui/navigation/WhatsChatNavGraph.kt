package com.whatschat.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.whatschat.app.ui.screens.auth.LoginScreen
import com.whatschat.app.ui.screens.auth.RegisterScreen
import com.whatschat.app.ui.screens.call.CallScreen
import com.whatschat.app.ui.screens.chat.ChatScreen
import com.whatschat.app.ui.screens.chatlist.ChatListScreen
import com.whatschat.app.ui.screens.chatlist.NewChatScreen
import com.whatschat.app.ui.screens.profile.ProfileScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CHAT_LIST = "chatList"
    const val NEW_CHAT = "newChat"
    const val PROFILE = "profile"
    const val CHAT = "chat/{chatId}?name={name}&photo={photo}"
    const val CALL = "call/{otherUid}?name={name}&photo={photo}&callId={callId}"

    fun chat(chatId: String, name: String, photo: String) =
        "chat/$chatId?name=${Uri.encode(name)}&photo=${Uri.encode(photo)}"

    fun call(otherUid: String, name: String, photo: String, callId: String = "") =
        "call/$otherUid?name=${Uri.encode(name)}&photo=${Uri.encode(photo)}&callId=${Uri.encode(callId)}"
}

@Composable
fun WhatsChatNavGraph(navController: NavHostController = rememberNavController()) {
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) Routes.CHAT_LIST else Routes.LOGIN

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
                onOpenNewChat = { navController.navigate(Routes.NEW_CHAT) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onAcceptCall = { callId, callerId, callerName, callerPhoto ->
                    navController.navigate(Routes.call(callerId, callerName, callerPhoto, callId))
                }
            )
        }

        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = { navController.popBackStack() },
                onChatStarted = { chatId, name, photo ->
                    navController.navigate(Routes.chat(chatId, name, photo)) {
                        popUpTo(Routes.CHAT_LIST)
                    }
                }
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
                onStartCall = { otherUid, otherName, otherPhoto ->
                    navController.navigate(Routes.call(otherUid, otherName, otherPhoto))
                }
            )
        }

        composable(
            route = Routes.CALL,
            arguments = listOf(
                navArgument("otherUid") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("photo") { type = NavType.StringType; defaultValue = "" },
                navArgument("callId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val otherUid = backStackEntry.arguments?.getString("otherUid").orEmpty()
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            val photo = backStackEntry.arguments?.getString("photo").orEmpty()
            val callId = backStackEntry.arguments?.getString("callId").orEmpty()
            CallScreen(
                otherUid = otherUid,
                otherUserName = name,
                otherUserPhoto = photo,
                existingCallId = callId.ifBlank { null },
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
