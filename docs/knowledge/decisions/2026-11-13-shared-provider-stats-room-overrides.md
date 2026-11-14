tags: [scoring, stats, ingestion, manual-entry]
Matches and provider stats are stored once per season dataset and shared by every room on it. Manual stats are sparse per-room overrides; the effective value is the override if present, else the provider value. Commissioner-added players have no source_player_id and take manual stats only.
Why: one import serves all rooms, while manual entry stays room-scoped (manual beats provider, recompute idempotent).
Open: provider import recomputes every room synchronously; batch or async fan-out is for 2026-11-02-13-provider-ingestion.
