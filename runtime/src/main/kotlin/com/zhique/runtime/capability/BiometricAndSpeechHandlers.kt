package com.zhique.runtime.capability

import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeLifecycleHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import com.zhique.runtime.permission.RuntimeUiHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BiometricCapabilityHandler(private val uiHost: RuntimeUiHost) : RuntimeCapabilityHandler {
    override val capabilityId = "biometric"

    override fun supports(method: String) = method == "biometric.authenticate"

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        require(method == "biometric.authenticate") { "Unsupported biometric method: $method" }
        return authenticate(params)
    }

    private suspend fun authenticate(params: Map<String, Any?>): Map<String, String> = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            val executor = ContextCompat.getMainExecutor(uiHost.activity)
            val prompt = BiometricPrompt.Builder(uiHost.activity)
                .setTitle((params["title"] as? String).orEmpty().ifBlank { "确认身份" })
                .setDescription((params["subtitle"] as? String).orEmpty().ifBlank { "使用设备生物识别继续" })
                .setNegativeButton("取消", executor) { _, _ ->
                    if (continuation.isActive) continuation.resumeWithException(cancelledRuntime(capabilityId, "Biometric authentication was cancelled."))
                }
                .build()
            prompt.authenticate(cancellation, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(mapOf("status" to "authenticated"))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resumeWithException(
                        RuntimeCapabilityException(RuntimeBridgeError("BIOMETRIC_ERROR", errString.toString(), capabilityId, recoverable = true, action = "retry"))
                    )
                }
            })
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
    }
}

object SpeechTextPolicy {
    fun requireText(value: String): String = value.trim().also { text ->
        require(text.isNotBlank() && text.length <= 10_000) { "text must contain 1 to 10000 characters." }
    }
}

class SpeechCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler, RuntimeLifecycleHandler {
    override val capabilityId = "speech"
    private val appContext = context.applicationContext
    private var speaker: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    override fun supports(method: String) = method in setOf(
        "speech.recognize",
        "speech.speak",
        "speech.stopSpeaking"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "speech.recognize" -> recognize(params)
        "speech.speak" -> speak(params.requiredSpeechString("text"), params["language"] as? String)
        "speech.stopSpeaking" -> {
            speaker?.stop()
            mapOf("state" to "stopped")
        }
        else -> throw IllegalArgumentException("Unsupported speech method: $method")
    }

    override fun releaseSession(sessionId: String) {
        speaker?.stop()
        speaker?.shutdown()
        speaker = null
        recognizer?.destroy()
        recognizer = null
    }

    private suspend fun recognize(params: Map<String, Any?>): Map<String, String> {
        permissionBroker.ensure(capabilityId)
        return withContext(Dispatchers.Main.immediate) {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                throw RuntimeCapabilityException(RuntimeBridgeError("UNSUPPORTED_DEVICE", "No speech recognition service is available.", capabilityId, recoverable = false))
            }
            suspendCancellableCoroutine { continuation ->
                recognizer?.destroy()
                val next = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer = next
                next.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: android.os.Bundle) {
                        val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (continuation.isActive && !text.isNullOrBlank()) continuation.resume(mapOf("text" to text))
                    }

                    override fun onError(error: Int) {
                        if (continuation.isActive) continuation.resumeWithException(
                            RuntimeCapabilityException(RuntimeBridgeError("SPEECH_ERROR", "Speech recognition failed ($error).", capabilityId, recoverable = true, action = "retry"))
                        )
                    }

                    override fun onReadyForSpeech(params: android.os.Bundle) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: android.os.Bundle) = Unit
                    override fun onEvent(eventType: Int, params: android.os.Bundle) = Unit
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, params["language"] as? String ?: Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                next.startListening(intent)
                continuation.invokeOnCancellation { next.cancel(); next.destroy(); if (recognizer === next) recognizer = null }
            }
        }
    }

    private suspend fun speak(raw: String, language: String?): Map<String, String> = withContext(Dispatchers.Main.immediate) {
        val text = SpeechTextPolicy.requireText(raw)
        val tts = ensureSpeaker()
        language?.let { tag -> tts.language = Locale.forLanguageTag(tag) }
        val utteranceId = "speech_${UUID.randomUUID()}"
        check(tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.ERROR) { "Android could not start text-to-speech." }
        mapOf("state" to "speaking", "utteranceId" to utteranceId)
    }

    private suspend fun ensureSpeaker(): TextToSpeech = speaker ?: suspendCancellableCoroutine { continuation ->
        lateinit var next: TextToSpeech
        next = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speaker = next
                if (continuation.isActive) continuation.resume(next)
            } else if (continuation.isActive) {
                next.shutdown()
                continuation.resumeWithException(IllegalStateException("Android text-to-speech is unavailable."))
            }
        }
        continuation.invokeOnCancellation { next.shutdown() }
    }
}

private fun cancelledRuntime(capability: String, message: String) = RuntimeCapabilityException(
    RuntimeBridgeError("USER_CANCELLED", message, capability, recoverable = true, action = "retry")
)

private fun Map<String, Any?>.requiredSpeechString(key: String): String = this[key] as? String
    ?: throw IllegalArgumentException("$key is required.")
