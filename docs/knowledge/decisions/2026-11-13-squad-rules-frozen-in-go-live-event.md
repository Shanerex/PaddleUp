tags: [auction, eligibility, event-log]
SquadGuard reads squad min/max, role minimums and restricted-category caps from the go-live event, not from the live room row, so a room edit or restart cannot change a decision (AC-4). An old log without the field means no squad guard.
The reserve (cheapest base prices still needed to fill role and squad minimums) is a pure function in `auction/util/SquadReserve`. With category caps it is a greedy estimate and may refuse a bid a better squad could afford; an unfillable pool refuses as `reserve`.
`bidLock` is computed server-side per viewer. A rival's bid patch cannot update it, so the client rereads state after a refused bid.
Rejected: reading rules from the room at bid time (breaks replay) and an exact solver for caps (cost not worth it for v1).
