Android Deferred Navigation Coordinator

Context

An Android app receives navigation events from deep links, push notifications, notification actions, activity results, and in-app clicks.

Events may arrive while the app is cold-starting, logged out, restoring after process death, changing configuration, or already waiting for the navigation graph. The coordinator must delay unsafe navigation, avoid duplicate navigation, survive restore, and emit actions deterministically.

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

data class SavedNavState(
    val pendingEvents: List<NavEvent>,
    val consumedEventIds: Set<String>
)

Requirements

1. A new coordinator starts in "Created", with "isGraphReady = false" and "isAuthenticated = false".

2. Navigation may happen only when lifecycle is "Resumed" and "isGraphReady = true".

3. Events with "requiresAuth = true" must not navigate unless "isAuthenticated = true".

4. Ineligible events must remain pending until they become eligible, are consumed through restore replacement, or are ignored because their id was already consumed.

5. Event ids are idempotency keys. An event id may be emitted at most once, including across "snapshot()" and "restore()".

6. If "submit" receives an event whose id is already consumed, ignore it.

7. If the same event id is submitted more than once before consumption, the first accepted event wins and later duplicates are ignored.

8. If multiple pending events are eligible, select by highest "priority", then lowest "timestampMillis", then lexicographically smallest "id".

9. Selection among distinct eligible events must not depend on submission order.

10. "nextAction()" consumes the selected event exactly once before returning it.

11. Calling "nextAction()" when no event is eligible must return "null" and must not remove pending events.

12. "snapshot()" must return a consistent saved state containing all pending events and all consumed event ids needed to prevent replay.

13. "restore(saved)" replaces the current pending and consumed event state with "saved".

14. "restore(saved)" does not change the current "AppState".

15. If a restored pending event id also appears in "consumedEventIds", the consumed id wins and that event must not be emitted.

16. Events submitted while lifecycle is not "Resumed", or while the graph is not ready, must not be lost.

17. Authentication changes must make previously blocked events eligible without requiring resubmission.

18. A lower-priority public event must not be emitted before a higher-priority protected event if both are eligible at the time of "nextAction()".

19. All public methods must behave as if each call executes atomically in some sequential order.

20. The coordinator must be safe under concurrent "submit", "updateState", "nextAction", "snapshot", and "restore" calls.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of event ordering, idempotency, restore behavior, and concurrency behavior.
3. Important tests or pseudocode tests.





Important hard test cases
Protected high-priority event becomes eligible before dispatch
Submit a high-priority authenticated event and a lower-priority public event while unauthenticated. Authenticate before calling nextAction(). Verify the protected event emits first.
Ineligible protected event does not block eligible public event
Submit a protected high-priority event and a public low-priority event while unauthenticated. Verify the public event emits, the protected event remains pending, and it emits after authentication.
Consumed event cannot replay after process restore
Submit and emit an event, take a snapshot, restore it into a new coordinator, then submit the same event id again. Verify it never emits again.
Pending event survives process restore
Submit an event while stopped or graph-not-ready, snapshot, restore into a new coordinator, then move to resumed and graph-ready. Verify the event emits exactly once.
Restore replaces current pending state
Start with pending event A, then restore a saved state containing only event B. Verify A is gone and only B can emit.
Consumed id dominates restored pending event
Restore a saved state where event X appears in both pendingEvents and consumedEventIds. Verify X is not emitted.
Duplicate event id uses first accepted event
Submit two events with the same id but different targets/priorities before consumption. Verify the first accepted event is the one emitted.
Priority/timestamp/id ordering under shuffled submission
Submit many eligible events in different orders. Verify emitted order is always highest priority, then oldest timestamp, then lexicographically smallest id.
Distinct event ordering ignores submission order
Use distinct ids with equivalent logical ordering inputs, shuffle submissions repeatedly, and verify identical action order every run.
Repeated nextAction() consumes exactly once
Submit one eligible event. Call nextAction() many times. Verify only the first call returns the action and all later calls return null.
Graph readiness defers without loss
Submit multiple events while lifecycle is Resumed but isGraphReady = false. Verify nextAction() returns null; after graph readiness becomes true, all events emit in deterministic order.
Lifecycle flapping does not drop or leak events
Submit events while state moves through Created, Started, Stopped, and Resumed. Verify no event emits before Resumed + graphReady, and pending events survive until eligible.
Auth flapping does not leak protected navigation
Submit a protected event, toggle authentication false/true/false/true around calls to nextAction(). Verify it emits only while authenticated and only once.
Concurrent nextAction() race
Submit one eligible event and call nextAction() concurrently from many threads/coroutines. Verify exactly one caller receives the action.
Concurrent submit and snapshot consistency
Submit events concurrently while taking snapshots and restoring them into new coordinators. Verify no emitted id appears twice and restored states reflect a valid atomic ordering.
Concurrent restore and nextAction atomicity
Race restore(savedWithB) against nextAction() when current state contains eligible A. Verify the result is consistent with either restore-before-action or action-before-restore, never a corrupted mix.
Consumed ids are included in snapshots
Emit several events, call snapshot(), and verify restoring that snapshot prevents all emitted ids from replaying.
Mixed stress test
Randomly interleave submit, state updates, nextAction, snapshot, and restore. Verify invariants: no duplicate emitted ids, no protected event while unauthenticated, no action before resumed/graph-ready, deterministic eligible ordering, and restore replacement semantics.
