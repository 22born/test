Task name
Symmetric Skin Weight Solver
Category
3D character rigging / geometry processing / constraint solving / performance
Core idea
A character mesh has vertices influenced by bones. Each vertex has skin weights like:
LeftUpperArm: 0.55
LeftForearm: 0.35
Spine: 0.10
The weights must satisfy constraints:
sum of weights per vertex = 1.0
no negative weights
max 4 influences per vertex
locked weights must not change
left/right bones should mirror across the character centerline
deformation should remain visually close to the original
The hard part is that fixing one rule can break another.
For example:
A vertex has 6 influences.
You must reduce it to 4.
But 2 influences are locked.
The mirrored vertex has a different bone set.
The total must still normalize to 1.0.
The result should preserve deformation as much as possible.
A naïve solution will normalize weights and call it done, but that can destroy the deformation or violate locked weights.
Problem idea
Implement a solver that repairs and mirrors skin weights on a mesh.
class SkinWeightSolver {
    fun solve(
        mesh: Mesh,
        skeleton: Skeleton,
        options: SolveOptions
    ): SkinWeightResult
}
Where each vertex has:
data class SkinWeights(
    val vertexId: Int,
    val weights: Map<BoneId, Float>,
    val lockedBones: Set<BoneId> = emptySet()
)
The solver must:
1. Normalize each vertex’s weights to sum to 1.
2. Preserve locked bone weights exactly.
3. Remove tiny/invalid influences.
4. Limit each vertex to max N influences.
5. Mirror weights across left/right bone pairs.
6. Handle centerline vertices specially.
7. Avoid sudden weight discontinuities between neighboring vertices.
8. Minimize deformation error compared to the original weights.
Why this is hard
The key conflict is:
locked weights + max influence count + normalization + mirroring
Example:
maxInfluences = 4

Vertex v:
Locked:
  Spine = 0.40
  LeftArm = 0.30

Unlocked:
  LeftForearm = 0.12
  LeftHand = 0.08
  Neck = 0.05
  Clavicle = 0.05
Only two more influences can remain because two are locked.
A simple “keep top 4 weights” works here.
But now suppose:
Locked:
  Spine = 0.40
  LeftArm = 0.30
  LeftForearm = 0.20
  LeftHand = 0.15
  Neck = 0.10
The locked weights already exceed:
maxInfluences = 4
sum = 1.15
The solver must detect that the constraints are impossible and return a structured error, not silently produce bad weights.
The genuinely difficult version
Add a deformation preservation requirement.
Given the original bind-pose vertex position and several test poses, the solver should minimize the positional error between:
original skinning result
and:
repaired skinning result
So it is no longer just “normalize numbers.” It becomes constrained optimization:
Find valid weights that:
- sum to 1
- respect locked weights
- use at most 4 influences
- mirror correctly
- minimize deformation error over sample poses
This forces real reasoning.
Good final task shape
Symmetric Skin Weight Solver
The solver receives a mesh, skeleton, bone mirror map, vertex mirror map, locked influences, max influence count, and sample poses.
It must output repaired weights and diagnostics.
Hard cases include:
- mirrored vertices with unmatched bone sets
- centerline vertices affected by left/right bones
- locked weights that make constraints impossible
- pruning influences without causing visible deformation jumps
- preserving deformation across multiple poses
- smoothing weights without changing locked influences
- floating-point tolerance issues
This is a very good complex task because it mixes geometry, rigging rules, constraint solving, symmetry, and numerical stability.
