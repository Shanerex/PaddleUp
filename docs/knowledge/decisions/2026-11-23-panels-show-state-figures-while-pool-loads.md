tags: [web, auction, loading-states, design]
Auction panels (Your needs, Squad sheet, Rivals) show every figure the live state holds (purse, Max bid, squad count, caps) at once; counts only the player list can tell show a muted pending bar, never 0.
A failed read keeps those figures and adds the shared load-error Note (error icon, title, body, small Retry), as drawn in docs/design/source/AuctionPanelStates.dc.html.
Reads back off on 5xx, 408, 429 and network errors; a permanent 4xx stops auto-retry and leaves the manual Retry.
Spec: 2026-11-22-auction-panels (CR-15, CR-17, CR-30).
