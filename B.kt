1. Insert above viewport preserves logical anchor
Purpose: verifies stable keys and scroll anchoring when new rows are inserted before the visible content.
Scenario:
1. Render 40 items.
2. Scroll until row item-25 is visible and near the top of the viewport.
3. Record boundsInRoot.top for row-item-25.
4. Insert 5 new items before item-1.
5. Wait for recomposition.
Assert:
- row-item-25 still exists.
- row-item-25 remains visible.
- row-item-25 top position is approximately unchanged.
- Its section header is still the correct date header.
Do not assert only that firstVisibleItemIndex is unchanged. It may change because headers and inserted rows affect entry indices.
2. Remove above viewport preserves logical anchor
Purpose: verifies the inverse of insertion anchoring.
Scenario:
1. Render 40 items.
2. Scroll until row item-25 is visible and near the top.
3. Record row-item-25 boundsInRoot.top.
4. Remove several rows before item-25.
5. Wait for recomposition.
Assert:
- row-item-25 remains visible.
- row-item-25 top position is approximately unchanged.
- The viewport does not jump to item-20, item-30, or another section.
This is valid because stable keys are supposed to preserve logical item identity across add/remove before the current visible item. �
Android Developers
3. Non-contiguous date sections preserve input order
Purpose: catches incorrect groupBy { it.dateLabel } solutions.
Scenario:
Input:
Today: A
Yesterday: B
Today: C
Assert render order:
Header Today
Row A
Header Yesterday
Row B
Header Today
Row C
This test should fail if the implementation globally groups all Today items together.
4. Alternating sections produce separate headers
Purpose: verifies repeated date labels create separate consecutive sections.
Scenario:
Today: A
Yesterday: B
Today: C
Yesterday: D
Assert:
- There are four headers.
- The headers appear in this order:
  Today, Yesterday, Today, Yesterday.
- Rows appear in exact input order:
  A, B, C, D.
This is similar to test 3, but stronger because it catches implementations that special-case only one repeated section.
5. Expansion state follows item after insertion
Purpose: catches missing or incorrect stable row keys.
Scenario:
1. Render rows A, B, C.
2. Tap row B.
3. Verify "Expanded details for B" is visible.
4. Insert row X above A.
Assert:
- Row B is still expanded.
- Rows A, C, and X are not expanded.
- Expansion state did not move to row A or row X.
This should test logical item identity, not position.
6. Expansion state follows item after reorder
Purpose: catches position-based remembered state.
Scenario:
1. Render rows A, B, C.
2. Tap row B to expand it.
3. Reorder items to C, A, B.
Assert:
- B remains expanded in its new position.
- C and A are not expanded.
- Render order is C, A, B.
This test is important because insertion/removal alone may not catch all bad key strategies.
7. Incremental async height growth above viewport preserves logical anchor
Purpose: verifies scroll anchoring during repeated measurement changes above the viewport.
Corrected version of the test:
1. Render 40 rows with previews initially using the 48.dp placeholder.
2. Scroll until row item-25 is visible near the top.
3. Record row-item-25 boundsInRoot.top.
4. Complete preview height for item-1, growing from 48.dp to 200.dp.
5. Assert row-item-25 is still visually anchored.
6. Repeat for item-5, item-10, and item-15, one completion at a time.
Assert after each completion:
- row-item-25 remains visible.
- row-item-25 top position remains within a small tolerance.
- The viewport does not drift cumulatively after multiple completions.
- The sticky header still corresponds to item-25’s section.
Do not assert exact raw index equality. Assert the same logical item and approximate visual position.
8. Incremental async height shrink above viewport preserves logical anchor
Purpose: catches solutions that only compensate for growth, not shrinkage.
Scenario:
1. Start with rows above the viewport using loaded preview heights, for example 200.dp.
2. Scroll until row item-25 is visible near the top.
3. Record row-item-25 boundsInRoot.top.
4. Update/reload item-1, item-5, item-10, and item-15 so their preview heights shrink to 48.dp, one at a time.
Assert after each shrink:
- row-item-25 remains visible.
- row-item-25 top position remains approximately stable.
- The list does not jump backward to earlier rows.
This is a real edge case because offset compensation must handle negative height deltas.
9. Stale async result ignored after items change
Purpose: verifies LaunchedEffect cancellation/restart behavior and stale-result protection. Compose cancels a LaunchedEffect coroutine when it leaves composition or when its keys change, so the test should force an old load to finish after a new item list is already active. �
Android Developers
Scenario:
1. Render old items with slow preview loaders.
2. Replace the list with new items before old preview loaders finish.
3. Complete old loaders.
4. Complete new loaders.
Assert:
- Old row titles are not visible.
- Old preview heights are not applied to new rows.
- New rows use only their own preview heights.
- No stale expanded state remains for removed old rows.
This should use controllable deferred/completable loaders, not real delay, so the test is deterministic.
10. Item loses previewId clears stale height
Purpose: catches stale preview-height state keyed only by item id.
Scenario:
1. Render item A with previewId = preview-a.
2. Complete preview-a height as 240.dp.
3. Recompose item A with previewId = null.
Assert:
- preview-A no longer exists.
- Row A does not reserve the old 240.dp preview height.
- Row A still renders its title and expansion behavior normally.
This is a high-value edge case because the item id stays the same while the preview identity changes.
11. Same item id with changed previewId does not reuse old height
Purpose: catches a more subtle stale-cache bug than test 10.
Scenario:
1. Render item A with previewId = preview-old.
2. Complete preview-old height as 240.dp.
3. Recompose item A with previewId = preview-new.
4. Before preview-new loads, row A should use the placeholder height.
5. Complete preview-new height as 80.dp.
Assert:
- The old 240.dp height is not reused for preview-new.
- The row uses placeholder height while preview-new is pending.
- After preview-new completes, the row uses 80.dp.
This is worth keeping if your solution stores loaded heights by item.id.
12. Non-default density and font scale with anchoring
Purpose: catches dp/px rounding assumptions and font-scale-sensitive header height issues.
Scenario:
CompositionLocalProvider(
    LocalDensity provides Density(
        density = 2.75f,
        fontScale = 1.3f
    )
) {
    AsyncStickyFeed(...)
}
Run either test 1 or test 7 under this density/font scale.
Assert:
- The same logical anchor row remains visually stable.
- Consecutive headers still render in input order.
- Sticky header association remains correct.
This should not require exact pixel equality. Use a tolerance.
13. Compose state not updated from background thread
This one is not a good black-box UI test. Keep it as a code-review/static-evaluation requirement.
Correct evaluator check:
Fail solutions that mutate Compose state inside launch(Dispatchers.IO) or withContext(Dispatchers.IO).

Accept solutions that do background work in IO and return to the composition coroutine before assigning Compose state.
Bad pattern:
launch(Dispatchers.IO) {
    val height = loadPreviewHeight(previewId)
    previewHeights = previewHeights + (item.id to height)
}
Good pattern:
launch {
    val height = withContext(Dispatchers.IO) {
        loadPreviewHeight(previewId)
    }

    previewHeights = previewHeights + (item.id to height)
}
Do not count this as one of the main UI tests.
