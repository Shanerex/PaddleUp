# Screens checked (T-21, AC-13)

Pairs: `web/test-results/home/` (git-ignored; made by `npx playwright test e2e/home-redesign.spec.ts` against `make dev`, mocks served with `python3 -m http.server 8099` in docs/design/source). Per screen and width: `<390|1440>-<name>.png` (ours), `mock-<w>-<name>.png` (the design frame) and `pair-<w>-<name>.png` (ours left, mock right). Playwright clears that folder at the start of every run; run the whole spec to rebuild all pairs. Each pair was viewed and every visible deviation fixed, then re-shot.

Checked at 390 and 1440 (mock frame in brackets):
- Hub: hubMany (8a), hubScheduled (8c), hubLive (8b), hubNoStart (8d), hubRetention (8e), hubSetupFranchise (8f), hubOne (8g), hubSetupNoFranchise (8h), hubSetupManager (8i), hubSeasonLive (8j), hubSingle (8k), hubPast (8l), hubLoneCompleted (8m), hubEmpty (8n), hubError (8o), hubLoading (8p)
- Room settings: settingsBasics (8q), settingsSquad (8r), settingsScoring (8s), settingsAi (8t), settingsInvite (8u), settingsLocked (8v), settingsConflict (8w), settingsNote (8x)
- Room: setupScheduled (8y)
- Outside-room: profile (8ab), signIn (8ac), join (8ad), joinCode (8ae), joinFull (8af), roomMissing (8ag)
- Lobby: lobby (3a), lobbyLate (3b1), lobbyOffline (3b2), lobbyAdmin (3b3)
- Admin import: importFilePicked (7l), importDenied (7o)
- Auction header height: asserted against its mock in the same spec (AC-12).

Not shot as a pair: 8z and 8aa (manager variants of the scheduled room; the Note in 8x and the room view tests cover the copy), 7m and 7n (import report and failure; unchanged layout, covered by import-view tests).

Fixed in this pass: hub error Note copy and layout (8o), wizard label-to-control gap 6 vs 8 (8q-8t), "missing room" card centring, lifeline order in House rules (Fit, Ceiling, Scan), empty "Role minimums" tile reads "None", lobby header chip ("AUCTION SAT 8 PM") and the Commissioner sub-line.

Accepted deviations:
- Data, not layout: the mock shows Monsoon League, Cricket T20 and its rosters; the pairs show seeded stub-football rooms, so names, counts, pack words (Foreign players vs Overseas) and roster rows differ. The Versions list in 7l is empty because the pack has no imports.
- Dates follow the viewer's locale (en-US in the shots: "Tue, Nov 17, 1:41 AM"), the mock's static text is en-GB ("Sun 10 Nov, 8:14 PM").
- Profile avatars (8ab): the presets are the accounts module's bundled SVG shapes, the mock draws them as coloured monograms. The Google picture appears only for people who have one.
- Sign-in (8ac): the Continue with Google button carries the real Google mark; the mock draws a grey placeholder glyph.
- 2 px: the AI advisor card in 8t and the sign-in hero paragraph run 2 px taller than the mock (line-height rounding); nothing shifts.
- Lobby (3a, 3b1-3b3): the mock's Room rail and phone tab bar (Auction, Squad, Standings, Matches, Report card, Audit log, Retention) belong to routes other specs build; the "120-player short pool" line (no pool size in the lobby props), the OVERSEAS and LIFELINES tiles and Start now are logged as deferred in the state.md of specs 08, 09 and 26.
- The Past seasons strip (8l) sits below the 844 px viewport at 390; the 1440 pair shows it.
