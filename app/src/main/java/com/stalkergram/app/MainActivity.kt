package com.stalkergram.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseConfig.database.reference }

    private lateinit var root: LinearLayout
    private var users = mutableListOf<UserItem>()
    private var chatListListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null
    private var activeChatUid: String? = null
    private var searchQuery = ""

    private data class UserItem(val uid: String, val username: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (auth.currentUser == null) showAuth(false) else showHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun newRoot(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Color.rgb(13, 18, 25))
        }
        setContentView(root)
        return root
    }

    private fun makeText(
        value: String,
        size: Float = 16f,
        color: Int = Color.WHITE,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun makeInput(hintText: String, password: Boolean = false): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(29, 38, 49), 12)
            if (password) inputType = 129
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(25, 190, 110), 12)
        }
    }

    private fun addGap(height: Int = 10) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun showAuth(registerMode: Boolean) {
        clearAllListeners()
        newRoot()

        val title = makeText("StalkerGram", 30f, Color.rgb(25, 220, 130), true)
        title.gravity = Gravity.CENTER
        root.addView(title, LinearLayout.LayoutParams(-1, dp(70)))

        val subtitle = makeText(
            if (registerMode) "Создай новый аккаунт" else "Войди в свой аккаунт",
            16f, Color.LTGRAY
        )
        subtitle.gravity = Gravity.CENTER
        root.addView(subtitle)
        addGap(24)

        val usernameInput = makeInput("Имя пользователя")
        root.addView(usernameInput, LinearLayout.LayoutParams(-1, dp(52)))
        if (!registerMode) usernameInput.visibility = View.GONE

        addGap()
        val emailInput = makeInput("Электронная почта")
        emailInput.inputType = 33
        root.addView(emailInput, LinearLayout.LayoutParams(-1, dp(52)))

        addGap()
        val passwordInput = makeInput("Пароль", true)
        root.addView(passwordInput, LinearLayout.LayoutParams(-1, dp(52)))

        addGap(18)
        val actionButton = makeButton(if (registerMode) "Зарегистрироваться" else "Войти")
        root.addView(actionButton, LinearLayout.LayoutParams(-1, dp(52)))

        addGap(8)
        val switchButton = Button(this).apply {
            text = if (registerMode) "Уже есть аккаунт? Войти" else "Создать аккаунт"
            isAllCaps = false
            setTextColor(Color.rgb(25, 220, 130))
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        }
        root.addView(switchButton, LinearLayout.LayoutParams(-1, dp(48)))

        actionButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isBlank() || password.isBlank() ||
                (registerMode && username.isBlank())
            ) {
                toast("Заполни все поля")
                return@setOnClickListener
            }

            actionButton.isEnabled = false
            if (registerMode) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        actionButton.isEnabled = true
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid
                            if (uid == null) {
                                toast("Не удалось получить аккаунт")
                                return@addOnCompleteListener
                            }
                            val userData = mapOf(
                                "uid" to uid,
                                "username" to username,
                                "email" to email,
                                "createdAt" to ServerValue.TIMESTAMP
                            )
                            db.child("users").child(uid).setValue(userData)
                                .addOnSuccessListener { showHome() }
                                .addOnFailureListener {
                                    toast(it.message ?: "Ошибка сохранения профиля")
                                }
                        } else {
                            toast(task.exception?.message ?: "Ошибка регистрации")
                        }
                    }
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        actionButton.isEnabled = true
                        if (task.isSuccessful) {
                            showHome()
                        } else {
                            toast(task.exception?.message ?: "Ошибка входа")
                        }
                    }
            }
        }

        switchButton.setOnClickListener { showAuth(!registerMode) }
    }

    private fun showHome() {
        clearAllListeners()
        activeChatUid = null
        searchQuery = ""
        newRoot()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = makeText("StalkerGram", 25f, Color.rgb(25, 220, 130), true)
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))

        val logout = Button(this).apply {
            text = "Выйти"
            isAllCaps = false
            setTextColor(Color.LTGRAY)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setOnClickListener {
                clearAllListeners()
                auth.signOut()
                showAuth(false)
            }
        }
        header.addView(logout)
        root.addView(header)

        val search = makeInput("Поиск пользователей")
        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)))
        addGap(12)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val chatsTitle = makeText("МОИ ЧАТЫ", 14f, Color.rgb(25, 220, 130), true)
        content.addView(chatsTitle, LinearLayout.LayoutParams(-1, dp(38)))
        val chatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(chatsContainer)

        val usersTitle = makeText("ПОЛЬЗОВАТЕЛИ", 14f, Color.rgb(25, 220, 130), true)
        val usersTitleParams = LinearLayout.LayoutParams(-1, dp(42))
        usersTitleParams.topMargin = dp(12)
        content.addView(usersTitle, usersTitleParams)

        val usersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(usersContainer)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                renderUsers(usersContainer)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadUsers(usersContainer)
        loadChats(chatsContainer)
    }

    private fun loadUsers(container: LinearLayout) {
        val myUid = auth.currentUser?.uid ?: return
        db.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                users.clear()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid == myUid) continue
                    val username = child.child("username").getValue(String::class.java)
                        ?: child.child("email").getValue(String::class.java)
                        ?: "Пользователь"
                    users.add(UserItem(uid, username))
                }
                users.sortBy { it.username.lowercase(Locale.getDefault()) }
                renderUsers(container)
            }

            override fun onCancelled(error: DatabaseError) {
                toast(error.message)
            }
        })
    }

    private fun renderUsers(container: LinearLayout) {
        container.removeAllViews()
        val filtered = users.filter {
            it.username.contains(searchQuery, ignoreCase = true)
        }
        if (filtered.isEmpty()) {
            container.addView(makeText(
                if (searchQuery.isBlank()) "Пользователей пока нет" else "Ничего не найдено",
                14f, Color.GRAY
            ))
            return
        }
        filtered.forEach { user ->
            container.addView(makeListItem(
                user.username,
                "Начать переписку",
                null
            ) {
                openChat(user.uid, user.username)
            })
        }
    }

    private fun loadChats(container: LinearLayout) {
        chatListListener = ChatListManager.listenChats(
            callback = { previews ->
                container.removeAllViews()
                if (previews.isEmpty()) {
                    container.addView(makeText("Здесь пока нет чатов", 14f, Color.GRAY))
                    return@listenChats
                }
                previews.forEach { preview ->
                    val time = if (preview.timestamp > 0L) {
                        SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(preview.timestamp))
                    } else ""
                    val message = preview.lastMessage.ifBlank { "Нет сообщений" }
                    container.addView(makeListItem(
                        preview.username,
                        message,
                        time
                    ) {
                        openChat(preview.uid, preview.username)
                    })
                }
            },
            onError = { message -> toast(message) }
        )
    }

    private fun makeAvatar(name: String): TextView {
        val initial = name.trim().firstOrNull()?.toString()
            ?.uppercase(Locale.getDefault()) ?: "?"
        return TextView(this).apply {
            text = initial
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(25, 155, 100))
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
    }

    private fun makeListItem(
        name: String,
        message: String,
        time: String?,
        onClick: () -> Unit
    ): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(Color.rgb(24, 32, 43), 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val avatar = makeAvatar(name)
        item.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
        }
        textColumn.addView(makeText(name, 16f, Color.WHITE, true))
        textColumn.addView(makeText(message, 13f, Color.LTGRAY))
        item.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

        if (!time.isNullOrBlank()) {
            val timeView = makeText(time, 12f, Color.rgb(25, 220, 130))
            item.addView(timeView)
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(8)
        item.layoutParams = params
        return item
    }

    private fun openChat(uid: String, name: String) {
        clearMessagesListener()
        activeChatUid = uid
        newRoot()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 30f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setOnClickListener { showHome() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(makeAvatar(name), LinearLayout.LayoutParams(dp(42), dp(42)))
        val nameView = makeText(name, 18f, Color.WHITE, true)
        nameView.setPadding(dp(12), 0, 0, 0)
        header.addView(nameView)
        root.addView(header)

        val messageScroll = ScrollView(this)
        val messageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        messageScroll.addView(messageContainer)
        root.addView(messageScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = makeInput("Сообщение...")
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        bottom.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))

        val send = makeButton("➤")
        val sendParams = LinearLayout.LayoutParams(dp(58), dp(52))
        sendParams.leftMargin = dp(8)
        bottom.addView(send, sendParams)
        root.addView(bottom)

        fun sendCurrentMessage() {
            val text = input.text.toString().trim()
            if (text.isBlank()) return
            send.isEnabled = false
            ChatManager.sendMessage(uid, text) { success, result ->
                runOnUiThread {
                    send.isEnabled = true
                    if (success) input.text.clear() else toast(result)
                }
            }
        }

        send.setOnClickListener { sendCurrentMessage() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage()
                true
            } else false
        }

        messagesListener = ChatManager.listenMessages(
            otherUid = uid,
            onMessages = { messages ->
                if (activeChatUid != uid) return@listenMessages
                messageContainer.removeAllViews()
                val sorted = messages.sortedBy {
                    it.child("timestamp").getValue(Long::class.java) ?: 0L
                }
                sorted.forEach { snapshot ->
                    val text = snapshot.child("text").getValue(String::class.java) ?: ""
                    val sender = snapshot.child("senderId").getValue(String::class.java)
                    val mine = sender == auth.currentUser?.uid
                    val timeValue = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    val time = if (timeValue > 0L) {
                        SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(timeValue))
                    } else ""

                    val bubble = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(8), dp(12), dp(6))
                        background = rounded(
                            if (mine) Color.rgb(20, 130, 85) else Color.rgb(40, 50, 63),
                            14
                        )
                    }
                    bubble.addView(makeText(text, 15f, Color.WHITE))
                    val timeText = makeText(time, 10f, Color.LTGRAY)
                    timeText.gravity = Gravity.END
                    bubble.addView(timeText)

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = if (mine) Gravity.END else Gravity.START
                        setPadding(dp(4), dp(3), dp(4), dp(3))
                        addView(bubble, LinearLayout.LayoutParams(-2, -2))
                    }
                    messageContainer.addView(row)
                }
                messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
            },
            onError = { message -> toast(message) }
        )
    }

    private fun clearMessagesListener() {
        val uid = activeChatUid
        val listener = messagesListener
        if (uid != null && listener != null) {
            ChatManager.removeMessagesListener(uid, listener)
        }
        messagesListener = null
    }

    private fun clearAllListeners() {
        clearMessagesListener()
        chatListListener?.let { ChatListManager.removeListener(it) }
        chatListListener = null
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (activeChatUid != null) {
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
