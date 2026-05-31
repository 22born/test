Correct fix
import java.util.ArrayDeque

data class Edge(val next: String, val defender: String)

// FIX: visited state must include both the current node and the last defender.
// The same node can have different legal next moves depending on lastDefender.
data class State(
    val node: String,
    val lastDefender: String?
)

data class QueueItem(
    val node: String,
    val distance: Int,
    val lastDefender: String?
)

fun minRotationsToTrap(
    graph: Map<String, List<Edge>>,
    start: String,
    trapNodes: Set<String>
): Int {
    val queue = ArrayDeque<QueueItem>()

    // FIX: Do not store only node names in visited.
    // Store State(node, lastDefender), because lastDefender affects future moves.
    val visited = mutableSetOf<State>()

    queue.add(QueueItem(start, 0, null))
    visited.add(State(start, null))

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()

        if (current.node in trapNodes) {
            return current.distance
        }

        for (edge in graph[current.node].orEmpty()) {

            // Rule: the same defender cannot rotate twice in a row.
            if (edge.defender == current.lastDefender) {
                continue
            }

            // FIX: next state includes the defender who just rotated.
            val nextState = State(edge.next, edge.defender)

            if (nextState !in visited) {
                visited.add(nextState)

                queue.add(
                    QueueItem(
                        node = edge.next,
                        distance = current.distance + 1,
                        lastDefender = edge.defender
                    )
                )
            }
        }
    }

    return -1
}
Explanation

The bug is that the original code used:

val visited = mutableSetOf<String>()

That only tracks the node.

But the legal next moves depend on who rotated last, so the same node reached after defender A is different from the same node reached after defender B.

The fix is:

val visited = mutableSetOf<State>()

where:

State(node, lastDefender)

This prevents the BFS from incorrectly discarding valid paths.
