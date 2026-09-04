# Android Animated Step Progress View

## Context

A custom Android `View` is needed to render a horizontal multi-step progress indicator. The indicator contains a full-width horizontal bar and numbered step circles placed at fixed intervals.

The visual meaning is:

- completed step: orange filled circle
- current step: white circle with orange outline
- future step: white circle with black outline

When progress moves from one step to another, the orange bar fill animates horizontally between step positions. A step becomes filled only after progress has moved beyond that step.

This task tests deterministic custom drawing and sampled animation correctness. It does not attempt to measure real device jank.

## Task

Implement `StepProgressView`.

## Starter Code

```kotlin
import android.content.Context
import android.util.AttributeSet
import android.view.View

class StepProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun setStepCount(count: Int) {
        TODO("Implement")
    }

    fun setCurrentStep(step: Int, animated: Boolean = true) {
        TODO("Implement")
    }

    fun jumpToStep(step: Int) {
        TODO("Implement")
    }
}




Design and Logic
The view draws a horizontal step progress indicator across its full measured width.
The background bar is a straight horizontal white bar with height 10dp. It is vertically centered in the view.
The bar has a top border and bottom border. Each border is black and 0.5dp thick.
The active/completed color is orange: #FFFF9800.
Step circles have diameter 30dp, are vertically centered on the bar, and have a 0.5dp border.
Step circles are placed every 30dp.
In LTR:
step 1 leading edge = 0dp
step 2 leading edge = 30dp
step 3 leading edge = 60dp
...
In RTL, the same positions are mirrored from the right edge of the view.
The progress coordinate of a step is the leading edge of that step’s circle, not the circle center.
The step number is drawn centered inside each circle.
Text color rules:
completed step text = white
current step text   = black
future step text    = black
Visual state rules:
completed step = orange filled circle, white number
current step   = white circle, orange outline, black number
future step    = white circle, black outline, black number
For example, when moving from step 2 to step 3:
- the orange bar endpoint animates from the leading edge of step 2 to the leading edge of step 3
- step 2 is outlined while progress is exactly at step 2
- step 2 becomes filled only after progress moves beyond step 2
- step 3 becomes outlined when the progress endpoint reaches step 3
- step 3 is not filled until progress moves beyond step 3
Requirements
Use Android custom View drawing APIs only. Do not use Compose, RecyclerView, MotionLayout, third-party animation libraries, or prebuilt progress/stepper widgets.
stepCount must be at least 2. Calling setStepCount(count) with count < 2 must throw IllegalArgumentException.
currentStep is 1-based. Calling setCurrentStep or jumpToStep with a step outside 1..stepCount must throw IllegalArgumentException.
The background bar is white, 10dp high, vertically centered, and spans the full measured width of the view.
The background bar has only a top and bottom black border. Each border is 0.5dp thick.
Step circles have diameter 30dp, are vertically centered on the bar, and have a 0.5dp stroke.
Step circles are spaced at 30dp intervals.
In LTR, step n has leading edge (n - 1) * 30dp.
In RTL, step n is mirrored from the right edge of the view; step 1 is rightmost.
The progress coordinate of a step is the leading edge of that step’s circle.
The active bar fill is orange #FFFF9800.
The active bar fill is drawn from the leading edge of step 1 to the current animated progress endpoint.
Animation duration is exactly 300ms, linear, with no start delay.
jumpToStep(step) immediately sets the visual progress to that step, cancels any running animation, and redraws.
setCurrentStep(step, animated = false) behaves the same as jumpToStep(step).
setCurrentStep(step, animated = true) animates from the current rendered progress position, not from the last committed step.
If setCurrentStep is called while an animation is running, the old animation must not produce any later visual update.
If an animation is interrupted by a new target step, the new animation starts from the currently rendered progress position without jumping.
A completed step is drawn as an orange filled circle with white step number text.
A current step is drawn as a white circle with orange outline and black step number text.
A future step is drawn as a white circle with black outline and black step number text.
A step is completed only when the animated progress endpoint has moved beyond that step’s progress coordinate.
A step is current when the animated progress endpoint is exactly at that step’s progress coordinate.
A step is future when the animated progress endpoint has not reached that step’s progress coordinate.
During animation between two step coordinates, the previous reached step is completed and the next step remains future until the endpoint exactly reaches the next step coordinate.
Step numbers must be centered inside their circles.
Drawing order is: white background bar, orange active bar fill, bar borders, step circles, step numbers.
Resizing the view during animation must recompute step positions and the active bar endpoint from the current measured size.
Changing layout direction during animation must recompute step positions correctly.
setStepCount cancels any running animation. If the current visual progress or target step is greater than the new count, clamp it to the new last step.
Calling setCurrentStep with the same target as an already-running animation must not visually jump.
The implementation must not crash for very small widths or heights.
Drawing must be deterministic enough for Robolectric bitmap tests and Roborazzi screenshot tests.





## Tests

```text
1. [Robolectric] invalid step count
Call setStepCount(0) and setStepCount(1). Verify IllegalArgumentException.

