package com.whatschat.app.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.whatschat.app.data.model.Call
import com.whatschat.app.data.model.CallStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import java.util.UUID

private const val TAG = "CallRepository"

/**
 * Firestore-backed signaling channel for 1:1 WebRTC voice calls. Firestore
 * only carries the SDP offer/answer and trickled ICE candidates needed to
 * establish the peer-to-peer connection; the actual audio never touches it.
 */
class CallRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val callsCollection = firestore.collection("calls")

    suspend fun createCall(callerId: String, calleeId: String, isVideo: Boolean = false): String {
        val callId = UUID.randomUUID().toString()
        val call = Call(
            callId = callId,
            callerId = callerId,
            calleeId = calleeId,
            status = CallStatus.RINGING.name,
            isVideo = isVideo,
            createdAt = System.currentTimeMillis()
        )
        callsCollection.document(callId).set(call).await()
        Log.d(TAG, "createCall: callId=$callId callerId=$callerId calleeId=$calleeId isVideo=$isVideo")
        return callId
    }

    /** The single active incoming call for this user, if any. */
    fun observeIncomingCalls(myUid: String): Flow<Call?> = callbackFlow {
        val registration = callsCollection
            .whereEqualTo("calleeId", myUid)
            .whereEqualTo("status", CallStatus.RINGING.name)
            .addSnapshotListener { snapshot, error ->
                // A silently-dropped error here (e.g. a security-rules rejection) previously
                // looked identical to "no incoming call" — nothing happened, with no way to
                // tell why. Logging it is the only way to see a rules/permission problem.
                if (error != null) {
                    Log.w(TAG, "observeIncomingCalls failed for uid=$myUid", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                val call = snapshot?.documents?.firstOrNull()
                    ?.toObject(Call::class.java)?.copy(callId = snapshot.documents.first().id)
                Log.d(
                    TAG,
                    "observeIncomingCalls: uid=$myUid matchingDocs=${snapshot?.documents?.size ?: 0} " +
                        "found=${call != null}"
                )
                trySend(call)
            }
        awaitClose { registration.remove() }
    }

    fun observeCall(callId: String): Flow<Call?> = callbackFlow {
        val registration = callsCollection.document(callId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeCall failed for callId=$callId", error)
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Call::class.java)?.copy(callId = snapshot.id))
        }
        awaitClose { registration.remove() }
    }

    suspend fun setOffer(callId: String, sdp: String) {
        callsCollection.document(callId).update("offerSdp", sdp).await()
    }

    suspend fun setAnswer(callId: String, sdp: String) {
        callsCollection.document(callId).update(
            mapOf("answerSdp" to sdp, "status" to CallStatus.ACCEPTED.name)
        ).await()
    }

    suspend fun updateStatus(callId: String, status: CallStatus) {
        callsCollection.document(callId).update("status", status.name).await()
    }

    suspend fun addIceCandidate(callId: String, role: String, candidate: IceCandidate) {
        callsCollection.document(callId).collection(role).add(
            mapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp
            )
        ).await()
    }

    fun observeIceCandidates(callId: String, role: String): Flow<IceCandidate> = callbackFlow {
        val registration = callsCollection.document(callId).collection(role)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        trySend(
                            IceCandidate(
                                data["sdpMid"] as? String,
                                ((data["sdpMLineIndex"] as? Long) ?: 0L).toInt(),
                                data["candidate"] as? String
                            )
                        )
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun deleteCall(callId: String) {
        runCatching { callsCollection.document(callId).delete().await() }
    }
}
