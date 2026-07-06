5. Stale async result ignored after items change
This is the most important missing concurrency test.
Scenario:
1. Render old items with slow preview loads.
2. Replace the list with new items before old loads finish.
3. Let old loads complete.
Assert:
- Old preview heights are not applied.
- Old item titles/previews do not appear.
- New items use only their own loaded heights.
This catches stale coroutine results corrupting the current feed.
6. Async height change above viewport preserves anchor
Your insert/remove tests cover structural changes, but not measurement changes.
Scenario:
1. Render items with preview placeholders at 48.dp.
2. Scroll to item 30.
3. Complete preview loads for items 1–20 with much larger heights.
Assert:
- The same logical item remains visible.
- Scroll does not jump to a different item.
- Offset is approximately preserved.
This is core to the task.
7. Async height shrink above viewport preserves anchor
Same as above, but inverse.
Scenario:
1. Items above viewport start with large preview heights.
2. Their preview heights shrink.
Assert:
- The viewport does not jump backward.
- The same logical anchor item remains visible.
This catches solutions that only compensate for growth, not shrinkage.
8. Item loses previewId clears stale height
Scenario:
1. Render item A with previewId and loaded height 240.dp.
2. Recompose item A with previewId = null.
Assert:
- Preview box disappears.
- The row does not keep the old height.
This catches leaked height state keyed only by item id.
9. Reorder existing items preserves row identity
Different from insert/remove: same items, different order.
Scenario:
1. Render A, B, C.
2. Reorder to C, A, B.
Assert:
- Rows appear in the new order.
- Preview heights follow item ids, not positions.
- No row displays another item’s preview height.
This catches missing or incorrect lazy-list keys.
10. Non-default density and font scale
Scenario:
CompositionLocalProvider(
    LocalDensity provides Density(
        density = 2.75f,
        fontScale = 1.3f
    )
) {
    AsyncStickyFeed(...)
}
Assert:
- Anchor preservation still works.
- Header order remains correct.
- Loaded preview heights are converted/handled correctly.
This catches pixel/dp rounding bugs.
If you only want to add three, add these:
5. Stale async result ignored after items change
6. Async height change above viewport preserves anchor
8. Item loses previewId clears stale height
Those give you the best coverage beyond your existing four.
