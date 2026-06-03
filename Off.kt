23. test_upsert_existing_same_path_preserves_children
Upsert an existing node at the same path where it already exists and has children. Verify only the node’s value changes and its children remain intact.
This catches solutions that replace the node with a new leaf.
24. test_upsert_existing_root_value
Upsert path ["root"] with a new value. Verify root value changes and children remain unchanged.
This catches implementations that assume upsert always inserts into a parent.
25. test_upsert_path_conflicts_with_existing_id
Have an existing node id appear as a missing intermediate path segment elsewhere. Example: node a exists under root/x/a, then upsert path root/a/b. Correct behavior should avoid duplicating a.
This is harder because identity uniqueness applies to intermediate path nodes too, not only the final node.
26. test_move_to_missing_descendant_path_rejected
Move root/a to root/a/b/newParent, where newParent does not exist yet. This must still be rejected because the destination path is inside the moving subtree.
This catches the bug I mentioned earlier.
27. test_move_destination_created_without_duplicate_ids
Move a node to a destination path whose missing intermediate id already exists elsewhere in the tree. Verify no duplicate id is created.
This is a very hard identity/path interaction case.
28. test_delete_then_upsert_same_id
Delete a subtree containing item, then later upsert item elsewhere. Expected: a new item is created, not restored with old children/value.
29. test_move_preserves_subtree
Move a node with children and grandchildren. Verify the entire subtree is preserved exactly.
30. test_invalid_paths_do_nothing
Use paths that do not start with the actual root id, such as ["wrongRoot", "a"]. Verify upsert, move, and delete are ignored.
31. test_multiple_moves_same_node
Move the same node several times across different parents. Verify only one copy exists and final location matches the last valid move.
32. test_upsert_after_move_uses_current_location
Move a node, then upsert the same id to another path. Verify the upsert relocates it from its current moved location, not the original location.
