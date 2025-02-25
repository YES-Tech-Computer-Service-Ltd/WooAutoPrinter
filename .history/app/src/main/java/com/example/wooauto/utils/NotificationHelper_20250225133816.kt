package com.example.wooauto.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.wooauto.MainActivity
import com.example.wooauto.R
import com.example.wooauto.data.api.models.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class NotificationHelper(private val context: Context) {

    private val TAG = "NotificationHelper"
    private val CHANNEL_ID = "new_orders_channel"
    private val NOTIFICATION_ID = 1000

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefsManager = PreferencesManager(context)
    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    init {
        createNotificationChannel()
        initTextToSpeech()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.new_order_received)
            val descriptionText = "WooAuto new order notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)

                // Set sound from preferences
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set language to English by default
                textToSpeech?.language = Locale.US

                // Set language based on preferences
                CoroutineScope(Dispatchers.Main).launch {
                    val language = prefsManager.language.first()
                    when (language) {
                        "zh" -> textToSpeech?.language = Locale.CHINESE
                        else -> textToSpeech?.language = Locale.US
                    }
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    fun showNewOrderNotification(order: Order) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("orderId", order.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_order)
            .setContentTitle(context.getString(R.string.new_order_received))
            .setContentText("${context.getString(R.string.order_number, order.number)} - ${order.billing.getFullName()}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Show the notification
        notificationManager.notify(NOTIFICATION_ID + order.id.toInt(), builder.build())

        // Play sound alert
        CoroutineScope(Dispatchers.IO).launch {
            playOrderAlert(order)
        }
    }

    private suspend fun playOrderAlert(order: Order) {
        try {
            // Get sound settings
            val soundType = prefsManager.soundType.first()
            val playCount = prefsManager.playCount.first()
            val volume = prefsManager.soundVolume.first()

            for (i in 1..playCount) {
                when (soundType) {
                    "system_sound" -> {
                        playSystemSound()
                    }
                    "custom_sound" -> {
                        val customSoundPath = prefsManager.customSoundPath.first()
                        if (customSoundPath.isNotEmpty()) {
                            playCustomSound(customSoundPath, volume)
                        } else {
                            playSystemSound()
                        }
                    }
                    "custom_text" -> {
                        val customText = prefsManager.customText.first().ifEmpty {
                            context.getString(R.string.new_order_received)
                        }
                        val voiceType = prefsManager.voiceType.first()
                        speakText(customText, voiceType, volume)
                    }
                }

                // Wait before next playback
                if (i < playCount) {
                    delay(2000) // 2 seconds pause between plays
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing order alert", e)
        }
    }

    private fun playSystemSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notification)
            ringtone.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing system sound", e)
        }
    }

    private fun playCustomSound(path: String, volume: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(path))
                setVolume(volume / 100f, volume / 100f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing custom sound", e)
            // Fallback to system sound
            playSystemSound()
        }
    }

    private fun speakText(text: String, voiceType: String, volume: Int) {
        try {
            textToSpeech?.let { tts ->
                // Set pitch based on voice type
                when (voiceType) {
                    "male" -> tts.setPitch(0.8f)
                    "female" -> tts.setPitch(1.2f)
                    else -> tts.setPitch(1.0f)
                }

                // Set volume
                tts.setSpeechRate(0.9f)

                // Speak the text
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orderAlert")
                } else {
                    @Suppress("DEPRECATION")
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
        }
    }

    fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}