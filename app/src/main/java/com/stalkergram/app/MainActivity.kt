
package com.stalkergram.app

import android.app.Activity
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

    private lateinit var root: LinearLayout
    private val users = mutableListOf<UserItem>()

    private var chatListListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null
    private var activeChatUid: String? = null
    private var searchQuery = ""

    private val PICK_IMAGE_REQUEST = 1001

    private data class UserItem(
        val uid: String,
        val username: String,
        val handle: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            showAuth(false)
        } else {
            showHome()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

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

        val uid = activeChatUid
        val listener = messagesListener

        if (uid != null && listener != null) {
            ChatManager.removeMessagesListener(uid, listener)
        }

        messagesListener = null
        activeChatUid = null
    }

    // ФОТОГРАФИЯ

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
            val uri = data?.data
            if (uri != null) saveProfilePhoto(uri)
        }
    }

    private fun saveProfilePhoto(uri: android.net.Uri) {
        val uid = auth.currentUser?.uid ?: return

        try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var sampleSize = 1

            while (
                bounds.outWidth / sampleSize > 512 ||
                bounds.outHeight / sampleSize > 512
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            if (bitmap == null) {
                toast("Не удалось открыть фотографию")
                return
            }

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
                    toast("Фото профиля сохранено! 💚")
                    showProfile()
                }
                .addOnFailureListener {
                    toast(it.message ?: "Ошибка сохранения фотографии")
                }

            if (bitmap != resized) bitmap.recycle()
            resized.recycle()

        } catch (e: Exception) {
            toast("Ошибка обработки фотографии")
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
            .addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val encoded =
                            snapshot.getValue(String::class.java) ?: return

                        try {
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(
                                bytes, 0, bytes.size
                            ) ?: return

                            val circular = makeCircularBitmap(bitmap)

                            avatar.text = ""
                            avatar.background = BitmapDrawable(
                                resources, circular
                            )

                            bitmap.recycle()
                        } catch (_: Exception) {
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }
            )
    }

    // ПРОФИЛЬ

    private fun showProfile() {
        clearAllListeners()
        newRoot()

        val uid = auth.currentUser?.uid ?: return

        root.setPadding(dp(18), dp(10), dp(18), dp(12))

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        fun gap(height: Int) {
            content.addView(
                View(this),
                LinearLayout.LayoutParams(1, dp(height))
            )
        }

        // ВЕРХНЯЯ ПАНЕЛЬ

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = Button(this).apply {
            text = "‹"
            textSize = 32f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.ColorDrawable(
                Color.TRANSPARENT
            )
            setOnClickListener { showHome() }
        }

        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))

        val headerTitle = makeText(
            "ПРОФИЛЬ", 17f, Color.WHITE, true
        )
        headerTitle.letterSpacing = 0.12f

        header.addView(
            headerTitle,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        val menu = makeText("⋮", 27f, Color.LTGRAY)
        menu.gravity = Gravity.CENTER

        header.addView(menu, LinearLayout.LayoutParams(dp(35), dp(48)))
        content.addView(header)

        gap(22)

        // АВАТАР И ИМЯ

        val profileTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = makeAvatar("Я")

        avatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(25, 155, 100))
            setStroke(dp(3), Color.rgb(25, 220, 130))
        }

        profileTop.addView(
            avatar,
            LinearLayout.LayoutParams(dp(132), dp(132))
        )

        loadAvatar(uid, avatar)
        avatar.setOnClickListener { chooseProfilePhoto() }

        val profileText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), 0, 0, 0)
        }

        val nameText = makeText(
            "Загрузка...", 24f, Color.WHITE, true
        )

        val handleText = makeText(
            "@username", 17f, Color.rgb(25, 220, 130), true
        )

        val status = makeText(
            "● Аккаунт StalkerGram",
            12f,
            Color.rgb(100, 190, 135)
        )

        status.isClickable = true
        status.setOnClickListener {
            showEditField(
                "status",
                "Мой статус",
                "Например: На Зоне 💚",
                60
            )
        }

        profileText.addView(nameText)
        profileText.addView(handleText)
        profileText.addView(status)

        profileTop.addView(
            profileText,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        content.addView(profileTop)

        gap(28)

        // КАРТОЧКИ ДЕЙСТВИЙ

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun actionCard(
            symbol: String,
            label: String,
            onClick: () -> Unit
        ): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                background = rounded(Color.rgb(24, 43, 34), 20)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }

                val iconText = makeText(
                    symbol, 29f, Color.rgb(25, 230, 125), true
                )
                iconText.gravity = Gravity.CENTER
                addView(iconText)

                val labelText = makeText(label, 13f, Color.WHITE)
                labelText.gravity = Gravity.CENTER
                addView(labelText)
            }
        }

        val photoCard = actionCard("📷", "Изменить\nфото") {
            chooseProfilePhoto()
        }

        val nameCard = actionCard("✎", "Изменить\nимя") {
            showEditName()
        }

        val handleCard = actionCard("@", "Изменить\nusername") {
            showEditHandle()
        }

        val p1 = LinearLayout.LayoutParams(0, dp(120), 1f)
        p1.marginEnd = dp(5)
        actions.addView(photoCard, p1)

        val p2 = LinearLayout.LayoutParams(0, dp(120), 1f)
        p2.marginStart = dp(3)
        p2.marginEnd = dp(3)
        actions.addView(nameCard, p2)

        val p3 = LinearLayout.LayoutParams(0, dp(120), 1f)
        p3.marginStart = dp(5)
        actions.addView(handleCard, p3)

        content.addView(actions)

        gap(22)

        // ИНФОРМАЦИЯ О ПРОФИЛЕ

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.rgb(28, 31, 37), 22)
        }

        infoCard.addView(
            makeText("StalkerGram 💚", 21f, Color.WHITE, true)
        )

        infoCard.addView(
            makeText(
                "Твой личный аккаунт в StalkerGram",
                14f,
                Color.LTGRAY
            )
        )

        val aboutText = makeText(
            "О себе: расскажи немного о себе",
            14f,
            Color.LTGRAY
        )

        aboutText.setPadding(0, dp(12), 0, dp(8))
        aboutText.isClickable = true

        aboutText.setOnClickListener {
            showEditField(
                "about",
                "Информация о себе",
                "Расскажи немного о себе...",
                200
            )
        }

        infoCard.addView(aboutText)

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(55, 60, 65))
        }

        val dividerParams = LinearLayout.LayoutParams(-1, dp(1))
        dividerParams.topMargin = dp(12)
        dividerParams.bottomMargin = dp(14)

        infoCard.addView(divider, dividerParams)

        infoCard.addView(
            makeText(
                "ИНФОРМАЦИЯ ОБ АККАУНТЕ",
                12f,
                Color.rgb(25, 220, 130),
                true
            )
        )

        val emailText = makeText(
            "Загрузка почты...", 14f, Color.LTGRAY
        )

        val emailParams = LinearLayout.LayoutParams(-1, -2)
        emailParams.topMargin = dp(10)

        infoCard.addView(emailText, emailParams)

        content.addView(
            infoCard,
            LinearLayout.LayoutParams(-1, -2)
        )

        // ЗАГРУЗКА ДАННЫХ FIREBASE

        db.child("users").child(uid)
            .addListenerForSingleValueEvent(
                object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {

                        nameText.text =
                            snapshot.child("username")
                                .getValue(String::class.java)
                                ?: "Пользователь"

                        val handle = snapshot.child("handle")
                            .getValue(String::class.java)

                        handleText.text =
                            if (handle.isNullOrBlank()) {
                                "@username не задан"
                            } else {
                                "@$handle"
                            }

                        emailText.text =
                            snapshot.child("email")
                                .getValue(String::class.java)
                                ?: "Почта не указана"

                        val savedStatus = snapshot.child("status")
                            .getValue(String::class.java)

                        status.text = "● " + (
                            savedStatus?.takeIf { it.isNotBlank() }
                                ?: "Аккаунт StalkerGram"
                            )

                        val about = snapshot.child("about")
                            .getValue(String::class.java)

                        aboutText.text =
                            if (about.isNullOrBlank()) {
                                "О себе: расскажи немного о себе"
                            } else {
                                "О себе: $about"
                            }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        toast(error.message)
                    }
                }
            )

        gap(18)

        val backHome = makeButton("⌂   Вернуться в чаты")

        content.addView(
            backHome,
            LinearLayout.LayoutParams(-1, dp(54))
        )

        backHome.setOnClickListener { showHome() }

        gap(15)
    }

    // РЕДАКТИРОВАНИЕ СТАТУСА И ИНФОРМАЦИИ

    private fun showEditField(
        field: String,
        title: String,
        hint: String,
        maxLength: Int
    ) {
        clearAllListeners()
        newRoot()

        val uid = auth.currentUser?.uid ?: return

        val back = makeButton("‹  Назад")
        root.addView(back, LinearLayout.LayoutParams(-1, dp(48)))
        back.setOnClickListener { showProfile() }

        addGap(25)

        root.addView(
            makeText(title, 24f, Color.WHITE, true)
        )

        addGap(15)

        val input = makeInput(hint)
        input.maxLines = 5

        root.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(100))
        )

        addGap(18)

        val save = makeButton("Сохранить")
        root.addView(
            save,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        db.child("users").child(uid).child(field)
            .get()
            .addOnSuccessListener {
                input.setText(it.getValue(String::class.java) ?: "")
            }
            .addOnFailureListener {
                toast("Не удалось загрузить данные")
            }

        save.setOnClickListener {
            val value = input.text.toString().trim()

            if (value.length > maxLength) {
                toast("Максимум $maxLength символов")
                return@setOnClickListener
            }

            save.isEnabled = false

            db.child("users").child(uid).child(field)
                .setValue(value)
                .addOnSuccessListener {
                    toast("Изменения сохранены! 💚")
                    showProfile()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    toast(it.message ?: "Ошибка сохранения")
                }
        }
    }

    // ИЗМЕНЕНИЕ ИМЕНИ

    private fun showEditName() {
        clearAllListeners()
        newRoot()

        val back = makeButton("‹  Назад")
        root.addView(back, LinearLayout.LayoutParams(-1, dp(48)))
        back.setOnClickListener { showProfile() }

        addGap(25)

        root.addView(
            makeText("Изменить имя", 24f, Color.WHITE, true)
        )

        addGap(15)

        val input = makeInput("Новое имя")
        root.addView(input, LinearLayout.LayoutParams(-1, dp(52)))

        addGap(18)

        val save = makeButton("Сохранить имя")
        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)))

        val uid = auth.currentUser?.uid ?: return

        db.child("users").child(uid).child("username")
            .get()
            .addOnSuccessListener {
                input.setText(it.getValue(String::class.java) ?: "")
            }

        save.setOnClickListener {
            val name = input.text.toString().trim()

            if (name.length < 2) {
                toast("Имя должно содержать минимум 2 символа")
                return@setOnClickListener
            }

            if (name.length > 30) {
                toast("Имя слишком длинное")
                return@setOnClickListener
            }

            save.isEnabled = false

            db.child("users").child(uid).child("username")
                .setValue(name)
                .addOnSuccessListener {
                    toast("Имя изменено! 💚")
                    showProfile()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    toast(it.message ?: "Ошибка изменения имени")
                }
        }
    }

    // ИЗМЕНЕНИЕ USERNAME

    private fun showEditHandle() {
        clearAllListeners()
        newRoot()

        val back = makeButton("‹  Назад")
        root.addView(back, LinearLayout.LayoutParams(-1, dp(48)))
        back.setOnClickListener { showProfile() }

        addGap(25)

        root.addView(
            makeText("Твой username", 24f, Color.WHITE, true)
        )

        addGap(10)

        root.addView(
            makeText(
                "От 3 до 20 символов. Только английские буквы, цифры и знак _",
                13f,
                Color.LTGRAY
            )
        )

        addGap(15)

        val input = makeInput("@username")
        root.addView(input, LinearLayout.LayoutParams(-1, dp(52)))

        addGap(18)

        val save = makeButton("Сохранить username")
        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)))

        val uid = auth.currentUser?.uid ?: return

        db.child("users").child(uid).child("handle")
            .get()
            .addOnSuccessListener {
                val old = it.getValue(String::class.java)
                if (!old.isNullOrBlank()) input.setText(old)
            }

        save.setOnClickListener {
            val handle = input.text.toString()
                .trim()
                .removePrefix("@")
                .lowercase(Locale.US)

            if (!handle.matches(Regex("^[a-z0-9_]{3,20}$"))) {
                toast("Используй 3–20 английских букв, цифр или _")
                return@setOnClickListener
            }

            save.isEnabled = false

            db.child("users").child(uid).child("handle")
                .get()
                .addOnSuccessListener { oldSnapshot ->

                    val oldHandle =
                        oldSnapshot.getValue(String::class.java)

                    if (oldHandle == handle) {
                        save.isEnabled = true
                        toast("Этот username уже установлен")
                        return@addOnSuccessListener
                    }

                    val usernameRef = db.child("usernames").child(handle)

                    usernameRef.runTransaction(
                        object : Transaction.Handler {

                            override fun doTransaction(
                                currentData: MutableData
                            ): Transaction.Result {

                                val existingUid =
                                    currentData.getValue(String::class.java)

                                if (
                                    existingUid != null &&
                                    existingUid != uid
                                ) {
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

                                if (error != null) {
                                    save.isEnabled = true
                                    toast(error.message)
                                    return
                                }

                                if (!committed) {
                                    save.isEnabled = true
                                    toast("Этот username уже занят")
                                    return
                                }

                                db.child("users").child(uid)
                                    .child("handle")
                                    .setValue(handle)
                                    .addOnSuccessListener {

                                        if (
                                            !oldHandle.isNullOrBlank() &&
                                            oldHandle != handle
                                        ) {
                                            db.child("usernames")
                                                .child(oldHandle)
                                                .removeValue()
                                        }

                                        toast("Username сохранён! 💚")
                                        showProfile()
                                    }
                                    .addOnFailureListener {
                                        save.isEnabled = true
                                        toast(
                                            it.message
                                                ?: "Ошибка сохранения username"
                                        )
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

        if (!registerMode) usernameInput.visibility = View.GONE

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

        val actionButton = makeButton(
            if (registerMode) "Зарегистрироваться" else "Войти"
        )

        root.addView(
            actionButton,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        addGap(8)

        val switchButton = Button(this).apply {
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
            switchButton,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        actionButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (
                email.isBlank() ||
                password.isBlank() ||
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

                            db.child("users").child(uid)
                                .setValue(userData)
                                .addOnSuccessListener { showHome() }
                                .addOnFailureListener {
                                    toast(
                                        it.message
                                            ?: "Ошибка сохранения профиля"
                                    )
                                }
                        } else {
                            toast(
                                task.exception?.message ?: "Ошибка регистрации"
                            )
                        }
                    }
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->

                        actionButton.isEnabled = true

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

        switchButton.setOnClickListener {
            showAuth(!registerMode)
        }
    }

    // ГЛАВНЫЙ ЭКРАН

    private fun showHome() {
        clearAllListeners()
        searchQuery = ""
        newRoot()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = makeText(
            "StalkerGram",
            23f,
            Color.rgb(25, 220, 130),
            true
        )

        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))

        val myUid = auth.currentUser?.uid

        if (myUid != null) {
            val myAvatar = makeAvatar("Я")

            header.addView(
                myAvatar,
                LinearLayout.LayoutParams(dp(42), dp(42))
            )

            loadAvatar(myUid, myAvatar)
            myAvatar.setOnClickListener { showProfile() }
        }

        val logout = Button(this).apply {
            text = "Выйти"
            isAllCaps = false
            setTextColor(Color.LTGRAY)
            background = android.graphics.drawable.ColorDrawable(
                Color.TRANSPARENT
            )

            setOnClickListener {
                clearAllListeners()
                auth.signOut()
                showAuth(false)
            }
        }

        header.addView(logout)
        root.addView(header)

        root.addView(
            makeText(
                "Нажми на аватар, чтобы открыть профиль",
                11f,
                Color.GRAY
            )
        )

        addGap(8)

        val search = makeInput("Поиск по имени или @username")
        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)))

        addGap(12)

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val chatsTitle = makeText(
            "МОИ ЧАТЫ", 14f, Color.rgb(25, 220, 130), true
        )

        content.addView(chatsTitle, LinearLayout.LayoutParams(-1, dp(38)))

        val chatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(chatsContainer)

        val usersTitle = makeText(
            "ПОЛЬЗОВАТЕЛИ", 14f, Color.rgb(25, 220, 130), true
        )

        val usersTitleParams = LinearLayout.LayoutParams(-1, dp(42))
        usersTitleParams.topMargin = dp(12)
        content.addView(usersTitle, usersTitleParams)

        val usersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(usersContainer)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                searchQuery = s?.toString().orEmpty()
                renderUsers(usersContainer)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        loadUsers(usersContainer)
        loadChats(chatsContainer)
    }

    // ПОЛЬЗОВАТЕЛИ И ПОИСК

    private fun loadUsers(container: LinearLayout) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("users").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    users.clear()

                    for (child in snapshot.children) {
                        val uid = child.key ?: continue
                        if (uid == myUid) continue

                        val username =
                            child.child("username")
                                .getValue(String::class.java)
                                ?: child.child("email")
                                    .getValue(String::class.java)
                                ?: "Пользователь"

                        val handle =
                            child.child("handle")
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
            container.addView(
                makeText(
                    if (searchQuery.isBlank()) {
                        "Пользователей пока нет"
                    } else {
                        "Ничего не найдено"
                    },
                    14f,
                    Color.GRAY
                )
            )
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

    // СПИСОК ЧАТОВ

    private fun loadChats(container: LinearLayout) {
        chatListListener = ChatListManager.listenChats(
            callback = { previews ->
                container.removeAllViews()

                if (previews.isEmpty()) {
                    container.addView(
                        makeText("Здесь пока нет чатов", 14f, Color.GRAY)
                    )
                    return@listenChats
                }

                previews.forEach { preview ->
                    val time = if (preview.timestamp > 0L) {
                        SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                        ).format(Date(preview.timestamp))
                    } else {
                        ""
                    }

                    val message = preview.lastMessage.ifBlank {
                        "Нет сообщений"
                    }

                    container.addView(
                        makeListItem(
                            preview.uid,
                            preview.username,
                            message,
                            time
                        ) {
                            openChat(preview.uid, preview.username)
                        }
                    )
                }
            },
            onError = { message -> toast(message) }
        )
    }

    // АВАТАРЫ И ЭЛЕМЕНТЫ СПИСКА

    private fun makeAvatar(name: String): TextView {
        val initial = name.trim()
            .firstOrNull()
            ?.toString()
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
        uid: String,
        name: String,
        message: String,
        time: String?,
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

        item.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
        loadAvatar(uid, avatar)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
        }

        textContainer.addView(makeText(name, 16f, Color.WHITE, true))
        textContainer.addView(makeText(message, 13f, Color.LTGRAY))

        item.addView(
            textContainer,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        if (!time.isNullOrBlank()) {
            item.addView(makeText(time, 11f, Color.GRAY))
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(7)
        item.layoutParams = params

        return item
    }

    // ОТКРЫТИЕ ЧАТА

    private fun openChat(otherUid: String, username: String) {
        clearAllListeners()
        activeChatUid = otherUid

        newRoot()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val backButton = Button(this).apply {
            text = "‹"
            textSize = 30f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.ColorDrawable(
                Color.TRANSPARENT
            )
            setOnClickListener { showHome() }
        }

        header.addView(
            backButton,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        val avatar = makeAvatar(username)
        header.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))
        loadAvatar(otherUid, avatar)

        val title = makeText(username, 18f, Color.WHITE, true)
        title.setPadding(dp(10), 0, 0, 0)

        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(header)

        addGap(8)

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
        }

        val messageInput = makeInput("Сообщение")
        messageInput.maxLines = 4
        messageInput.inputType = 1
        messageInput.imeOptions = EditorInfo.IME_ACTION_SEND

        bottom.addView(
            messageInput,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        val sendButton = makeButton("➤")

        val sendParams = LinearLayout.LayoutParams(dp(58), dp(52))
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
                        "Начни переписку 💚",
                        14f,
                        Color.GRAY
                    )
                    empty.gravity = Gravity.CENTER

                    messagesContainer.addView(
                        empty,
                        LinearLayout.LayoutParams(-1, dp(60))
                    )

                    return@listenMessages
                }

                val myUid = auth.currentUser?.uid ?: ""

                val sortedMessages = messages.sortedBy {
                    it.child("timestamp").getValue(Long::class.java) ?: 0L
                }

                sortedMessages.forEach { snapshot ->
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
                            if (isMine) {
                                Color.rgb(20, 145, 90)
                            } else {
                                Color.rgb(36, 46, 59)
                            },
                            14
                        )
                    }

                    bubble.addView(makeText(text, 16f, Color.WHITE))

                    val timeText = makeText(
                        if (timestamp > 0L) {
                            SimpleDateFormat(
                                "HH:mm",
                                Locale.getDefault()
                            ).format(Date(timestamp))
                        } else {
                            ""
                        },
                        10f,
                        Color.LTGRAY
                    )

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

                    val rowParams = LinearLayout.LayoutParams(-1, -2)
                    rowParams.bottomMargin = dp(7)

                    messagesContainer.addView(row, rowParams)
                }

                scroll.post {
                    scroll.fullScroll(View.FOCUS_DOWN)
                }
            },
            onError = { message -> toast(message) }
        )
    }

    override fun onDestroy() {
        clearAllListeners()
        super.onDestroy()
    }
}
