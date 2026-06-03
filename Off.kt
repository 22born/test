fun replayMutations(
    initial: SettingNode,
    mutations: List<Mutation>
): SettingNode {
    fun epoch(m: Mutation) = when (m) {
        is Mutation.Upsert -> m.epoch
        is Mutation.Move -> m.epoch
        is Mutation.Delete -> m.epoch
    }

    fun rank(m: Mutation) = when (m) {
        is Mutation.Upsert -> m.sourceRank
        is Mutation.Move -> m.sourceRank
        is Mutation.Delete -> m.sourceRank
    }

    fun findByPath(root: SettingNode, path: List<String>): SettingNode? {
        if (path.isEmpty() || path[0] != root.id) return null
        var cur = root
        for (id in path.drop(1)) {
            cur = cur.children.firstOrNull { it.id == id } ?: return null
        }
        return cur
    }

    fun findById(root: SettingNode, id: String): SettingNode? {
        if (root.id == id) return root
        for (child in root.children) {
            val found = findById(child, id)
            if (found != null) return found
        }
        return null
    }

    fun isPrefix(prefix: List<String>, path: List<String>): Boolean {
        return prefix.size <= path.size && prefix.indices.all { prefix[it] == path[it] }
    }

    fun removeById(root: SettingNode, id: String): SettingNode? {
        if (root.id == id) return null
        val children = root.children.mapNotNull { removeById(it, id) }
        return root.copy(children = children)
    }

    fun removeByPath(root: SettingNode, path: List<String>): SettingNode? {
        if (path.isEmpty() || path[0] != root.id) return root
        if (path.size == 1) return null

        val next = path[1]
        val children = root.children.mapNotNull { child ->
            if (child.id == next) removeByPath(child, path.drop(1)) else child
        }

        return root.copy(children = children)
    }

    fun ensurePath(root: SettingNode, path: List<String>): SettingNode {
        if (path.isEmpty() || path[0] != root.id) return root
        if (path.size == 1) return root

        val next = path[1]
        val index = root.children.indexOfFirst { it.id == next }

        val children =
            if (index >= 0) {
                root.children.mapIndexed { i, child ->
                    if (i == index) ensurePath(child, path.drop(1)) else child
                }
            } else {
                root.children + ensurePath(
                    SettingNode(next, null, emptyList()),
                    path.drop(1)
                )
            }

        return root.copy(children = children)
    }

    fun insertChild(
        root: SettingNode,
        parentPath: List<String>,
        child: SettingNode,
        index: Int
    ): SettingNode {
        if (parentPath.size == 1 && parentPath[0] == root.id) {
            val clamped = index.coerceIn(0, root.children.size)
            val children = root.children.toMutableList()
            children.add(clamped, child)
            return root.copy(children = children)
        }

        val children = root.children.map { existing ->
            if (parentPath.size > 1 && existing.id == parentPath[1]) {
                insertChild(existing, parentPath.drop(1), child, index)
            } else {
                existing
            }
        }

        return root.copy(children = children)
    }

    fun applyUpsert(root: SettingNode, m: Mutation.Upsert): SettingNode {
        val path = m.path
        if (path.isEmpty() || path[0] != root.id) return root

        val targetId = path.last()

        if (targetId == root.id) {
            return root.copy(value = m.value)
        }

        val existing = findById(root, targetId)
        var current = root

        if (existing != null) {
            current = removeById(current, targetId) ?: current
        }

        val parentPath = path.dropLast(1)
        current = ensurePath(current, parentPath)

        val node = existing?.copy(value = m.value)
            ?: SettingNode(targetId, m.value, emptyList())

        return insertChild(current, parentPath, node, Int.MAX_VALUE)
    }

    fun applyMove(root: SettingNode, m: Mutation.Move): SettingNode {
        val fromPath = m.fromPath
        val toParentPath = m.toParentPath

        if (fromPath.isEmpty() || fromPath[0] != root.id) return root
        if (toParentPath.isEmpty() || toParentPath[0] != root.id) return root
        if (fromPath.size <= 1) return root

        val moving = findByPath(root, fromPath) ?: return root

        if (isPrefix(fromPath, toParentPath)) {
            return root
        }

        var current = removeByPath(root, fromPath) ?: return root
        current = ensurePath(current, toParentPath)

        val parent = findByPath(current, toParentPath) ?: return current
        val index = m.newIndex.coerceIn(0, parent.children.size)

        return insertChild(current, toParentPath, moving, index)
    }

    fun applyDelete(root: SettingNode, m: Mutation.Delete): SettingNode {
        val path = m.path
        if (path.isEmpty() || path[0] != root.id) return root
        if (path.size <= 1) return root
        if (findByPath(root, path) == null) return root

        return removeByPath(root, path) ?: root
    }

    var current = initial

    val ordered = mutations.withIndex().sortedWith(
        compareBy<IndexedValue<Mutation>>(
            { epoch(it.value) },
            { rank(it.value) },
            { it.index }
        )
    )

    for ((_, mutation) in ordered) {
        current = when (mutation) {
            is Mutation.Upsert -> applyUpsert(current, mutation)
            is Mutation.Move -> applyMove(current, mutation)
            is Mutation.Delete -> applyDelete(current, mutation)
        }
    }

    return current
}




The solution does four main things.
First, it sorts mutations before applying them. It uses epoch, then sourceRank, then the mutation’s original input position. This guarantees deterministic replay.
Second, it uses helper functions to work with the tree:
findByPath finds a node using its current tree path.
findById finds a node anywhere in the tree by its unique id.
ensurePath creates missing parent path nodes, assigning them value = null.
removeByPath removes a node at a specific path.
removeById removes an existing node with a given id from anywhere in the tree.
insertChild inserts a node under a given parent path at a clamped index.
Third, it applies each mutation type.
For Upsert, the solution checks whether the target id already exists anywhere. If it does, the node is removed from its old location, updated with the new value, and inserted at the requested path. If it does not exist, a new node is created. Missing parent path nodes are created before insertion. This prevents duplicate ids.
For Move, the solution finds the source node. If it does not exist, the mutation is ignored. It also rejects moving the root. Before doing anything, it checks whether the destination parent path starts with the source path. If so, the move would place the node inside its own subtree, so it is ignored. Otherwise, it removes the node from its old location, creates the destination parent path if needed, clamps the requested index, and inserts the node.
For Delete, the solution ignores missing paths and ignores deletion of the root. Otherwise, it removes the target node and its whole subtree.
Finally, the tree is treated immutably. The code does not edit the original SettingNode objects in place. Instead, every helper returns copied nodes with updated child lists where needed.
The solution is correct as a reference implementation, but not highly optimized. It repeatedly searches the tree, so the complexity is roughly O(M × N), where M is the number of mutations and N is the number of nodes.
