tags: [auction, websocket, heartbeat, presence]
STOMP heartbeats are 10 s each way (server `WebSocketConfig.HEARTBEAT_MS` and web `HEARTBEAT_MS` in `live.ts`, kept equal), not 1 s.
Why: at 1 s a background-tab throttle or a 2-3 s mobile stall closed the session, flipped presence to "NOT HERE" and locked bidding in the last seconds of a lot (review of auction-screen).
Cost: a silent drop (no offline event, e.g. a dead path) is noticed in about 10-20 s, not within AC-13's 3 s; a clean network loss still locks at once via the browser `offline` event.
