// Bot managers for a live demo auction. Each bot polls the auction state as its own account (so it
// sees its own bid limits) and sends the same intents a person would: ready, bid, pass, picks.
// Usage: node scripts/demo/bots.mjs [--room <roomId>] [--start-in <sec>] [--human <email>]...
//   --start-in  reschedules the start to now + sec (the lobby shows first); default 45
//   --human     leave that seat to a person (repeatable); the Commissioner seat is always human
//   --no-start  keep the current start time
import { call, control, intent, loadState, money, must, signIn, sleep } from "./lib.mjs";
import { TIER_BY_NAME } from "./players.mjs";

const STYLES = {
  splurger: { star: [3.5, 7], mid: [1.5, 3], filler: [1, 1.8], passRate: 0.3 },
  shrewd: { star: [2, 3.5], mid: [1.4, 2.6], filler: [1.2, 2.2], passRate: 0.7 },
  balanced: { star: [2.5, 5], mid: [1.3, 2.4], filler: [1, 1.6], passRate: 0.5 },
  starHunter: { star: [4, 8], mid: [1, 1.6], filler: [1, 1.2], passRate: 0.8 },
  depth: { star: [1.5, 3], mid: [1.8, 3], filler: [1.3, 2.4], passRate: 0.4 },
};

const rand = (a, b) => a + Math.random() * (b - a);

/**
 * Runs bots until the auction ends. seats: [{ name, cookie, franchiseId, style }].
 * fast: quick reactions for a background run. commissioner: { cookie } handled when it is a bot
 * (fast seeding only): at main-done it ends the auction.
 */
