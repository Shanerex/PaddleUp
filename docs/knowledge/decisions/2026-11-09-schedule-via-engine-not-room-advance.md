tags: [auction, rooms, schedule, ui]
The Commissioner schedules through PUT /api/rooms/{id}/auction/schedule only; the room view no longer calls the rooms advance to auction_scheduled.
The engine moves the room itself (AuctionRoomPort.markScheduled, idempotent), so a room never sits in auction_scheduled without a start time.
A room already there with no start (older data) offers the same schedule sheet to the Commissioner on the room and on the No auction yet card.
Reschedule is the same call from the lobby (Change time); ready taps survive it.
