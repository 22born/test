Async Resource Memoizer
Context
In a Kotlin/Android app, many coroutines may request the same resource at the same time, such as a config value, auth token, or profile blob.
You need a small coroutine-safe memoizer that shares concurrent work, caches successful results, and handles invalidation correctly.
Task
Implement:
class AsyncMemo<K, V>(
    private val scope: CoroutineScope
) {
    suspend fun get(
        key: K,
        loader: suspend () -> V
    ): V

    suspend fun invalidate(key: K)

    suspend fun clear()
}
Requirements
Concurrent calls for the same key should share one ongoing load when none of them is separated by a completed invalidation or clear.
Calls for different keys must not block or interfere with each other unnecessarily.
Cancelling one caller must not cancel the result for other callers waiting on the same key.
Successful loads must be reused by later calls for the same key.
Failed loads must be reported to current callers, but later calls must be able to retry.
After invalidate(key) completes, later get(key) calls must not return older cached data.
After invalidate(key) completes, later get(key) calls must not attach to older unfinished work for that key.
After clear() completes, later get(...) calls must not return any older cached data.
After clear() completes, later get(...) calls must not attach to any older unfinished work.
Work that began before invalidation may still finish, but it must not affect later calls if it is no longer current.
The implementation must behave correctly under concurrent calls from multiple coroutine dispatchers.
The implementation must not block threads while waiting for asynchronous work.
Output Format
Return:
Full Kotlin implementation.
Brief explanation of the concurrency behavior.
Important coroutine tests or pseudocode tests.
Final test cases
1. Concurrent same-key calls share one load
Start many concurrent get("a") calls with a paused loader.
Assert:
- The loader runs exactly once.
- All callers receive the same result.
- A later get("a") returns the cached result without running the loader again.
2. Different keys load independently
Start a blocked get("a"), then start get("b").
Assert:
- get("b") completes without waiting for get("a").
- The loader for "a" and the loader for "b" each run once.
3. Caller cancellation does not cancel shared work
Start two callers waiting on get("a"). Cancel one caller while the shared load is still running.
Assert:
- The second caller still receives the result.
- The shared loader is not cancelled.
- The result is cached for later get("a") calls.
4. Successful load is cached
Call get("a") and let it return "value-1". Then call get("a") again with a different loader that would return "value-2".
Assert:
- The second call returns "value-1".
- The second loader does not run.
5. Loader failure is shared but not cached
Start multiple concurrent get("a") calls whose shared loader throws.
Assert:
- All current callers receive the same failure.
- A later get("a") invokes the loader again.
- If the retry succeeds, the successful value is cached.
6. Invalidate removes cached value
Cache "old" for key "a". Call invalidate("a"). Then call get("a") with a loader returning "new".
Assert:
- The later get("a") returns "new".
- The old cached value is not returned after invalidate completes.
7. Invalidate during in-flight load prevents stale reuse
Start get("a") with loader A paused. Call invalidate("a"). Then start another get("a") with loader B.
Assert:
- The second get("a") does not attach to loader A.
- Loader B runs separately.
- If loader A completes with "old" and loader B completes with "new", later get("a") returns "new".
8. Old waiter may complete after invalidation
Start get("a") with loader A paused. Call invalidate("a") before loader A completes. Then complete loader A.
Assert:
- The original waiter may receive loader A's result.
- A later get("a") must not receive loader A's result from cache.
9. Clear removes all cached values
Cache values for "a" and "b". Call clear(). Then call get("a") and get("b") with new loaders.
Assert:
- Both new loaders run.
- No cached value from before clear is returned.
10. Clear during multiple in-flight loads prevents stale reuse
Start paused loads for "a" and "b". Call clear(). Start new gets for both keys.
Assert:
- New gets do not attach to the old loads.
- Old load completions do not populate the cache.
- Later gets return only the newer results.
11. Get after invalidate cannot observe old cache
Cache a value for "a". Call invalidate("a") and wait for it to return. Then call get("a").
Assert:
- The post-invalidate call never returns the old cached value.
12. Get after clear cannot observe old cache
Cache values for several keys. Call clear() and wait for it to return. Then call get(...) for those keys.
Assert:
- No post-clear call returns any pre-clear cached value.
13. Invalidate racing with loader success
Use deterministic ordering for both cases.
Case A:
loader completes before invalidate takes effect
Assert:
- invalidate removes the completed value.
- later get("a") runs a new loader.
Case B:
invalidate completes before loader result is committed
Assert:
- late loader result is not cached.
- later get("a") runs a new loader.
14. Clear racing with loader success
Same as Test 13, but with clear() instead of invalidate(key).
Assert:
- Results completed before clear are removed by clear.
- Results completed after clear do not repopulate the cache.
- Later gets run new loaders.
15. Repeated invalidate/get cycles do not resurrect stale values
Repeat many times:
1. Start get("a") with an old paused loader.
2. Call invalidate("a").
3. Start a new get("a").
4. Complete the old loader.
5. Complete the new loader.
Assert:
- The final cached value always belongs to the latest valid load.
- No post-invalidate get returns an old value.
16. Repeated clear/get cycles do not resurrect stale values
Repeat many times across multiple keys:
1. Start old paused loads.
2. Call clear().
3. Start new loads.
4. Complete old loads.
5. Complete new loads.
Assert:
- Old completions never repopulate the cache.
- Later gets return only values from after the latest clear.
17. Cancellation plus invalidation
Start two callers for get("a"). Cancel one caller. Then call invalidate("a") before the loader completes.
Assert:
- The remaining old waiter may still receive the old result.
- Later get("a") does not receive that old result from cache.
- Caller cancellation and invalidation are handled as separate concepts.
18. Multi-dispatcher stress test
Run many coroutines across different dispatchers. Randomly call:
- get(key)
- invalidate(key)
- clear()
- cancel some callers
Assert:
- No deadlocks or hangs.
- No stale value is returned after a completed invalidate or clear boundary.
- Concurrent same-key calls that are not separated by invalidate/clear do not trigger duplicate valid loads.
- Different keys continue making progress independently.
These tests are enough to evaluate the core reasoning: shared work, cancellation isolation, failure behavior, invalidation boundaries, stale-result prevention, and multi-dispatcher safety.
