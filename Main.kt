# Compose Stable Layout Transition Coordinator

## Context

A Jetpack Compose screen displays a dynamic list of keyed UI items. Between recompositions, items may move, resize, reorder, appear, disappear, or reappear while an earlier transition is still running.

The coordinator must animate these layout changes by logical key, not by composition order, and must avoid visual jumps, duplicate disappearing items, stale exit animations, or wrong animations after recomposition.

## Task

Implement `StableTransitionColumn`.

## Starter Code

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StableTransitionColumn(
    items: List<TransitionItem>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (TransitionItem) -> Unit
) {
    TODO("Implement")
}

data class TransitionItem(
    val key: String,
    val contentVersion: Long = 0
)



Requirements
Use Jetpack Compose APIs only. Do not use Android View APIs, RecyclerView, coroutines, or third-party animation/layout libraries.
Items are laid out vertically in the order of items. Removed items may remain visually during exit, but must not affect the measured layout of current items.
All animations use linear interpolation over 300ms.
Transitions are keyed by TransitionItem.key, not by item index or composition position.
Reordering existing keys must animate each item from its previous visual bounds to its new bounds without jumping.
Resizing an existing key must animate from its previous visual bounds to its new measured bounds without jumping.
Inserting a new key must animate it from alpha = 0 to alpha = 1 at its final measured position.
Removing a key must keep an exit copy at its last visual bounds and animate its alpha from 1 to 0.
A removed key’s exit copy must be disposed exactly once after its exit animation completes.
If a key reappears while its exit animation is still running, there must be only one visual instance for that key, and the new transition must continue from the current visual bounds.
If a new layout change happens while an item is already animating, the new animation must start from the item’s current visual bounds at interruption time.
Changing contentVersion without changing measured bounds must not restart movement or resize animation.
Changing measured bounds without changing contentVersion must still animate resize.
Output must be deterministic for the same logical input sequence.
The implementation must be testable with Compose UI tests and Roborazzi without requiring a real device.




Test Cases
[Compose UI] Reorder follows keys, not indexes
Start with A, B, C, then reorder to C, A, B. Verify each item animates from its old visual bounds to its new bounds by key, not by list index.
[Compose UI] Resize without visual jump
Render A with height 40, then recompose A with height 100. Verify the first frame after recomposition visually starts from height 40, then animates to 100.
[Compose UI] Move and resize in the same transition
Move A from lower in the list to higher in the list while also changing its measured size. Verify both position and size interpolate from the previous visual bounds.
[Compose UI] Insert waits for final bounds
Insert D between existing items. Verify D appears at its final list position with alpha animating from 0 to 1, and surrounding items animate to their new positions.
[Compose UI] Removed item exits from last visual bounds
Remove B while it is visible. Verify an exit copy remains at B’s last visual bounds and fades out without affecting current item layout.
[Compose UI] Exit copy disposed exactly once
Remove B, advance time past 300ms, and verify B is gone. Repeated recompositions or extra frame advancement must not create or dispose another exit copy.
[Compose UI] Interrupted reorder starts from current visual bounds
Start moving A from position 0 to 200. Advance to 40%, then reorder again so A should end at 80. Verify the new animation starts from the current visual position, not from 0 or 200.
[Compose UI] Interrupted removal uses current visual bounds
Start moving B, advance partially, then remove B. Verify the exit copy starts from B’s current animated bounds, not its original or final list bounds.
[Compose UI] Reappearing key cancels duplicate exit
Remove B, let its exit animation run halfway, then add B back. Verify there is only one visible B, and it continues from the current exit position/alpha into its new final state.
[Compose UI] Content version change without size change
Change A.contentVersion but keep the same measured size and position. Verify no movement/resize animation restarts unnecessarily.
[Compose UI] Size change without content version change
Keep the same key and contentVersion but change measured height through item content. Verify resize still animates.
[Compose UI] Rapid repeated mutations
Apply reorder, insert, remove, reinsert, and resize across several recompositions before earlier animations finish. Verify no duplicate keys are visible, no item jumps, and final layout is correct.
[Compose UI] Determinism under equivalent recomposition
Run the same sequence of item mutations multiple times. Verify final positions, active exits, and cleanup behavior are identical.
[Roborazzi] Reorder midpoint visual regression
Start A, B, C, reorder to C, A, B, advance to 150ms, and capture a screenshot. Verify all items are visually halfway between old and new positions with no jump.
[Roborazzi] Resize midpoint visual regression
Resize A from short to tall, advance to 150ms, and capture a screenshot. Verify intermediate size is visually correct.
[Roborazzi] Removal ghost midpoint visual regression
Remove B, advance to 150ms, and capture a screenshot. Verify B is still visible as a fading exit copy at its old position.
[Roborazzi] Reappear during exit visual regression
Remove B, advance halfway, then reinsert B elsewhere and capture a screenshot. Verify there is only one B, not one exiting copy plus one inserted copy.
[Roborazzi] Mixed transition visual regression
In one sequence, move A, resize B, remove C, insert D, and reinsert E during exit. Capture start, midpoint, and end. Verify no duplicate item, stale exit, missing item, or visual jump.



Reference Solution
The correct solution is based on logical-key transition state.
Maintain state keyed by TransitionItem.key:
current visual bounds
target bounds
animation start bounds
animation start time/progress
whether the key is entering, exiting, or present
retained content for exiting keys
On each composition/layout pass:
Measure current items in order.
Compute their final vertical bounds.
Compare those final bounds with the previous visual state by key.
For existing keys, animate from current visual bounds to new final bounds.
For inserted keys, place at final bounds and animate alpha 0 -> 1.
For removed keys, retain an exit copy at the last current visual bounds and animate alpha 1 -> 0.
The important rule is that every new transition starts from the current visual bounds, not from stale previous layout bounds.
For example:
A is moving from y=0 to y=100.
At 150ms, A is visually around y=50.
A is then retargeted to y=200.
The new animation starts from y=50, not y=0 or y=100.
For reappearing keys:
If B is exiting and B appears again before exit completes:
- cancel the duplicate exit path
- keep one visual B
- start the new transition from B’s current visual bounds/alpha
For removed keys:
The item is no longer in the current item list, but its last composed content must be retained temporarily as an exit copy.
That exit copy fades out and is removed exactly once after 300ms.
For layout:
Current items determine the measured height of the column.
Exiting copies are drawn as overlays and do not affect measurement.
For determinism:
Process keys in lexicographic order when resolving retained state.
Draw current items in item order.
Draw exiting overlays using their last known order, then key as tie-breaker.
A solid implementation can be built with a custom Compose Layout or SubcomposeLayout, keeping per-key transition records in remembered state and advancing animations with Compose animation clocks. The essential correctness points are stable-key tracking, current-visual retargeting, retained exit content, single-instance reappearance handling, and exact cleanup after exit completion.
