package com.stalkergram.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class ChatPreview(
    val uid: String,
    val username: String,
    val lastMessage: String,
    val timestamp: Long
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

                    val last = chat.child("messages").children.maxByOrNull {
                        it.child("timestamp").getValue(Long::class.java) ?: 0L
                    }

                    val lastMessage = last?.child("text")
                        ?.getValue(String::class.java) ?: ""

                    val timestamp = last?.child("timestamp")
                        ?.getValue(Long::class.java) ?: 0L

                    db.child("users").child(otherUid)
                        .addListenerForSingleValueEvent(
                            object : ValueEventListener {
                                override fun onDataChange(user: DataSnapshot) {
                                    val username = user.child("username")
                                        .getValue(String::class.java)
                                        ?: "Пользователь"

                                    previews.add(
                                        ChatPreview(
                                            otherUid,
                                            username,
                                            lastMessage,
                                            timestamp
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

                                override fun onCancelled(error: DatabaseError) {
                                    onError(error.message)
                                }
                            }
                        )
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
