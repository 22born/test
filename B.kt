import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FeedItem(
    val id: String,
    val dateLabel: String,
    val title: String,
    val previewId: String?
)

private val DefaultPreviewHeight = 48.dp

/**
 * A preview height belongs to both an item id and a preview id.
 *
 * This prevents a stale height from being reused when the same item remains
 * in the feed but its previewId changes.
 */
private data class LoadedPreviewHeight(
    val previewId: String,
    val height: Dp
)

private data class PreviewLoadSpec(
    val itemId: String,
    val previewId: String
)

/**
 * The LazyColumn is rendered from explicit entries instead of using groupBy().
 *
 * This matters because groupBy() globally collects all matching date labels.
 * A feed must preserve input order and create headers for consecutive sections.
 */
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

/**
 * Build headers for consecutive date sections only.
 *
 * Example input:
 *   Today: A
 *   Yesterday: B
 *   Today: C
 *
 * Correct output:
 *   Header(Today), A, Header(Yesterday), B, Header(Today), C
 *
 * Incorrect groupBy-style output would be:
 *   Header(Today), A, C, Header(Yesterday), B
 */
private fun buildFeedEntries(items: List<FeedItem>): List<FeedEntry> {
    val entries = mutableListOf<FeedEntry>()
    var currentDateLabel: String? = null

    items.forEach { item ->
        if (item.dateLabel != currentDateLabel) {
            currentDateLabel = item.dateLabel

            /**
             * The first row id in the section makes repeated date labels distinct.
             *
             * FeedItem.id values are assumed unique by the task, so this prevents
             * duplicate lazy-list keys when the same date label appears in multiple
             * non-contiguous sections.
             */
            entries += FeedEntry.Header(
                key = "header:${item.dateLabel}:${item.id}",
                dateLabel = item.dateLabel
            )
        }

        entries += FeedEntry.Row(item)
    }

    return entries
}

private fun previewLoadSpecsFor(items: List<FeedItem>): List<PreviewLoadSpec> {
    return items.mapNotNull { item ->
        item.previewId?.let { previewId ->
            PreviewLoadSpec(
                itemId = item.id,
                previewId = previewId
            )
        }
    }
}

private fun previewHeightFor(
    item: FeedItem,
    previewHeights: Map<String, LoadedPreviewHeight>
): Dp {
    val previewId = item.previewId ?: return DefaultPreviewHeight

    val loaded = previewHeights[item.id]

    return if (loaded?.previewId == previewId) {
        loaded.height
    } else {
        DefaultPreviewHeight
    }
}

/**
 * Computes the total preview-height delta for rows before the current viewport.
 *
 * If rows above the viewport grow, the list would normally appear to jump.
 * To preserve the same visible logical item, the scroll offset should increase
 * by the amount of height added above the viewport.
 *
 * If rows above the viewport shrink, the scroll offset should decrease.
 */
private fun previewHeightDeltaBeforeViewportPx(
    entries: List<FeedEntry>,
    firstVisibleIndex: Int,
    oldHeights: Map<String, LoadedPreviewHeight>,
    newHeights: Map<String, LoadedPreviewHeight>,
    density: Density
): Int {
    if (firstVisibleIndex <= 0) return 0

    var deltaPx = 0

    entries
        .take(firstVisibleIndex.coerceAtMost(entries.size))
        .forEach { entry ->
            if (entry is FeedEntry.Row && entry.item.previewId != null) {
                val oldHeight = previewHeightFor(
                    item = entry.item,
                    previewHeights = oldHeights
                )

                val newHeight = previewHeightFor(
                    item = entry.item,
                    previewHeights = newHeights
                )

                deltaPx += with(density) {
                    (newHeight - oldHeight).roundToPx()
                }
            }
        }

    return deltaPx
}

/**
 * Applies scroll compensation before a preview-height state change.
 *
 * This function does not mutate previewHeights itself. It only requests the
 * LazyColumn to keep the same visible logical item anchored during the next
 * remeasure.
 */
