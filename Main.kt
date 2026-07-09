Gesture Ownership Router

Context

In an Android/Kotlin app, multiple gesture recognizers may want to handle the same pointer stream. For example, a photo viewer may want pinch gestures, a bottom sheet may want vertical drags, and a drawing canvas may want stylus input.

You need a small router that receives pointer events and dispatches them to the correct recognizer while preserving ownership, cancellation, and reentrant dispatch rules.

Task

Implement:

class GestureRouter {
    fun register(
        recognizer: GestureRecognizer
    ): Registration

    fun dispatch(event: PointerEvent)

    fun cancel(pointerId: Int)

    fun clear()

    interface Registration {
        fun unregister()
    }
}

interface GestureRecognizer {
    fun onPointerEvent(event: PointerEvent): GestureDecision
    fun onCancel(pointerId: Int)
}

data class PointerEvent(
    val pointerId: Int,
    val type: PointerEventType,
    val x: Float,
    val y: Float,
    val timestampMillis: Long
)

enum class PointerEventType {
    Down,
    Move,
    Up,
    Cancel
}

enum class GestureDecision {
    Ignore,
    Interested,
    Claim,
    Release
}

Requirements

1. Each call to "register" creates one independent active registration.
2. "unregister()" must remove only that registration.
3. Calling "unregister()" more than once must be safe.
4. A "Down" event starts a new pointer stream for that "pointerId".
5. Events for a pointer without an active stream must be ignored unless the event is "Down".
6. While no recognizer owns a pointer stream, events are offered to active recognizers in registration order.
7. "Interested" does not claim ownership and does not prevent later recognizers from receiving the same event.
8. "Ignore" does not claim ownership and does not prevent the recognizer from participating in future streams.
9. If a recognizer returns "Claim", it becomes the owner of that pointer stream, and no later recognizer receives that same event.
10. Once a recognizer owns a pointer stream, future events for that pointer go only to that owner.
11. If the owner returns "Release", ownership ends after that callback; the same event is not re-dispatched to other recognizers.
12. After ownership is released, later events for that active pointer stream may be offered again in registration order.
13. "Up" and "Cancel" events are delivered to the current owner if one exists, then end the pointer stream.
14. If there is no owner, "Up" and "Cancel" end the pointer stream after normal unowned routing.
15. "cancel(pointerId)" must notify the current owner with "onCancel(pointerId)", if one exists, and end that pointer stream.
16. If a recognizer unregisters while owning one or more pointer streams, it must receive "onCancel(pointerId)" exactly once for each owned active stream, and those streams lose ownership.
17. If an owner unregisters itself while handling an event for a stream it owns, it must not receive duplicate cancellation for that same stream.
18. "clear()" must unregister all recognizers, cancel all owned streams exactly once, and remove all active pointer streams.
19. A recognizer registered during dispatch must not receive the event currently being dispatched.
20. A recognizer unregistered before dispatch starts must not receive that event.
21. If a recognizer is unregistered during dispatch before its turn, it must not receive that event.
22. A recognizer may call "register", "unregister", "clear", "cancel", or "dispatch" from inside a callback without corrupting router state.
23. If a recognizer calls "dispatch()" while handling another event, the nested dispatch must complete before the outer dispatch continues to the next recognizer.
24. Events for different "pointerId"s must maintain independent ownership.
25. The router must not strongly retain unregistered recognizers after any in-flight dispatch that captured them has finished.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of ownership, release, cancellation, unregister, clear, and nested dispatch behavior.
3. Important tests or pseudocode tests.



