Fair Weighted Gate
Context
In a Kotlin/Android app, multiple coroutines need access to a limited shared resource, such as upload bandwidth, database write capacity, or image decoding memory.
Each request has a weight. A request may proceed only when enough capacity is available.
Task
Implement:
class WeightedGate(
    capacity: Int
) {
    suspend fun acquire(
        weight: Int
    ): Permit

    interface Permit {
        fun release()
    }
}
Requirements
capacity must be greater than 0.
weight must be greater than 0 and less than or equal to capacity.
The total weight of active permits must never exceed capacity.
If enough capacity is available and no earlier request is waiting, acquire(weight) should complete immediately.
Requests waiting for capacity must be served in arrival order.
A later smaller request must not proceed before an earlier larger request that is still waiting.
Cancelling a caller while it is waiting must not consume capacity.
Cancelling a caller after it has acquired a permit must not automatically release that permit.
Calling release() more than once for the same permit must not corrupt capacity.
acquire and release may be called concurrently from different coroutines.
The implementation must not block threads while waiting for capacity.
Output Format
Return:
Full Kotlin implementation.
Brief explanation of the concurrency behavior.
Important coroutine tests or pseudocode tests.
Test cases
1. Reject invalid capacity
Create gates with invalid capacities.
Assert:
- capacity = 0 throws.
- capacity < 0 throws.
2. Reject invalid weights
Call acquire with invalid weights.
Assert:
- weight = 0 throws.
- weight < 0 throws.
- weight > capacity throws.
3. Capacity is never exceeded
Use capacity 10.
Scenario:
1. acquire(6)
2. acquire(4)
3. start acquire(1)
Assert:
- acquire(6) succeeds.
- acquire(4) succeeds.
- acquire(1) waits until one permit is released.
- active weight never exceeds 10.
4. Immediate acquire when capacity is available
Use capacity 10.
Scenario:
1. acquire(3)
2. acquire(4)
Assert:
- both calls complete immediately.
- active weight is 7.
5. Wait when capacity is insufficient
Use capacity 10.
Scenario:
1. acquire(8)
2. start acquire(3)
3. release the first permit
Assert:
- acquire(3) does not complete while only 2 capacity is free.
- acquire(3) completes after release.
6. Arrival order is respected
Use capacity 10.
Scenario:
1. acquire(10)
2. start waiter A: acquire(4)
3. start waiter B: acquire(4)
4. release the first permit
Assert:
- waiter A completes before waiter B.
7. Smaller request cannot bypass earlier larger request
Use capacity 10.
Scenario:
1. acquire(7)
2. start waiter A: acquire(5)
3. start waiter B: acquire(3)
Assert:
- waiter B does not complete even though 3 capacity is free.
Then:
4. release the first permit
Assert:
- waiter A completes first.
- waiter B may complete after A if remaining capacity allows.
This is the core fairness test.
8. Cancel waiting request frees its position
Use capacity 10.
Scenario:
1. acquire(10)
2. start waiter A: acquire(6)
3. start waiter B: acquire(4)
4. cancel waiter A
5. release the first permit
Assert:
- waiter A does not acquire capacity.
- waiter B completes after capacity is released.
- capacity accounting remains correct.
9. Cancel acquired caller does not auto-release permit
Use capacity 10.
Scenario:
1. coroutine A acquires weight 10.
2. cancel coroutine A without calling release().
3. start coroutine B: acquire(1)
Assert:
- coroutine B remains waiting.
- capacity is released only when A’s permit.release() is called.
This verifies cancellation is not treated as ownership release.
10. Repeated release is safe
Use capacity 10.
Scenario:
1. permit = acquire(10)
2. permit.release()
3. permit.release()
4. acquire(10)
Assert:
- second release does not increase capacity above 10.
- later acquire(10) succeeds exactly once.
- no later acquire can exceed total capacity.
11. Release wakes multiple compatible waiters in order
Use capacity 10.
Scenario:
1. acquire(10)
2. start waiter A: acquire(3)
3. start waiter B: acquire(4)
4. start waiter C: acquire(3)
5. release the first permit
Assert:
- A, B, and C all complete.
- completion order respects arrival order.
- active weight becomes exactly 10.
12. Release wakes only the prefix that fits
Use capacity 10.
Scenario:
1. acquire(10)
2. start waiter A: acquire(6)
3. start waiter B: acquire(5)
4. start waiter C: acquire(4)
5. release the first permit
Assert:
- A completes.
- B remains waiting because only 4 capacity remains.
- C also remains waiting because C cannot bypass B.
13. Concurrent release and cancellation race
Use capacity 10.
Scenario:
1. acquire(10)
2. start waiter A: acquire(6)
3. start waiter B: acquire(4)
4. concurrently cancel A and release the first permit
Assert:
- capacity is not lost.
- capacity is not overcounted.
- B eventually completes if A was cancelled before acquiring.
- if A acquired before cancellation, B waits until A’s permit is released.
This test should allow either valid interleaving, but no corrupted capacity.
14. Different coroutines can release permits
Scenario:
1. coroutine A acquires a permit.
2. coroutine B calls release() on that permit.
Assert:
- release succeeds.
- waiting callers can proceed.
15. Multi-dispatcher stress test
Run many coroutines across different dispatchers. Randomly perform:
- acquire random valid weights
- release permits
- cancel waiting callers
- call release twice on some permits
Assert:
- no deadlocks or hangs.
- active weight never exceeds capacity.
- no request proceeds before an earlier waiting request that could not yet be served.
- all capacity is recoverable after releasing all acquired permits.
