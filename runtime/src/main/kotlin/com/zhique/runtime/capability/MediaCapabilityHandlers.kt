package com.zhique.runtime.capability

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeLifecycleHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.RuntimeUiHost
import com.zhique.runtime.permission.VisualMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MediaSourcePolicy {
    fun requirePlayable(raw: String): URI {
        val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("source must be a valid URI.") }
        require(uri.scheme in setOf("content", "android.resource", "file", "https")) {
            "Audio sources must use content, android.resource, file, or HTTPS URIs."
        }
        return uri
    }
}

class ImageMediaCapabilityHandler(private val uiHost: RuntimeUiHost) : RuntimeCapabilityHandler {
    override val capabilityId = "media_images"

    override fun supports(method: String) = method == "media.pickImages"

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        require(method == "media.pickImages") { "Unsupported image media method: $method" }
        val uris = uiHost.pickVisualMedia(VisualMediaType.Images, params["multiple"] as? Boolean ?: true)
        if (uris.isEmpty()) throw cancelled(capabilityId, "No images were selected.")
        return mapOf("uris" to uris.map(Uri::toString))
    }
}

class VideoMediaCapabilityHandler(private val uiHost: RuntimeUiHost) : RuntimeCapabilityHandler {
    override val capabilityId = "media_video"

    override fun supports(method: String) = method == "media.pickVideo"

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        require(method == "media.pickVideo") { "Unsupported video media method: $method" }
        val uri = uiHost.pickVisualMedia(VisualMediaType.Video, multiple = false).firstOrNull()
            ?: throw cancelled(capabilityId, "No video was selected.")
        return mapOf("uri" to uri.toString())
    }
}

class AudioMediaCapabilityHandler(
    context: Context,
    private val uiHost: RuntimeUiHost
) : RuntimeCapabilityHandler, RuntimeLifecycleHandler {
    override val capabilityId = "media_audio"
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var state = "stopped"

    override fun supports(method: String) = method in setOf(
        "media.pickAudio",
        "audio.play",
        "audio.pause",
        "audio.stop",
        "audio.state"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "media.pickAudio" -> {
            val uri = uiHost.openDocument(arrayOf("audio/*")) ?: throw cancelled(capabilityId, "No audio file was selected.")
            mapOf("uri" to uri.toString())
        }

        "audio.play" -> play(params.requiredMediaString("source"))
        "audio.pause" -> pause()
        "audio.stop" -> stop()
        "audio.state" -> mapOf("state" to state)
        else -> throw IllegalArgumentException("Unsupported audio media method: $method")
    }

    override fun releaseSession(sessionId: String) {
        release()
    }

    private suspend fun play(source: String): Map<String, String> = withContext(Dispatchers.Main.immediate) {
        val sourceUri = MediaSourcePolicy.requirePlayable(source)
        suspendCancellableCoroutine { continuation ->
            release()
            val next = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.start()
                    state = "playing"
                    if (continuation.isActive) continuation.resume(mapOf("state" to state))
                }
                setOnCompletionListener { state = "stopped" }
                setOnErrorListener { _, _, _ ->
                    state = "error"
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Android could not play this audio source."))
                    true
                }
            }
            player = next
            runCatching {
                if (sourceUri.scheme == "content" || sourceUri.scheme == "android.resource") {
                    next.setDataSource(appContext, Uri.parse(sourceUri.toString()))
                } else {
                    next.setDataSource(sourceUri.toString())
                }
                next.prepareAsync()
            }.onFailure { error ->
                state = "error"
                release()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            continuation.invokeOnCancellation { if (player === next) release() }
        }
    }

    private fun pause(): Map<String, String> {
        player?.takeIf(MediaPlayer::isPlaying)?.pause()
        if (player != null) state = "paused"
        return mapOf("state" to state)
    }

    private fun stop(): Map<String, String> {
        release()
        return mapOf("state" to state)
    }

    private fun release() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        state = "stopped"
    }
}

private fun cancelled(capability: String, message: String) = RuntimeCapabilityException(
    RuntimeBridgeError("USER_CANCELLED", message, capability, recoverable = true, action = "retry")
)

private fun Map<String, Any?>.requiredMediaString(key: String): String = this[key] as? String
    ?: throw IllegalArgumentException("$key is required.")
