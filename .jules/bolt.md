## 2024-05-16 - Fixed N+1 query in Note Search
**Learning:** Room DAOs can easily handle batch fetching using `IN (:ids)` lists, which drastically reduces database roundtrips. Inside `searchNotes` in `NoteRepository.kt`, mapping over each result and querying the database line-by-line led to massive N+1 slowdowns as the search hit list grew.
**Action:** Replaced O(N) database queries with O(1) batch queries using `getByIds` and mapped the results in memory using `associateBy`. Added an early return `if (recognitionHits.isEmpty()) return@map emptyList()` to avoid SQLite `IN ()` syntax errors. Next time, always check loops in repository layers for N+1 query patterns.

## 2024-06-03 - Avoid hypot() in fast touch loops
**Learning:** `kotlin.math.hypot` is unexpectedly expensive in Android touch loops running at 60Hz or 120Hz. `hypot()` converts its arguments to `Double`s, checks for overflow and underflow, and then converts the result back, which degrades performance and creates unnecessary allocations over thousands of events.
**Action:** Replace `hypot` calls in performance-critical areas with raw `sqrt(dx * dx + dy * dy)` on `Float`s, or entirely bypass `sqrt` by comparing the squared values (e.g., `if (vx*vx + vy*vy > V*V)`). Next time, proactively watch out for heavy math standard library functions in hot paths like gestures, rendering, and animations.
