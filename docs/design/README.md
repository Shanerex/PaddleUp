# PaddleUp — Design

Source of truth for UI. Build agents: read this file first, then open `design-system.html` in a browser (self-contained, works offline).

## Files
- `design-system.html` — tokens, components, states (light/dark toggle, franchise colour picker).
- `auction-explorations.html` — the 3 auction directions (1a Spotlight, 1b War Room, 1c Paddle Row) at 390/820/1440, advisor sheet vs panel (2a/2b), and every auction edge state (3a–3j). All frames are live simulations.
- `app-screens.html` — every other screen at phone + desktop (tablet for key screens). Frames are clickable.
- `source/` — editable source (`*.dc.html`, `paddle.js` = theme tokens + fake auction engine). Serve the folder (`python3 -m http.server` in `source/`) and open a `.dc.html`; App Screens needs the server to load its frames. The v1 frames (App Screens turn 6, added 2026-11-03) exist only in `source/`; the top-level `.html` bundles predate them.
- `source/AdminImport.dc.html` — app-admin season data import (added 2026-11-04). Its frames are in App Screens turn 7 with the player pool screens.
- `assets/favicon.svg` — favicon (use for 16/32/48).
- `assets/app-icon.svg` — PWA icon 192/512, `purpose: "any"`.
- `assets/app-icon-maskable.svg` — PWA icon, `purpose: "maskable"` (mark inside 40% safe zone).

