package id.peritanix.dape.android.core.hardware

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Tahap 6: Perintah Suara Offline (Hands-Free Field Control).
 *
 * Allows hands-free operation in the field when farmer's hands are soiled with mud,
 * fertilizers, or chemical pesticides. Supports offline Indonesian commands:
 *   - "Ambil Foto" -> Trigger camera / burst
 *   - "Mulai Hitung" -> Start 180s thermotherapy timer
 *   - "Bacakan Dosis" -> Speak fertilizer / spray recipe via TTS
 *   - "Lanjut Bedengan" -> Save current record and advance to next bed
 */
class OfflineVoiceController(private val context: Context) {

    enum class VoiceCommand(val triggerPhrase: String, val description: String) {
        CAPTURE_PHOTO("ambil foto", "Menembakkan kamera 12-frame burst / Stokes"),
        START_TIMER("mulai hitung", "Memulai timer 180 detik Asas Black"),
        SPEAK_DOSAGE("bacakan dosis", "Memicu TTS membacakan resep pupuk / fungisida"),
        NEXT_BED("lanjut bedengan", "Menyimpan data dan berpindah ke titik berikutnya")
    }

    var isListening: Boolean = false
        private set

    var onCommandRecognized: ((VoiceCommand) -> Unit)? = null
    var onSpeechStatusChanged: ((String) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onSpeechStatusChanged?.invoke("Pengenal suara offline tidak tersedia di perangkat ini.")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onSpeechStatusChanged?.invoke("Mendengarkan perintah suara... (Katakan: Ambil Foto / Mulai Hitung / Bacakan Dosis)")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                    onSpeechStatusChanged?.invoke("Suara belum terdengar jelas. Coba katakan lagi.")
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { spokenText ->
                        evaluateSpokenText(spokenText)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            initialize()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onSpeechStatusChanged?.invoke("Gagal mengaktifkan pendengar suara: ${e.message}")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    fun evaluateSpokenText(spokenText: String) {
        val lower = spokenText.lowercase(Locale.ROOT)
        val matchedCommand = when {
            lower.contains("foto") || lower.contains("ambil") || lower.contains("jepret") -> VoiceCommand.CAPTURE_PHOTO
            lower.contains("hitung") || lower.contains("mulai") || lower.contains("timer") -> VoiceCommand.START_TIMER
            lower.contains("dosis") || lower.contains("baca") || lower.contains("resep") -> VoiceCommand.SPEAK_DOSAGE
            lower.contains("lanjut") || lower.contains("bedeng") || lower.contains("berikut") -> VoiceCommand.NEXT_BED
            else -> null
        }

        if (matchedCommand != null) {
            onSpeechStatusChanged?.invoke("Perintah dikenali: '${matchedCommand.triggerPhrase}' (${matchedCommand.description})")
            onCommandRecognized?.invoke(matchedCommand)
        } else {
            onSpeechStatusChanged?.invoke("Perintah '$spokenText' belum terdaftar. Contoh: 'Ambil Foto', 'Mulai Hitung', 'Bacakan Dosis'.")
        }
    }
}
