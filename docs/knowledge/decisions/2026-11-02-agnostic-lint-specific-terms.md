tags: [lint, invariants, sport-agnostic, packs]
# Agnostic lint bans specific names, not generic concept words
Decision: narrows 2026-11-02-agnostic-grep-in-lint. `scripts/check-agnostic.sh` no longer bans sport, role, stat and currency (and plurals); specific names (cricket, wicket, inr, ...) and the rupee sign stay banned. `scripts/test-check-agnostic.sh` self-tests it in `make lint`.
Why: pack contract fields are named roles, statSchema and currencyCode; the invariant forbids naming a sport, role, stat or currency, not the concepts. CLAUDE.md wording is unchanged.
