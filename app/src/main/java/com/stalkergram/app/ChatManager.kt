package com.stalkergram.app

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage

object ChatManager {

    private val db = FirebaseConfig.database.reference
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private fun chatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) {
            "${uid1}_$uid2"
        } else {
            "${uid2}_$uid1"
        }
    }

    // ОТПРАВКА ТЕКСТОВОГО СООБЩЕНИЯ
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
            "type" to "text",
            "timestamp" to ServerValue.TIMESTAMP,
            "read" to false
        )

        messageRef.setValue(message)
            .addOnSuccessListener {
                callback(true, "Сообщение отправлено")
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Ошибка отправки")
            }
    }

    // ОТПРАВКА ФОТОГРАФИИ
    fun sendImage(
        receiverUid: String,
        imageUri: Uri,
        callback: (Boolean, String) -> Unit
    ) {
        val senderUid = auth.currentUser?.uid

        if (senderUid == null) {
            callback(false, "Сначала войди в аккаунт")
            return
        }

        val id = chatId(senderUid, receiverUid)

        val messageRef = db.child("chats")
            .child(id)
            .child("messages")
            .push()

        val messageId = messageRef.key ?: run {
            callback(false, "Не удалось создать сообщение")
            return
        }

        val imageRef = storage.reference
            .child("chat_images")
            .child(id)
            .child("$messageId.jpg")

        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                imageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->

                        val message = mapOf(
                            "id" to messageId,
                            "senderId" to senderUid,
                            "receiverId" to receiverUid,
                            "text" to "",
                            "imageUrl" to downloadUri.toString(),
                            "type" to "image",
                            "timestamp" to ServerValue.TIMESTAMP,
                            "read" to false
                        )

                        messageRef.setValue(message)
                            .addOnSuccessListener {
                                callback(true, "Фотография отправлена 💚")
                            }
                            .addOnFailureListener {
                                callback(false, it.message ?: "Ошибка сохранения фото")
                            }
                    }
                    .addOnFailureListener {
                        callback(false, it.message ?: "Не удалось получить ссылку")
                    }
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Ошибка загрузки фотографии")
            }
    }

    // ОТМЕТКА О ПРОЧТЕНИИ
    fun markMessagesRead(otherUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val id = chatId(currentUid, otherUid)

        db.child("chats")
            .child(id)
            .child("messages")
            .get()
            .addOnSuccessListener { snapshot ->

                for (message in snapshot.children) {
                    val receiver = message.child("receiverId")
                        .getValue(String::class.java)

                    val read = message.child("read")
                        .getValue(Boolean::class.java) ?: false

                    if (receiver == currentUid && !read) {
                        message.ref.child("read").setValue(true)
                    }
                }
            }
    }

    // ПОЛУЧЕНИЕ СООБЩЕНИЙ В РЕАЛЬНОМ ВРЕМЕНИ
    fun listenMessages(
        otherUid: String,
        onMessages: (List<DataSnapshot>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener? {

        val currentUid = auth.currentUser?.uid ?: run {
            onError("Сначала войди в аккаунт")
            return null
        }

        val id = chatId(currentUid, otherUid)

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

    // УДАЛЕНИЕ СЛУШАТЕЛЯ СООБЩЕНИЙ
    fun removeMessagesListener(
        otherUid: String,
        listener: ValueEventListener
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val id = chatId(currentUid, otherUid)

        db.child("chats")
            .child(id)
            .child("messages")
            .removeEventListener(listener)
    }
}
