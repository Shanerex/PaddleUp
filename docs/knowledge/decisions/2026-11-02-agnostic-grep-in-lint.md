tags: [lint, invariants, sport-agnostic]
# The sport-agnostic invariant is a lint gate
Decision: `make lint` runs `scripts/check-agnostic.sh` after Spotless and ESLint. It word-boundary greps `server/src/main` and `web/src` (tests excluded) for sport, role, stat and currency terms.
Why: CLAUDE.md forbids naming a sport, role, stat or currency in core code; a grep catches regressions cheaply. `SpringApplication.run(` is allowlisted because the term "run" matches it.
