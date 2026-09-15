# Android Interruptible Dropdown Arrow Layout

## Context

A custom Android `ViewGroup` is needed for an expandable dropdown section.

The header draws a chevron arrow. When collapsed, the arrow points down. When expanded, the arrow points up. The transition is not a drawable swap or rotation; it is a geometry morph:

down chevron → flat line → up chevron

The content layout below the header does not toggle immediately. Taps immediately toggle and animate the arrow, but the content visibility changes only after the latest tap has remained stable for 500ms.

Fast taps must reverse the arrow animation from its current rendered geometry without snapping.

## Task

Implement `DebouncedDropdownLayout`.

## Starter Code

```kotlin
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

class DebouncedDropdownLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    fun setExpanded(expanded: Boolean, animated: Boolean = true) {
        TODO("Implement")
    }

    fun isExpanded(): Boolean {
        TODO("Implement")
    }

    fun isContentVisible(): Boolean {
        TODO("Implement")
    }

    override fun performClick(): Boolean {
        TODO("Implement")
    }
}



Requirements
Use Android View/ViewGroup drawing and layout APIs only. Do not use Compose, MotionLayout, AnimatedVectorDrawable, prebuilt arrow drawables, or third-party animation libraries.
The layout supports zero or one content child. If present, the child is the expandable content below the header.
The header height is 48dp.
The arrow is drawn centered inside the header area.
The arrow is drawn as two stroked line segments: left endpoint → center point → right endpoint.
The left and right endpoints remain fixed during animation. Only the center point moves vertically.
The left endpoint is 6dp left of the header center. The right endpoint is 6dp right of the header center.
In collapsed state, the center point is 4dp below the endpoint line.
In expanded state, the center point is 4dp above the endpoint line.
At animation midpoint, the center point lies exactly on the endpoint line, producing a flat horizontal line.
Arrow stroke width is 2dp, stroke cap is round, stroke join is round, and color is black.
Arrow animation duration is exactly 300ms total.
Arrow animation interpolation is linear.
Arrow animation progress must be sampled from android.os.SystemClock.uptimeMillis(). A draw at any sampled main-looper time must render the geometry for that time.
isExpanded() returns the current arrow target state, not the delayed content visibility state.
isContentVisible() returns whether the expandable content child is currently displayed.
setExpanded(expanded, animated = false) immediately sets the arrow to its final geometry, cancels any running arrow animation, cancels any pending content visibility commit, and immediately sets content visibility to expanded.
setExpanded(expanded, animated = true) immediately changes the arrow target state and starts animating the arrow from the currently rendered geometry.
If setExpanded is called while the arrow is animating, the old animation must not produce any later visual update.
If the arrow animation is interrupted, the new animation starts from the current rendered arrow geometry without snapping to collapsed, flat, or expanded first.
Calling setExpanded with the current arrow target state must not visually jump.
performClick() toggles from the current arrow target state, not from the current content visibility state.
A tap immediately toggles the arrow target state and starts/reverses the arrow animation.
The content child must not become visible or hidden immediately on tap.
After each tap or animated setExpanded call, schedule a content visibility commit for 500ms after that action’s SystemClock.uptimeMillis() time.
If another tap or animated setExpanded call occurs before the pending 500ms commit fires, cancel the previous pending commit and schedule a new one.
When the latest 500ms commit fires, set content visibility to the latest arrow target state.
Content visibility must not change before the latest arrow animation has reached its final geometry.
During the delay window, the content visibility remains whatever it was before the latest pending commit.
When content is visible, the child is measured and laid out below the 48dp header. When content is hidden, the child is GONE and does not contribute to measured height.
Programmatic non-animated state changes override pending tap behavior.
The implementation must not crash for zero-size or very small layouts.
Drawing and layout must be deterministic enough for Robolectric bitmap tests and Roborazzi screenshot tests.





```text id="1gpzt0"
# Important Tests

1. [Robolectric] initial collapsed state
Create the layout. Verify `isExpanded()` is false, `isContentVisible()` is false, the child is GONE, and the arrow points down.

2. [Robolectric/Bitmap] expanded arrow geometry
Call `setExpanded(true, animated = false)`. Verify the center point is above the fixed endpoints and the arrow points up.

3. [Robolectric/Bitmap] collapsed arrow geometry
Call `setExpanded(false, animated = false)`. Verify the center point is below the fixed endpoints and the arrow points down.

