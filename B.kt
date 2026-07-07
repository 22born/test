Preview heights may complete one at a time and in any order. Each completed height may change the measured height of a row.

Rows can be expanded or collapsed by tapping them. Expansion state must stay with the same logical item across insertions, removals, and reorders.

Header height may vary with text length, density, and font scale. Header and row height changes above the viewport must not unexpectedly change the visible anchor.

The feed must preserve the exact input order. Headers represent consecutive date sections, not global groups of all items with the same date label.


import androidx.compose.foundation.clickable

Make header height more variable. This makes the header-height requirement real.

@Composable
fun Header(text: String) {
    Text(
        text = text,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray)
            .padding(8.dp)
    )
}
Change FeedRow

Add local expansion state in the row. This intentionally exposes broken identity if the solution does not use stable lazy-list keys.

@Composable
fun FeedRow(
    item: FeedItem,
    previewHeight: Dp
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable { expanded = !expanded }
    ) {
        Text(item.title)

        if (expanded) {
            Text("Expanded details for ${item.title}")
        }

        if (item.previewId != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .background(Color.Gray)
            )
        }
    }
}







A. Preserve consecutive sections, not global groups

Do not use:

items.groupBy { it.dateLabel }

Instead, build entries in input order:

private sealed interface FeedEntry {
    val key: String

    data class Header(
        override val key: String,
        val dateLabel: String
    ) : FeedEntry

    data class Row(
        val item: FeedItem
    ) : FeedEntry {
        override val key: String = "row:${item.id}"
    }
}

private fun buildFeedEntries(items: List<FeedItem>): List<FeedEntry> {
    val result = mutableListOf<FeedEntry>()
    var currentDate: String? = null

    items.forEach { item ->
        if (item.dateLabel != currentDate) {
            currentDate = item.dateLabel
            result += FeedEntry.Header(
                key = "header:${item.dateLabel}:${item.id}",
                dateLabel = item.dateLabel
            )
        }

        result += FeedEntry.Row(item)
    }

    return result
}
B. Use stable lazy-list keys

This is required so row state follows logical items instead of positions.

LazyColumn(
    modifier = modifier,
    state = listState
) {
    entries.forEach { entry ->
        when (entry) {
            is FeedEntry.Header -> {
                stickyHeader(
                    key = entry.key,
                    contentType = "header"
                ) {
                    Header(entry.dateLabel)
                }
            }

            is FeedEntry.Row -> {
                item(
                    key = entry.key,
                    contentType = "feed-row"
                ) {
                    FeedRow(
                        item = entry.item,
                        previewHeight = previewHeights[entry.item.id]
                            ?: DefaultPreviewHeight
                    )
                }
            }
        }
    }
}
C. Handle incremental async updates safely

Instead of loading everything and applying it once, it is now better to apply each loaded height as it completes, but still from the LaunchedEffect coroutine, not directly inside Dispatchers.IO.

LaunchedEffect(previewLoadSpecs) {
    val validItemIds = previewLoadSpecs.map { it.itemId }.toSet()

    previewHeights = previewHeights.filterKeys { it in validItemIds }

    coroutineScope {
        previewLoadSpecs.forEach { spec ->
            launch {
                val height = withContext(Dispatchers.IO) {
                    currentLoadPreviewHeight(spec.previewId)
                }

                val oldHeights = previewHeights
                val newHeights = oldHeights + (spec.itemId to height)

                compensateScrollForHeightChange(
                    listState = listState,
                    entries = entries,
                    oldHeights = oldHeights,
                    newHeights = newHeights,
                    density = density
                )

                previewHeights = newHeights
            }
        }
    }
}

This preserves cancellation behavior: when previewLoadSpecs changes, the old LaunchedEffect is cancelled. LaunchedEffect cancellation/restart on key changes is the expected Compose behavior.

D. Scroll compensation must handle repeated row-height changes

The previous solution batched all heights. With incremental loading, compensation must work repeatedly.

