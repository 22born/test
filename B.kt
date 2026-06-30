Context

A basketball analytics platform processes live player-tracking events from multiple cameras.

Each event belongs to a defensive possession and updates a shared Defensive Coverage Index, or "DCI".

Each possession tracks:

- the defenders who have participated,
- the cumulative pressure,
- the number of accepted rotations,
- the latest timestamp accepted,
- the event IDs that have already been processed.

Events may be processed concurrently by multiple worker threads.

For each possession:

1. A duplicate event ID must not be applied more than once.
2. A stale event must be ignored. An event is stale if its "timestamp" is less than or equal to the latest accepted timestamp for that possession.
3. An accepted event must update all possession fields atomically.
4. A snapshot must be computed from a consistent view of the possession.
5. Reads may happen concurrently with writes.
6. The implementation should avoid blocking unrelated possessions with one global lock.

The "DCI" for a possession is computed as:

DCI = (totalPressure / acceptedRotations) * numberOfDefenders

If "acceptedRotations == 0", the "DCI" is "0.0".

Recent production simulations show inconsistent results under heavy load. Some snapshots show rotation counts lower than expected, stale events sometimes appear to overwrite newer state, and defenders are occasionally missing from snapshots.

The following Kotlin implementation is currently used in "/app/Main.kt".

---

Starter Code

import java.util.concurrent.ConcurrentHashMap

data class TrackingEvent(
    val possessionId: String,
    val eventId: String,
    val defender: String,
    val pressure: Int,
    val timestamp: Long
)

data class CoverageSnapshot(
    val defenders: Set<String>,
    val totalPressure: Long,
    val acceptedRotations: Int,
    val lastTimestamp: Long,
    val dci: Double
)

data class PossessionState(
    val defenders: MutableSet<String> = mutableSetOf(),
    val processedEventIds: MutableSet<String> = mutableSetOf(),
    var totalPressure: Long = 0,
    var acceptedRotations: Int = 0,
    var lastTimestamp: Long = Long.MIN_VALUE
)

class DefensiveCoverageTracker {

    private val states =
        ConcurrentHashMap<String, PossessionState>()

    fun processEvent(event: TrackingEvent) {
        val state = states.computeIfAbsent(event.possessionId) {
            PossessionState()
        }

        if (event.eventId in state.processedEventIds) {
            return
        }

        if (event.timestamp <= state.lastTimestamp) {
            return
        }

        state.processedEventIds.add(event.eventId)
        state.lastTimestamp = event.timestamp
        state.defenders.add(event.defender)
        state.totalPressure += event.pressure
        state.acceptedRotations++
    }

    fun snapshot(possessionId: String): CoverageSnapshot {
        val state = states[possessionId]
            ?: return CoverageSnapshot(
                defenders = emptySet(),
                totalPressure = 0,
                acceptedRotations = 0,
                lastTimestamp = Long.MIN_VALUE,
                dci = 0.0
            )

        val dci =
            if (state.acceptedRotations == 0) {
                0.0
            } else {
                state.totalPressure.toDouble() /
                    state.acceptedRotations *
                    state.defenders.size
            }

        return CoverageSnapshot(
            defenders = state.defenders.toSet(),
            totalPressure = state.totalPressure,
            acceptedRotations = state.acceptedRotations,
            lastTimestamp = state.lastTimestamp,
            dci = dci
        )
    }
}

---

Task

Identify the concurrency issue or issues in "/app/Main.kt" and implement the correct solution.

Your answer should only include:

1. Issue

2. Corrected Kotlin Implementation

---

Constraints

- Keep the public class name "DefensiveCoverageTracker".
- Keep the public method signatures:
  - "fun processEvent(event: TrackingEvent)"
  - "fun snapshot(possessionId: String): CoverageSnapshot"
- Use only Kotlin/JDK standard library classes.
- Do not use one global lock for the entire tracker.
- The solution must be safe when multiple threads update the same possession concurrently.
- The solution must allow unrelated possessions to be updated independently.
- The snapshot must not read partially updated possession state.



Correct Solution
The safest fix is to make each PossessionState immutable and update it atomically using ConcurrentHashMap.compute.
import java.util.concurrent.ConcurrentHashMap

data class TrackingEvent(
    val possessionId: String,
    val eventId: String,
    val defender: String,
    val pressure: Int,
    val timestamp: Long
)

data class CoverageSnapshot(
    val defenders: Set<String>,
    val totalPressure: Long,
    val acceptedRotations: Int,
    val lastTimestamp: Long,
    val dci: Double
)

// FIX: PossessionState is immutable.
// No mutable sets and no mutable counters are shared across threads.
data class PossessionState(
    val defenders: Set<String> = emptySet(),
    val processedEventIds: Set<String> = emptySet(),
    val totalPressure: Long = 0L,
    val acceptedRotations: Int = 0,
    val lastTimestamp: Long = Long.MIN_VALUE
)

