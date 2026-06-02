# Android Offline Sync Fix

The repository contains an Android offline-first notes application with a synchronization system.

## Problem

Users report that notes edited offline sometimes disappear, revert unexpectedly, or become inconsistent after reconnecting to the network.

The synchronization system is not behaving correctly under certain conditions, leading to data loss, stale state, and inconsistent results between local and remote data.

Your task is to inspect the repository, understand the intended synchronization behavior, identify the root causes of the issues, and implement fixes that make the synchronization system reliable.

## Task

Analyze the synchronization system and implement the necessary fixes.

The solution should:

- Preserve the offline-first architecture.
- Maintain eventual consistency between local and remote state.
- Handle synchronization failures safely.
- Correctly process local and remote changes.
- Preserve user data and intent during synchronization.

## Constraints

- Do not bypass synchronization logic.
- Work within the existing architecture.
- Preserve existing functionality where possible.
- Maintain compatibility with the existing server API.
- Do not introduce new external dependencies.

## Expected Behavior

After the fix:

1. Offline edits are preserved and synchronize correctly when connectivity is restored.
2. Local changes are not incorrectly overwritten during synchronization.
3. Retries do not create inconsistent or duplicate results.
4. Synchronization remains reliable across transient failures.
5. Concurrent modifications are handled consistently.
6. User data is not lost during synchronization.





environment/
└── app/src/main/java/com/example/notes/
    ├── model/
    │   ├── Note.kt
    │   ├── NoteDto.kt
    │   └── MutationType.kt
    │
    ├── db/
    │   ├── NoteEntity.kt
    │   ├── PendingMutationEntity.kt
    │   ├── NoteDao.kt
    │   └── PendingMutationDao.kt
    │
    ├── api/
    │   ├── NotesApi.kt
    │   └── FakeNotesApi.kt
    │
    ├── repo/
    │   └── NotesRepository.kt
    │
    └── sync/
        ├── SyncManager.kt
        ├── ConflictResolver.kt
        ├── MutationQueue.kt
        └── SyncResult.kt


