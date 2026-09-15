Replace the old requirements with:
Step circles have outer diameter `10dp`, are vertically centered on the bar, and have a `0.5dp` stroke drawn fully inside the circle bounds.

Step circle centers are spaced at `30dp` intervals. In LTR, step `n` has center x = `n * 30dp`.

In LTR, step `n` has outer bounds from `n * 30dp - 5dp` to `n * 30dp + 5dp`.

The progress coordinate of a step is the leading edge of that step’s circle. In LTR, step `n` has progress coordinate `n * 30dp - 5dp`.
And update the geometry test:
[Robolectric/Bitmap] fixed LTR geometry
At mdpi with stepCount = 4, verify circle centers are 30, 60, 90, and 120 px. Verify circle outer bounds are [25,35], [55,65], [85,95], and [115,125]. Verify progress coordinates are 25, 55, 85, and 115 px.
For xhdpi:
[Robolectric/Bitmap] fixed LTR geometry at xhdpi
With stepCount = 4, verify circle centers are 60, 120, 180, and 240 px. Verify circle outer bounds are [50,70], [110,130], [170,190], and [230,250]. Verify the 1px circle stroke stays inside those bounds.