class DefensiveCoverageTracker {

    private val states =
        ConcurrentHashMap<String, PossessionState>()

    fun processEvent(event: TrackingEvent) {
        states.compute(event.possessionId) { _, currentState ->

            val state = currentState ?: PossessionState()

            // Duplicate event IDs must not be applied more than once.
            if (event.eventId in state.processedEventIds) {
                return@compute state
            }

            // Stale events must not update the possession.
            // The acceptedRotations check allows the very first event to be accepted
            // even if its timestamp is Long.MIN_VALUE.
            if (state.acceptedRotations > 0 &&
                event.timestamp <= state.lastTimestamp
            ) {
                return@compute state
            }

            // FIX: Create a new complete state atomically.
            // No thread can observe a partially updated possession.
            state.copy(
                defenders = state.defenders + event.defender,
                processedEventIds = state.processedEventIds + event.eventId,
                totalPressure = state.totalPressure + event.pressure.toLong(),
                acceptedRotations = state.acceptedRotations + 1,
                lastTimestamp = event.timestamp
            )
        }
    }

    fun snapshot(possessionId: String): CoverageSnapshot {
        val state = states[possessionId]
            ?: return CoverageSnapshot(
                defenders = emptySet(),
                totalPressure = 0L,
                acceptedRotations = 0,
                lastTimestamp = Long.MIN_VALUE,
                dci = 0.0
            )

        // FIX: This snapshot is consistent because state is immutable.
        // All fields come from the same accepted PossessionState object.
        val dci =
            if (state.acceptedRotations == 0) {
                0.0
            } else {
                state.totalPressure.toDouble() /
                    state.acceptedRotations *
                    state.defenders.size
            }

        return CoverageSnapshot(
            defenders = state.defenders,
            totalPressure = state.totalPressure,
            acceptedRotations = state.acceptedRotations,
            lastTimestamp = state.lastTimestamp,
            dci = dci
        )
    }
}
Issue in the Original Code
The original code used a ConcurrentHashMap, but the values inside the map were still mutable and shared:
val defenders: MutableSet<String>
val processedEventIds: MutableSet<String>
var totalPressure: Long
var acceptedRotations: Int
var lastTimestamp: Long
ConcurrentHashMap protects access to the map structure, but it does not make the PossessionState object thread-safe.
So these operations were unsafe:
state.processedEventIds.add(event.eventId)
state.defenders.add(event.defender)
state.totalPressure += event.pressure
state.acceptedRotations++
state.lastTimestamp = event.timestamp
Multiple threads could interleave these updates and cause lost defenders, lost rotations, duplicate processing, stale timestamp overwrites, or inconsistent snapshots.
What Was Fixed
The fix makes each possession update atomic.
Instead of mutating a shared PossessionState, the code creates a new immutable state inside:
states.compute(event.possessionId) { ... }
This ensures that, for the same possessionId, the duplicate check, stale check, and all updates happen as one atomic operation.
Snapshots are also safe because they read one immutable PossessionState, so they cannot observe a partially updated possession. Unrelated possessions can still update independently because there is no single global lock.





