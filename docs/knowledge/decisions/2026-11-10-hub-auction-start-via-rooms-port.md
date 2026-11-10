tags: [server, rooms, auction, packs]
The hub list carries `auctionStartsAt` and `serverNow`, read through `rooms.service.AuctionStartSource`, which `auction` implements; `rooms` never imports `auction`, and one request keeps the hero right on first paint.
Rejected: a second `/api/auctions/mine` fetch (two failure modes) and `rooms` reading the `auctions` table (breaks table ownership).
Packs gain optional `roles[].short`, `statSchema[].group`, `displayUnit.short` and `.minor`, validated on load and on config save; a missing field falls back to the label.
