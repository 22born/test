## 3. Behavioural Requirements

Implement all TODO functions.

`simulateTick(old, dt)`
Return the next `State` using the physics equations below:

```text
speed = old.velocity.magnitude()
dragForce = old.velocity * (-PhysicsConfig.dragCoefficient * speed)
magnusForce = cross(old.spin, old.velocity) * PhysicsConfig.magnusCoefficient
acceleration = PhysicsConfig.gravity + (dragForce + magnusForce) / PhysicsConfig.mass
nextVelocity = old.velocity + acceleration * dt
nextPosition = old.position + nextVelocity * dt
nextSpin = old.spin * exp(-PhysicsConfig.spinDecay * dt)
```

`classifyCrossing(previous, current)`
Check the straight path from `previous` to `current`. Return the first event on that path, or `null` if there is none.

Possible returned events:

```text
BLOCKED_BY_WALL: reaches x = 9.0 inside the wall rectangle
GOAL: reaches x = 25.0 inside the goal rectangle
MISS: reaches x = 25.0 outside the goal rectangle
HIT_GROUND: reaches z = 0.0 while moving downward before the goal
```

The returned `Crossing` must include the event result, the event position, and `alpha`, where `0.0` means `previous` and `1.0` means `current`.

`simulateKick(kick)`
Start at `(0.0, 0.0, 0.0)` with the kick’s initial velocity and spin. Keep calling `simulateTick` and `classifyCrossing` until the kick ends. Return `TIMEOUT` if no event happens before `PhysicsConfig.maxTime`.

`simulateManyKicks(kicks)`
Use Kotlin coroutines to simulate the kicks. Return one result per kick, sorted by `kickId`.

Repeated runs with the same input must return exactly the same results.






You are given a Kotlin programming task. Complete the TODO functions and return only the completed Kotlin source code.

Do not include explanations, markdown, comments about your approach, or prose outside the code.

# Problem: Deterministic Concurrent Football Bend Simulator

## 1. Context

A football free-kick simulator predicts whether a spinning ball bends around a defensive wall and enters the goal.

The simulation uses a 3D coordinate system:

```text
x = forward direction toward the goal
y = left/right sideways direction
z = height above the ground
```

The ball starts at:

```text
position = (0.0, 0.0, 0.0)
```

The goal is 25 metres in front of the ball:

```text
goal plane: x = 25.0
goal width: -3.66 <= y <= 3.66
goal height: 0.0 <= z <= 2.44
```

The goal dimensions are based on a standard full-size football goal: 7.32 metres wide and 2.44 metres high.

A simplified defensive wall is 9 metres in front of the ball:

```text
wall plane: x = 9.0
wall width: -1.5 <= y <= 1.5
wall height: 0.0 <= z <= 2.0
```

The simulation advances in fixed time steps:

```text
dt = 0.01 seconds
max simulation time = 5.0 seconds
```

The simulator must run many different kicks concurrently using Kotlin coroutines. Each kick has its own initial velocity and spin.

The same input kick must always produce the same result across repeated runs.

## 2. Constants and Starter Code with TODO Functions

```kotlin
import kotlinx.coroutines.*
import kotlin.math.*

data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: Vec3): Vec3 =
        Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 =
        Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(k: Double): Vec3 =
        Vec3(x * k, y * k, z * k)

    operator fun div(k: Double): Vec3 =
        Vec3(x / k, y / k, z / k)

    fun magnitude(): Double =
        sqrt(x * x + y * y + z * z)
}

fun cross(a: Vec3, b: Vec3): Vec3 =
    Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

data class State(
    val position: Vec3,
    val velocity: Vec3,
    val spin: Vec3
)

data class Kick(
    val id: Int,
    val initialVelocity: Vec3,
    val initialSpin: Vec3
)

enum class KickResult {
    GOAL,
    MISS,
    BLOCKED_BY_WALL,
    HIT_GROUND,
    TIMEOUT
}

data class Crossing(
    val result: KickResult,
    val position: Vec3,
    val alpha: Double
)

data class SimulationResult(
    val kickId: Int,
    val result: KickResult,
    val finalPosition: Vec3,
    val time: Double
)

object PhysicsConfig {
    const val dt = 0.01
    const val maxTime = 5.0

    const val mass = 0.43
    const val dragCoefficient = 0.02
    const val magnusCoefficient = 0.0004
    const val spinDecay = 0.25

    val gravity = Vec3(0.0, 0.0, -9.81)

    const val wallX = 9.0
    const val wallHalfWidth = 1.5
    const val wallHeight = 2.0

    const val goalX = 25.0
    const val goalHalfWidth = 3.66
    const val goalHeight = 2.44
}

fun simulateTick(old: State, dt: Double): State {
    TODO("Implement")
}

fun classifyCrossing(previous: Vec3, current: Vec3): Crossing? {
    TODO("Implement")
}

fun simulateKick(kick: Kick): SimulationResult {
    TODO("Implement")
}

suspend fun simulateManyKicks(kicks: List<Kick>): List<SimulationResult> {
    TODO("Implement")
}
```

