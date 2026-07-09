Cancellable Write Coalescer

Context

In a Kotlin/Android app, many coroutines request writes to the same expensive destination, such as disk, SQLite, DataStore, or a sync queue.

Writing every update immediately is inefficient. You need a small utility that coalesces rapid updates while preserving correctness for callers waiting for durability.

Task

Implement:

class WriteCoalescer<T>(
    private val scope: CoroutineScope,
    private val write: suspend (T) -> Unit
) {
    suspend fun submit(value: T)

    suspend fun flush()

    suspend fun close()
}

Requirements

1. "submit(value)" records the latest requested value.
2. Rapid submissions may be coalesced so that not every submitted value is written.
3. The final submitted value before a flush or close must not be lost.
4. "submit(value)" must return only after that value has either been written or superseded by a later submitted value.
5. "flush()" must return only after all values submitted before the flush call have either been written or superseded by a later submitted value, and the latest non-superseded value has been written.
6. "close()" must write the latest pending value, wait for any in-progress write needed for correctness, then reject future submissions.
7. If "write(value)" fails, callers waiting for that value’s durability must receive the failure.
8. A failed write must not permanently poison the coalescer; a later submit may still be written successfully.
9. Cancelling one caller waiting in "submit" or "flush" must not cancel the underlying write needed by other callers.
10. Concurrent "submit", "flush", and "close" calls must behave deterministically.
11. The implementation must not block threads while waiting for writes.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of coalescing, supersession, flush, close, failure, and cancellation behavior.
3. Important coroutine tests or pseudocode tests.




Cancellable Write Coalescer.
Rapid submissions coalesce to latest value
Submit A, B, and C rapidly while writes are delayed. Verify the implementation is allowed to skip A and B, but C must eventually be written.
Earlier submit completes only after safe supersession
Submit A, then submit B. Hold the writer. Verify submit(A) does not complete merely because B was submitted; it completes only when B is successfully written or otherwise safely satisfies the supersession rule.
Flush writes latest value before flush boundary
Submit A, then B, then call flush(). Verify flush() returns only after the latest value before the flush, B, has been written.
Submit after flush starts does not satisfy that flush
Submit A, start flush(), then submit B while the flush is waiting. Verify the flush cannot return just because B exists; it must ensure the pre-flush state is handled correctly according to the flush boundary.
Submit during in-progress write schedules another write
Start writing A. While A is still being written, submit B. Verify B is not lost and is written after A if B remains the latest pending value.
Write failure propagates to affected waiters
Submit A, call flush(), then make write(A) fail. Verify the submit(A) caller and the flush() caller that depended on A receive the failure.
Failure does not poison later submissions
Make write(A) fail. Then submit B and make write(B) succeed. Verify B is written successfully and future flushes can complete.
Superseded value failure does not incorrectly fail newer value
Submit A, then submit B. If the implementation already started writing A and A fails, verify B can still be written and callers waiting for B are not failed by A’s failure.
Close writes final pending value
Submit A, then B, then call close(). Verify close() does not return until the final pending value B is written.
Close rejects future submissions
Call close(), then call submit(C). Verify the post-close submission fails deterministically and does not start a write.
Close while write is in progress preserves final value
Start writing A. While A is in progress, submit B, then call close(). Verify close() waits until B is written, not just until A finishes.
Concurrent flush calls share the same durability boundary safely
Submit A, then start multiple concurrent flush() calls. Verify all flushes return only after A is written, and the write is not duplicated unnecessarily.
Flush racing with submit has deterministic boundary behavior
Race submit(B) with flush() after A was submitted. Verify each valid interleaving produces correct behavior: if B is accepted before the flush boundary, flush waits for B; otherwise it waits only for the prior latest value.
Caller cancellation does not cancel required write
Submit A, start flush(), then cancel the flush caller while write(A) is running. Verify the write continues if needed by other callers or pending state.
Cancelled submit waiter does not corrupt completion of other waiters
Start two callers whose completion depends on the same latest value. Cancel one waiter. Verify the other waiter still completes correctly when the value is written.
High-frequency submissions while writer is slow
Submit many values while each write is slow. Verify the implementation does not write every intermediate value unnecessarily, but the final value is written and all superseded submit callers complete correctly.
Repeated failure and recovery cycle
Alternate failing and succeeding writes across several submissions. Verify each failure is delivered only to affected waiters, later submissions still work, and no stale failure is reused.
Close racing with submit
Race close() with submit(X). Verify the result is consistent with one valid ordering: either submit(X) is accepted and close() must account for it, or submit(X) is rejected because close won.
Flush after failed write retries latest pending value when appropriate
Submit A, write fails, then call flush() again without a newer submission if the value is still pending by the chosen semantics. Verify the behavior is deterministic: either it retries A, or A is considered failed and a new submit is required. The prompt should specify which behavior is expected.
Stress test with submit, flush, close, cancellation, and failures
Randomly interleave submissions, flushes, close attempts, caller cancellations, and writer failures. Verify no hangs, no lost final value, no completion before write/supersession, no post-close writes from rejected submissions, and no failure delivered to unrelated later values.
