Prioritized Weak Listener Registry
Context
In a Kotlin/Android app, many objects subscribe to updates from a shared event source. Subscribers may be created and destroyed frequently during screen rotation, navigation, lifecycle changes, or background/foreground transitions.
You need a listener registry that dispatches events consistently, supports listener priority, avoids retaining owners after removal, and remains safe when listeners modify the registry during dispatch.
Task
Implement:
class ListenerRegistry<E> {
    fun subscribe(
        owner: Any,
        priority: Int = 0,
        listener: (E) -> DispatchResult
    ): Subscription

    fun emit(event: E)

    fun removeOwner(owner: Any)

    fun clear()

    interface Subscription {
        fun unsubscribe()
    }
}

enum class DispatchResult {
    Continue,
    Stop
}
Requirements
Each call to subscribe creates one independent active subscription.
The registry must not keep owners alive only because they subscribed.
If an owner is no longer reachable elsewhere, its subscriptions must eventually stop receiving events.
emit(event) must synchronously deliver the event to active subscriptions captured for that emit.
For each emit, listeners must be called in descending priority order.
Listeners with the same priority must be called in subscription order.
Each captured active subscription must receive the event at most once.
If a listener returns Stop, later listeners in that emit must not receive the event.
A subscription removed before an emit starts must not receive that event.
A subscription added while an emit is already running must not receive that in-progress event.
A subscription removed while an emit is already running must not receive later events.
If a listener calls emit() while handling another event, the nested emit must complete before the outer emit continues to the next listener.
unsubscribe() must remove only that subscription.
Calling unsubscribe() more than once must be safe.
removeOwner(owner) must remove all active subscriptions associated with that exact owner instance.
clear() must remove all active subscriptions.
Listener callbacks may call subscribe, unsubscribe, removeOwner, clear, or emit without corrupting registry state.
One slow or throwing listener must not corrupt registry state.
After unsubscribe, removeOwner, or clear, and after any already-running emit that captured them has finished, the registry must not strongly retain removed owners or removed listener lambdas.
The registry must behave correctly when subscribe, unsubscribe, removeOwner, clear, and emit are called concurrently.
The implementation should handle frequent subscription churn without pathological memory growth.
Output Format
Return:
Full Kotlin implementation.
Brief explanation of dispatch ordering, removal, weak ownership, and reentrant emit behavior.
Important tests or pseudocode tests.