## 3. Behavioural Requirements for TODO Functions

Implement all TODO functions.

### `simulateTick(old, dt)`

Advance the ball by one tick.

Use `old.position`, `old.velocity`, and `old.spin`.

`old.velocity` is a `Vec3`, and `Vec3` provides the `magnitude()` function.

Use these equations:

```text
speed = old.velocity.magnitude()
dragForce = old.velocity * (-PhysicsConfig.dragCoefficient * speed)
magnusForce = cross(old.spin, old.velocity) * PhysicsConfig.magnusCoefficient
acceleration = PhysicsConfig.gravity + (dragForce + magnusForce) / PhysicsConfig.mass
nextVelocity = old.velocity + acceleration * dt
nextPosition = old.position + nextVelocity * dt
nextSpin = old.spin * exp(-PhysicsConfig.spinDecay * dt)
return State(nextPosition, nextVelocity, nextSpin)
```

### `classifyCrossing(previous, current)`

Detect the earliest terminal event between `previous` and `current`.

Use linear interpolation for plane crossings:

```text
alpha = (planeValue - previousValue) / (currentValue - previousValue)
point = previous + (current - previous) * alpha
```

Only crossings with `0.0 <= alpha <= 1.0` are valid.

Terminal events are:

```text
BLOCKED_BY_WALL: crosses x = 9.0 inside the wall rectangle
GOAL: crosses x = 25.0 inside the goal rectangle
MISS: crosses x = 25.0 outside the goal rectangle
HIT_GROUND: crosses z = 0.0 downward before reaching the goal
TIMEOUT: simulation reaches 5.0 seconds
```

If the ball crosses the wall plane outside the wall rectangle, this is not terminal.

If the ball crosses the goal plane, it is always terminal.

If multiple terminal events occur between two positions, return the event with the smallest valid `alpha`.

Return `null` if no terminal event occurs.

### `simulateKick(kick)`

Simulate one complete kick.

Start from:

```kotlin
State(
    position = Vec3(0.0, 0.0, 0.0),
    velocity = kick.initialVelocity,
    spin = kick.initialSpin
)
```

Repeatedly call `simulateTick` and `classifyCrossing` until one terminal result occurs.

For `GOAL`, `MISS`, `BLOCKED_BY_WALL`, or `HIT_GROUND`, return the interpolated final position and interpolated time:

```text
time = previousTime + crossing.alpha * PhysicsConfig.dt
finalPosition = crossing.position
```

If no terminal event occurs before `PhysicsConfig.maxTime`, return `TIMEOUT`.

For `TIMEOUT`, return the final simulated position at `PhysicsConfig.maxTime`.

Repeated calls with the same `Kick` must return equal `SimulationResult` values.

### `simulateManyKicks(kicks)`

Run multiple independent kicks concurrently.

This function must be a `suspend` function and must use Kotlin coroutines.

Return one `SimulationResult` per `Kick`.

Return results sorted by `kickId`.

Repeated calls with the same input list must produce exactly equal result lists.

## 4. Required Output Format

Return only the completed Kotlin source code.

The returned source code must include all starter code and completed implementations for:

```text
Vec3
cross
State
Kick
KickResult
Crossing
SimulationResult
PhysicsConfig
simulateTick
classifyCrossing
simulateKick
simulateManyKicks
```

Do not include a `main` function unless necessary for compilation in your chosen format.


////
Sol:
import kotlinx.coroutines.*
import kotlin.math.*

data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: Vec3): Vec3 =
        Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 =
        Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(k: Double): Vec3 =
        Vec3(x * k, y * k, z * k)

    operator fun div(k: Double): Vec3 =
        Vec3(x / k, y / k, z / k)

    fun magnitude(): Double =
        sqrt(x * x + y * y + z * z)
}

fun cross(a: Vec3, b: Vec3): Vec3 =
    Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

data class State(
    val position: Vec3,
    val velocity: Vec3,
    val spin: Vec3
)

