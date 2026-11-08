tags: [auction, stomp, auth]
The socket accepts a session cookie on a same-origin handshake, or a single-use 30 s ticket from POST /ticket on CONNECT; both go through StompAuth.
Why: Vercel does not proxy WebSocket upgrades, so a browser may have to connect straight to the server origin (R-15).
Cost: /api/ws/** handshake is open; CONNECT without a session or valid ticket is refused, SEND is always refused, SUBSCRIBE checks membership.
