17. zIndex orders only siblings under the same parent. Siblings with higher zIndex draw above siblings with lower zIndex; ties are resolved by lexicographically ascending id. Ancestor/descendant draw order follows the tree structure.

21. Merged dirtyRects must be sorted ascending by left, then top, then right, then bottom.

22. Damage must include exactly the changed visible screen-space regions, except that overlapping or touching dirty regions are merged into their bounding rectangle as required above. Under-damage is never allowed.

18. If a zIndex change causes the relative paint order of overlapping visible screen-space regions to change, damage the affected overlap region. This includes changes caused by an ancestor sibling’s zIndex.

