Beat-Aligned Playback Scheduler

Context

In a Kotlin/Android music app, users can trigger loops, samples, and transitions while music is playing. Actions should not happen immediately. They should be scheduled to occur on musical boundaries such as the next beat or next bar.

The scheduler receives timing updates from the audio engine and user commands from the UI. These may arrive concurrently and out of order.

Task

Implement:

class BeatScheduler {
    fun updateClock(
        positionMillis: Long,
        bpm: Double,
        beatsPerBar: Int
    )

    fun schedule(
        id: String,
        quantization: Quantization,
        action: () -> Unit
    )

    fun cancel(id: String)

    fun clear()

    fun tick(nowMillis: Long)
}

enum class Quantization {
    NextBeat,
    NextBar
}

Requirements

1. "schedule" must register an action to run at the next requested musical boundary.
2. "NextBeat" actions run at the next beat boundary after the latest known playback position.
3. "NextBar" actions run at the next bar boundary after the latest known playback position.
4. Actions with the same "id" replace older pending actions with that id.
5. "cancel(id)" removes only the pending action with that id.
6. "clear()" removes all pending actions.
7. An action cancelled before its boundary must not run.
8. An action replaced before its boundary must not run.
9. If "bpm" changes before an action fires, the action must remain aligned to the correct musical boundary under the latest clock.
10. If clock updates arrive out of order, stale timing updates must not move the scheduler backward.
11. "tick(nowMillis)" must run all due actions exactly once.
12. Actions scheduled for the same boundary must run in scheduling order.
13. Actions may schedule, cancel, or clear other actions during execution without corrupting scheduler state.
14. The scheduler must behave correctly when "updateClock", "schedule", "cancel", "clear", and "tick" are called concurrently.
15. The implementation must avoid unbounded growth of cancelled or fired actions.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of musical timing, replacement, cancellation, BPM changes, and stale clock handling.
3. Important tests or pseudocode tests.





Playback Scheduler.
NextBeat quantizes strictly after current position
Set playback exactly on a beat boundary, then schedule NextBeat. Verify it fires on the next beat, not immediately on the current boundary.
NextBar quantizes strictly after current position
Set playback exactly on a bar boundary, then schedule NextBar. Verify it fires on the next bar, not immediately on the current bar boundary.
Near-boundary scheduling does not fire early
Update clock to just before a beat/bar boundary, schedule an action, call tick before the boundary, then after the boundary. Verify it fires only after the boundary is reached.
BPM change before firing preserves musical alignment
Schedule a NextBar action, then change BPM before the action fires. Verify the action fires at the correct next bar according to the latest musical clock, not the old precomputed wall-clock timestamp.
beatsPerBar change before firing affects bar alignment
Schedule a NextBar action with beatsPerBar = 4, then update clock with beatsPerBar = 3 before firing. Verify the action aligns to the correct next bar under the new meter.
Out-of-order clock update is ignored
Send a newer clock update, then an older/stale timing update. Verify the stale update cannot move playback backward or cause an action to fire late, early, or twice.
Same-id replacement cancels older pending action
Schedule action id = "drop", then schedule another action with the same id before the boundary. Verify only the newer action runs.
Replacement with different quantization recomputes boundary
Schedule id = "loop" with NextBar, then replace it with NextBeat. Verify the old bar-aligned action does not run and the replacement fires on the correct beat boundary.
Cancel before boundary prevents execution
Schedule an action, cancel it before its boundary, then advance past the boundary. Verify it never runs.
Cancel racing with tick at boundary
Arrange for cancel(id) and tick(nowAtBoundary) to race. Verify the result is consistent with one valid ordering: either the action runs exactly once, or it is cancelled and never runs. It must not run twice or remain pending.
clear cancels all pending actions
Schedule multiple actions for different boundaries, call clear(), advance time past all boundaries, and verify none run.
clear racing with tick
Race clear() with a tick that makes several actions due. Verify each action either runs once or is cleared, with no duplicate execution and no pending leftovers.
Actions with same boundary run in scheduling order
Schedule multiple actions that quantize to the same beat or bar. Verify execution order matches scheduling order.
Action scheduled during action does not run in same tick unless its boundary is already later and another tick occurs
Have action A schedule action B during execution. Verify B does not get accidentally included in the current due-action snapshot and run reentrantly in the same tick.
Action cancels another due action during execution
Schedule A and B for the same boundary. A runs first and cancels B. Verify B does not run if cancellation occurs before B’s turn.
Action replaces another due action during execution
Schedule A and B for the same boundary. A replaces B with a new action. Verify old B does not run, and the replacement is scheduled according to the current clock and quantization rules.
Exactly-once execution across repeated ticks
Schedule an action, advance past its boundary, then call tick many times. Verify the action runs exactly once and is removed after firing.
Multiple due boundaries in one tick
Schedule actions for different boundaries, then jump the clock far enough that several boundaries are crossed before the next tick. Verify all due actions run once, in boundary-time order, with scheduling order used for ties.
Clock drift correction does not resurrect fired actions
Fire an action, then send a clock update that slightly corrects playback position backward but is not stale under your accepted clock ordering. Verify the fired action does not become pending or run again.
Concurrent schedule, cancel, updateClock, and tick stress test
Randomly interleave clock updates, BPM changes, schedule/replace/cancel/clear calls, and ticks from multiple threads. Verify no duplicate execution, no stale cancelled action runs, same-boundary ordering is preserved, and pending action storage does not grow without bound
