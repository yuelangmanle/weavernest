package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RuntimeProjectDataBackupCodecTest {
    @Test
    fun passwordEncryptedRuntimeDataRestoresValuesAndFilesWithoutConfigurationSecrets() {
        val backup = RuntimeProjectDataBackup(
            projectId = "project-data-42",
            createdAtEpochMillis = 1234L,
            values = mapOf("form" to "{\"title\":\"织雀\"}"),
            files = mapOf("notes/today.txt" to "hello".encodeToByteArray())
        )

        val restored = RuntimeProjectDataBackupCodec.decrypt(
            RuntimeProjectDataBackupCodec.encrypt(backup, "correct horse battery staple"),
            "correct horse battery staple"
        )

        assertEquals(backup.projectId, restored.projectId)
        assertEquals(backup.values(), restored.values())
        assertContentEquals(backup.files().getValue("notes/today.txt"), restored.files().getValue("notes/today.txt"))
        assertFalse(restored.toString().contains("hello"))
    }

    @Test
    fun dataBackupRejectsWrongPasswordsAndUnsafePaths() {
        val backup = RuntimeProjectDataBackup("project-data-42", 1234L, emptyMap(), emptyMap())
        val encrypted = RuntimeProjectDataBackupCodec.encrypt(backup, "correct horse battery staple")

        assertFailsWith<IllegalArgumentException> {
            RuntimeProjectDataBackupCodec.decrypt(encrypted, "wrong password 123")
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeProjectDataBackup("project-data-42", 1234L, emptyMap(), mapOf("../escape.txt" to byteArrayOf(1)))
        }
    }
}
