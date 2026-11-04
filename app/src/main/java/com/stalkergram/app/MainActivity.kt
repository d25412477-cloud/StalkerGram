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

    // PDA COLORS
    private val pdaBackground = Color.rgb(13, 18, 13)
    private val pdaPanel = Color.rgb(25, 34, 22)
    private val pdaPanelLight = Color.rgb(35, 46, 28)
    private val pdaGreen = Color.rgb(155, 190, 105)
    private val pdaBright = Color.rgb(190, 220, 130)
    private val pdaMuted = Color.rgb(112, 135, 91)
    private val pdaBorder = Color.rgb(76, 99, 55)
    private val pdaText = Color.rgb(218, 230, 195)
    private val pdaWarning = Color.rgb(190, 155, 80)

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

        window.statusBarColor = pdaBackground
        window.navigationBarColor = pdaBackground

        if (auth.currentUser == null) showAuth(false)
        else showHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun newRoot(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(pdaBackground)
        }
        setContentView(root)
        return root
    }

    private fun makeText(
        value: String,
        size: Float = 16f,
        color: Int = pdaText,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = if (bold) {
                Typeface.create("monospace", Typeface.BOLD)
            } else {
                Typeface.create("monospace", Typeface.NORMAL)
            }
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), pdaBorder)
        }
    }

    private fun makeInput(
        hintText: String,
        password: Boolean = false
    ): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(pdaMuted)
            setTextColor(pdaBright)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(pdaPanel, 7)
            if (password) inputType = 129
        }
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(pdaBright)
            background = rounded(pdaPanelLight, 7)
        }
    }

    private fun addGap(height: Int = 10) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun addPdaDivider(parent: LinearLayout = root) {
        val line = View(this).apply {
            setBackgroundColor(pdaBorder)
        }
        val params = LinearLayout.LayoutParams(-1, dp(1))
        params.topMargin = dp(5)
        params.bottomMargin = dp(5)
        parent.addView(line, params)
    }

    private fun pdaHeader(
        title: String,
        subtitle: String = "PDA NETWORK // ZONE"
    ): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(pdaPanel, 7)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        top.addView(
            makeText(title, 20f, pdaBright, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        top.addView(makeText("● ONLINE", 10f, pdaGreen, true))
        header.addView(top)

        val sub = makeText(subtitle, 9f, pdaMuted)
        sub.setPadding(0, dp(5), 0, 0)
        header.addView(sub)

        return header
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

    // AVATARS

    private fun makeAvatar(name: String): TextView {
        val initial = name.trim().firstOrNull()?.toString()
            ?.uppercase(Locale.getDefault()) ?: "?"

        return TextView(this).apply {
            text = initial
            textSize = 18f
            setTextColor(pdaBright)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pdaPanelLight)
                setStroke(dp(2), pdaGreen)
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
            background = rounded(pdaPanel, 7)
        }

        val items = listOf(
            Triple("▤", "ЧАТЫ", 0),
            Triple("♙", "ЛЮДИ", 1),
            Triple("⚙", "ПАРАМЕТРЫ", 2),
            Triple("◉", "ПРОФИЛЬ", 3)
        )

        items.forEach { item ->
            val active = selected == item.third

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(
                    if (active) pdaPanelLight else Color.TRANSPARENT,
                    5
                )
                isClickable = true
                setPadding(dp(2), dp(4), dp(2), dp(4))

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
                if (active) pdaBright else pdaMuted,
                true
            )
            icon.gravity = Gravity.CENTER

            val label = makeText(
                item.second,
                8f,
                if (active) pdaBright else pdaMuted,
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

    // ГЛАВНЫЙ ЭКРАН PDA

    private fun showHome() {
        clearAllListeners()
        selectedSection = 0
        chatSearchQuery = ""
        newRoot()

        root.setPadding(dp(8), dp(7), dp(8), dp(7))

        // ВЕРХНЯЯ ПАНЕЛЬ УСТРОЙСТВА
        val device = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.rgb(29, 39, 24), 4)
        }

        val topLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        topLine.addView(
            makeText("PDA", 25f, pdaBright, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        topLine.addView(makeText("● ONLINE", 10f, pdaGreen, true))
        device.addView(topLine)

        addPdaDivider(device)

        val infoLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        infoLine.addView(
            makeText("STALKERGRAM // ЗОНА", 9f, pdaMuted, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        infoLine.addView(
            makeText(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                12f,
                pdaWarning,
                true
            )
        )

        device.addView(infoLine)
        root.addView(device)

        addGap(7)

        // НАЗВАНИЕ РАЗДЕЛА
        val titlePanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(pdaPanelLight, 3)
        }

        titlePanel.addView(
            makeText("ЖУРНАЛ СООБЩЕНИЙ", 13f, pdaBright, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        titlePanel.addView(makeText("ЗОНА / СВЯЗЬ", 8f, pdaWarning))
        root.addView(titlePanel)

        addGap(7)

        // ПОИСК
        val search = makeInput("Поиск сталкера или переписки...")
        search.inputType = 1

        root.addView(search, LinearLayout.LayoutParams(-1, dp(43)))

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

        addGap(7)

        // ИСТОРИИ
        val storiesHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        storiesHeader.addView(
            makeText("[ ИСТОРИИ СТАЛКЕРОВ ]", 10f, pdaGreen, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        storiesHeader.addView(makeText("● PDA", 8f, pdaMuted))
        root.addView(storiesHeader)

        addGap(4)

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
        myAvatar.textSize = 25f

        myStory.addView(myAvatar, LinearLayout.LayoutParams(dp(54), dp(54)))

        val myLabel = makeText("МОЯ PDA", 8f, pdaGreen)
        myLabel.gravity = Gravity.CENTER
        myStory.addView(myLabel)

        stories.addView(myStory, LinearLayout.LayoutParams(dp(72), dp(78)))

        val myUid = auth.currentUser?.uid

        db.child("users").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val uid = child.key ?: continue
                        if (uid == myUid) continue

                        val name = child.child("username")
                            .getValue(String::class.java) ?: "Сталкер"

                        val story = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(dp(5), dp(3), dp(5), dp(3))
                            setOnClickListener {
                                toast("Истории появятся после подключения публикации")
                            }
                        }

                        val avatar = makeAvatar(name)

                        story.addView(
                            avatar,
                            LinearLayout.LayoutParams(dp(54), dp(54))
                        )

                        val label = makeText(name, 8f, pdaText)
                        label.gravity = Gravity.CENTER
                        label.maxLines = 1

                        story.addView(label)

                        stories.addView(
                            story,
                            LinearLayout.LayoutParams(dp(72), dp(78))
                        )

                        loadAvatar(uid, avatar)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
        )

        root.addView(storiesScroll, LinearLayout.LayoutParams(-1, dp(82)))

        addGap(5)
        addPdaDivider()

        // СПИСОК ПЕРЕГОВОРОВ
        val chatTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        chatTitle.addView(
            makeText("[ ВХОДЯЩИЕ ПЕРЕГОВОРЫ ]", 10f, pdaGreen, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        chatTitle.addView(makeText("CHANNEL 01", 8f, pdaMuted))
        root.addView(chatTitle)

        addGap(5)

        val scroll = ScrollView(this)

        val chatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        scroll.addView(chatsContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        loadChats(chatsContainer)

        // КНОПКИ PDA
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(3))
        }

        val favorites = makeButton("★ ЖУРНАЛ")
        actions.addView(
            favorites,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        favorites.setOnClickListener { showFavorites() }

        val channel = makeButton("+ КАНАЛ")
        val channelParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        channelParams.leftMargin = dp(6)
        actions.addView(channel, channelParams)
        channel.setOnClickListener { showCreateChannel() }

        root.addView(actions)

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
                    .addOnSuccessListener { toast("Канал создан") }
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

        root.addView(pdaHeader("ИЗБРАННОЕ", "PDA STORAGE"))
        addGap(12)

        val card = settingsCard()

        card.addView(
            makeText("СОХРАНЁННЫЕ ДАННЫЕ", 13f, pdaGreen, true)
        )

        card.addView(
            makeText(
                "Здесь будут храниться сообщения, которые ты сохранишь.",
                13f,
                pdaText
            )
        )

        root.addView(card)
        addGap(15)

        val back = makeButton("‹  ВЕРНУТЬСЯ В ЧАТЫ")
        root.addView(back, LinearLayout.LayoutParams(-1, dp(48)))

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
                            if (query.isBlank()) "НЕТ АКТИВНЫХ ПЕРЕГОВОРОВ"
                            else "СТАЛКЕР НЕ НАЙДЕН",
                            12f,
                            pdaMuted
                        )

                        empty.gravity = Gravity.CENTER

                        container.addView(
                            empty,
                            LinearLayout.LayoutParams(-1, dp(90))
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
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(pdaPanel, 6)
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
            setPadding(dp(11), 0, dp(4), 0)
        }

        texts.addView(makeText(name.uppercase(Locale.getDefault()), 13f, pdaBright, true))

        val messageText = makeText(message, 11f, pdaMuted)
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
            rightColumn.addView(makeText(time, 9f, pdaMuted))
        }

        if (unreadCount > 0) {
            val badge = makeText(
                if (unreadCount > 99) "99+" else unreadCount.toString(),
                10f,
                pdaBackground,
                true
            )

            badge.gravity = Gravity.CENTER

            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pdaBright)
            }

            val badgeParams = LinearLayout.LayoutParams(dp(24), dp(24))
            badgeParams.topMargin = dp(5)
            rightColumn.addView(badge, badgeParams)
        }

        item.addView(rightColumn)

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(6)
        item.layoutParams = params

        return item
    }

    // КОНТАКТЫ

    private fun showContacts() {
        clearAllListeners()
        selectedSection = 1
        searchQuery = ""
        newRoot()

        root.addView(pdaHeader("КОНТАКТЫ", "PDA DATABASE // STALKERS"))
        addGap(10)

        val search = makeInput("Поиск по имени или @username")
        root.addView(search, LinearLayout.LayoutParams(-1, dp(46)))
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
                            ?: "Сталкер"

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
                if (searchQuery.isBlank()) "БАЗА СТАЛКЕРОВ ПУСТА"
                else "СОВПАДЕНИЙ НЕ НАЙДЕНО",
                12f,
                pdaMuted
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
                    "ОТКРЫТЬ ПЕРЕГОВОРЫ",
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

        root.setPadding(dp(10), dp(8), dp(10), dp(8))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = rounded(pdaPanel, 6)
        }

        val back = makeText("‹", 30f, pdaBright, true)
        back.gravity = Gravity.CENTER

        header.addView(back, LinearLayout.LayoutParams(dp(35), dp(45)))

        back.setOnClickListener {
            if (selectedSection == 1) showContacts()
            else showHome()
        }

        val avatar = makeAvatar(username)
        header.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))
        loadAvatar(otherUid, avatar)

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        titleBox.addView(
            makeText(username.uppercase(Locale.getDefault()), 13f, pdaBright, true)
        )
        titleBox.addView(makeText("PDA CONNECTION // PRIVATE", 9f, pdaMuted))

        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        addGap(5)

        val divider = View(this).apply {
            setBackgroundColor(pdaBorder)
        }

        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

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

        val messageInput = makeInput("Ввести сообщение...")
        messageInput.maxLines = 4
        messageInput.inputType = 1
        messageInput.imeOptions = EditorInfo.IME_ACTION_SEND

        bottom.addView(
            messageInput,
            LinearLayout.LayoutParams(0, dp(49), 1f)
        )

        val sendButton = makeText("➤", 21f, pdaBright, true)
        sendButton.gravity = Gravity.CENTER
        sendButton.background = rounded(pdaPanelLight, 7)

        val sendParams = LinearLayout.LayoutParams(dp(50), dp(49))
        sendParams.leftMargin = dp(7)

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

        sendButton.setOnClickListener { sendCurrentMessage() }

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
                        "КАНАЛ СВЯЗИ ОТКРЫТ\nНАЧНИ ПЕРЕГОВОРЫ",
                        11f,
                        pdaMuted
                    )

                    empty.gravity = Gravity.CENTER

                    messagesContainer.addView(
                        empty,
                        LinearLayout.LayoutParams(-1, dp(75))
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

                        val status = snapshot.child("status")
                            .getValue(String::class.java) ?: "sent"

                        val isMine = senderId == myUid

                        val bubble = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(11), dp(8), dp(11), dp(7))

                            background = rounded(
                                if (isMine) Color.rgb(43, 61, 32)
                                else pdaPanel,
                                7
                            )
                        }

                        bubble.addView(makeText(text, 14f, pdaText))

                        val time = if (timestamp > 0L) {
                            SimpleDateFormat(
                                "HH:mm",
                                Locale.getDefault()
                            ).format(Date(timestamp))
                        } else {
                            "--:--"
                        }

                        val meta = if (isMine) {
                            val ticks = if (status == "read") "✓✓" else "✓"
                            "$time   $ticks"
                        } else {
                            time
                        }

                        val timeText = makeText(
                            meta,
                            9f,
                            if (isMine && status == "read") pdaBright else pdaMuted
                        )
                        timeText.gravity = Gravity.END
                        bubble.addView(timeText)

                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = if (isMine) Gravity.END else Gravity.START
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

    // ПРОФИЛЬ И ОБЩИЕ КАРТОЧКИ

    private fun settingsCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(pdaPanel, 7)
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

        content.addView(pdaHeader("ЛИЧНОЕ ДЕЛО", "PDA USER PROFILE"))
        addGap(10)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(pdaPanel, 7)
        }

        val avatar = makeAvatar("Я")
        top.addView(avatar, LinearLayout.LayoutParams(dp(90), dp(90)))

        loadAvatar(uid, avatar)
        avatar.setOnClickListener { chooseProfilePhoto() }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }

        val name = makeText("ЗАГРУЗКА...", 17f, pdaBright, true)
        val handle = makeText("@username", 12f, pdaGreen)
        val status = makeText("● STALKER", 10f, pdaMuted)

        info.addView(name)
        info.addView(handle)
        info.addView(status)

        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(top)

        addGap(8)

        val edit = makeButton("✎  ИЗМЕНИТЬ ИМЯ")
        content.addView(edit, LinearLayout.LayoutParams(-1, dp(46)))
        edit.setOnClickListener { showEditName() }

        addGap(8)

        val aboutCard = settingsCard()
        aboutCard.addView(
            makeText("[ ИНФОРМАЦИЯ О СТАЛКЕРЕ ]", 11f, pdaGreen, true)
        )

        val about = makeText("Расскажи немного о себе", 12f, pdaText)
        about.setPadding(0, dp(10), 0, 0)
        aboutCard.addView(about)

        about.setOnClickListener {
            showEditField("about", "Информация о себе", "О себе...", 200)
        }

        content.addView(aboutCard)

        addGap(8)

        val handleButton = makeButton("ИЗМЕНИТЬ USERNAME")
        content.addView(handleButton, LinearLayout.LayoutParams(-1, dp(46)))
        handleButton.setOnClickListener { showEditHandle() }

        addGap(7)

        val statusButton = makeButton("ИЗМЕНИТЬ СТАТУС")
        content.addView(statusButton, LinearLayout.LayoutParams(-1, dp(46)))
        statusButton.setOnClickListener {
            showEditField("status", "Статус", "Твой статус...", 60)
        }

        addGap(7)

        val logout = makeButton("ВЫЙТИ ИЗ АККАУНТА")
        content.addView(logout, LinearLayout.LayoutParams(-1, dp(46)))

        logout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Завершить сеанс?")
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
                        .getValue(String::class.java) ?: "Сталкер"

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
            makeText("‹  НАЗАД", 13f, pdaBright, true).apply {
                setOnClickListener { showProfile() }
            }
        )

        addGap(20)
        root.addView(makeText(title.uppercase(Locale.getDefault()), 19f, pdaBright, true))
        addGap(15)

        val input = makeInput(hint)
        input.maxLines = 5

        root.addView(input, LinearLayout.LayoutParams(-1, dp(100)))
        addGap(15)

        val save = makeButton("СОХРАНИТЬ")
        root.addView(save, LinearLayout.LayoutParams(-1, dp(49)))

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

        val back = makeText("‹  НАЗАД", 13f, pdaBright)
        root.addView(back)
        back.setOnClickListener { showProfile() }

        addGap(20)
        root.addView(makeText("ИЗМЕНИТЬ ИМЯ", 19f, pdaBright, true))
        addGap(15)

        val input = makeInput("Новое имя")
        root.addView(input, LinearLayout.LayoutParams(-1, dp(49)))

        addGap(15)

        val save = makeButton("СОХРАНИТЬ ИМЯ")
        root.addView(save, LinearLayout.LayoutParams(-1, dp(49)))

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

        val back = makeText("‹  НАЗАД", 13f, pdaBright)
        root.addView(back)
        back.setOnClickListener { showProfile() }

        addGap(20)
        root.addView(makeText("ТВОЙ USERNAME", 19f, pdaBright, true))
        addGap(12)

        val input = makeInput("@username")
        root.addView(input, LinearLayout.LayoutParams(-1, dp(49)))

        addGap(15)

        val save = makeButton("СОХРАНИТЬ USERNAME")
        root.addView(save, LinearLayout.LayoutParams(-1, dp(49)))

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
                                val existing = currentData.getValue(String::class.java)

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
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = rounded(pdaPanel, 6)
            setOnClickListener { onClick() }
        }

        val iconView = makeText(icon, 19f, pdaGreen)
        row.addView(iconView, LinearLayout.LayoutParams(dp(38), -2))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        texts.addView(makeText(title, 13f, pdaBright, true))
        texts.addView(makeText(subtitle, 10f, pdaMuted))

        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(makeText("›", 21f, pdaMuted))

        return row
    }

    private fun settingsHeader(title: String) {
        root.addView(
            pdaHeader(
                title.uppercase(Locale.getDefault()),
                "PDA SYSTEM SETTINGS"
            )
        )
    }

    private fun showSettings() {
        clearAllListeners()
        selectedSection = 2
        newRoot()

        settingsHeader("НАСТРОЙКИ")
        addGap(10)

        val uid = auth.currentUser?.uid ?: return

        val card = settingsCard()
        val name = makeText("ЗАГРУЗКА...", 15f, pdaBright, true)
        val email = makeText("", 10f, pdaMuted)

        card.addView(name)
        card.addView(email)
        root.addView(card)

        db.child("users").child(uid).addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    name.text = snapshot.child("username")
                        .getValue(String::class.java) ?: "Сталкер"

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
            settingsRow("◉", "Уведомления", "Настройки уведомлений") {
                showSimpleSetting(
                    "Уведомления",
                    "Уведомления приложения можно настроить в параметрах Android."
                )
            }
        )

        addGap(6)

        list.addView(
            settingsRow("▣", "Конфиденциальность", "Параметры приватности") {
                showSimpleSetting(
                    "Конфиденциальность",
                    "Настройки приватности будут расширены в следующих версиях."
                )
            }
        )

        addGap(6)

        list.addView(
            settingsRow("◈", "Оформление", "Тема ПДА") {
                prefs.edit().putString("theme", "PDA").apply()
                toast("Тема ПДА активна")
            }
        )

        addGap(6)

        list.addView(
            settingsRow("◎", "Язык", "Русский") {
                val languages = arrayOf("Русский", "Українська", "English")

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

        addGap(6)

        list.addView(
            settingsRow("ⓘ", "О приложении", "StalkerGram • Версия 1.0.0") {
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

        val title = makeText("STALKERGRAM", 27f, pdaBright, true)
        title.gravity = Gravity.CENTER

        root.addView(title, LinearLayout.LayoutParams(-1, dp(70)))

        val subtitle = makeText(
            if (registerMode) "РЕГИСТРАЦИЯ НОВОГО СТАЛКЕРА"
            else "PDA NETWORK // ВХОД В СИСТЕМУ",
            11f,
            pdaMuted
        )

        subtitle.gravity = Gravity.CENTER
        root.addView(subtitle)

        addGap(24)

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

        addGap(18)

        val action = makeButton(
            if (registerMode) "СОЗДАТЬ АККАУНТ" else "ВОЙТИ В PDA"
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
            typeface = Typeface.MONOSPACE
            setTextColor(pdaGreen)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
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
                            toast(task.exception?.message ?: "Ошибка регистрации")
                        }
                    }

            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        action.isEnabled = true

                        if (task.isSuccessful) {
                            showHome()
                        } else {
                            toast(task.exception?.message ?: "Ошибка входа")
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
