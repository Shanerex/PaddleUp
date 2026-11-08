tags: [auction, idempotency, event-log]
Rejected intents are stored in an append-only `auction_rejections` table (V9), keyed by room, actor and intent id, and a resend returns the stored answer.
Why: R-10 says a repeat returns the first outcome. An in-memory cache loses that on restart or eviction, and the repeat can then be accepted (a first bid of 25 is rejected at 0, but valid once 20 is bid). The earlier claim that a re-decided intent could only be rejected again was wrong, and bids are irrevocable.
Cost: one insert per rejection and one extra lookup per intent. A rejection stays out of the event log because it changes no state and is never broadcast ("a rejection writes no event" holds), so the log keeps replaying to the same state.