## Screen index
| Area | Screen | Source | PRD |
|---|---|---|---|
| Auth | Sign in (OAuth + magic link) | Home `signin` | FR-1 |
| Home | Home hub (rooms + what's happening), empty state | Home `hub`, `empty` | FR-2 |
| Home (v2, home-redesign) | Franchise wall: hero band (the room that needs you most) over a wall of tiles in each room's franchise colour; hero per state, single room, Past seasons strip, lone completed room, load error, loading; blank wall for no rooms (added 2026-11-10, approved) | Home `wall:many\|live\|scheduled\|scheduledNoTime\|retention\|setupAdmin\|setupAdminOnly\|setupSeat\|setupWait\|season\|one\|past\|lonePast\|error\|loading`, `empty` (App Screens 8a-8p) | FR-2 |
| Setup (v2) | Room settings: its own mode of the wizard (room-name rail, five sections, sport locked, Prev/Next with Next saving the section, Back to room), read-only when the auction is live, version conflict, and the Note a manager sees (added 2026-11-10, approved) | Home `settings:basics\|squad\|scoring\|ai\|invite\|locked\|conflict`; Room `settingsNote` (App Screens 8q-8x) | FR-10..13, FR-15 |
| Room (v2) | Scheduled room: "Open the lobby" for the Commissioner and for a manager, and the manager with no start time yet (added 2026-11-10, approved) | Room `setupScheduled`, `setupManager`, `setupManagerNoStart` (App Screens 8y-8aa) | FR-40 |
| Account (v2) | Profile (name, avatar, sign out) and sign-in without Apple (added 2026-11-10, approved) | Home `profile`, `signin` (App Screens 8ab, 8ac) | FR-1, FR-3 |
| Join | Join room via code, create franchise (name, monogram, colour) | Home `join` | FR-11 |
| Setup | 6-step wizard: Sport · Basics · Squad rules · Scoring · AI · Invite | Home `wizard:1–6` | FR-10–15, FR-70–72 |
| Pre-auction | Lobby (countdown, readiness, house rules) | Room `lobby` | FR-40 |
| Pre-auction (v1) | Lobby variants (added 2026-11-09, pending approval): past start time, offline (ready locked, countdown frozen), Commissioner without a franchise | Room `lobbyLate`, `lobbyOffline`, `lobbyAdmin` (App Screens 3b1-3b3) | FR-40, FR-111 |
| Pre-auction (v1) | Schedule the auction (added 2026-11-09, approved): Commissioner picks date and time in their own zone (room), time already gone, pool too small; Change time from the lobby (Commissioner only) | Room `sheetSchedule`, `sheetScheduleError`, `sheetScheduleRefused`, `lobbyReschedule` (App Screens 6o1-6o3, 3b4) | FR-40, NFR-9 |
| Auction (v1) | Spotlight states added 2026-11-09, pending approval: Commissioner without a franchise (watching, no bid button, cause stated), auction ended (no auction UI, link to room); no auction yet with Schedule auction for the Commissioner (added 2026-11-09, approved); entering an adjourned auction is 3e | Auction A scenes `observer` (3k), `closed` (3l), `noAuction` (3m) | FR-41, FR-47 |
| Pre-auction | Retention (slabs, RTM cards) | Room `retention` | FR-30–32 |
| Auction | Live auction, 3 directions; Team Table advisor; RTM; all states | Auction A/B/C | FR-41–52, FR-120–125 |
| Post-auction | Summary + validation flags | Room `summary` | FR-52 |
| Post-auction | Report Card + private advice-vs-action | Room `report` | FR-126–127 |
| Season | Overview, squads (all public), standings (+ offline), player leaderboard | Room `overview`, `squad`, `standings`, `offline`, `players` | FR-60, FR-90, FR-92, FR-111 |
| Season | Matches (+ empty), match detail, point breakdown | Room `matches`, `matchesEmpty`, `match`, `breakdown` | FR-81–82, FR-91 |
| Records | Audit log with filters | Room `audit` | FR-48 |
| Admin | Commissioner tools (unmapped players, minimums, manual stat entry, controls) | Room `commish` | FR-47, FR-81, FR-83 |
| Home (v1) | Hub with pre-season rooms only | Home `hubNow` | FR-2 |
| Join (v1) | Join by typed code; errors: invalid/expired/revoked, full, auction live, rate-limited | Home `joinCode`, `joinError:invalid\|full\|locked\|limited` | FR-11, NFR-7 |
| Room (v1) | Room link that isn't yours or doesn't exist | Home `roomMissing` | FR-12 |
| Pre-auction (v1) | Room in setup/retention/scheduled until the lobby ships: Commissioner below minimum, ready, admin-only, manager view | Room `setup`, `setupReady`, `setupAdmin`, `setupManager` | FR-10–13, FR-15 |
| Pre-auction (v1) | Player pool: Commissioner (newer season data notice), manager read-only, auction live (locked, write refused), no season data (auction blocked), season data that doesn't fit the room (prices vs purse or price steps, roles or tags vs pack; with or without a pool), stale tab | Room `pool`, `poolMember`, `poolLive`, `poolEmpty`, `poolEmptyUnfit`, `poolEmptyUnfitPack`, `poolUnfit`, `poolUnfitPack`, `poolStale` | FR-20–21 |
| Pre-auction (v1) | Pool sheets: edit player (field error), add player, confirm remove, confirm reset to latest | Room `poolEdit`, `poolAdd`, `poolRemove`, `poolReset` | FR-21 |
| Pre-auction (v1) | Set order: Commissioner (one set moved, unsaved), manager read-only | Room `sets`, `setsMember` | FR-22 |
| Admin (v1) | Season data import: file picked, some rows skipped (new version), every row skipped (no version), not an app admin | AdminImport `import`, `importReport`, `importFailed`, `importDenied` | FR-21 |
| Pre-auction (v1) | Sheets: member actions, remove, hand over, move on, edit franchise; copied toast; loading; refresh-failed and stale-tab banners | Room `sheetMember`, `sheetRemove`, `sheetHandover`, `sheetAdvance`, `editFranchise`, `copied`, `loading`, `loadError`, `staleAction` | FR-11–13 |

Open decision: pick one auction direction (1a/1b/1c) and one advisor presentation (2a/2b) before building FR-41+.

## Principles
- Two colour worlds: outside a room (auth, room-setup wizard, Room settings, profile) use `--brand`. Inside a room, the manager's franchise colour (`--team*`) drives the header, primary actions, tints and `--room-bg`. The home hub is the bridge: its chrome and app-level actions are `--brand`, but each room's hero and tile is painted in that room's franchise colour (inverse surface when you have no franchise there). See decisions/2026-11-10-home-franchise-wall.
- Rivals always render in their own franchise colour (monogram).
- Danger, warning and AI surfaces never take the team colour. AI (Team Table) lives on `--inverse`.
- Every live-changing number (bids, purse, timer, points) uses DM Mono with tabular numerals. Fixed-height containers, no layout shift (FR-112).
- Sport-agnostic: role labels, tags, stats, currency and units come from the Sport Pack. Never hard-code cricket strings in core UI (NFR-12).
- Bids are irrevocable (no undo grace).
- Copy tone: playful banter, facts first.

## Fonts & icons
- Plus Jakarta Sans 400/500/600/700/800 — all text.
- DM Mono 400/500 — numbers.
- Material Symbols Rounded — icons, 20/24px, FILL 0 default, FILL 1 for active.

## Tokens (CSS custom properties on the app root)

| Token | Light | Dark |
|---|---|---|
| --bg | oklch(0.975 0.006 75) | oklch(0.17 0.008 60) |
| --surface | oklch(0.995 0.003 75) | oklch(0.215 0.009 60) |
| --surface-2 | oklch(0.955 0.007 75) | oklch(0.26 0.01 60) |
| --line | oklch(0.905 0.008 75) | oklch(0.32 0.01 60) |
| --ink | oklch(0.23 0.012 60) | oklch(0.96 0.006 75) |
| --ink-2 | oklch(0.46 0.012 60) | oklch(0.76 0.01 70) |
| --ink-3 | oklch(0.62 0.01 60) | oklch(0.55 0.01 70) |
| --brand | oklch(0.62 0.19 38) | oklch(0.66 0.18 38) |
| --brand-tint | oklch(0.95 0.035 45) | oklch(0.3 0.06 40) |
| --brand-ink | oklch(0.5 0.17 38) | oklch(0.8 0.12 40) |
| --ok / --ok-tint | oklch(0.56 0.14 155) / oklch(0.95 0.04 155) | oklch(0.72 0.14 155) / oklch(0.28 0.05 155) |
| --warn / --warn-ink / --warn-tint | oklch(0.72 0.15 75) / oklch(0.5 0.12 70) / oklch(0.96 0.05 85) | oklch(0.78 0.14 75) / oklch(0.85 0.12 80) / oklch(0.3 0.05 80) |
| --bad / --bad-tint | oklch(0.57 0.2 25) / oklch(0.95 0.035 25) | oklch(0.68 0.19 25) / oklch(0.29 0.06 25) |
| --info / --info-tint | oklch(0.55 0.13 245) / oklch(0.95 0.03 245) | oklch(0.74 0.11 245) / oklch(0.28 0.05 245) |
| --inverse / --inverse-2 | oklch(0.24 0.012 60) / oklch(0.31 0.012 60) | oklch(0.28 0.012 60) / oklch(0.35 0.012 60) |
| --on-inverse / --on-inverse-2 | oklch(0.97 0.005 75) / oklch(0.78 0.01 70) | same |
| --coach / --analyst | oklch(0.78 0.14 70) / oklch(0.76 0.11 230) | same |

### Franchise tokens (h = franchise hue)
| Token | Light | Dark |
|---|---|---|
| --team | oklch(0.56 0.15 h) | oklch(0.6 0.15 h) |
| --team-strong | oklch(0.48 0.14 h) | oklch(0.66 0.15 h) |
| --team-tint | oklch(0.955 0.03 h) | oklch(0.28 0.05 h) |
| --team-tint-2 | oklch(0.9 0.06 h) | oklch(0.36 0.07 h) |
| --team-ink | oklch(0.42 0.12 h) | oklch(0.82 0.1 h) |
| --on-team | oklch(0.99 0.005 h) | same |
| --room-bg | oklch(0.972 0.014 h) | oklch(0.19 0.02 h) |

Franchise hues (unique per room): Crimson 15, Tangerine 45, Bronze 75, Olive 105, Fern 135, Emerald 165, Teal 195, Sky 225, Cobalt 255, Iris 285, Orchid 315, Rose 345.

### Role colour slots (pack maps roles → slots 1–4)
| Slot | Light bg / ink | Dark bg / ink |
|---|---|---|
| 1 (hue 70) | oklch(0.93 0.04 70) / oklch(0.4 0.09 70) | oklch(0.32 0.06 70) / oklch(0.9 0.07 70) |
| 2 (hue 250) | oklch(0.93 0.04 250) / oklch(0.4 0.1 250) | oklch(0.32 0.06 250) / oklch(0.88 0.07 250) |
| 3 (hue 150) | oklch(0.93 0.04 150) / oklch(0.4 0.09 150) | oklch(0.32 0.06 150) / oklch(0.88 0.07 150) |
| 4 (hue 320) | oklch(0.93 0.04 320) / oklch(0.42 0.1 320) | oklch(0.32 0.06 320) / oklch(0.88 0.07 320) |

## Scale
- Spacing: 4, 8, 12, 16, 24, 32, 48.
- Radius: chip 8, button 14, card 20–24, sheet 28 (top), monogram 30%, pill 999.
- Buttons: L 52, M 44, S 36. Min hit target 44. Phone bid button 72.
- Type: display 44/48 800 · h1 28/34 800 · h2 22/28 800 · h3 17/24 700 · body 15/22 500 · small 13/18 500 · label 11/14 700 uppercase +6% · num-xl 56 DM Mono 500.
- Motion: press 80ms scale .97 · bid arrives 180ms number roll · sheet/toast 240ms ease-out · hammer 600ms stamp · reduced motion = fades only.
- Breakpoints designed: phone 390, tablet 820, desktop 1440.
