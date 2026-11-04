package com.stalkergram.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseConfig.database.reference

    private lateinit var root: LinearLayout
    private var chatListListener: ValueEventListener? = null
    private var usersListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null

    private var activeChatUid = ""
    private var activeChatName = ""

    private val usersCache = mutableListOf<Pair<String, String>>()
    private lateinit var usersContainer: LinearLayout
    private lateinit var chatsContainer: LinearLayout
    private lateinit var searchInput: EditText

    private val green = Color.rgb(90, 255, 100)
    private val dark = Color.rgb(15, 18, 15)
    private val panel = Color.rgb(29, 34, 29)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            showHome()
        } else {
            showAuth(false)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun setupRoot() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        root.setBackgroundColor(dark)
        setContentView(root)
    }

    private fun addTitle(text: String) {
        val title = TextView(this)
        title.text = text
        title.textSize = 25f
        title.setTextColor(green)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(12), 0, dp(18))
        root.addView(title)
    }

    private fun makeButton(text: String): Button {
        val button = Button(this)
        button.text = text
        button.setTextColor(Color.BLACK)
        button.setBackgroundColor(green)
        return button
    }

    private fun makeInput(hint: String, password: Boolean = false): EditText {
        val input = EditText(this)
        input.hint = hint
        input.setHintTextColor(Color.LTGRAY)
        input.setTextColor(white)
        input.textSize = 16f
        input.setSingleLine(true)
        input.setPadding(dp(12), dp(8), dp(12), dp(8))
        input.setBackgroundColor(panel)

        if (password) {
            input.inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        )
        params.bottomMargin = dp(12)
        root.addView(input, params)

        return input
    }

    private fun showAuth(registerMode: Boolean) {
        clearAllListeners()
        setupRoot()
        addTitle("STALKERGRAM")

        val usernameInput = makeInput("Имя пользователя")
        if (!registerMode) {
            usernameInput.visibility = View.GONE
        }

        val emailInput = makeInput("Email")
        val passwordInput = makeInput("Пароль", true)

        val actionButton = makeButton(
            if (registerMode) "ЗАРЕГИСТРИРОВАТЬСЯ" else "ВОЙТИ"
        )
        root.addView(actionButton)

        val switchButton = Button(this)
        switchButton.text =
            if (registerMode) "Уже есть аккаунт? Войти"
            else "Создать аккаунт"
        switchButton.setTextColor(green)
        switchButton.setBackgroundColor(dark)
        root.addView(switchButton)

        switchButton.setOnClickListener {
            showAuth(!registerMode)
        }

        actionButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val username = usernameInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() ||
                (registerMode && username.isEmpty())) {
                toast("Заполни все поля")
                return@setOnClickListener
            }

            if (registerMode) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val uid = auth.currentUser?.uid ?: return@addOnSuccessListener

                        val profile = mapOf(
                            "uid" to uid,
                            "username" to username,
                            "email" to email,
                            "createdAt" to ServerValue.TIMESTAMP
                        )

                        db.child("users").child(uid).setValue(profile)
                            .addOnSuccessListener {
                                toast("Аккаунт создан!")
                                showHome()
                            }
                            .addOnFailureListener {
                                toast(it.message ?: "Ошибка профиля")
                            }
                    }
                    .addOnFailureListener {
                        toast(it.message ?: "Ошибка регистрации")
                    }
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        toast("Вход выполнен!")
                        showHome()
                    }
                    .addOnFailureListener {
                        toast(it.message ?: "Ошибка входа")
                    }
            }
        }
    }

    private fun showHome() {
        clearAllListeners()
        setupRoot()
        addTitle("STALKERGRAM")

        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL

        val welcome = TextView(this)
        welcome.text = "МОЙ АККАУНТ"
        welcome.setTextColor(green)
        welcome.textSize = 16f
        welcome.typeface = Typeface.DEFAULT_BOLD
        topRow.addView(
            welcome,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        val logoutButton = Button(this)
        logoutButton.text = "ВЫЙТИ"
        logoutButton.setTextColor(green)
        logoutButton.setBackgroundColor(dark)
        topRow.addView(logoutButton)

        root.addView(topRow)

        logoutButton.setOnClickListener {
            auth.signOut()
            showAuth(false)
        }

        searchInput = EditText(this)
        searchInput.hint = "Поиск пользователей..."
        searchInput.setTextColor(white)
        searchInput.setHintTextColor(Color.LTGRAY)
        searchInput.setSingleLine(true)
        searchInput.setPadding(dp(12), 0, dp(12), 0)
        searchInput.setBackgroundColor(panel)

        root.addView(
            searchInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        val scroll = ScrollView(this)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content)

        val chatsTitle = sectionTitle("МОИ ЧАТЫ")
        content.addView(chatsTitle)

        chatsContainer = LinearLayout(this)
        chatsContainer.orientation = LinearLayout.VERTICAL
        content.addView(chatsContainer)

        val usersTitle = sectionTitle("ПОЛЬЗОВАТЕЛИ")
        content.addView(usersTitle)

        usersContainer = LinearLayout(this)
        usersContainer.orientation = LinearLayout.VERTICAL
        content.addView(usersContainer)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                renderUsers(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        loadUsers()
        loadChats()
    }

    private fun sectionTitle(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = 17f
        view.setTextColor(green)
        view.typeface = Typeface.DEFAULT_BOLD
        view.setPadding(0, dp(20), 0, dp(8))
        return view
    }

    private fun loadUsers() {
        val ref = db.child("users")

        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersCache.clear()

                val myUid = auth.currentUser?.uid ?: return

                for (user in snapshot.children) {
                    val uid = user.key ?: continue
                    if (uid == myUid) continue

                    val name = user.child("username")
                        .getValue(String::class.java)
                        ?: "Пользователь"

                    usersCache.add(Pair(uid, name))
                }

                renderUsers(searchInput.text.toString())
            }

            override fun onCancelled(error: DatabaseError) {
                toast(error.message)
            }
        }

        ref.addValueEventListener(usersListener!!)
    }

    private fun renderUsers(query: String) {
        if (!::usersContainer.isInitialized) return

        usersContainer.removeAllViews()

        val filtered = usersCache.filter {
            it.second.contains(query.trim(), ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            addEmptyText(usersContainer, "Пользователи не найдены")
            return
        }

        for ((uid, name) in filtered) {
            val item = makeListItem(name, "Открыть переписку")
            item.setOnClickListener {
                openChat(uid, name)
            }
            usersContainer.addView(item)
        }
    }

    private fun loadChats() {
        chatListListener = ChatListManager.listenChats(
            { previews ->
                if (!::chatsContainer.isInitialized) return@listenChats

                chatsContainer.removeAllViews()

                if (previews.isEmpty()) {
                    addEmptyText(chatsContainer, "Пока нет переписок")
                    return@listenChats
                }

                for (chat in previews) {
                    val lastText = if (chat.lastMessage.isBlank()) {
                        "Нет сообщений"
                    } else {
                        chat.lastMessage
                    }

                    val time = if (chat.timestamp > 0L) {
                        SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                        ).format(Date(chat.timestamp))
                    } else {
                        ""
                    }

                    val item = makeListItem(
                        chat.username,
                        "$lastText    $time"
                    )

                    item.setOnClickListener {
                        openChat(chat.uid, chat.username)
                    }

                    chatsContainer.addView(item)
                }
            },
            { error ->
                toast(error)
            }
        )
    }

    private fun makeListItem(
        title: String,
        subtitle: String
    ): LinearLayout {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(dp(14), dp(10), dp(14), dp(10))
        item.setBackgroundColor(panel)

        val name = TextView(this)
        name.text = title
        name.textSize = 17f
        name.setTextColor(green)
        name.typeface = Typeface.DEFAULT_BOLD

        val message = TextView(this)
        message.text = subtitle
        message.textSize = 13f
        message.setTextColor(Color.LTGRAY)

        item.addView(name)
        item.addView(message)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(7)
        item.layoutParams = params

        return item
    }

    private fun addEmptyText(container: LinearLayout, text: String) {
        val view = TextView(this)
        view.text = text
        view.textSize = 14f
        view.setTextColor(Color.LTGRAY)
        view.setPadding(dp(8), dp(12), dp(8), dp(12))
        container.addView(view)
    }

    private fun openChat(uid: String, name: String) {
        clearChatListener()

        activeChatUid = uid
        activeChatName = name

        clearHomeListeners()
        setupRoot()

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val back = Button(this)
        back.text = "← НАЗАД"
        back.setTextColor(green)
        back.setBackgroundColor(dark)
        header.addView(back)

        val title = TextView(this)
        title.text = name
        title.textSize = 19f
        title.setTextColor(green)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        root.addView(header)

        back.setOnClickListener {
            showHome()
        }

        val messageScroll = ScrollView(this)
        root.addView(
            messageScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val messageContainer = LinearLayout(this)
        messageContainer.orientation = LinearLayout.VERTICAL
        messageContainer.setPadding(0, dp(8), 0, dp(8))
        messageScroll.addView(messageContainer)

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL

        val messageInput = EditText(this)
        messageInput.hint = "Сообщение..."
        messageInput.setTextColor(white)
        messageInput.setHintTextColor(Color.LTGRAY)
        messageInput.setBackgroundColor(panel)
        messageInput.setSingleLine(true)
        messageInput.imeOptions = EditorInfo.IME_ACTION_SEND

        bottom.addView(
            messageInput,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        val sendButton = makeButton("➤")
        bottom.addView(sendButton)

        root.addView(bottom)

        fun sendCurrentMessage() {
            val text = messageInput.text.toString()

            if (text.isBlank()) return

            ChatManager.sendMessage(uid, text) { success, result ->
                runOnUiThread {
                    if (success) {
                        messageInput.setText("")
                    } else {
                        toast(result)
                    }
                }
            }
        }

        sendButton.setOnClickListener {
            sendCurrentMessage()
        }

        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage()
                true
            } else {
                false
            }
        }

        messagesListener = ChatManager.listenMessages(
            uid,
            { messages ->
                messageContainer.removeAllViews()

                val sorted = messages.sortedBy {
                    it.child("timestamp")
                        .getValue(Long::class.java) ?: 0L
                }

                for (message in sorted) {
                    val sender = message.child("senderId")
                        .getValue(String::class.java) ?: ""

                    val text = message.child("text")
                        .getValue(String::class.java) ?: ""

                    val timestamp = message.child("timestamp")
                        .getValue(Long::class.java) ?: 0L

                    val time = if (timestamp > 0L) {
                        SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                        ).format(Date(timestamp))
                    } else {
                        ""
                    }

                    val line = TextView(this)
                    line.text = (if (sender == auth.currentUser?.uid) {
                        "Я: "
                    } else {
                        "$name: "
                    }) + text + "   " + time

                    line.textSize = 15f
                    line.setTextColor(white)
                    line.setPadding(dp(12), dp(9), dp(12), dp(9))
                    line.setBackgroundColor(panel)

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = dp(6)
                    messageContainer.addView(line, params)
                }

                messageScroll.post {
                    messageScroll.fullScroll(View.FOCUS_DOWN)
                }
            },
            { error ->
                toast(error)
            }
        )
    }

    private fun clearChatListener() {
        val listener = messagesListener
        if (listener != null && activeChatUid.isNotEmpty()) {
            ChatManager.removeMessagesListener(activeChatUid, listener)
        }
        messagesListener = null
    }

    private fun clearHomeListeners() {
        val chatListener = chatListListener
        if (chatListener != null) {
            ChatListManager.removeListener(chatListener)
        }
        chatListListener = null

        val userListener = usersListener
        if (userListener != null) {
            db.child("users").removeEventListener(userListener)
        }
        usersListener = null
    }

    private fun clearAllListeners() {
        clearChatListener()
        clearHomeListeners()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (auth.currentUser != null) {
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        clearAllListeners()
        super.onDestroy()
    }
}
