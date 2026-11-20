tags: [auction, reducer, event-log]
A tick guard that reads a stored flag (startRefusal) stays shut after a later event (start_now) supersedes the reason for it, so a retry added for that event never fires. When adding a retry or resend path, replay the earlier refusal then the new event in a reducer test and check the guard state, not only the happy path.
A control that applies "between lots" (pause in a gap) must not copy the closed lot or its clock into its event: derive from the phase, and carry an explicit flag (gap: true).
Review loops found five of six CR3 bugs in code the previous loop had just touched; re-read neighbouring state transitions when a fix adds a new one.
