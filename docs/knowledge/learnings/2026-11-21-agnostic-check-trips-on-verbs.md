tags: [check-agnostic, tooling, specs]
scripts/check-agnostic.sh matches whole words, so an ordinary identifier such as intents.run( reads as the stat "run" and fails AuctionStubPackIT; main went red this way after PR 16 and nobody saw it until the next spec ran make test.
Allowlist such identifiers in the script's sed list, and run the script before merging any web change that adds a verb-like name.
AC text that quotes an "existing" error code must be checked against architecture.md: non-members get 404 on auction routes, not 403.
