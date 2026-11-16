package com.mypaymentspeaker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class PaymentNotificationListener : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val seen = ConcurrentHashMap.newKeySet<String>()

    // Package IDs vary by region/app version. Add/remove packages in this list as needed.
    private val paymentPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "net.one97.paytm",                       // Paytm
        "com.phonepe.app",                       // PhonePe
        "in.org.npci.upiapp"                     // BHIM
    )

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enabled", true)) return
        if (sbn.packageName !in paymentPackages) return

        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() }.orEmpty()
        val content = listOf(title, text, big, lines).filter { it.isNotBlank() }.joinToString(" ")
        if (content.isBlank() || !looksLikePayment(content)) return

        val key = "${sbn.packageName}:${sbn.key}:${content.hashCode()}"
        if (!seen.add(key)) return
        if (seen.size > 300) seen.clear()

        val amount = extractAmount(content) ?: return
        val lang = getSharedPreferences("settings", MODE_PRIVATE).getInt("language", 1)
        val spoken = buildAnnouncement(amount, title, lang)
        if (ready) tts?.speak(spoken, TextToSpeech.QUEUE_ADD, null, key)
    }

    private fun looksLikePayment(s: String): Boolean {
        val x = s.lowercase(Locale.ROOT)
        val positive = listOf("received", "credited", "payment received", "money received",
            "ପାଇଛନ୍ତି", "ଜମା", "ପ୍ରାପ୍ତ", "पाए", "प्राप्त", "जमा", "credited to")
        val negative = listOf("request", "collect", "pending", "failed", "reminder",
            "अनुरोध", "मांगा", "ଅନୁରୋଧ")
        return positive.any { x.contains(it) } && negative.none { x.contains(it) }
    }

    private fun extractAmount(s: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:₹|Rs\\.?\\s*|INR\\s*)([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:rupees|rs\\.?|ଟଙ୍କା)", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(s)
            if (m.find()) return m.group(1)?.replace(",", "")
        }
        return null
    }

    private fun buildAnnouncement(amount: String, title: String, lang: Int): String {
        return when (lang) {
            0 -> "ଆପଣଙ୍କୁ $amount ଟଙ୍କା ପେମେଣ୍ଟ ଆସିଛି।"
            2 -> "Payment received. $amount rupees received."
            else -> "आपको $amount रुपये प्राप्त हुए हैं।"
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val lang = getSharedPreferences("settings", MODE_PRIVATE).getInt("language", 1)
            val locale = when (lang) {
                0 -> Locale("or", "IN")
                2 -> Locale("en", "IN")
                else -> Locale("hi", "IN")
            }
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("en", "IN"))
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
