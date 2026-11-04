package com.stalkergram.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(12, 15, 13)
        window.navigationBarColor = Color.rgb(12, 15, 13)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(12, 15, 13))
        }

        val logo = TextView(this).apply {
            text = "☢ STALKERGRAM"
            textSize = 30f
            setTextColor(Color.rgb(130, 220, 135))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "СВЯЗЬ В ЗОНЕ"
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "ВХОД В СИСТЕМУ"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val login = EditText(this).apply {
            hint = "Имя сталкера"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(20, 16, 20, 16)
        }

        val password = EditText(this).apply {
            hint = "Пароль"
            inputType = 129
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(20, 16, 20, 16)
        }

        val button = Button(this).apply {
            text = "ВОЙТИ В ЗОНУ"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(130, 220, 135))
        }

        root.addView(logo)
        root.addView(subtitle)
        root.addView(space())
        root.addView(title)
        root.addView(login)
        root.addView(password)
        root.addView(button)

        setContentView(root)
    }

    private fun space(): TextView {
        return TextView(this).apply {
            height = 55
        }
    }
}
