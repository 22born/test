Pay particular attention to how the system handles pending mutations, retries, conflict resolution, and synchronization ordering.



NoteDto.kt
// Monotonic version assigned by the server.
// This represents server-side ordering of remote changes.
val serverVersion: Long = 0L
PendingMutationEntity.kt
// Stable client-generated identifier for this local change.
// It is preserved across retries.
val mutationId: String
NoteEntity.kt
// True when local changes exist that have not been acknowledged by the server.
val dirty: Boolean = false
NoteDao.kt
// Marks a note as synchronized with the server.
fun markClean(noteId: String, serverVersion: Long)
