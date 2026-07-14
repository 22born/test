Requirements

1. Generate dose reminders only within the requested date range "[rangeStart, rangeEndExclusive)".

2. Respect medication start dates, end dates, and paused windows.

3. Fixed local-time doses are interpreted in the active time zone for that local date.

4. Every-"N"-hours doses preserve elapsed-time spacing from their anchor instant.

5. Time-zone changes affect fixed local-time doses, but must not compress interval doses below their required elapsed interval.

6. On DST spring-forward, if a requested local time does not exist, emit a "NeedsReview" dose with "scheduledAt = null" and a diagnostic.

7. On DST fall-back, if a requested local time occurs twice, schedule only the earlier instant and emit an ambiguity diagnostic.

8. Pause windows suppress affected doses as "SkippedDueToPause".

9. Skipped, paused, invalid, or missed doses must not be doubled, backfilled, or caught up later.

10. Taper phases must be non-overlapping and contiguous across the medication’s active date range.

11. If taper phases overlap or leave a gap, affected doses must be "NeedsReview" with diagnostics.

12. If taper phases are present, taper dosage overrides the base dosage for covered dates.

13. Enforce minimum separation constraints between medications.

14. No two "Scheduled" doses may violate a separation constraint.

15. Fixed-time and EveryHours doses must not be moved to resolve conflicts.

16. TimesPerDay doses may be moved only within their allowed windows on the same local date, using one-minute granularity.

17. If multiple valid TimesPerDay placements exist, choose the earliest valid minute, then medication id order.

18. If constraints cannot be safely satisfied, mark affected doses as "NeedsReview" or "SkippedDueToConflict".

19. Invalid timing rules must produce diagnostics and must not produce "Scheduled" doses.

20. Travel windows must be non-overlapping. Overlaps produce diagnostics and affected dates are "NeedsReview".

21. Return diagnostics for impossible rules, ambiguous local times, DST conflicts, invalid taper phases, invalid travel windows, and unsatisfied constraints.

22. Output reminders must be sorted by "scheduledAt" when present, then intended local date-time, then medication id. Doses with "scheduledAt = null" sort after doses with real instants.

23. Produce deterministic output and diagnostics for the same logical input, regardless of input ordering.
