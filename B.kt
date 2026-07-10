Add these requirements:
Actions that become due in the same tick must run in increasing scheduled boundary order. Actions with the same boundary must run in scheduling order.

tick(nowMillis) uses a due-action snapshot. Actions scheduled or replaced while a tick is executing must not run during that same tick, even if their computed boundary is already due. They may run on a later tick.

Actions scheduled or replaced during an action callback must compute their boundary from the latest accepted clock state, not from the tick’s nowMillis unless updateClock has advanced the clock state.
That last line fixes the ambiguity in Test 16: recomputation should be based on the scheduler’s latest accepted musical clock, not silently on the current tick(nowMillis).
Corrected versions of the disputed tests
Test 18: Multiple due boundaries in one tick
This is valid only after adding boundary-ordering semantics.
Correct description:
Schedule actions for different musical boundaries, then call tick after all of them are due. Verify due actions run in increasing scheduled boundary order. For actions with the same boundary, verify scheduling order is preserved.
Test 14: Action scheduled during action
This is valid only after adding due-action snapshot semantics.
Correct description:
Action A runs during tick and schedules action B whose boundary would already be due under the latest clock. Verify B does not run in the same tick. Verify B can run on a later tick if it is still due.
Test 16: Action replaces another due action during execution
This needs a careful rewrite.
Correct description:
Actions A and B are due in the same tick. A runs first and replaces B with a new action using the same id. Verify the old B does not run in the current tick. Verify the replacement does not run in the current tick, and on a later tick it runs only if its boundary, computed from the latest accepted clock state at replacement time, is due.
This avoids assuming that tick(nowMillis) itself advances the musical clock.