data class Kick(
    val id: Int,
    val initialVelocity: Vec3,
    val initialSpin: Vec3
)

enum class KickResult {
    GOAL,
    MISS,
    BLOCKED_BY_WALL,
    HIT_GROUND,
    TIMEOUT
}

data class Crossing(
    val result: KickResult,
    val position: Vec3,
    val alpha: Double
)

data class SimulationResult(
    val kickId: Int,
    val result: KickResult,
    val finalPosition: Vec3,
    val time: Double
)

object PhysicsConfig {
    const val dt = 0.01
    const val maxTime = 5.0

    const val mass = 0.43
    const val dragCoefficient = 0.02
    const val magnusCoefficient = 0.0004
    const val spinDecay = 0.25

    val gravity = Vec3(0.0, 0.0, -9.81)

    const val wallX = 9.0
    const val wallHalfWidth = 1.5
    const val wallHeight = 2.0

    const val goalX = 25.0
    const val goalHalfWidth = 3.66
    const val goalHeight = 2.44
}

fun simulateTick(old: State, dt: Double): State {
    val speed = old.velocity.magnitude()

    val dragForce =
        old.velocity * (-PhysicsConfig.dragCoefficient * speed)

    val magnusForce =
        cross(old.spin, old.velocity) * PhysicsConfig.magnusCoefficient

    val acceleration =
        PhysicsConfig.gravity + (dragForce + magnusForce) / PhysicsConfig.mass

    val nextVelocity =
        old.velocity + acceleration * dt

    val nextPosition =
        old.position + nextVelocity * dt

    val nextSpin =
        old.spin * exp(-PhysicsConfig.spinDecay * dt)

    return State(
        position = nextPosition,
        velocity = nextVelocity,
        spin = nextSpin
    )
}

fun classifyCrossing(previous: Vec3, current: Vec3): Crossing? {
    val candidates = mutableListOf<Crossing>()
    val delta = current - previous

    fun interpolate(alpha: Double): Vec3 =
        previous + delta * alpha

    fun planeAlpha(previousValue: Double, currentValue: Double, planeValue: Double): Double? {
        val denominator = currentValue - previousValue
        if (denominator == 0.0) return null

        val alpha = (planeValue - previousValue) / denominator
        return if (alpha in 0.0..1.0) alpha else null
    }

    val wallAlpha = planeAlpha(previous.x, current.x, PhysicsConfig.wallX)
    if (wallAlpha != null) {
        val point = interpolate(wallAlpha)

        val insideWall =
            point.y >= -PhysicsConfig.wallHalfWidth &&
            point.y <= PhysicsConfig.wallHalfWidth &&
            point.z >= 0.0 &&
            point.z <= PhysicsConfig.wallHeight

        if (insideWall) {
            candidates += Crossing(
                result = KickResult.BLOCKED_BY_WALL,
                position = point,
                alpha = wallAlpha
            )
        }
    }

    val goalAlpha = planeAlpha(previous.x, current.x, PhysicsConfig.goalX)
    if (goalAlpha != null) {
        val point = interpolate(goalAlpha)

        val insideGoal =
            point.y >= -PhysicsConfig.goalHalfWidth &&
            point.y <= PhysicsConfig.goalHalfWidth &&
            point.z >= 0.0 &&
            point.z <= PhysicsConfig.goalHeight

        candidates += Crossing(
            result = if (insideGoal) KickResult.GOAL else KickResult.MISS,
            position = point,
            alpha = goalAlpha
        )
    }

    val movingDownward = current.z < previous.z
    val groundAlpha =
        if (movingDownward) planeAlpha(previous.z, current.z, 0.0) else null

    if (groundAlpha != null) {
        val point = interpolate(groundAlpha)

        if (point.x < PhysicsConfig.goalX) {
            candidates += Crossing(
                result = KickResult.HIT_GROUND,
                position = point,
                alpha = groundAlpha
            )
        }
    }

    return candidates.minWithOrNull(
        compareBy<Crossing> { it.alpha }
            .thenBy { it.result.ordinal }
    )
}

fun simulateKick(kick: Kick): SimulationResult {
    var state = State(
        position = Vec3(0.0, 0.0, 0.0),
        velocity = kick.initialVelocity,
        spin = kick.initialSpin
    )

    var time = 0.0

    while (time < PhysicsConfig.maxTime) {
        val step = min(PhysicsConfig.dt, PhysicsConfig.maxTime - time)

        val previousState = state
        val previousTime = time

        val currentState = simulateTick(previousState, step)
        val crossing = classifyCrossing(previousState.position, currentState.position)

        if (crossing != null) {
            return SimulationResult(
                kickId = kick.id,
                result = crossing.result,
                finalPosition = crossing.position,
                time = previousTime + crossing.alpha * step
            )
        }

        state = currentState
        time += step
    }

    return SimulationResult(
        kickId = kick.id,
        result = KickResult.TIMEOUT,
        finalPosition = state.position,
        time = PhysicsConfig.maxTime
    )
}

