# Basketball triangular defence

A basketball defensive strategy is represented as a directed graph.

Each node represents a defensive configuration:

(ballZone, topDefender, leftDefender, rightDefender)

Each directed edge represents a possible defensive rotation and stores:

(nextNode, defender, pressure)

where:
- nextNode is the resulting defensive configuration
- defender is the defender involved in the rotation
- pressure is the pressure added by that rotation

The system must determine the minimum number of rotations required to reach any defensive configuration that forms a successful trap.

A transition is usable only if:
1. The rotation exists as an edge in the graph.
2. The rotation sequence satisfies the defensive rotation constraints.
3. The accumulated pressure since the last reset does not exceed maxPressure.
4. Pressure resets to 0 whenever the ball moves through a middle-zone configuration.

The following Kotlin implementation is currently used in /app/Main.kt.

# Task

Identify the issue in /app/Main.kt and implement the correct solution.

Your answer should only include:
1. The issue in the given implementation.
2. The corrected Kotlin implementation.

import java.util.ArrayDeque

data class Edge(
    val next: String,
    val defender: String,
    val pressure: Int
)

data class QueueItem(
    val node: String,
    val distance: Int,
    val lastDefender: String?,
    val pressureSinceReset: Int
)

fun minRotationsToTrap(
    graph: Map<String, List<Edge>>,
    middleNodes: Set<String>,
    start: String,
    trapNodes: Set<String>,
    maxPressure: Int
): Int {
    val queue = ArrayDeque<QueueItem>()

    val visited = mutableSetOf<String>()

    queue.add(QueueItem(start, 0, null, 0))
    visited.add(start)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()

        if (current.node in trapNodes) {
            return current.distance
        }

        for (edge in graph[current.node].orEmpty()) {
            if (edge.defender == current.lastDefender) {
                continue
            }

            var nextPressure = current.pressureSinceReset + edge.pressure

            if (edge.next in middleNodes) {
                nextPressure = 0
            }

            if (nextPressure > maxPressure) {
                continue
            }

            if (edge.next !in visited) {
                visited.add(edge.next)

                queue.add(
                    QueueItem(
                        node = edge.next,
                        distance = current.distance + 1,
                        lastDefender = edge.defender,
                        pressureSinceReset = nextPressure
                    )
                )
            }
        }
    }

    return -1
}




Issue

The implementation incorrectly uses:

val visited = mutableSetOf<String>()

This tracks only the graph node.

But future valid moves depend on more than the node:

current node
last defender who rotated
pressure accumulated since reset

So the same node may need to be explored multiple times if it is reached with a different lastDefender or a different pressureSinceReset.

Because the original code marks only the node as visited, it can incorrectly discard valid paths and return -1 even when a trap is reachable.



Correct implementation:
import java.util.ArrayDeque

data class Edge(
    val next: String,
    val defender: String,
    val pressure: Int
)

// FIX: The real BFS state includes all values that affect future transitions.
data class State(
    val node: String,
    val lastDefender: String?,
    val pressureSinceReset: Int
)

data class QueueItem(
    val node: String,
    val distance: Int,
    val lastDefender: String?,
    val pressureSinceReset: Int
)

fun minRotationsToTrap(
    graph: Map<String, List<Edge>>,
    middleNodes: Set<String>,
    start: String,
    trapNodes: Set<String>,
    maxPressure: Int
): Int {
    val queue = ArrayDeque<QueueItem>()

    // FIX: Track the complete state, not just the node.
    val visited = mutableSetOf<State>()

    queue.add(QueueItem(start, 0, null, 0))
    visited.add(State(start, null, 0))

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()

        if (current.node in trapNodes) {
            return current.distance
        }

        for (edge in graph[current.node].orEmpty()) {

            // Defensive constraint: same defender cannot rotate twice consecutively.
            if (edge.defender == current.lastDefender) {
                continue
            }

            var nextPressure = current.pressureSinceReset + edge.pressure

            // Pressure resets when the ball reaches a middle-zone configuration.
            if (edge.next in middleNodes) {
                nextPressure = 0
            }

            if (nextPressure > maxPressure) {
                continue
            }

            // FIX: The next visited state must include updated defender and pressure.
            val nextState = State(
                node = edge.next,
                lastDefender = edge.defender,
                pressureSinceReset = nextPressure
            )

            if (nextState !in visited) {
                visited.add(nextState)

                queue.add(
                    QueueItem(
                        node = edge.next,
                        distance = current.distance + 1,
                        lastDefender = edge.defender,
                        pressureSinceReset = nextPressure
                    )
                )
            }
        }
    }

    return -1
}

What was fixed

The fix changes visited tracking from:

mutableSetOf<String>()

to:

mutableSetOf<State>()

where State contains:

node
lastDefender
pressureSinceReset

This makes BFS explore the correct state space.
The key reasoning step is to infer that the graph node alone is not the full BFS state. Since lastDefender and pressureSinceReset affect which moves are legal next, they must also be part of the visited state.
