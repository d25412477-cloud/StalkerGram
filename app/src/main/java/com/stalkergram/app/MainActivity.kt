
package com.stalkergram.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
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
    private var chatListener: ValueEventListener? = null

    private var currentChatUid: String? = null
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
        root.setPadding(dp(18), dp(15), dp(18), dp(12))
        root.setBackgroundColor(bg)

        val logo = TextView(this)
        logo.text = "☢ STALKERGRAM"
        logo.textSize = 25f
        logo.setTextColor(green)
        logo.gravity = Gravity.CENTER
        root.addView(logo)

        val heading = TextView(this)
        heading.text = title
        heading.textSize = 20f
        heading.setTextColor(white)
        heading.gravity = Gravity.CENTER
        heading.setPadding(0, dp(18), 0, dp(15))
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

        val p = LinearLayout.LayoutParams(-1, dp(52))
        p.setMargins(0, dp(5), 0, dp(5))
        root.addView(e, p)
        return e
    }

    private fun makeButton(text: String, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setTextColor(bg)
        b.setBackgroundColor(green)
        b.setOnClickListener { action() }

        val p = LinearLayout.LayoutParams(-1, dp(50))
        p.setMargins(0, dp(8), 0, dp(5))
        root.addView(b, p)
    }

    private fun makeLink(text: String, action: () -> Unit) {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(green)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(14), dp(8), dp(14))
        t.setOnClickListener { action() }
        root.addView(t)
    }

    private fun stopUsersListener() {
        usersListener?.let {
            db.child("users").removeEventListener(it)
        }
        usersListener = null
    }

    private fun stopChatListener() {
        val otherUid = currentChatUid
        val listener = chatListener

        if (otherUid != null && listener != null) {
            ChatManager.removeMessagesListener(otherUid, listener)
        }

        currentChatUid = null
        chatListener = null
    }

    private fun showLogin() {
        stopUsersListener()
        stopChatListener()
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
        stopUsersListener()
        stopChatListener()
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
        stopChatListener()
        stopUsersListener()
        users.clear()

        setup("ТЫ В ЗОНЕ")

        val subtitle = TextView(this)
        subtitle.text = "ПОИСК СТАЛКЕРОВ"
        subtitle.textSize = 16f
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

                val uid = user.child("uid")
                    .getValue(String::class.java) ?: user.key ?: ""

                val item = TextView(this)
                item.text = "☢  $name\nНажми, чтобы открыть чат"
                item.textSize = 16f
                item.setTextColor(white)
                item.setPadding(dp(14), dp(15), dp(14), dp(15))
                item.setBackgroundColor(Color.rgb(25, 31, 26))

                val params = LinearLayout.LayoutParams(-1, -2)
                params.setMargins(0, dp(5), 0, dp(5))
                results.addView(item, params)

                item.setOnClickListener {
                    openChat(uid, name)
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
                updateResults(search.text.toString())
                if (search.text.isEmpty()) {
                    status.text = "Загружено аккаунтов: ${users.size}"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                status.text = "Ошибка: ${error.message}"
            }
        }

        db.child("users").addValueEventListener(usersListener!!)

        makeButton("ВЫЙТИ ИЗ АККАУНТА") {
            stopUsersListener()
            AuthManager.logout()
            showLogin()
        }

        setContentView(root)
    }

    private fun openChat(otherUid: String, otherName: String) {
        stopUsersListener()
        stopChatListener()

        currentChatUid = otherUid
        setup("ЧАТ: $otherName")

        val back = TextView(this)
        back.text = "← НАЗАД К ПОИСКУ"
        back.textSize = 15f
        back.setTextColor(green)
        back.setPadding(0, dp(8), 0, dp(12))
        back.setOnClickListener { showHome() }
        root.addView(back)

        val scroll = ScrollView(this)
        val messagesBox = LinearLayout(this)
        messagesBox.orientation = LinearLayout.VERTICAL
        scroll.addView(messagesBox)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val input = EditText(this)
        input.hint = "Напиши сообщение..."
        input.setHintTextColor(gray)
        input.setTextColor(white)
        input.textSize = 15f
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setPadding(dp(12), dp(8), dp(12), dp(8))
        input.setBackgroundColor(Color.rgb(25, 31, 26))

        val inputParams = LinearLayout.LayoutParams(-1, dp(50))
        inputParams.setMargins(0, dp(8), 0, dp(5))
        root.addView(input, inputParams)

        makeButton("ОТПРАВИТЬ") {
            sendCurrentMessage(otherUid, input)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage(otherUid, input)
                true
            } else {
                false
            }
        }

        chatListener = ChatManager.listenMessages(
            otherUid,
            { snapshots ->
                runOnUiThread {
                    if (currentChatUid != otherUid) return@runOnUiThread

                    messagesBox.removeAllViews()

                    if (snapshots.isEmpty()) {
                        val empty = TextView(this)
                        empty.text = "Пока нет сообщений. Напиши первым!"
                        empty.setTextColor(gray)
                        empty.setPadding(0, dp(15), 0, dp(15))
                        messagesBox.addView(empty)
                    }

                    snapshots.sortedBy {
                        it.child("timestamp").getValue(Long::class.java) ?: 0L
                    }.forEach { message ->
                        val sender = message.child("senderId")
                            .getValue(String::class.java) ?: ""

                        val text = message.child("text")
                            .getValue(String::class.java) ?: ""

                        val timestamp = message.child("timestamp")
                            .getValue(Long::class.java) ?: 0L

                        val time = if (timestamp > 0) {
                            android.text.format.DateFormat.format(
                                "HH:mm",
                                timestamp
                            ).toString()
                        } else {
                            "..."
                        }

                        val mine = sender ==
                            FirebaseAuth.getInstance().currentUser?.uid

                        val bubble = TextView(this)
                        bubble.text = "$text\n$time"
                        bubble.textSize = 15f
                        bubble.setTextColor(if (mine) green else white)
                        bubble.setPadding(dp(12), dp(10), dp(12), dp(10))
                        bubble.setBackgroundColor(
                            if (mine) Color.rgb(35, 55, 38)
                            else Color.rgb(25, 31, 26)
                        )

                        val params = LinearLayout.LayoutParams(
                            -1,
                            -2
                        )
                        params.setMargins(
                            0,
                            dp(4),
                            0,
                            dp(4)
                        )
                        messagesBox.addView(bubble, params)
                    }

                    scroll.post {
                        scroll.fullScroll(View.FOCUS_DOWN)
                    }
                }
            },
            { error ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )

        setContentView(root)
    }

    private fun sendCurrentMessage(
        receiverUid: String,
        input: EditText
    ) {
        val text = input.text.toString().trim()

        if (text.isEmpty()) {
            Toast.makeText(
                this,
                "Напиши сообщение",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        input.text.clear()

        ChatManager.sendMessage(receiverUid, text) { success, message ->
            runOnUiThread {
                if (!success) {
                    input.setText(text)
                }

                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        stopUsersListener()
        stopChatListener()
        super.onDestroy()
    }
}
