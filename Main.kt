# Android Render Layer Cache Invalidator

## Context

A custom Android rendering engine caches expensive view subtrees into offscreen layers. Reusing a cached layer is fast, but unsafe if any visual input that affects that layer’s local pixels has changed.

The engine receives immutable render-tree snapshots over time. Your job is to decide which layer caches can be reused, which must be redrawn, which were evicted, and which reusable layers still require placement or composition updates.

## Task

Implement `LayerCacheInvalidator`.

## Starter Code

```kotlin
class LayerCacheInvalidator {
    fun update(tree: LayerTree): CacheInvalidationResult {
        TODO("Implement")
    }

    fun reset() {
        TODO("Implement")
    }
}

data class LayerTree(
    val nodes: List<LayerNode>
)

data class LayerNode(
    val id: String,
    val parentId: String?,
    val bounds: Rect,
    val layer: Boolean,
    val drawsContent: Boolean = false,
    val contentVersion: Long = 0,
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

data class CacheInvalidationResult(
    val invalidatedLayers: List<String>,
    val reusableLayers: List<String>,
    val placementChangedLayers: List<String>,
    val compositionChangedLayers: List<String>,
    val evictedLayers: List<String>,
    val diagnostics: List<String> = emptyList()
)


Requirements
Use only the Kotlin standard library.
The tree must contain exactly one root node. Node bounds are parent-relative, except the root bounds are screen-relative.
If the tree has zero roots, multiple roots, duplicate ids, missing parents, cycles, or malformed bounds, treat it as invalid. On invalid input, evict all previously tracked layer caches, return a diagnostic, and make the next valid update behave like the first valid update.
A cache exists only for nodes with layer = true.
The first valid update after construction or reset() must invalidate every currently visible layer. reset() forgets all previously tracked state.
A layer is currently visible only if it exists, has visible = true, has non-empty bounds, and all ancestors are visible and non-empty.
Hidden layers are not reported as reusable or invalidated while hidden. If a hidden layer changes while hidden, it must be invalidated before reuse when it later becomes visible.
Layer caches store local subtree pixels only. Ancestor visibility, clipping, position, and z-order do not by themselves invalidate descendant layer pixels.
Ancestor changes may require placement or composition updates for descendant layers, but descendant layer caches remain reusable unless their own local pixel inputs changed.
A layer’s local pixel inputs include its local size, drawsContent, contentVersion, clipToBounds, visible non-layer descendant content, visible non-layer descendant geometry, and visible non-layer descendant ordering up to nested layer boundaries.
Changes inside a nested layer must not invalidate an ancestor layer’s local pixels, but may require composition changes for the ancestor layer.
Moving a visible layer without changing local size or local pixel inputs keeps its cache reusable and reports placementChanged.
Resizing a visible layer, or changing its drawsContent, contentVersion, or clipToBounds, invalidates that layer.
Adding a visible layer invalidates it. Removing a layer, or changing layer = true to layer = false, evicts that layer and all previously tracked descendant layer caches.
Changing a non-layer descendant’s visible content or geometry invalidates the nearest visible ancestor layer. If there is no visible ancestor layer, no layer cache is invalidated for that change.
Reparenting a visible layer may preserve its cache if local pixel inputs are unchanged, but must report placement and/or composition changes when its screen placement or containing composition changes.
zIndex orders only siblings under the same parent. Higher zIndex draws above lower zIndex; ties are resolved by lexicographically ascending id. Changing z-order among overlapping visible siblings reports compositionChanged for the nearest visible ancestor layer; non-overlapping z-order changes must not create unrelated invalidations.
Each output list must contain no duplicates, be sorted lexicographically, and be deterministic regardless of input node list order.
Output Format
Return:
Full Kotlin implementation.
Brief explanation of cache invalidation, reuse, eviction, placement changes, composition changes, hidden subtrees, and invalid-tree handling.
Important tests or pseudocode tests.
Important Hard Test Cases
Hidden subtree changes, then becomes visible
A visible cached layer becomes hidden. While hidden, a child’s contentVersion changes. Verify no invalidation or reuse is reported while hidden. When the layer becomes visible again, verify it is invalidated before reuse.
Moving a cached layer preserves pixels
A visible layer changes only its parent-relative position, not size, content, clipping, or descendants. Verify the layer appears in reusableLayers and placementChangedLayers, not invalidatedLayers.
Resizing a cached layer invalidates pixels
A visible layer keeps the same id and contentVersion, but its width or height changes. Verify the layer is invalidated, not merely placement-changed.
Ancestor movement does not invalidate descendant pixels
Move a non-layer parent of a visible cached descendant. Verify the descendant layer remains reusable, but placement change is reported because its screen position changed.
Ancestor clip change does not invalidate descendant local pixels
A clipping ancestor shrinks or expands over a child layer. Verify the child layer cache remains reusable. Verify composition change is reported where the containing layer’s composition is affected.
Non-layer content change invalidates nearest ancestor layer
A non-layer child inside layer L changes contentVersion. Verify L is invalidated. Verify higher ancestor layers are not invalidated unless their own local pixel inputs changed.
Nested layer isolates ancestor pixels
A child layer inside parent layer P changes contentVersion. Verify the child layer is invalidated, while P remains reusable. Verify P reports composition change because one composed child layer changed.
Removed layer evicts descendant caches
Remove a cached layer that contains nested cached descendants. Verify the removed layer and all previously tracked descendant layers appear in evictedLayers.
Layer boundary added and removed
Change a visible node from layer = false to layer = true. Verify the new layer is invalidated. Then change it back to layer = false. Verify the old layer cache is evicted.
Reparent under equivalent local inputs
Move a visible layer to a different parent without changing its local size, content, clipping, or descendants. Verify the layer cache is reusable, with placement and/or composition change reported.
Reparent with local input change
Reparent a visible layer and also change its local size or clipToBounds. Verify the layer is invalidated, not reused.
Overlapping sibling z-order flip
Two visible sibling layers overlap and swap zIndex. Verify child layer pixels remain reusable, but the nearest visible ancestor layer reports compositionChanged.
Non-overlapping z-order change
Change zIndex among visible siblings that do not overlap. Verify no layer is invalidated and no unrelated composition change is reported.
Invalid tree evicts tracked caches
After a valid update with several visible layers, feed trees with duplicate ids, missing parent, multiple roots, cycle, or malformed bounds. Verify all previously tracked layer caches are evicted, a diagnostic is returned, and the next valid update behaves like the first valid update.
Determinism under shuffled input
Use the same logical tree with nodes in different list orders. Verify all output lists are identical, deduplicated, and sorted lexicographically.
Mixed complex update
In one update: move one layer, resize another, change non-layer content inside a third, remove a nested layer, reparent a layer, and flip z-order of overlapping siblings. Verify reusable, invalidated, evicted, placementChanged, and compositionChanged outputs are all correct with no stale cache reuse.


