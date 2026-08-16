package com.whatschat.app.data.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.whatschat.app.data.model.Chat
import com.whatschat.app.data.model.Message
import com.whatschat.app.data.model.MessageType
import com.whatschat.app.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class ChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val userRepository: UserRepository = UserRepository()
) {
    private val chatsCollection = firestore.collection("chats")

    companion object {
        private const val TYPING_TIMEOUT_MS = 6000L
    }

    /** Deterministic chat id so two users always land in the same conversation. */
    private fun chatIdFor(uidA: String, uidB: String) =
        listOf(uidA, uidB).sorted().joinToString("_")

    suspend fun getOrCreateChat(currentUid: String, otherUid: String): String {
        val chatId = chatIdFor(currentUid, otherUid)
        val doc = chatsCollection.document(chatId)
        if (!doc.get().await().exists()) {
            val chat = Chat(chatId = chatId, participants = listOf(currentUid, otherUid))
            doc.set(chat).await()
        }
        return chatId
    }

    fun observeChats(currentUid: String): Flow<List<Chat>> = callbackFlow {
        val registration = chatsCollection
            .whereArrayContains("participants", currentUid)
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObjects(Chat::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }.map { chats ->
        chats.map { chat ->
            val otherUid = chat.participants.firstOrNull { it != currentUid }
            val otherUser = otherUid?.let { userRepository.getUser(it) }
            chat.copy(otherUser = otherUser)
        }
    }

    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val registration = chatsCollection.document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObjects(Message::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendTextMessage(chatId: String, senderId: String, text: String) {
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = senderId,
            text = text,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis()
        )
        saveMessage(chatId, senderId, message, previewText = text)
    }

    suspend fun sendImageMessage(chatId: String, senderId: String, imageUri: Uri) {
        val ref = storage.reference.child("chat_images/$chatId/${UUID.randomUUID()}")
        ref.putFile(imageUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = senderId,
            imageUrl = downloadUrl,
            type = MessageType.IMAGE,
            timestamp = System.currentTimeMillis()
        )
        saveMessage(chatId, senderId, message, previewText = "📷 Photo")
    }

    suspend fun sendAudioMessage(chatId: String, senderId: String, audioFile: File, durationMs: Long) {
        val ref = storage.reference.child("chat_audio/$chatId/${UUID.randomUUID()}.wav")
        ref.putFile(Uri.fromFile(audioFile)).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = senderId,
            audioUrl = downloadUrl,
            audioDurationMs = durationMs,
            type = MessageType.AUDIO,
            timestamp = System.currentTimeMillis()
        )
        saveMessage(chatId, senderId, message, previewText = "🎤 Voice message")
    }

    suspend fun sendSticker(chatId: String, senderId: String, emoji: String) {
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = senderId,
            text = emoji,
            type = MessageType.STICKER,
            timestamp = System.currentTimeMillis()
        )
        saveMessage(chatId, senderId, message, previewText = emoji)
    }

    suspend fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        val updates = if (isTyping) {
            mapOf("typingUid" to uid, "typingUpdatedAt" to System.currentTimeMillis())
        } else {
            mapOf("typingUid" to "", "typingUpdatedAt" to 0L)
        }
        runCatching { chatsCollection.document(chatId).update(updates).await() }
    }

    /** Emits the uid currently typing in this chat, or "" if nobody is (or the status is stale). */
    fun observeTyping(chatId: String): Flow<String> = callbackFlow {
        val registration = chatsCollection.document(chatId).addSnapshotListener { snapshot, _ ->
            val typingUid = snapshot?.getString("typingUid").orEmpty()
            val updatedAt = snapshot?.getLong("typingUpdatedAt") ?: 0L
            val isStale = System.currentTimeMillis() - updatedAt > TYPING_TIMEOUT_MS
            trySend(if (isStale) "" else typingUid)
        }
        awaitClose { registration.remove() }
    }

    private suspend fun saveMessage(chatId: String, senderId: String, message: Message, previewText: String) {
        val chatDoc = chatsCollection.document(chatId)
        chatDoc.collection("messages").document(message.messageId).set(message).await()
        chatDoc.update(
            mapOf(
                "lastMessage" to previewText,
                "lastMessageTime" to message.timestamp,
                "lastMessageSenderId" to senderId,
                "typingUid" to "",
                "typingUpdatedAt" to 0L
            )
        ).await()
    }
}