private fun compensateScrollForHeightChange(
    listState: LazyListState,
    entries: List<FeedEntry>,
    oldHeights: Map<String, Dp>,
    newHeights: Map<String, Dp>,
    density: Density
) {
    val firstIndex = listState.firstVisibleItemIndex
    val firstOffset = listState.firstVisibleItemScrollOffset

    val deltaBeforeViewportPx = previewHeightDeltaBeforeIndexPx(
        entries = entries,
        firstVisibleIndex = firstIndex,
        oldHeights = oldHeights,
        newHeights = newHeights,
        density = density
    )

    if (deltaBeforeViewportPx != 0 && firstIndex in entries.indices) {
        listState.requestScrollToItem(
            index = firstIndex,
            scrollOffset = firstOffset + deltaBeforeViewportPx
        )
    }
}

requestScrollToItem is useful here because it requests the item to be positioned during the next remeasure.

E. Expansion state can be kept local, but only if keys are correct

Because FeedRow now has:

var expanded by remember { mutableStateOf(false) }

the correct solution must use stable keys in the lazy list. Otherwise, expansion state can move to the wrong row after insertions/removals/reorders.

For an even stronger solution, you can hoist expansion state:

var expandedIds by remember {
    mutableStateOf<Set<String>>(emptySet())
}

Then pass:

expanded = item.id in expandedIds
onToggleExpanded = {
    expandedIds =
        if (item.id in expandedIds) expandedIds - item.id
        else expandedIds + item.id
}

But if the prompt keeps FeedRow internal and allows internal refactoring, either approach is acceptable. Stable keys are the minimum requirement.






tests Add these.

5. Expansion state follows item after insertion

This is the most important new test if you add row expansion.

Scenario:

1. Render A, B, C.
2. Tap B to expand it.
3. Insert X above A.

Assert:

- B is still expanded.
- A, C, and X are not expanded.
- Expanded state did not move to A or X.

This catches missing stable keys.

6. Expansion state follows item after reorder

Scenario:

1. Render A, B, C.
2. Tap B to expand it.
3. Reorder to C, A, B.

Assert:

- B remains expanded in its new position.
- C and A are not expanded.

This catches position-based identity bugs.

7. Incremental async height growth above viewport preserves anchor

Scenario:

1. Render 40 items with previews initially at 48.dp.
2. Scroll until item 25 is first visible.
3. Complete preview heights for items 1, 5, 10, and 15 one at a time, each growing to 200.dp.

Assert:

- Item 25 remains the first visible logical item after each completion.
- The viewport does not drift after multiple incremental updates.

This catches overcompensation and cumulative scroll drift.

8. Incremental async height shrink above viewport preserves anchor

Scenario:

1. Start with loaded preview heights above the viewport at 200.dp.
2. Scroll to item 25.
3. Update/reload items above the viewport so their preview heights shrink to 48.dp.

Assert:

- The same logical item remains visible.
- The list does not jump backward to earlier content.

This catches solutions that only handle growth.

9. Stale async result ignored after items change

Scenario:

1. Render old items with slow preview loads.
2. Replace them with new items before the old loads complete.
3. Allow all loaders to finish.

Assert:

- Old item titles are not visible.
- Old preview heights are not applied to new items.
- New items use only their own loaded heights.

This is the key cancellation/race test.

10. Non-default density and font scale with anchor preservation

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

- Insert/remove above viewport still preserves the same logical anchor.
- Incremental height changes above viewport still preserve the same logical anchor.
- Consecutive date sections still render in input order.

This catches bad dp/pixel assumptions.

Final recommended package of changes

Add these to the prompt:

- Incremental preview height completion.
- Expand/collapse row state follows item identity.
- Variable header height.
- Consecutive date sections preserve input order.

Change the starter code only by:

- Making Header maxLines = 2.
- Adding clickable expansion state inside FeedRow.
- Keeping the broken groupBy logic.

Change the correct solution by:

- Building feed entries in input order.
- Using stable keys for headers and rows.
- Applying async height updates safely and incrementally.
- Preserving the visible anchor after repeated height changes.
- Ensuring expansion state follows item identity.

Add these tests:

5. Expansion state follows item after insertion
6. Expansion state follows item after reorder
7. Incremental height growth above viewport preserves anchor
8. Incremental height shrink above viewport preserves anchor
9. Stale async result ignored after items change
10. Non-default density/font scale with anchor preservation
