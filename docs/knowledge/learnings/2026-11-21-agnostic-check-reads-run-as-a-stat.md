tags: [agnostic, lint, web]
`scripts/check-agnostic.sh` bans the word `run`, so a neutral method name `intents.run` (spec 09) turned `make test` and `make lint` red; it reproduced on the branch before any spec 12 change.
Renamed to `submit`. Pick method and variable names with the ban list (`scripts/check-agnostic.sh` WORDS) in mind. Related: [[2026-11-04-mock-copy-must-pass-check-agnostic]].
