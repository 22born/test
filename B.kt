
## Context

You are given a Jetpack Compose component called `AsyncClipRow`. It displays a single horizontal row of chips. Chip labels are loaded asynchronously. If not all chips fit in the available width, the row should show a `+N more` chip for the hidden chips.

The component currently behaves incorrectly under rapid async updates, narrow widths, different screen densities, and non-default font scales. You may change the internal implementation, including replacing `Layout` with `SubcomposeLayout`, but keep the public `AsyncClipRow` API unchanged. Assume `chipIds` are unique.

## Starter Code

```kotlin
@Composable
fun AsyncClipRow(
    chipIds: List<String>,
    modifier: Modifier = Modifier,
    loadLabel: suspend (String) -> String
) {
    var labels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(chipIds) {
        chipIds.forEach { id ->
            launch(Dispatchers.IO) {
                val label = loadLabel(id)
                labels = labels + (id to label)
            }
        }
    }

    Layout(
        modifier = modifier.height(40.dp).clipToBounds(),
        content = {
            chipIds.forEach { id ->
                Chip(labels[id] ?: "...")
            }
            Chip("+${chipIds.size} more")
        }
    ) { measurables, constraints ->
        val chipPlaceables = measurables.dropLast(1).map {
            it.measure(constraints)
        }
        val morePlaceable = measurables.last().measure(constraints)

        var usedWidth = 0
        var visibleCount = 0

        for (p in chipPlaceables) {
            if (usedWidth + p.width <= constraints.maxWidth) {
                usedWidth += p.width
                visibleCount++
            }
        }

        val hiddenCount = chipIds.size - visibleCount

        layout(constraints.maxWidth, constraints.maxHeight) {
            var x = 0
            chipPlaceables.take(visibleCount).forEach {
                it.placeRelative(x, 0)
                x += it.width
            }

            if (hiddenCount > 0) {
                morePlaceable.placeRelative(
                    constraints.maxWidth - morePlaceable.width,
                    0
                )
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .background(Color.LightGray, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, maxLines = 1)
    }
}
```

## Task

Fix `AsyncClipRow` so it renders correctly and deterministically.

The fixed component must satisfy these visible behaviors:

1. Chips never overlap.
2. Chips never draw outside the row bounds.
3. The `+N more` chip appears only when at least one chip is hidden.
4. The `N` in `+N more` is always correct.
5. The layout remains stable when labels finish loading in different orders.
6. The component behaves correctly under non-default density and font scale.
7. Changing `chipIds` while labels are loading must not show stale labels from the previous input.

## Output Format

Return:

1. Corrected Kotlin code.
2. A brief explanation of the bug.
3. One test case or pseudocode test covering async loading plus non-default density/font scale.

//
Solution
//

