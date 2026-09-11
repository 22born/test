Animation duration is exactly 300ms total. Animation progress is sampled from `SystemClock.uptimeMillis()` and must be correct for arbitrary main-looper clock times.









Robolectric/Bitmap] completed/current/future colors
At xhdpi, set stepCount = 4 and jumpToStep(3). Verify:
- steps 1 and 2 have orange-filled interiors
- step 3 has white interior and orange outline
- step 4 has white interior and black outline
- completed step text is white
- current/future step text is black

[Robolectric/Bitmap] bar colors
At xhdpi, jumpToStep(3). Verify the active bar segment is orange and the remaining background bar is white.

[Robolectric/Bitmap] bar border placement
At xhdpi, verify the bar has black top and bottom borders and no left/right border.

[Roborazzi] visual state regression
Capture a settled mixed state with completed, current, and future steps visible. Verify colors, borders, text, and fill state together.

[Roborazzi] completion timing visual
Animate from step 1 to step 3. Capture just before step 2, exactly at step 2, and just after step 2. Verify step 2 changes future → current outline → completed fill.



Yes, that is a real issue. The prompt/tests should be updated so animation progress is clock-derived, not callback-derived.
The requirement should not merely say “use a 300ms linear animation.” It should say exactly how sampled frames are determined.
Requirement update
Replace the animation timing requirements with these:
Animation duration is exactly 300ms total for every animated step change, regardless of how many step intervals are crossed.

For a running animation, the rendered visual progress must be derived from the main looper clock:

fraction = clamp((currentMainLooperTime - animationStartTime) / 300ms, 0f, 1f)

visualProgress = lerp(startVisualProgress, targetStepProgress, fraction)

The implementation must not derive progress from frame count, accumulated deltas, callback count, or the last delivered animator callback.
Add this too:
If the view is drawn at an arbitrary sampled time during a running animation, it must render the position for that sampled main-looper time, even if no animation frame callback happened exactly at that timestamp.
This makes Roborazzi/Robolectric tests fair: they can advance the main looper clock to 100ms, force a draw, and expect the one-third position.
Test wording update
Change tests from:
Advance to 100ms and verify...
to:
Start the animation at main-looper time T. Advance the main looper clock to T + 100ms, force layout/draw or capture with Roborazzi, and verify the rendered endpoint equals the clock-derived one-third position.
For multi-step jump:
Start at step 1 and animate to step 4. Total duration is 300ms. Sample using the main looper clock:

T + 100ms -> progress is one third of the total distance, endpoint at step 2
T + 200ms -> progress is two thirds, endpoint at step 3
T + 300ms -> progress complete, endpoint at step 4



Requirement updates
Replace old Requirement 14:
Animation duration is 300ms per crossed step interval. Moving from step 2 to step 3 takes 300ms; moving from step 1 to step 4 takes 900ms.
with:
Animation duration is exactly 300ms total for every animated step change, regardless of how many step intervals are crossed. Moving from step 2 to step 3 takes 300ms; moving from step 1 to step 4 also takes 300ms.
Keep this requirement:
The animation may jump across multiple steps in one call. Intermediate steps must be passed through continuously, not skipped visually.
Update the interpolation requirement to:
Animation interpolation is linear over the full start-to-target distance, with no start delay. For a multi-step jump, intermediate step coordinates are reached proportionally within the same 300ms duration.
