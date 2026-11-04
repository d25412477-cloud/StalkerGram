package com.stalkergram.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object ChatManager {

    private val db = FirebaseConfig.database.reference
    private val auth = FirebaseAuth.getInstance()

    private fun chatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
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

        val id = chatId(senderUid, receiverUid)
        val messageId = db.child("chats").child(id)
            .child("messages").push().key

        if (messageId == null) {
            callback(false, "Не удалось создать сообщение")
            return
        }

        val message = mapOf(
            "id" to messageId,
            "senderId" to senderUid,
            "receiverId" to receiverUid,
            "text" to text,
            "type" to "text",
            "timestamp" to ServerValue.TIMESTAMP,
            "read" to false
        )

        db.child("chats").child(id).child("messages")
            .child(messageId).setValue(message)
            .addOnSuccessListener {
                callback(true, "Сообщение отправлено")
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Ошибка отправки")
            }
    }

    fun sendImage(
        receiverUid: String,
        imageBase64: String,
        callback: (Boolean, String) -> Unit
    ) {
        val senderUid = auth.currentUser?.uid
        if (senderUid == null) {
            callback(false, "Сначала войди в аккаунт")
            return
        }

        if (imageBase64.isBlank()) {
            callback(false, "Фотография пустая")
            return
        }

        val id = chatId(senderUid, receiverUid)
        val messageId = db.child("chats").child(id)
            .child("messages").push().key

        if (messageId == null) {
            callback(false, "Не удалось создать сообщение")
            return
        }

        val message = mapOf(
            "id" to messageId,
            "senderId" to senderUid,
            "receiverId" to receiverUid,
            "text" to "",
            "type" to "image",
            "imageBase64" to imageBase64,
            "timestamp" to ServerValue.TIMESTAMP,
            "read" to false
        )

        db.child("chats").child(id).child("messages")
            .child(messageId).setValue(message)
            .addOnSuccessListener {
                callback(true, "Фотография отправлена 💚")
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Ошибка отправки фотографии")
            }
    }

    fun markMessagesRead(otherUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val id = chatId(myUid, otherUid)

        db.child("chats").child(id).child("messages")
            .get()
            .addOnSuccessListener { snapshot ->
                for (message in snapshot.children) {
                    val receiver = message.child("receiverId")
                        .getValue(String::class.java)

                    val read = message.child("read")
                        .getValue(Boolean::class.java) ?: false

                    if (receiver == myUid && !read) {
                        message.ref.child("read").setValue(true)
                    }
                }
            }
    }

    fun listenMessages(
        otherUid: String,
        onMessages: (List<DataSnapshot>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val myUid = auth.currentUser?.uid

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMessages(snapshot.children.toList())
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        if (myUid == null) {
            onError("Сначала войди в аккаунт")
            return listener
        }

        val id = chatId(myUid, otherUid)

        db.child("chats").child(id).child("messages")
            .addValueEventListener(listener)

        return listener
    }

    fun removeMessagesListener(
        otherUid: String,
        listener: ValueEventListener
    ) {
        val myUid = auth.currentUser?.uid ?: return
        val id = chatId(myUid, otherUid)

        db.child("chats").child(id).child("messages")
            .removeEventListener(listener)
    }
}
