package com.whatschat.app.ui.screens.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatschat.app.data.model.Chat
import com.whatschat.app.ui.components.Avatar
import com.whatschat.app.ui.viewmodel.ChatListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (chatId: String, otherUserName: String, otherUserPhoto: String) -> Unit,
    onOpenNewChat: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val chats by viewModel.chats.collectAsState()

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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenNewChat) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
        }
    ) { padding ->
        if (chats.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                Text("No conversations yet. Tap + to message someone.")
            }
        } else {
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                items(chats, key = { it.chatId }) { chat ->
                    ChatRow(chat = chat, onClick = {
                        val user = chat.otherUser
                        onOpenChat(chat.chatId, user?.name ?: "Unknown", user?.photoUrl ?: "")
                    })
                }
            }
        }
    }
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

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
