
Step 1: Change the domain

Instead of a simple NoteEntity, use a richer local entity:

data class NoteEntity(
    val id: String,

    // Last published content from server
    val publishedTitle: String,
    val publishedBody: String,

    // Local unpublished draft content
    val draftTitle: String?,
    val draftBody: String?,

    val draftLockId: String?,
    val editSessionId: String?,
    val acknowledgedEditSessionId: String?,

    val dirtyFields: Set<DirtyField>,

    val lastAppliedRemoteRevision: Long,
    val locallyDeleted: Boolean,
    val remoteDeleted: Boolean
)

Now the bug is not simply “dirty local note overwritten.”

It is:

Remote published updates are being merged into draft fields while a draft lock is active.
Step 2: Add small project docs

Add:

docs/note-lifecycle.md

Keep it concise.

It should define rules, but not name bug locations.

Example:

A note may have published content and unpublished draft content.

Published content is the last version acknowledged by the server.

Draft content belongs to a local edit session.

When a draftLockId is active, remote sync may update published metadata,
but it must not replace draftTitle or draftBody.

A draft may be cleared only when its editSessionId is acknowledged by the server
or when the user explicitly discards it.

Remote delete marks remoteDeleted=true.
It must not erase a locally locked draft.

This creates custom semantics.

Step 3: Update the prompt

Use this exact prompt style:

# Android Notes Draft Sync Fix

The repository contains an Android notes application with offline editing and synchronization.

## Problem

Users report that after reconnecting to the network, some notes show the wrong content or lose unpublished draft changes.

The issue appears only in certain combinations of local draft editing, remote updates, deletes, and sync acknowledgements.

Your task is to inspect the repository, understand the note lifecycle and synchronization behavior, identify the root causes, and implement fixes that preserve user intent.

Requirements and expected behavior should be inferred from the repository contents, source code, and lifecycle documentation.

## Task

Analyze the note editing and synchronization system and implement the necessary fixes.

The solution should:

- Preserve unpublished local draft content when appropriate.
- Correctly distinguish published content from draft content.
- Handle remote updates without corrupting active local edit sessions.
- Handle acknowledgements and deletes consistently.
- Preserve the existing architecture and server API.

## Constraints

- Do not bypass synchronization logic.
- Work within the existing architecture.
- Preserve existing functionality where possible.
- Do not introduce new external dependencies.

No mention of hidden tests, serverVersion, push-before-pull, idempotency, or textbook sync terms.

Step 4: Change project files

You need these files:

app/src/main/java/com/example/notes/
├── model/
│   ├── DirtyField.kt
│   ├── Note.kt
│   ├── NoteDto.kt
│   ├── DraftMutation.kt
│   └── MutationType.kt
├── db/
│   ├── NoteEntity.kt
│   ├── PendingMutationEntity.kt
│   ├── NoteDao.kt
│   └── PendingMutationDao.kt
├── api/
│   ├── NotesApi.kt
│   └── FakeNotesApi.kt
├── repo/
│   └── NotesRepository.kt
└── sync/
    ├── SyncManager.kt
    ├── DraftMergePolicy.kt
    ├── MutationQueue.kt
    └── SyncResult.kt

Important new file:

sync/DraftMergePolicy.kt

This replaces generic ConflictResolver.kt.

Step 5: Intentionally buggy behavior

The buggy repository given to the model should contain these mistakes:

Bug 1: Remote update overwrites draft fields

In DraftMergePolicy.kt, buggy logic:

draftTitle = remote.title
draftBody = remote.body

even when draftLockId != null.

Correct behavior:

Remote may update publishedTitle/publishedBody,
but must not overwrite draftTitle/draftBody while draftLockId is active.
Bug 2: Draft cleared on any server ack

In SyncManager.kt, buggy logic:

noteDao.clearDraft(noteId)

after any mutation ack.

Correct behavior:

Clear draft only when acknowledged editSessionId matches current editSessionId.
Bug 3: Remote delete erases locked draft

Buggy logic:

if (remote.deleted) noteDao.delete(remote.id)

Correct behavior:

If a locked draft exists, mark remoteDeleted=true but keep draft content.
Bug 4: Local edit updates published fields directly

In NotesRepository.kt, buggy logic:

publishedTitle = newTitle
publishedBody = newBody

Correct behavior:

Local edit should update draftTitle/draftBody and dirtyFields,
not overwrite published content before server acknowledgement.
Bug 5: Dirty fields ignored

Buggy logic sends the whole note as an update even if only title changed.

Correct behavior:

Mutation should carry only fields changed in this edit session,
or merge should respect dirtyFields.

This forces field-level reasoning.

Step 6: Tests to write

Write tests that check behavior, not implementation.

Test 1: active draft survives remote update

Scenario:

Local note has draftLockId and draftBody.
Remote sends newer published body.

Expected:

publishedBody updates to remote body.
draftBody remains local draft body.

Purpose:

Catches remote overwrite of draft fields.
Test 2: local edit writes draft fields only

Scenario:

User edits title locally.

Expected:

draftTitle changes.
publishedTitle remains unchanged.
dirtyFields contains TITLE.

Purpose:

Prevents generic “update entity directly” logic.
Test 3: ack clears draft only for matching edit session

Scenario:

Current editSessionId = session-B.
Server ack arrives for session-A.

Expected:

Draft is not cleared.
session-B remains active.

Purpose:

Catches stale ack bug.
Test 4: matching ack publishes draft

Scenario:

Current editSessionId = session-A.
Server ack confirms session-A.

Expected:

publishedTitle/body become draftTitle/body.
draft fields cleared.
acknowledgedEditSessionId = session-A.

Purpose:

Tests correct completion path.
Test 5: remote delete does not erase locked draft

Scenario:

Local note has active draft.
Remote says note deleted.

Expected:

remoteDeleted = true.
draft content remains.
note still exists locally.

Purpose:

Catches destructive delete merge.
Test 6: remote delete removes clean note

Scenario:

No draft lock.
Remote says note deleted.

Expected:

note removed or marked remoteDeleted, depending on your chosen architecture.

Purpose:

Ensures delete still works for non-draft notes.
Test 7: field-level merge preserves unrelated local dirty field

Scenario:

Local dirtyFields = BODY.
Remote updates title.

Expected:

publishedTitle updates.
draftBody remains.
dirtyFields still contains BODY.

Purpose:

Forces field-level reasoning.
Test 8: discard draft allows remote overwrite

Scenario:

User discards draft.
Remote update arrives.

Expected:

No draft lock.
Remote published content applies normally.

Purpose:

Prevents overprotecting all local state forever.
Step 7: Make it easier but still non-recall

Instead of telling the model “look at draftLockId,” include comments in the code:

// Non-null while a local unpublished edit session is active.
val draftLockId: String?
// Server acknowledgement for the edit session that was accepted.
val acknowledgedEditSessionId: String?
// Fields changed locally during the current edit session.
val dirtyFields: Set<DirtyField>

These help solve the task without turning it into a memorized sync recipe.

Final recommendation

Do not continue with the generic offline sync benchmark if your evaluator rejects memorized co-occurring textbook fixes.

Use the Draft Lifecycle Sync version.

It is still Android/offline-sync flavored, but the core reasoning is project-specific:

When may remote published content affect local draft content?
When may a draft be cleared?
What does an acknowledgement actually acknowledge?
What happens when delete conflicts with an active draft?
