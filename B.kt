
## Android Concurrency Problem: Fix the Buggy Compose Map Screen

You are given a Jetpack Compose screen that displays incident markers on a real-time emergency map.

Markers can be updated from:

1. Local UI actions
2. WebSocket events
3. Offline retry sync
4. GPS/location updates

The code below has concurrency bugs. It sometimes shows stale markers, resurrects deleted markers, duplicates updates, and occasionally crashes during recomposition.

Identify the concurrency problems and rewrite the code so that the Compose UI is updated safely and deterministically.

```kotlin
// IncidentMapScreen.kt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Marker(
    val id: String,
    val title: String,
    val lat: Double,
    val lng: Double,
    val deleted: Boolean = false
)

class IncidentRepository {
    val websocketEvents: Flow<Marker> = TODO()
    val retryEvents: Flow<Marker> = TODO()
    val gpsEvents: Flow<Marker> = TODO()

    suspend fun uploadMarker(marker: Marker) {
        // Upload marker to server
    }
}

class IncidentViewModel(
    private val repository: IncidentRepository
) : ViewModel() {

    // BUG: mutable Compose state is exposed and mutated from multiple coroutines.
    val markers = mutableStateListOf<Marker>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.websocketEvents.collect { remoteMarker ->
                // BUG: direct mutation from background thread.
                markers.removeAll { it.id == remoteMarker.id }
                markers.add(remoteMarker)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.retryEvents.collect { pendingMarker ->
                // BUG: retry can resurrect a marker that was deleted remotely.
                repository.uploadMarker(pendingMarker)

                markers.removeAll { it.id == pendingMarker.id }
                markers.add(pendingMarker)
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            repository.gpsEvents.collect { locationMarker ->
                // BUG: high-frequency GPS updates mutate UI state directly.
                markers.removeAll { it.id == locationMarker.id }
                markers.add(locationMarker)
            }
        }
    }

    fun onMarkerCreated(marker: Marker) {
        viewModelScope.launch {
            // BUG: optimistic local update races with WebSocket and retry updates.
            markers.add(marker)

            withContext(Dispatchers.IO) {
                repository.uploadMarker(marker)
            }
        }
    }

    fun onMarkerDeleted(markerId: String) {
        viewModelScope.launch {
            // BUG: local delete is not durable and can be overwritten by old events.
            markers.removeAll { it.id == markerId }
        }
    }
}

@Composable
fun IncidentMapScreen(
    viewModel: IncidentViewModel
) {
    val markers = viewModel.markers

    // BUG: UI reads mutable shared state directly.
    MapView(
        markers = markers,
        onMarkerCreated = { marker ->
            viewModel.onMarkerCreated(marker)
        },
        onMarkerDeleted = { markerId ->
            viewModel.onMarkerDeleted(markerId)
        }
    )
}

@Composable
fun MapView(
    markers: List<Marker>,
    onMarkerCreated: (Marker) -> Unit,
    onMarkerDeleted: (String) -> Unit
) {
    // Renders markers on a map
}
```

### Requirements

Fix the code so that:

1. Compose observes immutable UI state.
2. Multiple flows cannot mutate marker state concurrently.
3. Deleted markers are not resurrected by delayed retry or WebSocket events.
4. GPS updates do not overwhelm the UI.
5. Optimistic updates are safe.
6. Updates are lifecycle-safe.
7. The final marker state is deterministic.

Explain the main bugs, then provide a corrected implementation.


------
Solution:
The correct solution is below:
// IncidentMapScreen.kt

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Marker(
    val id: String,
    val title: String,
    val lat: Double,
    val lng: Double,
    val version: Long,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false
)

data class IncidentUiState(
    val markers: List<Marker> = emptyList()
)

class IncidentRepository {
    val websocketEvents: Flow<Marker> = TODO()
    val retryEvents: Flow<Marker> = TODO()
    val gpsEvents: Flow<Marker> = TODO()

    suspend fun uploadMarker(marker: Marker) {
        // Upload marker to server
    }
}

private data class UploadKey(
    val markerId: String,
    val version: Long,
    val deleted: Boolean
)

