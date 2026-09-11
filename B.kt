[Robolectric/Roborazzi] multi-step jump timing
Start at step 1 and animate to step 4. Total duration is 300ms. Capture at 100ms, 200ms, and 300ms. Verify the endpoint reaches steps 2, 3, and 4 respectively.

[Robolectric/Roborazzi] multi-step between intervals
Start at step 1 and animate to step 4. Capture at 150ms. Verify the endpoint is halfway between steps 2 and 3, steps 1 and 2 are completed, step 3 is future, and no step is incorrectly current.

[Robolectric/Roborazzi] backward multi-step jump
Start at step 5 and animate to step 2. Total duration is 300ms. Capture at 100ms, 200ms, and 300ms. Verify the endpoint reaches steps 4, 3, and 2 in reverse.





Also update this existing test:
[Robolectric/Roborazzi] forward animation completion
Start at step 1 and animate to step 3. At 300ms, verify the endpoint is at step 3, steps 1 and 2 are completed, step 3 is current/outlined, and step 4 is future.
