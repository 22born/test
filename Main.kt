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
In one update: move one layer, resize another, change non-layer content inside a third, remove a nested layer, reparent a layer, and flip z-order of overlapping siblings. Verify reusable, invalidated, evicted, placementChanged, and compositionChanged outputs are all correct with no stale cache reuse