Core dispatch tests
Basic subscribe and emit
Subscribe one listener, emit one event, and verify it receives the event exactly once.
Multiple listeners receive event
Subscribe several listeners with different owners, emit once, and verify all active listeners receive the event.
Duplicate subscriptions are independent
Subscribe the same owner/listener pair twice. Emit once. Verify both subscriptions are called. Unsubscribe one and verify only the other remains active.
Priority order
Subscribe listeners with priorities 10, 0, and 5. Emit once. Verify call order is 10 → 5 → 0.
Same-priority subscription order
Subscribe three listeners with the same priority. Emit once. Verify they are called in subscription order.
Stop propagation
Subscribe listeners A, B, C in dispatch order. Make B return Stop. Verify A and B are called, but C is not.
Stop respects priority order
Subscribe high-, medium-, and low-priority listeners. Make the medium-priority listener return Stop. Verify lower-priority listeners are skipped.
Removal behavior tests
Unsubscribe removes only one subscription
Subscribe two listeners for the same owner. Unsubscribe one. Verify the other still receives future events.
Unsubscribe is idempotent
Call unsubscribe() multiple times on the same subscription. Verify no crash and no duplicate cleanup effects.
Removed-before-emit listener is not called
Subscribe, unsubscribe, then emit. Verify the removed listener is not called.
removeOwner removes all subscriptions for exact owner
Subscribe multiple listeners for one owner and one listener for another owner. Call removeOwner(owner). Verify only that owner’s listeners are removed.
Equal-but-distinct owners are not confused
Use two distinct objects that are equals-equal if possible. Verify removeOwner(ownerA) removes only subscriptions for that exact instance.
clear removes all listeners
Subscribe many listeners, call clear(), then emit. Verify no listener is called.
Mutation-during-dispatch tests
Added-during-emit listener does not receive current event
Listener A subscribes listener B during its callback. Verify B does not receive the current event but does receive the next event.
Self-unsubscribe during callback
Listener A unsubscribes itself during its callback. Verify A receives the current event once, but not later events.
Unsubscribe another listener during callback
Listener A unsubscribes listener B during the same emit. Verify the current emit follows snapshot semantics, and B does not receive later events.
removeOwner during callback
Listener A calls removeOwner(ownerB) during dispatch. Verify ownerB’s listeners do not receive future events.
clear during callback
Listener A calls clear() during dispatch. Verify the in-progress emit is consistent, and later emits call no listeners.
Subscribe with higher priority during emit
A low-priority listener subscribes a new high-priority listener during dispatch. Verify the new listener does not jump into the current emit, but is first on the next emit.
Stop after mutation during emit
Listener A subscribes/removes listeners, then listener B returns Stop. Verify listeners after B in the original emit are skipped, and registry state is correct for later emits.
Reentrant emit tests
Nested emit completes before outer emit continues
Listener A calls emit("nested") while handling emit("outer"). Verify all nested-event listeners run before the outer emit continues to listener B.
Nested emit observes current registry state
Listener A subscribes listener C, then calls nested emit. Verify C receives the nested event but not the already-running outer event.
Nested emit after removal
Listener A removes listener B, then calls nested emit. Verify B does not receive the nested event or later events.
Stop in nested emit does not stop outer emit
A listener returns Stop during the nested emit. Verify nested propagation stops, but the outer emit continues according to the outer emit’s own listener results.
Stop in outer emit after nested emit
Listener A calls nested emit, then returns Stop for the outer event. Verify nested dispatch completes, then the outer emit stops before later outer listeners.
Exception and slow-listener tests
Throwing listener does not corrupt registry
One listener throws during emit. Verify registry state remains valid and future emits/removals still work.
Throwing listener behavior is deterministic
Decide expected behavior: either emit propagates the exception and stops current dispatch, or catches and continues. Test that behavior consistently.
Slow listener with concurrent unsubscribe
One listener blocks during callback while another thread/coroutine unsubscribes a different listener. Verify no deadlock and future events respect the removal.
Weak ownership and leak tests
Registry does not keep owner alive
Subscribe with an owner, drop all strong references except the registry, force GC in a leak-style test, then emit. Verify the owner can be collected and its subscription eventually stops receiving events.
Weak-owner cleanup removes dead subscriptions
After an owner is collected, emit multiple times or trigger cleanup. Verify dead subscriptions are removed and do not accumulate forever.
No owner leak after unsubscribe
Subscribe, unsubscribe, drop owner reference, force GC. Verify the registry does not retain the owner after any in-flight emit finishes.
No listener leak after unsubscribe
Subscribe with a listener object/lambda, unsubscribe, drop external references, force GC. Verify the registry does not retain the listener after any in-flight emit finishes.
No owner/listener leak after removeOwner
Register several listeners for an owner, call removeOwner(owner), drop references, force GC. Verify owner and listeners can be collected.
No owner/listener leak after clear
Register many owners/listeners, call clear(), drop references, force GC. Verify removed owners and listeners can be collected.
Concurrent access tests
Concurrent subscribe and emit
Subscribe listeners concurrently while emits are happening. Verify no crashes, no duplicate calls per emit, and newly added listeners only appear in later emits.
Concurrent unsubscribe and emit
Unsubscribe listeners concurrently while emits are happening. Verify no crashes and unsubscribed listeners stop receiving later events.
Concurrent removeOwner and emit
Remove owners concurrently with emits. Verify removed owners do not receive later events.
Concurrent clear and emit
Call clear() concurrently with emits. Verify no crash and no listener receives events after clear has completed and in-flight emits finish.
Concurrent priority churn
Rapidly subscribe/unsubscribe listeners with different priorities while emitting. Verify each emit’s call order is valid for its captured snapshot.
High-churn memory/performance test
Repeatedly subscribe, unsubscribe, remove owners, clear, and emit. Verify no pathological memory growth, no duplicate delivery, and no stale listener retention.
The most important tests are 4, 5, 6, 14, 21, 22, 29, 32, 35, and 40.
