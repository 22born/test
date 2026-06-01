
15. Same node, same defender, different pressure

Validates that pressure must be part of state, not just (node, lastDefender).

A->B(D1,p=3)
A->X(D2,p=1)
X->B(D1,p=1)
B->T(D2,p=2)
max=3
Expected: 3

Why:
B reached directly has pressure 3, so B->T becomes 5 > 3.
But B reached through X has pressure 2, so B->T becomes 4 > 3 still fail — adjust:

A->B(D1,p=3)
A->X(D2,p=0)
X->B(D1,p=0)
B->T(D2,p=2)
max=3
Expected: 3

Buggy code may mark B visited too early.

16. Trap node reached with invalid pressure should not count

Validates that the code checks pressure before accepting a trap transition.

A->T(D1,p=5)
max=3
Expected: -1

The trap exists as a node, but the transition into it is invalid.

17. Middle reset plus defender constraint interaction

A->M(D1,p=3)
M->B(D1,p=1)
M->B(D2,p=1)
B->T(D1,p=1)
middle={M}
max=2
Expected: 3

Why:
Pressure resets at M, but M->B(D1) is blocked because D1 just rotated. Must use M->B(D2), then B->T(D1).



# Basketball triangular defence

A basketball defensive strategy is represented as a directed graph.

Each node represents a defensive configuration:

(ballZone, topDefender, leftDefender, rightDefender)

Each directed edge represents a possible defensive rotation and stores:

(nextNode, defender, pressure)

where:
- nextNode is the resulting defensive configuration
- defender is the defender involved in the rotation
- pressure is the defensive pressure added by that rotation

The system must determine the minimum number of rotations required to reach any defensive configuration that forms a successful trap.

A transition is usable only if:

1. The rotation exists as an edge in the graph.
2. A defender cannot perform two active close-outs without another defender rotating in between.
3. The accumulated pressure since the last reset does not exceed maxPressure.
4. Pressure resets to 0 whenever the ball moves through a middle-zone configuration.
5. Some transition constraints depend on short-term defensive history, not only on the current graph node.

Recent game simulations have produced inconsistent results. Some scenarios that previously resulted in a successful trap are now reported as unreachable.

The following Kotlin implementation is currently used in /app/Main.kt.

# Task

Identify the issue in /app/Main.kt and implement the correct solution.

Your answer should only include:

1. The issue in the implementation.
2. The corrected Kotlin implementation.