Important test cases
1. Claim stops current dispatch
Register recognizers A, B, and C.
A returns Interested, B returns Claim, and C records calls.
Dispatch Down(pointerId = 1).
Assert:
- A receives the Down event.
- B receives the Down event.
- C does not receive the Down event.
- B becomes owner of pointer 1.
2. Owner receives later events exclusively
B owns pointer 1.
Dispatch:
Move(pointerId = 1)
Move(pointerId = 1)
Up(pointerId = 1)
Assert:
- Only B receives the Move events.
- B receives the Up event.
- A and C do not receive those owned events.
- Pointer stream 1 ends after Up.
3. Events without active stream are ignored except Down
Dispatch Move, Up, and Cancel for pointer 99 without a prior Down.
Assert:
- No recognizer receives those events.
- No stream or owner is created.
Then dispatch Down(pointerId = 99).
Assert:
- The Down event starts routing normally.
4. Release affects only future events
B owns pointer 1.
During Move(pointerId = 1), B returns Release.
Assert:
- The current Move is delivered only to B.
- The current Move is not re-offered to A or C.
- Ownership is cleared after B’s callback.
Then dispatch another Move(pointerId = 1).
Assert:
- The new Move is offered in registration order.
- Another recognizer may claim it.
5. Up clears owner and ends stream
B owns pointer 1.
Dispatch Up(pointerId = 1), then dispatch Move(pointerId = 1).
Assert:
- B receives Up.
- Ownership is cleared.
- The later Move is ignored because the stream ended.
6. Cancel event clears owner and ends stream
B owns pointer 1.
Dispatch PointerEvent(type = Cancel, pointerId = 1).
Assert:
- B receives the Cancel event through onPointerEvent.
- Ownership is cleared.
- Stream 1 ends.
- Later non-Down events for pointer 1 are ignored.
7. External cancel notifies owner once
B owns pointer 1.
Call:
router.cancel(1)
Assert:
- B.onCancel(1) is called exactly once.
- Stream 1 ends.
- Later events for pointer 1 do not go to B unless a new Down starts a new stream.
8. Owner unregisters while owning
B owns pointer 1.
Call B’s registration:
registrationB.unregister()
Assert:
- B.onCancel(1) is called exactly once.
- B is removed from the registry.
- Stream 1 remains active but unowned, or is ended only if your implementation documents that unregister ends it.
- Later events do not go to B.
For this prompt, the expected behavior is:
The stream remains active but loses ownership.
9. Owner unregisters itself during owned callback
B owns pointer 1.
During Move(pointerId = 1), B calls its own unregister().
Assert:
- Router state remains valid.
- B is removed.
- B does not receive duplicate onCancel for pointer 1.
- Pointer 1 loses ownership.
- Later events for pointer 1 do not go to B.
10. Unregister removes recognizer from all owned pointers
A owns pointer 1 and pointer 2.
A unregisters.
Assert:
- A.onCancel(1) is called exactly once.
- A.onCancel(2) is called exactly once.
- Both streams lose ownership.
- A receives no later events.
11. Register during dispatch does not receive current event
A receives Down(pointerId = 1) and registers D during its callback.
Assert:
- D does not receive the current Down event.
- D may receive a later unowned event or a future stream.
12. Unregister before turn skips current event
A, B, and C are registered.
During A’s callback, A unregisters C before C’s turn.
Assert:
- C does not receive the current event.
- C does not receive later events.
13. Interested does not block later recognizers
A returns Interested, B returns Interested, C returns Claim.
Dispatch Down(pointerId = 1).
Assert:
- A, B, and C receive the Down event.
- C becomes owner.
- Later pointer 1 events go only to C.
14. Ignore does not permanently disable recognizer
A returns Ignore for pointer 1.
Later, a new stream starts with Down(pointerId = 2), and A returns Claim.
Assert:
- A can still receive and claim future streams.
- Ignore affects only the current routing decision.
15. Nested dispatch completes before outer dispatch continues
A receives an outer event and calls dispatch(nestedEvent) inside its callback.
Assert call order:
A: outer event
nested dispatch fully completes
B: outer event
C: outer event
This verifies stack-like reentrant dispatch.
16. Nested dispatch observes updated registry
A receives an outer event, registers D, then calls nested dispatch.
Assert:
- D does not receive the already-running outer event.
- D can receive the nested event if eligible.
17. Nested dispatch with independent pointer ownership
B owns pointer 1.
During B’s callback for pointer 1, B dispatches a Down or Move for pointer 2.
Assert:
- Pointer 1 ownership remains with B.
- Pointer 2 is routed independently.
- Ownership state for pointer 1 and pointer 2 does not corrupt each other.
18. Multiple pointers owned by different recognizers
A claims pointer 1. B claims pointer 2.
Dispatch mixed events:
Move pointer 1
Move pointer 2
Up pointer 1
Move pointer 2
Assert:
- Pointer 1 events go only to A until Up.
- Pointer 2 events go only to B.
- Ending pointer 1 does not affect pointer 2.
19. clear cancels all owned streams and removes recognizers
A owns pointer 1, B owns pointer 2, and C is registered but owns nothing.
Call:
router.clear()
Assert:
- A.onCancel(1) is called exactly once.
- B.onCancel(2) is called exactly once.
- C is removed.
- All pointer streams are removed.
- Later events are ignored until new recognizers are registered.
20. clear during callback
A receives an event and calls clear() inside its callback.
Assert:
- Router does not crash.
- All owned streams are cancelled exactly once.
- No recognizer receives later events after clear.
- Recognizers removed by clear are not called later in the same dispatch if their turn had not happened yet.
21. Concurrent cancel and unregister race
A owns pointer 1.
Concurrently call:
router.cancel(1)
registrationA.unregister()
Assert:
- A.onCancel(1) is called at most once.
- Registration A is removed.
- Pointer 1 has no stale owner.
- Later events do not go to A.
22. Concurrent register, unregister, dispatch, cancel, and clear stress test
Run many operations concurrently:
register
unregister
dispatch Down / Move / Up / Cancel
cancel(pointerId)
clear
nested dispatch from callbacks
Assert:
- No crashes.
- No duplicate delivery of the same event to the same active registration.
- No stale owner after Up, Cancel, unregister, cancel(pointerId), or clear.
- No recognizer receives events after unregister or clear has completed, except if it was already inside its own callback.
23. Leak after unregister
Register a recognizer, unregister it, drop all external strong references, and force GC in a leak-style test.
Assert:
- The router does not strongly retain the recognizer after any in-flight dispatch that captured it has finished.
24. Leak after unregister while owning
A recognizer owns a pointer stream, then unregisters.
Drop external references and force GC in a leak-style test.
Assert:
- The recognizer can be collected.
- No registration entry or ownership entry retains it.
These are the important tests. The hardest and highest-value ones are 1, 4, 8, 9, 15, 16, 17, 19, 20, 21, and 24.
