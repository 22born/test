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







Correct Kotlin solution
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

private sealed class LayoutPlan {
    abstract val width: Int
    abstract val height: Int
}

private data class WidgetPlan(
    val id: String,
    override val width: Int,
    override val height: Int
) : LayoutPlan()

private data class CombinedPlan(
    val type: CutType,
    val gap: Int,
    val first: LayoutPlan,
    val second: LayoutPlan,
    override val width: Int,
    override val height: Int
) : LayoutPlan()

fun optimizeLayout(
    root: LayoutNode,
    minAspect: Double,
    maxAspect: Double
): OptimizedLayout? {
    val plans = buildPlans(root)

    val best = plans
        .filter { plan ->
            val aspect = plan.width.toDouble() / plan.height.toDouble()
            aspect >= minAspect && aspect <= maxAspect
        }
        .minWithOrNull(
            compareBy<LayoutPlan> { it.width.toLong() * it.height.toLong() }
                .thenBy { it.width }
                .thenBy { it.height }
        )
        ?: return null

    val placements = mutableListOf<WidgetPlacement>()
    collectPlacements(best, 0, 0, placements)

    return OptimizedLayout(
        width = best.width,
        height = best.height,
        area = best.width.toLong() * best.height.toLong(),
        placements = placements
    )
}

private fun buildPlans(node: LayoutNode): List<LayoutPlan> {
    return when (node) {
        is WidgetNode -> {
            val plans = mutableListOf<LayoutPlan>()

            plans.add(
                WidgetPlan(
                    id = node.id,
                    width = node.width,
                    height = node.height
                )
            )

            if (node.rotatable && node.width != node.height) {
                plans.add(
                    WidgetPlan(
                        id = node.id,
                        width = node.height,
                        height = node.width
                    )
                )
            }

            deduplicateBySize(plans)
        }

        is CutNode -> {
            val firstPlans = buildPlans(node.first)
            val secondPlans = buildPlans(node.second)
            val combined = mutableListOf<LayoutPlan>()

            for (first in firstPlans) {
                for (second in secondPlans) {
                    when (node.type) {
                        CutType.VERTICAL -> {
                            combined.add(
                                CombinedPlan(
                                    type = CutType.VERTICAL,
                                    gap = node.gap,
                                    first = first,
                                    second = second,
                                    width = first.width + node.gap + second.width,
                                    height = maxOf(first.height, second.height)
                                )
                            )
                        }

                        CutType.HORIZONTAL -> {
                            combined.add(
                                CombinedPlan(
                                    type = CutType.HORIZONTAL,
                                    gap = node.gap,
                                    first = first,
                                    second = second,
                                    width = maxOf(first.width, second.width),
                                    height = first.height + node.gap + second.height
                                )
                            )
                        }
                    }
                }
            }

            deduplicateBySize(combined)
        }
    }
}

private fun deduplicateBySize(plans: List<LayoutPlan>): List<LayoutPlan> {
    val bySize = LinkedHashMap<Pair<Int, Int>, LayoutPlan>()

    for (plan in plans) {
        val key = plan.width to plan.height
        if (key !in bySize) {
            bySize[key] = plan
        }
    }

    return bySize.values.toList()
}

