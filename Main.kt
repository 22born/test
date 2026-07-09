Causal Mutation Reconciler

Context

In an offline-capable Android/Kotlin app, users can edit local state while disconnected. Each local edit is applied optimistically and later acknowledged or rejected by the server.

Local edits may depend on earlier edits. Server acknowledgements, rejections, and remote snapshots may arrive out of order.

You need a reconciler that keeps the visible state correct while handling optimistic mutations, dependency invalidation, rollback, and remote snapshot reconciliation.

Task

Implement:

class MutationReconciler<K, V> {
    fun applyRemoteSnapshot(
        version: Long,
        values: Map<K, V>
    )

    fun localPut(
        key: K,
        value: V,
        dependsOn: Set<Long> = emptySet()
    ): Long

    fun localDelete(
        key: K,
        dependsOn: Set<Long> = emptySet()
    ): Long

    fun acknowledge(
        mutationId: Long,
        serverVersion: Long
    )

    fun reject(
        mutationId: Long
    )

    fun read(): Map<K, V>
}

"localPut" and "localDelete" return unique local mutation IDs.

Requirements

1. Local mutations must be visible immediately and applied on top of the latest accepted remote snapshot.

2. Remote snapshots have versions and may arrive out of order. A snapshot older than the latest accepted remote version must be ignored.

3. Acknowledgements and rejections may arrive out of order and must be reconciled against the current mutation graph, not just the current visible value.

4. Rejecting a mutation must invalidate that mutation and every local mutation that directly or indirectly depends on it.

5. A mutation must not be treated as durable if any declared dependency has been rejected, even if that mutation was acknowledged earlier.

6. If multiple local mutations affect the same key, the latest valid local mutation wins.

7. If the latest local mutation for a key is rejected, the visible state must fall back to the next-latest valid local mutation for that key, or to the remote snapshot if none remains.

8. Remote snapshots must update the base state underneath valid local mutations without hiding them.

9. A rejected mutation must not be resurrected by later acknowledgements or stale snapshots.

10. Reads after completed operations must return a consistent visible state that reflects local-over-remote precedence, dependency invalidation, and accepted remote version ordering.

11. The reconciler must behave correctly when local mutations, remote snapshots, acknowledgements, rejections, and reads are called concurrently.

The returned read state must be a safe snapshot and must not expose mutable internal state.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of optimistic state, dependency handling, acknowledgement/rejection behavior, and remote snapshot reconciliation.
3. Important tests or pseudocode tests.






Crucial test cases
Out-of-order acknowledgement followed by dependency rejection
Create mutation m1, then mutation m2 depending on m1, then mutation m3 depending on m2. Acknowledge m2 before m1, then reject m1. Verify m1, m2, and m3 are all invalidated, even though m2 was acknowledged earlier.
Acknowledged mutation is invalidated if dependency later rejects
Create m1, create m2 depending on m1, acknowledge m2, then reject m1. Verify m2 is not treated as durable and its visible effect disappears.
Reject latest mutation falls back to previous valid mutation on same key
Start with remote A = remote. Apply m1: A = local-1, then m2: A = local-2. Reject m2. Verify visible A becomes local-1, not remote.
Reject chain only removes dependent branch, not unrelated mutations
Create m1, then m2 depending on m1, and also create unrelated m3. Reject m1. Verify m1 and m2 are invalidated, but m3 remains visible.
Remote snapshot updates underneath pending local mutation
Start with remote A = remote-1. Apply local A = local. Then apply newer remote snapshot A = remote-2. Verify visible A remains local. After rejecting the local mutation, verify visible A becomes remote-2.
Remote snapshot must not resurrect rejected mutation
Create a local mutation, reject it, then apply an older remote snapshot or acknowledge the rejected mutation later. Verify the rejected mutation does not become visible again.
Out-of-order remote snapshots preserve latest accepted base
Apply remote snapshot version 10, then version 12, then version 11. Verify version 11 is ignored and reads reflect version 12 plus any valid local overlay.
Delete mutation fallback behavior
Start with remote A = remote. Apply m1: put A = local, then m2: delete A. Reject m2. Verify A falls back to local. Then reject m1 and verify A falls back to remote.
Rejected dependency blocks later acknowledgement
Create m1, create m2 depending on m1, reject m1, then acknowledge m2. Verify m2 remains invalid and never becomes visible or durable.
Remote snapshot with deleted key under local edit
Start with remote containing A. Apply local A = local. Then apply a newer remote snapshot that omits A. Verify visible A remains local. After rejecting the local mutation, verify A disappears.
Concurrent local edits to same key produce deterministic visible winner
Perform many concurrent local edits to the same key. Verify mutation IDs are unique, the visible value corresponds to one valid latest mutation, and rejecting that mutation falls back to the next-latest valid mutation.
Rollback/reject racing with newer local edit
Create m1: A = one. Race reject(m1) with localPut(A, two). Verify no final state incorrectly removes the newer mutation if it was created after or independently of the rejected mutation.
Read never exposes partially reconciled dependency state
While one thread rejects a root mutation with many transitive dependents, other threads call read(). Verify reads never show a state where a dependent mutation remains visible while its rejected dependency is already gone.
Read never exposes mixed remote snapshot state
Apply large remote snapshots concurrently with reads. Verify each read returns a complete consistent base snapshot plus valid local overlay, never a mixture of two remote versions.
Mixed out-of-order stress test
Randomly interleave local puts, local deletes, dependency chains, acknowledgements, rejections, remote snapshots, and reads across threads. Verify invariants: rejected mutations and their dependents stay invalid, latest valid local mutation wins per key, stale snapshots are ignored, and reads are consistent.
