tags: [packs, rooms, validation, pool]
One pure validator, packs.service.PlayerRowRules, checks every player row for imports and for room edits (role and tag keys, length caps, sanitised text, price).
Price: whole number above zero, at most the purse, on the increment ladder. An import must satisfy every preset of the pack; a room edit uses that room's own purse and ladder.
Sets: add and edit pick one of the room's existing sets. A set emptied by edits stays in the order with 0 players, so each player is in exactly one set.
Copied rows are not re-priced when a room's ladder differs from the pack's; only added or edited rows are checked against the room config.
