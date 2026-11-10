tags: [web, design, ui, home, rooms]
Home is a Franchise wall: a hero band for the room that needs the viewer most, a wall of tiles in each room's own franchise colour (`teamStyle`), and a Past seasons strip. Amends "two colour worlds": brand stays for app-level actions (Create a room, Join with code), a room is always its own colour.
Hero priority is the pure `web/src/app/hub/hero.ts`: auction_live, auction_scheduled (soonest start, undated last), retention, setup needing the viewer, season_live, setup waiting; ties go to latest `updatedAt`; completed is never the hero.
Why: white cards gave rooms no identity and clashed with the team-coloured room and auction screens.
Room settings is a mode of `/rooms/[roomId]/setup` (`?section=`; `?step=` is create). Next saves the section (config plus `version`), the last says Done; the server stays the authority.
Residual risk: the hub refetches on every focus; fine at friend-group scale.