suspend fun simulateManyKicks(kicks: List<Kick>): List<SimulationResult> =
    coroutineScope {
        kicks.map { kick ->
            async(Dispatchers.Default) {
                simulateKick(kick)
            }
        }.awaitAll().sortedBy { it.kickId }
    }

////
Tests:
Test cases

These are the tests I would use.

1. Vec3.magnitude() test

Purpose: confirm vector length works.

val v = Vec3(3.0, 4.0, 12.0)
check(abs(v.magnitude() - 13.0) < 1e-9)

Expected:

magnitude = 13.0
2. Cross product test

Purpose: confirm the cross product direction is correct.

val a = Vec3(1.0, 0.0, 0.0)
val b = Vec3(0.0, 1.0, 0.0)

check(cross(a, b) == Vec3(0.0, 0.0, 1.0))

Expected:

cross((1,0,0), (0,1,0)) = (0,0,1)
3. simulateTick returns a new state

Purpose: make sure tick update does not mutate the old state.

val old = State(
    position = Vec3(0.0, 0.0, 0.0),
    velocity = Vec3(20.0, 0.0, 5.0),
    spin = Vec3(0.0, 50.0, 0.0)
)

val next = simulateTick(old, PhysicsConfig.dt)

check(old.position == Vec3(0.0, 0.0, 0.0))
check(next != old)

Expected:

old state unchanged
next state different
4. Wall hit detected by interpolation

Purpose: ball crosses x = 9.0 inside the wall.

val crossing = classifyCrossing(
    previous = Vec3(8.9, 0.0, 1.0),
    current = Vec3(9.1, 0.0, 1.0)
)

check(crossing != null)
check(crossing.result == KickResult.BLOCKED_BY_WALL)
check(abs(crossing.position.x - 9.0) < 1e-9)
check(abs(crossing.alpha - 0.5) < 1e-9)

Expected:

BLOCKED_BY_WALL
position.x = 9.0
alpha = 0.5
5. Wall crossing outside wall is not terminal

Purpose: ball crosses x = 9.0, but outside wall width.

val crossing = classifyCrossing(
    previous = Vec3(8.9, 2.0, 1.0),
    current = Vec3(9.1, 2.0, 1.0)
)

check(crossing == null)

Expected:

null

Because y = 2.0 is outside:

-1.5 <= y <= 1.5
6. Goal detected

Purpose: ball crosses x = 25.0 inside the goal.

val crossing = classifyCrossing(
    previous = Vec3(24.9, 2.0, 1.0),
    current = Vec3(25.1, 2.0, 1.0)
)

check(crossing != null)
check(crossing.result == KickResult.GOAL)
check(abs(crossing.position.x - 25.0) < 1e-9)

Expected:

GOAL
7. Miss detected outside goal width

Purpose: ball crosses the goal plane but is too far sideways.

val crossing = classifyCrossing(
    previous = Vec3(24.9, 4.0, 1.0),
    current = Vec3(25.1, 4.0, 1.0)
)

check(crossing != null)
check(crossing.result == KickResult.MISS)

Expected:

MISS

Because y = 4.0 is outside:

-3.66 <= y <= 3.66
8. Miss detected above goal height

Purpose: ball crosses the goal plane but is too high.

val crossing = classifyCrossing(
    previous = Vec3(24.9, 2.0, 3.0),
    current = Vec3(25.1, 2.0, 3.0)
)

check(crossing != null)
check(crossing.result == KickResult.MISS)

Expected:

MISS

Because z = 3.0 is above 2.44.

9. Ground hit detected

Purpose: ball crosses downward through z = 0.0.

val crossing = classifyCrossing(
    previous = Vec3(10.0, 0.0, 0.2),
    current = Vec3(10.5, 0.0, -0.2)
)

check(crossing != null)
check(crossing.result == KickResult.HIT_GROUND)
check(abs(crossing.position.z - 0.0) < 1e-9)

Expected:

HIT_GROUND
10. Ground hit only when moving downward

Purpose: starting at ground and moving upward should not count as HIT_GROUND.

