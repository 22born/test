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
