
You are working in a repository located at `environment/`.

The repository contains an Android offline-first notes synchronization system.

Users report that notes edited offline sometimes disappear, revert unexpectedly, or become inconsistent after reconnecting to the network.

Your task is to inspect the repository, infer the intended synchronization behavior from the available artifacts, identify the root causes of the issues, and implement a fix.

Requirements may be distributed across source code, documentation, configuration files, and sample scenarios.

Constraints:

* Do not modify documentation or sample scenario files.
* Do not bypass synchronization logic.
* Preserve existing functionality where possible.
* Your implementation should generalize beyond the visible examples.

Your solution will be evaluated using hidden tests that are not present in the repository.

The hidden evaluation covers:

* offline edits
* conflict resolution
* mutation ordering
* retries
* deletes
* partial failures
* synchronization correctness

Expected output:

Modified files: <relative file paths>


  The problem is about an offline-first Android notes app with a broken synchronization system.

Users can edit notes while offline. These edits are stored locally and later synchronized with a remote server when connectivity returns. However, the current implementation contains several subtle bugs that can cause:

Offline edits to be lost.
Remote data to overwrite unsynced local changes.
Duplicate updates during retries.
Deletes to be undone by stale server data.
Pending mutations to disappear after network failures.
Incorrect conflict resolution due to timestamp-based logic.

The model's task is to inspect the repository, understand how synchronization is intended to work, identify the bugs in the sync pipeline, conflict resolution, and mutation handling, and implement fixes that make the synchronization system reliable.

In essence, this is a distributed state consistency problem disguised as an Android sync engine. The challenge is reasoning about the lifecycle of local changes, server state, retries, ordering, and conflict resolution rather than simply implementing an Android API.









  
