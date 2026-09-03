Given a start shape, an end shape, a progress value, a size, density, and layout direction, it must generate the intermediate Outline for that shape.

Use Jetpack Compose UI geometry/graphics APIs only. Do not use Android View, Canvas, Material component implementations, Material3 Shapes, RoundedCornerShape, CutCornerShape, or third-party shape libraries to generate the morph.


# Material 3 Shape Morph Outline Engine

## Context

A Jetpack Compose UI needs to animate between Material-style container shapes, such as rounded cards, cut-corner surfaces, asymmetric dialogs, and adaptive containers whose size changes during animation.

The task is to generate a valid Compose `Outline` for any morph progress. The hard part is not running an animation; the hard part is producing correct, deterministic shape geometry at each progress value.

## Task

Implement `MaterialShapeMorph`.

## Starter Code

```kotlin
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class MaterialShapeMorph(
    private val from: ShapeSpec,
    private val to: ShapeSpec,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        TODO("Implement shape morph outline")
    }
}

@Immutable
data class ShapeSpec(
    val topStart: CornerSpec,
    val topEnd: CornerSpec,
    val bottomEnd: CornerSpec,
    val bottomStart: CornerSpec
)

@Immutable
data class CornerSpec(
    val family: CornerFamily,
    val size: CornerSizeSpec
)

enum class CornerFamily {
    Rounded,
    Cut
}

sealed class CornerSizeSpec {
    data class Dp(val value: Float) : CornerSizeSpec()
    data class Percent(val value: Float) : CornerSizeSpec()
}




Requirements
Use Jetpack Compose UI geometry/graphics APIs only. Do not use Android View, Canvas, Material component implementations, or third-party shape libraries.
progress is clamped to [0f, 1f]. progress <= 0f must produce the from geometry. progress >= 1f must produce the to geometry.
Logical corners map through LayoutDirection. In LTR: topStart/topEnd/bottomEnd/bottomStart map to top-left/top-right/bottom-right/bottom-left. In RTL, start/end are swapped horizontally.
CornerSizeSpec.Dp resolves through the provided Density. CornerSizeSpec.Percent resolves as a percentage of min(width, height). Negative resolved sizes are treated as 0.
Each corner’s resolved size is interpolated independently from from to to.
Corner family morphs independently per corner. A rounded corner at one endpoint and a cut corner at the other must produce a continuous intermediate corner, not switch abruptly.
Resolved corner sizes must be clamped so adjacent corners never cross. Preserve corner-size ratios while ensuring top, bottom, left, and right corner pairs fit within the current outline size.
The generated outline must stay inside [0, width] x [0, height].
The generated path must be closed, continuous, deterministic, and free of NaN or infinite coordinates.
Zero-width or zero-height input size must produce an empty rectangular outline and must not crash.
Recomputing the outline at a different Size, Density, or LayoutDirection must use the current inputs and must not reuse stale resolved corner sizes.
The same inputs must always produce the same outline.






Important Hard Test Cases
[Unit] Progress clamps to endpoints
Use progress = -0.5f and progress = 1.5f. Verify the generated geometry equals the start and end geometry respectively.
[Unit] Asymmetric rounded interpolation
Morph between two shapes where all four rounded corners have different sizes. Verify each physical corner interpolates independently and does not swap with another corner.
[Unit] Rounded-to-cut midpoint
Morph a rounded corner into a cut corner at progress = 0.5f. Verify the corner is continuous, closed, inside bounds, and visibly between arc-like and straight-cut geometry.
[Unit] Mixed corner families
Use a shape with rounded top-left/bottom-right and cut top-right/bottom-left, then morph to the opposite. Verify each corner morphs independently without abrupt family switching.
[Unit] Oversized corner clamping
Use a small rectangle with corner sizes larger than width and height. Verify the generated outline stays inside bounds and adjacent corners do not cross.
[Unit] Ratio-preserving clamp
Use asymmetric oversized corners where only one edge overflows. Verify clamping preserves relative corner-size ratios instead of independently chopping corners unpredictably.
[Unit] Percentage sizes recompute with bounds
Use percent-based corners and call createOutline with different sizes. Verify corner sizes are based on the current min(width, height) every time.
[Unit] Density-sensitive dp sizes
Use dp-based corners under two different densities. Verify resolved pixel sizes change with density and no stale values are reused.
[Unit] RTL logical corner mapping
Use distinct corner sizes for topStart, topEnd, bottomStart, and bottomEnd. Verify LTR and RTL place them on the correct physical corners.
[Unit] Zero and thin sizes
Call createOutline with zero width, zero height, and very thin rectangles. Verify no crash, no NaN, and valid clamped geometry.
[Unit] Morph continuity
Evaluate progress at 0f, 0.25f, 0.5f, 0.75f, and 1f. Verify corner sizes and family interpolation change monotonically without identity swaps or jumps.
[Unit] Deterministic output
Generate the same outline repeatedly with the same size, density, direction, and progress. Verify identical geometry or identical rendered result.
[Roborazzi] Asymmetric midpoint visual
Render a complex asymmetric rounded-to-cut morph at progress = 0.5f. Verify the screenshot matches the expected golden.
[Roborazzi] RTL visual regression
Render the same logical shape in LTR and RTL. Verify start/end corners visually swap correctly.
[Roborazzi] Oversized corner visual regression
Render a tiny container with huge rounded and cut corners. Verify the result is clamped cleanly with no self-intersection artifacts.
[Roborazzi] Resizing during morph
Render the same morph at the same progress while the container size changes. Verify corners adapt to the new size without stale-radius artifacts.
[Compose UI] Endpoint visual equality
At progress = 0f and progress = 1f, render the morph and render equivalent standalone start/end specs. Verify endpoints visually match.
Reference Solution
The correct solution should treat the morph as deterministic geometry generation.
For each createOutline call:
Clamp progress to [0, 1].
Resolve the current width and height from Size.
If width or height is zero, return an empty rectangle outline.
Map logical corners to physical corners using LayoutDirection.
Resolve each corner size:
dp through Density
percent from min(width, height)
negative values to 0
Interpolate each physical corner size independently.
Interpolate each corner’s family value independently:
cut = 0
rounded = 1
Clamp all four corner sizes together so no adjacent pair crosses the available width or height.
Build one closed clockwise path inside the rectangle.
A robust clamping policy is ratio-preserving:
scale = minOf(
    1,
    width / (topLeft + topRight) if that sum > 0,
    width / (bottomLeft + bottomRight) if that sum > 0,
    height / (topLeft + bottomLeft) if that sum > 0,
    height / (topRight + bottomRight) if that sum > 0
)

finalCornerSize = rawCornerSize * scale
For each corner, use the same endpoints as both rounded and cut corners. For example, top-left uses:
start point = (left + radius, top)
end point   = (left, top + radius)
For a cut corner, connect those endpoints with a straight line.
For a rounded corner, connect them with a quarter-curve.
For an intermediate rounded/cut morph, interpolate the curve controls between the straight-line representation and the rounded-corner representation. This avoids an abrupt switch from line to arc.
The path should be constructed in a fixed order:
top edge
top-right corner
right edge
bottom-right corner
bottom edge
bottom-left corner
left edge
top-left corner
close
The solution must not cache resolved pixel sizes across calls, because size, density, layout direction, and progress can all change independently.

