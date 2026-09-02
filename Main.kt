Replace Requirement #2 with:
Current items participate in the column’s measured layout in `items` order. Removed exit copies are drawn as overlays and must not affect the measured layout of current items.
Keep/replace Requirement #3 with:
Each item’s target size is `widthPx` by `heightPx` pixels, and both values must be positive. When `widthPx` or `heightPx` changes, that item’s measured size must animate from its previous measured size to the new target size.
Replace Requirement #5 with:
Items and exit copies must use integer-pixel bounds. Animated x, y, width, and height values are interpolated as floats, then rounded with `roundToInt()` before measurement, placement, or drawing. Exit copies are drawn after current items, so they appear above current items when their bounds overlap.
