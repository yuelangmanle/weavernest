package com.zhique.core.project

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Non-secret signing identity that may safely be stored in project metadata and build history. */
data class ProjectSigningIdentity(
    val keyId: String,
    val certificateSha256: String
)

/**
 * In-memory signing material. It is intentionally not a data class so Kotlin does not generate a
 * diagnostic string containing byte arrays. Callers must persist it through an encrypted store.
 */
class ProjectSigningMaterial(
    val projectId: String,
    val keyId: String,
    privateKeyEncoded: ByteArray,
    certificateEncoded: ByteArray
) {
    private val privateKeyBytes = privateKeyEncoded.copyOf()
    private val certificateBytes = certificateEncoded.copyOf()

    init {
        require(projectId.isNotBlank()) { "Project id is required." }
        require(keyId.isNotBlank()) { "Signing key id is required." }
        require(privateKeyBytes.isNotEmpty()) { "Private key material is required." }
        require(certificateBytes.isNotEmpty()) { "Certificate material is required." }
    }

    val identity: ProjectSigningIdentity = ProjectSigningIdentity(
        keyId = keyId,
        certificateSha256 = sha256(certificateBytes)
    )

    fun privateKeyEncoded(): ByteArray = privateKeyBytes.copyOf()

    fun certificateEncoded(): ByteArray = certificateBytes.copyOf()

    override fun toString(): String = "ProjectSigningMaterial(projectId=$projectId, keyId=$keyId, material=redacted)"
}

/** Password-encrypted interchange format for a single project's exportable signing identity. */
object ProjectKeyBackupCodec {
    private const val formatVersion = 1
    private const val iterations = 210_000
    private const val keyBits = 256
    private const val tagBits = 128
    private const val maxMaterialBytes = 256 * 1024
    private val magic = byteArrayOf(0x5a, 0x51, 0x4b, 0x31) // ZQK1
    private val random = SecureRandom()

    fun encrypt(material: ProjectSigningMaterial, backupPassword: String): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(backupPassword, salt)
        val encrypted = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(tagBits, iv))
            }.doFinal(encodeMaterial(material))
        } finally {
            key.encoded?.fill(0)
        }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(magic)
                output.writeInt(formatVersion)
                output.writeInt(salt.size)
                output.write(salt)
                output.writeInt(iv.size)
                output.write(iv)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
            bytes.toByteArray()
        }
    }

    fun requireValidBackupPassword(password: String) {
        require(password.length >= 12) { "Backup password must contain at least 12 characters." }
    }

    fun decrypt(encryptedBackup: ByteArray, backupPassword: String): ProjectSigningMaterial = try {
        DataInputStream(ByteArrayInputStream(encryptedBackup)).use { input ->
            require(input.readBytesExact(magic.size).contentEquals(magic)) { "This is not a Zhique signing-key backup." }
            require(input.readInt() == formatVersion) { "Unsupported signing-key backup version." }
            val salt = input.readBoundedBytes(64)
            val iv = input.readBoundedBytes(32)
            val encrypted = input.readBoundedBytes(maxMaterialBytes + 64 * 1024)
            require(input.available() == 0) { "Signing-key backup contains trailing data." }
            val key = deriveKey(backupPassword, salt)
            val materialBytes = try {
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(tagBits, iv))
                }.doFinal(encrypted)
            } finally {
                key.encoded?.fill(0)
            }
            decodeMaterial(materialBytes)
        }
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Unable to decrypt the signing-key backup. Check the backup password.", error)
    }

    private fun encodeMaterial(material: ProjectSigningMaterial): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(material.projectId)
            output.writeUTF(material.keyId)
            output.writeInt(material.privateKeyEncoded().size)
            output.write(material.privateKeyEncoded())
            output.writeInt(material.certificateEncoded().size)
            output.write(material.certificateEncoded())
        }
        bytes.toByteArray()
    }

    private fun decodeMaterial(bytes: ByteArray): ProjectSigningMaterial = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val projectId = input.readUTF().take(256)
        val keyId = input.readUTF().take(256)
        val privateKey = input.readBoundedBytes(maxMaterialBytes)
        val certificate = input.readBoundedBytes(maxMaterialBytes)
        require(input.available() == 0) { "Signing-key material contains trailing data." }
        ProjectSigningMaterial(projectId, keyId, privateKey, certificate)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        requireValidBackupPassword(password)
        val characters = password.toCharArray()
        val raw = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(characters, salt, iterations, keyBits))
                .encoded
        } finally {
            characters.fill('\u0000')
        }
        return try {
            SecretKeySpec(raw, "AES")
        } finally {
            raw.fill(0)
        }
    }

    private fun DataInputStream.readBoundedBytes(maximum: Int): ByteArray {
        val size = readInt()
        require(size in 1..maximum) { "Invalid signing-key backup field length." }
        return readBytesExact(size)
    }

    private fun DataInputStream.readBytesExact(size: Int): ByteArray = ByteArray(size).also(::readFully)
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
