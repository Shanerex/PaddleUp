tags: [e2e, playwright, websocket, dev]
Running the e2e stack on non-default ports (server 8082, web 3002) needs NEXT_PUBLIC_WS_URL=ws://localhost:8082/api/ws for the web and PADDLEUP_AUTH_WEB_BASE_URL=http://localhost:3002 for the server.
Without them the lobby sits on "Reconnecting…": the socket defaults to :8080 and the handshake origin allowlist is the web base URL.