private fun collectPlacements(
    plan: LayoutPlan,
    x: Int,
    y: Int,
    output: MutableList<WidgetPlacement>
) {
    when (plan) {
        is WidgetPlan -> {
            output.add(
                WidgetPlacement(
                    id = plan.id,
                    x = x,
                    y = y,
                    width = plan.width,
                    height = plan.height
                )
            )
        }

        is CombinedPlan -> {
            when (plan.type) {
                CutType.VERTICAL -> {
                    collectPlacements(
                        plan = plan.first,
                        x = x,
                        y = y,
                        output = output
                    )

                    collectPlacements(
                        plan = plan.second,
                        x = x + plan.first.width + plan.gap,
                        y = y,
                        output = output
                    )
                }

                CutType.HORIZONTAL -> {
                    collectPlacements(
                        plan = plan.first,
                        x = x,
                        y = y,
                        output = output
                    )

                    collectPlacements(
                        plan = plan.second,
                        x = x,
                        y = y + plan.first.height + plan.gap,
                        output = output
                    )
                }
            }
        }
    }
}
Crucial test cases
1. Single non-rotatable widget, valid aspect
Input: one widget A(4, 2, rotatable=false), minAspect=1.0, maxAspect=3.0.
Expected: returns layout width=4, height=2, area=8, one placement A at (0,0) with size 4x2.
2. Single widget invalid aspect
Input: one widget A(10, 1, rotatable=false), minAspect=0.5, maxAspect=2.0.
Expected: returns null.
3. Rotation needed to satisfy aspect
Input: one widget A(10, 2, rotatable=true), minAspect=0.1, maxAspect=0.5.
Expected: returns rotated layout width=2, height=10, area=20.
4. Rotation not allowed
Input: one widget A(10, 2, rotatable=false), minAspect=0.1, maxAspect=0.5.
Expected: returns null.
5. Vertical cut coordinate placement
Tree: vertical cut with gap 2.
First child: A(3, 4)
Second child: B(5, 2)
Expected final size: width=3+2+5=10, height=max(4,2)=4.
Expected placements:
A at (0,0) with size 3x4
B at (5,0) with size 5x2
6. Horizontal cut coordinate placement
Tree: horizontal cut with gap 3.
First child: A(4, 2)
Second child: B(2, 5)
Expected final size: width=max(4,2)=4, height=2+3+5=10.
Expected placements:
A at (0,0) with size 4x2
B at (0,5) with size 2x5
7. Minimum-area layout is not from locally obvious orientation
Use a tree where both widgets are rotatable:
Vertical cut, gap 0.
A(8, 2, rotatable=true)
B(2, 8, rotatable=true)
With aspect constraint around square, for example minAspect=0.8, maxAspect=1.25.
Expected: chooses orientations producing width=10, height=8 or width=8, height=10, whichever satisfies the tie rules and minimum area.
This verifies that all valid rotations are considered.
8. Tie by area chooses smaller width
Create two possible valid layouts with same area but different width.
Example: single rotatable widget A(2, 6, rotatable=true), minAspect=0.1, maxAspect=10.0.
Possible layouts:
2x6, area 12
6x2, area 12
Expected: chooses 2x6 because area ties and width 2 is smaller than width 6.
9. Gap affects size and coordinates
Tree: vertical cut with gap 10.
A(2, 2) and B(3, 3).
Expected final size: width=15, height=3.
Expected placements:
A at (0,0)
B at (12,0)
This verifies the gap is included in both size and placement.
10. Nested slicing tree coordinates
Tree:
Horizontal cut gap 1:
first child: vertical cut gap 2 of A(2,2) and B(3,4)
second child: C(5,1)
Expected:
first subtree size: width=2+2+3=7, height=4
root size: width=max(7,5)=7, height=4+1+1=6
A at (0,0)
B at (4,0)
C at (0,5)
11. No valid aspect among all rotations
Use a tree where every possible final rectangle is too wide or too tall for the aspect range.
Expected: returns null.
This verifies the final aspect constraint is checked after optimization, not ignored.
12. Deep nested tree
Create a chain of 16 widgets connected by cuts.
Expected:
returns a valid layout if aspect allows it,
every widget appears exactly once,
no duplicate placements,
no missing widgets,
no crash from nested recursion.
13. Maximum-size dimensions do not overflow area
Use widgets with dimensions near 10_000 and gaps near 10_000.
Expected:
area is computed correctly as Long,
no integer overflow in area,
returned OptimizedLayout.area == width.toLong() * height.toLong().
14. Duplicate final sizes from different rotations
Use square widgets or rotatable widgets where rotation produces the same size.
Expected:
function still returns a correct layout,
no duplicate or missing widget placements,
output remains deterministic enough to satisfy size and placement rules.
15. Alignment empty space does not shift children
Vertical cut:
A(2, 10) and B(2, 2) with gap 0.
Expected final size: 4x10.
Expected:
A at (0,0)
B at (2,0)
B should not be vertically centered or shifted; it starts at the same y.
