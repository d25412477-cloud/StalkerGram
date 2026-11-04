
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

        if (auth.currentUser == null) showAuth(false)
        else showHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun newRoot(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Color.rgb(10, 14, 18))
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
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(29, 38, 49), 12)
            if (password) inputType = 129
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun clearAllListeners() {
        chatListListener?.let { ChatListManager.removeListener(it) }
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

    // АВАТАРКИ

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

    private fun makeCircularBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2

        val result = Bitmap.createBitmap(
            size, size, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)
        val path = Path().apply {
            addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
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
            data?.data?.let { saveProfilePhoto(it) }
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
                output.toByteArray(), Base64.NO_WRAP
            )

            db.child("users").child(uid).child("photo")
                .setValue(encoded)
                .addOnSuccessListener {
                    toast("Фото профиля сохранено 💚")
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

    // НИЖНЯЯ ПАНЕЛЬ

    private fun addBottomNavigation(selected: Int) {
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(Color.rgb(20, 29, 36), 18)
        }

        val items = listOf(
            Triple("💬", "Чаты", 0),
            Triple("👥", "Контакты", 1),
            Triple("⚙", "Настройки", 2),
            Triple("👤", "Профиль", 3)
        )

        items.forEach { item ->
            val active = selected == item.third

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(
                    if (active) Color.rgb(25, 75, 55)
                    else Color.TRANSPARENT,
                    12
                )
                isClickable = true

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
                if (active) Color.rgb(25, 220, 130) else Color.LTGRAY
            )
            icon.gravity = Gravity.CENTER

            val label = makeText(
                item.second,
                10f,
                if (active) Color.rgb(25, 220, 130) else Color.LTGRAY,
                active
            )
            label.gravity = Gravity.CENTER

            tab.addView(icon)
            tab.addView(label)

            navigation.addView(
                tab,
                LinearLayout.LayoutParams(0, dp(55), 1f)
            )
        }

        val params = LinearLayout.LayoutParams(-1, dp(65))
        params.topMargin = dp(8)
        root.addView(navigation, params)
    }

    // ГЛАВНЫЙ ЭКРАН

    private fun showHome() {
        clearAllListeners()
        selectedSection = 0
        chatSearchQuery = ""
        newRoot()

        root.setPadding(dp(14), dp(8), dp(14), dp(8))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            makeText("StalkerGram", 25f, Color.rgb(25, 220, 130), true),
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        root.addView(header)

        val search = makeInput("Поиск")
        search.inputType = 1
        root.addView(search, LinearLayout.LayoutParams(-1, dp(46)))

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

        addGap(12)
        root.addView(makeText("ИСТОРИИ", 12f, Color.GRAY, true))
        addGap(6)

        val storiesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val stories = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        storiesScroll.addView(stories)

        val myStory = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
            setOnClickListener {
                toast("Добавление историй подключим следующим шагом")
            }
        }

        val myAvatar = makeAvatar("+")
        myAvatar.text = "+"
        myAvatar.textSize = 27f
        myAvatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(25, 65, 48))
            setStroke(dp(2), Color.rgb(25, 220, 130))
        }

        myStory.addView(myAvatar, LinearLayout.LayoutParams(dp(58), dp(58)))

        val myLabel = makeText("Моя история", 11f, Color.LTGRAY)
        myLabel.gravity = Gravity.CENTER
        myStory.addView(myLabel)

        stories.addView(myStory, LinearLayout.LayoutParams(dp(76), dp(88)))

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
                                toast("Истории появятся после подключения публикации")
                            }
                        }

                        val avatar = makeAvatar(name)
                        avatar.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.rgb(25, 65, 48))
                            setStroke(dp(2), Color.rgb(25, 220, 130))
                        }

                        story.addView(
                            avatar,
                            LinearLayout.LayoutParams(dp(58), dp(58))
                        )

                        val label = makeText(name, 11f, Color.LTGRAY)
                        label.gravity = Gravity.CENTER
                        label.maxLines = 1
                        story.addView(label)

                        stories.addView(
                            story,
                            LinearLayout.LayoutParams(dp(76), dp(88))
                        )

                        loadAvatar(uid, avatar)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
        )

        root.addView(storiesScroll, LinearLayout.LayoutParams(-1, dp(92)))

        addGap(8)
        root.addView(makeText("ВСЕ ЧАТЫ", 12f, Color.GRAY, true))
        addGap(6)

        val scroll = ScrollView(this)

        val chatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(chatsContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        loadChats(chatsContainer)

        val floating = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(0, dp(4), dp(5), dp(5))
        }

        val camera = makeText("📷", 20f, Color.WHITE)
        camera.gravity = Gravity.CENTER
        camera.background = rounded(Color.rgb(35, 45, 52), 50)

        floating.addView(camera, LinearLayout.LayoutParams(dp(43), dp(43)))

        camera.setOnClickListener {
            toast("Камеру для историй подключим следующим шагом")
        }

        val plus = makeText("+", 32f, Color.WHITE, true)
        plus.gravity = Gravity.CENTER
        plus.background = rounded(Color.rgb(25, 190, 110), 50)

        val plusParams = LinearLayout.LayoutParams(dp(58), dp(58))
        plusParams.topMargin = dp(8)

        floating.addView(plus, plusParams)
        plus.setOnClickListener { showContacts() }

        root.addView(floating, LinearLayout.LayoutParams(-1, -2))

        addBottomNavigation(0)
    }

    // СОЗДАНИЕ КАНАЛА

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
                    .addOnSuccessListener { toast("Канал создан 💚") }
                    .addOnFailureListener {
                        toast(it.message ?: "Ошибка создания канала")
                    }
            }
            .show()
    }

    // ИЗБРАННОЕ

    private fun showFavorites() {
        clearAllListeners()
        newRoot()

        root.addView(makeText("⭐  Избранное", 23f, Color.WHITE, true))
        addGap(15)

        val card = settingsCard()

        card.addView(
            makeText("Твои сохранённые сообщения", 17f, Color.WHITE, true)
        )

        card.addView(
            makeText(
                "Здесь будут храниться сообщения, которые ты сохранишь.",
                14f,
                Color.LTGRAY
            )
        )

        root.addView(card)
        addGap(15)

        val back = makeButton("‹  Вернуться в чаты")
        root.addView(back, LinearLayout.LayoutParams(-1, dp(50)))

        back.setOnClickListener { showHome() }
    }

    // СПИСОК ЧАТОВ

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
                            if (query.isBlank()) "Здесь пока нет чатов"
                            else "Чаты не найдены",
                            14f,
                            Color.GRAY
                        )

                        empty.gravity = Gravity.CENTER

                        container.addView(
                            empty,
                            LinearLayout.LayoutParams(-1, dp(75))
                        )

                    } else {
                        filtered.sortedByDescending { it.timestamp }.forEach { preview ->

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
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(Color.rgb(24, 32, 43), 12)
            setOnClickListener { onClick() }
        }

        val avatar = makeAvatar(name)

        item.addView(
            avatar,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        loadAvatar(uid, avatar)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
        }

        texts.addView(makeText(name, 16f, Color.WHITE, true))

        val messageText = makeText(message, 13f, Color.LTGRAY)
        messageText.maxLines = 1
        messageText.ellipsize = android.text.TextUtils.TruncateAt.END

        texts.addView(messageText)

        item.addView(
            texts,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        if (!time.isNullOrBlank()) {
            rightColumn.addView(makeText(time, 11f, Color.GRAY))
        }

        if (unreadCount > 0) {
            val badge = makeText(
                if (unreadCount > 99) "99+" else unreadCount.toString(),
                11f,
                Color.WHITE,
                true
            )

            badge.gravity = Gravity.CENTER

            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(25, 190, 110))
            }

            val badgeParams = LinearLayout.LayoutParams(
                dp(25),
                dp(25)
            )

            badgeParams.topMargin = dp(5)

            rightColumn.addView(badge, badgeParams)
        }

        item.addView(rightColumn)

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(7)

        item.layoutParams = params

        return item
    }

    // КОНТАКТЫ

    private fun showContacts() {
        clearAllListeners()
        selectedSection = 1
        searchQuery = ""
        newRoot()

        root.addView(makeText("Контакты", 25f, Color.WHITE, true))
        addGap(12)

        val search = makeInput("Поиск по имени или @username")

        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)))
        addGap(10)

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

                        users.add(UserItem(uid, username, handle))
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
                Color.GRAY
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
                    "Начать переписку",
                    null
                ) {
                    openChat(user.uid, user.username)
                }
            )
        }
    }

    // ЭКРАН ПЕРЕПИСКИ

    private fun openChat(otherUid: String, username: String) {
        clearAllListeners()

        activeChatUid = otherUid
        newRoot()

        root.setPadding(dp(12), dp(8), dp(12), dp(10))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = makeText("‹", 34f, Color.WHITE)
        back.gravity = Gravity.CENTER

        header.addView(
            back,
            LinearLayout.LayoutParams(dp(38), dp(48))
        )

        back.setOnClickListener {
            if (selectedSection == 1) showContacts()
            else showHome()
        }

        val avatar = makeAvatar(username)

        header.addView(
            avatar,
            LinearLayout.LayoutParams(dp(42), dp(42))
        )

        loadAvatar(otherUid, avatar)

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        titleBox.addView(makeText(username, 17f, Color.WHITE, true))
        titleBox.addView(makeText("Личная переписка", 11f, Color.GRAY))

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(header)

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(38, 47, 54))
        }

        val dividerParams = LinearLayout.LayoutParams(-1, dp(1))
        dividerParams.topMargin = dp(7)
        dividerParams.bottomMargin = dp(7)

        root.addView(divider, dividerParams)

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
            LinearLayout.LayoutParams(0, dp(50), 1f)
        )

        val sendButton = makeText("➤", 22f, Color.WHITE, true)
        sendButton.gravity = Gravity.CENTER
        sendButton.background = rounded(Color.rgb(25, 190, 110), 50)

        val sendParams = LinearLayout.LayoutParams(dp(50), dp(50))
        sendParams.leftMargin = dp(8)

        bottom.addView(sendButton, sendParams)
        root.addView(bottom)

        fun sendCurrentMessage() {
            val text = messageInput.text.toString().trim()
            if (text.isBlank()) return

            sendButton.isEnabled = false

            ChatManager.sendMessage(otherUid, text) { success, result ->
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

        // Отмечаем входящие сообщения прочитанными при открытии чата.
        ChatManager.markMessagesRead(otherUid)

        messagesListener = ChatManager.listenMessages(
            otherUid,

            onMessages = { messages ->

                // Также проверяем новые входящие сообщения,
                // пока переписка открыта.
                ChatManager.markMessagesRead(otherUid)

                messagesContainer.removeAllViews()

                if (messages.isEmpty()) {
                    val empty = makeText(
                        "Начни переписку 💚",
                        14f,
                        Color.GRAY
                    )

                    empty.gravity = Gravity.CENTER

                    messagesContainer.addView(
                        empty,
                        LinearLayout.LayoutParams(-1, dp(60))
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

                        val text = snapshot.child("text")
                            .getValue(String::class.java) ?: ""

                        val timestamp = snapshot.child("timestamp")
                            .getValue(Long::class.java) ?: 0L

                        val isMine = senderId == myUid

                        val bubble = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(12), dp(8), dp(12), dp(6))

                            background = rounded(
                                if (isMine) Color.rgb(20, 145, 90)
                                else Color.rgb(31, 40, 48),
                                17
                            )
                        }

                        bubble.addView(
                            makeText(text, 16f, Color.WHITE)
                        )

                        val time = if (timestamp > 0L) {
                            SimpleDateFormat(
                                "HH:mm",
                                Locale.getDefault()
                            ).format(Date(timestamp))
                        } else {
                            ""
                        }

                        val timeText = makeText(time, 10f, Color.LTGRAY)
                        timeText.gravity = Gravity.END

                        bubble.addView(timeText)

                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = if (isMine) Gravity.END else Gravity.START
                        }

                        row.addView(
                            bubble,
                            LinearLayout.LayoutParams(dp(270), -2)
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

    // ПРОФИЛЬ И ОБЩИЕ КАРТОЧКИ

    private fun settingsCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = rounded(Color.rgb(24, 29, 35), 18)
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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = makeText("‹", 34f)
        back.gravity = Gravity.CENTER

        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        back.setOnClickListener { showHome() }

        header.addView(
            makeText("Профиль", 22f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        val settings = makeText("⚙", 27f, Color.rgb(25, 220, 130))
        settings.gravity = Gravity.CENTER

        header.addView(settings, LinearLayout.LayoutParams(dp(48), dp(48)))
        settings.setOnClickListener { showSettings() }

        content.addView(header)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = makeAvatar("Я")

        top.addView(
            avatar,
            LinearLayout.LayoutParams(dp(100), dp(100))
        )

        loadAvatar(uid, avatar)
        avatar.setOnClickListener { chooseProfilePhoto() }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), 0, 0, 0)
        }

        val name = makeText("Загрузка...", 22f, Color.WHITE, true)
        val handle = makeText("@username", 15f, Color.rgb(25, 220, 130))
        val status = makeText("● StalkerGram", 13f, Color.LTGRAY)

        info.addView(name)
        info.addView(handle)
        info.addView(status)

        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(top)

        val edit = makeButton("✎  Изменить имя")

        content.addView(edit, LinearLayout.LayoutParams(-1, dp(50)))
        edit.setOnClickListener { showEditName() }

        val aboutCard = settingsCard()

        aboutCard.addView(
            makeText("О СЕБЕ", 12f, Color.rgb(25, 220, 130), true)
        )

        val about = makeText("Расскажи немного о себе", 14f, Color.LTGRAY)
        about.setPadding(0, dp(10), 0, 0)

        aboutCard.addView(about)

        about.setOnClickListener {
            showEditField("about", "Информация о себе", "О себе...", 200)
        }

        content.addView(aboutCard)

        val handleButton = makeButton("Изменить username")

        content.addView(
            handleButton,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        handleButton.setOnClickListener { showEditHandle() }

        val statusButton = makeButton("Изменить статус")

        content.addView(
            statusButton,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        statusButton.setOnClickListener {
            showEditField("status", "Статус", "Твой статус...", 60)
        }

        val logout = makeButton("Выйти из аккаунта")

        content.addView(
            logout,
            LinearLayout.LayoutParams(-1, dp(48))
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

        db.child("users").child(uid).addListenerForSingleValueEvent(
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
                        snapshot.child("status").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Аккаунт StalkerGram"
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

    // РЕДАКТИРОВАНИЕ ПРОФИЛЯ

    private fun showEditField(
        field: String,
        title: String,
        hint: String,
        maxLength: Int
    ) {
        clearAllListeners()
        newRoot()

        val uid = auth.currentUser?.uid ?: return

        root.addView(
            makeText("‹  Назад", 18f, Color.WHITE, true).apply {
                setOnClickListener { showProfile() }
            }
        )

        addGap(20)
        root.addView(makeText(title, 24f, Color.WHITE, true))
        addGap(15)

        val input = makeInput(hint)
        input.maxLines = 5

        root.addView(input, LinearLayout.LayoutParams(-1, dp(100)))

        addGap(15)

        val save = makeButton("Сохранить")

        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)))

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
                    toast("Изменения сохранены 💚")
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

        val back = makeText("‹  Назад", 18f, Color.WHITE)
        root.addView(back)

        back.setOnClickListener { showProfile() }

        addGap(20)
        root.addView(makeText("Изменить имя", 24f, Color.WHITE, true))
        addGap(15)

        val input = makeInput("Новое имя")

        root.addView(input, LinearLayout.LayoutParams(-1, dp(52)))

        addGap(15)

        val save = makeButton("Сохранить имя")

        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)))

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
                    toast("Имя изменено 💚")
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

        val back = makeText("‹  Назад", 18f, Color.WHITE)
        root.addView(back)

        back.setOnClickListener { showProfile() }

        addGap(20)
        root.addView(makeText("Твой username", 24f, Color.WHITE, true))
        addGap(12)

        val input = makeInput("@username")

        root.addView(input, LinearLayout.LayoutParams(-1, dp(52)))

        addGap(15)

        val save = makeButton("Сохранить username")

        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)))

        db.child("users").child(uid).child("handle").get()
            .addOnSuccessListener {
                input.setText(it.getValue(String::class.java) ?: "")
            }

        save.setOnClickListener {
            val handle = input.text.toString().trim()
                .removePrefix("@").lowercase(Locale.US)

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

                                val existing = currentData
                                    .getValue(String::class.java)

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
                                                .child(oldHandle).removeValue()
                                        }

                                        toast("Username сохранён 💚")
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

    // НАСТРОЙКИ

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
            background = rounded(Color.rgb(24, 29, 35), 14)
            setOnClickListener { onClick() }
        }

        val iconView = makeText(icon, 22f, Color.rgb(25, 220, 130))

        row.addView(iconView, LinearLayout.LayoutParams(dp(40), -2))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        texts.addView(makeText(title, 15f, Color.WHITE, true))
        texts.addView(makeText(subtitle, 12f, Color.LTGRAY))

        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(makeText("›", 24f, Color.GRAY))

        return row
    }

    private fun settingsHeader(title: String) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = makeText("‹", 34f)
        back.gravity = Gravity.CENTER

        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        back.setOnClickListener { showHome() }

        header.addView(
            makeText(title, 21f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        root.addView(header)
    }

    private fun showSettings() {
        clearAllListeners()
        selectedSection = 2
        newRoot()

        settingsHeader("Настройки")
        addGap(12)

        val uid = auth.currentUser?.uid ?: return

        val card = settingsCard()

        val name = makeText("Загрузка...", 18f, Color.WHITE, true)
        val email = makeText("", 13f, Color.LTGRAY)

        card.addView(name)
        card.addView(email)

        root.addView(card)

        db.child("users").child(uid).addListenerForSingleValueEvent(
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

        addGap(12)

        val scroll = ScrollView(this)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)

        list.addView(
            settingsRow(
                "🔔",
                "Уведомления",
                "Настройки уведомлений"
            ) {
                showSimpleSetting(
                    "Уведомления",
                    "Уведомления приложения можно настроить в параметрах Android."
                )
            }
        )

        list.addView(
            settingsRow(
                "🔒",
                "Конфиденциальность",
                "Параметры приватности"
            ) {
                showSimpleSetting(
                    "Конфиденциальность",
                    "Настройки приватности будут расширены в следующих версиях."
                )
            }
        )

        list.addView(
            settingsRow(
                "🎨",
                "Оформление",
                "Тёмная тема"
            ) {
                prefs.edit().putString("theme", "Тёмная").apply()
                toast("Тёмная тема включена")
            }
        )

        list.addView(
            settingsRow(
                "🌐",
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

        list.addView(
            settingsRow(
                "ⓘ",
                "О приложении",
                "StalkerGram • Версия 1.0.0"
            ) {
                showSimpleSetting("StalkerGram", "Версия 1.0.0")
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

    // АВТОРИЗАЦИЯ

    private fun showAuth(registerMode: Boolean) {
        clearAllListeners()
        newRoot()

        val title = makeText(
            "StalkerGram",
            30f,
            Color.rgb(25, 220, 130),
            true
        )

        title.gravity = Gravity.CENTER

        root.addView(title, LinearLayout.LayoutParams(-1, dp(70)))

        val subtitle = makeText(
            if (registerMode) "Создай новый аккаунт"
            else "Войди в свой аккаунт",
            16f,
            Color.LTGRAY
        )

        subtitle.gravity = Gravity.CENTER
        root.addView(subtitle)

        addGap(24)

        val usernameInput = makeInput("Имя пользователя")

        root.addView(
            usernameInput,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        if (!registerMode) {
            usernameInput.visibility = View.GONE
        }

        addGap()

        val emailInput = makeInput("Электронная почта")
        emailInput.inputType = 33

        root.addView(
            emailInput,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        addGap()

        val passwordInput = makeInput("Пароль", true)

        root.addView(
            passwordInput,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        addGap(18)

        val action = makeButton(
            if (registerMode) "Зарегистрироваться" else "Войти"
        )

        root.addView(
            action,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        val switch = Button(this).apply {
            text = if (registerMode) {
                "Уже есть аккаунт? Войти"
            } else {
                "Создать аккаунт"
            }

            isAllCaps = false
            setTextColor(Color.rgb(25, 220, 130))
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
                                    toast(
                                        it.message
                                            ?: "Ошибка сохранения профиля"
                                    )
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
                                task.exception?.message ?: "Ошибка входа"
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
