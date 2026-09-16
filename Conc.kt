## Rendering Logic

The view displays audio samples as a horizontal waveform.

The drawing area is the padded content bounds of the view. A gray baseline is drawn horizontally through the vertical center of that area.

Each sample is a normalized float value:

- `1f` is drawn at the top of the content bounds
- `0f` is drawn on the center baseline
- `-1f` is drawn at the bottom of the content bounds

The latest samples are drawn from left to right. The oldest visible sample is placed at the left edge of the content bounds, and the newest visible sample is placed at the right edge.

If there are `n >= 2` samples, sample `i` is positioned as:

x = contentLeft + i * contentWidth / (n - 1)

y = centerY - sample[i] * contentHeight / 2

The waveform is drawn as one connected orange polyline through those points. If there are fewer than two samples, only the baseline is drawn.



The visual output is an oscilloscope-like waveform: a gray center baseline with an orange connected line showing the latest normalized samples from oldest on the left to newest on the right.



# Android Concurrent Waveform Snapshot View

## Context

A custom Android `View` renders a live waveform from floating-point audio samples. Samples may be appended from background threads while the UI thread is drawing, and callers may also request an offscreen bitmap snapshot from any thread.

The rendering is visually simple: a centered baseline plus a waveform polyline. The hard part is producing consistent frames without data races, corrupted buffers, or deadlocks.

## Task

Implement `ConcurrentWaveformView`.

## Starter Code

```kotlin
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class ConcurrentWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun appendSamples(samples: FloatArray) {
        TODO("Implement")
    }

    fun setCapacity(sampleCount: Int) {
        TODO("Implement")
    }

    fun clear() {
        TODO("Implement")
    }

    fun renderSnapshot(width: Int, height: Int): Bitmap {
        TODO("Implement")
    }

    override fun onDraw(canvas: Canvas) {
        TODO("Implement")
    }
}






Requirements
Use Android custom View drawing APIs, Android bitmap/canvas APIs, Kotlin standard library, and JDK concurrency primitives only. Do not use Compose, TextureView, SurfaceView, coroutines, RxJava, or third-party rendering libraries.
Public methods may be called from any thread.
The initial sample capacity is 1024.
setCapacity(sampleCount) requires sampleCount > 0; otherwise throw IllegalArgumentException.
The view stores at most the latest capacity samples. Older samples are discarded first.
appendSamples(samples) appends all provided samples atomically. Empty arrays are no-ops.
appendSamples must copy input values during the call. Mutating the caller’s array after return must not affect the view.
Sample values are normalized to [-1f, 1f]. Values below -1f clamp to -1f; values above 1f clamp to 1f; NaN and infinite values are treated as 0f.
clear() atomically removes all samples.
Mutating operations are linearizable: each completed appendSamples, setCapacity, and clear must appear to take effect as one complete operation, never partially.
onDraw must render from one immutable sample snapshot. A frame must never contain a mix of old and new buffer contents.
onDraw must not hold internal state locks while calling Canvas drawing APIs.
No method may hold an internal state lock while calling invalidate, postInvalidate, postInvalidateOnAnimation, or any other main-thread posting API.
onDraw must not block waiting for background work or another render call.
renderSnapshot(width, height) may be called from the main thread or a worker thread.
renderSnapshot(width, height) requires positive width and height; otherwise throw IllegalArgumentException.
renderSnapshot must render into a new Bitmap using an immutable sample snapshot. It must not call View.draw, must not wait for the main thread, and must not mutate view state.
For the same sample snapshot and same dimensions, onDraw and renderSnapshot must produce equivalent waveform geometry.
Drawing uses the padded content bounds. If the content width or height is zero, draw nothing beyond the normal View background and do not crash.
Draw a horizontal baseline through the vertical center of the content bounds.
Draw the waveform as one connected polyline over the latest samples, oldest sample on the left and newest sample on the right.
If there are fewer than two samples, draw the baseline only.
For n >= 2 samples, sample i has:
x = contentLeft + i * contentWidth / (n - 1)
y = centerY - sample[i] * contentHeight / 2
Drawing must be clipped to the padded content bounds.
Baseline color is #FFBDBDBD, baseline stroke width is 1dp.
Waveform color is #FFFF9800, waveform stroke width is 2dp, stroke cap is round, and stroke join is round.
The implementation must be deterministic enough for Robolectric bitmap tests and Roborazzi screenshot tests.
The implementation must not deadlock under concurrent appendSamples, setCapacity, clear, onDraw, and renderSnapshot calls.







```text id="sxko17"
# Important Tests

1. [Robolectric] invalid capacity
Call setCapacity(0) and setCapacity(-1). Verify IllegalArgumentException.

