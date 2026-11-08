tags: [auction, concurrency, runner]
Each unended auction has one in-memory runner; every intent, timer step and schedule change is a task on its serial queue, built on a shared pool.
Why: arrival order is queue order, so the first of two equal bids wins with no locks (R-9, FR-45).
Cost: an auction must run on one server instance; a second writer fails on the (room_id, seq) key and cannot fork the log.
Rejected: SELECT FOR UPDATE per bid (two sources of truth) and a lock per auction with cancellable timers (races).
