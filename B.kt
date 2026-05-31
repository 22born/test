
import java.util.ArrayDeque

data class Edge(val next: String, val defender: String)
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
    val visited = mutableSetOf<String>()

    queue.add(QueueItem(start, 0, null))
    visited.add(start)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()

        if (current.node in trapNodes) {
            return current.distance
        }

        for (edge in graph[current.node].orEmpty()) {
            if (edge.next !in visited &&
                edge.defender != current.lastDefender
            ) {
                visited.add(edge.next)

                queue.add(
                    QueueItem(
                        edge.next,
                        current.distance + 1,
                        edge.defender
                    )
                )
            }
        }
    }

    return -1
}
