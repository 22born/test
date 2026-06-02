The correct fix should make these changes.

Correct files to fix
sync/SyncManager.kt
sync/ConflictResolver.kt
api/FakeNotesApi.kt
repo/NotesRepository.kt
1. SyncManager.kt fix

Correct behavior:

Push pending local mutations first.
Only remove mutation after server ack.
Only mark note clean if no other pending mutation exists.
Then pull remote notes.
On failure, preserve pending mutation.

Core fixed logic:

for (mutation in mutationQueue.pending()) {
    mutationQueue.recordAttempt(mutation.mutationId)

    val ack = api.applyMutation(mutation)

    if (ack.accepted) {
        mutationQueue.remove(mutation.mutationId)

        if (!pendingMutationDao.hasPendingForNote(ack.noteId)) {
            noteDao.markClean(ack.noteId, ack.serverVersion)
        }

        pushed++
    }
}

for (remote in api.fetchAllNotes()) {
    val local = noteDao.get(remote.id)
    val merged = conflictResolver.merge(local, remote)

    if (merged != null) {
        noteDao.upsert(merged)
        pulled++
    }
}
2. ConflictResolver.kt fix

Correct behavior:

Never overwrite dirty local notes.
Never overwrite notes with pending mutations.
Do not rely only on updatedAt.
Use serverVersion / lastSyncedVersion.
Preserve local deletes against stale remote updates.

Core fixed logic:

class ConflictResolver(
    private val pendingMutationDao: PendingMutationDao
) {
    fun merge(local: NoteEntity?, remote: NoteDto): NoteEntity? {
        if (local == null) {
            return NoteEntity.fromDto(remote)
        }

        if (local.dirty || pendingMutationDao.hasPendingForNote(local.id)) {
            return local
        }

        if (local.deleted && remote.serverVersion <= local.lastSyncedVersion) {
            return local
        }

        return if (remote.serverVersion > local.lastSyncedVersion) {
            NoteEntity.fromDto(remote)
        } else {
            local
        }
    }
}
3. FakeNotesApi.kt fix

Correct behavior:

Mutation retries must be idempotent.
The same mutationId should not apply duplicate effects.

Add:

private val appliedMutations = mutableSetOf<String>()

Then:

if (appliedMutations.contains(mutation.mutationId)) {
    val existing = notes[mutation.noteId]
    return MutationAck(
        mutationId = mutation.mutationId,
        noteId = mutation.noteId,
        serverVersion = existing?.serverVersion ?: versionCounter,
        accepted = true
    )
}

appliedMutations.add(mutation.mutationId)
4. NotesRepository.kt fix

Correct behavior:

Every local create/update/delete must mark the note dirty.
Deletes should be tombstones, not hard deletes.
Preserve lastSyncedVersion when editing.

Fix update:

val updated = NoteEntity(
    id = id,
    title = title,
    body = body,
    updatedAt = now,
    deleted = false,
    dirty = true,
    lastSyncedVersion = current?.lastSyncedVersion ?: 0L
)

Fix delete:

val deleted = current.copy(
    deleted = true,
    dirty = true,
    updatedAt = now
)

noteDao.upsert(deleted)
mutationQueue.enqueueDelete(deleted, now)
Test cases to write
1. Offline edit is preserved

Scenario:

Local note is edited offline.
Remote note has newer updatedAt.
Sync runs.

Expected:

Local edited title/body remains.
Remote does not overwrite dirty local note.

Purpose:

Catches timestamp-only conflict resolution bug.
2. Push happens before pull

Scenario:

Local mutation is pending.
Remote has stale version.
Sync runs.

Expected:

Pending mutation is pushed before remote merge.
Local state is not reverted.

Purpose:

Catches wrong sync order.
3. Mutation is not removed before ack

Scenario:

Pending mutation exists.
API fails during applyMutation.
Sync returns failure.

Expected:

Pending mutation still exists.
Local note remains dirty.

Purpose:

Catches data loss during partial failure.
4. Retry is idempotent

Scenario:

Same mutation is applied twice.

Expected:

Server does not create duplicate effects.
Server version does not advance incorrectly for duplicate retry.

Purpose:

Catches missing mutationId deduplication.
5. Multiple pending mutations

Scenario:

Same note has two pending updates.
First mutation succeeds.
Second remains pending.

Expected:

Note is not marked clean until all mutations for that note are acknowledged.

Purpose:

Catches premature markClean bug.
6. Delete wins over stale update

Scenario:

Local note is deleted offline.
Remote still has older active version.
Sync runs.

Expected:

Local tombstone remains.
Remote stale update does not resurrect note.

Purpose:

Catches hard-delete and stale-remote overwrite bugs.
7. Clean note accepts newer remote version

Scenario:

Local note is clean.
Remote serverVersion is newer.
Sync runs.

Expected:

Local note updates to remote version.

Purpose:

Ensures fix does not block legitimate remote updates.
8. Older remote does not overwrite clean local

Scenario:

Local lastSyncedVersion is newer.
Remote serverVersion is older.
Sync runs.

Expected:

Local note remains unchanged.

Purpose:

Ensures conflict resolver uses serverVersion correctly.

These tests force the model to fix the actual sync protocol rather than patching one obvious line.
