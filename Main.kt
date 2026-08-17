Context
A custom Android rendering engine draws a tree of view-like nodes onto a canvas. Nodes can move, resize, become hidden, clip descendants, overlap siblings, change z-order, or be added/removed between frames.
Redrawing the full screen every frame is too expensive, so the engine must compute safe dirty rectangles: screen-space regions that must be redrawn after each tree update.
Task
Implement RenderDamageTracker.
Starter Code
class RenderDamageTracker {
    fun update(tree: RenderTree): DamageResult {
        TODO("Implement")
    }

    fun reset() {
        TODO("Implement")
    }
}

data class RenderTree(
    val nodes: List<RenderNode>
)

data class RenderNode(
    val id: String,
    val parentId: String?,
    val bounds: Rect,
    val visible: Boolean = true,
    val clipToBounds: Boolean = false,
    val zIndex: Int = 0
)

data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class DamageResult(
    val dirtyRects: List<Rect>
)
Requirements
The tree must contain exactly one root node, defined as the only node with parentId = null.
Node bounds are parent-relative, except the root bounds are screen-relative. All dirtyRects must be absolute screen-space rectangles after applying ancestor positions and clipping.
A rectangle is empty if left == right or top == bottom. A rectangle is malformed if left > right or top > bottom.
If the tree has zero roots, multiple roots, duplicate ids, missing parents, cycles, or malformed bounds, treat it as invalid and return full damage.
Full damage means the union of the previous valid root screen-space bounds and the current valid root screen-space bounds. On the first update after construction or reset(), full damage means the current valid root screen-space bounds.
Dirty rectangles must not extend outside the union of the previous valid root screen-space bounds and the current valid root screen-space bounds.
First update() after construction or reset() must damage the visible area of the whole current tree.
Moving a visible node must damage both its old and new screen-space visible regions.
Resizing a visible node must damage both its old and new screen-space visible regions.
Hiding a node must damage its old screen-space visible region.
Showing a node must damage its new screen-space visible region.
Removing a node must damage its old screen-space visible region.
Adding a node must damage its new screen-space visible region.
Parent movement, resize, visibility change, clipping change, or removal must affect descendants.
A node clipped by an ancestor must damage only the region visible after all active ancestor clips are applied.
Changing clipToBounds must damage regions that become newly visible or newly hidden because of the clipping change.
zIndex orders only siblings under the same parent. Siblings with higher zIndex draw above siblings with lower zIndex; ties are resolved by ascending traversal order, then lexicographic id. Ancestor/descendant draw order follows the tree structure.
Changing zIndex among overlapping visible siblings must damage the affected overlap region.
Changing zIndex among non-overlapping siblings must not create unrelated damage.
Dirty rectangles must be merged when they overlap or touch.
Merged dirty rectangle output must be deterministic regardless of node list order.
It is acceptable to over-damage, but not to under-damage.
reset() forgets the previous tree. The next valid update() after reset() behaves like the first update.


1. Parent move with clipped descendant
A clipping parent contains a child that extends outside its bounds. Move the parent. Verify dirtyRects are absolute screen-space rects and include old/new clipped visible regions, not local or unclipped bounds.

2. Root move or resize
Move or resize the root. Verify damage is bounded by the union of previous and current root screen-space bounds, not only the current root.

3. First update after reset
Call reset(), then update with a valid tree. Verify damage is based only on the current root bounds.

4. Clip shrink exposes old pixels
A clipping parent shrinks so part of a previously visible child becomes clipped out. Verify the disappeared old visible region is damaged.

5. Clip expand reveals new pixels
A clipping parent expands while the child stays unchanged. Verify the newly visible child region is damaged.

6. Nested clipping
A child is clipped by multiple ancestors. Move or resize one ancestor. Verify damage is intersected with all old and new active clips in screen space.

7. Reparent across different clips
Move a visible node from one parent to another with different clipping bounds. Verify damage includes old clipped screen-space region and new clipped screen-space region.

8. Hidden parent becomes visible after child changed while hidden
Change a child while its parent is hidden, then later show the parent. Verify no damage occurs while hidden, and correct visible subtree damage occurs when shown.

9. Sibling zIndex swap with overlap
Two visible siblings under the same parent overlap and swap zIndex. Verify the affected overlap region is damaged.

10. zIndex change across different parents
Change zIndex values on nodes under different parents. Verify they are not globally compared; zIndex affects only sibling ordering.

11. zIndex change without overlap
A node changes zIndex relative to non-overlapping siblings. Verify no unrelated damage is emitted.

12. Higher-z sibling hides and reveals lower sibling
A top overlapping sibling becomes hidden. Verify its old visible region is damaged so lower content can redraw.

13. Dirty rectangle merge transitivity
Produce regions where A overlaps B and B overlaps C, but A does not directly overlap C. Verify all merge into one deterministic rect.

14. Determinism under shuffled node order
Use the same logical tree with nodes in different list orders. Verify identical dirty rect output.

15. Invalid tree returns full damage
Use zero roots, multiple roots, duplicate ids, missing parent, parent cycle, or malformed bounds. Verify full damage is returned and the tracker does not crash or loop.

16. Empty rectangles are ignored, malformed rectangles are invalid
Use zero-width or zero-height bounds and verify they do not create bogus damage. Use left > right or top > bottom and verify invalid-tree full damage.

17. Clip flag toggles
Toggle a parent from unclipped to clipped, then clipped to unclipped. Verify disappearing outside-child regions and newly revealed outside-child regions are damaged.

18. Large subtree move
Move a parent containing descendants that extend beyond the parent. Verify damage covers the visible union of the subtree’s old and new screen-space regions.

19. Multiple simultaneous changes
In one update, combine move, hide, remove, add, zIndex change, and clip expansion. Verify the result safely covers all affected visible regions.

20. Randomized stress
Generate random valid trees and mutations. Verify invariants: absolute dirty rects, deterministic output, no rect outside previous/current root union, merged rects do not overlap/touch, and no known changed visible region is omitted.