private sealed interface MarkerAction {
    data class LocalCreate(val marker: Marker) : MarkerAction
    data class LocalDelete(val markerId: String) : MarkerAction
    data class RemoteUpsert(val marker: Marker) : MarkerAction
    data class RetryUpload(val marker: Marker) : MarkerAction
    data class GpsUpdate(val marker: Marker) : MarkerAction
    data class UploadSucceeded(val key: UploadKey) : MarkerAction
    data class UploadFailed(val key: UploadKey) : MarkerAction
}

class IncidentViewModel(
    private val repository: IncidentRepository
) : ViewModel() {

    private val actions = Channel<MarkerAction>(capacity = Channel.BUFFERED)

    /**
     * Internal store keeps deleted markers as tombstones.
     * Deleted markers are hidden from the UI but retained internally so
     * stale WebSocket/retry events cannot resurrect them.
     */
    private val markerStore = mutableMapOf<String, Marker>()

    /**
     * Prevents duplicate concurrent uploads for the same marker/version/delete state.
     */
    private val inFlightUploads = mutableSetOf<UploadKey>()

    private val _uiState = MutableStateFlow(IncidentUiState())
    val uiState: StateFlow<IncidentUiState> = _uiState.asStateFlow()

    init {
        startSingleWriterReducer()
        collectWebSocketEvents()
        collectRetryEvents()
        collectGpsEvents()
    }

    private fun startSingleWriterReducer() {
        viewModelScope.launch {
            actions.receiveAsFlow().collect { action ->
                reduce(action)
                publishUiState()
            }
        }
    }

    private fun collectWebSocketEvents() {
        viewModelScope.launch {
            repository.websocketEvents.collect { remoteMarker ->
                actions.send(
                    MarkerAction.RemoteUpsert(
                        remoteMarker.copy(pendingSync = false)
                    )
                )
            }
        }
    }

    private fun collectRetryEvents() {
        viewModelScope.launch {
            repository.retryEvents.collect { pendingMarker ->
                /**
                 * Do not upload immediately here.
                 * First send the retry request through the reducer.
                 * The reducer decides whether this marker is still current.
                 */
                actions.send(MarkerAction.RetryUpload(pendingMarker))
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun collectGpsEvents() {
        viewModelScope.launch {
            repository.gpsEvents
                .conflate()
                .sample(250L)
                .collect { locationMarker ->
                    actions.send(MarkerAction.GpsUpdate(locationMarker))
                }
        }
    }

    fun onMarkerCreated(marker: Marker) {
        viewModelScope.launch {
            actions.send(MarkerAction.LocalCreate(marker))
        }
    }

    fun onMarkerDeleted(markerId: String) {
        viewModelScope.launch {
            actions.send(MarkerAction.LocalDelete(markerId))
        }
    }

    private fun reduce(action: MarkerAction) {
        when (action) {
            is MarkerAction.LocalCreate -> {
                val optimisticMarker = action.marker.copy(
                    version = nextVersion(action.marker.id),
                    deleted = false,
                    pendingSync = true
                )

                applyMarker(optimisticMarker)
                maybeStartUpload(optimisticMarker)
            }

            is MarkerAction.LocalDelete -> {
                val existing = markerStore[action.markerId]
                val deleteVersion = nextVersion(action.markerId)

                val tombstone = Marker(
                    id = action.markerId,
                    title = existing?.title.orEmpty(),
                    lat = existing?.lat ?: 0.0,
                    lng = existing?.lng ?: 0.0,
                    version = deleteVersion,
                    deleted = true,
                    pendingSync = true
                )

                applyMarker(tombstone)
                maybeStartUpload(tombstone)
            }

            is MarkerAction.RemoteUpsert -> {
                applyMarker(
                    action.marker.copy(pendingSync = false)
                )
            }

            is MarkerAction.RetryUpload -> {
                val current = markerStore[action.marker.id]

                /**
                 * Retry only if this exact marker version is still the current
                 * pending local state.
                 *
                 * This prevents an old retry from uploading a marker that has
                 * already been deleted or replaced by a newer version.
                 */
                if (
                    current != null &&
                    current.id == action.marker.id &&
                    current.version == action.marker.version &&
                    current.deleted == action.marker.deleted &&
                    current.pendingSync
                ) {
                    maybeStartUpload(current)
                }
            }

            is MarkerAction.GpsUpdate -> {
                val current = markerStore[action.marker.id]

                /**
                 * Do not let GPS resurrect a marker that has been deleted.
                 */
                if (current?.deleted == true) {
                    return
                }

                applyMarker(action.marker.copy(pendingSync = false))
            }

            is MarkerAction.UploadSucceeded -> {
                inFlightUploads.remove(action.key)

                val current = markerStore[action.key.markerId]

                /**
                 * A stale upload success must not overwrite newer local state.
                 */
                if (
                    current != null &&
                    current.version == action.key.version &&
                    current.deleted == action.key.deleted
                ) {
                    markerStore[action.key.markerId] = current.copy(
                        pendingSync = false
                    )
                }
            }

            is MarkerAction.UploadFailed -> {
                inFlightUploads.remove(action.key)

                val current = markerStore[action.key.markerId]

                /**
                 * If this is still the current version, keep it pending.
                 * If a newer version exists, ignore the stale failure.
                 */
                if (
                    current != null &&
                    current.version == action.key.version &&
                    current.deleted == action.key.deleted
                ) {
                    markerStore[action.key.markerId] = current.copy(
                        pendingSync = true
                    )
                }
            }
        }
    }

    private fun maybeStartUpload(marker: Marker) {
        val key = UploadKey(
            markerId = marker.id,
            version = marker.version,
            deleted = marker.deleted
        )

        if (!inFlightUploads.add(key)) {
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.uploadMarker(marker)
                }

                actions.send(MarkerAction.UploadSucceeded(key))
            } catch (_: Throwable) {
                actions.send(MarkerAction.UploadFailed(key))
            }
        }
    }

    private fun applyMarker(incoming: Marker) {
        val current = markerStore[incoming.id]

        if (current == null) {
            markerStore[incoming.id] = incoming
            return
        }

        if (shouldReplaceCurrentMarker(current, incoming)) {
            markerStore[incoming.id] = incoming
        }
    }

    private fun shouldReplaceCurrentMarker(
        current: Marker,
        incoming: Marker
    ): Boolean {
        /**
         * Newer versions always win.
         */
        if (incoming.version > current.version) return true

        /**
         * Older versions never overwrite newer versions.
         */
        if (incoming.version < current.version) return false

        /**
         * Same version: delete wins.
         *
         * This prevents stale upserts from resurrecting deleted markers.
         */
        if (incoming.deleted && !current.deleted) return true
        if (!incoming.deleted && current.deleted) return false

        /**
         * Same version and same delete state:
         * confirmed state wins over pending optimistic state.
         */
        if (!incoming.pendingSync && current.pendingSync) return true

        return false
    }

    private fun publishUiState() {
        val visibleMarkers = markerStore.values
            .filterNot { it.deleted }
            .sortedWith(
                compareBy<Marker> { it.id }
                    .thenBy { it.version }
            )

        _uiState.update {
            IncidentUiState(markers = visibleMarkers)
        }
    }

    private fun nextVersion(markerId: String): Long {
        return (markerStore[markerId]?.version ?: 0L) + 1L
    }
}

@Composable
fun IncidentMapScreen(
    viewModel: IncidentViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    MapView(
        markers = uiState.value.markers,
        onMarkerCreated = viewModel::onMarkerCreated,
        onMarkerDeleted = viewModel::onMarkerDeleted
    )
}

@Composable
fun MapView(
    markers: List<Marker>,
    onMarkerCreated: (Marker) -> Unit,
    onMarkerDeleted: (String) -> Unit
) {
    // Renders markers on a map
}

The buggy code allowed WebSocket, retry sync, GPS, and UI actions to all mutate this directly:

val markers = mutableStateListOf<Marker>()

That creates races:

WebSocket updates marker
Retry updates same marker
GPS updates same marker
User deletes marker
Old retry finishes and adds it back

So markers could become stale, duplicated, resurrected after delete, or mutated from the wrong thread.

What the fixed solution does

The fixed code introduces a single pipeline:

UI / WebSocket / Retry / GPS
        ↓
MarkerAction
        ↓
Channel
        ↓
single reducer coroutine
        ↓
markerStore
        ↓
IncidentUiState
        ↓
Compose

Compose observes only this:

val uiState: StateFlow<IncidentUiState>

and collects it safely with:

collectAsStateWithLifecycle()

So Compose receives immutable UI state instead of shared mutable state.

Why the reducer matters

All updates are processed one at a time inside reduce().

That means this cannot happen anymore:

Coroutine A removes marker
Coroutine B adds marker
Coroutine C deletes marker
Coroutine D adds old marker back

The reducer decides deterministically whether each incoming update should apply.

How stale updates are blocked

Each marker has a version.

The rule is:

newer version wins
older version is ignored
same version: delete wins
confirmed state wins over pending state

So an old WebSocket or retry event cannot overwrite a newer local change.

How deleted markers stay deleted

When a marker is deleted, the solution does not simply remove it from memory.

It keeps a tombstone:

deleted = true

The UI hides deleted markers, but the internal store remembers the deletion. This prevents an old retry or WebSocket update from bringing the marker back.

How retry is made safer

Retry events do not upload immediately.

They first go through the reducer:

MarkerAction.RetryUpload(marker)

The reducer checks whether that marker version is still the current pending state. If the marker was already deleted or replaced by a newer version, the retry is ignored.

How duplicate uploads are prevented

The solution keeps:

inFlightUploads

This tracks uploads already running for the same marker/version/delete state, so the same upload is not started multiple times concurrently.

How GPS is handled

GPS updates can be high-frequency, so the solution uses:

conflate()
sample(250L)

That means the UI does not process every single location event. It only processes the latest update at a controlled interval.

Main idea

The fixed architecture is:

Immutable state for Compose
Single-writer reducer for mutations
Version checks for stale events
Tombstones for deletes
Deduplication for uploads
Throttling for GPS
Lifecycle-aware UI collection

----Test

The most important tests are race-condition tests around stale updates, delete tombstones, retries, duplicate uploads, and GPS spam.

I would test these cases:

1. Local create appears in UI optimistically.
2. Older WebSocket event cannot overwrite newer marker.
3. Delete hides marker from UI.
4. Deleted marker is not resurrected by old WebSocket event.
5. Deleted marker is not resurrected by old retry event.
6. Retry only uploads if the marker version is still current.
7. Duplicate retries do not start duplicate uploads.
8. Upload success for stale version is ignored.
9. GPS update does not resurrect deleted marker.
10. GPS updates are throttled/conflated.

For testability, make the repository an interface and inject the IO dispatcher:

interface IncidentRepository {
    val websocketEvents: Flow<Marker>
    val retryEvents: Flow<Marker>
    val gpsEvents: Flow<Marker>

    suspend fun uploadMarker(marker: Marker)
}

And in the ViewModel:

class IncidentViewModel(
    private val repository: IncidentRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    // use ioDispatcher instead of Dispatchers.IO
}
Example test setup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestWatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeIncidentRepository : IncidentRepository {
    override val websocketEvents = MutableSharedFlow<Marker>()
    override val retryEvents = MutableSharedFlow<Marker>()
    override val gpsEvents = MutableSharedFlow<Marker>()

    val uploadedMarkers = mutableListOf<Marker>()

    var uploadGate: CompletableDeferred<Unit>? = null

    override suspend fun uploadMarker(marker: Marker) {
        uploadedMarkers += marker
        uploadGate?.await()
    }
}
1. Local create appears optimistically
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun localCreate_addsMarkerToUi() = runTest {
        val repository = FakeIncidentRepository()
        val viewModel = IncidentViewModel(
            repository = repository,
            ioDispatcher = mainDispatcherRule.dispatcher
        )

        runCurrent()

        viewModel.onMarkerCreated(
            Marker(
                id = "m1",
                title = "Victim located",
                lat = 10.0,
                lng = 20.0,
                version = 0
            )
        )

        advanceUntilIdle()

        val markers = viewModel.uiState.value.markers

        assertEquals(1, markers.size)
        assertEquals("m1", markers.first().id)
        assertEquals("Victim located", markers.first().title)
    }
}
2. Older WebSocket event cannot overwrite newer marker
@Test
fun olderWebSocketEvent_doesNotOverwriteNewerMarker() = runTest {
    val repository = FakeIncidentRepository()
    val viewModel = IncidentViewModel(
        repository = repository,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    runCurrent()

    repository.websocketEvents.emit(
        Marker(
            id = "m1",
            title = "New title",
            lat = 10.0,
            lng = 20.0,
            version = 2
        )
    )

    repository.websocketEvents.emit(
        Marker(
            id = "m1",
            title = "Old title",
            lat = 99.0,
            lng = 99.0,
            version = 1
        )
    )

    advanceUntilIdle()

    val marker = viewModel.uiState.value.markers.single()

    assertEquals("New title", marker.title)
    assertEquals(2, marker.version)
}
3. Deleted marker is not resurrected by old WebSocket event
@Test
fun deletedMarker_isNotResurrectedByOldWebSocketEvent() = runTest {
    val repository = FakeIncidentRepository()
    val viewModel = IncidentViewModel(
        repository = repository,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    runCurrent()

    repository.websocketEvents.emit(
        Marker(
            id = "m1",
            title = "Road blocked",
            lat = 10.0,
            lng = 20.0,
            version = 1
        )
    )

    advanceUntilIdle()

    viewModel.onMarkerDeleted("m1")

    advanceUntilIdle()

    repository.websocketEvents.emit(
        Marker(
            id = "m1",
            title = "Road blocked",
            lat = 10.0,
            lng = 20.0,
            version = 1
        )
    )

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.markers.isEmpty())
}
4. Deleted marker is not resurrected by retry event
@Test
fun deletedMarker_isNotResurrectedByRetryEvent() = runTest {
    val repository = FakeIncidentRepository()
    val viewModel = IncidentViewModel(
        repository = repository,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    runCurrent()

    val oldMarker = Marker(
        id = "m1",
        title = "Hazard",
        lat = 10.0,
        lng = 20.0,
        version = 1,
        pendingSync = true
    )

    repository.websocketEvents.emit(oldMarker.copy(pendingSync = false))

    advanceUntilIdle()

    viewModel.onMarkerDeleted("m1")

    advanceUntilIdle()

    repository.retryEvents.emit(oldMarker)

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.markers.isEmpty())

    val resurrectingUploads = repository.uploadedMarkers.filter {
        it.id == "m1" && !it.deleted && it.version == 1L
    }

    assertTrue(resurrectingUploads.isEmpty())
}
5. Duplicate retry does not start duplicate upload
@Test
fun duplicateRetry_doesNotStartDuplicateUpload() = runTest {
    val repository = FakeIncidentRepository()
    repository.uploadGate = CompletableDeferred()

    val viewModel = IncidentViewModel(
        repository = repository,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    runCurrent()

    viewModel.onMarkerCreated(
        Marker(
            id = "m1",
            title = "Victim",
            lat = 10.0,
            lng = 20.0,
            version = 0
        )
    )

    advanceUntilIdle()

    val pendingMarker = viewModel.uiState.value.markers.single()

    repository.retryEvents.emit(pendingMarker)
    repository.retryEvents.emit(pendingMarker)

    advanceUntilIdle()

    val uploadsForSameVersion = repository.uploadedMarkers.count {
        it.id == "m1" && it.version == pendingMarker.version
    }

    assertEquals(1, uploadsForSameVersion)
}
6. GPS update does not resurrect deleted marker
@Test
fun gpsUpdate_doesNotResurrectDeletedMarker() = runTest {
    val repository = FakeIncidentRepository()
    val viewModel = IncidentViewModel(
        repository = repository,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    runCurrent()

    repository.websocketEvents.emit(
        Marker(
            id = "m1",
            title = "Responder",
            lat = 10.0,
            lng = 20.0,
            version = 1
        )
    )

    advanceUntilIdle()

    viewModel.onMarkerDeleted("m1")

    advanceUntilIdle()

    repository.gpsEvents.emit(
        Marker(
            id = "m1",
            title = "Responder",
            lat = 11.0,
            lng = 21.0,
            version = 99
        )
    )

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.markers.isEmpty())
}
7. Upload success for stale version is ignored

This catches a nasty race:

create marker v1
upload v1 starts
delete marker v2
upload v1 succeeds late
v1 success must not undo v2 delete
@Test
fun staleUploadSuccess_doesNotUndoNewerDelete() = runTest {
    val repository = FakeIncidentRepository()
    repository.uploadGate = CompletableDeferred()

    val viewModel = IncidentViewModel(
        repository = repository,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    runCurrent()

    viewModel.onMarkerCreated(
        Marker(
            id = "m1",
            title = "Victim",
            lat = 10.0,
            lng = 20.0,
            version = 0
        )
    )

    advanceUntilIdle()

    viewModel.onMarkerDeleted("m1")

    advanceUntilIdle()

    repository.uploadGate?.complete(Unit)

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.markers.isEmpty())
}
