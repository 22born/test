12. If taper phases are present, the taper schedule is authoritative for the medication’s active date range. Dates covered by a valid taper phase use that phase’s dosage. Dates not covered by a valid taper phase must produce "NeedsReview" doses with "dosage = null" and a diagnostic. The base dosage must not be used on taper-gap dates.

20. Return diagnostics for impossible rules, ambiguous local times, DST conflicts, invalid taper phases, invalid timing rules, and unsatisfied constraints.

23. Diagnostics must be deterministic and must clearly identify the affected medication/date-time and the violated rule category, such as DST nonexistent time, DST ambiguous time, invalid taper, invalid timing rule, pause, or separation conflict.


requirements do not define overlapping travel-window behavior anymore, do not test it.
