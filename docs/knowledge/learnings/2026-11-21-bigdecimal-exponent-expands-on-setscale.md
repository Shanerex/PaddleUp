tags: [security, scoring, validation, bigdecimal]
A tiny JSON number such as `1e999999999` passes Jackson's length limit and becomes a BigDecimal; `setScale(0)` (inside `StatValues.normalise`) then expands it to about a billion digits while the room lock is held.
Check magnitude from `precision() - scale()` before any `setScale` or `toBigInteger`, and cap request lists (`fields`) before looping.
Spec 12 verify found it as SEC-1; the new tests were checked red by switching the pre-check off. Related: [[2026-11-04-bound-json-before-parse-and-after-store]].
