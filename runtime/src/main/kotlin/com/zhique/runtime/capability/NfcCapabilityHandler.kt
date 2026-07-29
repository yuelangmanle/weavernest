package com.zhique.runtime.capability

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Base64
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.RuntimeUiHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NfcTagPolicy {
    fun hex(bytes: ByteArray): String = bytes.joinToString(separator = "") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}

class NfcCapabilityHandler(
    context: Context,
    private val uiHost: RuntimeUiHost
) : RuntimeCapabilityHandler {
    override val capabilityId = "nfc"
    private val adapter = NfcAdapter.getDefaultAdapter(context.applicationContext)

    override fun supports(method: String) = method in setOf("nfc.isAvailable", "nfc.read", "nfc.write")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "nfc.isAvailable" -> mapOf("available" to (adapter != null), "enabled" to (adapter?.isEnabled == true))
        "nfc.read" -> read()
        "nfc.write" -> write(params)
        else -> throw IllegalArgumentException("Unsupported NFC method: $method")
    }

    private suspend fun read(): Map<String, Any?> {
        val tag = awaitTag()
        val ndef = Ndef.get(tag)
        val message = ndef?.let { technology ->
            technology.connect()
            try { technology.ndefMessage ?: technology.cachedNdefMessage } finally { technology.close() }
        }
        return mapOf(
            "id" to NfcTagPolicy.hex(tag.id),
            "technologies" to tag.techList.toList(),
            "records" to message?.records.orEmpty().map { record ->
                mapOf(
                    "tnf" to record.tnf.toInt(),
                    "type" to Base64.encodeToString(record.type, Base64.NO_WRAP),
                    "payload" to Base64.encodeToString(record.payload, Base64.NO_WRAP)
                )
            }
        )
    }

    private suspend fun write(params: Map<String, Any?>): Map<String, Any?> {
        val message = params["message"]
        val text = when (message) {
            is String -> message
            is Map<*, *> -> message["text"] as? String
            else -> params["text"] as? String
        }?.trim().orEmpty()
        require(text.isNotBlank() && text.length <= 4_000) { "message.text must contain 1 to 4000 characters." }
        val ndefMessage = NdefMessage(arrayOf(NdefRecord.createTextRecord("", text)))
        val tag = awaitTag()
        Ndef.get(tag)?.let { technology ->
            technology.connect()
            try {
                require(technology.isWritable) { "This NFC tag is read-only." }
                require(technology.maxSize >= ndefMessage.toByteArray().size) { "This NFC tag is too small." }
                technology.writeNdefMessage(ndefMessage)
            } finally {
                technology.close()
            }
        } ?: NdefFormatable.get(tag)?.let { formatable ->
            formatable.connect()
            try { formatable.format(ndefMessage) } finally { formatable.close() }
        } ?: throw IllegalStateException("This NFC tag does not support NDEF messages.")
        return mapOf("id" to NfcTagPolicy.hex(tag.id), "written" to true)
    }

    private suspend fun awaitTag(): Tag = withContext(Dispatchers.Main.immediate) {
        val nfc = adapter ?: throw RuntimeCapabilityException(
            RuntimeBridgeError("UNSUPPORTED_DEVICE", "This device has no NFC adapter.", capabilityId, recoverable = false)
        )
        if (!nfc.isEnabled) throw RuntimeCapabilityException(
            RuntimeBridgeError("SPECIAL_FLOW_REQUIRED", "NFC is turned off.", capabilityId, recoverable = true, action = "open_settings")
        )
        suspendCancellableCoroutine { continuation ->
            nfc.enableReaderMode(
                uiHost.activity,
                { tag -> uiHost.activity.runOnUiThread { if (continuation.isActive) continuation.resume(tag) } },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
            continuation.invokeOnCancellation { nfc.disableReaderMode(uiHost.activity) }
        }.also { nfc.disableReaderMode(uiHost.activity) }
    }
}
