
package com.stalkergram.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object ChatManager {

    private val db = FirebaseConfig.database.reference
    private val auth = FirebaseAuth.getInstance()

    private fun chatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) {
            "${uid1}_${uid2}"
        } else {
            "${uid2}_${uid1}"
        }
    }

    private fun saveMessage(
        receiverUid: String,
        message: Map<String, Any>,
        previewText: String,
        callback: (Boolean, String) -> Unit
    ) {
        val senderUid = auth.currentUser?.uid

        if (senderUid == null) {
            callback(false, "Сначала войди в аккаунт")
            return
        }

        if (receiverUid == senderUid) {
            callback(false, "Нельзя отправить сообщение самому себе")
            return
        }

        val id = chatId(senderUid, receiverUid)
        val messageId = db.child("chats").child(id)
            .child("messages").push().key

        if (messageId == null) {
            callback(false, "Не удалось создать сообщение")
            return
        }

        val finalMessage = message.toMutableMap()
        finalMessage["id"] = messageId

        val updates = mapOf(
            "chats/$id/members/$senderUid" to true,
            "chats/$id/members/$receiverUid" to true,
            "chats/$id/messages/$messageId" to finalMessage,

            "userChats/$senderUid/$id/otherUid" to receiverUid,
            "userChats/$senderUid/$id/lastMessage" to previewText,
            "userChats/$senderUid/$id/timestamp" to ServerValue.TIMESTAMP,
            "userChats/$senderUid/$id/unreadCount" to 0,

            "userChats/$receiverUid/$id/otherUid" to senderUid,
            "userChats/$receiverUid/$id/lastMessage" to previewText,
            "userChats/$receiverUid/$id/timestamp" to ServerValue.TIMESTAMP,
            "userChats/$receiverUid/$id/unreadCount" to ServerValue.increment(1)
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                callback(true, "Сообщение отправлено 💚")
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Ошибка отправки")
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

        val message = mapOf(
            "senderId" to senderUid,
            "receiverId" to receiverUid,
            "text" to text,
            "type" to "text",
            "timestamp" to ServerValue.TIMESTAMP,
            "read" to false,
            "status" to "sent"
        )

        saveMessage(receiverUid, message, text, callback)
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

        val message = mapOf(
            "senderId" to senderUid,
            "receiverId" to receiverUid,
            "text" to "",
            "type" to "image",
            "imageBase64" to imageBase64,
            "timestamp" to ServerValue.TIMESTAMP,
            "read" to false,
            "status" to "sent"
        )

        saveMessage(receiverUid, message, "📷 Фото", callback)
    }

    fun markMessagesRead(otherUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val id = chatId(myUid, otherUid)

        db.child("chats").child(id).child("messages")
            .get()
            .addOnSuccessListener { snapshot ->

                val updates = mutableMapOf<String, Any>()

                for (message in snapshot.children) {
                    val receiver = message.child("receiverId")
                        .getValue(String::class.java)

                    val read = message.child("read")
                        .getValue(Boolean::class.java) ?: false

                    if (receiver == myUid && !read) {
                        val key = message.key ?: continue
                        updates["$key/read"] = true
                        updates["$key/status"] = "read"
                    }
                }

                if (updates.isNotEmpty()) {
                    db.child("chats").child(id)
                        .child("messages")
                        .updateChildren(updates)
                }

                db.child("userChats").child(myUid)
                    .child(id).child("unreadCount")
                    .setValue(0)
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
