

If the reference solution computes column width as:
maxWidth over all live records, including exit copies
then the reference is wrong/inconsistent. That behavior makes a removed wide item keep widening the column while fading out, which contradicts “exit copies are overlays.”
The reference should instead do:
measuredWidth = max width of current items only
measuredHeight = sum of current item measured heights only
Exit copies may still be drawn/placed using their retained visual bounds, but they must not affect the parent layout size.
Use this correction in the reference:
val currentRecords = items.mapNotNull { item ->
    records[item.key]?.takeIf { it.isCurrent }
}

val measuredWidth = currentRecords.maxOfOrNull { it.roundedWidth } ?: 0
val measuredHeight = currentRecords.sumOf { it.roundedHeight }
And separately render exits:
val exitRecords = records.values.filter { it.isExiting }

// Draw/place exitRecords as overlays.
// Do not include them in measuredWidth or measuredHeight.
So the rule should stay:
Current items define measured layout. Exit copies are visual overlays only.
Add this test to lock it:
Remove a wide item while its exit animation is still visible. The remaining current item is narrow. Verify the column’s measured width equals the narrow current item’s animated width, not the exiting wide item’s width.










Add one definition
A live visual record is any key that is currently being drawn, either as a current item or as a retained exit copy, together with its current visual x, y, width, height, and alpha.
Replace Requirement 2
Current version is too vague about measured size.
Use:
Current items participate in the column’s measured layout in `items` order. At each frame, each current item is measured using that frame’s rounded animated width and height. The column’s measured width is the maximum measured width of current items, or 0 if there are no current items. The column’s measured height is the sum of the measured heights of current items in order. Removed exit copies are drawn as overlays and do not contribute to the column’s measured width or measured height.
This fixes the report’s concerns about:
- sum-of-heights vs furthest-bottom-edge
- exit copies widening the column
- round-then-sum vs sum-then-round
Keep Requirement 5
This one is fine:
Animated x, y, width, and height values are interpolated as floats, then rounded with `roundToInt()` before measurement, placement, or drawing.
Once Requirement 2 says the column height is the sum of measured item heights, Requirement 5 already decides the rounding behavior.
Replace Requirement 9
Current “new key” is ambiguous.
Use:
A key is considered newly inserted only if it had no live visual record immediately before the layout change. A newly inserted key must appear at its final measured bounds and animate alpha from 0 to 1. This rule does not apply to a key that reappears while its exit copy is still live.
Replace Requirement 12
Current reappearance rule omits alpha.
Use:
If a key reappears while its exit animation is still running, there must be only one visual instance for that key. The new transition must continue from the exit copy’s current visual x, y, width, height, and alpha at interruption time, then animate to the current item’s target bounds and alpha = 1. It must not restart from alpha = 0.
Replace Requirement 13
Current “new animation” is too broad and can accidentally reset alpha.
Use:
If a live visual record receives new target bounds while already animating, its x, y, width, and height must retarget from the current visual bounds at interruption time. A bounds retarget must not reset, restart, or cancel an unrelated alpha animation. Alpha must continue from its current value toward the target implied by the key’s current presence state: 1 for current items and 0 for exiting copies.
Add/adjust tests
1. Reappearing key during exit
Remove key A, advance halfway through its fade-out, then reinsert A. Verify there is one visual instance, it continues from current bounds and current alpha, and alpha animates back to 1 rather than restarting from 0.

2. Geometry retarget during fade-in
Insert key A, advance halfway through fade-in, then change layout so A’s target bounds move. Verify x/y/width/height retarget from current visual bounds, while alpha continues from its current value instead of restarting.

3. Exit copy does not affect column size
Remove a wide item while its exit copy remains visible. Verify the column’s measured width is computed only from current items.

4. Round-before-sum height
Use two current items whose animated heights are 40.5px. Verify each height rounds first, then the column height sums the rounded values.