This solution uses SubcomposeLayout because the overflow chip text depends on the hidden count, and the hidden count depends on measured widths. Compose’s layout model separates composition, measurement/layout, and drawing; when the set of children itself depends on measurement, subcomposition is the clean way to resolve that dependency.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
fun AsyncClipRow(
    chipIds: List<String>,
    modifier: Modifier = Modifier,
    loadLabel: suspend (String) -> String
) {
    var labels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(chipIds) {
        val idsForThisRequest = chipIds.toList()

        // Avoid showing labels from a previous input while the new input is loading.
        labels = emptyMap()

        val loadedLabels = coroutineScope {
            idsForThisRequest.map { id ->
                async(Dispatchers.IO) {
                    id to loadLabel(id)
                }
            }.awaitAll().toMap()
        }

        // This resumes in the LaunchedEffect coroutine context, not inside Dispatchers.IO.
        // If chipIds changes, this LaunchedEffect is cancelled before stale results are applied.
        labels = loadedLabels
    }

    SubcomposeLayout(
        modifier = modifier
            .height(40.dp)
            .clipToBounds()
    ) { constraints ->
        val childConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0
        )

        val chipPlaceables: List<Placeable> = chipIds.mapIndexed { index, id ->
            subcompose("chip-$index") {
                Chip(text = labels[id] ?: "...")
            }.single().measure(childConstraints)
        }

        val prefixWidths = IntArray(chipPlaceables.size + 1)
        for (i in chipPlaceables.indices) {
            prefixWidths[i + 1] = prefixWidths[i] + chipPlaceables[i].width
        }

        fun measureMoreChip(hiddenCount: Int): Placeable {
            return subcompose("more-$hiddenCount") {
                Chip(text = "+$hiddenCount more")
            }.single().measure(childConstraints)
        }

        val hasBoundedWidth = constraints.hasBoundedWidth
        val maxWidth = constraints.maxWidth

        var visibleCount = chipPlaceables.size
        var morePlaceable: Placeable? = null

        if (hasBoundedWidth) {
            visibleCount = 0
            morePlaceable = null

            for (candidateVisibleCount in chipPlaceables.size downTo 0) {
                val hiddenCount = chipPlaceables.size - candidateVisibleCount
                val candidateMorePlaceable =
                    if (hiddenCount > 0) measureMoreChip(hiddenCount) else null

                val totalWidth =
                    prefixWidths[candidateVisibleCount] +
                        (candidateMorePlaceable?.width ?: 0)

                if (totalWidth <= maxWidth) {
                    visibleCount = candidateVisibleCount
                    morePlaceable = candidateMorePlaceable
                    break
                }
            }
        }

        val contentWidth = prefixWidths[visibleCount] + (morePlaceable?.width ?: 0)

        val layoutWidth =
            if (hasBoundedWidth) {
                maxWidth
            } else {
                constraints.constrainWidth(contentWidth)
            }

        val maxChildHeight = maxOf(
            chipPlaceables.take(visibleCount).maxOfOrNull { it.height } ?: 0,
            morePlaceable?.height ?: 0
        )

        val layoutHeight = constraints.constrainHeight(maxChildHeight)

        layout(width = layoutWidth, height = layoutHeight) {
            var x = 0

            chipPlaceables.take(visibleCount).forEach { placeable ->
                val y = ((layoutHeight - placeable.height) / 2).coerceAtLeast(0)
                placeable.placeRelative(x, y)
                x += placeable.width
            }

            morePlaceable?.let { placeable ->
                val y = ((layoutHeight - placeable.height) / 2).coerceAtLeast(0)
                placeable.placeRelative(x, y)
            }
        }
    }
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .background(
                color = Color.LightGray,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

Why this fixes the issue

The original code first counted visible chips without reserving space for the overflow chip. That can produce a row like:

[Chip A][Chip B][Chip C]        [+1 more]

where Chip C and +1 more overlap.

The corrected code tries candidate visible counts from largest to smallest:

Can all chips fit?
Can first N-1 chips + "+1 more" fit?
Can first N-2 chips + "+2 more" fit?
...

The first candidate that fits is chosen. This avoids circular reasoning because the algorithm measures the actual +N more chip for each candidate hidden count.

The async loading is also fixed. LaunchedEffect(chipIds) cancels the previous loading job when chipIds changes, which prevents stale async results from being applied after a newer input arrives.

//
Test
//
Test cases that should be there
Test 1: no overflow when all chips fit

Purpose: verifies the component does not show +N more unnecessarily.

@Test
fun allChipsFit_noMoreChipShown() {
    composeTestRule.setContent {
        AsyncClipRow(
            chipIds = listOf("a", "b"),
            modifier = Modifier.width(400.dp),
            loadLabel = { id -> mapOf("a" to "A", "b" to "B").getValue(id) }
        )
    }

    composeTestRule.onNodeWithText("A").assertExists()
    composeTestRule.onNodeWithText("B").assertExists()
    composeTestRule.onNodeWithText("+1 more").assertDoesNotExist()
    composeTestRule.onNodeWithText("+2 more").assertDoesNotExist()
}
Test 2: overflow count is correct

Purpose: verifies +N more reflects the actual hidden chip count.

@Test
fun narrowWidth_showsCorrectHiddenCount() {
    composeTestRule.setContent {
        AsyncClipRow(
            chipIds = listOf("a", "b", "c", "d"),
            modifier = Modifier.width(170.dp),
            loadLabel = { id ->
                mapOf(
                    "a" to "Refund",
                    "b" to "International Shipping",
                    "c" to "VIP",
                    "d" to "Escalated"
                ).getValue(id)
            }
        )
    }

    composeTestRule.waitForIdle()

    // Exact count depends on real measured text width, but the test should assert
    // that only one valid "+N more" is present and that visible + hidden == total.
}

For a real automated test, add Modifier.testTag(...) to chips in a test-only version or expose tags through semantics, then read bounds and visible nodes.

Test 3: no overlap between last visible chip and +N more

Purpose: catches the original bug.

@Test
fun overflowChipDoesNotOverlapVisibleChips() {
    composeTestRule.setContent {
        AsyncClipRow(
            chipIds = listOf("a", "b", "c"),
            modifier = Modifier.width(220.dp),
            loadLabel = { id ->
                mapOf(
                    "a" to "Short",
                    "b" to "Long Long Label",
                    "c" to "Tail"
                ).getValue(id)
            }
        )
    }

    composeTestRule.waitForIdle()

    // Pseudocode:
    // val visibleChipBounds = bounds of visible normal chips
    // val moreBounds = bounds of node whose text matches Regex("""\+\d+ more""")
    // assert(visibleChipBounds.all { it.right <= moreBounds.left || it.left >= moreBounds.right })
}
Test 4: non-default density and font scale

Purpose: ensures the solution relies on Compose measurement rather than label.length * constant.

@Test
fun worksWithNonDefaultDensityAndFontScale() {
    val density = Density(
        density = 2.75f,
        fontScale = 1.3f
    )

    composeTestRule.setContent {
        CompositionLocalProvider(LocalDensity provides density) {
            AsyncClipRow(
                chipIds = listOf("a", "b", "c", "d"),
                modifier = Modifier.width(180.dp),
                loadLabel = { id ->
                    mapOf(
                        "a" to "Refund",
                        "b" to "International Shipping",
                        "c" to "VIP",
                        "d" to "Pending"
                    ).getValue(id)
                }
            )
        }
    }

    composeTestRule.waitForIdle()

    // Assert:
    // 1. A "+N more" chip exists if not all labels fit.
    // 2. Its bounds are inside the row.
    // 3. It does not overlap the visible chips.
}
Test 5: async labels complete out of order

Purpose: verifies layout stability when labels arrive in a different order from chipIds.

@Test
fun asyncLabelsOutOfOrder_layoutStillStabilizes() {
    composeTestRule.setContent {
        AsyncClipRow(
            chipIds = listOf("a", "b", "c"),
            modifier = Modifier.width(220.dp),
            loadLabel = { id ->
                when (id) {
                    "a" -> {
                        delay(100)
                        "Alpha"
                    }
                    "b" -> {
                        delay(10)
                        "Very Very Long Beta"
                    }
                    else -> {
                        delay(50)
                        "Gamma"
                    }
                }
            }
        )
    }

    composeTestRule.waitForIdle()

    // Assert:
    // - no overlap
    // - correct "+N more"
    // - no stale placeholder-only final state
}
Test 6: chipIds changes while old labels are still loading

Purpose: verifies stale results do not corrupt the current row.

@Test
fun chipIdsChange_cancelsOldLoadAndDoesNotShowStaleLabels() {
    var ids by mutableStateOf(listOf("old1", "old2"))

    composeTestRule.setContent {
        AsyncClipRow(
            chipIds = ids,
            modifier = Modifier.width(240.dp),
            loadLabel = { id ->
                if (id.startsWith("old")) {
                    delay(200)
                    "OLD-$id"
                } else {
                    delay(10)
                    "NEW-$id"
                }
            }
        )
    }

    composeTestRule.runOnIdle {
        ids = listOf("new1", "new2")
    }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("NEW-new1").assertExists()
    composeTestRule.onNodeWithText("NEW-new2").assertExists()
    composeTestRule.onNodeWithText("OLD-old1").assertDoesNotExist()
    composeTestRule.onNodeWithText("OLD-old2").assertDoesNotExist()
}
Test 7: extremely narrow width

Purpose: verifies the row stays bounded even when only the overflow chip can fit.

@Test
fun extremelyNarrowWidth_doesNotDrawOutsideBounds() {
    composeTestRule.setContent {
        AsyncClipRow(
            chipIds = listOf("a", "b", "c", "d", "e"),
            modifier = Modifier.width(64.dp),
            loadLabel = { "Long Label $it" }
        )
    }

    composeTestRule.waitForIdle()

    // Assert:
    // - no normal chip overlaps the overflow chip
    // - any rendered node bounds are inside the row bounds
    // - "+N more" is clipped within parent if necessary, not drawn outside
}