2. [Robolectric] invalid current step
With stepCount = 4, call setCurrentStep(0), setCurrentStep(5), jumpToStep(0), and jumpToStep(5). Verify IllegalArgumentException.

3. [Robolectric/Bitmap] settled step 2
Set stepCount = 4 and jumpToStep(2). Verify step 1 is orange-filled, step 2 is white with orange outline, and steps 3/4 are white with black outline.

4. [Robolectric/Bitmap] fixed LTR geometry
At mdpi, set width to 120dp and stepCount = 4. Verify step leading edges are 0, 30, 60, and 90 px, and circle centers are 15, 45, 75, and 105 px.

5. [Robolectric/Roborazzi] forward animation start
Start at step 1, animate to step 3, capture at 0ms. Verify the active fill starts at step 1 and step 1 is current/outlined.

6. [Robolectric/Roborazzi] forward animation between steps
Start at step 1, animate to step 3, advance to 75ms. Verify active fill endpoint is halfway between step 1 and step 2. Step 1 is completed and step 2 is still future.

7. [Robolectric/Roborazzi] forward animation exact midpoint
Start at step 1, animate to step 3, advance to 150ms. Verify active fill endpoint is exactly at step 2. Step 1 is completed, step 2 is current/outlined, and step 3 is future.

8. [Robolectric/Roborazzi] forward animation complete
Start at step 1, animate to step 3, advance to 300ms. Verify active fill endpoint is at step 3. Steps 1 and 2 are completed, step 3 is current, and step 4 is future.

9. [Robolectric/Roborazzi] completed only after passing
Start at step 1, animate to step 3. Capture just before step 2, exactly at step 2, and just after step 2. Verify step 2 changes from future to current outline to completed fill.

10. [Robolectric/Roborazzi] backward animation
Start at step 4, animate to step 2, advance to 150ms. Verify the active fill endpoint moves backward and steps beyond the animated endpoint return to future/current states.

11. [Robolectric] interrupted animation continuity
Start at step 1, animate to step 4, advance to 150ms, then call setCurrentStep(2, animated = true). Verify the new animation starts from the currently rendered progress position without snapping.

12. [Robolectric] stale animation ignored
Start one animation, interrupt it with another, then advance time enough that the old animation would have completed. Verify the old animation does not update the visual state.

13. [Robolectric] repeated same target
Start animating to step 3, advance partially, then call setCurrentStep(3, animated = true) again. Verify no visual jump.

14. [Robolectric] jump cancels animation
Start animating to step 4, advance partially, then call jumpToStep(2). Verify the view immediately settles at step 2 and no later animation frame changes it.

15. [Robolectric] setCurrentStep animated false
Call setCurrentStep(3, animated = false). Verify it behaves exactly like jumpToStep(3).

16. [Robolectric/Roborazzi] RTL layout
Set layout direction to RTL. Verify step 1 is rightmost, active fill grows right-to-left, and numeric step states still follow step order.

17. [Robolectric/Roborazzi] RTL resize during animation
Start an RTL animation, advance halfway, resize the view, and redraw. Verify the active endpoint is recomputed from the new width.

18. [Robolectric] step count shrink
Start with stepCount = 5, jumpToStep(5), then call setStepCount(3). Verify visual progress and target step clamp to step 3.

19. [Robolectric] step count change cancels animation
Start animating, call setStepCount(4), then advance time. Verify no stale animation continues.

20. [Roborazzi] current outlined but not filled
Capture a settled current step. Verify the current circle has orange outline, white interior, and black number.

21. [Roborazzi] completed filled visual
Capture a state where earlier steps are completed. Verify completed circles are orange-filled with white numbers.

22. [Roborazzi] tiny view
Render the view with very small width and height. Verify no crash and deterministic output.

23. [Roborazzi] full mixed frame
Render stepCount = 5 at a between-step animation frame. Verify bar fill, completed steps, current/future states, borders, and text colors together.










