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
    private val currentUid get() = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var root: LinearLayout
    private var activeRef: DatabaseReference? = null
    private var activeListener: ValueEventListener? = null
    private var allUsers = mutableListOf<DataSnapshot>()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthManager.isLoggedIn()) showHome()
        else showLogin()
    }

    private fun clearListener() {
        if (activeRef != null && activeListener != null) {
            activeRef!!.removeEventListener(activeListener!!)
        }
        activeRef = null
        activeListener = null
    }

    private fun setupScreen(title: String, subtitle: String) {
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
        heading.setPadding(0, dp(20), 0, dp(5))
        root.addView(heading)

        val info = TextView(this)
        info.text = subtitle
        info.textSize = 13f
        info.setTextColor(gray)
        info.gravity = Gravity.CENTER
        info.setPadding(0, 0, 0, dp(15))
        root.addView(info)
    }

    private fun addField(hint: String, password: Boolean = false): EditText {
        val field = EditText(this)
        field.hint = hint
        field.setHintTextColor(gray)
        field.setTextColor(white)
        field.textSize = 16f
        field.setSingleLine(true)
        field.setPadding(dp(14), dp(10), dp(14), dp(10))
        field.setBackgroundColor(panel)

        if (password) field.inputType = 129

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        )
        params.setMargins(0, dp(6), 0, dp(6))
        root.addView(field, params)
        return field
    }

    private fun addButton(text: String, action: () -> Unit) {
        val button = Button(this)
        button.text = text
        button.setTextColor(bg)
        button.textSize = 14f
        button.setBackgroundColor(green)
        button.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        )
        params.setMargins(0, dp(8), 0, dp(6))
        root.addView(button, params)
    }

    private fun addLink(text: String, action: () -> Unit) {
        val link = TextView(this)
        link.text = text
        link.textSize = 14f
        link.setTextColor(green)
        link.gravity = Gravity.CENTER
        link.setPadding(dp(8), dp(14), dp(8), dp(14))
        link.setOnClickListener { action() }
        root.addView(link)
    }

    private fun showLogin() {
        setupScreen("ВХОД В СИСТЕМУ", "СВЯЗЬ В ЗОНЕ")

        val email = addField("Email")
        val password = addField("Пароль", true)

        addButton("ВОЙТИ В ЗОНУ") {
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

        addLink("НЕТ АККАУНТА? СОЗДАТЬ") {
            showRegister()
        }

        setContentView(root)
    }

    private fun showRegister() {
        setupScreen("НОВЫЙ СТАЛКЕР", "РЕГИСТРАЦИЯ В СИСТЕМЕ")

        val username = addField("Имя пользователя")
        val email = addField("Email")
        val password = addField("Придумай пароль", true)
        val confirm = addField("Повтори пароль", true)

        addButton("СОЗДАТЬ АККАУНТ") {
            val name = username.text.toString().trim()
            val mail = email.text.toString().trim()
            val pass = password.text.toString()

            when {
                name.length < 3 ->
                    Toast.makeText(this, "Имя: минимум 3 символа", Toast.LENGTH_SHORT).show()

                !android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches() ->
                    Toast.makeText(this, "Введи правильный Email", Toast.LENGTH_SHORT).show()

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

        addLink("УЖЕ ЕСТЬ АККАУНТ? ВОЙТИ") {
            showLogin()
        }

        setContentView(root)
    }

    private fun section(text: String) {
        val label = TextView(this)
        label.text = text
        label.textSize = 17f
        label.setTextColor(green)
        label.typeface = Typeface.DEFAULT_BOLD
        label.setPadding(0, dp(12), 0, dp(8))
        root.addView(label)
    }

    private fun addScrollableList(): LinearLayout {
        val scroll = ScrollView(this)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        return list
    }

    private fun addUserRow(
        list: LinearLayout,
        name: String,
        detail: String,
        action: () -> Unit
    ) {
        val item = TextView(this)
        item.text = "☢  $name\n     $detail"
        item.textSize = 15f
        item.setTextColor(white)
        item.setPadding(dp(14), dp(14), dp(10), dp(14))
        item.setBackgroundColor(panel)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, dp(4), 0, dp(4))
        list.addView(item, params)
        item.setOnClickListener { action() }
    }

    private fun showHome() {
        setupScreen("ТЫ В ЗОНЕ", "ТВОЯ СВЯЗЬ СО СТАЛКЕРАМИ")

        val list = addScrollableList()

        section("МОИ ЧАТЫ")

        val uid = currentUid
        if (uid == null) {
            showLogin()
            return
        }

        val chatsRef = db.child("userChats").child(uid)

        activeRef = chatsRef
        activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                list.removeAllViews()

                if (!snapshot.hasChildren()) {
                    addUserRow(list, "Пока нет чатов", "Найди сталкера ниже") {}
                }

                for (chat in snapshot.children) {
                    val peerUid = chat.child("peerUid").getValue(String::class.java)
                        ?: continue

                    db.child("users").child(peerUid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(user: DataSnapshot) {
                                val name = user.child("username")
                                    .getValue(String::class.java) ?: "Сталкер"

                                if (isFinishing) return

                                addUserRow(list, name, "Открыть переписку") {
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
        chatsRef.addValueEventListener(activeListener!!)

        section("ПОИСК СТАЛКЕРОВ")

        val search = EditText(this)
        search.hint = "Введи имя пользователя..."
        search.setHintTextColor(gray)
        search.setTextColor(white)
        search.setSingleLine(true)
        search.setPadding(dp(12), dp(8), dp(12), dp(8))
        search.setBackgroundColor(panel)
        root.addView(search)

        val searchList = LinearLayout(this)
        searchList.orientation = LinearLayout.VERTICAL
        list.addView(searchList)

        fun updateSearch(query: String) {
            searchList.removeAllViews()

            val matches = allUsers.filter {
                val name = it.child("username").getValue(String::class.java) ?: ""
                name.contains(query, ignoreCase = true) &&
                    it.child("uid").getValue(String::class.java) != uid
            }

            for (user in matches) {
                val peerUid = user.child("uid").getValue(String::class.java) ?: continue
                val name = user.child("username").getValue(String::class.java) ?: "Сталкер"

                addUserRow(searchList, name, "Начать переписку") {
                    openChat(peerUid, name)
                }
            }

            if (query.isNotBlank() && matches.isEmpty()) {
                addUserRow(searchList, "Никого не найдено", "Попробуй другое имя") {}
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        db.child("users").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allUsers.clear()
                    for (user in snapshot.children) {
                        allUsers.add(user)
                    }
                    updateSearch(search.text.toString())
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
                }
            }
        )

        addButton("ВЫЙТИ ИЗ АККАУНТА") {
            AuthManager.logout()
            showLogin()
        }

        setContentView(root)
    }

    private fun chatId(uid1: String, uid2: String): String =
        if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"

    private fun openChat(peerUid: String, peerName: String) {
        val myUid = currentUid ?: return
        val id = chatId(myUid, peerUid)

        setupScreen(peerName, "ЛИЧНАЯ СВЯЗЬ")

        addLink("← НАЗАД К ЧАТАМ") {
            showHome()
        }

        val messagesList = LinearLayout(this)
        messagesList.orientation = LinearLayout.VERTICAL

        val scroll = ScrollView(this)
        scroll.addView(messagesList)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL

        val messageInput = EditText(this)
        messageInput.hint = "Сообщение..."
        messageInput.setHintTextColor(gray)
        messageInput.setTextColor(white)
        messageInput.setBackgroundColor(panel)
        messageInput.setPadding(dp(10), dp(5), dp(10), dp(5))

        inputRow.addView(
            messageInput,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        val send = Button(this)
        send.text = "➤"
        send.setTextColor(bg)
        send.setBackgroundColor(green)
        inputRow.addView(send)

        root.addView(inputRow)

        val messagesRef = db.child("chats").child(id).child("messages")

        activeRef = messagesRef
        activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.removeAllViews()

                for (message in snapshot.children) {
                    val sender = message.child("senderUid")
                        .getValue(String::class.java) ?: ""
                    val body = message.child("text")
                        .getValue(String::class.java) ?: ""

                    val item = TextView(this@MainActivity)
                    item.text = (if (sender == myUid) "ТЫ: " else "$peerName: ") + body
                    item.textSize = 15f
                    item.setTextColor(if (sender == myUid) green else white)
                    item.setPadding(dp(12), dp(10), dp(12), dp(10))
                    item.setBackgroundColor(panel)

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, dp(3), 0, dp(3))
                    messagesList.addView(item, params)
                }

                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        messagesRef.addValueEventListener(activeListener!!)

        send.setOnClickListener {
            val text = messageInput.text.toString().trim()

            if (text.isNotEmpty()) {
                send.isEnabled = false

                val message = mapOf(
                    "senderUid" to myUid,
                    "text" to text,
                    "timestamp" to ServerValue.TIMESTAMP
                )

                messagesRef.push().setValue(message)
                    .addOnSuccessListener {
                        val chatInfo = mapOf(
                            "peerUid" to peerUid,
                            "updatedAt" to ServerValue.TIMESTAMP
                        )

                        db.child("userChats").child(myUid).child(id)
                            .setValue(chatInfo)

                        db.child("userChats").child(peerUid).child(id)
                            .setValue(
                                mapOf(
                                    "peerUid" to myUid,
                                    "updatedAt" to ServerValue.TIMESTAMP
                                )
                            )

                        messageInput.text.clear()
                        send.isEnabled = true
                    }
                    .addOnFailureListener { error ->
                        send.isEnabled = true
                        Toast.makeText(
                            this,
                            error.message ?: "Ошибка отправки",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }

        setContentView(root)
    }

    override fun onDestroy() {
        clearListener()
        super.onDestroy()
    }
}
