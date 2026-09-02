Change these three requirements.
Replace Requirement #2
Current items are laid out vertically in the order of `items` using their current animated measured heights. Removed exit copies may remain visually during exit, but must not affect the measured layout of current items.
Replace Requirement #3
Each item’s target size is `widthPx` by `heightPx` pixels, and both values must be positive. When `widthPx` or `heightPx` changes, the item’s measured size must animate from its previous measured size to the new target size.
Replace Requirement #5
Items and exit copies must use integer-pixel bounds. Animated x, y, width, and height values are interpolated as floats, then rounded with `roundToInt()` before measurement, placement, or drawing. Exit copies are drawn after current items, so they appear above current items when their bounds overlap.
