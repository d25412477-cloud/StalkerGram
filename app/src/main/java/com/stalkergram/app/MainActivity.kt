package com.stalkergram.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 15, 13)
    private val panel = Color.rgb(25, 31, 26)
    private val green = Color.rgb(130, 220, 135)
    private val white = Color.WHITE
    private val gray = Color.rgb(133, 139, 134)

    private val db by lazy { FirebaseConfig.database.reference }
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var root: LinearLayout
    private var activeRef: DatabaseReference? = null
    private var activeListener: ValueEventListener? = null
    private var usersListener: ValueEventListener? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun clearListener() {
        activeRef?.let { ref ->
            activeListener?.let { ref.removeEventListener(it) }
        }
        activeRef = null
        activeListener = null
    }

    private fun setup(title: String, subtitle: String) {
        clearListener()

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(18), dp(18), dp(12))
        root.setBackgroundColor(bg)

        val logo = TextView(this)
        logo.text = "☢ STALKERGRAM"
        logo.textSize = 26f
        logo.setTextColor(green)
        logo.typeface = Typeface.DEFAULT_BOLD
        logo.gravity = Gravity.CENTER
        root.addView(logo)

        val heading = TextView(this)
        heading.text = title
        heading.textSize = 21f
        heading.setTextColor(white)
        heading.gravity = Gravity.CENTER
        heading.setPadding(0, dp(16), 0, dp(5))
        root.addView(heading)

        val info = TextView(this)
        info.text = subtitle
        info.textSize = 13f
        info.setTextColor(gray)
        info.gravity = Gravity.CENTER
        info.setPadding(0, 0, 0, dp(12))
        root.addView(info)
    }

    private fun field(hint: String, password: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(gray)
        e.setTextColor(white)
        e.textSize = 16f
        e.setSingleLine(true)
        e.setPadding(dp(14), dp(10), dp(14), dp(10))
        e.setBackgroundColor(panel)
        if (password) e.inputType = 129

        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        )
        p.setMargins(0, dp(6), 0, dp(6))
        root.addView(e, p)
        return e
    }

    private fun button(text: String, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setTextColor(bg)
        b.setBackgroundColor(green)
        b.setOnClickListener { action() }

        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        )
        p.setMargins(0, dp(8), 0, dp(6))
        root.addView(b, p)
    }

    private fun link(text: String, action: () -> Unit) {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(green)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(14), dp(8), dp(14))
        t.setOnClickListener { action() }
        root.addView(t)
    }

    private fun section(parent: LinearLayout, text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 17f
        t.setTextColor(green)
        t.typeface = Typeface.DEFAULT_BOLD
        t.setPadding(0, dp(12), 0, dp(8))
        parent.addView(t)
    }

    private fun row(
        parent: LinearLayout,
        name: String,
        detail: String,
        action: () -> Unit
    ) {
        val t = TextView(this)
        t.text = "☢  $name\n     $detail"
        t.textSize = 15f
        t.setTextColor(white)
        t.setPadding(dp(14), dp(14), dp(10), dp(14))
        t.setBackgroundColor(panel)

        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(0, dp(4), 0, dp(4))
        parent.addView(t, p)
        t.setOnClickListener { action() }
    }

    private fun showLogin() {
        setup("ВХОД В СИСТЕМУ", "СВЯЗЬ В ЗОНЕ")

        val email = field("Email")
        val password = field("Пароль", true)

        button("ВОЙТИ В ЗОНУ") {
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

        link("НЕТ АККАУНТА? СОЗДАТЬ") { showRegister() }
        setContentView(root)
    }

    private fun showRegister() {
        setup("НОВЫЙ СТАЛКЕР", "РЕГИСТРАЦИЯ В СИСТЕМЕ")

        val username = field("Имя пользователя")
        val email = field("Email")
        val password = field("Придумай пароль", true)
        val confirm = field("Повтори пароль", true)

        button("СОЗДАТЬ АККАУНТ") {
            val name = username.text.toString().trim()
            val mail = email.text.toString().trim()
            val pass = password.text.toString()

            when {
                name.length < 3 ->
                    Toast.makeText(this, "Имя: минимум 3 символа", Toast.LENGTH_SHORT).show()
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

        link("УЖЕ ЕСТЬ АККАУНТ? ВОЙТИ") { showLogin() }
        setContentView(root)
    }

    private fun showHome() {
        setup("ТЫ В ЗОНЕ", "ТВОЯ СВЯЗЬ СО СТАЛКЕРАМИ")

        val search = EditText(this)
        search.hint = "Введи имя пользователя..."
        search.setHintTextColor(gray)
        search.setTextColor(white)
        search.setSingleLine(true)
        search.setPadding(dp(12), dp(8), dp(12), dp(8))
        search.setBackgroundColor(panel)
        root.addView(search)

        val status = TextView(this)
        status.text = "Загрузка пользователей..."
        status.setTextColor(gray)
        status.textSize = 13f
        root.addView(status)

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        section(content, "РЕЗУЛЬТАТЫ ПОИСКА")
        val results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        content.addView(results)

        section(content, "МОИ ЧАТЫ")
        val chats = LinearLayout(this)
        chats.orientation = LinearLayout.VERTICAL
        content.addView(chats)

        val myUid = uid
        if (myUid == null) {
            showLogin()
            return
        }

        var allUsers = listOf<DataSnapshot>()

        fun updateSearch(query: String) {
            results.removeAllViews()

            if (query.isBlank()) {
                status.text = "Введи имя пользователя для поиска"
                return
            }

            val matches = allUsers.filter { user ->
                val name = user.child("username")
                    .getValue(String::class.java) ?: ""
                val userId = user.child("uid")
                    .getValue(String::class.java) ?: user.key ?: ""

                name.contains(query, ignoreCase = true) && userId != myUid
            }

            status.text = "Найдено пользователей: ${matches.size}"

            if (matches.isEmpty()) {
                row(results, "Никого не найдено", "Попробуй другое имя") {}
            }

            matches.forEach { user ->
                val peerUid = user.child("uid")
                    .getValue(String::class.java) ?: user.key ?: ""
                val name = user.child("username")
                    .getValue(String::class.java) ?: "Сталкер"

                if (peerUid.isNotEmpty()) {
                    row(results, name, "Начать переписку") {
                        openChat(peerUid, name)
                    }
                }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allUsers = snapshot.children.toList()
                status.text = "Загружено аккаунтов: ${allUsers.size}"
                updateSearch(search.text.toString())
            }

            override fun onCancelled(error: DatabaseError) {
                status.text = "Ошибка Firebase: ${error.message}"
            }
        }
        db.child("users").addValueEventListener(usersListener!!)

        val chatRef = db.child("userChats").child(myUid)
        activeRef = chatRef
        activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chats.removeAllViews()

                if (!snapshot.hasChildren()) {
                    row(chats, "Пока нет чатов", "Найди пользователя выше") {}
                }

                for (chat in snapshot.children) {
                    val peerUid = chat.child("peerUid")
                        .getValue(String::class.java) ?: continue

                    db.child("users").child(peerUid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(user: DataSnapshot) {
                                val name = user.child("username")
                                    .getValue(String::class.java) ?: "Сталкер"
                                row(chats, name, "Открыть переписку") {
                                    openChat(peerUid, name)
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        chatRef.addValueEventListener(activeListener!!)

        button("ВЫЙТИ ИЗ АККАУНТА") {
            usersListener?.let { db.child("users").removeEventListener(it) }
            usersListener = null
            AuthManager.logout()
            showLogin()
        }

        setContentView(root)
    }

    private fun openChat(peerUid: String, peerName: String) {
        val myUid = uid ?: return
        val id = if (myUid < peerUid) "${myUid}_$peerUid" else "${peerUid}_$myUid"

        setup(peerName, "ЛИЧНАЯ СВЯЗЬ")
        link("← НАЗАД К ЧАТАМ") { showHome() }

        val messages = LinearLayout(this)
        messages.orientation = LinearLayout.VERTICAL

        val scroll = ScrollView(this)
        scroll.addView(messages)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL

        val input = EditText(this)
        input.hint = "Сообщение..."
        input.setHintTextColor(gray)
        input.setTextColor(white)
        input.setBackgroundColor(panel)

        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))

        val send = Button(this)
        send.text = "➤"
        send.setTextColor(bg)
        send.setBackgroundColor(green)
        inputRow.addView(send)
        root.addView(inputRow)

        val ref = db.child("chats").child(id).child("messages")
        activeRef = ref
        activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messages.removeAllViews()

                for (item in snapshot.children) {
                    val sender = item.child("senderUid")
                        .getValue(String::class.java) ?: ""
                    val body = item.child("text")
                        .getValue(String::class.java) ?: ""

                    val line = TextView(this@MainActivity)
                    line.text = (if (sender == myUid) "ТЫ: " else "$peerName: ") + body
                    line.textSize = 15f
                    line.setTextColor(if (sender == myUid) green else white)
                    line.setPadding(dp(12), dp(10), dp(12), dp(10))
                    line.setBackgroundColor(panel)
                    messages.addView(line)
                }
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        ref.addValueEventListener(activeListener!!)

        send.setOnClickListener {
            val body = input.text.toString().trim()
            if (body.isNotEmpty()) {
                send.isEnabled = false
                ref.push().setValue(
                    mapOf(
                        "senderUid" to myUid,
                        "text" to body,
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                ).addOnSuccessListener {
                    db.child("userChats").child(myUid).child(id).setValue(
                        mapOf("peerUid" to peerUid, "updatedAt" to ServerValue.TIMESTAMP)
                    )
                    db.child("userChats").child(peerUid).child(id).setValue(
                        mapOf("peerUid" to myUid, "updatedAt" to ServerValue.TIMESTAMP)
                    )
                    input.text.clear()
                    send.isEnabled = true
                }.addOnFailureListener {
                    send.isEnabled = true
                    Toast.makeText(this, it.message ?: "Ошибка отправки", Toast.LENGTH_LONG).show()
                }
            }
        }

        setContentView(root)
    }

    override fun onDestroy() {
        clearListener()
        usersListener?.let { db.child("users").removeEventListener(it) }
        super.onDestroy()
    }
}
