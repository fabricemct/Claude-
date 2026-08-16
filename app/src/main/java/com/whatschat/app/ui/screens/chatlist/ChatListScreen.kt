package com.whatschat.app.ui.screens.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatschat.app.data.model.Chat
import com.whatschat.app.data.model.User
import com.whatschat.app.ui.components.Avatar
import com.whatschat.app.ui.viewmodel.ChatListViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (chatId: String, otherUserName: String, otherUserPhoto: String) -> Unit,
    onOpenProfile: () -> Unit,
    onAcceptCall: (callId: String, callerId: String, callerName: String, callerPhoto: String) -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val otherUsers by viewModel.otherUsers.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()
    var callerName by remember { mutableStateOf("") }
    var callerPhoto by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(incomingCall?.callId) {
        val callerId = incomingCall?.callerId
        val caller = if (callerId != null) viewModel.getUser(callerId) else null
        callerName = caller?.name.orEmpty()
        callerPhoto = caller?.photoUrl.orEmpty()
    }

    if (incomingCall != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Incoming call") },
            text = { Text(callerName.ifBlank { "Someone" } + " is calling you") },
            confirmButton = {
                TextButton(onClick = {
                    val call = incomingCall ?: return@TextButton
                    viewModel.dismissIncomingCall()
                    onAcceptCall(call.callId, call.callerId, callerName.ifBlank { "Unknown" }, callerPhoto)
                }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declineIncomingCall() }) { Text("Decline") }
            }
        )
    }

    val chattedUids = remember(chats) { chats.mapNotNull { it.otherUser?.uid }.toSet() }
    val contactsWithoutChat = remember(otherUsers, chattedUids) {
        otherUsers.filter { it.uid !in chattedUids }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsChat") },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.Person, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { padding ->
        if (chats.isEmpty() && contactsWithoutChat.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                Text("No contacts yet. Once someone else signs up, they'll appear here.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (chats.isNotEmpty()) {
                    item(key = "chats_header") { SectionHeader("Chats") }
                    items(chats, key = { "chat_${it.chatId}" }) { chat ->
                        ChatRow(chat = chat, onClick = {
                            val user = chat.otherUser
                            onOpenChat(chat.chatId, user?.name ?: "Unknown", user?.photoUrl ?: "")
                        })
                    }
                }
                if (contactsWithoutChat.isNotEmpty()) {
                    item(key = "contacts_header") { SectionHeader("Contacts") }
                    items(contactsWithoutChat, key = { "contact_${it.uid}" }) { user ->
                        ContactRow(user = user, onClick = {
                            scope.launch {
                                val chatId = viewModel.startChatWith(user.uid)
                                if (chatId != null) onOpenChat(chatId, user.name, user.photoUrl)
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ChatRow(chat: Chat, onClick: () -> Unit) {
    val user = chat.otherUser
    ListItem(
        headlineContent = { Text(user?.name ?: "Unknown", fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(chat.lastMessage, maxLines = 1) },
        trailingContent = {
            if (chat.lastMessageTime > 0) {
                Text(
                    text = formatTimestamp(chat.lastMessageTime),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingContent = { Avatar(photoUrl = user?.photoUrl.orEmpty(), name = user?.name.orEmpty()) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ContactRow(user: User, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(user.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(user.status, maxLines = 1) },
        leadingContent = { Avatar(photoUrl = user.photoUrl, name = user.name) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
