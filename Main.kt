1. Expansion must preserve velocity across interrupted animations
   - Start expanding from 2 to 7 lines over 800ms.
   - At 320ms into expansion, request collapse.
   - After the 500ms debounce, reverse toward 2 lines.
   - At the reversal instant, both height and its first derivative
     (velocity) must be continuous.
   - No zero-velocity restart, snapping, or overshoot is allowed.
   - The reversed motion must still reach its target in 800ms.

2. Expansion must be independent of parent layout constraints
   - Start expansion at width W1.
   - At 350ms into expansion, change parent width to W2.
   - At the same instant, change the font scale.
   - The content must reflow for W2 immediately.
   - The viewport must preserve its current physical height.
   - It must then animate to the newly measured target without
     restarting its animation clock.
   - The final height must match the new text layout.

3. Expansion must preserve the visible glyph boundary
   - Start expanding a paragraph containing mixed font sizes,
     combining characters, emoji, and bidirectional text.
   - During expansion, change the content while retaining
     a common text prefix.
   - The last fully visible glyph before the update must remain
     at exactly the same screen coordinate afterward.
   - Text below that glyph must adopt the new layout.
   - No previously visible glyph may disappear during the update.

4. Expansion must survive removal and reinsertion
   - Start expanding.
   - Remove the composable from the composition at 370ms.
   - Reinsert it 200ms later with the same logical identity.
   - The content must resume at the height and animation progress
     it would have reached if it had never been removed.
   - The 500ms debounce must also preserve its original deadline.
   - No wall-clock timers or externally running animation engines
     are permitted.

5. Expansion must support a moving height target
   - Begin expanding toward a 7-line height.
   - During the animation, asynchronously replace the content
     with text requiring 3 lines, then 6 lines, then 4 lines.
   - Each replacement changes the destination height.
   - The viewport must continuously approach the latest target.
   - Height and velocity must remain continuous at every update.
   - The 800ms animation deadline must remain anchored to the
     original expansion start, regardless of target changes.

6. Expansion must obey a strict layout transaction
   - During one frame, the parent width changes, the text changes,
     and the 500ms debounce expires.
   - The component must commit all three changes atomically.
   - No intermediate frame may combine the new expansion state
     with stale text measurements or stale width constraints.
   - The height animation must start using the final layout
     produced by that transaction.

7. Expansion must be invariant under interruption history
   - Two different tap sequences end with the same requested
     expansion state at the same absolute time.
   - Both sequences also produce the same viewport height
     and velocity at that instant.
   - From that point onward, their rendered height trajectories
     must be identical.
   - Previous cancelled animations must have no residual effect.
   - This must hold even after dozens of rapid interruptions.

8. Expansion must handle a target crossing the current height
   - Begin expanding toward 7 lines.
   - While the viewport is at the equivalent of 5 lines,
     replace the content with text requiring only 3 lines.
   - The new target is now below the current viewport height.
   - The animation must reverse direction without a position jump.
   - It must not reveal empty space beyond the new text.
   - It must not instantly shrink to the new text height.
   - Parent layout must remain consistent throughout.

9. Expansion must preserve geometry under nested animations
   - Place the expandable component inside a parent whose width,
     height, and position are independently animated.
   - Animate both parent and child simultaneously.
   - The child must calculate line wrapping from its actual
     constraints on each layout pass.
   - The child expansion must remain 800ms in its own timeline.
   - Parent movement must not restart or accelerate it.
   - No frame may display text outside the child's border.

10. Expansion must be deterministic under simultaneous events
    - At exactly 500ms, the debounce expires.
    - At the same timestamp, the user taps collapse.
    - At the same timestamp, contentText changes.
    - At the same timestamp, parent constraints change.
    - Define the event priority as:
      content update -> constraint update -> user tap ->
      debounce evaluation -> animation update.
    - The implementation must produce identical results
      regardless of coroutine dispatch ordering.
    - No transient expansion is permitted.




    
