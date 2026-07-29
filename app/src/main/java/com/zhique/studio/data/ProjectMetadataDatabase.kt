package com.zhique.studio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.zhique.core.project.BuildRecord
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import com.zhique.core.project.PreviewDataPersistence
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "project_metadata")
data class ProjectMetadataEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val packageName: String,
    val packageNameLocked: Boolean,
    val versionName: String,
    val versionCode: Int,
    val lastModifiedEpochMillis: Long,
    val capabilitiesJson: String,
    val promptPackVersion: String,
    val previewDataPersistence: String,
    val iconAssetPath: String?,
    val signingKeyId: String?,
    val signingCertificateSha256: String?,
    val signingBackupId: String?,
    val buildHistoryJson: String
)

@Dao
interface ProjectMetadataDao {
    @Query("SELECT * FROM project_metadata ORDER BY lastModifiedEpochMillis DESC, id ASC")
    fun observeAll(): Flow<List<ProjectMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: ProjectMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<ProjectMetadataEntity>)

    @Query("DELETE FROM project_metadata WHERE id = :projectId")
    fun delete(projectId: String)

    @Query("DELETE FROM project_metadata")
    fun clear()

    @Transaction
    fun replaceAll(entities: List<ProjectMetadataEntity>) {
        clear()
        upsertAll(entities)
    }
}

@Database(entities = [ProjectMetadataEntity::class], version = 1, exportSchema = true)
abstract class ProjectMetadataDatabase : RoomDatabase() {
    abstract fun projects(): ProjectMetadataDao

    companion object {
        fun open(context: Context): ProjectMetadataDatabase = Room.databaseBuilder(
            context.applicationContext,
            ProjectMetadataDatabase::class.java,
            "zhique-project-metadata.db"
        )
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries() // Workspace migration remains synchronous until the repository API is fully suspend-only.
            .build()
    }
}

fun ProjectDocument.toMetadataEntity(): ProjectMetadataEntity = ProjectMetadataEntity(
    id = metadata.id,
    displayName = metadata.displayName,
    packageName = metadata.packageName,
    packageNameLocked = metadata.packageNameLocked,
    versionName = metadata.versionName,
    versionCode = metadata.versionCode,
    lastModifiedEpochMillis = metadata.lastModifiedEpochMillis,
    capabilitiesJson = JSONArray(metadata.capabilities.sorted()).toString(),
    promptPackVersion = metadata.promptPackVersion,
    previewDataPersistence = metadata.previewDataPersistence.name,
    iconAssetPath = metadata.iconAssetPath,
    signingKeyId = metadata.signingKeyId,
    signingCertificateSha256 = metadata.signingCertificateSha256,
    signingBackupId = metadata.signingBackupId,
    buildHistoryJson = buildHistoryJson(buildHistory)
)

fun ProjectMetadataEntity.toMetadata(): ProjectMetadata = ProjectMetadata(
    id = id,
    displayName = displayName,
    packageName = packageName,
    packageNameLocked = packageNameLocked,
    versionName = versionName,
    versionCode = versionCode,
    lastModifiedEpochMillis = lastModifiedEpochMillis,
    capabilities = JSONArray(capabilitiesJson).toStringSet(),
    promptPackVersion = promptPackVersion,
    previewDataPersistence = PreviewDataPersistence.entries.firstOrNull { it.name == previewDataPersistence }
        ?: PreviewDataPersistence.Persistent,
    iconAssetPath = iconAssetPath,
    signingKeyId = signingKeyId,
    signingCertificateSha256 = signingCertificateSha256,
    signingBackupId = signingBackupId
)

private fun buildHistoryJson(records: List<BuildRecord>): String = JSONArray().apply {
    records.forEach { record ->
        put(JSONObject().apply {
            put("versionName", record.versionName)
            put("versionCode", record.versionCode)
            put("createdAt", record.createdAtEpochMillis)
            put("status", record.status)
            put("message", record.message)
            record.artifactFileName?.let { put("artifactFileName", it) }
            record.artifactSha256?.let { put("artifactSha256", it) }
            record.signingKeyId?.let { put("signingKeyId", it) }
        })
    }
}.toString()

private fun JSONArray.toStringSet(): Set<String> = buildSet {
    for (index in 0 until length()) add(getString(index))
}
