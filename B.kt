do not require exact diagnostic keywords in tests. Require diagnostic meaning/category instead.
Add/adjust requirement:
Diagnostics must be deterministic and must clearly identify the affected medication/date-time and the violated rule category, such as DST nonexistent time, DST ambiguous time, invalid taper, invalid timing rule, pause, separation conflict, or invalid travel window. Exact wording is not required.
Then hidden tests should check for category/status, not strings like "DST_CONFLICT" or "OVERLAP".
