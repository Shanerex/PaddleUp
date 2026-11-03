---
status: final
---
# Review: 2026-11-02-03-cricket-t20-pack

## QA
make test (server verify + web 40/40) and make lint (spotless, eslint, check-agnostic) green. CricketPackTest 27/27, PackOptionalFieldsTest 2/2, PackStructureValidationTest 12/12.
- AC-1..AC-6 pass: pack registers manual-entry only; roles, tags, overseas cap, standard and short-pool presets, increments, retention all asserted.
- AC-7 pass (note): STAT_IDS list is test-authored; PRD 7.3 "dismissal" has no stat id, covered by duck flag plus wickets eventTypes. Non-blocking.
- AC-8..AC-12 pass: batting, strike-rate bands, bowling, economy bands, fielding and other rules match JSON, boundary values tested.
- AC-13..AC-15 pass: six primitives with unique ids, AI context in 3 parts with glossary per stat and no player names, no adapterKey.
- AC-16, AC-17 pass: startup failure on bad minimums names file, pack id and numbers; cricket and stub football pack register together.
- AC-18 pass: server/src/main diff = PresetDto, StatDto, PackValidator only; no migrations or web changes.
- AC-19 pass (note): unchanged section values rest on existing SecondPackTest and PackServiceTest, still green. Non-blocking.
- T-1..T-16: every ticked task has a matching test or artifact.

## Security
- minor: PackValidator.java:211 slab without `tag` throws NullPointerException at boot instead of a `retention-unknown-tag` violation. Operator-supplied file, no remote impact.
- minor: PresetDto.java:77, :85 `Map.copyOf` / `List.copyOf` throw on null values; should surface as `invalid-section` via Jackson. Same trust boundary.
- No findings: injection, deserialization, paths, secrets, data exposure, authn/authz, dependencies.

## Verdict
pass. No blocking items. Two minor security notes (non-blocking) and two non-blocking QA notes (AC-7, AC-19).
