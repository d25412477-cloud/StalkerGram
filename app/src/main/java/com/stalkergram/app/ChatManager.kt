
package com.stalkergram.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object ChatManager {

    private val db = FirebaseConfig.database.reference
    private val auth = FirebaseAuth.getInstance()

    private fun chatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) {
            "${uid1}_$uid2"
        } else {
            "${uid2}_$uid1"
        }
    }

    fun sendMessage(
        receiverUid: String,
        text: String,
        callback: (Boolean, String) -> Unit
    ) {
        val senderUid = auth.currentUser?.uid

        if (senderUid == null) {
            callback(false, "Сначала войди в аккаунт")
            return
        }

        if (text.isBlank()) {
            callback(false, "Сообщение пустое")
            return
        }

        val id = chatId(senderUid, receiverUid)
        val messageRef = db.child("chats")
            .child(id)
            .child("messages")
            .push()

        val message = mapOf(
            "id" to messageRef.key,
            "senderId" to senderUid,
            "receiverId" to receiverUid,
            "text" to text.trim(),
            "timestamp" to ServerValue.TIMESTAMP
        )

        messageRef.setValue(message)
            .addOnSuccessListener {
                callback(true, "Сообщение отправлено")
            }
            .addOnFailureListener { error ->
                callback(false, error.message ?: "Ошибка отправки")
            }
    }

    fun listenMessages(
        otherUid: String,
        onMessages: (List<DataSnapshot>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener? {

        val myUid = auth.currentUser?.uid

        if (myUid == null) {
            onError("Сначала войди в аккаунт")
            return null
        }

        val id = chatId(myUid, otherUid)
        val ref = db.child("chats")
            .child(id)
            .child("messages")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMessages(snapshot.children.toList())
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        ref.addValueEventListener(listener)
        return listener
    }

    fun removeMessagesListener(
        otherUid: String,
        listener: ValueEventListener
    ) {
        val myUid = auth.currentUser?.uid ?: return
        val id = chatId(myUid, otherUid)

        db.child("chats")
            .child(id)
            .child("messages")
            .removeEventListener(listener)
    }
}

