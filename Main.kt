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




## Updated Requirements

1. Use Android custom `View` drawing APIs only. Do not use Compose, RecyclerView, MotionLayout, third-party animation libraries, or prebuilt progress/stepper widgets.

2. `stepCount` must be at least `2`. Calling `setStepCount(count)` with `count < 2` must throw `IllegalArgumentException`.

3. `currentStep` is 1-based. Calling `setCurrentStep` or `jumpToStep` with a step outside `1..stepCount` must throw `IllegalArgumentException`.

4. The background bar is white, `10dp` high, vertically centered, and spans the full measured width of the view.

5. The background bar has only a top and bottom black border. Each border is `0.5dp` thick.

6. Step circles have diameter `30dp`, are vertically centered on the bar, and have a `0.5dp` stroke.

7. Step circles are spaced at `30dp` intervals.

8. In LTR, step `n` has leading edge `(n - 1) * 30dp`.

9. In RTL, step positions are mirrored from the right edge of the view. Step `1` is rightmost, and its leading edge is the right edge of the view.

10. The progress coordinate of a step is the leading edge of that step’s circle, not the circle center.

11. The active/completed color is orange `#FFFF9800`.

12. The active bar fill is drawn over the white bar from step `1`’s leading edge to the current animated progress endpoint.

13. In LTR, the active bar grows left-to-right as progress increases. In RTL, it grows right-to-left as progress increases.

14. Animation duration is `300ms` per crossed step interval. Moving from step `2` to step `3` takes `300ms`; moving from step `1` to step `4` takes `900ms`.

15. Animation interpolation is linear over the full distance, with no start delay.

16. The animation may jump across multiple steps in one call. Intermediate steps must be passed through continuously, not skipped visually.

17. `jumpToStep(step)` immediately sets the visual progress to that step, cancels any running animation, and redraws.

18. `setCurrentStep(step, animated = false)` behaves the same as `jumpToStep(step)`.

19. `setCurrentStep(step, animated = true)` animates from the current rendered progress position, not from the last committed step.

20. If `setCurrentStep` is called while an animation is running, the old animation must be cancelled and must not produce any later visual update.

21. If an animation is interrupted by a new target step, the new animation starts from the currently rendered progress position without jumping.

22. Calling `setCurrentStep` with the same target as an already-running animation must not visually jump or restart from the committed step.

23. A completed step is drawn as an orange filled circle with white step number text.

24. A current step is drawn as a white circle with orange outline and black step number text.

25. A future step is drawn as a white circle with black outline and black step number text.

26. A step is completed only when the animated progress endpoint has moved beyond that step’s progress coordinate.

27. A step is current only when the animated progress endpoint is exactly at that step’s progress coordinate.

28. A step is future when the animated progress endpoint has not reached that step’s progress coordinate.

29. During a multi-step jump, intermediate steps become current only at the exact frame where the progress endpoint reaches their coordinate. They become completed as soon as the endpoint moves beyond them.

30. During backward animation, steps that are no longer reached must return from completed/current to future according to the same progress-coordinate rules.

31. Step numbers must be drawn centered inside their circles.

32. Text color rules are: completed step = white text; current step = black text; future step = black text.

33. Drawing order is: white background bar, orange active bar fill, bar borders, step circles, step numbers.

34. Resizing the view during animation must recompute step positions and the active bar endpoint from the current measured width and height.

35. Changing layout direction during animation must recompute step positions and active fill direction correctly.

36. `setStepCount` cancels any running animation. If the current visual progress or target step is greater than the new count, clamp it to the new last step.

37. The implementation must not crash for very small widths or heights.

38. Drawing must be deterministic enough for Robolectric bitmap tests and Roborazzi screenshot tests.




# Important Tests

1. [Robolectric] invalid step count
Call setStepCount(0) and setStepCount(1). Verify IllegalArgumentException.

2. [Robolectric] invalid current step
With stepCount = 4, call setCurrentStep(0), setCurrentStep(5), jumpToStep(0), and jumpToStep(5). Verify IllegalArgumentException.

3. [Robolectric/Bitmap] fixed LTR geometry
At mdpi, set width = 120dp and stepCount = 4. Verify step leading edges are 0, 30, 60, and 90 px, and centers are 15, 45, 75, and 105 px.

4. [Robolectric/Bitmap] settled step 2
Set stepCount = 4 and jumpToStep(2). Verify step 1 is orange-filled with white text, step 2 is white with orange outline and black text, and steps 3/4 are white with black outline and black text.

