
package com.stalkergram.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ServerValue

object AuthManager {

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    fun register(
        username: String,
        email: String,
        password: String,
        callback: (Boolean, String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    val user = auth.currentUser

                    if (user == null) {
                        callback(false, "Не удалось получить аккаунт")
                        return@addOnCompleteListener
                    }

                    val profile = mapOf(
                        "uid" to user.uid,
                        "username" to username,
                        "email" to email,
                        "createdAt" to ServerValue.TIMESTAMP
                    )

                    FirebaseConfig.database
                        .reference
                        .child("users")
                        .child(user.uid)
                        .setValue(profile)
                        .addOnSuccessListener {
                            callback(true, "Аккаунт создан!")
                        }
                        .addOnFailureListener { error ->
                            callback(false, error.message ?: "Ошибка сохранения профиля")
                        }

                } else {
                    callback(
                        false,
                        task.exception?.message ?: "Ошибка регистрации"
                    )
                }
            }
    }

    fun login(
        email: String,
        password: String,
        callback: (Boolean, String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    callback(true, "Вход выполнен!")
                } else {
                    callback(
                        false,
                        task.exception?.message ?: "Ошибка входа"
                    )
                }
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}
