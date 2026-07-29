package com.zhique.studio.data

import android.content.Context
import com.zhique.core.project.ProjectDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Coordinates the durable workspace with a Room metadata index. Files and binary resources stay
 * in project folders; Room supports fast list observation and resilient metadata migration.
 */
class ProjectRepository(context: Context) : AutoCloseable {
    private val store = ProjectStore(context)
    private val database = ProjectMetadataDatabase.open(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val metadata: StateFlow<List<ProjectMetadataEntity>> = database.projects()
        .observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun load(): List<ProjectDocument> = store.load().also(::syncIndex)

    fun save(document: ProjectDocument) {
        store.save(document)
        database.projects().upsert(document.toMetadataEntity())
    }

    fun moveToRecycleBin(projectId: String): RecycledProject = store.moveToRecycleBin(projectId).also {
        database.projects().delete(projectId)
    }

    fun restoreFromRecycleBin(recycleId: String): ProjectDocument = store.restoreFromRecycleBin(recycleId).also {
        database.projects().upsert(it.toMetadataEntity())
    }

    fun loadRecycleBin(): List<RecycledProject> = store.loadRecycleBin()

    fun loadSnapshots(projectId: String): List<ProjectSnapshot> = store.loadSnapshots(projectId)

    fun restoreSnapshot(projectId: String, snapshotId: String): ProjectDocument =
        store.restoreSnapshot(projectId, snapshotId).also { database.projects().upsert(it.toMetadataEntity()) }

    private fun syncIndex(documents: List<ProjectDocument>) {
        database.projects().replaceAll(documents.map(ProjectDocument::toMetadataEntity))
    }

    override fun close() {
        scope.cancel()
        database.close()
    }
}
