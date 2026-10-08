
strongest cases:
Expand to actual content height, capped at 7 lines. A 3-line text expands only to 3 lines, while 10-line text expands only to 7. This forces real measurement instead of hardcoding the expanded height.
Reverse during expansion. After the 500ms debounce, let expansion run for 300ms of its 800ms duration, then tap collapse. After the new 500ms stable period, collapse must animate from the currently displayed height, not jump to 7 lines first.
Reverse multiple times. Expand → partially expand → collapse → partially collapse → expand. Every transition must begin from the current rendered height.
Content changes while expanded. Replace a 7-line string with a 3-line string. The component should animate smoothly to the new measured height rather than snap.
Width changes while expanded. A parent width/orientation change causes different text wrapping. The component must remeasure and animate to the new appropriate height.
Expansion becomes unnecessary. While expanded, replace the content with text that fits within 2 lines. The component must collapse correctly and remove the Show More control without leaving stale expanded state.
Very rapid tap at the debounce boundary. Tap expand, then collapse at exactly/approximately 500ms. The implementation must have deterministic behavior with no simultaneous expand/collapse animation caused by racing coroutines.
Expansion while previous height animation is unfinished. A newly accepted expanded/collapsed state must supersede the previous height animation rather than queue animations:
2 lines
   ↓ expanding
4.3 lines     ← currently rendered
   ↑ new collapse accepted
   ↓
2 lines
