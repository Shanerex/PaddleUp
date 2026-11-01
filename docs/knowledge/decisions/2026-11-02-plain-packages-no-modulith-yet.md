tags: [architecture, modules, server]
# Module boundaries are plain packages for now
Decision: one top-level package per module under `com.paddleup` (rooms, auction, scoring, packs, advisor), with no automated boundary enforcement yet.
Why: the scaffold has no domain code to enforce against. Enforcement (ArchUnit or Spring Modulith) is deferred to the first feature that adds cross-module calls.
