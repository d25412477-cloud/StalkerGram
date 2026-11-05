
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

        val ref = db.child("userChats").child(myUid)

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val previews = mutableListOf<ChatPreview>()
                val entries = snapshot.children.toList()

                if (entries.isEmpty()) {
                    callback(emptyList())
                    return
                }

                var completed = 0

                for (entry in entries) {
                    val otherUid = entry.child("otherUid")
                        .getValue(String::class.java) ?: continue

                    val lastMessage = entry.child("lastMessage")
                        .getValue(String::class.java) ?: ""

                    val timestamp = entry.child("timestamp")
                        .getValue(Long::class.java) ?: 0L

                    val unreadCount = entry.child("unreadCount")
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

                            if (completed == entries.size) {
                                callback(
                                    previews.sortedByDescending {
                                        it.timestamp
                                    }
                                )
                            }
                        }
                        .addOnFailureListener {
                            completed++

                            if (completed == entries.size) {
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

        ref.addValueEventListener(listener)
        return listener
    }

    fun removeListener(listener: ValueEventListener) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("userChats").child(myUid)
            .removeEventListener(listener)
    }
}
