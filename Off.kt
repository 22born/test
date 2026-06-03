Yes. Here are the test cases in plain text form.






Mutation sorting test

Provide mutations in an intentionally shuffled order. The expected result should prove that the implementation sorted by epoch, then sourceRank, then original input index before applying them.






Upsert existing node test

Start with a tree that already contains a node such as sync with value "wifi". Apply an upsert to the same path with value "all". The expected result is that the existing node’s value changes to "all" and no duplicate node is created.






Upsert missing path test

Apply an upsert to a path where some intermediate nodes do not exist. The implementation should create all missing nodes, assign null to intermediate nodes, and assign the provided value only to the final node.






Upsert relocates existing node test

Start with a node already present in one part of the tree. Apply an upsert targeting the same node id under a different parent. The expected result is that the node is moved to the new location, its value is updated, and it no longer exists in the old location.






Basic move test

Move an existing node from one valid path to another valid parent path. The expected result is that the node appears under the new parent at the requested index and is removed from its original location.






Move missing source test

Apply a move where the source path does not exist. The expected result is that the tree remains unchanged.






Move creates destination parent test

Apply a move where the source node exists but the destination parent path does not. The implementation should create the missing destination path, then move the source node under it.






Move into own descendant test

Attempt to move a node into one of its own descendants. The expected result is that the move is ignored and the tree remains unchanged.






Move index clamping test

Apply moves with invalid indices, such as a negative index or an index larger than the child count. Negative indices should insert at the beginning. Too-large indices should insert at the end.






Move within same parent test

Move a node from one index to another index under the same parent. The expected result should be based on removing the node first, then inserting it at the clamped destination index. This catches incorrect index handling.






Delete leaf node test

Delete a node with no children. The expected result is that only that node is removed and the rest of the tree remains unchanged.






Delete subtree test

Delete a node that has descendants. The expected result is that the node and all of its descendants are removed.






Delete missing path test

Apply a delete to a path that does not exist. The expected result is that the tree remains unchanged.






Delete root ignored test

Apply a delete targeting the root node. The expected result is that the root is not deleted and the tree remains unchanged.






Move before delete conflict test

Start with a node inside a subtree that will later be deleted. First move that node out of the subtree, then delete the original subtree. The expected result is that the moved node survives because it was relocated before the delete happened.






Delete before move conflict test

Use the same setup as the previous test, but apply the delete before the move. The expected result is that the move is ignored because the source node no longer exists.






Conflicting upserts to same node test

Apply multiple upserts targeting the same node id in different locations. The expected result is that only one copy of the node exists, located according to the latest mutation in replay order, with the latest value.






Sibling order preservation test

Start with several siblings. Apply a mutation that affects only one of them. The expected result is that unaffected siblings keep their original relative order.






Immutability test

Keep the original input tree and compare it after calling the function. The expected result is that the original tree has not changed, even though the returned tree reflects the mutations.






Deep tree stress test

Use a very deeply nested path, such as hundreds or thousands of nodes. Apply an upsert, move, or delete deep in the tree. The expected result is correct behavior without crashing or corrupting the path.










data class SettingNode(
    val id: String,
    val value: String?,
    val children: List<SettingNode>
)

sealed class Mutation {
    data class Upsert(
        val path: List<String>,
        val value: String?,
        val epoch: Long,
        val sourceRank: Int
    ) : Mutation()

    data class Move(
        val fromPath: List<String>,
        val toParentPath: List<String>,
        val newIndex: Int,
        val epoch: Long,
        val sourceRank: Int
    ) : Mutation()

    data class Delete(
        val path: List<String>,
        val epoch: Long,
        val sourceRank: Int
    ) : Mutation()
}

Context
An Android application stores its settings hierarchy as a tree. While offline, multiple subsystems may independently generate mutations against the tree. When synchronization begins, all mutations must be replayed to reconstruct the final state.
Each node has a globally unique identifier. Mutations reference nodes using paths within the current tree structure.
The required data structures and function signature are provided in the starter code.
Task
Implement the mutation replay engine.
Given an initial tree and a list of mutations, return the final tree after replaying all mutations.
Before replaying, mutations must be sorted by:
epoch ascending
sourceRank ascending
original position in the input list ascending
Mutations are then applied sequentially in that order.
Rules
Upsert
Creates any missing nodes along the target path.
Newly created intermediate nodes must have value = null.
The final node in the path must receive the provided value.
Node identifiers are globally unique across the tree.
If a node with the target identifier already exists elsewhere, it must not be duplicated.
Move
Relocates the node at the source path.
If the source path does not exist, ignore the mutation.
If the destination parent path does not exist, create it.
A node may not be moved into its own descendant subtree.
Clamp insertion indices to the valid range.
Delete
Removes the node at the target path and all of its descendants.
If the target path does not exist, ignore the mutation.
Deleting the root node has no effect.
Ordering
Preserve existing sibling order whenever possible.
Newly created nodes are appended to the end of their parent's children.
If an existing node is relocated due to an upsert, append it to the end of the destination parent's children.
Immutability
Do not modify the input tree.
Return the final reconstructed tree after all mutations have been applied.





