private fun compensateScrollForPreviewHeightChange(
    listState: LazyListState,
    entries: List<FeedEntry>,
    oldHeights: Map<String, LoadedPreviewHeight>,
    newHeights: Map<String, LoadedPreviewHeight>,
    density: Density
) {
    val firstVisibleIndex = listState.firstVisibleItemIndex

    if (firstVisibleIndex !in entries.indices) {
        return
    }

    val deltaBeforeViewportPx = previewHeightDeltaBeforeViewportPx(
        entries = entries,
        firstVisibleIndex = firstVisibleIndex,
        oldHeights = oldHeights,
        newHeights = newHeights,
        density = density
    )

    if (deltaBeforeViewportPx == 0) {
        return
    }

    val currentOffset = listState.firstVisibleItemScrollOffset

    /**
     * Positive delta means content above grew, so we scroll further down by
     * the same number of pixels.
     *
     * Negative delta means content above shrank, so we reduce the offset.
     * If the exact offset would be negative, preserving the anchor exactly is
     * impossible with the same firstVisibleIndex, so clamp to 0.
     */
    val adjustedOffset = (currentOffset + deltaBeforeViewportPx)
        .coerceAtLeast(0)

    listState.requestScrollToItem(
        index = firstVisibleIndex,
        scrollOffset = adjustedOffset
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AsyncStickyFeed(
    items: List<FeedItem>,
    modifier: Modifier = Modifier,
    loadPreviewHeight: suspend (String) -> Dp
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    /**
     * Keep the latest loader lambda available inside coroutines without using
     * the lambda object itself as a restart key.
     */
    val currentLoadPreviewHeight by rememberUpdatedState(loadPreviewHeight)

    /**
     * Preview heights are keyed by item id, but each stored value also remembers
     * the previewId it was loaded for.
     */
    var previewHeights by remember {
        mutableStateOf<Map<String, LoadedPreviewHeight>>(emptyMap())
    }

    /**
     * Expansion state is hoisted and keyed by item id.
     *
     * This makes the state follow the logical row across insertions, removals,
     * and reorders.
     */
    var expandedItemIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    val entries = remember(items) {
        buildFeedEntries(items)
    }

    val previewLoadSpecs = remember(items) {
        previewLoadSpecsFor(items)
    }

    val currentEntries by rememberUpdatedState(entries)
    val currentDensity by rememberUpdatedState(density)

    /**
     * Remove expansion state for items that no longer exist.
     */
    LaunchedEffect(items.map { it.id }) {
        val currentItemIds = items.map { it.id }.toSet()
        expandedItemIds = expandedItemIds.intersect(currentItemIds)
    }

    /**
     * Load preview heights incrementally and safely.
     *
     * Important properties:
     * - The effect restarts when preview specs change.
     * - Old in-flight loads are cancelled when items change.
     * - State is updated after returning from Dispatchers.IO.
     * - Heights for removed items or changed previewIds are discarded.
     * - Each completed height is applied independently.
     */
    LaunchedEffect(previewLoadSpecs) {
        val currentPreviewByItemId = previewLoadSpecs.associate {
            it.itemId to it.previewId
        }

        /**
         * First remove stale loaded heights:
         * - item removed
         * - item no longer has previewId
         * - item still exists but previewId changed
         */
        val filteredHeights = previewHeights.filter { (itemId, loaded) ->
            currentPreviewByItemId[itemId] == loaded.previewId
        }

        if (filteredHeights != previewHeights) {
            compensateScrollForPreviewHeightChange(
                listState = listState,
                entries = currentEntries,
                oldHeights = previewHeights,
                newHeights = filteredHeights,
                density = currentDensity
            )

            previewHeights = filteredHeights
        }

        val specsToLoad = previewLoadSpecs.filter { spec ->
            val loaded = previewHeights[spec.itemId]
            loaded?.previewId != spec.previewId
        }

        coroutineScope {
            specsToLoad.forEach { spec ->
                launch {
                    try {
                        /**
                         * Only the expensive loading work runs on Dispatchers.IO.
                         * The Compose state mutation happens below, after this
                         * withContext call returns.
                         */
                        val loadedHeight = withContext(Dispatchers.IO) {
                            currentLoadPreviewHeight(spec.previewId)
                        }

                        /**
                         * If the LaunchedEffect was cancelled while the IO work
                         * was running, do not apply the old result.
                         */
                        if (!isActive) return@launch

                        val oldHeights = previewHeights

                        /**
                         * The item may still exist, but its previewId may have
                         * changed while this load was running. Re-check before
                         * applying the result.
                         */
                        val stillCurrent = currentEntries.any { entry ->
                            entry is FeedEntry.Row &&
                                entry.item.id == spec.itemId &&
                                entry.item.previewId == spec.previewId
                        }

                        if (!stillCurrent) return@launch

                        val newHeights = oldHeights + (
                            spec.itemId to LoadedPreviewHeight(
                                previewId = spec.previewId,
                                height = loadedHeight
                            )
                        )

                        if (newHeights == oldHeights) {
                            return@launch
                        }

                        compensateScrollForPreviewHeightChange(
                            listState = listState,
                            entries = currentEntries,
                            oldHeights = oldHeights,
                            newHeights = newHeights,
                            density = currentDensity
                        )

                        previewHeights = newHeights
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        /**
                         * For this component, a failed preview-height load should
                         * not crash the feed. Keep the deterministic placeholder
                         * height instead.
                         *
                         * In production, this is where logging/reporting would go.
                         */
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState
    ) {
        entries.forEach { entry ->
            when (entry) {
                is FeedEntry.Header -> {
                    stickyHeader(
                        key = entry.key,
                        contentType = "date-header"
                    ) {
                        Header(text = entry.dateLabel)
                    }
                }

                is FeedEntry.Row -> {
                    item(
                        key = entry.key,
                        contentType = "feed-row"
                    ) {
                        val item = entry.item
                        val expanded = item.id in expandedItemIds

                        FeedRow(
                            item = item,
                            previewHeight = previewHeightFor(
                                item = item,
                                previewHeights = previewHeights
                            ),
                            expanded = expanded,
                            onToggleExpanded = {
                                expandedItemIds =
                                    if (item.id in expandedItemIds) {
                                        expandedItemIds - item.id
                                    } else {
                                        expandedItemIds + item.id
                                    }
                            }
                        )
                    }
                }
            }
        }
    }
}

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

@Composable
fun FeedRow(
    item: FeedItem,
    previewHeight: Dp,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable(onClick = onToggleExpanded)
    ) {
        Text(text = item.title)

        if (expanded) {
            Text(text = "Expanded details for ${item.title}")
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







You are given a Jetpack Compose component called AsyncStickyFeed. It displays a feed in a LazyColumn with sticky date headers. Each feed item may contain an async preview whose height is loaded later.
The current implementation has bugs when items are inserted, removed, reordered, or when async preview heights finish loading. It also mishandles non-contiguous date sections and row expansion state.
You may change the internal implementation, but keep the public AsyncStickyFeed API unchanged.
Assume FeedItem.id values are unique.



The fixed component must satisfy these visible behaviors:
The feed must preserve the exact input order.
Headers must represent consecutive date sections, not global groups of all items with the same date label.
Sticky headers must remain associated with the correct section.
Rows must keep stable identity when items are inserted, removed, or reordered.
Row expansion state must stay with the same logical item across insertions, removals, and reorders.
Preview heights may complete one at a time and in any order.
Async preview results from an old items list must not affect the current list.
Preview height changes above the viewport must not unexpectedly change the visible anchor.
Removing or inserting items above the viewport must preserve the same visible logical item when possible.
Header height may vary with text length, density, and font scale.
The component must behave correctly under non-default density and font scale.
Compose state must not be updated from a background thread.




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
