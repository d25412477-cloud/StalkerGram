
package com.stalkergram.app

import com.google.firebase.database.FirebaseDatabase

object FirebaseConfig {

    val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(
            "https://stalkergram-default-rtdb.firebaseio.com/"
        )
    }

}
