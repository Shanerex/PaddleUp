tags: [auction, timers, clock]
Timers are one 100 ms tick on the server Clock plus lazy closing: every intent first closes a lot whose deadline has passed.
Why: lot_closed is exact however late the tick runs, and tests turn the tick off (tick=0) and step a mutable Clock (R-7, R-8).
Cost: a lot can close up to 100 ms after its deadline in the broadcast; a deadline passed in downtime resumes with a fresh T_bid.