4. [Robolectric/Bitmap] midpoint flat line
Start collapsed, call `setExpanded(true, animated = true)`, advance main-looper time to animation start + 150ms, force draw. Verify the center point lies on the endpoint line.

5. [Robolectric/Roborazzi] expand animation sampled frames
Start collapsed and animate to expanded. Capture at 0ms, 75ms, 150ms, 225ms, and 300ms. Verify down → partly flat → flat → partly up → up.

6. [Robolectric/Roborazzi] collapse animation sampled frames
Start expanded and animate to collapsed. Capture at 0ms, 75ms, 150ms, 225ms, and 300ms. Verify up → partly flat → flat → partly down → down.

7. [Robolectric] clock-derived sampling
Start an animation, advance the main-looper clock directly to 100ms after start, force draw, and verify the arrow geometry corresponds to one-third progress.

8. [Robolectric] tap immediately changes arrow target
Start collapsed. Call `performClick()`. Verify `isExpanded()` becomes true immediately, but `isContentVisible()` remains false.

9. [Robolectric] content reveal delayed
Start collapsed and tap once at time T. At T + 499ms verify content is still hidden. At T + 500ms verify content becomes visible.

10. [Robolectric] content does not reveal when arrow merely finishes
Start collapsed and tap once. At T + 300ms verify the arrow is fully expanded but content is still hidden.

11. [Robolectric] fast taps reverse arrow target
Start collapsed. Tap at T, T + 100ms, and T + 200ms. Verify arrow target states are expanded, collapsed, expanded.

12. [Robolectric] fast taps cancel pending content commits
Start collapsed. Tap at T, tap again at T + 100ms. Advance to T + 500ms. Verify content is still hidden because the first pending commit was cancelled.

13. [Robolectric] final tap controls content visibility
Start collapsed. Tap at T, T + 100ms, and T + 200ms. Verify content is hidden at T + 699ms and visible at T + 700ms.

14. [Robolectric] collapsed final target keeps content hidden
Start collapsed. Tap at T, tap again at T + 100ms. At T + 600ms verify latest arrow target is collapsed and content remains hidden.

15. [Robolectric] collapse content delayed
Start expanded with content visible. Tap once. Verify arrow target becomes collapsed immediately, content remains visible until T + 500ms, then becomes hidden.

16. [Robolectric/Roborazzi] no snap on reversal
Start collapsed, tap, advance 100ms, tap again. Capture immediately after the second tap. Verify the arrow continues from its current in-flight geometry toward collapsed.

17. [Robolectric] tap toggles from arrow target, not content visibility
Start collapsed. Tap once so arrow target is expanded but content is still hidden. Tap again before 500ms. Verify target becomes collapsed, not expanded again.

18. [Robolectric] animated setExpanded schedules delayed content
Call `setExpanded(true, animated = true)`. Verify arrow target changes immediately, content remains hidden until 500ms, then becomes visible.

19. [Robolectric] non-animated setExpanded overrides pending tap
Start collapsed, tap once, then before 500ms call `setExpanded(true, animated = false)`. Verify content becomes visible immediately and no later pending commit changes it.

20. [Robolectric] programmatic collapse cancels pending reveal
Start collapsed, tap once, then before 500ms call `setExpanded(false, animated = false)`. Advance past the original reveal time. Verify content remains hidden.

21. [Robolectric] repeated same target does not jump
Start animating to expanded, advance halfway, call `setExpanded(true, animated = true)` again. Verify the arrow does not snap or restart from collapsed.

22. [Robolectric] stale animation ignored
Start one animation, interrupt it with another, then advance past the first animation’s original completion time. Verify the old animation does not update the arrow geometry.

23. [Robolectric] child measurement hidden
With one content child, verify hidden content is GONE and does not contribute to measured height.

24. [Robolectric] child measurement visible
After the delayed expand commit fires, verify the child is VISIBLE, measured, and laid out below the 48dp header.

25. [Robolectric/Bitmap] fixed endpoint positions
At mdpi with no padding, verify the left and right endpoints are 6px from header center and remain fixed across animation frames.

26. [Roborazzi] delayed expand visual sequence
Capture collapsed, arrow-expanded-but-content-hidden at 300ms, and arrow-expanded-with-content-visible at 500ms.

27. [Roborazzi] rapid tap visual sequence
Tap at T, T + 100ms, and T + 200ms. Capture frames showing smooth arrow reversals and no content visibility until the final 500ms commit.

28. [Robolectric/Roborazzi] tiny layout stability
Render zero-size and very small layouts. Verify no crash and deterministic output.
