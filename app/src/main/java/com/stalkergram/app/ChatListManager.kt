
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
                if (!snapshot.exists()) {
                    callback(emptyList())
                    return
                }

                val previews = mutableListOf<ChatPreview>()
                val chats = snapshot.children.toList()

                if (chats.isEmpty()) {
                    callback(emptyList())
                    return
                }

                var completed = 0

                for (chat in chats) {
                    val otherUid = chat.child("otherUid")
                        .getValue(String::class.java)

                    if (otherUid.isNullOrBlank()) {
                        completed++
                        if (completed == chats.size) {
                            callback(previews.sortedByDescending { it.timestamp })
                        }
                        continue
                    }

                    val lastMessage = chat.child("lastMessage")
                        .getValue(String::class.java) ?: ""

                    val timestamp = chat.child("timestamp")
                        .getValue(Long::class.java) ?: 0L

                    val unreadCount = chat.child("unreadCount")
                        .getValue(Int::class.java) ?: 0

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

                            if (completed == chats.size) {
                                callback(
                                    previews.sortedByDescending { it.timestamp }
                                )
                            }
                        }
                        .addOnFailureListener {
                            previews.add(
                                ChatPreview(
                                    uid = otherUid,
                                    username = "Пользователь",
                                    lastMessage = lastMessage,
                                    timestamp = timestamp,
                                    unreadCount = unreadCount
                                )
                            )

                            completed++

                            if (completed == chats.size) {
                                callback(
                                    previews.sortedByDescending { it.timestamp }
                                )
                            }
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        db.child("userChats").child(myUid)
            .addValueEventListener(listener)

        return listener
    }

    fun removeListener(listener: ValueEventListener) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("userChats").child(myUid)
            .removeEventListener(listener)
    }
}
