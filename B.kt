Android Playback Focus Coordinator

Context

An Android media app can receive playback requests from user taps, headset buttons, Bluetooth auto-play, Android Auto, notification actions, voice commands, and system resume.

Requests may arrive while audio focus is lost, the output route is unavailable, the player is not ready, the app is backgrounded, or the app is restoring after process death. The coordinator must decide when playback can start, when it must pause, when it may resume, and which requests must never replay.

Task

Implement "PlaybackFocusCoordinator".

Starter Code

class PlaybackFocusCoordinator {
    fun submit(request: PlaybackRequest) {
        TODO("Implement")
    }

    fun updateEnvironment(environment: PlaybackEnvironment) {
        TODO("Implement")
    }

    fun nextAction(): PlaybackAction? {
        TODO("Implement")
    }

    fun snapshot(): SavedPlaybackState {
        TODO("Implement")
    }

    fun restore(saved: SavedPlaybackState) {
        TODO("Implement")
    }
}

data class PlaybackRequest(
    val id: String,
    val mediaId: String,
    val origin: RequestOrigin,
    val priority: Int,
    val timestampMillis: Long
)

enum class RequestOrigin {
    User,
    Notification,
    Headset,
    BluetoothAuto,
    AndroidAuto,
    Voice,
    SystemResume
}

data class PlaybackEnvironment(
    val lifecycle: PlaybackLifecycle,
    val audioFocus: AudioFocusState,
    val route: OutputRouteState,
    val isPlayerReady: Boolean
)

enum class PlaybackLifecycle {
    Foreground,
    Background,
    Destroyed
}

enum class AudioFocusState {
    Granted,
    LostTransient,
    LostPermanent
}

enum class OutputRouteState {
    Available,
    Unavailable
}

data class PlaybackAction(
    val requestId: String?,
    val mediaId: String?,
    val type: PlaybackActionType,
    val reason: PlaybackActionReason? = null
)

enum class PlaybackActionType {
    Start,
    Pause,
    Resume
}

enum class PlaybackActionReason {
    FocusLost,
    FocusRegained,
    RouteUnavailable
}

class SavedPlaybackState internal constructor(
    internal val payload: String
)

Requirements

1. Use only the Kotlin standard library. Do not use Android framework classes, Jetpack libraries, coroutines, serialization libraries, or third-party dependencies.

2. A new coordinator starts with "lifecycle = Background", "audioFocus = LostPermanent", "route = Unavailable", and "isPlayerReady = false".

3. A "Start" action may be emitted only when "audioFocus = Granted", "route = Available", "isPlayerReady = true", and "lifecycle != Destroyed".

4. A request id may produce at most one "Start" action, including across "snapshot()" and "restore()".

5. If "submit" receives a request id that has already produced a "Start" action in the active restored history, ignore it.

6. If the same request id is submitted more than once before it starts, the first accepted request wins.

7. Requests that cannot start because of environment state must remain available until they become startable, are replaced by "restore", or become ignored by idempotency rules.

8. Manual origins are "User", "Notification", "Headset", "AndroidAuto", and "Voice".

9. Automatic origins are "BluetoothAuto" and "SystemResume".

10. An automatic request must be ignored if its "timestampMillis" is less than or equal to the latest accepted manual request timestamp for a different "mediaId".

11. When multiple requests are startable, choose by highest "priority", then lowest "timestampMillis", then lexicographically smallest "id".

12. Selection among distinct requests must not depend on submission order.

13. "nextAction()" must record the selected start request as already started before returning its "Start" action.

14. If no action is currently valid, "nextAction()" returns "null" and must not discard deferred requests.

15. If playback was started by this coordinator and audio focus changes from "Granted" to "LostTransient" or "LostPermanent", emit one "Pause" action with reason "FocusLost".

16. A focus-loss pause may be followed by one "Resume" action only after audio focus becomes "Granted" again, the route is available, the player is ready, and lifecycle is not "Destroyed".

17. "LostPermanent" clears focus-resume eligibility after the required pause is emitted.

18. If playback was started or resumed by this coordinator and the route changes to "Unavailable", emit one "Pause" action with reason "RouteUnavailable".

19. Route recovery must not by itself emit "Resume".

20. A newer start request must take precedence over a focus-regain resume if both are valid at the same "nextAction()" call.

21. "snapshot()" must contain enough information for "restore()" to continue deferred requests and prevent replay of requests already started before the snapshot.

22. The "SavedPlaybackState.payload" format is internal to the implementation, but it must be produced and consumed without external dependencies.

23. "restore(saved)" replaces all playback-request history represented by the current coordinator with the saved history.

24. "restore(saved)" must not change the current "PlaybackEnvironment".

25. All public methods must behave as if each call executes atomically in some sequential order.

26. The coordinator must be safe under concurrent "submit", "updateEnvironment", "nextAction", "snapshot", and "restore" calls.

Output Format

Return:

1. Full Kotlin implementation.
2. Brief explanation of request ordering, stale automatic suppression, pause/resume behavior, restore behavior, and concurrency behavior.
3. Important tests or pseudocode tests.







