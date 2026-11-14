tags: [scoring, rooms, rules, freeze]
Scoring owns the room's edited rule set, its version (compare-and-set, 409 stale_version), frozen_at and the append-only scoring_rule_log; no row means pack default. The rooms config scoringRules stays the immutable pack-default snapshot and saveConfig ignores client-sent rules. Scoring reads rooms only through rooms.service.ScoringRoomPort.
Why: rooms would otherwise need fixture data from scoring (module cycle) and freeze/attestation paths in saveConfig.
Rejected: rules editable in rooms config; event-sourced rule set (no replay consumer yet).