2. [Robolectric] invalid snapshot size
Call renderSnapshot(0, 100), renderSnapshot(100, 0), and renderSnapshot(-1, 100). Verify IllegalArgumentException.

3. [Robolectric/Bitmap] empty buffer draws baseline only
Create an empty view, render a snapshot, and verify only the centered gray baseline is drawn.

4. [Robolectric/Bitmap] fewer than two samples draws no waveform
Append one sample and render. Verify baseline appears but no waveform polyline is drawn.

5. [Robolectric/Bitmap] known sample geometry
Append [-1f, 0f, 1f] and render 100x100 with no padding. Verify the waveform points map to bottom-left, center, and top-right.

6. [Robolectric/Bitmap] padding respected
Apply padding and render known samples. Verify baseline and waveform are drawn inside padded content bounds, not full view bounds.

7. [Robolectric] sample clamping
Append [-2f, -1f, 0f, 1f, 2f, NaN, POSITIVE_INFINITY]. Verify rendered y positions correspond to [-1, -1, 0, 1, 1, 0, 0].

8. [Robolectric] capacity keeps latest samples
Set capacity to 3, append [1, 2, 3, 4, 5] as distinguishable normalized values. Verify only the latest 3 samples are rendered.

9. [Robolectric] capacity shrink keeps newest
Append several samples, shrink capacity, and verify the newest samples remain while older samples are discarded.

10. [Robolectric] capacity grow preserves existing samples
Append samples, grow capacity, and verify existing samples remain in order.

11. [Robolectric] append copies input array
Append an array, mutate the array after appendSamples returns, then render. Verify the rendered waveform uses the original appended values.

12. [Robolectric] clear removes all samples
Append samples, call clear, render, and verify only the baseline remains.

13. [Robolectric] append is atomic
Use two threads appending large arrays while snapshots are taken. Verify each snapshot contains complete append blocks only, never a partial block.

14. [Robolectric] clear is atomic against append
Race clear with appendSamples. After both return, verify the final buffer is either cleared-before-append or append-before-clear according to one valid linearized order, never corrupted or partially cleared.

15. [Robolectric] setCapacity is atomic against append
Race setCapacity with appendSamples. Verify the final buffer obeys one valid operation order and never exceeds capacity.

16. [Robolectric] onDraw uses stable snapshot
Make onDraw take a snapshot, then append more samples while drawing is in progress. Verify the drawn frame uses one complete snapshot and does not mix old and new samples.

17. [Robolectric] renderSnapshot uses stable snapshot
Start renderSnapshot while another thread appends and clears. Verify the returned bitmap represents one consistent snapshot and does not crash.

18. [Robolectric] renderSnapshot from main thread does not deadlock
Call renderSnapshot on the main thread under a timeout. Verify it returns without posting to the main thread and waiting.

19. [Robolectric] renderSnapshot from worker during draw does not deadlock
Run onDraw on the main thread while a worker calls renderSnapshot. Verify both complete under timeout.

20. [Robolectric] append during renderSnapshot does not block indefinitely
Run a large renderSnapshot while another thread appends repeatedly. Verify neither operation deadlocks.

21. [Robolectric] no lock while invalidating
Stress appendSamples, clear, and setCapacity from worker threads while the main thread processes invalidations. Verify no deadlock or lock-order inversion.

22. [Robolectric] renderSnapshot does not mutate view state
Render a snapshot, then draw the view. Verify the view’s stored samples and later output are unchanged by renderSnapshot.

23. [Robolectric/Bitmap] onDraw and renderSnapshot match
For the same samples and dimensions, compare a bitmap produced by view.draw(canvas) with renderSnapshot(width, height). Verify equivalent baseline and waveform geometry.

24. [Robolectric/Bitmap] clipping to content bounds
Append samples that map to top and bottom extremes. Verify strokes are clipped to the padded content bounds.

25. [Robolectric] deterministic output
Run the same append sequence after clear twice and compare renderSnapshot outputs. Verify identical bitmaps.

26. [Robolectric] concurrent snapshot stress
Run many threads calling appendSamples, clear, setCapacity, and renderSnapshot. Verify no exceptions, no deadlocks, revealable state remains valid, and capacity is never exceeded.

27. [Roborazzi] sine-like waveform visual
Append smooth sine-like samples and capture the view. Verify a continuous orange waveform around the centered gray baseline.

28. [Roborazzi] jagged waveform visual
Append alternating high/low samples and capture. Verify the polyline is connected, clipped, and deterministic.

29. [Roborazzi] clear visual
Capture after samples are drawn, then clear and capture again. Verify the waveform disappears and the baseline remains.

30. [Roborazzi] padding visual
Capture with large padding. Verify waveform and baseline stay inside the padded content area.