1. Stale Bluetooth auto-play suppressed by newer manual request
Submit a manual User request for media B at timestamp 200. Then submit a BluetoothAuto request for media A at timestamp 200 or earlier. Make the environment startable. Verify only B can start and the automatic request is ignored.

2. Automatic request for same media is not incorrectly suppressed
Submit a manual User request for media A at timestamp 200, then a BluetoothAuto request for media A at timestamp 150 before either starts. Verify suppression does not happen merely because the auto request is old; normal id/order rules decide what starts.

3. Deferred automatic request becomes stale after later manual request
Submit a BluetoothAuto request while focus is lost. Then submit a newer User request for different media before focus is granted. When the environment becomes startable, verify the automatic request is ignored and the manual request starts.

4. Duplicate request id with conflicting fields
Submit two requests with the same id but different mediaId, origin, priority, and timestamp before start. Verify the first accepted request wins and the later duplicate cannot alter ordering or target media.

5. Started request id cannot replay after snapshot restore
Start request A, take a snapshot, restore into a new coordinator, then submit A again. Verify A never produces another Start.

6. Deferred request survives snapshot restore
Submit request A while the route is unavailable or the player is not ready. Snapshot, restore into a new coordinator, make the environment startable, and verify A starts exactly once.

7. Restore replaces current request history
Coordinator has deferred request A. Restore a saved state containing only deferred request B. Make the environment startable. Verify A is gone and only B can start.

8. Restore does not change environment
Restore a saved state containing a startable request while the current environment still has focus lost or route unavailable. Verify nextAction returns null until the environment is updated.

9. Priority ordering after multiple deferred gates open together
Submit several requests while focus is lost, route unavailable, and player not ready. Later make all gates valid at once. Verify start order is priority, then timestamp, then id, independent of submit order.

10. Start beats focus-regain resume
Start media A, lose focus transiently and emit Pause. While focus is lost, submit a newer start request for media B. Restore focus with route/player/lifecycle valid. Verify the next action is Start for B, not Resume for A.

11. Transient focus loss emits exactly one pause
Start playback, change focus from Granted to LostTransient, call nextAction repeatedly, and verify exactly one Pause(FocusLost) is emitted until focus changes again.

12. Transient focus regain resumes exactly once
After a focus-loss pause, restore focus to Granted with route available and player ready. Verify exactly one Resume(FocusRegained) is emitted, and repeated nextAction calls do not resume again.

13. Permanent focus loss does not allow resume
Start playback, change focus from Granted to LostPermanent, emit Pause(FocusLost), then change focus back to Granted. Verify no Resume is emitted from that prior permanent loss.

14. Route loss pause is not resumed by route recovery
Start or resume playback, change route to Unavailable and emit Pause(RouteUnavailable). Then restore route to Available with focus granted and player ready. Verify route recovery alone does not emit Resume.

15. Route loss and focus loss do not double-pause
Start playback, then update environment so focus is lost and route becomes unavailable before nextAction. Verify only one pause is emitted for that interruption state, not two duplicate pauses.

16. Focus loss after route pause does not create focus-resume eligibility
Start playback, route becomes unavailable and emits Pause(RouteUnavailable). Then focus becomes LostTransient and later Granted. Verify no Resume occurs because the pause was route-caused, not focus-caused.

17. Start request after pause supersedes resume path
Start A, lose focus transiently, emit Pause, submit request B, then regain focus. Verify B starts and A does not resume afterward.

18. Lifecycle Destroyed blocks start and resume
Have a deferred start request and also a focus-resume-eligible playback. Set lifecycle to Destroyed with focus granted, route available, and player ready. Verify nextAction returns null.

19. Background does not block start
Set lifecycle to Background with focus granted, route available, and player ready. Submit a request. Verify Start may emit because only Destroyed blocks start.

20. Player readiness gate preserves request ordering
Submit high-priority and low-priority requests while player is not ready. Make player ready later. Verify no request was lost and ordering is still by priority, timestamp, id.

21. Concurrent nextAction race for one startable request
Submit one startable request and call nextAction concurrently from many callers. Verify exactly one caller receives Start and all others receive null or actions valid under later state changes.

22. Concurrent duplicate submit race
Concurrently submit several versions of the same request id with different media and priorities. Verify exactly one version can ever start, consistent with some atomic ordering.

23. Concurrent focus-loss update and nextAction
After starting playback, race an environment update to LostTransient against nextAction. Verify the result is consistent with a valid atomic order: either no pause before the update is visible, or exactly one Pause after it.

24. Concurrent restore and nextAction
Current coordinator has startable request A. Saved state contains startable request B. Race restore(savedWithB) against nextAction. Verify behavior matches either restore-before-action or action-before-restore, never a mixed state where both histories corrupt idempotency.

25. Snapshot during concurrent submit/start
Interleave submit, nextAction, and snapshot, then restore snapshots into fresh coordinators. Verify no request id that had started before a snapshot can replay from that snapshot, and deferred requests reflect a valid atomic ordering.

26. Mixed stress test
Randomly interleave submit, environment updates, nextAction, snapshot, and restore. Verify invariants: no request id starts more than once, no Start unless focus granted + route available + player ready + lifecycle not Destroyed, no protected stale automatic request overrides a newer manual different-media request, pause/resume ownership rules hold, and results are consistent with atomic method ordering.
