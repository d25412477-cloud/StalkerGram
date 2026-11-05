
package com.stalkergram.app

import com.google.firebase.database.*

object PresenceManager {

    private val db = FirebaseConfig.database.reference

    private var connectedRef: DatabaseReference? = null
    private var connectionListener: ValueEventListener? = null
    private var currentUid: String? = null

    fun start(uid: String) {
        if (currentUid == uid && connectionListener != null) return

        stop()
        currentUid = uid

        val presenceRef = db.child("users")
            .child(uid)
            .child("presence")

        val ref = db.child(".info/connected")

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {

                    presenceRef.onDisconnect()
                        .updateChildren(
                            mapOf(
                                "online" to false,
                                "lastSeen" to ServerValue.TIMESTAMP
                            )
                        )
                        .addOnSuccessListener {
                            presenceRef.updateChildren(
                                mapOf(
                                    "online" to true,
                                    "lastSeen" to ServerValue.TIMESTAMP
                                )
                            )
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        }

        connectedRef = ref
        connectionListener = listener
        ref.addValueEventListener(listener)
    }

    fun stop() {
        val uid = currentUid

        if (uid != null) {
            db.child("users")
                .child(uid)
                .child("presence")
                .updateChildren(
                    mapOf(
                        "online" to false,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                )
        }

        if (connectedRef != null && connectionListener != null) {
            connectedRef!!.removeEventListener(connectionListener!!)
        }

        connectedRef = null
        connectionListener = null
        currentUid = null
    }
}
