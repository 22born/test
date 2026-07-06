Context

You are given a Jetpack Compose component called "AsyncStickyFeed". It displays grouped feed items in a "LazyColumn" with sticky date headers. Each item may load extra content asynchronously, which can change the item height after it first appears.

The current implementation causes scroll jumps, sticky-header flicker, and incorrect item anchoring when async content loads or when new items are inserted above the current viewport.

Starter Code

data class FeedItem(
    val id: String,
    val dateLabel: String,
    val title: String,
    val previewId: String?
)

@Composable
fun AsyncStickyFeed(
    items: List<FeedItem>,
    modifier: Modifier = Modifier,
    loadPreviewHeight: suspend (String) -> Dp
) {
    val listState = rememberLazyListState()
    var previewHeights by remember { mutableStateOf<Map<String, Dp>>(emptyMap()) }

    LaunchedEffect(items) {
        items.forEach { item ->
            val previewId = item.previewId ?: return@forEach
            launch(Dispatchers.IO) {
                val height = loadPreviewHeight(previewId)
                previewHeights = previewHeights + (item.id to height)
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState
    ) {
        items.groupBy { it.dateLabel }.forEach { (date, groupItems) ->
            stickyHeader {
                Header(date)
            }

            items(groupItems) { item ->
                FeedRow(
                    item = item,
                    previewHeight = previewHeights[item.id] ?: 48.dp
                )
            }
        }
    }
}

@Composable
fun Header(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray)
            .padding(8.dp)
    )
}

@Composable
fun FeedRow(
    item: FeedItem,
    previewHeight: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(item.title)
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

Task

Fix "AsyncStickyFeed" so it behaves correctly and deterministically.

The fixed component must satisfy these visible behaviors:

1. Loading preview heights must not apply stale results after "items" changes.
2. Rows must keep stable identity when items are inserted, removed, or reordered.
3. Sticky headers must remain associated with the correct date group.
4. Async height changes above the viewport must not cause unexpected visible scroll jumps.
5. The layout must work under non-default density and font scale.
6. Preview height loading must not update Compose state from a background thread.

You may change the internal implementation, but keep the public "AsyncStickyFeed" API unchanged.

Output Format

Return:

1. Corrected Kotlin code.
2. A brief explanation of the bug.
3. One Compose UI test or pseudocode test covering insertion above the viewport plus async height loading.






for Async Sticky Feed Layout, the correct solution should not just “add keys.” A strong solution should handle three bugs together:
Stale async results when items changes.
Wrong identity tracking when rows are inserted/reordered.
Scroll jump when async preview heights load above the viewport.
Compose lazy lists support stable item keys, and Google’s docs explicitly recommend stable unique keys so item state is consistent across dataset changes. Sticky headers are supported through stickyHeader, and LazyListState.requestScrollToItem can request a scroll position for the next remeasure, which is useful for scroll anchoring after height changes. �
Android Developers +2
Correct solution with comments
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.lazy.item
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class FeedItem(
    val id: String,
    val dateLabel: String,
    val title: String,
    val previewId: String?
)

private val DefaultPreviewHeight = 48.dp

private data class PreviewLoadSpec(
    val itemId: String,
    val previewId: String
)

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
 * Builds the actual LazyColumn entries.
 *
 * Important detail:
 * We group only consecutive items with the same date label.
 * This preserves feed order instead of blindly using groupBy(), which can
 * accidentally regroup non-contiguous items and change the visible order.
 */
private fun buildFeedEntries(items: List<FeedItem>): List<FeedEntry> {
    val result = mutableListOf<FeedEntry>()

    var currentDate: String? = null

    items.forEach { item ->
        if (item.dateLabel != currentDate) {
            currentDate = item.dateLabel

            // Header key must be stable enough for lazy-list identity.
            // The first item id makes repeated date sections distinct.
            result += FeedEntry.Header(
                key = "header:${item.dateLabel}:${item.id}",
                dateLabel = item.dateLabel
            )
        }

        result += FeedEntry.Row(item)
    }

    return result
}

/**
 * Computes how much preview height changed before the first visible lazy item.
 *
 * If an offscreen item above the viewport grows by 120px, the visible content
 * would normally jump down by 120px. We compensate by increasing the requested
 * scroll offset by the same amount.
 */
private fun previewHeightDeltaBeforeIndexPx(
    entries: List<FeedEntry>,
    firstVisibleIndex: Int,
    oldHeights: Map<String, Dp>,
    newHeights: Map<String, Dp>,
    density: Density
): Int {
    if (firstVisibleIndex <= 0) return 0

    var deltaPx = 0

    entries
        .take(firstVisibleIndex.coerceAtMost(entries.size))
        .forEach { entry ->
            if (entry is FeedEntry.Row && entry.item.previewId != null) {
                val oldHeight = oldHeights[entry.item.id] ?: DefaultPreviewHeight
                val newHeight = newHeights[entry.item.id] ?: DefaultPreviewHeight

                deltaPx += with(density) {
                    (newHeight - oldHeight).roundToPx()
                }
            }
        }

    return deltaPx
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

    // Keep the latest lambda without restarting work just because the lambda
    // instance changed during recomposition.
    val currentLoadPreviewHeight by rememberUpdatedState(loadPreviewHeight)

    var previewHeights by remember {
        mutableStateOf<Map<String, Dp>>(emptyMap())
    }

    val entries = remember(items) {
        buildFeedEntries(items)
    }

    val previewLoadSpecs = remember(items) {
        items.mapNotNull { item ->
            item.previewId?.let { previewId ->
                PreviewLoadSpec(
                    itemId = item.id,
                    previewId = previewId
                )
            }
        }
    }

    LaunchedEffect(previewLoadSpecs) {
        val currentPreviewItemIds = previewLoadSpecs
            .map { it.itemId }
            .toSet()

        // Remove heights for rows that are no longer present or no longer have
        // previews. This prevents stale height state from affecting future rows.
        previewHeights = previewHeights.filterKeys { it in currentPreviewItemIds }

        val missingSpecs = previewLoadSpecs.filter { spec ->
            previewHeights[spec.itemId] == null
        }

        if (missingSpecs.isEmpty()) {
            return@LaunchedEffect
        }

        /**
         * The expensive loading work happens on Dispatchers.IO.
         *
         * The Compose state write does NOT happen inside Dispatchers.IO.
         * awaitAll() resumes back in the LaunchedEffect coroutine, which is the
         * correct place to apply Compose state.
         *
         * If previewLoadSpecs changes, LaunchedEffect cancels this coroutine,
         * so old results cannot be applied to a newer item list.
         */
        val loadedHeights: Map<String, Dp> = coroutineScope {
            missingSpecs
                .map { spec ->
                    async(Dispatchers.IO) {
                        spec.itemId to currentLoadPreviewHeight(spec.previewId)
                    }
                }
                .awaitAll()
                .toMap()
        }

        val oldHeights = previewHeights
        val newHeights = oldHeights + loadedHeights

        /**
         * Capture the current anchor before changing previewHeights.
         *
         * If rows above the viewport change height, we request the same first
         * visible lazy item with an adjusted offset during the next remeasure.
         */
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

        // Apply all loaded heights as one batch to avoid flickery per-row jumps.
        previewHeights = newHeights
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
}

@Composable
fun Header(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray)
            .padding(8.dp)
    )
}

