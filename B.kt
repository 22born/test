Android Deferred Navigation Coordinator

Context

An Android app receives navigation events from deep links, push notifications, notification actions, activity results, and in-app clicks.

Events may arrive while the app is cold-starting, logged out, restoring after process death, changing configuration, or waiting for the navigation graph. The coordinator must delay unsafe navigation, avoid duplicate navigation, survive restore, and emit actions deterministically.

Task

Implement "NavigationCoordinator".

Starter Code

class NavigationCoordinator {
    fun submit(event: NavEvent) {
        TODO("Implement")
    }

    fun updateState(state: AppState) {
        TODO("Implement")
    }

    fun nextAction(): NavAction? {
        TODO("Implement")
    }

    fun snapshot(): SavedNavState {
        TODO("Implement")
    }

    fun restore(saved: SavedNavState) {
        TODO("Implement")
    }
}

data class NavEvent(
    val id: String,
    val target: String,
    val requiresAuth: Boolean,
    val priority: Int,
    val timestampMillis: Long
)

data class AppState(
    val lifecycle: LifecycleState,
    val isGraphReady: Boolean,
    val isAuthenticated: Boolean
)

enum class LifecycleState {
    Created,
    Started,
    Resumed,
    Stopped,
    Destroyed
}

data class NavAction(
    val eventId: String,
    val target: String
)

class SavedNavState internal constructor(
    internal val payload: String
)

Requirements

1. A new coordinator starts in "Created", with "isGraphReady = false" and "isAuthenticated = false".

2. Navigation may happen only when lifecycle is "Resumed" and "isGraphReady = true".

3. Events with "requiresAuth = true" may navigate only when "isAuthenticated = true".

4. Events submitted while navigation is not currently allowed must remain available for later eligibility.

5. Event ids are idempotency keys. An event id represented as already emitted in the active coordinator state must not emit again.

6. If "submit" receives an event whose id is already emitted in the active coordinator state, ignore it.

7. If the same event id is submitted more than once before emission, the first accepted event wins and later duplicates are ignored.

8. When multiple events are eligible, choose by highest "priority", then lowest "timestampMillis", then lexicographically smallest "id".

9. Selection among distinct eligible events must not depend on submission order.

10. "nextAction()" must mark the selected event as emitted before returning it.

11. Calling "nextAction()" when no event is eligible must return "null" and must not discard deferred events.

12. "snapshot()" must contain enough information for "restore()" to continue deferred events and prevent replay of events emitted before the snapshot was created.

13. "restore(saved)" replaces all navigation-event history represented by the current coordinator with the saved history.

14. "restore(saved)" must not change the current "AppState".

15. Authentication changes must make previously blocked events eligible without resubmission.

16. A lower-priority public event must not emit before a higher-priority protected event when both are eligible at the time of "nextAction()".

17. All public methods must behave as if each call executes atomically in some sequential order.

18. The coordinator must be safe under concurrent "submit", "updateState", "nextAction", "snapshot", and "restore" calls.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of event ordering, idempotency, restore behavior, and concurrency behavior.
3. Important tests or pseudocode tests.









1. Protected high-priority event becomes eligible before dispatch
Submit a high-priority protected event and a lower-priority public event while unauthenticated. Authenticate before calling nextAction(). Verify the protected event emits first.

2. Ineligible protected event does not block eligible public event
Submit a protected high-priority event and a public low-priority event while unauthenticated. Verify the public event emits, the protected event remains deferred, and later emits after authentication.

3. Consumed event cannot replay after snapshot restore
Submit and emit an event, call snapshot(), restore that snapshot into another coordinator, then submit the same id again. Verify the event never emits again.

4. Deferred event survives restore
Submit an event while lifecycle is not Resumed or graph is not ready. Snapshot, restore, then update state to eligible. Verify the event emits exactly once.

5. Restore replaces current event history
Create a saved state containing event B. In another coordinator, submit event A, then restore the saved state. Verify A is gone and only B can emit.

6. Restore does not change current app state
Restore a saved state containing an otherwise eligible event while current state is not graph-ready. Verify nextAction() returns null until graph readiness is updated.

7. Duplicate id uses first accepted event
Submit two events with the same id but different target, priority, and timestamp before emission. Verify the first accepted event is the one emitted.

8. Ordering under shuffled submission
Submit many eligible distinct events in different orders. Verify emission order always follows priority, then timestamp, then id.

9. Submission order cannot break ties
Use events whose ordering is fully determined by priority, timestamp, and id. Shuffle submissions repeatedly and verify identical output order.

10. Repeated nextAction() consumes exactly once
Submit one eligible event and call nextAction() many times. Verify exactly one call returns the action; all later calls return null.

11. Graph readiness defers without loss
Submit multiple events while lifecycle is Resumed but graph is not ready. Verify no action emits until graph readiness becomes true, then events emit in deterministic order.

12. Lifecycle flapping does not leak or drop events
Submit events while state moves through Created, Started, Stopped, and Resumed. Verify no event emits before Resumed + graphReady, and all deferred events survive.

13. Auth flapping does not leak protected navigation
Submit a protected event, toggle authentication around calls to nextAction(), and verify it emits only during an authenticated eligible state and only once.

14. Lower-priority public event cannot jump ahead once protected event is eligible
Submit a protected higher-priority event and a public lower-priority event. Authenticate before dispatch. Verify the protected event wins.

15. Concurrent nextAction() race
Submit one eligible event and call nextAction() concurrently from many callers. Verify exactly one caller receives the action.

16. Concurrent duplicate submit race
Concurrently submit multiple versions of the same id with different targets/priorities. Verify only one version is accepted, and the result is consistent with a valid atomic ordering.

17. Concurrent snapshot while submitting
Submit events concurrently while taking snapshots and restoring them into new coordinators. Verify restored coordinators never emit duplicate ids and reflect a valid atomic ordering.

18. Concurrent restore and nextAction
Race restore(savedWithB) against nextAction() when the current coordinator has eligible A. Verify the result is consistent with either restore-before-action or action-before-restore, never a mixed corrupted state.

19. Snapshot preserves emitted history
Emit several events, snapshot, restore, then resubmit all emitted ids. Verify none replay.

20. Mixed stress test
Randomly interleave submit, updateState, nextAction, snapshot, and restore. Verify no duplicate emitted ids, no protected event while unauthenticated, no action before Resumed + graphReady, deterministic eligible ordering, and restore replacement semantics.
