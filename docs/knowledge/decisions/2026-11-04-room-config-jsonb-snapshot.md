tags: [rooms, config, jsonb]
Room config and pack vocabulary are stored as one JSONB snapshot with an int config_version, taken at room creation.
Why: one read/write per save, validated in Java, immune to later pack edits (R-1, R-2, R-13).
Cost: no SQL constraints inside the config; saves use version compare-and-set (409 stale_version).
