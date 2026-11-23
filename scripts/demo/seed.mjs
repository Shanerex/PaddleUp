// Builds the demo world over the real REST API (plus psql for fixtures and provider stats, which
// have no route yet). Expects a fresh demo database and the server started by scripts/demo/up.sh.
//
// Rooms (all visible to cara@paddleup.test, the account you play as):
//   Seed League            setup, no player pool (made by the dev seed before season data existed)
//   Weekend Warriors       setup with a pool and an open invite; Cara can configure and schedule
//   Office Premier League  scheduled in 3 days; Alex is Commissioner, Cara a Manager (lobby view)
//   Sunday Smashers        auction finished (bots played it), squads, 3 matches with points
//   Demo Cup               the live simulation: Cara vs 5 bots, start with scripts/demo/bots.mjs
import { call, must, signIn, sql, saveState, sleep, money } from "./lib.mjs";
import { PLAYERS, seasonCsv } from "./players.mjs";
import { runBots } from "./bots.mjs";

const PACK = "cricket-t20";
const CARA = "cara@paddleup.test";
const people = {
  cara: { email: CARA, name: "Cara" },
  alex: { email: "alex@paddleup.test", name: "Alex" },
  bea: { email: "bea@paddleup.test", name: "Bea" },
  chen: { email: "chen@paddleup.test", name: "Chen" },
  dev: { email: "dev@paddleup.test", name: "Dev" },
  esha: { email: "esha@paddleup.test", name: "Esha" },
};
const step = (m) => console.log(`\n== ${m}`);

async function cookieOf(key) {
  const p = people[key];
  if (!p.cookie) {
    p.cookie = await signIn(p.email);
    must(await call(p.cookie, "PATCH", "/api/me", { displayName: p.name }), `name ${p.name}`);
  }
  return p.cookie;
}

/** A room with the Commissioner's own franchise and the joiners'. Returns { room, seats }. */
async function makeRoom({ owner, name, competition, franchise, joiners, config = {} }) {
  const c = await cookieOf(owner);
  let room = must(await call(c, "POST", "/api/rooms", { packId: PACK, presetId: "standard", name, competition }), `create ${name}`);
  room = must(await call(c, "PUT", `/api/rooms/${room.id}/config`, { version: room.configVersion, config: { ...room.config, ...config } }), `config ${name}`);
  must(await call(c, "POST", `/api/rooms/${room.id}/franchises`, franchise), `franchise ${name}`);
  const invite = must(await call(c, "POST", `/api/rooms/${room.id}/invite`, {}), `invite ${name}`);
  for (const [key, f] of joiners) {
    must(await call(await cookieOf(key), "POST", "/api/join", { code: invite.code, franchise: f }), `join ${key} ${name}`);
  }
  const members = must(await call(c, "GET", `/api/rooms/${room.id}/members`), "members");
  const byName = new Map(members.filter((m) => m.franchise).map((m) => [m.franchise.name, m.franchise.id]));
  return { room, invite, byName };
}

const F = (name, colour, monogram) => ({ name, colour, monogram });

// ---------------------------------------------------------------------------------------------
step("Accounts and season data");
for (const k of Object.keys(people)) await cookieOf(k);
const form = new FormData();
form.append("packId", PACK);
form.append("file", new Blob([seasonCsv()], { type: "text/csv" }), "ipl-demo-season.csv");
const imported = must(await call(people.cara.cookie, "POST", "/api/admin/datasets", form), "import season");
console.log(`Imported ${PLAYERS.length} players as dataset v${imported.version ?? "?"}`);
const datasetId = sql(`SELECT id FROM season_datasets WHERE pack_id = '${PACK}' ORDER BY version DESC LIMIT 1`);

