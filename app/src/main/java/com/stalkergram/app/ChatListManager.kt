
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

    fun listenChats(
        callback: (List<ChatPreview>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener? {

        val myUid = auth.currentUser?.uid

        if (myUid == null) {
            onError("Сначала войди в аккаунт")
            return null
        }

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val previews = mutableListOf<ChatPreview>()

                val myChats = snapshot.children.filter { chat ->
                    val key = chat.key ?: ""
                    key.startsWith("${myUid}_") ||
                        key.endsWith("_$myUid")
                }

                if (myChats.isEmpty()) {
                    callback(emptyList())
                    return
                }

                var completed = 0

                for (chat in myChats) {
                    val key = chat.key ?: continue

                    val otherUid = if (key.startsWith("${myUid}_")) {
                        key.removePrefix("${myUid}_")
                    } else {
                        key.removeSuffix("_$myUid")
                    }

                    val messages = chat.child("messages").children.toList()

                    val last = messages.maxByOrNull {
                        it.child("timestamp")
                            .getValue(Long::class.java) ?: 0L
                    }

                    val lastMessage = last?.child("text")
                        ?.getValue(String::class.java) ?: ""

                    val timestamp = last?.child("timestamp")
                        ?.getValue(Long::class.java) ?: 0L

                    val unreadCount = messages.count { message ->
                        val receiverId = message.child("receiverId")
                            .getValue(String::class.java)

                        val read = message.child("read")
                            .getValue(Boolean::class.java) ?: false

                        receiverId == myUid && !read
                    }

                    db.child("users").child(otherUid)
                        .child("username")
                        .get()
                        .addOnSuccessListener { user ->
                            val username = user.getValue(String::class.java)
                                ?: "Пользователь"

                            previews.add(
                                ChatPreview(
                                    uid = otherUid,
                                    username = username,
                                    lastMessage = lastMessage,
                                    timestamp = timestamp,
                                    unreadCount = unreadCount
                                )
                            )

                            completed++

                            if (completed == myChats.size) {
                                callback(
                                    previews.sortedByDescending {
                                        it.timestamp
                                    }
                                )
                            }
                        }
                        .addOnFailureListener {
                            completed++

                            if (completed == myChats.size) {
                                callback(
                                    previews.sortedByDescending {
                                        it.timestamp
                                    }
                                )
                            }
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        db.child("chats").addValueEventListener(listener)
        return listener
    }

    fun removeListener(listener: ValueEventListener) {
        db.child("chats").removeEventListener(listener)
    }
}
