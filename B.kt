Lifecycle Listener Registry
Context
In a Kotlin/Android app, many objects subscribe to updates from a shared event source. Subscribers may be created and destroyed frequently during screen rotation, navigation, lifecycle changes, or background/foreground transitions.
You need a small listener registry that is safe under frequent subscribe/remove operations, avoids retaining removed subscribers, and dispatches events consistently.
Task
Implement:
class ListenerRegistry<E> {
    fun subscribe(
        owner: Any,
        listener: (E) -> Unit
    ): Subscription

    fun emit(event: E)

    fun removeOwner(owner: Any)

    fun clear()

    interface Subscription {
        fun unsubscribe()
    }
}
Requirements
Each call to subscribe creates one independent active subscription.
emit(event) must synchronously deliver the event to the active subscriptions captured for that emit.
Each active subscription captured by an emit must receive that event at most once.
A subscription removed before an emit starts must not receive that event.
A subscription added while an emit is already running must not receive that in-progress event.
A subscription removed while an emit is already running must not receive later events.
unsubscribe() must remove only that subscription.
Calling unsubscribe() more than once must be safe.
removeOwner(owner) must remove all active subscriptions associated with that exact owner instance.
clear() must remove all active subscriptions.
Listener callbacks may call subscribe, unsubscribe, removeOwner, clear, or emit without corrupting registry state.
One slow or throwing listener must not corrupt registry state or prevent removed listeners from being cleaned up.
After a subscription or owner is removed, and after any already-running emit that captured it has finished, the registry must not strongly retain that owner or listener.
The registry must behave correctly when subscribe, unsubscribe, removeOwner, clear, and emit are called concurrently.
The registry should handle frequent subscription churn without pathological performance degradation.
Output Format
Return:
Full Kotlin implementation.
Brief explanation of dispatch, removal, and leak-prevention behavior.
Important tests or pseudocode tests.
Test cases
Basic subscribe and emit
Register one listener, emit an event, and verify the listener receives it exactly once.
Multiple listeners receive the same event
Register several listeners with different owners, emit once, and verify all active listeners receive the event once.
Independent duplicate subscriptions
Subscribe the same owner/listener pair twice, emit once, and verify both subscriptions receive the event. Then unsubscribe one and verify only the other remains active.
Unsubscribe removes only one subscription
Register two subscriptions for the same owner. Unsubscribe one. Verify the other still receives future events.
Unsubscribe is idempotent
Call unsubscribe() multiple times on the same subscription. Verify no crash and no capacity/state corruption.
Removed-before-emit listener does not receive event
Subscribe, unsubscribe, then emit. Verify the removed listener is not called.
Added-during-emit listener does not receive current event
Listener A subscribes listener B during its callback. Verify B does not receive the current event but does receive the next event.
Removed-during-emit listener does not receive future events
Listener A unsubscribes listener B during an emit. Verify B’s future-event behavior follows the defined snapshot semantics for the current emit, and B does not receive later events.
Self-unsubscribe during callback
A listener calls its own unsubscribe() while handling an event. Verify it does not receive later events and the registry remains valid.
removeOwner removes all subscriptions for exact owner instance
Register multiple subscriptions for the same owner and one for a different owner. Call removeOwner(owner). Verify all subscriptions for that owner are removed and the other owner’s subscription remains.
Equal-but-distinct owners are not confused
Use two distinct owner objects that are equals-equal if possible. Verify removeOwner(ownerA) removes only subscriptions associated with the exact owner instance intended by the prompt.
clear removes all listeners
Register many listeners, call clear(), then emit. Verify no listener receives the event.
clear during emit affects later events only
Listener A calls clear() during an emit. Verify the in-progress emit remains consistent, and no listener receives subsequent events.
Listener subscribes and emits reentrantly
A listener calls emit() from inside its callback. Verify nested dispatch works deterministically and does not corrupt state.
Listener removes owner during callback
A listener calls removeOwner(owner) while another event is being dispatched. Verify future events do not go to that owner’s subscriptions.
Throwing listener does not corrupt registry
One listener throws during emit. Verify registry state remains valid, cleanup still works, and later emits behave correctly.
Slow listener with concurrent unsubscribe
One listener blocks or waits while another thread/coroutine unsubscribes it or another listener. Verify no deadlock and future events respect the removal.
Concurrent subscribe and emit
Run many concurrent subscribes while emits are happening. Verify each emit observes a consistent set of listeners and no listener is called more than once per emit.
Concurrent unsubscribe and emit
Run many concurrent unsubscribes while emits are happening. Verify no crashes, no duplicate delivery, and removed subscriptions stop receiving later events.
Concurrent removeOwner and emit
Remove owners while emits are happening. Verify removed owners do not receive later events and registry state remains consistent.
Concurrent clear and emit
Call clear() while emits are happening. Verify no crashes and no listeners receive events after clear has completed.
Memory leak after unsubscribe
Subscribe with an owner and listener, unsubscribe, drop external references, force garbage collection in a leak-style test, and verify the registry no longer strongly retains them after in-flight emits finish.
Memory leak after removeOwner
Register several listeners for an owner, call removeOwner(owner), drop external references, and verify the owner/listeners can be collected after in-flight emits finish.
Memory leak after clear
Register many owners/listeners, call clear(), drop references, and verify they can be collected after in-flight emits finish.
High-churn performance test
Repeatedly subscribe and unsubscribe many listeners while emitting events. Verify no pathological slowdown, memory growth, duplicate calls, or stale listener retention.
