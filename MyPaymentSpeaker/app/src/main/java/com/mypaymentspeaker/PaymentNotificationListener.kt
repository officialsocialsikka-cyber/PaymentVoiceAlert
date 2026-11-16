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
    @Volatile private var ready = false
    private val seen = ConcurrentHashMap.newKeySet<String>()

    private val paymentPackages = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "com.phonepe.app" to "PhonePe",
        "in.org.npci.upiapp" to "BHIM"
    )

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return

        val appName = paymentPackages[sbn.packageName] ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() }.orEmpty()

        val content = listOf(title, text, bigText, lines)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (content.isBlank()) return

        val event = classifyNotification(content) ?: return
        val amount = extractAmount(content) ?: return

        // Ignore ongoing notifications and reduce duplicate announcements.
        val key = "${sbn.packageName}:${content.hashCode()}:${event}"
        if (!seen.add(key)) return
        if (seen.size > 500) seen.clear()

        val language = prefs.getInt("language", 1)
        val mode = prefs.getInt("voice_mode", 1) // 0=Short, 1=Normal, 2=Detailed
        val sender = extractSender(content, title, amount)
        val time = extractTime(content)
        val transactionId = extractTransactionId(content)

        val spoken = buildAnnouncement(
            appName = appName,
            event = event,
            amount = amount,
            sender = sender,
            time = time,
            transactionId = transactionId,
            language = language,
            mode = mode
        )

        if (ready) {
            tts?.speak(spoken, TextToSpeech.QUEUE_ADD, null, key)
        }
    }

    private enum class Event { RECEIVED, REFUND, FAILED }

    private fun classifyNotification(content: String): Event? {
        val s = content.lowercase(Locale.ROOT)

        val failedWords = listOf(
            "payment failed", "transaction failed", "failed transaction",
            "भुगतान विफल", "पेमेंट फेल", "ବିଫଳ", "ପେମେଣ୍ଟ ଫେଲ"
        )
        val refundWords = listOf(
            "refund", "refunded", "refund processed", "रिफंड", "वापस",
            "ରିଫଣ୍ଡ", "ଫେରସ୍ତ"
        )
        val receivedWords = listOf(
            "received", "credited", "payment successful", "money received",
            "paid you", "sent you", "ପାଇଛନ୍ତି", "ଜମା", "ପ୍ରାପ୍ତ",
            "मिले", "प्राप्त", "जमा", "आपको भेजे"
        )

        return when {
            failedWords.any { s.contains(it) } -> Event.FAILED
            refundWords.any { s.contains(it) } -> Event.REFUND
            receivedWords.any { s.contains(it) } -> Event.RECEIVED
            else -> null
        }
    }

    private fun extractAmount(content: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:₹|Rs\\.?\\s*|INR\\s*)([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:rupees|rs\\.?|ଟଙ୍କା|रुपये)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) return matcher.group(1)?.replace(",", "")
        }
        return null
    }

    private fun extractSender(content: String, title: String, amount: String): String? {
        val cleaned = content
            .replace(Regex("(?:₹|Rs\\.?\\s*|INR\\s*)[0-9,]+(?:\\.[0-9]{1,2})?", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[0-9,]+(?:\\.[0-9]{1,2})?\\s*(?:rupees|rs\\.?|ଟଙ୍କା|रुपये)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b(?:UPI|UTR|RRN|Txn|Transaction\\s*ID)[:#\\s-]*[A-Za-z0-9-]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Prefer an explicit "from" / "by" phrase; do not guess a sender from arbitrary text.
        val patterns = listOf(
            Regex("(?:from|by|\\bसे|द्वारा|ଙ୍କଠାରୁ)\\s+([A-Za-z][A-Za-z .'-]{1,35})", RegexOption.IGNORE_CASE),
            Regex("([A-Za-z][A-Za-z .'-]{1,35})\\s+(?:sent you|paid you)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(cleaned)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()?.trim('.', ',', ':', '-', ' ')
            if (!candidate.isNullOrBlank() && !candidate.equals(title, true) &&
                !candidate.equals("payment", true) && !candidate.equals("money", true)) {
                return candidate
            }
        }
        return null
    }

    private fun extractTime(content: String): String? {
        val match = Regex("\\b(?:at\\s*)?([0-1]?[0-9]):[0-5][0-9]\\s*(?:AM|PM)?\\b", RegexOption.IGNORE_CASE)
            .find(content)
        return match?.value?.trim()
    }

    private fun extractTransactionId(content: String): String? {
        val patterns = listOf(
            Regex("(?:transaction\\s*id|txn\\s*id|upi\\s*ref(?:erence)?|utr|rrn)\\s*[:#-]?\\s*([A-Za-z0-9]{8,})", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { it.find(content)?.groupValues?.getOrNull(1) }
    }

    private fun buildAnnouncement(
        appName: String,
        event: Event,
        amount: String,
        sender: String?,
        time: String?,
        transactionId: String?,
        language: Int,
        mode: Int
    ): String {
        val detailed = mode == 2
        val normal = mode == 1
        return when (language) {
            0 -> { // Odia
                when (event) {
                    Event.RECEIVED -> buildString {
                        append("$appName ରେ $amount ଟଙ୍କା ପେମେଣ୍ଟ ଆସିଛି।")
                        if ((normal || detailed) && sender != null) append(" $sender ଙ୍କଠାରୁ।")
                        if (detailed && time != null) append(" ସମୟ $time।")
                        if (detailed && transactionId != null) append(" ଟ୍ରାଞ୍ଜାକ୍ସନ ଆଇଡି $transactionId।")
                    }
                    Event.REFUND -> "$appName ରେ $amount ଟଙ୍କା ରିଫଣ୍ଡ ନୋଟିଫିକେସନ ଆସିଛି।"
                    Event.FAILED -> "$appName ରେ $amount ଟଙ୍କା ପେମେଣ୍ଟ ବିଫଳ ହୋଇଛି।"
                }
            }
            2 -> { // English
                when (event) {
                    Event.RECEIVED -> buildString {
                        append("$appName. $amount rupees received.")
                        if ((normal || detailed) && sender != null) append(" From $sender.")
                        if (detailed && time != null) append(" Time $time.")
                        if (detailed && transactionId != null) append(" Transaction ID $transactionId.")
                    }
                    Event.REFUND -> "$appName. Refund notification for $amount rupees."
                    Event.FAILED -> "$appName. Payment of $amount rupees failed."
                }
            }
            else -> { // Hindi
                when (event) {
                    Event.RECEIVED -> buildString {
                        append("$appName में $amount रुपये प्राप्त हुए हैं।")
                        if ((normal || detailed) && sender != null) append(" $sender से।")
                        if (detailed && time != null) append(" समय $time।")
                        if (detailed && transactionId != null) append(" ट्रांजैक्शन आईडी $transactionId।")
                    }
                    Event.REFUND -> "$appName में $amount रुपये का रिफंड नोटिफिकेशन आया है।"
                    Event.FAILED -> "$appName में $amount रुपये का पेमेंट असफल हुआ है।"
                }
            }
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val language = getSharedPreferences("settings", MODE_PRIVATE).getInt("language", 1)
        val locale = when (language) {
            0 -> Locale("or", "IN")
            2 -> Locale("en", "IN")
            else -> Locale("hi", "IN")
        }
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale("en", "IN"))
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        super.onDestroy()
    }
}
