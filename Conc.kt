# Android Streaming Waveform Timeline View

## Context

A custom Android `View` renders a live audio waveform timeline. Samples may be appended from background threads while the UI thread is drawing, and callers may request an offscreen bitmap snapshot from any thread.

The visual output is simple: a gray center baseline with an orange waveform. The hard part is preserving waveform peaks while zoomed out, handling missing or evicted sample ranges, and producing consistent frames without races or deadlocks.

## Task

Implement `StreamingWaveformView`.

## Starter Code

```kotlin
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class StreamingWaveformView @JvmOverloads constructor(
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

    fun setViewport(firstSampleIndex: Long, samplesPerPixel: Float) {
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





Rendering Logic
Each appended sample receives an absolute sample index. The first sample after construction has index 0, the next has index 1, and so on.
A normalized sample is a Float where:
1f  = top of the waveform
0f  = center baseline
-1f = bottom of the waveform
The viewport decides which part of the timeline is visible:
firstSampleIndex = sample index at the left edge
samplesPerPixel = how many samples one horizontal pixel represents
When zoomed in, where samplesPerPixel < 1, retained samples are drawn as a connected orange line.
When zoomed out, where samplesPerPixel >= 1, each horizontal pixel column represents a range of samples. The view must draw the minimum and maximum sample value in that column as a vertical orange line. This preserves sharp peaks that would be lost by averaging or picking one sample.
Requirements
Use Android custom View drawing APIs, Android bitmap/canvas APIs, Kotlin standard library, and JDK concurrency primitives only. Do not use Compose, TextureView, SurfaceView, coroutines, RxJava, or third-party rendering libraries.
Public methods may be called from any thread.
The view stores at most the latest capacity samples, with an initial capacity of 1024. When capacity is exceeded, the oldest samples are discarded first.
setCapacity(sampleCount) requires sampleCount > 0; otherwise throw IllegalArgumentException.
appendSamples(samples) appends all provided samples as one complete operation. Empty arrays are no-ops. The input array must be copied before the call returns.
Sample values are clamped before storage: values below -1f become -1f, values above 1f become 1f, and NaN or infinite values become 0f.
setViewport(firstSampleIndex, samplesPerPixel) requires samplesPerPixel to be finite and greater than 0; otherwise throw IllegalArgumentException.
clear() removes all stored samples and resets the next appended sample index to 0.
Each call to appendSamples, setCapacity, setViewport, or clear must take effect all at once. Drawing or snapshotting must see either the state before the call or the state after the call, never a half-finished update.
onDraw and renderSnapshot must render from one immutable snapshot of samples and viewport values.
No method may hold an internal state lock while drawing to a Canvas, creating a Bitmap, calling invalidate, or posting to the main thread.
renderSnapshot(width, height) requires positive width and height; otherwise throw IllegalArgumentException.
renderSnapshot must render into a new bitmap, may be called from the main thread or a worker thread, must not call View.draw, must not wait for the main thread, and must not mutate view state.
Drawing uses the padded content bounds. If content width or height is zero, draw nothing except normal View background and do not crash.
Always draw a horizontal gray baseline through the vertical center of the content bounds.
For samplesPerPixel < 1, draw retained visible samples as a connected orange polyline. Missing, evicted, or future sample gaps must not be connected across.
For samplesPerPixel >= 1, each horizontal pixel column must draw the min/max retained samples in that column as a vertical orange line. Do not average, skip peaks, or pick only one sample.
Retained sample ranges outside the viewport are not drawn. Viewport ranges before the earliest retained sample or after the newest sample show only the baseline.
Drawing must be clipped to the padded content bounds.
Baseline color is #FFBDBDBD with 1dp stroke. Waveform color is #FFFF9800 with 2dp stroke, round cap, and round join.
For the same samples, viewport, dimensions, padding, density, and layout direction, output must be deterministic enough for Robolectric bitmap tests and Roborazzi screenshots.
The implementation must not deadlock under concurrent appendSamples, setCapacity, setViewport, clear, onDraw, and renderSnapshot calls.








```text
# Important Tests

1. [Robolectric] invalid inputs
Verify `setCapacity(0)`, `setCapacity(-1)`, invalid `samplesPerPixel`, and non-positive `renderSnapshot` sizes throw `IllegalArgumentException`.

2. [Robolectric/Bitmap] zoomed-in polyline geometry
Set `samplesPerPixel = 0.5`, append known samples, render a snapshot, and verify samples are drawn as a connected line at the expected left-to-right positions.

3. [Robolectric/Bitmap] zoomed-out peak preservation
Set `samplesPerPixel = 10`, put one sharp spike inside a 10-sample bucket, and verify that column draws the spike using min/max rather than averaging it away.

4. [Robolectric/Bitmap] missing ranges are not connected
Use a viewport that begins before the earliest retained sample and ends after the newest sample. Verify missing left/right ranges show only baseline and waveform segments are not connected across gaps.

5. [Robolectric] capacity eviction keeps absolute indices
Set capacity to 4, append more than 4 samples, then set a viewport covering old and retained indices. Verify evicted indices are absent and retained samples still appear at their absolute-index positions.

6. [Robolectric] capacity shrink/grow correctness
Append samples, shrink capacity, verify newest samples remain; then grow capacity and verify existing samples remain in order without inventing old samples.

7. [Robolectric] append copies and clamps input
Append an array containing out-of-range, NaN, and infinite values. Mutate the array after return. Verify rendered output uses the copied, clamped values.

8. [Robolectric] clear resets timeline
Append samples, call `clear()`, append new samples, and verify the new samples start again at index `0` and no old waveform remains.

9. [Robolectric] atomic append during snapshot
Append large recognizable blocks from different threads while repeatedly calling `renderSnapshot`. Verify snapshots contain whole completed blocks only, never half of one append call.

10. [Robolectric] viewport change during draw
Start drawing or snapshotting with one viewport while another thread changes the viewport. Verify the rendered bitmap corresponds to one complete viewport state, not a mix.

11. [Robolectric] renderSnapshot main-thread safe
Call `renderSnapshot` from the main thread under a timeout. Verify it returns without posting to the main thread and waiting.

12. [Robolectric] no deadlock under lock pressure
Stress `appendSamples`, `setCapacity`, `setViewport`, `clear`, `onDraw`, and `renderSnapshot` from multiple threads. Verify all calls complete and capacity is never exceeded.

13. [Robolectric/Bitmap] onDraw and renderSnapshot match
For the same state and dimensions, compare a bitmap produced by `view.draw(canvas)` with one from `renderSnapshot(width, height)`. Verify equivalent waveform geometry.

14. [Roborazzi] zoomed-out dense waveform visual
Render dense samples with spikes at different locations. Verify the orange min/max envelope preserves peaks around the gray baseline.

15. [Roborazzi] evicted-and-future viewport visual
Render a viewport containing evicted samples, retained samples, and future sample space. Verify only the retained middle region shows waveform and the rest shows baseline.
