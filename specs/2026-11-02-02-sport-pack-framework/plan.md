---
status: final
---
# Plan: 2026-11-02-02-sport-pack-framework

## Approach
Packs are read-only files, so the packs module has no table and no controller: a startup bean reads every `*.json` in `paddleup.packs.dir` (R-3), parses each into immutable `packs.dto` records (R-1, R-2, R-7), validates them (R-5, R-6, R-8), and builds an immutable in-memory registry served by `PackService` (R-4).
Parsing works on the Jackson tree one section and one rule at a time, so a broken section or unknown rule type becomes a violation, not an abort. Validation then runs on everything that parsed, and the loader throws one `PackLoadException` holding every violation across all files (R-9, AC-11).
Data adapters are Spring beans implementing `packs.service.DataAdapter`, matched by their `key()` (R-8, AC-10). The stat sheet dto and its validator are public so manual entry (FR-81) can reuse them (AC-13).
All ids (role, tag, stat, rule, preset, event type) are opaque `String`s and every label comes from the pack (R-10). Class names use `Pack*`, never a specific sport term.
Before any packs code, `scripts/check-agnostic.sh` is narrowed to ban specific sport, role, stat and currency names rather than the generic concept words, so pack fields such as `roles` and `currencyCode` pass AC-14 honestly.

## Options considered
- A (chosen): Jackson tree, then typed dto records, then a hand-written validator that collects all violations. No new dependency, typed rules for scoring, and one error format.
- B: a JSON Schema file plus a validator library (networknt) for structure, with Java for the FR-143 checks. Pack authors get editor hints, but it adds a dependency, gives two error formats, and the schema duplicates the records.
- C: seed packs into Postgres through Flyway or at startup. Packs become queryable, but the file stops being the only source of truth and AC-12 then needs a stale-copy guard.

## Decisions (pass 2)
- Q1 event-type-bonus: per-player typed event counts (a). The PRD §7.4 cricket default awards a bonus per LBW/bowled dismissal, and the §7 stat list has wickets with a dismissal type. A stat declares `eventTypes`, and the rule is `{stat, eventType, points}`.
- Q2 types: parse straight into immutable `packs.dto` records (a); `packs.model` holds only loader internals.
- Q3 adapter key: `DataAdapter.key()` (a); duplicate keys across beans fail startup.
- Lint clash: the human chose to narrow `check-agnostic.sh` (drop the eight generic words) instead of renaming fields.

## Design
Lint (first change):
- scripts/check-agnostic.sh: remove `sport|sports|role|roles|stat|stats|currency|currencies` from `WORDS` (line 7). Keep every specific term (cricket, batter, wicket, inr, ...) and the ₹ symbol. Change the header comment to say it bans specific sport, role, stat and currency names, not the generic concepts.
- scripts/test-check-agnostic.sh (new, run by `make lint`): runs the check against a temp tree. A file containing `wicket` must exit non-zero, and a file containing only `roles`/`currencyCode`/`stat` must exit zero. If the script cannot take a target dir yet, add an optional root argument (or env var) that defaults to the current `server/src/main web/src`.

Package `com.paddleup.packs` (server/src/main/java/com/paddleup/packs/):
- config/PacksProperties: `@ConfigurationProperties("paddleup.packs")` record with `dir`, default `../packs` (the repo `packs/` seen from `server/`). application.yml adds `paddleup.packs.dir: ${PADDLEUP_PACKS_DIR:../packs}`.
- config/PacksConfig: enables the properties and defines the `PackRegistry` bean as `PackLoader.load()`. Any exception here aborts startup (R-9).
- repository/PackFileRepository: lists the regular `*.json` files directly in the dir (not recursive, sorted by name) and reads each into a `JsonNode`. An absent or empty dir returns an empty list and logs one WARN with the resolved absolute path (R-3). Unreadable JSON becomes a violation for that file.
- dto (public contract, all records, immutable `List.copyOf`/`Map.copyOf`):
  - Pack header and sections: `PackDto(schemaVersion, id, version, roles, tags, restrictedCategories, presets, statSchema, defaultRuleSet, appearanceRule, Optional adapterKey, terminology, aiDomainContext)`, with `boolean manualEntryOnly()` (AC-10). Also `PackSummaryDto(id, version, manualEntryOnly)`.
  - Building blocks: `RoleDto(id,label)`, `TagDto(id,label)`, `RestrictedCategoryDto(id,label,tag,max)`, `StatDto(id,label,kind,eventTypes List<String>, empty when none)`, `AppearanceRuleDto(stat,atLeast)`, terminology `Map<String,String>`, aiDomainContext `String`.
  - `PresetDto(id,label,purse,currencyCode,displayUnit{label,divisor},incrementLadder[{from,step}],squadMin,squadMax,roleMinimums Map<roleId,int>,retention{maxRetentions,rtmCards})`. Amounts are `long` in the smallest unit (R-2).
  - Rules: `RuleDto` sealed interface with `id()` and a Jackson `type` discriminator, one record per R-7 primitive. Points are `BigDecimal`.
    - `per-unit`: `{stat, points}`
    - `threshold-bonus`: `{stat, tiers[{atLeast, points}]}`
    - `banded-rate`: `{numerator, denominator, scale, minVolume{stat, atLeast}, bands[{from, to, points}]}`
    - `role-conditional`: `{roles[], rule{nested rule}}`
    - `event-type-bonus`: `{stat, eventType, points}`, awarded per occurrence of that event type on that stat
    - `appearance-bonus`: `{points}`
    - `RuleSetDto(rules)`.
  - Adapter types: `FixtureDto(externalMatchId, startsAt Instant, participants, status)`, `StatSheetDto(externalMatchId, lines)`, `StatLineDto(playerId, stats Map<statId,BigDecimal>, events Map<statId,Map<eventType,Integer>>)`.