val crossing = classifyCrossing(
    previous = Vec3(0.0, 0.0, 0.0),
    current = Vec3(0.2, 0.0, 0.1)
)

check(crossing == null)

Expected:

null
11. Earliest event wins

Purpose: if the ball crosses wall and goal in one segment, wall comes first.

val crossing = classifyCrossing(
    previous = Vec3(8.5, 0.0, 1.0),
    current = Vec3(25.5, 0.0, 1.0)
)

check(crossing != null)
check(crossing.result == KickResult.BLOCKED_BY_WALL)
check(abs(crossing.position.x - 9.0) < 1e-9)

Expected:

BLOCKED_BY_WALL
12. Goal plane is always terminal

Purpose: crossing x = 25.0 must return either GOAL or MISS.

val goal = classifyCrossing(
    previous = Vec3(24.9, 0.0, 1.0),
    current = Vec3(25.1, 0.0, 1.0)
)

val miss = classifyCrossing(
    previous = Vec3(24.9, 10.0, 1.0),
    current = Vec3(25.1, 10.0, 1.0)
)

check(goal?.result == KickResult.GOAL)
check(miss?.result == KickResult.MISS)

Expected:

GOAL
MISS
13. simulateKick deterministic test

Purpose: same kick should always return same result.

val kick = Kick(
    id = 1,
    initialVelocity = Vec3(25.0, 1.0, 7.0),
    initialSpin = Vec3(0.0, 60.0, 20.0)
)

val first = simulateKick(kick)

repeat(100) {
    val next = simulateKick(kick)
    check(next == first)
}

Expected:

all 100 runs equal
14. simulateManyKicks returns sorted results

Purpose: coroutine completion order should not affect output order.

val kicks = listOf(
    Kick(3, Vec3(25.0, 1.0, 7.0), Vec3(0.0, 60.0, 20.0)),
    Kick(1, Vec3(20.0, 0.0, 5.0), Vec3(0.0, 20.0, 0.0)),
    Kick(2, Vec3(30.0, 2.0, 8.0), Vec3(0.0, 80.0, 10.0))
)

val results = runBlocking {
    simulateManyKicks(kicks)
}

check(results.map { it.kickId } == listOf(1, 2, 3))

Expected:

results sorted by kickId
15. simulateManyKicks deterministic test

Purpose: concurrent execution should not introduce nondeterminism.

val kicks = listOf(
    Kick(1, Vec3(25.0, 0.0, 7.0), Vec3(0.0, 60.0, 20.0)),
    Kick(2, Vec3(22.0, 1.5, 6.0), Vec3(0.0, 30.0, 10.0)),
    Kick(3, Vec3(30.0, -2.0, 8.0), Vec3(10.0, 70.0, 0.0)),
    Kick(4, Vec3(15.0, 0.0, 2.0), Vec3(0.0, 0.0, 0.0)),
    Kick(5, Vec3(5.0, 0.0, 20.0), Vec3(0.0, 100.0, 0.0))
)

val baseline = runBlocking {
    simulateManyKicks(kicks)
}

repeat(100) {
    val next = runBlocking {
        simulateManyKicks(kicks)
    }

    check(next == baseline)
}

Expected:

all repeated concurrent runs equal
16. Empty input test

Purpose: simulateManyKicks(emptyList()) should work.

val results = runBlocking {
    simulateManyKicks(emptyList())
}

check(results.isEmpty())

Expected:

empty list
17. TIMEOUT test

Purpose: verify timeout result when the ball does not reach a terminal event.

This depends on chosen velocity. A low-forward-speed ball that stays airborne but does not reach the goal may timeout, though many low-speed kicks will hit ground first. To force timeout, use an artificial kick with zero velocity and upward spin that does not move forward but may eventually hit ground. However, gravity will usually cause HIT_GROUND.

A more reliable timeout test may require adjusting constants or using a specialised test harness. With the current physics, most realistic kicks will eventually either hit ground, wall, or goal before 5 seconds.

So this is a weaker test unless the harness controls constants.

Best minimal hidden test suite

If I were building a benchmark harness, I would include these as must-pass:

1. Wall interpolation hit
2. Wall outside rectangle returns null
3. Goal inside rectangle returns GOAL
4. Goal outside width returns MISS
5. Goal above height returns MISS
6. Ground downward crossing returns HIT_GROUND
7. Ground upward/non-downward does not trigger
8. Earliest event wins
9. simulateKick is deterministic
10. simulateManyKicks returns sorted results
11. simulateManyKicks is deterministic over 100 runs
12. Empty kick list returns empty result list