export async function runBots({ roomId, seats, fast = false, commissioner = null, quiet = false }) {
  const say = (...m) => { if (!quiet) console.log(new Date().toLocaleTimeString(), ...m); };
  const value = new Map(); // `${seat}:${lot}` -> max lakh
  const pending = new Set(); // `${seat}` while a timer is armed
  const passedOnce = new Set(); // `${seat}:${lot}`
  const readied = new Set();
  const picked = new Set();
  let ended = false;
  let commissionerActed = new Set();
  const names = new Map();
  for (const s of seats) names.set(s.franchiseId, s.name);

  function valuation(seat, lot, standing, config) {
    const key = `${seat.name}:${lot.no}`;
    if (value.has(key)) return value.get(key);
    const style = STYLES[seat.style];
    const tier = TIER_BY_NAME.get(lot.name) ?? 3;
    const band = tier === 1 ? style.star : tier === 2 ? style.mid : style.filler;
    let v = lot.basePrice * rand(band[0], band[1]);
    // Chase a role the squad still needs for its minimum.
    const need = config?.roleMinimums?.[lot.role] ?? 0;
    if (standing && need > 0) {
      const have = standing.squad.filter((e) => e.role === lot.role).length;
      if (have < need) v *= 1.25;
    }
    // Some bots sit out a lot entirely.
    if (Math.random() < (tier === 3 ? 0.45 : 0.2)) v = 0;
    value.set(key, Math.round(v));
    return value.get(key);
  }

  function allowed(state, amount) {
    return (state.bidLimits ?? []).every((l) => l.above != null && l.above >= amount);
  }

  async function act(seat) {
    let res = await call(seat.cookie, "GET", `/api/rooms/${roomId}/auction`);
    if (res.status !== 200) return;
    const s = res.body;
    if (s.phase === "ended") { ended = true; return; }

    if ((s.phase === "scheduled" || s.phase === "resume_lobby") && !readied.has(`${seat.name}:${s.phase}`)) {
      readied.add(`${seat.name}:${s.phase}`);
      await sleep(rand(500, 4000));
      await call(seat.cookie, "PUT", `/api/rooms/${roomId}/auction/ready`, { ready: true });
      say(`${seat.name} is ready`);
      return;
    }

    if (s.phase === "nominating" && !picked.has(seat.name)) {
      picked.add(seat.name);
      const slots = s.openSlots ?? 0;
      const pool = [...(s.unsoldPool ?? [])].sort(() => Math.random() - 0.5);
      const lots = pool.slice(0, Math.min(slots, 1 + Math.floor(Math.random() * 3))).map((l) => l.no);
      await sleep(rand(800, 3000));
      const r = await call(seat.cookie, "PUT", `/api/rooms/${roomId}/auction/picks`, { intentId: intent(), lots });
      say(`${seat.name} nominates ${lots.length} unsold lot(s)`, r.body?.outcome ?? r.status);
      return;
    }

    if (s.phase !== "live" || !s.lotOpen || !s.lot || s.nextBid == null) return;
    if (s.leaderFranchiseId === seat.franchiseId) return;
    if (s.passedBy.includes(seat.franchiseId)) return;

    const standing = s.standings.find((x) => x.franchiseId === seat.franchiseId);
    // Squad roles are not in the standing entry, so join them from the lots we have seen.
    if (standing) standing.squad = standing.squad.map((e) => ({ ...e, role: seenRoles.get(e.lot) }));
    seenRoles.set(s.lot.no, s.lot.role);
    const max = valuation(seat, s.lot, standing, s.config);
    const remaining = new Date(s.deadline).getTime() - new Date(s.serverNow).getTime();
    const passKey = `${seat.name}:${s.lot.no}`;

    if (s.nextBid > max || !allowed(s, s.nextBid)) {
      if (!passedOnce.has(passKey) && Math.random() < STYLES[seat.style].passRate) {
        passedOnce.add(passKey);
        await sleep(fast ? rand(100, 400) : rand(1200, Math.max(1500, Math.min(5000, remaining - 1500))));
        const r = await call(seat.cookie, "POST", `/api/rooms/${roomId}/auction/passes`, { intentId: intent(), lot: s.lot.no });
        if (r.body?.outcome === "accepted") say(`${seat.name} passes on ${s.lot.name}`);
      } else {
        passedOnce.add(passKey);
      }
      return;
    }

    // Think a little, sometimes wait to snipe near the end.
    const snipe = !fast && Math.random() < 0.15 && remaining > 3000;
    const delay = fast ? rand(100, 700) : snipe ? remaining - rand(900, 1600) : rand(700, Math.max(900, Math.min(4500, remaining - 1500)));
    await sleep(Math.max(0, delay));
    res = await call(seat.cookie, "GET", `/api/rooms/${roomId}/auction`);
    const now = res.body;
    if (!now || now.phase !== "live" || !now.lotOpen || now.lot?.no !== s.lot.no) return;
    if (now.leaderFranchiseId === seat.franchiseId || now.nextBid == null || now.nextBid > max || !allowed(now, now.nextBid)) return;
    const r = await call(seat.cookie, "POST", `/api/rooms/${roomId}/auction/bids`, { intentId: intent(), lot: now.lot.no, amount: now.nextBid });
    if (r.body?.outcome === "accepted") say(`${seat.name} bids ${money(now.nextBid)} on ${now.lot.name}`);
  }

  const seenRoles = new Map();

  async function commissionerTick() {
    if (!commissioner) return;
    const res = await call(commissioner.cookie, "GET", `/api/rooms/${roomId}/auction`);
    const s = res.body;
    if (!s) return;
    if (s.phase === "main_done" && !commissionerActed.has("main_done")) {
      commissionerActed.add("main_done");
      await sleep(500);
      const r = await control(commissioner.cookie, roomId, "end_auction");
      say("Commissioner ends the auction", r.body?.outcome ?? r.status);
    }
    if (s.phase === "ended") ended = true;
  }

  const loops = seats.map(async (seat) => {
    while (!ended) {
      if (!pending.has(seat.name)) {
        pending.add(seat.name);
        act(seat).catch((e) => say(`${seat.name} error`, e.message)).finally(() => pending.delete(seat.name));
      }
      await sleep(fast ? 150 : 400);
    }
  });
  const boss = (async () => {
    while (!ended) {
      await commissionerTick().catch(() => {});
      await sleep(1000);
    }
  })();
  await Promise.all([...loops, boss]);
  say("Auction ended");
}

// CLI: run bots for the live demo room.
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const opt = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
  const humans = args.flatMap((a, i) => (a === "--human" ? [args[i + 1]] : []));
  const state = loadState();
  const live = state.live;
  if (!live) throw new Error("No demo state; run scripts/demo/seed.mjs first");
  const roomId = opt("--room") ?? live.roomId;
  const commish = await signIn(live.commissionerEmail);
  if (!args.includes("--no-start")) {
    const st = must(await call(commish, "GET", `/api/rooms/${roomId}/auction`), "state");
    if (st.phase === "scheduled") {
      const sec = Number(opt("--start-in") ?? 45);
      const startsAt = new Date(Date.now() + sec * 1000).toISOString();
      must(await call(commish, "PUT", `/api/rooms/${roomId}/auction/schedule`, { startsAt }), "reschedule");
      console.log(`Auction starts in ${sec}s (${startsAt})`);
    }
  }
  const seats = [];
  for (const s of live.seats) {
    if (s.email === live.commissionerEmail || humans.includes(s.email)) continue;
    seats.push({ ...s, cookie: await signIn(s.email) });
  }
  console.log(`Bots: ${seats.map((s) => `${s.name} (${s.style})`).join(", ")}`);
  await runBots({ roomId, seats });
}
