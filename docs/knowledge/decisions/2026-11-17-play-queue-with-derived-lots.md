tags: [auction, lots, event-log]
Play order is a queue of lot numbers in `AuctionState`. A re-opened lot and each accelerated-round lot get the next lot number (max + 1), built in the reducer from the original lot's card at base price and put in the queue; the log only names the original lot and the new number. A lot number in the log therefore never changes meaning, and `auction_lots` gets no new rows.
`SquadReserve` takes the set of upcoming lot numbers instead of "lots after this one", because a re-run lot is numbered after lots that play later than it.
After the last lot's gap the engine goes to main_done (unsold lots left) or ended; the Commissioner opens one accelerated round or ends the auction. End round plays the picked lots most-picked first, ties by original lot number; with no picks the auction ends.
Rejected: inserting re-run lots into `lots` and renumbering (it changes the meaning of numbers in older events, rejections and the audit log).