// ---------------------------------------------------------------------------------------------
step("Weekend Warriors (setup, pool loaded, invite open)");
const ww = await makeRoom({
  owner: "cara", name: "Weekend Warriors", competition: "IPL 2027",
  franchise: F("Crimson Kings", "crimson", "CK"),
  joiners: [["bea", F("Bronze Bulls", "bronze", "BB")], ["chen", F("Teal Tigers", "teal", "TG")]],
});
{
  // One edited player so the pool shows an edit.
  const pool = must(await call(people.cara.cookie, "GET", `/api/rooms/${ww.room.id}/pool`), "pool");
  const p = pool.players.find((x) => x.name === "Rinku Singh");
  if (p) {
    const r = await call(people.cara.cookie, "PUT", `/api/rooms/${ww.room.id}/pool/players/${p.id}`, { version: pool.version, name: p.name, country: p.country, role: p.role, tags: p.tags, basePrice: 100, set: p.set, providerId: p.providerId });
    console.log(`Edited Rinku Singh base price: ${r.status}`);
  }
}

// ---------------------------------------------------------------------------------------------
step("Office Premier League (scheduled in 3 days, Cara is a Manager)");
const opl = await makeRoom({
  owner: "alex", name: "Office Premier League", competition: "IPL 2027", config: { squadMin: 15, squadMax: 20 },
  franchise: F("Tangerine Titans", "tangerine", "TT"),
  joiners: [["cara", F("Crimson Kings", "crimson", "CK")], ["chen", F("Teal Tigers", "teal", "TG")], ["dev", F("Cobalt Chargers", "cobalt", "CC")]],
});
{
  const startsAt = new Date(Date.now() + 3 * 24 * 3600 * 1000);
  startsAt.setUTCHours(14, 0, 0, 0);
  must(await call(people.alex.cookie, "PUT", `/api/rooms/${opl.room.id}/auction/schedule`, { startsAt: startsAt.toISOString() }), "schedule OPL");
  // Two managers already marked ready in the lobby.
  await call(people.chen.cookie, "PUT", `/api/rooms/${opl.room.id}/auction/ready`, { ready: true });
  await call(people.dev.cookie, "PUT", `/api/rooms/${opl.room.id}/auction/ready`, { ready: true });
}

// ---------------------------------------------------------------------------------------------
step("Sunday Smashers (bots play a short auction to the end)");
const smashConfig = { squadMin: 5, squadMax: 6, roleMinimums: { WK: 1, BAT: 1, BOWL: 1, AR: 1 }, categoryQuotas: { "overseas-cap": 3 }, openSeconds: 5, bidSeconds: 3, gapSeconds: 0 };
const ss = await makeRoom({
  owner: "cara", name: "Sunday Smashers", competition: "IPL 2027", config: smashConfig,
  franchise: F("Crimson Kings", "crimson", "CK"),
  joiners: [["bea", F("Bronze Bulls", "bronze", "BB")], ["chen", F("Teal Tigers", "teal", "TG")], ["esha", F("Orchid Owls", "orchid", "OO")]],
});
{
  // Trim the pool to 26 headline players so the bot auction takes a couple of minutes.
  const keep = new Set(["Virat Kohli", "Jasprit Bumrah", "Rashid Khan", "Jos Buttler", "Hardik Pandya", "Pat Cummins", "Shubman Gill", "Suryakumar Yadav", "Travis Head", "Yashasvi Jaiswal", "Rishabh Pant", "KL Rahul", "Sanju Samson", "Heinrich Klaasen", "Ravindra Jadeja", "Andre Russell", "Glenn Maxwell", "Axar Patel", "Sunil Narine", "Mohammed Shami", "Mohammed Siraj", "Trent Boult", "Mitchell Starc", "Yuzvendra Chahal", "Kuldeep Yadav", "Abhishek Sharma"]);
  let pool = must(await call(people.cara.cookie, "GET", `/api/rooms/${ss.room.id}/pool`), "pool");
  for (const p of pool.players.filter((x) => !keep.has(x.name))) {
    pool = must(await call(people.cara.cookie, "DELETE", `/api/rooms/${ss.room.id}/pool/players/${p.id}?version=${pool.version}`), `remove ${p.name}`);
  }
  console.log(`Pool trimmed to ${pool.players.length}`);
  must(await call(people.cara.cookie, "PUT", `/api/rooms/${ss.room.id}/auction/schedule`, { startsAt: new Date(Date.now() + 4000).toISOString() }), "schedule SS");
  const seats = [
    { name: "Cara", key: "cara", style: "splurger", f: "Crimson Kings" },
    { name: "Bea", key: "bea", style: "shrewd", f: "Bronze Bulls" },
    { name: "Chen", key: "chen", style: "balanced", f: "Teal Tigers" },
    { name: "Esha", key: "esha", style: "depth", f: "Orchid Owls" },
  ].map((s) => ({ ...s, cookie: people[s.key].cookie, franchiseId: ss.byName.get(s.f) }));
  const t0 = Date.now();
  await runBots({ roomId: ss.room.id, seats, fast: true, commissioner: { cookie: people.cara.cookie }, quiet: true });
  const end = must(await call(people.cara.cookie, "GET", `/api/rooms/${ss.room.id}/auction`), "state");
  console.log(`Auction ended in ${Math.round((Date.now() - t0) / 1000)}s`);
  for (const st of end.standings) console.log(`  ${[...ss.byName].find(([, id]) => id === st.franchiseId)?.[0]}: ${st.squad.length} players, ${money(st.purse)} left`);
}

