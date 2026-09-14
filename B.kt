Step circles have outer diameter 30dp, are vertically centered on the bar, and have a 0.5dp stroke drawn fully inside the circle bounds. The stroke centerline is inset by half the stroke width from the outer radius.



Use one concise requirement:
During animation, completed/current/future states must be derived only from the current animated progress endpoint. The target step is not automatically current while the animation is running.
Then keep the existing coordinate rules:
A step is completed only when the endpoint has moved beyond that step’s progress coordinate.

A step is current only when the endpoint is exactly at that step’s progress coordinate.

A step is future when the endpoint has not reached that step’s progress coordinate.
Add these tests:
[Robolectric/Roborazzi] forward target is not current while in flight
Start at step 1 and animate to step 4. At 100ms, verify the endpoint is at step 2, step 2 is current, step 3 is future, and step 4 is future, not current.

[Robolectric/Roborazzi] backward target is not current while in flight
Start at step 5 and animate to step 2. At 100ms, verify the endpoint is at step 4, step 4 is current, step 3 is completed, and step 2 is completed, not current.
