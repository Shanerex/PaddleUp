tags: [auction, event-log, schema]
auction_events (PK room_id+seq, append-only by trigger) is the only source of truth; auctions is a boot index, auction_lots holds frozen cards.
Why: persist, then apply, broadcast, reply; boot replays the log through the same pure reducer (R-11, R-14).
Cost: events hold ids, not cards, so a large pool stays under the 256 KB JSON limit; purses are sorted by id since jsonb loses key order.
