package com.stalkergram.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseConfig.database.reference }

    private val prefs by lazy {
        getSharedPreferences("stalkergram_settings", MODE_PRIVATE)
    }

    // MODERN STALKERGRAM COLORS

    private val bg = Color.rgb(18, 22, 29)
    private val panel = Color.rgb(29, 35, 44)
    private val panelLight = Color.rgb(39, 47, 58)
    private val green = Color.rgb(36, 190, 115)
    private val greenLight = Color.rgb(80, 220, 150)
    private val muted = Color.rgb(145, 157, 173)
    private val textColor = Color.rgb(240, 244, 248)
    private val border = Color.rgb(48, 58, 70)
    private val white = Color.WHITE

    private lateinit var root: LinearLayout

    private val users = mutableListOf<UserItem>()

    private var chatListListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null
    private var activeChatUid: String? = null

    private var chatSearchQuery = ""
    private var searchQuery = ""
    private var renderChats: (() -> Unit)? = null
    private var selectedSection = 0

    private val PICK_IMAGE_REQUEST = 1001

    private data class UserItem(
        val uid: String,
        val username: String,
        val handle: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        if (auth.currentUser == null) {
            showAuth(false)
        } else {
            showHome()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun newRoot(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
            setBackgroundColor(bg)
        }

        setContentView(root)
        return root
    }

    private fun makeText(
        value: String,
        size: Float = 16f,
        color: Int = textColor,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD
            else Typeface.DEFAULT
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun makeInput(
        hintText: String,
        password: Boolean = false
    ): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 15f
            typeface = Typeface.DEFAULT
            setPadding(dp(15), dp(10), dp(15), dp(10))
            background = rounded(panelLight, 14)
            if (password) inputType = 129
        }
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            background = rounded(green, 14)
        }
    }

    private fun addGap(height: Int = 10) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun divider(parent: LinearLayout = root) {
        val line = View(this).apply {
            setBackgroundColor(border)
        }

        val params = LinearLayout.LayoutParams(-1, dp(1))
        params.topMargin = dp(5)
        params.bottomMargin = dp(5)
        parent.addView(line, params)
    }

    private fun header(title: String, subtitle: String = "StalkerGram"): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(12))
        }

        box.addView(makeText(title, 25f, textColor, true))
        box.addView(makeText(subtitle, 13f, muted))

        return box
    }

    private fun clearAllListeners() {
        chatListListener?.let {
            ChatListManager.removeListener(it)
        }

        chatListListener = null
        renderChats = null

        val uid = activeChatUid
        val listener = messagesListener

        if (uid != null && listener != null) {
            ChatManager.removeMessagesListener(uid, listener)
        }

        messagesListener = null
        activeChatUid = null
    }

    // AVATARS

    private fun makeAvatar(name: String, size: Int = 52): TextView {
        val initial = name.trim().firstOrNull()?.toString()
            ?.uppercase(Locale.getDefault()) ?: "?"

        return TextView(this).apply {
            text = initial
            textSize = 19f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(39, 90, 65))
                setStroke(dp(2), green)
            }

            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    }

    private fun makeCircularBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2

        val result = Bitmap.createBitmap(
            size, size, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        val path = Path().apply {
            addCircle(
                size / 2f,
                size / 2f,
                size / 2f,
                Path.Direction.CW
            )
        }

        canvas.save()
        canvas.clipPath(path)

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
        )

        canvas.drawBitmap(
            source,
            Rect(left, top, left + size, top + size),
            Rect(0, 0, size, size),
            paint
        )

        canvas.restore()
        return result
    }

    private fun loadAvatar(uid: String, avatar: TextView) {
        db.child("users").child(uid).child("photo")
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val encoded = snapshot.getValue(String::class.java) ?: return

                    try {
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)

                        val bitmap = BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.size
                        ) ?: return

                        val circular = makeCircularBitmap(bitmap)

                        avatar.text = ""
                        avatar.background = BitmapDrawable(resources, circular)

                        bitmap.recycle()
                    } catch (_: Exception) {
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun chooseProfilePhoto() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }

        startActivityForResult(
            Intent.createChooser(intent, "Выбери фотографию"),
            PICK_IMAGE_REQUEST
        )
    }

    @Deprecated("Используется для выбора фотографии")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let {
                saveProfilePhoto(it)
            }
        }
    }

    private fun saveProfilePhoto(uri: android.net.Uri) {
        val uid = auth.currentUser?.uid ?: return

        try {
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return

            val scale = minOf(
                256f / bitmap.width,
                256f / bitmap.height,
                1f
            )

            val resized = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )

            val output = java.io.ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 65, output)

            val encoded = Base64.encodeToString(
                output.toByteArray(),
                Base64.NO_WRAP
            )

            db.child("users").child(uid).child("photo")
                .setValue(encoded)
                .addOnSuccessListener {
                    toast("Фото профиля сохранено")
                    showProfile()
                }
                .addOnFailureListener {
                    toast(it.message ?: "Ошибка сохранения фото")
                }

            bitmap.recycle()
            resized.recycle()

        } catch (_: Exception) {
            toast("Ошибка обработки фотографии")
        }
    }

    // BOTTOM NAVIGATION

    private fun addBottomNavigation(selected: Int) {
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            background = rounded(panel, 18)
        }

        val items = listOf(
            Triple("●", "Чаты", 0),
            Triple("♙", "Люди", 1),
            Triple("⚙", "Настройки", 2),
            Triple("◉", "Профиль", 3)
        )

        items.forEach { item ->
            val active = selected == item.third

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(
                    if (active) Color.rgb(39, 75, 59)
                    else Color.TRANSPARENT,
                    12
                )

                isClickable = true
                setPadding(dp(3), dp(5), dp(3), dp(5))

                setOnClickListener {
                    when (item.third) {
                        0 -> showHome()
                        1 -> showContacts()
                        2 -> showSettings()
                        3 -> showProfile()
                    }
                }
            }

            val icon = makeText(
                item.first,
                21f,
                if (active) greenLight else muted,
                true
            )

            icon.gravity = Gravity.CENTER

            val label = makeText(
                item.second,
                10f,
                if (active) greenLight else muted,
                active
            )

            label.gravity = Gravity.CENTER

            tab.addView(icon)
            tab.addView(label)

            navigation.addView(
                tab,
                LinearLayout.LayoutParams(0, dp(58), 1f)
            )
        }

        val params = LinearLayout.LayoutParams(-1, dp(65))
        params.topMargin = dp(8)

        root.addView(navigation, params)
    }

    // HOME

    private fun showHome() {
        clearAllListeners()

        selectedSection = 0
        chatSearchQuery = ""

        newRoot()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        top.addView(
            makeText("StalkerGram", 25f, textColor, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val add = makeText("✎", 25f, green, true)
        add.gravity = Gravity.CENTER

        top.addView(add, LinearLayout.LayoutParams(dp(45), dp(45)))
        add.setOnClickListener {
            showContacts()
        }

        root.addView(top)

        addGap(10)

        val search = makeInput("Поиск")

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(47))
        )

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                chatSearchQuery = s?.toString().orEmpty()
                renderChats?.invoke()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        addGap(10)

        val storiesTitle = makeText("Истории", 17f, textColor, true)
        root.addView(storiesTitle)

        addGap(7)

        val storiesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val stories = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        storiesScroll.addView(stories)

        val myStory = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
            setOnClickListener {
                toast("Добавление историй подключим отдельно")
            }
        }

        val myAvatar = makeAvatar("+", 58)
        myAvatar.text = "+"
        myAvatar.textSize = 26f

        myStory.addView(myAvatar)

        val myLabel = makeText("Моя история", 10f, muted)
        myLabel.gravity = Gravity.CENTER
        myStory.addView(myLabel)

        stories.addView(
            myStory,
            LinearLayout.LayoutParams(dp(78), dp(85))
        )

        val myUid = auth.currentUser?.uid

        db.child("users").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val uid = child.key ?: continue
                        if (uid == myUid) continue

                        val name = child.child("username")
                            .getValue(String::class.java) ?: "Пользователь"

                        val story = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(dp(5), dp(3), dp(5), dp(3))
                            setOnClickListener {
                                toast("Истории появятся в следующей версии")
                            }
                        }

                        val avatar = makeAvatar(name, 58)
                        story.addView(avatar)

                        val label = makeText(name, 10f, muted)
                        label.gravity = Gravity.CENTER
                        label.maxLines = 1

                        story.addView(label)

                        stories.addView(
                            story,
                            LinearLayout.LayoutParams(dp(78), dp(85))
                        )

                        loadAvatar(uid, avatar)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
        )

        root.addView(
            storiesScroll,
            LinearLayout.LayoutParams(-1, dp(88))
        )

        addGap(8)

        root.addView(makeText("Сообщения", 19f, textColor, true))

        addGap(7)

        val scroll = ScrollView(this)

        val chatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(chatsContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        loadChats(chatsContainer)

        addBottomNavigation(0)
    }

    // CREATE CHANNEL

    private fun showCreateChannel() {
        val uid = auth.currentUser?.uid ?: return

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(5))
        }

        val nameInput = makeInput("Название канала")
        val descriptionInput = makeInput("Описание канала")

        layout.addView(nameInput)
        layout.addView(descriptionInput)

        AlertDialog.Builder(this)
            .setTitle("Создать канал")
            .setView(layout)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Создать") { _, _ ->

                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()

                if (name.length < 2) {
                    toast("Название должно содержать минимум 2 символа")
                    return@setPositiveButton
                }

                val channelId = db.child("channels").push().key
                    ?: return@setPositiveButton

                val channel = mapOf(
                    "id" to channelId,
                    "name" to name,
                    "description" to description,
                    "creatorUid" to uid,
                    "createdAt" to ServerValue.TIMESTAMP
                )

                db.child("channels").child(channelId).setValue(channel)
                    .addOnSuccessListener {
                        toast("Канал создан")
                    }
                    .addOnFailureListener {
                        toast(it.message ?: "Ошибка создания канала")
                    }
            }
            .show()
    }

    // FAVORITES

    private fun showFavorites() {
        clearAllListeners()
        newRoot()

        root.addView(header("Избранное", "Сохранённые сообщения"))
        addGap(10)

        val card = settingsCard()

        card.addView(
            makeText(
                "Здесь будут храниться сообщения, которые ты сохранишь.",
                14f,
                textColor
            )
        )

        root.addView(card)

        addGap(15)

        val back = makeButton("Вернуться в чаты")

        root.addView(
            back,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        back.setOnClickListener {
            showHome()
        }
    }

    // CHAT LIST

    private fun loadChats(container: LinearLayout) {
        renderChats = null

        chatListListener = ChatListManager.listenChats(
            callback = { previews ->

                val drawChats: () -> Unit = {
                    container.removeAllViews()

                    val query = chatSearchQuery.trim()
                        .removePrefix("@")
                        .lowercase(Locale.getDefault())

                    val filtered = previews.filter { preview ->
                        preview.username.lowercase(Locale.getDefault()).contains(query) ||
                            preview.uid.lowercase(Locale.getDefault()).contains(query)
                    }

                    if (filtered.isEmpty()) {
                        val empty = makeText(
                            if (query.isBlank()) "Пока нет сообщений"
                            else "Ничего не найдено",
                            14f,
                            muted
                        )

                        empty.gravity = Gravity.CENTER

                        container.addView(
                            empty,
                            LinearLayout.LayoutParams(-1, dp(100))
                        )

                    } else {
                        filtered.sortedByDescending {
                            it.timestamp
                        }.forEach { preview ->

                            val time = if (preview.timestamp > 0L) {
                                SimpleDateFormat(
                                    "HH:mm",
                                    Locale.getDefault()
                                ).format(Date(preview.timestamp))
                            } else ""

                            container.addView(
                                makeListItem(
                                    preview.uid,
                                    preview.username,
                                    preview.lastMessage.ifBlank {
                                        "Нет сообщений"
                                    },
                                    time,
                                    preview.unreadCount
                                ) {
                                    openChat(preview.uid, preview.username)
                                }
                            )
                        }
                    }
                }

                renderChats = drawChats
                drawChats()
            },

            onError = { message ->
                toast(message)
            }
        )
    }

    private fun makeListItem(
        uid: String,
        name: String,
        message: String,
        time: String?,
        unreadCount: Int = 0,
        onClick: () -> Unit
    ): View {

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = rounded(Color.TRANSPARENT, 12)
            setOnClickListener { onClick() }
        }

        val avatar = makeAvatar(name, 54)

        item.addView(
            avatar,
            LinearLayout.LayoutParams(dp(54), dp(54))
        )

        loadAvatar(uid, avatar)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
        }

        texts.addView(
            makeText(name, 16f, textColor, true)
        )

        val messageText = makeText(message, 13f, muted)
        messageText.maxLines = 1
        messageText.ellipsize =
            android.text.TextUtils.TruncateAt.END

        texts.addView(messageText)

        item.addView(
            texts,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        if (!time.isNullOrBlank()) {
            right.addView(makeText(time, 10f, muted))
        }

        if (unreadCount > 0) {
            val badge = makeText(
                if (unreadCount > 99) "99+" else unreadCount.toString(),
                11f,
                white,
                true
            )

            badge.gravity = Gravity.CENTER
            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(green)
            }

            val params = LinearLayout.LayoutParams(dp(25), dp(25))
            params.topMargin = dp(5)

            right.addView(badge, params)
        }

        item.addView(right)

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(2)

        item.layoutParams = params

        return item
    }

    // CONTACTS

    private fun showContacts() {
        clearAllListeners()

        selectedSection = 1
        searchQuery = ""

        newRoot()

        root.addView(header("Контакты", "Найди людей в StalkerGram"))

        addGap(5)

        val search = makeInput("Поиск по имени или @username")

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(47))
        )

        addGap(8)

        val scroll = ScrollView(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(container)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                searchQuery = s?.toString().orEmpty()
                renderUsers(container)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        loadUsers(container)
        addBottomNavigation(1)
    }

    private fun loadUsers(container: LinearLayout) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("users").addListenerForSingleValueEvent(
            object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    users.clear()

                    for (child in snapshot.children) {
                        val uid = child.key ?: continue

                        if (uid == myUid) continue

                        val username = child.child("username")
                            .getValue(String::class.java)
                            ?: child.child("email")
                                .getValue(String::class.java)
                            ?: "Пользователь"

                        val handle = child.child("handle")
                            .getValue(String::class.java) ?: ""

                        users.add(
                            UserItem(uid, username, handle)
                        )
                    }

                    users.sortBy {
                        it.username.lowercase(Locale.getDefault())
                    }

                    renderUsers(container)
                }

                override fun onCancelled(error: DatabaseError) {
                    toast(error.message)
                }
            }
        )
    }

    private fun renderUsers(container: LinearLayout) {
        container.removeAllViews()

        val query = searchQuery.trim().removePrefix("@")

        val filtered = users.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
                it.handle.contains(query, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            val empty = makeText(
                if (searchQuery.isBlank()) "Пользователей пока нет"
                else "Ничего не найдено",
                14f,
                muted
            )

            container.addView(empty)
            return
        }

        filtered.forEach { user ->

            val display = if (user.handle.isNotBlank()) {
                "${user.username}  •  @${user.handle}"
            } else {
                user.username
            }

            container.addView(
                makeListItem(
                    user.uid,
                    display,
                    "Написать сообщение",
                    null
                ) {
                    openChat(user.uid, user.username)
                }
            )
        }
    }

    // CHAT SCREEN

    private fun openChat(otherUid: String, username: String) {
        clearAllListeners()

        activeChatUid = otherUid

        newRoot()

        val headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(panel, 14)
        }

        val back = makeText("‹", 34f, green, true)
        back.gravity = Gravity.CENTER

        headerBox.addView(
            back,
            LinearLayout.LayoutParams(dp(38), dp(48))
        )

        back.setOnClickListener {
            if (selectedSection == 1) {
                showContacts()
            } else {
                showHome()
            }
        }

        val avatar = makeAvatar(username, 44)

        headerBox.addView(
            avatar,
            LinearLayout.LayoutParams(dp(44), dp(44))
        )

        loadAvatar(otherUid, avatar)

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        titleBox.addView(
            makeText(username, 16f, textColor, true)
        )

        titleBox.addView(
            makeText("StalkerGram", 11f, green)
        )

        headerBox.addView(
            titleBox,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(headerBox)

        addGap(7)

        val scroll = ScrollView(this)

        val messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        scroll.addView(messagesContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, 0)
        }

        val messageInput = makeInput("Сообщение...")
        messageInput.maxLines = 4
        messageInput.inputType = 1
        messageInput.imeOptions = EditorInfo.IME_ACTION_SEND

        bottom.addView(
            messageInput,
            LinearLayout.LayoutParams(0, dp(49), 1f)
        )

        val sendButton = TextView(this).apply {
            text = "➤"
            textSize = 22f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = rounded(green, 15)
        }

        val sendParams = LinearLayout.LayoutParams(dp(50), dp(49))
        sendParams.leftMargin = dp(7)

        bottom.addView(sendButton, sendParams)
        root.addView(bottom)

        fun sendCurrentMessage() {
            val message = messageInput.text.toString().trim()

            if (message.isBlank()) return

            sendButton.isEnabled = false

            ChatManager.sendMessage(otherUid, message) { success, result ->
                sendButton.isEnabled = true

                if (success) {
                    messageInput.text.clear()
                } else {
                    toast(result)
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
            otherUid,

            onMessages = { messages ->

                messagesContainer.removeAllViews()

                if (messages.isEmpty()) {
                    val empty = makeText(
                        "Начни общение!",
                        14f,
                        muted
                    )

                    empty.gravity = Gravity.CENTER

                    messagesContainer.addView(
                        empty,
                        LinearLayout.LayoutParams(-1, dp(80))
                    )

                } else {
                    val myUid = auth.currentUser?.uid ?: ""

                    val sorted = messages.sortedBy {
                        it.child("timestamp")
                            .getValue(Long::class.java) ?: 0L
                    }

                    sorted.forEach { snapshot ->

                        val senderId = snapshot.child("senderId")
                            .getValue(String::class.java) ?: ""

                        val messageText = snapshot.child("text")
                            .getValue(String::class.java) ?: ""

                        val timestamp = snapshot.child("timestamp")
                            .getValue(Long::class.java) ?: 0L

                        val status = snapshot.child("status")
                            .getValue(String::class.java) ?: "sent"

                        val isMine = senderId == myUid

                        val bubble = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(12), dp(9), dp(12), dp(7))

                            background = rounded(
                                if (isMine) Color.rgb(39, 104, 72)
                                else panel,
                                15
                            )
                        }

                        bubble.addView(
                            makeText(messageText, 15f, textColor)
                        )

                        val time = if (timestamp > 0L) {
                            SimpleDateFormat(
                                "HH:mm",
                                Locale.getDefault()
                            ).format(Date(timestamp))
                        } else {
                            ""
                        }

                        val meta = if (isMine) {
                            val ticks = if (status == "read") "✓✓" else "✓"
                            "$time   $ticks"
                        } else {
                            time
                        }

                        val timeText = makeText(
                            meta,
                            10f,
                            if (isMine && status == "read") {
                                greenLight
                            } else {
                                muted
                            }
                        )

                        timeText.gravity = Gravity.END
                        bubble.addView(timeText)

                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = if (isMine) {
                                Gravity.END
                            } else {
                                Gravity.START
                            }
                        }

                        row.addView(
                            bubble,
                            LinearLayout.LayoutParams(
                                minOf(
                                    dp(280),
                                    (resources.displayMetrics.widthPixels * 0.78f).toInt()
                                ),
                                -2
                            )
                        )

                        val params = LinearLayout.LayoutParams(-1, -2)
                        params.bottomMargin = dp(7)

                        messagesContainer.addView(row, params)
                    }

                    scroll.post {
                        scroll.fullScroll(View.FOCUS_DOWN)
                    }
                }
            },

            onError = { message ->
                toast(message)
            }
        )
    }

    // PROFILE

    private fun settingsCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 16)
        }
    }

    private fun showProfile() {
        clearAllListeners()

        selectedSection = 3

        newRoot()

        val uid = auth.currentUser?.uid ?: return

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        content.addView(header("Профиль", "Твой аккаунт StalkerGram"))

        addGap(8)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 18)
        }

        val avatar = makeAvatar("Я", 86)

        top.addView(
            avatar,
            LinearLayout.LayoutParams(dp(86), dp(86))
        )

        loadAvatar(uid, avatar)

        avatar.setOnClickListener {
            chooseProfilePhoto()
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }

        val name = makeText("Загрузка...", 19f, textColor, true)
        val handle = makeText("@username", 13f, green)
        val status = makeText("● StalkerGram", 12f, muted)

        info.addView(name)
        info.addView(handle)
        info.addView(status)

        top.addView(
            info,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        content.addView(top)

        addGap(10)

        val edit = makeButton("Изменить имя")

        content.addView(
            edit,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        edit.setOnClickListener {
            showEditName()
        }

        addGap(8)

        val aboutCard = settingsCard()

        aboutCard.addView(
            makeText("О себе", 16f, textColor, true)
        )

        val about = makeText(
            "Расскажи немного о себе",
            13f,
            muted
        )

        about.setPadding(0, dp(8), 0, 0)

        aboutCard.addView(about)

        aboutCard.setOnClickListener {
            showEditField(
                "about",
                "Информация о себе",
                "О себе...",
                200
            )
        }

        content.addView(aboutCard)

        addGap(8)

        val handleButton = makeButton("Изменить username")

        content.addView(
            handleButton,
            LinearLayout.LayoutParams(-1, dp(46))
        )

        handleButton.setOnClickListener {
            showEditHandle()
        }

        addGap(7)

        val statusButton = makeButton("Изменить статус")

        content.addView(
            statusButton,
            LinearLayout.LayoutParams(-1, dp(46))
        )

        statusButton.setOnClickListener {
            showEditField(
                "status",
                "Статус",
                "Твой статус...",
                60
            )
        }

        addGap(7)

        val logout = Button(this).apply {
            text = "Выйти из аккаунта"
            isAllCaps = false
            setTextColor(Color.rgb(255, 110, 110))
            background = rounded(panel, 14)
        }

        content.addView(
            logout,
            LinearLayout.LayoutParams(-1, dp(46))
        )

        logout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Выйти из аккаунта?")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Выйти") { _, _ ->
                    clearAllListeners()
                    auth.signOut()
                    showAuth(false)
                }
                .show()
        }

        db.child("users").child(uid)
            .addListenerForSingleValueEvent(
                object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {
                        name.text = snapshot.child("username")
                            .getValue(String::class.java) ?: "Пользователь"

                        val h = snapshot.child("handle")
                            .getValue(String::class.java)

                        handle.text = if (h.isNullOrBlank()) {
                            "@username не задан"
                        } else {
                            "@$h"
                        }

                        status.text = "● " + (
                            snapshot.child("status")
                                .getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                                ?: "В сети"
                        )

                        about.text = snapshot.child("about")
                            .getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Расскажи немного о себе"
                    }

                    override fun onCancelled(error: DatabaseError) {
                        toast(error.message)
                    }
                }
            )

        addBottomNavigation(3)
    }

    // PROFILE EDITING

    private fun showEditField(
        field: String,
        title: String,
        hint: String,
        maxLength: Int
    ) {
        clearAllListeners()
        newRoot()

        val uid = auth.currentUser?.uid ?: return

        val back = makeText("‹  Назад", 16f, green, true)

        root.addView(back)

        back.setOnClickListener {
            showProfile()
        }

        addGap(20)

        root.addView(
            makeText(title, 23f, textColor, true)
        )

        addGap(15)

        val input = makeInput(hint)
        input.maxLines = 5

        root.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(100))
        )

        addGap(15)

        val save = makeButton("Сохранить")

        root.addView(
            save,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        db.child("users").child(uid).child(field).get()
            .addOnSuccessListener {
                input.setText(it.getValue(String::class.java) ?: "")
            }

        save.setOnClickListener {
            val value = input.text.toString().trim()

            if (value.length > maxLength) {
                toast("Максимум $maxLength символов")
                return@setOnClickListener
            }

            save.isEnabled = false

            db.child("users").child(uid).child(field).setValue(value)
                .addOnSuccessListener {
                    toast("Изменения сохранены")
                    showProfile()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    toast(it.message ?: "Ошибка сохранения")
                }
        }
    }

    private fun showEditName() {
        clearAllListeners()
        newRoot()

        val uid = auth.currentUser?.uid ?: return

        val back = makeText("‹  Назад", 16f, green, true)

        root.addView(back)

        back.setOnClickListener {
            showProfile()
        }

        addGap(20)

        root.addView(
            makeText("Изменить имя", 23f, textColor, true)
        )

        addGap(15)

        val input = makeInput("Новое имя")

        root.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        addGap(15)

        val save = makeButton("Сохранить имя")

        root.addView(
            save,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        db.child("users").child(uid).child("username").get()
            .addOnSuccessListener {
                input.setText(it.getValue(String::class.java) ?: "")
            }

        save.setOnClickListener {
            val name = input.text.toString().trim()

            if (name.length < 2 || name.length > 30) {
                toast("Имя должно содержать от 2 до 30 символов")
                return@setOnClickListener
            }

            save.isEnabled = false

            db.child("users").child(uid).child("username").setValue(name)
                .addOnSuccessListener {
                    toast("Имя изменено")
                    showProfile()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    toast(it.message ?: "Ошибка изменения имени")
                }
        }
    }

    private fun showEditHandle() {
        clearAllListeners()
        newRoot()

        val uid = auth.currentUser?.uid ?: return

        val back = makeText("‹  Назад", 16f, green, true)

        root.addView(back)

        back.setOnClickListener {
            showProfile()
        }

        addGap(20)

        root.addView(
            makeText("Твой username", 23f, textColor, true)
        )

        addGap(12)

        val input = makeInput("@username")

        root.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        addGap(15)

        val save = makeButton("Сохранить username")

        root.addView(
            save,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        db.child("users").child(uid).child("handle").get()
            .addOnSuccessListener {
                input.setText(it.getValue(String::class.java) ?: "")
            }

        save.setOnClickListener {
            val handle = input.text.toString().trim()
                .removePrefix("@")
                .lowercase(Locale.US)

            if (!handle.matches(Regex("^[a-z0-9_]{3,20}$"))) {
                toast("Нужно 3–20 английских букв, цифр или _")
                return@setOnClickListener
            }

            save.isEnabled = false

            db.child("users").child(uid).child("handle").get()
                .addOnSuccessListener { oldSnapshot ->

                    val oldHandle = oldSnapshot.getValue(String::class.java)

                    if (oldHandle == handle) {
                        save.isEnabled = true
                        toast("Этот username уже установлен")
                        return@addOnSuccessListener
                    }

                    db.child("usernames").child(handle).runTransaction(
                        object : Transaction.Handler {

                            override fun doTransaction(
                                currentData: MutableData
                            ): Transaction.Result {

                                val existing =
                                    currentData.getValue(String::class.java)

                                if (existing != null && existing != uid) {
                                    return Transaction.abort()
                                }

                                currentData.value = uid

                                return Transaction.success(currentData)
                            }

                            override fun onComplete(
                                error: DatabaseError?,
                                committed: Boolean,
                                currentData: DataSnapshot?
                            ) {

                                if (error != null || !committed) {
                                    save.isEnabled = true
                                    toast(error?.message ?: "Username уже занят")
                                    return
                                }

                                db.child("users").child(uid).child("handle")
                                    .setValue(handle)
                                    .addOnSuccessListener {

                                        if (!oldHandle.isNullOrBlank() &&
                                            oldHandle != handle
                                        ) {
                                            db.child("usernames")
                                                .child(oldHandle)
                                                .removeValue()
                                        }

                                        toast("Username сохранён")
                                        showProfile()
                                    }
                                    .addOnFailureListener {
                                        save.isEnabled = true
                                        toast(it.message ?: "Ошибка сохранения")
                                    }
                            }
                        }
                    )
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    toast(it.message ?: "Ошибка получения профиля")
                }
        }
    }

    // SETTINGS

    private fun settingsRow(
        icon: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panel, 14)
            setOnClickListener { onClick() }
        }

        val iconView = makeText(icon, 22f, green)

        row.addView(
            iconView,
            LinearLayout.LayoutParams(dp(40), -2)
        )

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        texts.addView(
            makeText(title, 15f, textColor, true)
        )

        texts.addView(
            makeText(subtitle, 11f, muted)
        )

        row.addView(
            texts,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        row.addView(makeText("›", 24f, muted))

        return row
    }

    private fun showSettings() {
        clearAllListeners()

        selectedSection = 2

        newRoot()

        root.addView(
            header("Настройки", "Настрой StalkerGram")
        )

        addGap(5)

        val uid = auth.currentUser?.uid ?: return

        val card = settingsCard()

        val name = makeText("Загрузка...", 16f, textColor, true)
        val email = makeText("", 12f, muted)

        card.addView(name)
        card.addView(email)

        root.addView(card)

        db.child("users").child(uid)
            .addListenerForSingleValueEvent(
                object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {
                        name.text = snapshot.child("username")
                            .getValue(String::class.java) ?: "Пользователь"

                        email.text = snapshot.child("email")
                            .getValue(String::class.java) ?: ""
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }
            )

        addGap(10)

        val scroll = ScrollView(this)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)

        list.addView(
            settingsRow(
                "●",
                "Уведомления",
                "Настройки уведомлений"
            ) {
                showSimpleSetting(
                    "Уведомления",
                    "Уведомления приложения можно настроить в параметрах Android."
                )
            }
        )

        addGap(7)

        list.addView(
            settingsRow(
                "◈",
                "Конфиденциальность",
                "Параметры приватности"
            ) {
                showSimpleSetting(
                    "Конфиденциальность",
                    "Настройки приватности будут расширены в следующих версиях."
                )
            }
        )

        addGap(7)

        list.addView(
            settingsRow(
                "◉",
                "Оформление",
                "Тёмная тема StalkerGram"
            ) {
                prefs.edit().putString("theme", "DARK").apply()
                toast("Тёмная тема активна")
            }
        )

        addGap(7)

        list.addView(
            settingsRow(
                "◎",
                "Язык",
                "Русский"
            ) {
                val languages = arrayOf(
                    "Русский",
                    "Українська",
                    "English"
                )

                AlertDialog.Builder(this)
                    .setTitle("Выбери язык")
                    .setItems(languages) { _, which ->
                        prefs.edit()
                            .putString("language", languages[which])
                            .apply()

                        toast("Язык сохранён")
                    }
                    .show()
            }
        )

        addGap(7)

        list.addView(
            settingsRow(
                "ⓘ",
                "О приложении",
                "StalkerGram • Версия 1.0.0"
            ) {
                showSimpleSetting(
                    "StalkerGram",
                    "Версия 1.0.0"
                )
            }
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        addBottomNavigation(2)
    }

    private fun showSimpleSetting(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ОК", null)
            .show()
    }

    // AUTHENTICATION

    private fun showAuth(registerMode: Boolean) {
        clearAllListeners()

        newRoot()

        addGap(35)

        val title = makeText(
            "StalkerGram",
            32f,
            green,
            true
        )

        title.gravity = Gravity.CENTER

        root.addView(
            title,
            LinearLayout.LayoutParams(-1, dp(60))
        )

        val subtitle = makeText(
            if (registerMode) {
                "Создай новый аккаунт"
            } else {
                "Добро пожаловать!"
            },
            15f,
            muted
        )

        subtitle.gravity = Gravity.CENTER

        root.addView(subtitle)

        addGap(30)

        val usernameInput = makeInput("Имя пользователя")

        root.addView(
            usernameInput,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        if (!registerMode) {
            usernameInput.visibility = View.GONE
        }

        addGap()

        val emailInput = makeInput("Электронная почта")
        emailInput.inputType = 33

        root.addView(
            emailInput,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        addGap()

        val passwordInput = makeInput("Пароль", true)

        root.addView(
            passwordInput,
            LinearLayout.LayoutParams(-1, dp(49))
        )

        addGap(20)

        val action = makeButton(
            if (registerMode) {
                "Создать аккаунт"
            } else {
                "Войти"
            }
        )

        root.addView(
            action,
            LinearLayout.LayoutParams(-1, dp(50))
        )

        val switch = Button(this).apply {
            text = if (registerMode) {
                "Уже есть аккаунт? Войти"
            } else {
                "Создать аккаунт"
            }

            isAllCaps = false
            typeface = Typeface.DEFAULT
            setTextColor(green)
            background = android.graphics.drawable.ColorDrawable(
                Color.TRANSPARENT
            )
        }

        root.addView(
            switch,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        action.setOnClickListener {

            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isBlank() || password.isBlank() ||
                (registerMode && username.isBlank())
            ) {
                toast("Заполни все поля")
                return@setOnClickListener
            }

            action.isEnabled = false

            if (registerMode) {

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->

                        action.isEnabled = true

                        if (task.isSuccessful) {

                            val uid = auth.currentUser?.uid
                                ?: return@addOnCompleteListener

                            val userData = mapOf(
                                "uid" to uid,
                                "username" to username,
                                "email" to email,
                                "createdAt" to ServerValue.TIMESTAMP
                            )

                            db.child("users").child(uid).setValue(userData)
                                .addOnSuccessListener {
                                    showHome()
                                }
                                .addOnFailureListener {
                                    toast(it.message ?: "Ошибка сохранения профиля")
                                }

                        } else {
                            toast(
                                task.exception?.message
                                    ?: "Ошибка регистрации"
                            )
                        }
                    }

            } else {

                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->

                        action.isEnabled = true

                        if (task.isSuccessful) {
                            showHome()
                        } else {
                            toast(
                                task.exception?.message
                                    ?: "Ошибка входа"
                            )
                        }
                    }
            }
        }

        switch.setOnClickListener {
            showAuth(!registerMode)
        }
    }

    override fun onBackPressed() {
        if (auth.currentUser == null) {
            super.onBackPressed()
        } else {
            showHome()
        }
    }

    override fun onDestroy() {
        clearAllListeners()
        super.onDestroy()
    }
}
