Monotonic Snapshot Store

Context

In a Kotlin/Android app, a local store holds records that can be updated by remote server snapshots, remote server deltas, local optimistic edits, and rollback events. These operations may happen concurrently and may arrive out of order.

The store must provide consistent immutable snapshots to readers while preventing stale remote updates and stale rollback events from overwriting newer state.

Task

Implement:

class SnapshotStore<K, V> {
    fun applySnapshot(
        version: Long,
        values: Map<K, V>
    )

    fun applyDelta(
        version: Long,
        changes: Map<K, V?>
    )

    fun localEdit(
        key: K,
        value: V
    ): Long

    fun rollback(
        editId: Long
    )

    fun read(): Map<K, V>
}

"changes" uses "null" to mean deletion.

Requirements

1. "read()" must return a consistent immutable snapshot.
2. Remote updates have server versions. Remote updates may arrive out of order.
3. A remote update with "version <= latest accepted remote version" must be ignored.
4. "applySnapshot(version, values)" must replace the entire remote state if its version is newer than the latest accepted remote version.
5. "applyDelta(version, changes)" must apply sparse changes to the latest accepted remote state if its version is newer than the latest accepted remote version.
6. Local edits must be visible immediately after "localEdit" returns.
7. "localEdit" must return a unique edit id.
8. Local edits form an overlay on top of remote state.
9. If a key has an active local edit, reads must show the local value for that key instead of the remote value.
10. Remote snapshots, remote deltas, and remote deletions must not hide active local edits.
11. A new "localEdit" for a key supersedes any previous local edit for that same key.
12. "rollback(editId)" must undo only that edit if it is still the active local edit for its key.
13. Rolling back an older edit must not remove or alter a newer local edit for the same key.
14. Rolling back the active local edit for a key must reveal the current remote value for that key, or remove the key from reads if the key is absent remotely.
15. Once an operation has completed, later reads must not observe a state older than that completed operation allows.
16. All methods may be called concurrently from different threads.
17. Returned snapshots must not expose mutable internal state.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of remote version handling, local edit precedence, rollback behavior, and snapshot consistency.
3. Important tests or pseudocode tests.





10 high-value tests:
Newer delta beats older snapshot
Apply a higher-version delta, then a lower-version full snapshot. Verify the older snapshot is ignored and cannot restore stale data.
Snapshot replacement preserves active local overlay
Apply a snapshot, make a local edit, then apply a newer snapshot that omits or changes that key. Verify the local edit remains visible while the remote layer is replaced underneath.
Remote delete under local edit, then rollback
Locally edit a key, remotely delete that key, then roll back the active edit. Verify the local value remains visible before rollback, and the key disappears after rollback.
Remote state evolves under local edit
Keep a local edit active while multiple newer remote deltas/snapshots change the same key. Verify the local value stays visible, and after rollback the latest accepted remote value appears.
Rollback of older edit does not remove newer edit
Make two local edits to the same key. Roll back the older edit. Verify the newer edit remains active and visible.
Rollback racing with newer local edit
Race rollback(oldEditId) against a new localEdit for the same key. Verify rollback cannot remove or corrupt the newer edit.
Concurrent remote updates respect monotonic versioning
Apply snapshots and deltas concurrently with different versions. Verify the accepted remote version never moves backward and the final state is consistent with one valid serialized order.
Read never exposes partial snapshot during full replacement
While one thread repeatedly applies large snapshots, other threads read. Verify every read is a complete consistent state, never a mixture of two snapshots.
Read-after-completed-operation boundary
After each completed operation, immediately read. Verify the read reflects all completed operations according to precedence: active local edit over latest accepted remote state.
Mixed-operation stress test
Randomly interleave snapshots, deltas, local edits, rollbacks, and reads across threads. Verify no torn reads, no stale remote overwrite, no rollback of superseded edits, and no mutable internal state exposure.
