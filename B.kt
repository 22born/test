Context
An Android app has a screen that lets users search for nearby charging stations for electric vehicles.
The screen supports:
typing a search query,
changing a distance filter,
automatic refresh when the user’s location changes,
manual pull-to-refresh,
showing cached results immediately while a fresh network request is running.
Multiple refresh triggers may happen close together.
The app must guarantee that:
The UI only shows results for the latest search parameters.
A slower older network request must not overwrite a newer result.
Cached results may be shown immediately, but they must match the current search parameters.
Loading state must stay correct even if multiple refreshes overlap.
The solution must be safe across Android lifecycle events.
The ViewModel must not leak work after it is cleared.
The following Kotlin implementation is currently used in /app/Main.kt.
Starter Code
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchParams(
    val query: String,
    val radiusKm: Int,
    val latitude: Double,
    val longitude: Double
)

data class Station(
    val id: String,
    val name: String,
    val distanceKm: Double
)

data class StationUiState(
    val params: SearchParams? = null,
    val stations: List<Station> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

interface StationRepository {
    suspend fun cachedStations(params: SearchParams): List<Station>
    suspend fun fetchStations(params: SearchParams): List<Station>
}

class StationSearchViewModel(
    private val repository: StationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StationUiState())
    val uiState: StateFlow<StationUiState> = _uiState

    private var currentParams: SearchParams? = null
    private val cache = mutableMapOf<SearchParams, List<Station>>()

    fun updateSearchParams(params: SearchParams) {
        currentParams = params
        refresh()
    }

    fun refresh() {
        val params = currentParams ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                params = params,
                isLoading = true,
                error = null
            )

            val cached = withContext(Dispatchers.IO) {
                cache[params] ?: repository.cachedStations(params)
            }

            cache[params] = cached

            _uiState.value = _uiState.value.copy(
                params = params,
                stations = cached
            )

            try {
                val fresh = withContext(Dispatchers.IO) {
                    repository.fetchStations(params)
                }

                cache[params] = fresh

                _uiState.value = _uiState.value.copy(
                    params = params,
                    stations = fresh,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}
Task
Identify the issue or issues in /app/Main.kt and implement the correct solution.
Your answer should only include:
1. Issue

2. Corrected Kotlin Implementation
Constraints
Keep the public class name StationSearchViewModel.
Keep the public methods:
fun updateSearchParams(params: SearchParams)
fun refresh()
Use only Kotlin coroutines and AndroidX lifecycle APIs.
Do not block the main thread.
Do not use GlobalScope.
The UI must never show results for stale search parameters.
A slower older request must not overwrite a newer request.
Loading state must remain correct during rapid repeated refreshes.
The cache must not be corrupted by concurrent refreshes.





Correct solution
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class SearchParams(
    val query: String,
    val radiusKm: Int,
    val latitude: Double,
    val longitude: Double
)

data class Station(
    val id: String,
    val name: String,
    val distanceKm: Double
)

data class StationUiState(
    val params: SearchParams? = null,
    val stations: List<Station> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

interface StationRepository {
    suspend fun cachedStations(params: SearchParams): List<Station>
    suspend fun fetchStations(params: SearchParams): List<Station>
}

class StationSearchViewModel(
    private val repository: StationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StationUiState())
    val uiState: StateFlow<StationUiState> = _uiState.asStateFlow()

    private val jobLock = Any()
    private val cacheMutex = Mutex()
    private val latestRequestId = AtomicLong(0L)

    private var currentParams: SearchParams? = null
    private var refreshJob: Job? = null

    private val cache = mutableMapOf<SearchParams, List<Station>>()

    fun updateSearchParams(params: SearchParams) {
        synchronized(jobLock) {
            currentParams = params
            startRefreshLocked(params)
        }
    }

    fun refresh() {
        synchronized(jobLock) {
            val params = currentParams ?: return
            startRefreshLocked(params)
        }
    }

    private fun startRefreshLocked(params: SearchParams) {
        val requestId = latestRequestId.incrementAndGet()

        // Cancel older work so an outdated request does not continue updating UI.
        refreshJob?.cancel()

        refreshJob = viewModelScope.launch {
            runRefresh(params, requestId)
        }
    }

    private suspend fun runRefresh(
        params: SearchParams,
        requestId: Long
    ) {
        if (!isLatest(requestId)) return

        _uiState.update { current ->
            if (isLatest(requestId)) {
                current.copy(
                    params = params,
                    isLoading = true,
                    error = null
                )
            } else {
                current
            }
        }

        try {
            val cached = loadCached(params, requestId)

            if (!isLatest(requestId)) return

            // Cached results are shown only if they still match the latest request.
            _uiState.update { current ->
                if (isLatest(requestId)) {
                    current.copy(
                        params = params,
                        stations = cached,
                        isLoading = true,
                        error = null
                    )
                } else {
                    current
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Cache failure should not stop the fresh network request.
            if (!isLatest(requestId)) return
        }

        try {
            val fresh = withContext(Dispatchers.IO) {
                repository.fetchStations(params).toList()
            }

            if (!isLatest(requestId)) return

            cacheMutex.withLock {
                if (isLatest(requestId)) {
                    cache[params] = fresh
                }
            }

            if (!isLatest(requestId)) return

            _uiState.update { current ->
                if (isLatest(requestId)) {
                    current.copy(
                        params = params,
                        stations = fresh,
                        isLoading = false,
                        error = null
                    )
                } else {
                    current
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isLatest(requestId)) return

            _uiState.update { current ->
                if (isLatest(requestId)) {
                    current.copy(
                        params = params,
                        isLoading = false,
                        error = e.message
                    )
                } else {
                    current
                }
            }
        }
    }

    private suspend fun loadCached(
        params: SearchParams,
        requestId: Long
    ): List<Station> {
        val memoryCached = cacheMutex.withLock {
            cache[params]
        }

        if (memoryCached != null) {
            return memoryCached
        }

        val repositoryCached = withContext(Dispatchers.IO) {
            repository.cachedStations(params).toList()
        }

        if (isLatest(requestId)) {
            cacheMutex.withLock {
                if (isLatest(requestId)) {
                    cache[params] = repositoryCached
                }
            }
        }

        return repositoryCached
    }

    private fun isLatest(requestId: Long): Boolean {
        return latestRequestId.get() == requestId
    }
}
Re-verification of correctness
The original issue was that multiple refresh coroutines could run at the same time. A slow older request could update _uiState after a newer request had already completed. Also, the shared mutable cache was accessed without synchronization.
The corrected implementation fixes this by:
assigning every refresh a monotonically increasing requestId;
cancelling the previous refresh job when a new one starts;
checking isLatest(requestId) before every UI update and cache write;
protecting the shared cache with a Mutex;
using viewModelScope, so work is lifecycle-bound and cancelled when the ViewModel is cleared;
rethrowing CancellationException, so cancellation is not accidentally treated as a normal failure;
ensuring cached results are only shown for the latest active search parameters.
This satisfies the requirements: stale responses cannot overwrite newer UI, loading state is controlled only by the latest request, unrelated work is not blocked by a global app-wide lock, and cache access is concurrency-safe.
Test cases to write
#
Test case
Description
Expectation
1
Initial state
Create the ViewModel and read uiState.
params == null, stations empty, isLoading == false, error == null.
2
Refresh before params
Call refresh() before updateSearchParams().
No crash and UI state remains unchanged.
3
Single successful search
Call updateSearchParams(params) and let cached + fresh complete.
Final state has params, fresh stations, isLoading == false, error == null.
4
Cached results shown first
Repository returns cached results before fresh results.
UI first shows cached stations with isLoading == true, then fresh stations with isLoading == false.
5
No cached results
Cached repository returns empty list, fresh returns stations.
UI may show empty cached state while loading, then fresh stations finally.
6
Fresh failure after cached success
Cached succeeds, network fetch throws.
Cached stations remain visible, isLoading == false, error is set.
7
Cache failure does not block network
Cached repository throws but fresh succeeds.
Final state shows fresh stations, isLoading == false, error == null.
8
Fresh failure with no cached data
Cached returns empty or fails, fresh throws.
Final state has no fresh stations, isLoading == false, error is set.
9
Slow old request cannot overwrite newer request
Start request A, then request B. Make B finish first and A finish later.
Final UI shows only B results. A never overwrites B.
10
Slow old cached result cannot flash stale data
Start request A, then request B before A’s cache returns.
UI must not show A’s cached stations after B becomes latest.
11
Slow old network error cannot overwrite newer success
Request A fails late, request B succeeds earlier.
Final UI remains B success with error == null.
12
Loading state not cleared by old request
Request A starts, request B starts. A finishes or fails while B is still running.
isLoading remains true until B completes.
13
Rapid repeated refresh with same params
Call refresh() many times quickly for same params.
Final UI comes from the latest refresh only; no stale overwrite.
14
Rapid parameter changes
Call updateSearchParams() repeatedly with different params.
Final UI corresponds only to the last params.
15
Pull-to-refresh after successful search
Complete search once, then call refresh() again.
UI sets isLoading == true, then updates with latest fresh results.
16
Cache key isolation
Search params A and B have different cached values.
A never shows B’s cached stations and B never shows A’s cached stations.
17
Cache reused for same params
Search params A once, then refresh A again.
Second refresh can show memory cached A results immediately before fresh completes.
18
Concurrent cache access stress
Trigger many overlapping refreshes while repository delays responses.
No ConcurrentModificationException, no corrupted cache, final UI is latest request.
19
Cancellation is not reported as error
Start request A, then start request B causing A cancellation.
UI should not show cancellation as error.
20
ViewModel cleared cancels work
Start a long-running request, then clear the ViewModel.
Pending work is cancelled; no further UI updates from that request.
21
Repository returns mutable list
Repository returns a mutable list and mutates it later.
UI/cache should not be affected unexpectedly because results are copied with toList().
22
Empty fresh result replaces cached result
Cached returns stations, fresh returns empty list.
Final state shows empty fresh result with isLoading == false.
23
Same station IDs across results
Cached and fresh contain overlapping stations.
Final state exactly matches fresh result, not a merged or duplicated list.
24
Error cleared on next success
One refresh fails, next refresh succeeds.
Final state has successful stations and error == null.
25
Latest request with null/blank query if allowed
Use blank query params if the app permits it.
Behavior follows repository result; no crash or stale overwrite.
