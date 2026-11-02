tags: [packs, sport-agnostic, format]
# Pack format: schemaVersion 1 JSON files in one registry
Decision: a pack is one `*.json` file in `paddleup.packs.dir` with `schemaVersion` 1; an unsupported version or any violation aborts startup. One version per pack id (a duplicate id fails); `version` is metadata, not a key.
Adapters are Spring beans implementing `DataAdapter`; a pack picks one with `adapterKey` == `key()`. No key means manual entry; unknown or duplicate keys fail startup.
Why: files keep the pack the only source of truth, and fail-fast loading keeps core modules free of sport data.
