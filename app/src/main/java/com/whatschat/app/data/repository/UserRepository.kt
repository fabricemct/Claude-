package com.whatschat.app.data.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.whatschat.app.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    private val usersCollection = firestore.collection("users")

    suspend fun getUser(uid: String): User? =
        usersCollection.document(uid).get().await().toObject(User::class.java)

    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val registration = usersCollection.document(uid).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toObject(User::class.java))
        }
        awaitClose { registration.remove() }
    }

    /** All registered users except the current one, ordered by name, for starting a new chat. */
    fun observeOtherUsers(currentUid: String): Flow<List<User>> = callbackFlow {
        val registration = usersCollection.orderBy("name").addSnapshotListener { snapshot, _ ->
            val users = snapshot?.toObjects(User::class.java).orEmpty().filter { it.uid != currentUid }
            trySend(users)
        }
        awaitClose { registration.remove() }
    }

    suspend fun updateProfile(uid: String, name: String, status: String, photoUri: Uri?): Result<Unit> =
        runCatching {
            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "status" to status
            )
            if (photoUri != null) {
                val ref = storage.reference.child("profile_photos/$uid/${UUID.randomUUID()}")
                ref.putFile(photoUri).await()
                updates["photoUrl"] = ref.downloadUrl.await().toString()
            }
            usersCollection.document(uid).update(updates).await()
        }
}
