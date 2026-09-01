# Android Stable Layout Transition Orchestrator

## Context

A custom Android ViewGroup displays dynamic item views. Between layout passes, items may move, resize, reorder, appear, disappear, detach, or be rebound to different logical items because of view recycling.

The orchestrator must animate these layout changes without visual jumps, stale animations, leaked ghost views, or animations mutating the wrong recycled View.

## Task

Implement StableLayoutTransitionOrchestrator.

## Starter Code

```kotlin
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

class StableLayoutTransitionOrchestrator(
    private val container: ViewGroup,
    private val keyProvider: (View) -> String?,
    private val ghostHost: GhostHost
) {
    fun captureBeforeLayout() {
        TODO("Implement")
    }

    fun animateAfterLayout() {
        TODO("Implement")
    }

    fun cancel(key: String) {
        TODO("Implement")
    }

    fun endAll() {
        TODO("Implement")
    }
}

interface GhostHost {
    fun addGhost(key: String, bounds: Rect, zOrder: Int): View
    fun removeGhost(key: String)
}


Requirements
Use Kotlin and Android framework APIs only. Do not use Jetpack Compose, coroutines, or third-party animation libraries.
Participating items are direct children of container whose keyProvider(view) returns a non-null key, whose visibility is View.VISIBLE, and whose width and height are non-zero.
Transitions are keyed by logical item key, not by View instance. If a View is rebound to another key, old animations must not mutate it. If the same key appears on a different View, the transition follows the key.
captureBeforeLayout() captures the current visual bounds of participating items in container coordinates, including active transition transforms.
animateAfterLayout() compares the captured state with the current post-layout state and starts transitions for moved, resized, inserted, and removed keys. If animateAfterLayout() is called without a prior capture, it must not create animations.
All animations use linear interpolation over 300ms.
For an existing key whose bounds changed, the real post-layout View must visually start at the captured bounds and animate to its final laid-out bounds without jumping. At the end, translationX, translationY, scaleX, scaleY, pivotX, and pivotY must be reset to a clean final state.
Inserted keys animate their real View from alpha = 0 to alpha = 1 at their final laid-out bounds.
Removed keys animate out using a ghost view created from the captured visual bounds. The real removed View must not be mutated after removal. The ghost fades from alpha = 1 to alpha = 0.
Each ghost must be removed exactly once after completion, cancel(key), or endAll().
If a new layout transition starts while an older transition is still running, the new transition must start from the item’s current visual bounds at interruption time, not from the old captured bounds or final layout bounds.
A running animation may mutate a real View only while that View is still a direct child of container and still maps to the same logical key.
cancel(key) affects only that key. Existing or inserted real views for that key must immediately reach a clean final state. Removed-key ghosts for that key must be removed.
endAll() must synchronously finish all active transitions, reset all remaining real views to clean final state, and remove all ghosts.
Repeated capture/animate cycles with no visual change must not create animations or ghost views.
Ghosts for removed keys must be created in ascending previous child index, then lexicographic key. The zOrder passed to addGhost must be the removed item’s previous child index.
The implementation must be testable with Robolectric and must not require real device rendering.





Test Cases
[Robolectric] Move starts from old visual bounds
Capture item A at x = 0. Relayout A at x = 100. Start the transition. Verify the first frame visually places A at the old bounds using translation, and the final frame resets transforms with A laid out at x = 100.
[Robolectric] Resize without visual jump
Capture A as 100x50. Relayout A as 200x80. Start the transition. Verify the start frame visually matches the old size through scale, and the end frame has clean final properties.
[Robolectric] Reorder with recycled View instances
Before layout, V1 represents A and V2 represents B. After layout, V1 represents B and V2 represents A. Verify animations follow logical keys A and B, not stale View instances.
[Robolectric] Removed item uses ghost
Capture A, remove its real View, then animate. Verify addGhost("A", oldBounds, previousIndex) is called, the real removed View is not mutated, and removeGhost("A") is called exactly once.
[Robolectric] Inserted item waits for final bounds
Add new item C after layout. Verify C animates from alpha = 0 to alpha = 1 at its final post-layout bounds, not from zero bounds or stale recycled bounds.
[Robolectric] Interrupted move starts from current visual position
Start A moving from x = 0 to x = 100. Advance to 40%. Capture again, relayout A to x = 200, and animate. Verify the new transition starts from A’s current visual position, not from 0 or 100.
[Robolectric] Interrupted removal creates ghost from current visual bounds
A is moving when it gets removed. Verify the ghost starts from A’s current visual bounds at interruption time, not from the original captured bounds or final bounds.
[Robolectric] Rebound View is not mutated by stale animation
Start animation for key A on V1. Before completion, rebind V1 to key B. Advance animation time. Verify the old A animation no longer mutates V1.
[Robolectric] Same key moves to new View instance
Key A starts animating on V1. Then V1 detaches and V2 represents A. Verify the transition follows key A safely and no later frame touches detached V1.
[Robolectric] Detach during animation
Detach an animating View, advance animation time, then call cancel and endAll. Verify no later operation mutates the detached View.
[Robolectric] cancel(key) affects only one item
Start transitions for A, B, and C. Call cancel("B"). Verify B reaches clean state or its ghost is removed, while A and C continue normally.
[Robolectric] endAll cleanup
Start move, resize, insert, and remove transitions. Call endAll(). Verify all real Views have clean final properties and all ghosts are removed exactly once.
[Robolectric] No-change layout creates nothing
Capture and animate with identical keys and bounds. Verify no ghost is created and no View properties change.
[Robolectric] Ghost cleanup race
Remove A, advance near completion, then call cancel("A"). Verify removeGhost("A") is still called exactly once.
[Robolectric] Deterministic ghost order
Remove multiple items with different previous child indices and keys. Verify ghosts are created in ascending previous child index, then lexicographic key, and each zOrder equals the previous child index.
[Roborazzi] Move + resize midpoint visual check
Capture A at old bounds, relayout it to different position and size, start transition, advance to 150ms, and capture a screenshot. Verify A appears halfway between old and new visual bounds with no jump.
[Roborazzi] Removed item ghost midpoint visual check
Remove A after capture, start transition, advance to 150ms, and capture a screenshot. Verify A still appears as a fading ghost at its old visual position.
[Roborazzi] Interrupted transition visual continuity
Start A moving from x = 0 to x = 100, advance to 40%, then relayout to x = 200 and restart transition. Capture after interruption. Verify A continues smoothly from the interruption position.
[Roborazzi] Recycled View visual regression
V1 starts as key A, then is rebound to key B while A continues on V2. Capture midpoint. Verify B does not visually inherit A’s translation, alpha, or scale.
[Roborazzi] Mixed transition visual regression
In one transition, move A, resize B, remove C, insert D, and reorder E/F. Capture start, midpoint, and end. Verify no duplicate item, missing ghost, stale transform, or visual jump.
[Robolectric] Mixed complex state test
In one update, move A, resize B, remove C, insert D, rebind one View, and reorder children. Verify no stale View mutation, correct ghost lifecycle, clean final state, and no duplicate cleanup.
Reference Solution
The correct solution tracks transitions by logical key, never by View identity alone.
On captureBeforeLayout():
iterate through container children
keep only participating children
compute current visual bounds in container coordinates
include active translation and scale
store key -> visual bounds, child index, and View reference
Current visual bounds should reflect what the user sees, not only layout bounds. For a View, that means using left/top/right/bottom plus current translation and scale.
On animateAfterLayout():
build the current post-layout key -> View/final bounds map
compare captured keys with current keys
existing key with changed bounds: animate real View from captured bounds to final bounds
old key missing now: create a ghost and fade it out
new key missing before: fade the real View in
unchanged key: do nothing
For moved/resized real Views, the View is already laid out at its final bounds. Initialize transforms so it visually appears at the captured bounds:
translationX = oldLeft - newLeft
translationY = oldTop - newTop
scaleX = oldWidth / newWidth
scaleY = oldHeight / newHeight
pivotX = 0
pivotY = 0
Then animate back to:
translationX = 0
translationY = 0
scaleX = 1
scaleY = 1
alpha = 1
For inserted Views:
set alpha = 0
animate alpha to 1
leave final alpha = 1
For removed Views:
create a ghost at the captured visual bounds
set ghost alpha = 1
animate ghost alpha to 0
remove the ghost through an idempotent cleanup path
Ghost cleanup must be guarded so removeGhost(key) is called exactly once, whether the animation completes, cancel(key) is called, or endAll() is called.
For interruption:
capture current visual bounds before replacing old animations
cancel old animations without forcing stale final values
start the new transition from those current visual bounds
For recycled-view safety, every animation update must check:
view.parent == container
keyProvider(view) == key
If either check fails, that animation must stop mutating the View.
cancel(key):
affects only that logical key
cancels its active animator
resets the real View only if it still belongs to that key
removes its ghost exactly once
leaves all other transitions running
endAll():
cancels all active animators
resets all still-valid real Views to clean final state
removes all ghosts exactly once
clears active transition state
For determinism:
process keys in sorted order
create ghosts in ascending previous child index, then key
never rely on unordered maps for observable ordering
