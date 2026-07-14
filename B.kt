18. Fixed-time and EveryHours doses must not be moved to resolve conflicts.

19. TimesPerDay doses may be moved only within their allowed local-time windows on the same local date, using one-minute granularity.

20. If a TimesPerDay dose has multiple valid placements, choose the earliest valid minute.

21. If an invalid timing rule is encountered, emit a diagnostic and do not emit a Scheduled dose for that invalid rule.

22. If taper phases are provided, the taper dosage replaces the medication’s base dosage for dates covered by the phase.

23. Doses with scheduledAt = null sort after doses with scheduledAt present, then by intendedLocalDateTime, then medicationId.

24. Travel windows must be non-overlapping. Overlapping travel windows produce diagnostics and affected dates are NeedsReview.
