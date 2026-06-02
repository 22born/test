model/MutationType.kt
package com.example.notes.model

enum class MutationType {
    CREATE,
    UPDATE,
    DELETE
}
model/Note.kt
package com.example.notes.model

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val deleted: Boolean = false
)
model/NoteDto.kt
package com.example.notes.model

data class NoteDto(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val serverVersion: Long = 0L
)
db/NoteEntity.kt
package com.example.notes.db

import com.example.notes.model.Note
import com.example.notes.model.NoteDto

data class NoteEntity(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    val lastSyncedVersion: Long = 0L
) {
    fun toNote(): Note =
        Note(id, title, body, updatedAt, deleted)

    fun toDto(): NoteDto =
        NoteDto(
            id = id,
            title = title,
            body = body,
            updatedAt = updatedAt,
            deleted = deleted,
            serverVersion = lastSyncedVersion
        )

    companion object {
        fun fromDto(dto: NoteDto): NoteEntity =
            NoteEntity(
                id = dto.id,
                title = dto.title,
                body = dto.body,
                updatedAt = dto.updatedAt,
                deleted = dto.deleted,
                dirty = false,
                lastSyncedVersion = dto.serverVersion
            )
    }
}
db/PendingMutationEntity.kt
package com.example.notes.db

import com.example.notes.model.MutationType

data class PendingMutationEntity(
    val mutationId: String,
    val noteId: String,
    val type: MutationType,
    val createdAt: Long,
    val baseServerVersion: Long,
    val title: String? = null,
    val body: String? = null,
    val deleted: Boolean = false,
    val attemptCount: Int = 0
)
db/NoteDao.kt
package com.example.notes.db

class NoteDao {
    private val notes = linkedMapOf<String, NoteEntity>()

    fun get(id: String): NoteEntity? =
        notes[id]

    fun getAll(): List<NoteEntity> =
        notes.values.toList()

    fun upsert(note: NoteEntity) {
        notes[note.id] = note
    }

    fun delete(id: String) {
        notes.remove(id)
    }

    fun markClean(noteId: String, serverVersion: Long) {
        val note = notes[noteId] ?: return
        notes[noteId] = note.copy(
            dirty = false,
            lastSyncedVersion = serverVersion
        )
    }

    fun clear() {
        notes.clear()
    }
}
db/PendingMutationDao.kt
package com.example.notes.db

class PendingMutationDao {
    private val mutations = linkedMapOf<String, PendingMutationEntity>()

    fun getAll(): List<PendingMutationEntity> =
        mutations.values.sortedBy { it.createdAt }

    fun getForNote(noteId: String): List<PendingMutationEntity> =
        mutations.values
            .filter { it.noteId == noteId }
            .sortedBy { it.createdAt }

    fun insert(mutation: PendingMutationEntity) {
        mutations[mutation.mutationId] = mutation
    }

    fun remove(mutationId: String) {
        mutations.remove(mutationId)
    }

    fun incrementAttempt(mutationId: String) {
        val mutation = mutations[mutationId] ?: return
        mutations[mutationId] =
            mutation.copy(attemptCount = mutation.attemptCount + 1)
    }

    fun hasPendingForNote(noteId: String): Boolean =
        mutations.values.any { it.noteId == noteId }

    fun clear() {
        mutations.clear()
    }
}
api/NotesApi.kt
package com.example.notes.api

import com.example.notes.db.PendingMutationEntity

interface NotesApi {
    fun fetchAllNotes(): List<com.example.notes.model.NoteDto>

    fun applyMutation(mutation: PendingMutationEntity): MutationAck
}

data class MutationAck(
    val mutationId: String,
    val noteId: String,
    val serverVersion: Long,
    val accepted: Boolean
)
api/FakeNotesApi.kt
package com.example.notes.api

import com.example.notes.db.PendingMutationEntity
import com.example.notes.model.MutationType
import com.example.notes.model.NoteDto

class FakeNotesApi : NotesApi {
    private val notes = linkedMapOf<String, NoteDto>()
    private var versionCounter = 1L

    var failNextMutation: Boolean = false

    override fun fetchAllNotes(): List<NoteDto> =
        notes.values.toList()

    override fun applyMutation(mutation: PendingMutationEntity): MutationAck {
        if (failNextMutation) {
            failNextMutation = false
            error("Simulated network failure")
        }

        val newVersion = versionCounter++

        when (mutation.type) {
            MutationType.CREATE,
            MutationType.UPDATE -> {
                notes[mutation.noteId] = NoteDto(
                    id = mutation.noteId,
                    title = mutation.title.orEmpty(),
                    body = mutation.body.orEmpty(),
                    updatedAt = mutation.createdAt,
                    deleted = false,
                    serverVersion = newVersion
                )
            }

            MutationType.DELETE -> {
                val existing = notes[mutation.noteId]
                notes[mutation.noteId] = NoteDto(
                    id = mutation.noteId,
                    title = existing?.title.orEmpty(),
                    body = existing?.body.orEmpty(),
                    updatedAt = mutation.createdAt,
                    deleted = true,
                    serverVersion = newVersion
                )
            }
        }

        return MutationAck(
            mutationId = mutation.mutationId,
            noteId = mutation.noteId,
            serverVersion = newVersion,
            accepted = true
        )
    }