// ---------------------------------------------------------------------------------------------
step("Season fixtures and provider stats");
{
  const day = 24 * 3600 * 1000;
  const at = (ms) => new Date(Date.now() + ms).toISOString();
  const matches = [
    ["demo-m1", at(-6 * day), "Season opener"],
    ["demo-m2", at(-4 * day), "Southern derby"],
    ["demo-m3", at(-2 * day), "Rivalry week"],
    ["demo-m4", at(-3 * 3600 * 1000), "Tonight (no data yet)"],
    ["demo-m5", at(2 * day), "Super Sunday"],
    ["demo-m6", at(5 * day), null],
  ];
  sql(`INSERT INTO scoring_matches (dataset_id, match_id, starts_at, label) VALUES ${matches.map(([id, t, l]) => `('${datasetId}', '${id}', '${t}', ${l ? `'${l}'` : "NULL"})`).join(",")}`);
  const ids = new Map(sql(`SELECT name || '|' || id FROM season_players WHERE dataset_id = '${datasetId}'`).split("\n").map((r) => r.split("|")));
  const ri = (a, b) => Math.floor(a + Math.random() * (b - a + 1));
  const wicketKinds = ["bowled", "lbw", "caught", "caught", "caught", "stumped"];
  for (const [match] of matches.slice(0, 3)) {
    const rows = [];
    const add = (pid, stat, value, ev = "") => rows.push(`('${datasetId}', '${match}', '${pid}', '${stat}', '${ev}', ${value})`);
    for (const p of PLAYERS) {
      const pid = ids.get(p.name);
      if (!pid || Math.random() < 0.3) { if (pid) add(pid, "in-playing-xi", 0); continue; }
      add(pid, "in-playing-xi", 1);
      const bats = p.role !== "BOWL" || Math.random() < 0.3;
      if (bats) {
        const top = p.role === "BAT" || p.role === "WK";
        const balls = top ? ri(4, 55) : ri(1, 25);
        const runs = Math.max(0, Math.round(balls * (Math.random() * 1.2 + 0.6)) - (Math.random() < 0.1 ? balls : 0));
        add(pid, "runs", runs); add(pid, "balls-faced", balls);
        add(pid, "fours", Math.floor(runs / ri(9, 15))); add(pid, "sixes", Math.floor(runs / ri(12, 25)));
        add(pid, "duck", runs === 0 ? 1 : 0);
        add(pid, "strike-rate", (balls ? (runs * 100) / balls : 0).toFixed(2));
      }
      if (p.role === "BOWL" || p.role === "AR") {
        const balls = 6 * ri(p.role === "BOWL" ? 3 : 1, 4);
        const conceded = Math.round((balls / 6) * (Math.random() * 5 + 6));
        const wk = Math.random() < 0.55 ? ri(0, 2) : ri(0, 4);
        add(pid, "wickets", wk);
        for (let w = 0; w < wk; w++) add(pid, "wickets", 1, wicketKinds[ri(0, wicketKinds.length - 1)]);
        add(pid, "balls-bowled", balls); add(pid, "runs-conceded", conceded);
        add(pid, "maidens", Math.random() < 0.15 ? 1 : 0);
        add(pid, "economy", ((conceded * 6) / balls).toFixed(2));
      }
      add(pid, "catches", Math.random() < 0.3 ? ri(1, 2) : 0);
      if (p.role === "WK") add(pid, "stumpings", Math.random() < 0.25 ? 1 : 0);
    }
    // Event-type rows repeat per wicket; fold them into counts.
    const folded = new Map();
    for (const r of rows) {
      const m = /^\('([^']+)', '([^']+)', '([^']+)', '([^']+)', '([^']*)', (.+)\)$/.exec(r);
      const key = `${m[3]}|${m[4]}|${m[5]}`;
      const prev = folded.get(key);
      folded.set(key, m[5] ? { m, v: (prev?.v ?? 0) + Number(m[6]) } : { m, v: m[6] });
    }
    const values = [...folded.values()].map(({ m, v }) => `('${m[1]}', '${m[2]}', '${m[3]}', '${m[4]}', '${m[5]}', ${v})`);
    sql(`INSERT INTO scoring_stat_values (dataset_id, match_id, season_player_id, stat, event_type, value) VALUES ${values.join(",")}`);
  }
  // Points are stored by a recompute, which a manual save or clear triggers for the whole match.
  // m1 and m2: save then clear (no override left); m3 keeps one manual correction.
  for (const room of [ss.room.id]) {
    for (const match of ["demo-m1", "demo-m2", "demo-m3"]) {
      const list = must(await call(people.cara.cookie, "GET", `/api/rooms/${room}/stat-entry/matches/${match}/players`), `players ${match}`);
      const first = list.players.find((p) => p.name === "Virat Kohli") ?? list.players[0];
      must(await call(people.cara.cookie, "PUT", `/api/rooms/${room}/stat-entry/matches/${match}/players/${first.poolPlayerId}`, { fields: [{ stat: "catches", value: 2 }] }), `save ${match}`);
      if (match !== "demo-m3") {
        must(await call(people.cara.cookie, "POST", `/api/rooms/${room}/stat-entry/matches/${match}/players/${first.poolPlayerId}/clear`, { fields: [{ stat: "catches" }] }), `clear ${match}`);
      }
    }
  }
  console.log("6 fixtures, provider stats for 3, points computed for Sunday Smashers");
}