The problem asks the model to simulate how an Android app would rebuild a nested UI/settings tree after receiving offline edits from different sources. Each edit is a mutation: create/update, move, or delete. The hard part is that mutations use paths, but nodes have unique identities, so an upsert can secretly mean “move this existing node here and update it,” not “create a duplicate.”
Expected output is the final immutable SettingNode tree after sorting and applying all mutations deterministically.
Prompt
Context
You are building an Android/Kotlin app with an offline settings editor. The settings screen is represented as a tree. While offline, the app records mutations from different sources such as Compose state updates, ViewModel retries, and restored process-death snapshots.
Because these sources can emit overlapping changes, the app must replay all mutations in a deterministic order to reconstruct the final settings tree.
Each node has a globally unique id. Paths describe where a node is currently located in the tree.
Starter code
Kotlin
data class SettingNode(
    val id: String,
    val value: String?,
    val children: List<SettingNode>
)

sealed class Mutation {
    data class Upsert(
        val path: List<String>,
        val value: String?,
        val epoch: Long,
        val sourceRank: Int
    ) : Mutation()

    data class Move(
        val fromPath: List<String>,
        val toParentPath: List<String>,
        val newIndex: Int,
        val epoch: Long,
        val sourceRank: Int
    ) : Mutation()

    data class Delete(
        val path: List<String>,
        val epoch: Long,
        val sourceRank: Int
    ) : Mutation()
}
Task
Implement:
Kotlin
fun replayMutations(
    initial: SettingNode,
    mutations: List<Mutation>
): SettingNode
The function must return the final tree after applying all mutations.
Mutation ordering
Before applying mutations, sort them by:
Kotlin
epoch ASC,
sourceRank ASC,
original input index ASC
The original input index is the mutation’s position in the given mutations list.
Rules
Upsert(path, value, ...)
Creates all missing nodes along the path. Intermediate nodes get value = null. The final node gets the given value.
If a node with the same id already exists elsewhere in the tree, it must be moved to the requested path instead of duplicated.
Move(fromPath, toParentPath, newIndex, ...)
Moves the node at fromPath into toParentPath at newIndex.
If fromPath does not exist, ignore the mutation.
If toParentPath does not exist, create it.
If the move would place a node inside one of its own descendants, ignore the mutation.
Clamp newIndex to the valid insertion range.
Delete(path, ...)
Deletes the node at path and all its descendants.
Deleting the root is ignored.
If the path does not exist, ignore the mutation.
Sibling ordering
Existing sibling order must be preserved unless affected by an upsert or move.
If an existing node is upserted into a new parent, append it to that parent’s children.
If missing path nodes are created, append each new node at its level.
Immutability
Do not mutate the original initial tree.
Return a new tree with the correct final structure.
Example input
Kotlin
val initial = SettingNode(
    id = "root",
    value = null,
    children = listOf(
        SettingNode(
            id = "display",
            value = null,
            children = listOf(
                SettingNode("theme", "light", emptyList())
            )
        ),
        SettingNode("sync", "wifi", emptyList())
    )
)

val mutations = listOf(
    Mutation.Upsert(listOf("root", "privacy", "ads"), "off", 2, 0),
    Mutation.Move(listOf("root", "display", "theme"), listOf("root", "privacy"), 0, 1, 0),
    Mutation.Upsert(listOf("root", "sync"), "all", 1, 1),
    Mutation.Delete(listOf("root", "display"), 3, 0)
)
Expected output
Kotlin
SettingNode(
    id = "root",
    value = null,
    children = listOf(
        SettingNode("sync", "all", emptyList()),
        SettingNode(
            id = "privacy",
            value = null,
            children = listOf(
                SettingNode("theme", "light", emptyList()),
                SettingNode("ads", "off", emptyList())
            )
        )
    )
)
