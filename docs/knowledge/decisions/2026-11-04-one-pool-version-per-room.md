tags: [rooms, pool, concurrency]
A room's pool copy has one header row whose pool_version is the single compare-and-set version for player edits, set order and reset.
Why: every pool write locks the room row (the row RoomLifecycle.advance locks), so a write never commits after the auction is live, and one version keeps stale-tab handling to one rule (R-8).
Cost: two edits to different players still conflict (409 stale_version); the client reloads and retries.
A room with no header row has no copy: reset expects version 0 and creates it, and RoomLifecycle refuses auction_live.
