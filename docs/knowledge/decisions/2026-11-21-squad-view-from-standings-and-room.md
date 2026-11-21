tags: [squads, web, auction]
The squad view is a pure web function, squadView(state, pool, config), over the auction state's standings; there is no squads endpoint and no schema change.
Before go-live the engine's standings are empty, so franchises come from the room's members and the full purse from the room config.
The auction screen's squad sheet reuses the same SquadPanel as /rooms/{id}/squads, so one component follows the live feed and the fetch fallback.