    fun seed(note: NoteDto) {
        notes[note.id] = note
        versionCounter = maxOf(versionCounter, note.serverVersion + 1)
    }

    fun getRemote(id: String): NoteDto? =
        notes[id]
}
repo/NotesRepository.kt
package com.example.notes.repo

import com.example.notes.db.NoteDao
import com.example.notes.db.NoteEntity
import com.example.notes.sync.MutationQueue

class NotesRepository(
    private val noteDao: NoteDao,
    private val mutationQueue: MutationQueue
) {
    fun createNote(id: String, title: String, body: String, now: Long) {
        val note = NoteEntity(
            id = id,
            title = title,
            body = body,
            updatedAt = now,
            deleted = false,
            dirty = true
        )

        noteDao.upsert(note)
        mutationQueue.enqueueCreate(note, now)
    }

    fun updateNote(id: String, title: String, body: String, now: Long) {
        val current = noteDao.get(id)

        val updated = NoteEntity(
            id = id,
            title = title,
            body = body,
            updatedAt = now,
            deleted = false,
            dirty = false,
            lastSyncedVersion = current?.lastSyncedVersion ?: 0L
        )

        noteDao.upsert(updated)
        mutationQueue.enqueueUpdate(updated, now)
    }

    fun deleteNote(id: String, now: Long) {
        val current = noteDao.get(id) ?: return

        noteDao.delete(id)

        mutationQueue.enqueueDelete(current, now)
    }

    fun getNote(id: String): NoteEntity? =
        noteDao.get(id)

    fun getAllNotes(): List<NoteEntity> =
        noteDao.getAll()
}
sync/SyncResult.kt
package com.example.notes.sync

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val failed: Boolean = false,
    val errorMessage: String? = null
)
sync/MutationQueue.kt
package com.example.notes.sync

import com.example.notes.db.NoteEntity
import com.example.notes.db.PendingMutationDao
import com.example.notes.db.PendingMutationEntity
import com.example.notes.model.MutationType
import java.util.UUID

class MutationQueue(
    private val pendingMutationDao: PendingMutationDao
) {
    fun enqueueCreate(note: NoteEntity, now: Long) {
        enqueue(note, MutationType.CREATE, now)
    }

    fun enqueueUpdate(note: NoteEntity, now: Long) {
        enqueue(note, MutationType.UPDATE, now)
    }

    fun enqueueDelete(note: NoteEntity, now: Long) {
        enqueue(note.copy(deleted = true), MutationType.DELETE, now)
    }

    fun pending(): List<PendingMutationEntity> =
        pendingMutationDao.getAll()

    fun remove(mutationId: String) {
        pendingMutationDao.remove(mutationId)
    }

    fun recordAttempt(mutationId: String) {
        pendingMutationDao.incrementAttempt(mutationId)
    }

    private fun enqueue(note: NoteEntity, type: MutationType, now: Long) {
        pendingMutationDao.insert(
            PendingMutationEntity(
                mutationId = UUID.randomUUID().toString(),
                noteId = note.id,
                type = type,
                createdAt = now,
                baseServerVersion = note.lastSyncedVersion,
                title = note.title,
                body = note.body,
                deleted = note.deleted
            )
        )
    }
}
sync/ConflictResolver.kt
package com.example.notes.sync

import com.example.notes.db.NoteEntity
import com.example.notes.model.NoteDto

class ConflictResolver {
    fun merge(local: NoteEntity?, remote: NoteDto): NoteEntity {
        if (local == null) {
            return NoteEntity.fromDto(remote)
        }

        return if (remote.updatedAt > local.updatedAt) {
            NoteEntity.fromDto(remote)
        } else {
            local
        }
    }
}
sync/SyncManager.kt
package com.example.notes.sync

import com.example.notes.api.NotesApi
import com.example.notes.db.NoteDao
import com.example.notes.db.PendingMutationDao

class SyncManager(
    private val noteDao: NoteDao,
    private val pendingMutationDao: PendingMutationDao,
    private val api: NotesApi
) {
    private val mutationQueue = MutationQueue(pendingMutationDao)
    private val conflictResolver = ConflictResolver()

    fun sync(): SyncResult {
        var pushed = 0
        var pulled = 0

        try {
            for (remote in api.fetchAllNotes()) {
                val local = noteDao.get(remote.id)
                val merged = conflictResolver.merge(local, remote)
                noteDao.upsert(merged)
                pulled++
            }

            for (mutation in mutationQueue.pending()) {
                mutationQueue.remove(mutation.mutationId)

                val ack = api.applyMutation(mutation)

                if (ack.accepted) {
                    noteDao.markClean(
                        noteId = ack.noteId,
                        serverVersion = ack.serverVersion
                    )
                    pushed++
                }
            }

            return SyncResult(
                pushed = pushed,
                pulled = pulled
            )
        } catch (t: Throwable) {
            return SyncResult(
                pushed = pushed,
                pulled = pulled,
                failed = true,
                errorMessage = t.message
            )
        }
    }
}
