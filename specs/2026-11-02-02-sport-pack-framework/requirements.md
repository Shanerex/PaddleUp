---
status: final
---
# Requirements: 2026-11-02-02-sport-pack-framework

## Problem
Core modules (rooms, auction, scoring, advisor) must read every sport semantic from a pack, or the agnostic invariant breaks on day one.
Pack authors need one versioned contract and a loader that rejects a broken pack with a clear error.
Scope: FR-140, FR-141, FR-142 (interface only), FR-143, NFR-12; module packs. Cricket data is spec 03, not here.

## Pack lifetime matrix
A pack lives for the process: read once at startup, immutable, changed only by file edit plus restart (no hot reload, no sliding).
Entry points: S = startup load, L = registry lookup (by pack id), R = restart.
- Pack dir absent or empty: S starts with zero packs and logs a warning (R-3); L returns not-found (AC-7); R same.
- Valid pack: S registers it (AC-1); L returns it unchanged (AC-2); R re-reads the file, so an edited valid file takes effect (AC-12).
- Invalid pack (FR-143 or structural): S aborts startup naming file, pack id, every violation (AC-3..AC-6); L and R n/a, the app never runs.
- Unknown schemaVersion: S aborts with a versioned error (AC-8).
- Same pack id in two files (any version): S aborts naming both files (AC-9).
- Unknown adapter key: S aborts (AC-10); pack with no adapter key is manual-entry only (R-8).

## Requirements
- R-1: Define a versioned pack JSON schema (schemaVersion, pack id, pack version) holding every FR-140 section: roles+labels, tags, restricted categories, room presets, stat schema, default rule set, appearance rule, adapter key, UI terminology, AI domain context.
- R-2: Room presets carry purse, currency code and display unit, increment ladder, squad min/max, role minimums, retention/RTM defaults, all as opaque pack data.
- R-3: At startup load every pack JSON from a filesystem directory set by config (default: repo `packs/`) into an immutable registry; a missing or empty directory is not an error.
- R-4: packs.service exposes the registry: list packs, get by pack id; returns packs.dto types only (FR-141). One version per pack id; the version is pack metadata, not a lookup key.
- R-5: Validate per FR-143: every role referenced anywhere in the pack exists in its role list; the sum of role minimums is at most squad min; every scoring rule references only stats in the stat schema.
- R-6: Validate structure: required sections present; ids unique within their list; squad min <= max; role minimums >= 0; increment ladder thresholds strictly ascending; each restricted category names an existing tag and its max <= squad max; appearance rule names an existing stat.
- R-7: Fix the rule JSON shape for the FR-71 primitives: per-unit, threshold bonus, banded rate with minimum volume, role-conditional, event-type bonus, appearance bonus. Each parses to a typed rule; evaluation is scoring's job, not this spec.
- R-8: Define the data adapter interface (FR-142): list fixtures, fetch completed-match stats as a stat sheet in the pack's stat schema, map external player IDs. A pack names its adapter by string key, resolved to a Spring bean implementing the interface; no key means manual entry only. The stat sheet type is the one manual entry (FR-81) will reuse.
- R-9: Any load failure aborts startup (fail fast) with one error listing pack file, pack id and every violation found, not only the first.
- R-10: No class, schema or UI string in server/src/main names a sport, role, stat or currency (NFR-12); role, stat and tag ids are opaque strings, labels come from the pack.
- R-11: A stub football pack in server/src/test/resources, with different roles, stats, tags and currency from the cricket data, loads through the same code path with zero core changes; tests point the config dir at test resources.
- R-12: A decision record in docs/knowledge/decisions fixes the pack format, one-version-per-id policy and adapter key registration.

## Acceptance criteria
- AC-1: Starting the app with the stub pack in the configured dir (default `packs/`) registers it; the registry lists its id and version.
- AC-2: Get by pack id returns every FR-140 section exactly as in the file.
- AC-3: A pack whose scoring rule names a role not in its role list fails startup; the error contains the file name, pack id and that role id.
- AC-4: A pack whose role minimums sum to more than squad min fails startup; the error states both numbers.
- AC-5: A pack whose scoring rule names a stat not in the stat schema fails startup; the error contains that stat id.
- AC-6: A pack breaking any R-6 check (one test per check) fails startup naming the check and the offending id.
- AC-7: Get for an unknown pack id returns not-found (typed exception), not null or a default pack.
- AC-8: A pack with an unsupported schemaVersion fails startup with an error naming the version and the supported ones.
- AC-9: Two files with the same pack id, same or different version, fail startup naming both files.
- AC-10: A pack naming an unregistered adapter key fails startup; a pack with no adapter key loads and reports manual-entry only.
- AC-11: A pack with three violations reports all three in one startup error (R-9).
- AC-12: Editing a valid stub pack file and restarting returns the edited values; no stale copy is served.
- AC-13: A test implements the adapter interface for the stub pack, returns a stat sheet whose stats all validate against the stub stat schema, and maps one external player ID.
- AC-14: make lint passes, including scripts/check-agnostic.sh, with the packs module present.
- AC-15: Core code (packs main sources) is unchanged between a run with only the stub pack and a run with a second differing test pack; both load (NFR-12).
- AC-16: The decision record from R-12 exists and is at most 6 lines.
- AC-17: A stub pack with one rule of each R-7 primitive loads and each parses to its typed rule; a rule with an unknown primitive type fails startup naming it.

## Out of scope
- The cricket pack data and its rule values (spec 03); the live cricket provider adapter (spec 13).
- Rule evaluation, scoring, ingestion, manual entry UI; the scripted auction and scoring cycle of NFR-12 (runs once those modules exist, reusing the stub).
- Rooms referencing a pack and room-level preset edits (rooms specs); multiple coexisting versions of one pack id; pack hot reload; pack upload UI.
- Prompt assembly from the AI domain context (advisor specs); this spec only stores and validates its presence.
