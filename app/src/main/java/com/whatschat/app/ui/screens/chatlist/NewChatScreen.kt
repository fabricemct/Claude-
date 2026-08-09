package com.whatschat.app.ui.screens.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatschat.app.ui.components.Avatar
import com.whatschat.app.ui.viewmodel.ChatListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onChatStarted: (chatId: String, otherUserName: String, otherUserPhoto: String) -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val users by viewModel.otherUsers.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Select a contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            items(users, key = { it.uid }) { user ->
                ListItem(
                    headlineContent = { Text(user.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(user.status, maxLines = 1) },
                    leadingContent = { Avatar(photoUrl = user.photoUrl, name = user.name) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            val chatId = viewModel.startChatWith(user.uid)
                            if (chatId != null) onChatStarted(chatId, user.name, user.photoUrl)
                        }
                    }
                )
            }
        }
    }
}
