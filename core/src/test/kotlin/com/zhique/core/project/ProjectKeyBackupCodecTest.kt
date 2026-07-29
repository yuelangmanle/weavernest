package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ProjectKeyBackupCodecTest {
    @Test
    fun `password encrypted signing material restores the same project identity`() {
        val material = ProjectSigningMaterial(
            projectId = "project-42",
            keyId = "key-42",
            privateKeyEncoded = ByteArray(64) { it.toByte() },
            certificateEncoded = ByteArray(48) { (it + 64).toByte() }
        )

        val restored = ProjectKeyBackupCodec.decrypt(
            ProjectKeyBackupCodec.encrypt(material, "correct horse battery staple"),
            "correct horse battery staple"
        )

        assertEquals(material.identity, restored.identity)
        assertEquals(material.projectId, restored.projectId)
        assertContentEquals(material.privateKeyEncoded(), restored.privateKeyEncoded())
        assertContentEquals(material.certificateEncoded(), restored.certificateEncoded())
    }

    @Test
    fun `wrong password cannot restore signing material`() {
        val material = ProjectSigningMaterial(
            projectId = "project-42",
            keyId = "key-42",
            privateKeyEncoded = byteArrayOf(1, 2, 3),
            certificateEncoded = byteArrayOf(4, 5, 6)
        )
        val encrypted = ProjectKeyBackupCodec.encrypt(material, "correct horse battery staple")

        assertFailsWith<IllegalArgumentException> {
            ProjectKeyBackupCodec.decrypt(encrypted, "incorrect password")
        }
    }

    @Test
    fun `signing material diagnostic string redacts private and certificate bytes`() {
        val material = ProjectSigningMaterial(
            projectId = "project-42",
            keyId = "key-42",
            privateKeyEncoded = "private-secret".encodeToByteArray(),
            certificateEncoded = "certificate-secret".encodeToByteArray()
        )

        val diagnostic = material.toString()

        assertFalse(diagnostic.contains("private-secret"))
        assertFalse(diagnostic.contains("certificate-secret"))
    }
}
