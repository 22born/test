The three I would actually add
For maximum difficulty without making the exercise artificially complicated:
#6 — Exact debounce-boundary race: Tests event ordering and coroutine cancellation guarantees.
#7 — Atomic content measurement: Tests Compose's measurement and state consistency.
#1 — Unchanged committed target: Tests whether the candidate distinguishes user intent, committed state, and rendered animation state.




1. Debounce must not restart for an unchanged content target
   - If expansion is already committed and the content is animating toward expanded, tapping collapse and then expand again within 500ms must not restart the expansion animation.
   - The content must continue toward its existing target without resetting its animation progress.

2. Debounce expiration during an active height animation
   - If a new 500ms debounce expires while the previous 800ms height animation is still running, the new animation must start from the current rendered height.
   - No snapping, queued animations, or waiting for the previous animation to finish is allowed.

3. No invisible text may affect parent measurement
   - During expansion, only the currently visible portion of text may contribute to the component's measured height.
   - The parent must never reserve space for hidden lines, including during intermediate animation frames.

4. Expansion must work under changing font scale
   - If system font scale changes from 1.0 to 1.5 while expansion is running, the content must remeasure correctly.
   - The visible text must maintain correct line clipping.
   - The height must transition to the new measured target without jumping.

5. Expansion cannot reveal partially laid-out text
   - While height is animating, text wrapping and glyph positions must remain identical to the final expanded layout.
   - Only the clipping boundary may move.
   - Text must not reflow or shift during expansion.

6. A tap exactly at the 500ms debounce boundary
   - If a collapse tap occurs at the exact frame when the expansion debounce expires, the collapse intent takes precedence.
   - The expansion animation must not start, even for one frame.
   - Behavior must be deterministic regardless of coroutine scheduling order.

7. No stale measurement after content replacement
   - When content changes during expansion, the new text must be measured using the current width, density, font scale, and text style before committing a new height target.
   - No frame may display the new text using the previous text's height measurement.

8. Height animation must be frame-rate independent
   - Given identical input events and elapsed animation time, the rendered viewport height must be the same at 60Hz, 90Hz, and 120Hz, within pixel-rounding tolerance.
   - Dropped frames must not extend the nominal 800ms animation duration.