// ---------------------------------------------------------------------------------------------
step("Demo Cup (the live simulation room)");
const liveSeats = [
  { key: "cara", style: "human", f: F("Crimson Kings", "crimson", "CK") },
  { key: "alex", style: "splurger", f: F("Tangerine Titans", "tangerine", "TT") },
  { key: "bea", style: "shrewd", f: F("Bronze Bulls", "bronze", "BB") },
  { key: "chen", style: "balanced", f: F("Teal Tigers", "teal", "TG") },
  { key: "dev", style: "starHunter", f: F("Cobalt Chargers", "cobalt", "CC") },
  { key: "esha", style: "depth", f: F("Orchid Owls", "orchid", "OO") },
];
const dc = await makeRoom({
  owner: "cara", name: "Demo Cup", competition: "IPL 2027 mock auction",
  config: { purse: 9000, squadMin: 9, squadMax: 12, roleMinimums: { WK: 1, BAT: 3, BOWL: 3, AR: 2 }, categoryQuotas: { "overseas-cap": 4 }, openSeconds: 15, bidSeconds: 10, gapSeconds: 4 },
  franchise: liveSeats[0].f,
  joiners: liveSeats.slice(1).map((s) => [s.key, s.f]),
});
must(await call(people.cara.cookie, "PUT", `/api/rooms/${dc.room.id}/auction/schedule`, { startsAt: new Date(Date.now() + 60 * 60 * 1000).toISOString() }), "schedule Demo Cup");

saveState({
  datasetId,
  rooms: { weekendWarriors: ww.room.id, officePremierLeague: opl.room.id, sundaySmashers: ss.room.id, demoCup: dc.room.id },
  live: {
    roomId: dc.room.id,
    commissionerEmail: CARA,
    seats: liveSeats.map((s) => ({ name: people[s.key].name, email: people[s.key].email, style: s.style, franchiseId: dc.byName.get(s.f.name) })),
  },
});
console.log(`\nDone. Demo Cup is scheduled an hour out; scripts/demo/bots.mjs moves the start up and plays the other five seats.`);
await sleep(10);
