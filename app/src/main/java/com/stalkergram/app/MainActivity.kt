package com.stalkergram.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 15, 13)
    private val green = Color.rgb(130, 220, 135)
    private val white = Color.WHITE
    private val gray = Color.rgb(133, 139, 134)
    private val db by lazy { FirebaseConfig.database.reference }

    private lateinit var root: LinearLayout
    private var usersListener: ValueEventListener? = null
    private val users = mutableListOf<DataSnapshot>()

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthManager.isLoggedIn()) {
            showHome()
        } else {
            showLogin()
        }
    }

    private fun setup(title: String) {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(20), dp(18), dp(12))
        root.setBackgroundColor(bg)

        val logo = TextView(this)
        logo.text = "☢ STALKERGRAM"
        logo.textSize = 27f
        logo.setTextColor(green)
        logo.gravity = Gravity.CENTER
        root.addView(logo)

        val heading = TextView(this)
        heading.text = title
        heading.textSize = 22f
        heading.setTextColor(white)
        heading.gravity = Gravity.CENTER
        heading.setPadding(0, dp(25), 0, dp(20))
        root.addView(heading)
    }

    private fun makeField(hint: String, password: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(gray)
        e.setTextColor(white)
        e.textSize = 16f
        e.setSingleLine(true)
        e.setPadding(dp(12), dp(8), dp(12), dp(8))
        e.setBackgroundColor(Color.rgb(25, 31, 26))

        if (password) e.inputType = 129

        val p = LinearLayout.LayoutParams(-1, dp(54))
        p.setMargins(0, dp(6), 0, dp(6))
        root.addView(e, p)
        return e
    }

    private fun makeButton(text: String, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setTextColor(bg)
        b.setBackgroundColor(green)
        b.setOnClickListener { action() }

        val p = LinearLayout.LayoutParams(-1, dp(52))
        p.setMargins(0, dp(10), 0, dp(5))
        root.addView(b, p)
    }

    private fun makeLink(text: String, action: () -> Unit) {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(green)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(16), dp(8), dp(16))
        t.setOnClickListener { action() }
        root.addView(t)
    }

    private fun showLogin() {
        setup("ВХОД В СИСТЕМУ")

        val email = makeField("Email")
        val password = makeField("Пароль", true)

        makeButton("ВОЙТИ В ЗОНУ") {
            AuthManager.login(
                email.text.toString().trim(),
                password.text.toString()
            ) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    if (success) showHome()
                }
            }
        }

        makeLink("НЕТ АККАУНТА? СОЗДАТЬ") {
            showRegister()
        }

        setContentView(root)
    }

    private fun showRegister() {
        setup("НОВЫЙ СТАЛКЕР")

        val username = makeField("Имя пользователя")
        val email = makeField("Email")
        val password = makeField("Придумай пароль", true)
        val confirm = makeField("Повтори пароль", true)

        makeButton("СОЗДАТЬ АККАУНТ") {
            val name = username.text.toString().trim()
            val mail = email.text.toString().trim()
            val pass = password.text.toString()

            when {
                name.length < 3 ->
                    Toast.makeText(this, "Минимум 3 символа в имени", Toast.LENGTH_SHORT).show()

                !android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches() ->
                    Toast.makeText(this, "Неправильный Email", Toast.LENGTH_SHORT).show()

                pass.length < 6 ->
                    Toast.makeText(this, "Пароль: минимум 6 символов", Toast.LENGTH_SHORT).show()

                pass != confirm.text.toString() ->
                    Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()

                else -> AuthManager.register(name, mail, pass) { success, message ->
                    runOnUiThread {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        if (success) showHome()
                    }
                }
            }
        }

        makeLink("УЖЕ ЕСТЬ АККАУНТ? ВОЙТИ") {
            showLogin()
        }

        setContentView(root)
    }

    private fun showHome() {
        usersListener?.let {
            db.child("users").removeEventListener(it)
        }
        usersListener = null
        users.clear()

        setup("ТЫ В ЗОНЕ")

        val subtitle = TextView(this)
        subtitle.text = "ПОИСК СТАЛКЕРОВ"
        subtitle.textSize = 17f
        subtitle.setTextColor(green)
        root.addView(subtitle)

        val search = makeField("Введи имя пользователя...")

        val status = TextView(this)
        status.text = "Загрузка пользователей..."
        status.setTextColor(gray)
        status.textSize = 13f
        root.addView(status)

        val scroll = ScrollView(this)
        val results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        scroll.addView(results)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val myUid = FirebaseAuth.getInstance().currentUser?.uid

        if (myUid == null) {
            showLogin()
            return
        }

        fun updateResults(query: String) {
            results.removeAllViews()

            if (query.isBlank()) {
                status.text = "Введи имя для поиска"
                return
            }

            val matches = users.filter { user ->
                val name = user.child("username")
                    .getValue(String::class.java) ?: ""
                val id = user.child("uid")
                    .getValue(String::class.java) ?: user.key ?: ""

                name.contains(query, ignoreCase = true) && id != myUid
            }

            status.text = "Найдено: ${matches.size}"

            if (matches.isEmpty()) {
                val empty = TextView(this)
                empty.text = "Никого не найдено"
                empty.setTextColor(gray)
                empty.textSize = 16f
                empty.setPadding(0, dp(15), 0, dp(15))
                results.addView(empty)
            }

            matches.forEach { user ->
                val name = user.child("username")
                    .getValue(String::class.java) ?: "Сталкер"

                val item = TextView(this)
                item.text = "☢  $name"
                item.textSize = 17f
                item.setTextColor(white)
                item.setPadding(dp(14), dp(18), dp(14), dp(18))
                item.setBackgroundColor(Color.rgb(25, 31, 26))

                val params = LinearLayout.LayoutParams(-1, -2)
                params.setMargins(0, dp(5), 0, dp(5))
                results.addView(item, params)

                item.setOnClickListener {
                    Toast.makeText(
                        this,
                        "Пользователь: $name",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                updateResults(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                users.clear()
                users.addAll(snapshot.children.toList())

                status.text = "Загружено аккаунтов: ${users.size}"
                updateResults(search.text.toString())
            }

            override fun onCancelled(error: DatabaseError) {
                status.text = "Ошибка: ${error.message}"
            }
        }

        db.child("users").addValueEventListener(usersListener!!)

        makeButton("ВЫЙТИ ИЗ АККАУНТА") {
            usersListener?.let {
                db.child("users").removeEventListener(it)
            }
            usersListener = null
            AuthManager.logout()
            showLogin()
        }

        setContentView(root)
    }

    override fun onDestroy() {
        usersListener?.let {
            db.child("users").removeEventListener(it)
        }
        usersListener = null
        super.onDestroy()
    }
}