@Composable
fun FeedRow(
    item: FeedItem,
    previewHeight: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(text = item.title)

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
Why this is the correct fix
The original code uses lazy-list rows without stable item keys. That means insertion, removal, or reordering can make Compose treat an item as “the row at this position” rather than “the row with this id.” Stable keys are important for dataset changes. �
Android Developers
The original async loading also writes Compose state from inside Dispatchers.IO, and old loads can complete after items has changed. The fixed code uses LaunchedEffect(previewLoadSpecs), so a changed item list cancels the previous effect; Compose’s side-effect docs describe LaunchedEffect as launching a coroutine that is cancelled when it leaves composition or its keys change. �
Android Developers
The subtle part is scroll anchoring. When an offscreen preview above the viewport changes from 48.dp to 180.dp, the visible content can jump. The fix computes the pixel height delta above the current first visible lazy item and calls requestScrollToItem with the adjusted offset for the next remeasure. requestScrollToItem is specifically documented as requesting the item to be placed at the viewport start during the next remeasure. �
Android Developers
Important edge/quality test cases only
1. Stale async load must not affect new items
Purpose: catches the most dangerous concurrency bug.
Scenario:
var items by mutableStateOf(oldItems)

AsyncStickyFeed(
    items = items,
    loadPreviewHeight = { previewId ->
        if (previewId.startsWith("old")) {
            delay(500)
            300.dp
        } else {
            delay(10)
            80.dp
        }
    }
)

items = newItems
Assert:
- No old item title is visible.
- New items render with their own preview heights.
- Old preview heights are not applied to new rows.
2. Insert above viewport preserves visible anchor
Purpose: verifies stable keys and scroll-position preservation.
Scenario:
1. Render 50 items.
2. Scroll to item 30.
3. Insert 5 new items at the top.
Assert:
- The same logical item, not merely the same index, remains visible.
- The row state is not transferred to the wrong item.
- Sticky header still matches the visible item’s date group.
3. Async height increase above viewport does not jump visible content
Purpose: catches the core measurement/scroll-anchor bug.
Scenario:
1. Render feed with previews defaulting to 48.dp.
2. Scroll until item 20 is first visible.
3. Complete async height loads for items 1–10 with much larger heights.
Assert:
- Item 20 remains the first visible logical item.
- Its scroll offset is approximately preserved.
- No sudden visual jump to a different item occurs.
This should run with a non-default density, for example:
CompositionLocalProvider(
    LocalDensity provides Density(
        density = 2.75f,
        fontScale = 1.3f
    )
) {
    AsyncStickyFeed(...)
}
4. Async height shrink above viewport does not jump backward
Purpose: catches the inverse of test 3.
Scenario:
1. Start with cached/loaded preview heights above the viewport at 240.dp.
2. Update/reload them to 48.dp.
3. Keep the viewport anchored on a lower item.
Assert:
- The visible anchor item remains stable.
- The list does not snap upward to older items.
5. Reorder items across date groups
Purpose: catches bad keys and sticky-header association bugs.
Scenario:
1. Render items grouped under Today and Yesterday.
2. Move one item from Yesterday into Today.
3. Recompose.
Assert:
- The moved item appears under the correct sticky header.
- Its remembered row state, if any, follows the item id.
- No duplicate/wrong sticky header appears.
6. Item loses previewId
Purpose: catches stale height-state leaks.
Scenario:
1. Render item A with previewId = "preview-a" and loaded height 220.dp.
2. Recompose item A with previewId = null.
Assert:
- Item A no longer renders the preview box.
- The old 220.dp height does not remain reserved.
7. Duplicate date sections that are not contiguous
Purpose: catches incorrect groupBy use.
Scenario:
Today: item A
Yesterday: item B
Today: item C
Assert:
- The rendered order remains A, B, C.
- There are two Today headers if the input has two separate Today sections.
- The implementation does not regroup item C next to item A unless the caller already sorted it that way.
Those seven are enough. I would not add many “happy path” tests beyond these, because this benchmark is specifically about identity, async cancellation, sticky headers, density-aware measurement, and scroll anchoring.
