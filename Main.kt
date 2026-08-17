Android Incremental Render Damage Tracker
Context
A custom Android rendering engine draws a complex UI tree onto a canvas. Nodes can move, resize, animate, clip children, overlap, change z-order, or become hidden. Redrawing the full screen every frame is too expensive, so the engine must compute the smallest safe set of dirty rectangles to redraw.
This is hard because an update can invalidate both the old and new visual positions, clipping changes can expose hidden pixels, z-order changes can reveal covered content, and parent transforms affect entire subtrees.
Task idea
Implement a tracker that receives immutable render-tree snapshots over time and returns the screen-space regions that must be redrawn.
Small starter API
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
Why it is very hard
The solver must reason about:
- old and new bounds
- subtree movement
- parent clipping
- visibility changes
- z-order changes
- overlapping siblings
- removed nodes
- inserted nodes
- deterministic rectangle merging
- avoiding both under-invalidation and excessive redraw
Hard requirements

1. First update after construction or reset must damage the visible area of the whole tree.

2. Moving a visible node must damage both its old and new screen-space visible regions.

3. Resizing a visible node must damage both old and new visible regions.

4. Hiding a node must damage its old visible region.

5. Showing a node must damage its new visible region.

6. Removing a node must damage its old visible region.

7. Adding a node must damage its new visible region.

8. Parent movement, resize, visibility change, or clipping change must affect descendants.

9. A node clipped by an ancestor must only damage the clipped visible region.

10. Changing zIndex among overlapping visible nodes must damage the affected overlap region.

11. Non-overlapping zIndex changes must not create unnecessary damage.

12. Dirty rectangles must be merged when they overlap or touch.

13. Dirty rectangle output must be deterministic regardless of node list order.

14. The tracker must never return a rectangle outside the current or previous root visible area.

15. It is acceptable to over-damage, but not to under-damage.

16. If the tree is invalid, such as a missing parent or cycle, return full damage for safety.



tests:
1. Parent move with clipped descendant
A clipping parent contains a child that extends outside its bounds. Move the parent. Verify damage includes the old and new clipped visible regions, not the child’s full unclipped bounds.

2. Clip shrink exposes old pixels
A clipping parent shrinks so part of a previously visible child becomes clipped out. Verify the old visible area that disappeared is damaged.

3. Clip expand reveals new pixels
A clipping parent expands while the child stays unchanged. Verify the newly visible child region is damaged.

4. Nested clipping
A child is clipped by multiple ancestors. Move or resize one ancestor. Verify damage is intersected with all old and new active clips.

5. Reparent across different clips
Move a visible node from one parent to another with different clipping bounds. Verify damage includes the old clipped region and the new clipped region.

6. Parent hidden with visible descendants
Set a visible parent with visible descendants to hidden. Verify damage includes the previously visible clipped union of the subtree.

7. Hidden parent becomes visible after child changed while hidden
Change a child while its parent is hidden, then later show the parent. Verify no damage occurs while hidden, and correct visible subtree damage occurs when shown.

8. Z-index swap with overlapping siblings
Two visible siblings overlap and swap zIndex. Verify the affected overlap region is damaged.

9. Z-index change without overlap
A node changes zIndex relative to non-overlapping siblings. Verify no unnecessary damage is emitted for unrelated regions.

10. Higher-z node hides and reveals lower content
A top overlapping node becomes hidden. Verify its old visible region is damaged so the lower node can be redrawn.

11. Dirty rectangle merge transitivity
Produce regions where A overlaps B and B overlaps C, but A does not directly overlap C. Verify all merge into one deterministic rect.

12. Determinism under shuffled node order
Use the same logical tree with nodes in different list orders. Verify identical dirty rect output.

13. Invalid tree returns full damage
Use missing parent, duplicate ids, or a parent cycle. Verify full safe damage is returned and the tracker does not crash or loop.

14. Root bounds shrink/expand
Shrink and expand the root between updates. Verify damage is clipped to previous/current root visible area and newly exposed root area is damaged.

15. Clip flag toggles
Toggle a parent from unclipped to clipped, then clipped to unclipped. Verify disappearing outside-child regions and newly revealed outside-child regions are damaged.

16. Large subtree move
Move a parent containing descendants that extend beyond the parent. Verify damage covers the visible union of the subtree’s old and new screen-space regions.

17. Multiple simultaneous changes
In one update, combine move, hide, remove, add, zIndex change, and clip expansion. Verify the result safely covers all affected visible regions.

18. Randomized stress
Generate random valid trees and mutations. Verify invariants: deterministic output, no dirty rect outside allowed root areas, merged rects do not overlap/touch, and no known changed visible region is omitted.