5. [Robolectric/Roborazzi] one-step animation timing
Start at step 2 and animate to step 3. Capture at 0ms, 150ms, and 300ms. Verify the orange bar endpoint moves linearly from step 2’s leading edge to step 3’s leading edge.

6. [Robolectric/Roborazzi] multi-step jump timing
Start at step 1 and animate to step 4. Since duration is 300ms per interval, total duration is 900ms. Capture at 300ms, 600ms, and 900ms. Verify the endpoint reaches steps 2, 3, and 4 respectively.

7. [Robolectric/Roborazzi] multi-step between intervals
Start at step 1 and animate to step 4. Capture at 450ms. Verify the endpoint is halfway between steps 2 and 3, steps 1 and 2 are completed, step 3 is future, and no step is incorrectly current.

8. [Robolectric/Roborazzi] completed only after passing
Start at step 1 and animate to step 3. Capture just before step 2, exactly at step 2, and just after step 2. Verify step 2 changes from future to current outline to completed fill.

9. [Robolectric/Roborazzi] forward animation completion
Start at step 1 and animate to step 3. At 600ms, verify the endpoint is at step 3, steps 1 and 2 are completed, step 3 is current/outlined, and step 4 is future.

10. [Robolectric/Roborazzi] backward one-step animation
Start at step 4 and animate to step 3. Capture at 150ms. Verify the active fill endpoint moves backward linearly and step 4 is no longer completed once progress moves before it.

11. [Robolectric/Roborazzi] backward multi-step jump
Start at step 5 and animate to step 2. Capture at 300ms, 600ms, and 900ms. Verify the endpoint reaches steps 4, 3, and 2 in reverse, and steps beyond the endpoint return to future/current states.

12. [Robolectric] interrupted animation continuity
Start at step 1, animate to step 5, advance to 450ms, then call setCurrentStep(2, animated = true). Verify the new animation starts from the currently rendered progress position without snapping to step 1 or step 5.

13. [Robolectric] stale animation ignored
Start one animation, interrupt it with another, then advance time enough that the old animation would have completed. Verify the old animation does not update the visual state.

14. [Robolectric] repeated same target
Start animating to step 4, advance partially, then call setCurrentStep(4, animated = true) again. Verify there is no visual jump or restart from the committed step.

15. [Robolectric] jump cancels animation
Start animating to step 5, advance partially, then call jumpToStep(2). Verify the view immediately settles at step 2 and no later animation frame changes it.

16. [Robolectric] animated false equals jump
Call setCurrentStep(3, animated = false). Verify it behaves exactly like jumpToStep(3): no animation, immediate redraw, and previous animation cancelled.

17. [Robolectric/Roborazzi] RTL geometry
Set layout direction to RTL. Verify step 1 is rightmost, stepCount is leftmost, and step leading edges are mirrored from the right edge of the view.

18. [Robolectric/Roborazzi] RTL active fill
In RTL, start at step 1 and animate to step 3. Verify the orange fill grows from right to left and state rules still follow numeric step order.

19. [Robolectric/Roborazzi] resize during animation
Start an animation, advance halfway, resize the view, and redraw. Verify step positions and active endpoint are recomputed from the new measured width.

20. [Robolectric/Roborazzi] layout direction change during animation
Start an animation in LTR, advance halfway, switch to RTL, and redraw. Verify the endpoint and step circles are mirrored correctly without stale LTR coordinates.

21. [Robolectric] step count shrink
Start with stepCount = 5, jumpToStep(5), then call setStepCount(3). Verify visual progress and target step clamp to step 3.

22. [Robolectric] step count change cancels animation
Start animating, call setStepCount(4), then advance time. Verify no stale animation continues.

23. [Roborazzi] current outlined but not filled
Capture a settled current step. Verify the current circle has orange outline, white interior, and black number.

24. [Roborazzi] completed filled visual
Capture a state where earlier steps are completed. Verify completed circles are orange-filled with white numbers.

25. [Roborazzi] future state visual
Capture a settled earlier step. Verify future circles are white with black outline and black numbers.

26. [Roborazzi] bar and border dimensions
At mdpi, verify the background bar is 10px high, spans the full view width, and has 0.5dp-equivalent top and bottom borders.

27. [Roborazzi] tiny view stability
Render the view with very small width and height. Verify no crash and deterministic output.

28. [Roborazzi] full mixed frame
Render stepCount = 5 during a between-step animation frame. Verify the white bar, orange fill, borders, completed/current/future circles, and text colors together.
