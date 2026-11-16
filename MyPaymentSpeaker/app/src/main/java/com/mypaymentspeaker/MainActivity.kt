package com.mypaymentspeaker

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private lateinit var language: Spinner
    private lateinit var enabled: CheckBox
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 42, 36, 24)
            gravity = Gravity.TOP
        }
        layout.addView(TextView(this).apply {
            text = "My Payment Speaker"
            textSize = 26f
            setTextColor(0xFF1565C0.toInt())
        })
        layout.addView(TextView(this).apply {
            text = "Google Pay, Paytm, PhonePe ଓ ଅନ୍ୟ UPI notification ପାଇଁ voice alert"
            textSize = 16f
            setPadding(0, 18, 0, 18)
        })

        status = TextView(this).apply { textSize = 15f }
        layout.addView(status)

        enabled = CheckBox(this).apply {
            text = "Payment voice alert ON"
            isChecked = prefs().getBoolean("enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs().edit().putBoolean("enabled", checked).apply()
            }
        }
        layout.addView(enabled)

        layout.addView(TextView(this).apply {
            text = "Voice language"
            textSize = 16f
            setPadding(0, 16, 0, 6)
        })
        language = Spinner(this)
        val languages = listOf("Odia", "Hindi", "English")
        language.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        language.setSelection(prefs().getInt("language", 1).coerceIn(0, 2))
        language.onItemSelectedListener = SimpleItemSelectedListener { position ->
            prefs().edit().putInt("language", position).apply()
        }
        layout.addView(language)

        addButton(layout, "Enable Notification Access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        addButton(layout, "Allow Notifications") {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            } else {
                Toast.makeText(this, "Notification permission already allowed", Toast.LENGTH_SHORT).show()
            }
        }
        addButton(layout, "Test Voice") {
            speak("Payment alert test. My Payment Speaker is ready.")
        }
        addButton(layout, "Open App Battery Settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
        layout.addView(TextView(this).apply {
            text = "\nଟିପ୍ପଣୀ: Notification ରେ ଥିବା ତଥ୍ୟ ମାତ୍ର ପଢ଼ାଯିବ। Bank credit ନିଶ୍ଚିତ କରିବା ପାଇଁ bank app/statement ଯାଞ୍ଚ କରନ୍ତୁ।"
            textSize = 13f
        })
        setContentView(ScrollView(this).apply { addView(layout) })
        updateStatus()
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }

    private fun prefs() = getSharedPreferences("settings", MODE_PRIVATE)

    private fun updateStatus() {
        val nm = getSystemService(NotificationManager::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        val access = enabledListeners.contains(packageName)
        status.text = "Notification Access: ${if (access) "Enabled" else "Not enabled"}\n" +
            "Voice engine: ${if (ttsReady) "Ready" else "Loading"}"
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) updateStatus() }

    override fun onInit(result: Int) {
        ttsReady = result == TextToSpeech.SUCCESS
        if (ttsReady) {
            val locale = when (prefs().getInt("language", 1)) {
                0 -> Locale("or", "IN")
                2 -> Locale("en", "IN")
                else -> Locale("hi", "IN")
            }
            val availability = tts.setLanguage(locale)
            if (availability == TextToSpeech.LANG_MISSING_DATA ||
                availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale("en", "IN"))
                Toast.makeText(this, "Selected language voice unavailable; using English fallback", Toast.LENGTH_LONG).show()
            }
            // Android TTS engines do not expose a reliable universal female-voice flag.
            // The installed engine's available voice may be female or male.
        }
        if (::status.isInitialized) updateStatus()
    }

    private fun speak(message: String) {
        if (ttsReady) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "test_voice")
        else Toast.makeText(this, "Voice engine is not ready", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}
