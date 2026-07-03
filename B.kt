# Context

A mobile dashboard builder represents a layout as a slicing tree.

A leaf is a widget. An internal node is a cut.

A vertical cut places the first child left of the second:

width = firstWidth + gap + secondWidth
height = max(firstHeight, secondHeight)

A horizontal cut places the first child above the second:

width = max(firstWidth, secondWidth)
height = firstHeight + gap + secondHeight

A widget may be rotated only if `rotatable == true`.

The goal is to choose widget orientations so the final layout has the minimum area while satisfying:

minAspect <= width / height <= maxAspect

The function must also return each widget’s coordinates. The root starts at `(0, 0)`.

For a vertical cut:
- first child starts at `(x, y)`
- second child starts at `(x + firstWidth + gap, y)`

For a horizontal cut:
- first child starts at `(x, y)`
- second child starts at `(x, y + firstHeight + gap)`

# Task

Implement:

```kotlin
fun optimizeLayout(
    root: LayoutNode,
    minAspect: Double,
    maxAspect: Double
): OptimizedLayout?



  Return null if no valid layout exists.
Required Types
sealed class LayoutNode

data class WidgetNode(
    val id: String,
    val width: Int,
    val height: Int,
    val rotatable: Boolean
) : LayoutNode()

data class CutNode(
    val type: CutType,
    val gap: Int,
    val first: LayoutNode,
    val second: LayoutNode
) : LayoutNode()

enum class CutType {
    VERTICAL,
    HORIZONTAL
}

data class RectSize(
    val width: Int,
    val height: Int
)

data class WidgetPlacement(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class OptimizedLayout(
    val width: Int,
    val height: Int,
    val area: Long,
    val placements: List<WidgetPlacement>
)
Requirements
Return the minimum-area valid layout.
If tied by area, choose smaller width.
If still tied, choose smaller height.
Every widget ID appears at most once.
Every widget must be placed exactly once.
Widgets must not overlap.
Widget dimensions are positive.
Gaps are non-negative.
Use only Kotlin standard library.
Constraints
1 <= number of widgets <= 16
1 <= widget width, widget height <= 10_000
0 <= gap <= 10_000
0.1 <= minAspect <= maxAspect <= 10.0
