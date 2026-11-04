
package com.stalkergram.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 15, 13)
    private val green = Color.rgb(130, 220, 135)
    private val white = Color.WHITE
    private val gray = Color.rgb(133, 139, 134)

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLogin()
    }

    private fun setupScreen(title: String, subtitle: String) {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(28, 24, 28, 24)
        root.setBackgroundColor(bg)

        val logo = TextView(this)
        logo.text = "☢ STALKERGRAM"
        logo.textSize = 30f
        logo.setTextColor(green)
        logo.typeface = Typeface.DEFAULT_BOLD
        logo.gravity = Gravity.CENTER
        root.addView(logo)

        val heading = TextView(this)
        heading.text = title
        heading.textSize = 23f
        heading.setTextColor(white)
        heading.gravity = Gravity.CENTER
        heading.setPadding(0, 35, 0, 8)
        root.addView(heading)

        val info = TextView(this)
        info.text = subtitle
        info.textSize = 14f
        info.setTextColor(gray)
        info.gravity = Gravity.CENTER
        info.setPadding(0, 0, 0, 25)
        root.addView(info)

        setContentView(root)
    }

    private fun addField(hint: String, password: Boolean = false): EditText {
        val field = EditText(this)
        field.hint = hint
        field.setHintTextColor(gray)
        field.setTextColor(white)
        field.textSize = 16f
        field.setSingleLine(true)
        field.setPadding(16, 12, 16, 12)
        field.setBackgroundColor(Color.rgb(25, 31, 26))

        if (password) {
            field.inputType = 129
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            58
        )
        params.setMargins(0, 7, 0, 7)
        root.addView(field, params)
        return field
    }

    private fun addButton(text: String, action: () -> Unit) {
        val button = Button(this)
        button.text = text
        button.setTextColor(bg)
        button.textSize = 15f
        button.setBackgroundColor(green)
        button.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            58
        )
        params.setMargins(0, 16, 0, 8)
        root.addView(button, params)
    }

    private fun addLink(text: String, action: () -> Unit) {
        val link = TextView(this)
        link.text = text
        link.textSize = 15f
        link.setTextColor(green)
        link.gravity = Gravity.CENTER
        link.setPadding(10, 18, 10, 18)
        link.setOnClickListener { action() }
        root.addView(link)
    }

    private fun showLogin() {
        setupScreen("ВХОД В СИСТЕМУ", "СВЯЗЬ В ЗОНЕ")

        val username = addField("Имя пользователя")
        val password = addField("Пароль", true)

        addButton("ВОЙТИ В ЗОНУ") {
            Toast.makeText(this, "Авторизация появится после подключения сервера", Toast.LENGTH_LONG).show()
        }

        addLink("НЕТ АККАУНТА? СОЗДАТЬ") {
            showRegister()
        }
    }

    private fun showRegister() {
        setupScreen("НОВЫЙ СТАЛКЕР", "РЕГИСТРАЦИЯ В СИСТЕМЕ")

        val username = addField("Придумай имя пользователя")
        val password = addField("Придумай пароль", true)
        val confirm = addField("Повтори пароль", true)

        addButton("СОЗДАТЬ АККАУНТ") {
            val name = username.text.toString().trim()
            val pass = password.text.toString()
            val repeat = confirm.text.toString()

            when {
                name.length < 3 ->
                    Toast.makeText(this, "Имя: минимум 3 символа", Toast.LENGTH_SHORT).show()

                pass.length < 6 ->
                    Toast.makeText(this, "Пароль: минимум 6 символов", Toast.LENGTH_SHORT).show()

                pass != repeat ->
                    Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()

                else ->
                    Toast.makeText(this, "Форма готова. Сервер регистрации добавим следующим этапом.", Toast.LENGTH_LONG).show()
            }
        }

        addLink("УЖЕ ЕСТЬ АККАУНТ? ВОЙТИ") {
            showLogin()
        }
    }
}
