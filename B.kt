Medication Schedule Resolver

Context

A medication reminder app needs to turn medication instructions into concrete reminder times. Users may take multiple medications with constraints between them, pause medications, travel across time zones, or follow tapering schedules.

Medication instructions can conflict. When a safe schedule cannot be produced, the resolver must flag the affected doses for review instead of silently choosing unsafe times.

Task

Implement a resolver that accepts medication instructions, scheduling constraints, a date range, and a time zone, then returns a deterministic schedule of dose reminders plus diagnostics.

The public API and data representation are up to you, but the implementation must support:

- fixed daily times
- “N times per day” schedules
- “every N hours” schedules
- tapering dosage phases
- medication start/end dates
- paused windows
- minimum separation constraints between medications
- daylight-saving-time and time-zone edge cases

Requirements

1. Generate dose reminders only within the requested date range.
2. Respect medication start dates, end dates, and paused windows.
3. Enforce minimum separation constraints between medications.
4. Support taper phases without overlapping phases or unintended gaps.
5. Handle daylight-saving transitions deterministically.
6. Handle time-zone changes without dangerously compressing doses.
7. Do not automatically double or “catch up” missed/skipped doses.
8. If constraints cannot be safely satisfied, mark affected doses as needing review.
9. Return diagnostics for impossible rules, ambiguous local times, DST conflicts, invalid taper phases, and unsatisfied constraints.
10. Produce deterministic output for the same input.

Output Format

Return:

1. The implementation.
2. A brief explanation of the scheduling and conflict-resolution strategy.
3. Crucial tests or pseudocode tests.





Medication Schedule Resolver.
DST spring-forward nonexistent dose time
Schedule a fixed dose at 02:30 on a day where local time jumps from 02:00 to 03:00. Verify the resolver does not silently create an impossible local time and marks or adjusts it according to a deterministic policy with a diagnostic.
DST fall-back ambiguous dose time
Schedule a fixed dose at 01:30 on a day where 01:30 occurs twice. Verify the resolver does not duplicate the dose unsafely and reports which occurrence policy it used.
Every-8-hours schedule across DST transition
Generate an every-8-hours medication over a DST boundary. Verify doses are not dangerously compressed or stretched in local time without diagnostics.
Time-zone travel compresses doses
A user travels from one time zone to another on the same day. Verify the schedule avoids creating two doses too close together due to local clock changes.
Time-zone travel expands interval beyond safe window
Travel causes the next scheduled local dose to be much later than the medication’s intended interval. Verify the resolver flags the affected dose for review rather than pretending the schedule is normal.
Two fixed-time medications violate separation constraint
Medication A and B are both scheduled at 08:00, but require four hours of separation. Verify the resolver does not silently move one into an unsafe or arbitrary time and marks the conflict for review if no valid slot exists.
Conflict chain across three medications
A conflicts with B, B conflicts with C, and all have preferred windows. Verify moving one dose to satisfy one constraint does not create a hidden violation with another medication.
N-times-per-day with narrow windows impossible
A medication requires three doses per day, but the preferred windows and separation rules only allow two safe slots. Verify the unscheduled dose is marked NeedsReview.
Every-N-hours conflicts with sleep/paused window
An every-6-hours medication overlaps a long paused window, such as surgery or sleep restriction. Verify skipped doses are not doubled afterward and the resumed schedule remains safe.
Paused window cuts through a taper transition
A taper changes dosage on a date that falls inside a pause window. Verify doses inside the pause are skipped, and the dosage after the pause follows the correct taper phase, not the old phase.
Overlapping taper phases
Two taper phases overlap with different dosages. Verify the resolver reports an invalid taper and marks affected doses for review.
Taper phase gap
A taper schedule has an unintended gap between phases. Verify the resolver does not invent a dosage and flags the gap.
Remote/edited instruction changes mid-range
The medication rule changes halfway through the requested range. Verify earlier dates use the old rule and later dates use the new rule without duplicate or missing boundary-day doses.
Start/end date boundary with time zone shift
A medication starts or ends on a date that differs depending on the user’s travel time zone. Verify doses are included/excluded according to the intended local-date policy.
Skipped dose near next scheduled dose
A dose is skipped or paused shortly before the next normal dose. Verify the resolver does not schedule a catch-up dose too close to the next one.
Impossible global constraint set
Multiple medications have individually valid schedules, but combined separation constraints make the full schedule impossible. Verify the resolver identifies the affected set instead of making local greedy choices that hide the impossibility.
Determinism under equivalent input ordering
Provide the same medications and constraints in different list orders. Verify the final schedule and diagnostics are identical.
Ambiguous conflict resolution tie
Two possible dose moves are equally valid by timing. Verify the resolver uses a deterministic tie-breaker and does not vary across runs.
Long-range recurring schedule across month/year boundaries
Generate a schedule spanning month-end, year-end, leap day, and DST. Verify recurrence math remains consistent and no off-by-one dates appear.
Dense polypharmacy stress case
Many medications with mixed fixed times, every-N-hours rules, taper phases, pauses, travel, and separation constraints. Verify no unsafe dose compression, no duplicate reminders, no hidden separation violations, and all unsatisfied constraints are diagnosed.
