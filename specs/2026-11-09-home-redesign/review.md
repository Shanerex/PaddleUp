---
status: final
---
# Review: 2026-11-09-home-redesign

## QA
Re-verified after fix loop 1. web vitest 36 files / 386 tests and `tsc --noEmit` green; full `make test` and `make lint` green per dev. Playwright e2e not re-run by QA (pairs dated Nov 10 01:50).
- AC-1..AC-12 pass: unchanged from the first review; regression-checked through test results.
- AC-13 pass: web/test-results/home holds 74 pairs (37 screens x 390/1440). QA viewed 9 (hubMany, settings basics/squad/invite, lobby, lobbyAdmin, signIn, profile, join); layout, spacing, type, colour and copy match the mocks; no Apple button.
- AC-13 accepted deviations (README.md): data differences, en-US vs en-GB dates, real Google mark, two 2px rounding gaps, Past strip below the 390 fold.
- AC-13 unshot 8z, 8aa, 7m, 7n: not blocking; 8y, 8x, 7l and 7o cover the same components.
- AC-14 pass: R-12 gaps logged in specs 08, 09, 10, 14, 23, 24, 26; the lobby short-pool gap is in spec 08 and this state.md.
- Non-blocking: profile avatars are SVG shapes where the mock draws monograms (8ab). Redraw one or the other later.
- Non-blocking: lobby pairs lack the ROLE MINIMUMS and TIMER tiles because the e2e fixture has no minimums or timer. Add both to the fixture on the next shoot.
- Non-blocking: the stale-conflict UI shows mapped copy, not a server message (the server sends only the code).
- check-agnostic allowlist "You run this one,": acceptable, narrow match.

## Security
Pass, no findings. Re-reviewed the full diff against main including the fix-loop changes: bound-parameter start-time query limited to the caller's rooms; pack display fields length-capped and cleaned on load and on save; no raw HTML or new browser storage in the web screens; links from server room IDs; `?section=` checked against a fixed list; e2e spec has no secrets or test-only endpoints and takes invite codes from the real API; allowlist adds one exact phrase; no dependency changes.

## Verdict
pass. No blocking IDs.