Core test cases
#
Test name
What it validates
Expected
1
test_compiles
Code is valid Kotlin
Compiles
2
test_empty_snapshot
Unknown possession returns empty snapshot
0 pressure, 0 rotations, 0.0 DCI
3
test_single_event
One valid event updates all fields
1 defender, pressure added, rotations = 1
4
test_duplicate_event_ignored
Same eventId is not applied twice
rotations stays 1
5
test_stale_event_ignored
Older timestamp after newer event is ignored
newer state remains
6
test_equal_timestamp_ignored
Equal timestamp is stale
second event ignored
7
test_same_defender_multiple_events
Defender set de-duplicates names but rotations still count events
defenders size 1, rotations > 1
8
test_multiple_possessions_independent
Updates to one possession do not affect another
separate snapshots
9
test_concurrent_duplicate_event_applied_once
Race on duplicate event processing
rotations must be 1
10
test_concurrent_snapshot_consistency
Snapshot never observes partially updated state
returned DCI matches returned fields
11
test_no_concurrent_modification_during_snapshot
Snapshot does not iterate mutable sets while writers modify them
no exception
12
test_concurrent_same_timestamp_events
Multiple events with same timestamp racing against each other
only one accepted
13
test_concurrent_different_possessions
No global corruption across possessions
each possession remains valid
14
test_stress_many_threads
Repeated high-load race test
stable, no inconsistent snapshots
Most important hidden tests
These are the ones that catch weak fixes.
1. Concurrent duplicate event applied once
This catches solutions that still mutate shared state unsafely.
@Test
fun test_concurrent_duplicate_event_applied_once() {
    val tracker = DefensiveCoverageTracker()

    val event = TrackingEvent(
        possessionId = "P1",
        eventId = "E1",
        defender = "D1",
        pressure = 10,
        timestamp = 100
    )

    val threads = List(50) {
        Thread {
            tracker.processEvent(event)
        }
    }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    val snapshot = tracker.snapshot("P1")

    assertEquals(1, snapshot.acceptedRotations)
    assertEquals(10L, snapshot.totalPressure)
    assertEquals(setOf("D1"), snapshot.defenders)
    assertEquals(100L, snapshot.lastTimestamp)
}
2. Equal timestamp events should not all be accepted
This catches non-atomic timestamp checks.
@Test
fun test_concurrent_same_timestamp_events() {
    val tracker = DefensiveCoverageTracker()

    val events = List(30) { i ->
        TrackingEvent(
            possessionId = "P1",
            eventId = "E$i",
            defender = "D$i",
            pressure = 1,
            timestamp = 100
        )
    }

    val threads = events.map { event ->
        Thread {
            tracker.processEvent(event)
        }
    }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    val snapshot = tracker.snapshot("P1")

    assertEquals(1, snapshot.acceptedRotations)
    assertEquals(1L, snapshot.totalPressure)
    assertEquals(1, snapshot.defenders.size)
    assertEquals(100L, snapshot.lastTimestamp)
}
3. Snapshot consistency under concurrent writes
This catches fixes that synchronize writes but not reads.
@Test
fun test_concurrent_snapshot_consistency() {
    val tracker = DefensiveCoverageTracker()
    val errors = mutableListOf<String>()

    val writer = Thread {
        for (i in 1..10_000) {
            tracker.processEvent(
                TrackingEvent(
                    possessionId = "P1",
                    eventId = "E$i",
                    defender = "D${i % 5}",
                    pressure = 2,
                    timestamp = i.toLong()
                )
            )
        }
    }

    val reader = Thread {
        repeat(10_000) {
            val snapshot = tracker.snapshot("P1")

            val expectedDci =
                if (snapshot.acceptedRotations == 0) {
                    0.0
                } else {
                    snapshot.totalPressure.toDouble() /
                        snapshot.acceptedRotations *
                        snapshot.defenders.size
                }

            if (snapshot.dci != expectedDci) {
                errors.add("Inconsistent snapshot: $snapshot")
            }
        }
    }

    writer.start()
    reader.start()

    writer.join()
    reader.join()

    assertTrue(errors.isEmpty())
}
4. Stale event ignored
@Test
fun test_stale_event_ignored() {
    val tracker = DefensiveCoverageTracker()

    tracker.processEvent(
        TrackingEvent("P1", "E2", "D2", 20, 200)
    )

    tracker.processEvent(
        TrackingEvent("P1", "E1", "D1", 10, 100)
    )

    val snapshot = tracker.snapshot("P1")

    assertEquals(1, snapshot.acceptedRotations)
    assertEquals(20L, snapshot.totalPressure)
    assertEquals(setOf("D2"), snapshot.defenders)
    assertEquals(200L, snapshot.lastTimestamp)
}
5. Duplicate event ignored sequentially
@Test
fun test_duplicate_event_ignored() {
    val tracker = DefensiveCoverageTracker()

    val event = TrackingEvent("P1", "E1", "D1", 10, 100)

    tracker.processEvent(event)
    tracker.processEvent(event)

    val snapshot = tracker.snapshot("P1")

    assertEquals(1, snapshot.acceptedRotations)
    assertEquals(10L, snapshot.totalPressure)
    assertEquals(setOf("D1"), snapshot.defenders)
}
Best extra test to add
Add this one if you want to catch many incomplete fixes:
@Test
fun test_snapshot_does_not_throw_under_concurrent_mutation() {
    val tracker = DefensiveCoverageTracker()
    val failures = mutableListOf<Throwable>()

    val writer = Thread {
        try {
            for (i in 1..20_000) {
                tracker.processEvent(
                    TrackingEvent(
                        possessionId = "P1",
                        eventId = "E$i",
                        defender = "D$i",
                        pressure = 1,
                        timestamp = i.toLong()
                    )
                )
            }
        } catch (t: Throwable) {
            failures.add(t)
        }
    }

    val reader = Thread {
        try {
            repeat(20_000) {
                tracker.snapshot("P1")
            }
        } catch (t: Throwable) {
            failures.add(t)
        }
    }

    writer.start()
    reader.start()

    writer.join()
    reader.join()

    assertTrue(failures.isEmpty())
}
This catches the original code because snapshot() does:
state.defenders.toSet()
while another thread may be mutating the same MutableSet.
The most crucial tests are 9, 10, 11, and 12. Those are the ones that prove the solution is truly concurrency-safe, not just sequentially correct.