- service/PackService (public): `List<PackSummaryDto> list()`, `PackDto get(String packId)` (throws `PackNotFoundException`, AC-7), `Optional<DataAdapter> adapterFor(String packId)`, and `List<String> validateStatSheet(String packId, StatSheetDto)` (AC-13, reused by FR-81). The stat sheet check verifies that every stat id is in the schema and every event type is declared on its stat.
- service/DataAdapter (public interface, R-8): `String key()`, `List<FixtureDto> listFixtures(Instant from, Instant to)`, `StatSheetDto fetchStats(String externalMatchId)`, `Optional<String> mapPlayerId(String externalPlayerId)`.
- service/PackLoader (package-private): repository, then parser, then validator. It also runs the cross-file checks: duplicate pack ids naming both files (AC-9), adapter keys resolved against the injected `List<DataAdapter>` (AC-10), and duplicate `key()` across beans fail startup. It builds `PackRegistry` or throws `PackLoadException`.
- service/PackValidator (package-private): the R-5 checks (AC-3..AC-5) and every R-6 check (AC-6), given stable check codes: `required-section`, `duplicate-id`, `squad-bounds`, `negative-role-minimum`, `ladder-order`, `category-unknown-tag`, `category-max`, `appearance-unknown-stat`, `unknown-role`, `role-minimums-exceed-squad-min`, `unknown-stat`, `unknown-event-type`. It also checks `schemaVersion` against the supported set {1} (AC-8).
- util/PackParser: converts a `JsonNode` to `PackDto` one section at a time and one rule at a time (`treeToValue`). Each failure becomes a violation, and an unknown rule `type` is reported with its value (AC-17).
- model/PackRegistry (immutable `Map<String, LoadedPack>`) and model/LoadedPack (`Path file`, `PackDto pack`, `Optional<DataAdapter>`). Loader internals only.
- exception/PackLoadException (lists `PackViolation(file, packId, check, message)`; the message has one line per violation) and exception/PackNotFoundException. No `@RestControllerAdvice` yet (no controller).

Tests and fixtures:
- server/src/test/resources/packs/stub/stub-football.json: different roles, stats, tags, currency and event types, plus one rule of each primitive.
- packs/alt/: the second differing pack for AC-15.
- packs-invalid/<check>/pack.json: one invalid file per check.
- server/src/test/java/com/paddleup/packs/: `ApplicationContextRunner` tests on `PacksConfig` (no DB, fast; each restart is a new context), plus one full `@SpringBootTest` for AC-1.

Docs:
- docs/knowledge/decisions/2026-11-02-pack-format-and-registry.md (R-12, at most 6 lines).
- Decision candidate docs/knowledge/decisions/2026-11-02-agnostic-lint-specific-terms.md, which narrows 2026-11-02-agnostic-grep-in-lint: the gate bans specific names, not generic concept words.

Untouched: web/, packs/ (still `.gitkeep`), other modules, CLAUDE.md.

## Architecture changes
Proposed edits to docs/knowledge/architecture.md. The human approves; I have not edited it.
- Server modules, `repository` line: "repository (Spring JDBC; in packs, read-only file access)".
- New section "Sport Packs":
  - Packs are loaded once at startup from `paddleup.packs.dir` (default `../packs`, env `PADDLEUP_PACKS_DIR`) into an immutable in-memory registry. There is no DB table and no hot reload, and an invalid pack aborts startup.
  - Other modules read packs only through `packs.service.PackService` and `packs.dto`.
  - Data adapters are beans implementing `packs.service.DataAdapter`, selected by the pack's `adapterKey` == `key()`.
- Deployment note: the server image or working dir must contain the packs dir. Without it the server starts with zero packs and logs a WARN.
- Quality gates line: "`scripts/check-agnostic.sh` (grep for specific sport, role, stat and currency names, not the generic concept words, over `server/src/main` and `web/src`; self-tested by `scripts/test-check-agnostic.sh`)".
- The CLAUDE.md invariant wording is unchanged; the lint now matches it.

## Risks
- The narrowed lint lets a generic word hide a specific meaning (e.g. a hard-coded `stat` default). Mitigation: specific terms and ₹ stay banned, the self-test proves `wicket` still fails, and reviews check the dto names.
- Relative default `../packs` depends on the server cwd. Mitigation: env override; the WARN and the startup INFO log the resolved absolute path.
- Jackson polymorphic parse aborts on the first error. Mitigation: parse per section and per rule from the tree, so AC-11 sees all violations.
- A semantic check skipped because its section failed to parse could hide a violation. Mitigation: the error says which sections were skipped; the fix-and-rerun loop is acceptable at startup.
