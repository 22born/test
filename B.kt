invariants with exact pinned policies.
DST spring-forward nonexistent fixed time
Fixed dose at 02:30 in a zone where that local time does not exist.
Verify: dose is NeedsReview, scheduledAt = null, diagnostic mentions nonexistent DST local time.
DST fall-back ambiguous fixed time
Fixed dose at 01:30 in a zone where that local time occurs twice.
Verify: exactly one dose is emitted, it uses the earlier instant, and a diagnostic is emitted.
Interval dosing across DST
Every-8-hours medication anchored before DST transition.
Verify: all Scheduled interval doses are at least 8 elapsed hours apart, even if local clock times shift.
Travel changes fixed-time zone
Fixed 08:00 dose before and after a travel window changes the active zone.
Verify: each dose uses the active zone for that local date.
Travel must not compress interval doses
Every-12-hours medication crosses travel zones.
Verify: no two Scheduled doses are less than 12 elapsed hours apart.
Pause window suppresses but does not catch up
Every-6-hours medication has two candidate doses inside a pause window.
Verify: those doses are SkippedDueToPause; no extra replacement doses are added after the pause.
Pause overlaps taper transition
A taper changes dosage during a pause window.
Verify: paused doses are skipped, and the first scheduled dose after the pause uses the new taper dosage.
Overlapping taper phases
Two taper phases overlap with different dosages.
Verify: affected doses are NeedsReview and a deterministic diagnostic is emitted.
Taper gap
A date falls between two taper phases.
Verify: no dosage is invented; affected doses are NeedsReview with a missing/invalid taper diagnostic.
Fixed-time separation conflict
Two fixed-time medications are both scheduled at 08:00 with a 4-hour separation constraint.
Verify: they are not both Scheduled; conflict diagnostic is emitted.
Movable window resolves conflict
Medication A fixed at 08:00; Medication B is once per day in 08:00–12:00; separation is 2 hours.
Verify: B is scheduled at the earliest valid minute, 10:00.
Conflict chain
A conflicts with B, B conflicts with C, but A does not conflict with C.
Verify: final schedule has no hidden B/C violation after resolving A/B.
Impossible global separation set
Three medications each need a dose inside a narrow window, with pairwise separation too large to fit all.
Verify: impossible dose or doses are marked NeedsReview or SkippedDueToConflict; no invalid Scheduled pair remains.
Skipped dose near next dose
A pause skips one interval dose shortly before the next normal interval dose.
Verify: resolver does not add a catch-up dose between them.
Invalid interval rule
EveryHours(0, anchor) or negative hours.
Verify: no invalid schedule is generated; diagnostic is emitted.
Invalid times-per-day window
A window has start >= endExclusive.
Verify: affected doses are NeedsReview or omitted with diagnostic; no dose is placed outside a valid window.
Times-per-day deterministic placement
Two valid placements are equally good.
Verify: earliest valid minute is chosen; if still tied, medication id tie-break is used.
Output sorting
Multiple medications produce doses at same instant or null scheduledAt.
Verify: output order follows the contract: scheduled instant when present, then intended local date-time, then medication id.
Input-order determinism
Shuffle medications, constraints, taper phases, pause windows, and travel windows.
Verify: identical doses and diagnostics.
Dense stress case
Mix fixed doses, interval doses, times-per-day windows, taper phases, pauses, travel, DST, and separation constraints.
Verify invariants: no unsafe separation violations, no compressed interval doses, no catch-up doses, deterministic diagnostics, and all unresolved cases are marked.
