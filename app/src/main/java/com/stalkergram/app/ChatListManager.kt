
package com.stalkergram.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class ChatPreview(
    val uid: String,
    val username: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int
)

object ChatListManager {

    private val db = FirebaseConfig.database.reference
    private val auth = FirebaseAuth.getInstance()

    private val messageListeners =
        mutableMapOf<String, ValueEventListener>()

    fun listenChats(
        callback: (List<ChatPreview>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener? {

        val myUid = auth.currentUser?.uid

        if (myUid == null) {
            onError("Сначала войди в аккаунт")
            return null
        }

        removeAllMessageListeners()

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                removeAllMessageListeners()

                val chats = snapshot.children.toList()

                if (chats.isEmpty()) {
                    callback(emptyList())
                    return
                }

                val previews = mutableMapOf<String, ChatPreview>()
                val pending = mutableSetOf<String>()

                for (chat in chats) {
                    val chatId = chat.key ?: continue
                    val otherUid = chat.child("otherUid")
                        .getValue(String::class.java)

                    if (otherUid.isNullOrBlank()) continue

                    pending.add(chatId)

                    db.child("users")
                        .child(otherUid)
                        .child("username")
                        .get()
                        .addOnSuccessListener { user ->

                            val username =
                                user.getValue(String::class.java)
                                    ?: "Пользователь"

                            val messagesRef = db.child("chats")
                                .child(chatId)
                                .child("messages")

                            val messagesListener =
                                object : ValueEventListener {

                                    override fun onDataChange(
                                        messages: DataSnapshot
                                    ) {
                                        var lastText = ""
                                        var lastTime = 0L
                                        var unread = 0

                                        for (message in messages.children) {
                                            val sender = message.child("senderId")
                                                .getValue(String::class.java)

                                            val receiver = message.child("receiverId")
                                                .getValue(String::class.java)

                                            val text = message.child("text")
                                                .getValue(String::class.java) ?: ""

                                            val type = message.child("type")
                                                .getValue(String::class.java)

                                            val time = message.child("timestamp")
                                                .getValue(Long::class.java) ?: 0L

                                            val read = message.child("read")
                                                .getValue(Boolean::class.java) ?: false

                                            if (time >= lastTime) {
                                                lastTime = time
                                                lastText = if (type == "image") {
                                                    "📷 Фото"
                                                } else {
                                                    text
                                                }
                                            }

                                            if (receiver == myUid &&
                                                sender == otherUid &&
                                                !read
                                            ) {
                                                unread++
                                            }
                                        }

                                        previews[chatId] = ChatPreview(
                                            uid = otherUid,
                                            username = username,
                                            lastMessage = lastText,
                                            timestamp = lastTime,
                                            unreadCount = unread
                                        )

                                        pending.remove(chatId)

                                        if (pending.isEmpty()) {
                                            callback(
                                                previews.values.sortedByDescending {
                                                    it.timestamp
                                                }
                                            )
                                        }
                                    }

                                    override fun onCancelled(
                                        error: DatabaseError
                                    ) {
                                        onError(error.message)
                                    }
                                }

                            messageListeners[chatId] = messagesListener
                            messagesRef.addValueEventListener(messagesListener)
                        }
                        .addOnFailureListener {
                            pending.remove(chatId)

                            if (pending.isEmpty()) {
                                callback(
                                    previews.values.sortedByDescending {
                                        it.timestamp
                                    }
                                )
                            }
                        }
                }

                if (pending.isEmpty()) {
                    callback(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        db.child("userChats")
            .child(myUid)
            .addValueEventListener(listener)

        return listener
    }

    private fun removeAllMessageListeners() {
        val myUid = auth.currentUser?.uid ?: return

        for ((chatId, listener) in messageListeners) {
            db.child("chats")
                .child(chatId)
                .child("messages")
                .removeEventListener(listener)
        }

        messageListeners.clear()
    }

    fun removeListener(listener: ValueEventListener) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("userChats")
            .child(myUid)
            .removeEventListener(listener)

        removeAllMessageListeners()
    }
}
