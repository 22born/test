# Android Scratch Card Reveal View

## Context

A custom Android `View` is needed to behave like a scratch card overlay. The coupon/content is placed behind the view by the parent layout. The scratch card view draws an opaque cover above it. When the user rubs over the view, the touched cover pixels are erased, making the content underneath visible through transparent scratched areas.

This task tests deterministic custom drawing, touch-stream handling, bitmap masking, reveal-progress accounting, and threshold completion.

## Task

Implement `ScratchCardView`.

## Starter Code

```kotlin
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ScratchCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun setCoverColor(color: Int) {
        TODO("Implement")
    }

    fun setRevealThreshold(percent: Float) {
        TODO("Implement")
    }

    fun getRevealPercent(): Float {
        TODO("Implement")
    }

    fun setOnRevealCompleteListener(listener: (() -> Unit)?) {
        TODO("Implement")
    }

    fun reset() {
        TODO("Implement")
    }

    fun revealAll() {
        TODO("Implement")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        TODO("Implement")
    }
}





Requirements
Use Android custom View drawing APIs only. Do not use Compose, TextureView, SurfaceView, WebView, prebuilt scratch-card widgets, or third-party drawing libraries.
The view draws only the scratch cover overlay. Coupon/content drawing is outside this view.
The default cover color is opaque gray #FFBDBDBD.
setCoverColor(color) changes the cover color for unscratched areas and redraws without resetting the scratched mask.
Scratched areas must be transparent, so content behind the view can show through.
Scratch stroke radius is 12dp.
Scratch strokes use round caps and round joins.
ACTION_DOWN starts the active scratch path and erases at the down point immediately.
ACTION_MOVE erases a continuous stroke segment from the previous accepted point to the current point. It must not erase only isolated circles at move points.
ACTION_UP ends the active scratch path.
ACTION_CANCEL ends the active scratch path without restoring already erased pixels.
Touch coordinates outside the view bounds are clipped to the view bounds. Scratching must never modify pixels outside the current view area.
Multi-touch uses only the first active pointer from ACTION_DOWN. Secondary pointers are ignored until the active pointer ends or is cancelled.
If the active pointer ends, the scratch gesture ends. The implementation must not silently switch to another pointer mid-stroke.
Reveal percent is the percentage of cover pixels erased within the current view bounds.
Overlapping scratches must not increase reveal percent more than once for the same already-erased pixels.
getRevealPercent() returns a value in [0f, 100f].
If the view has zero width or zero height, getRevealPercent() returns 0f unless the card has been fully revealed.
setRevealThreshold(percent) accepts only values in [0f, 100f]; otherwise it throws IllegalArgumentException.
When reveal percent becomes greater than or equal to the threshold, the card automatically reveals all remaining cover.
revealAll() makes the full cover transparent, sets reveal percent to 100f, redraws, and ignores later scratch gestures until reset().
reset() restores the full opaque cover, clears all scratch paths, sets reveal percent to 0f, and allows scratching again.
The reveal-complete listener fires exactly once per reset cycle when the card reaches full reveal by scratching or by threshold completion.
Calling revealAll() multiple times in the same reset cycle must not fire the listener more than once.
Calling reset() after completion allows the listener to fire again in the next cycle.
On size change, partial scratch state is discarded and reveal percent resets to 0f.
On size change after full reveal, the card remains fully revealed.
Drawing must be deterministic on a software Canvas, so Robolectric bitmap tests and Roborazzi screenshot tests can sample the result.
The implementation must not crash for zero-size or very small views.






```text id="j5ofpb"
# Important Tests

1. [Robolectric] default state
Create the view, measure/layout it, and draw. Verify the cover is opaque gray and `getRevealPercent()` is `0f`.

2. [Robolectric] invalid threshold
Call `setRevealThreshold(-1f)` and `setRevealThreshold(101f)`. Verify `IllegalArgumentException`.

3. [Robolectric/Bitmap] down erases immediately
Send `ACTION_DOWN` at the center. Verify pixels around the touched point become transparent and reveal percent becomes greater than `0`.

4. [Robolectric/Bitmap] move erases continuous segment
Send `ACTION_DOWN` at x=20, then `ACTION_MOVE` to x=120. Verify pixels along the middle of the segment are erased, not only the endpoints.

5. [Robolectric] overlapping scratch is not double-counted
Scratch the same path twice. Verify reveal percent after the second pass does not increase except for tolerance caused by edge antialiasing.

6. [Robolectric/Bitmap] clipping outside bounds
Scratch from outside the left edge to outside the right edge. Verify no crash, no pixels outside the bitmap are accessed, and erased pixels are clipped to the view bounds.

7. [Robolectric] action cancel preserves erased pixels
Start scratching, move once, then send `ACTION_CANCEL`. Verify already erased pixels remain erased and later move events without a new down do not continue the old path.

8. [Robolectric] up ends scratch path
Scratch and send `ACTION_UP`. Then send another `ACTION_MOVE` without a new down. Verify no additional pixels are erased.

9. [Robolectric] secondary pointer ignored
Start with pointer 0, add pointer 1, move pointer 1 only. Verify pointer 1 does not erase while pointer 0 is active.

10. [Robolectric] active pointer up ends gesture
Start with pointer 0, add pointer 1, lift pointer 0. Verify the gesture ends and does not switch to pointer 1.

11. [Robolectric] reveal percent range
After several scratches, verify `getRevealPercent()` is always between `0f` and `100f`.

12. [Robolectric] threshold auto reveal
Set a low reveal threshold, scratch enough area to cross it, and verify the full cover becomes transparent and reveal percent becomes `100f`.

13. [Robolectric] threshold listener fires once
Set a threshold and listener. Scratch past the threshold, then continue scratching. Verify the listener fires exactly once.

14. [Robolectric] revealAll behavior
Call `revealAll()`. Verify the full cover is transparent, reveal percent is `100f`, and later touch events do not change state before reset.

15. [Robolectric] revealAll listener idempotency
Call `revealAll()` twice. Verify the complete listener fires at most once in that reset cycle.

16. [Robolectric] reset after partial scratch
Scratch part of the cover, call `reset()`, and verify the cover is fully restored and reveal percent returns to `0f`.

17. [Robolectric] reset after completion
Complete the reveal, call `reset()`, scratch past threshold again, and verify the listener can fire again.

18. [Robolectric] cover color change preserves scratches
Scratch an area, call `setCoverColor(Color.RED)`, redraw, and verify unscratched areas change color while scratched areas remain transparent.

19. [Robolectric] partial resize resets scratch state
Scratch partially, change view size, and verify reveal percent resets to `0f` and the new cover is fully opaque.

20. [Robolectric] resize after full reveal
Call `revealAll()`, resize the view, and verify the card remains fully revealed.

21. [Robolectric/Bitmap] tiny view stability
Render zero-size and very small views. Verify no crash and deterministic output.

22. [Robolectric/Bitmap] deterministic gesture output
Run the same scratch gesture sequence twice after reset and verify the resulting bitmap and reveal percent match.

23. [Roborazzi] partial diagonal scratch
Capture a card after a diagonal drag. Verify a continuous transparent scratched trail over the opaque cover.

24. [Roborazzi] threshold completed visual
Capture after threshold completion. Verify the cover is fully gone.

25. [Roborazzi] reset visual
Capture after scratch, then reset, then capture again. Verify the cover is restored.
