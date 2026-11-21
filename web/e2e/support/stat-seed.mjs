// Seeds season matches and provider stats for the manual stat entry specs. There is no REST route
// for fixtures or provider stats (ingestion is a later spec), so rows go in through psql in the
// `make dev` Postgres container (E2E_PG_CONTAINER, default paddleup-postgres-1; E2E_PG_DB, default paddleup).
import { execFileSync } from "node:child_process";
import { call } from "./seed.mjs";

const CONTAINER = process.env.E2E_PG_CONTAINER ?? "paddleup-postgres-1";
const DB = process.env.E2E_PG_DB ?? "paddleup";

export function sql(statement) {
  return execFileSync("docker", ["exec", CONTAINER, "psql", "-U", "paddleup", "-d", DB, "-v", "ON_ERROR_STOP=1", "-At", "-c", statement], { encoding: "utf8" }).trim();
}

const day = 24 * 3600 * 1000;

/**
 * Four matches on the room's dataset: m1 (provider data), m2 (provider data, the one the specs
 * open), m3 (started, no data) and m4 (upcoming). The first six pool players get provider stats in
 * m1 and m2. Points are computed by one save and one clear, which recompute the whole match.
 */
export async function seedMatches(fx) {
  const dataset = sql(`SELECT dataset_id FROM room_pools WHERE room_id = '${fx.roomId}'`);
  const now = Date.now();
  const at = (offset) => new Date(now + offset * day).toISOString();
  sql(`INSERT INTO scoring_matches (dataset_id, match_id, starts_at, label) VALUES
    ('${dataset}', 'e2e-m1', '${at(-3)}', 'Opener'),
    ('${dataset}', 'e2e-m2', '${at(-2)}', 'Derby'),
    ('${dataset}', 'e2e-m3', '${at(-1)}', NULL),
    ('${dataset}', 'e2e-m4', '${at(2)}', 'Final')
    ON CONFLICT DO NOTHING`);
  const seasonIds = sql(`SELECT source_player_id FROM room_pool_players WHERE room_id = '${fx.roomId}' AND source_player_id IS NOT NULL ORDER BY position, id LIMIT 6`).split("\n");
  for (const match of ["e2e-m1", "e2e-m2"]) {
    const rows = seasonIds.flatMap((id, i) => [
      `('${dataset}', '${match}', '${id}', 'minutes', '', ${60 + i * 5})`,
      `('${dataset}', '${match}', '${id}', 'assists', '', ${i % 3})`,
      `('${dataset}', '${match}', '${id}', 'goals', 'open-play', ${i % 2})`,
    ]);
    sql(`INSERT INTO scoring_stat_values (dataset_id, match_id, season_player_id, stat, event_type, value) VALUES ${rows.join(",")} ON CONFLICT DO NOTHING`);
  }
  const first = (await call(fx.commissioner, "GET", `/api/rooms/${fx.roomId}/stat-entry/matches/e2e-m2/players`)).body.players[0];
  // A save, then (for m1) a clear, so every provider player has stored points without leaving an override on m1.
  for (const match of ["e2e-m1", "e2e-m2"]) {
    const saved = await call(fx.commissioner, "PUT", `/api/rooms/${fx.roomId}/stat-entry/matches/${match}/players/${first.poolPlayerId}`, { fields: [{ stat: "assists", value: 3 }] });
    if (saved.status !== 200) throw new Error(`seed save ${match}: ${saved.status} ${JSON.stringify(saved.body)}`);
  }
  const cleared = await call(fx.commissioner, "POST", `/api/rooms/${fx.roomId}/stat-entry/matches/e2e-m1/players/${first.poolPlayerId}/clear`, { fields: [{ stat: "assists" }] });
  if (cleared.status !== 200) throw new Error(`seed clear: ${cleared.status} ${JSON.stringify(cleared.body)}`);
  return { dataset, first, seasonIds };
}

/** New provider values for a match, written straight to the table (the provider "delivers"). */
export function deliverProvider(dataset, match, seasonId, stat, value) {
  sql(`INSERT INTO scoring_stat_values (dataset_id, match_id, season_player_id, stat, event_type, value) VALUES ('${dataset}', '${match}', '${seasonId}', '${stat}', '', ${value})
    ON CONFLICT (dataset_id, match_id, season_player_id, stat, event_type) DO UPDATE SET value = EXCLUDED.value`);
}
