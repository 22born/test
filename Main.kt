1. Collapsed chevron geometry
   - Center point is exactly 4dp below the endpoint line.
   - Left/right endpoints are exactly 6dp from center.
   - Stroke width is 2dp.

2. Chevron midpoint
   - Tap to expand.
   - At exactly 150ms, center point is on the endpoint line.
   - Chevron appears as a flat horizontal line.

3. Chevron animation completion
   - Tap to expand.
   - At exactly 300ms, center point is 4dp above the endpoint line.
   - Chevron is fully pointing up.

4. Chevron reverses without snapping
   - Tap to expand.
   - Advance 100ms and record current center-point Y.
   - Tap again.
   - Verify the animation continues from the currently rendered Y with no geometry jump.
   - Verify it then moves toward the collapsed chevron.

5. Content remains collapsed before debounce
   - Tap to expand.
   - Advance 499ms.
   - Content must still have the collapsed 2-line window.

6. Content expansion starts after 500ms
   - Tap to expand.
   - Advance exactly 500ms.
   - Content expansion should begin.
   - Height should animate toward the 7-line window.

7. Fast taps reset the 500ms debounce
   - Tap to expand.
   - Advance 400ms.
   - Tap to collapse.
   - Advance 499ms.
   - Content must not change state.
   - Content may change only after 500ms from the latest tap.

8. Content height animation takes 800ms
   - After the 500ms debounce completes, start measuring the content animation.
   - At 0ms, height is the collapsed height.
   - During the 800ms animation, height is between collapsed and expanded heights.
   - At exactly 800ms, height is the 7-line expanded height.
   - Verify equivalent behavior when collapsing.

9. Arrow and content animations are independent
   - Tap to expand.
   - At 300ms, chevron must be fully pointing up.
   - Content must still be collapsed because the 500ms debounce has not completed.

10. Expansion control visibility
    - For text requiring <= 2 lines, "Show more" and chevron are not displayed.
    - For text requiring > 2 lines, "Show more" and chevron are displayed.
    - Collapsed content shows at most 2 lines.
    - Expanded content shows at most 7 lines.



import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.StrokeCap
import androidx.compose.ui.layout.onTextLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ExpandComponent(
    contentText: String,
    modifier: Modifier = Modifier,
) {
    /*
     * targetExpanded:
     *     Changes immediately on every tap. Drives the arrow.
     *
     * contentExpanded:
     *     Changes only after targetExpanded has remained stable for 500 ms.
     *     Drives the content height/maxLines.
     */
    var targetExpanded by remember(contentText) {
        mutableStateOf(false)
    }
    var contentExpanded by remember(contentText) {
        mutableStateOf(false)
    }

    /*
     * Determine whether the complete text requires more than two lines.
     *
     * We let Text perform normal layout without maxLines initially and
     * inspect the resulting line count. Once known, the actual visible
     * window is constrained below.
     */
    var hasMoreThanTwoLines by remember(contentText) {
        mutableStateOf<Boolean?>(null)
    }

    /*
     * Debounce the content state.
     *
     * When targetExpanded changes, Compose cancels the coroutine belonging
     * to the previous LaunchedEffect and launches this one. Therefore rapid
     * taps continually restart the 500 ms delay.
     */
    LaunchedEffect(targetExpanded) {
        delay(500)
        contentExpanded = targetExpanded
    }

    /*
     * 10sp text with a fixed line height makes the requested 2-line and
     * 7-line windows deterministic.
     *
     * Using 12sp here gives:
     *   collapsed = 24sp high
     *   expanded  = 84sp high
     *
     * The requirement specifies 10sp text but not a separate line height,
     * so a fixed 12sp line height provides a practical readable window.
     */
    val contentStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp
    )

    val density = LocalDensity.current

    val collapsedHeight = with(density) {
        (contentStyle.lineHeight * 2).toDp()
    }
    val expandedHeight = with(density) {
        (contentStyle.lineHeight * 7).toDp()
    }

    val animatedContentHeight by animateDpAsState(
        targetValue = if (contentExpanded) {
            expandedHeight
        } else {
            collapsedHeight
        },
        animationSpec = tween(durationMillis = 800),
        label = "ExpandableContentHeight"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.Black
            )
            .padding(8.dp)
    ) {
        /*
         * Before line count is known we allow Text to lay itself out so
         * onTextLayout can determine whether the control is necessary.
         *
         * Afterwards its visible viewport is animated between exactly
         * two and seven line heights.
         */
        if (hasMoreThanTwoLines == null) {
            Text(
                text = contentText,
                style = contentStyle,
                modifier = Modifier.fillMaxWidth(),
                onTextLayout = { result ->
                    hasMoreThanTwoLines = result.lineCount > 2
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        if (hasMoreThanTwoLines == true) {
                            animatedContentHeight
                        } else {
                            collapsedHeight
                        }
                    )
            ) {
                Text(
                    text = contentText,
                    style = contentStyle,
                    maxLines = if (contentExpanded) 7 else 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (hasMoreThanTwoLines == true) {
            Spacer(Modifier.height(8.dp))

            /*
             * One clickable Row means both "Show more" and the chevron
             * share exactly the same click listener.
             */
            Row(
                modifier = Modifier.clickable {
                    targetExpanded = !targetExpanded
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show more",
                    fontSize = 20.sp
                )

                Spacer(Modifier.width(6.dp))

                ChevronMorph(
                    expanded = targetExpanded
                )
            }
        }
    }
}

@Composable
private fun ChevronMorph(
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    /*
     * Only the centre Y coordinate is animated.
     *
     * collapsed: +4dp -> V/down chevron
     * midpoint:   0dp -> horizontal line
     * expanded:  -4dp -> ^/up chevron
     *
     * animateFloatAsState starts a new animation from the CURRENT animated
     * value whenever the target changes. Consequently a rapid tap reverses
     * the geometry smoothly rather than snapping to either endpoint.
     */
    val centerYOffsetDp by animateFloatAsState(
        targetValue = if (expanded) -4f else 4f,
        animationSpec = tween(
            durationMillis = 300,
            easing = LinearEasing
        ),
        label = "ChevronCenterY"
    )

    val density = LocalDensity.current

    Canvas(
        modifier = modifier.size(
            width = 16.dp,
            height = 12.dp
        )
    ) {
        val centerX = size.width / 2f
        val endpointY = size.height / 2f

        val horizontalDistance = with(density) {
            6.dp.toPx()
        }

        val centerYOffset = with(density) {
            centerYOffsetDp.dp.toPx()
        }

        val strokeWidth = with(density) {
            2.dp.toPx()
        }

        val left = Offset(
            x = centerX - horizontalDistance,
            y = endpointY
        )

        val center = Offset(
            x = centerX,
            y = endpointY + centerYOffset
        )

        val right = Offset(
            x = centerX + horizontalDistance,
            y = endpointY
        )

        drawLine(
            color = Color.Black,
            start = left,
            end = center,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )

        drawLine(
            color = Color.Black,
            start = center,
            end = right,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}




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
