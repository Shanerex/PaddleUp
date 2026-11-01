tags: [process, rx, specs, roadmap]
# All P0/P1 specs pre-seeded in build order
Decision: 27 specs (2026-11-02-NN-<feature>) are created up front with intake from the PRD; NN is the build order and each depends only on lower numbers (docs/roadmap.md).
Why: makes the full scope and dependency order visible before building. P2 stays unspecced.
Start one with git switch -c feature/<id> main, then /rx:next; /rx:feature refuses existing ids.
