Symmetric Skin Weight Solver

Context

In a 3D character rigging pipeline, each mesh vertex is influenced by skeleton bones through skin weights. Bad weights can cause broken deformation, asymmetry, or engine export failures.

You need to implement a solver that repairs skin weights while respecting locked influences and left/right symmetry.

Task

Implement:

class SkinWeightSolver {
    fun solve(
        mesh: Mesh,
        skeleton: Skeleton,
        options: SolveOptions
    ): SkinWeightResult
}

Use these models, or equivalent ones:

typealias BoneId = String

data class Mesh(
    val vertices: List<Vertex>,
    val mirrorVertex: Map<Int, Int>
)

data class Vertex(
    val id: Int,
    val weights: Map<BoneId, Float>,
    val lockedBones: Set<BoneId> = emptySet()
)

data class Skeleton(
    val bones: Set<BoneId>,
    val mirrorBone: Map<BoneId, BoneId>,
    val rootBone: BoneId
)

data class SolveOptions(
    val maxInfluences: Int = 4,
    val minWeight: Float = 0.001f,
    val tolerance: Float = 0.0001f
)

data class SkinWeightResult(
    val weights: Map<Int, Map<BoneId, Float>>,
    val diagnostics: List<String>
)

Requirements

1. For every solvable vertex, output weights must be non-negative, use only known bones, sum to "1.0" within tolerance, and contain at most "maxInfluences" bones.

2. Unknown unlocked bones must be removed.

3. Weights below "minWeight" may be pruned unless the bone is locked.

4. Known locked bone weights must be preserved exactly.

5. If locked constraints make a vertex impossible, report a diagnostic. Impossible cases include locked unknown bones, negative locked weights, locked weights summing above "1.0", or more locked bones than "maxInfluences".

6. If a solvable vertex has no usable unlocked or locked weights after cleanup, assign weight "1.0" to "skeleton.rootBone".

7. When pruning unlocked influences, preserve the original weight distribution as closely as possible using deterministic tie-breaking.

8. If two vertices are mirrored by "mesh.mirrorVertex", their solved weights should mirror through "skeleton.mirrorBone" whenever this does not violate locked weights.

9. Mirroring must not overwrite locked weights.

10. If mirror symmetry conflicts with locked weights, preserve the locked weights and report a symmetry diagnostic.

11. A vertex mapped to itself is a centerline vertex. For centerline vertices, mirrored left/right bone pairs should have equal weights unless locked weights make that impossible.

12. Missing mirror bones, missing mirrored vertices, invalid input weights, impossible vertices, and symmetry conflicts must be reported in diagnostics.

13. The solver must produce deterministic results regardless of map iteration order.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of the solving strategy.
3. Important tests or pseudocode tests.






Here are the important hard test cases for Symmetric Skin Weight Solver.
Locked weights make normalization impossible
A vertex has locked known bones whose weights sum above 1.0. Verify the solver reports an impossible-constraint diagnostic and does not silently normalize locked weights.
Too many locked influences
A vertex has more locked known bones than maxInfluences. Verify the solver reports an impossible-constraint diagnostic.
Locked unknown bone
A vertex has a locked bone that is not in the skeleton. Verify the solver reports an impossible-constraint diagnostic instead of dropping it.
Unlocked unknown bones are removed and remaining weights renormalize
A vertex has a mix of known and unknown unlocked bones. Verify unknown bones are removed and the known remaining weights are normalized correctly.
Pruning respects locked low weights
A locked weight is below minWeight, while unlocked low weights are also present. Verify the locked low weight is preserved, but unlocked low weights may be pruned.
Max-influence pruning preserves locked weights
A vertex has more influences than maxInfluences, including locked bones. Verify locked bones remain exactly unchanged and pruning only removes unlocked influences.
Pruning tie-break is deterministic
A vertex has several unlocked influences with equal weights and only some can remain. Run the solver multiple times with different map iteration orders. Verify the same bones are kept every time.
Empty usable weights fall back to root bone
A solvable vertex has only invalid, unknown, negative, or pruned unlocked weights. Verify the solver assigns rootBone = 1.0.
Mirrored vertices produce mirrored weights
Vertex L mirrors vertex R. L uses left-side bones and R uses right-side bones. Verify solved weights match through mirrorBone.
Mirroring handles different original influence sets
Mirrored vertices start with different bone sets and different small/invalid weights. Verify the final pair is symmetric when no locks prevent it.
Mirroring does not overwrite locked weights
One vertex in a mirror pair has locked weights that conflict with the mirrored partner. Verify locked weights are preserved exactly and a symmetry diagnostic is reported.
Both sides have conflicting locked weights
Mirrored vertices each lock different incompatible values on corresponding bones. Verify both locked sets are preserved and a symmetry conflict diagnostic is reported.
Centerline vertex equalizes left/right pairs
A vertex maps to itself and has asymmetric left/right bone weights. Verify the solved centerline weights equalize mirrored bone pairs when no locks prevent it.
Centerline locked asymmetry is preserved with diagnostic
A centerline vertex has locked asymmetric left/right weights. Verify the locked values are preserved and a symmetry diagnostic is reported.
Missing mirror bone diagnostic
A vertex uses a bone whose mirror counterpart is missing from skeleton.mirrorBone. Verify the solver reports the missing mirror bone and does not produce nondeterministic mirrored output.
Mirror map references missing vertex
mesh.mirrorVertex points to a vertex id that does not exist. Verify the solver reports a diagnostic and still solves the existing vertex locally.
Mirror cycle consistency
The mirror map has A -> B and B -> A. Verify solving the pair is deterministic and does not apply mirroring twice in a way that changes weights again.
Asymmetric pair with locked plus pruning pressure
A mirrored pair has locked weights on one side, more than maxInfluences on both sides, and several low weights. Verify the final solution respects locks, max influences, normalization, and reports symmetry conflicts only where unavoidable.
Negative unlocked weights are removed or diagnosed without corrupting normalization
A vertex has negative unlocked weights mixed with valid known weights. Verify negative values do not survive into the output and the final solvable vertex still normalizes correctly.
Full deterministic stress case
Use many vertices with mirrored pairs, centerline vertices, unknown bones, equal-weight ties, locks, low weights, and max-influence pressure. Shuffle all input maps/lists and run repeatedly. Verify identical output weights and diagnostics each time.
