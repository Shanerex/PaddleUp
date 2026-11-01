/* PaddleUp prototype helpers: theme tokens + fake auction engine (prototype only; real engine is server-authoritative). */
(function () {
  const L = (h) => ({
    '--team': `oklch(0.56 0.15 ${h})`, '--team-strong': `oklch(0.48 0.14 ${h})`, '--team-tint': `oklch(0.955 0.03 ${h})`,
    '--team-tint-2': `oklch(0.9 0.06 ${h})`, '--team-ink': `oklch(0.42 0.12 ${h})`, '--on-team': `oklch(0.99 0.005 ${h})`, '--room-bg': `oklch(0.972 0.014 ${h})`
  });
  const D = (h) => ({
    '--team': `oklch(0.6 0.15 ${h})`, '--team-strong': `oklch(0.66 0.15 ${h})`, '--team-tint': `oklch(0.28 0.05 ${h})`,
    '--team-tint-2': `oklch(0.36 0.07 ${h})`, '--team-ink': `oklch(0.82 0.1 ${h})`, '--on-team': `oklch(0.99 0.005 ${h})`, '--room-bg': `oklch(0.19 0.02 ${h})`
  });
  const LIGHT = {
    '--bg':'oklch(0.975 0.006 75)','--surface':'oklch(0.995 0.003 75)','--surface-2':'oklch(0.955 0.007 75)','--line':'oklch(0.905 0.008 75)',
    '--ink':'oklch(0.23 0.012 60)','--ink-2':'oklch(0.46 0.012 60)','--ink-3':'oklch(0.62 0.01 60)',
    '--brand':'oklch(0.62 0.19 38)','--brand-tint':'oklch(0.95 0.035 45)','--brand-ink':'oklch(0.5 0.17 38)',
    '--ok':'oklch(0.56 0.14 155)','--ok-tint':'oklch(0.95 0.04 155)','--warn':'oklch(0.72 0.15 75)','--warn-ink':'oklch(0.5 0.12 70)','--warn-tint':'oklch(0.96 0.05 85)',
    '--bad':'oklch(0.57 0.2 25)','--bad-tint':'oklch(0.95 0.035 25)','--info':'oklch(0.55 0.13 245)','--info-tint':'oklch(0.95 0.03 245)',
    '--inverse':'oklch(0.24 0.012 60)','--inverse-2':'oklch(0.31 0.012 60)','--on-inverse':'oklch(0.97 0.005 75)','--on-inverse-2':'oklch(0.78 0.01 70)',
    '--coach':'oklch(0.78 0.14 70)','--analyst':'oklch(0.76 0.11 230)',
    '--shadow':'0 1px 2px oklch(0.3 0.02 60 / 0.06), 0 6px 20px oklch(0.3 0.02 60 / 0.07)',
    '--role1-bg':'oklch(0.93 0.04 70)','--role1-ink':'oklch(0.4 0.09 70)','--role2-bg':'oklch(0.93 0.04 250)','--role2-ink':'oklch(0.4 0.1 250)',
    '--role3-bg':'oklch(0.93 0.04 150)','--role3-ink':'oklch(0.4 0.09 150)','--role4-bg':'oklch(0.93 0.04 320)','--role4-ink':'oklch(0.42 0.1 320)'
  };
  const DARK = {
    '--bg':'oklch(0.17 0.008 60)','--surface':'oklch(0.215 0.009 60)','--surface-2':'oklch(0.26 0.01 60)','--line':'oklch(0.32 0.01 60)',
    '--ink':'oklch(0.96 0.006 75)','--ink-2':'oklch(0.76 0.01 70)','--ink-3':'oklch(0.55 0.01 70)',
    '--brand':'oklch(0.66 0.18 38)','--brand-tint':'oklch(0.3 0.06 40)','--brand-ink':'oklch(0.8 0.12 40)',
    '--ok':'oklch(0.72 0.14 155)','--ok-tint':'oklch(0.28 0.05 155)','--warn':'oklch(0.78 0.14 75)','--warn-ink':'oklch(0.85 0.12 80)','--warn-tint':'oklch(0.3 0.05 80)',
    '--bad':'oklch(0.68 0.19 25)','--bad-tint':'oklch(0.29 0.06 25)','--info':'oklch(0.74 0.11 245)','--info-tint':'oklch(0.28 0.05 245)',
    '--inverse':'oklch(0.28 0.012 60)','--inverse-2':'oklch(0.35 0.012 60)','--on-inverse':'oklch(0.97 0.005 75)','--on-inverse-2':'oklch(0.78 0.01 70)',
    '--coach':'oklch(0.78 0.14 70)','--analyst':'oklch(0.76 0.11 230)',
    '--shadow':'0 1px 2px oklch(0 0 0 / 0.3), 0 6px 20px oklch(0 0 0 / 0.25)',
    '--role1-bg':'oklch(0.32 0.06 70)','--role1-ink':'oklch(0.9 0.07 70)','--role2-bg':'oklch(0.32 0.06 250)','--role2-ink':'oklch(0.88 0.07 250)',
    '--role3-bg':'oklch(0.32 0.06 150)','--role3-ink':'oklch(0.88 0.07 150)','--role4-bg':'oklch(0.32 0.06 320)','--role4-ink':'oklch(0.88 0.07 320)'
  };
  function applyTheme(el, mode, hue) {
    if (!el) return;
    const v = Object.assign({}, mode === 'dark' ? DARK : LIGHT, (mode === 'dark' ? D : L)(hue ?? 195));
    for (const k in v) el.style.setProperty(k, v[k]);
  }
  function watchWidth(el, cb) {
    if (!el || !window.ResizeObserver) return () => {};
    const ro = new ResizeObserver((e) => { const w = e[0].contentRect.width; cb(w < 640 ? 'phone' : w < 1100 ? 'tablet' : 'desktop', w); });
    ro.observe(el); return () => ro.disconnect();
  }

  const fmt = (p) => p == null ? '' : p >= 100 ? '₹' + (p / 100).toFixed(2) + ' Cr' : '₹' + p + ' L';
  const fmtShort = (p) => p >= 100 ? '₹' + (Math.round(p / 10) / 10).toFixed(1) + ' Cr' : '₹' + p + ' L';
  const inc = (p) => p < 100 ? 5 : p < 200 ? 10 : p < 500 ? 20 : 25;
  const ROLES = ['WK', 'BAT', 'BOWL', 'AR'];
  const ROLE_SLOT = { BAT: 1, BOWL: 2, AR: 3, WK: 4 };
  const MINS = { WK: 2, BAT: 5, BOWL: 5, AR: 3 };
  const SQUAD_MIN = 18, SQUAD_MAX = 25, OS_MAX = 8, PURSE = 12000, MIN_BASE = 30;
  const T_OPEN = 30000, T_BID = 15000;

  const TEAMS = [
    { id: 'MR', name: 'Monsoon Riders', mgr: 'You', hue: 195, me: true },
    { id: 'CC', name: 'Chai Chargers', mgr: 'Priya', hue: 45, purse: 3820, squad: 12, need: { WK: 1, BAT: 0, BOWL: 1, AR: 0 }, os: 6 },
    { id: 'BB', name: 'Bandra Blasters', mgr: 'Arjun', hue: 15, purse: 5150, squad: 10, need: { WK: 1, BAT: 1, BOWL: 3, AR: 1 }, os: 4 },
    { id: 'GG', name: 'Ghat Gliders', mgr: 'Kabir', hue: 135, purse: 2240, squad: 14, need: { WK: 0, BAT: 0, BOWL: 1, AR: 1 }, os: 7 },
    { id: 'HH', name: 'Hilltop Howlers', mgr: 'Meera', hue: 315, purse: 4710, squad: 9, need: { WK: 1, BAT: 2, BOWL: 2, AR: 2 }, os: 3 },
    { id: 'SS', name: 'Spice Strikers', mgr: 'Rohan', hue: 255, purse: 3390, squad: 12, need: { WK: 1, BAT: 0, BOWL: 2, AR: 0 }, os: 8 },
    { id: 'DD', name: 'Deccan Dynamos', mgr: 'Zoya', hue: 285, purse: 4480, squad: 11, need: { WK: 2, BAT: 1, BOWL: 0, AR: 1 }, os: 5, offline: true },
    { id: 'KK', name: 'Kochi Kites', mgr: 'Dev', hue: 75, purse: 2970, squad: 13, need: { WK: 0, BAT: 1, BOWL: 1, AR: 1 }, os: 6 }
  ];
  const MY_SQUAD = [
    { name: 'Kiran Desai', role: 'BAT', price: 1150, tags: 'Capped' }, { name: 'Leo Fairbank', role: 'BAT', price: 875, tags: 'Overseas' },
    { name: 'Aditya Rao', role: 'BAT', price: 640, tags: 'Capped' }, { name: 'Ben Hartley', role: 'BAT', price: 210, tags: 'Overseas' },
    { name: 'Tomás Reyes', role: 'BAT', price: 520, tags: 'Overseas' }, { name: 'Varun Iyer', role: 'BAT', price: 95, tags: 'Uncapped' },
    { name: 'Callum Webb', role: 'BOWL', price: 1025, tags: 'Overseas' }, { name: 'Harshit Malhotra', role: 'BOWL', price: 480, tags: 'Capped' },
    { name: 'Imran Siddiqui', role: 'BOWL', price: 165, tags: 'Uncapped' }, { name: 'Dwayne Pollard', role: 'AR', price: 1310, tags: 'Overseas' },
    { name: 'Shreyas Gaitonde', role: 'AR', price: 300, tags: 'Capped' }
  ];
  const LOTS = [
    { name: 'Jaden Okoro', country: 'South Africa', style: 'Right-arm fast', role: 'BOWL', tags: ['Overseas', 'Capped'], base: 200, last: 612, m: 14, set: 'Set 3 · Fast bowlers', cap: 960, fans: ['CC', 'BB', 'HH', 'SS'],
      fit: { ceil: 900, delta: '+31 pts', value: '74 pts / Cr' }, ai: {
        coach: ['GO', 'ok', 'Death-over pace is the hardest thing to buy later, and you have none. Get him.'],
        analyst: ['STOP AT ₹9 CR', 'warn', 'Above ₹9 Cr his value drops below your squad average, and he costs an overseas slot.'],
        bottom: 'Bid, but walk away past ₹9 Cr.' } },
    { name: 'Ravi Teja Nair', country: 'India', style: 'Keeper-batter', role: 'WK', tags: ['Capped'], base: 150, last: 488, m: 13, set: 'Set 4 · Wicketkeepers', cap: 720, fans: ['CC', 'BB', 'DD', 'SS', 'HH'],
      fit: { ceil: 820, delta: '+27 pts', value: '66 pts / Cr' }, ai: {
        coach: ['GO', 'ok', 'You need two keepers and this is the one who also bats in the top four. Scarcity is real.'],
        analyst: ['GO TO ₹8.2 CR', 'ok', 'Fills a role minimum you can\'t skip. Paying a premium here is cheaper than a forced buy later.'],
        bottom: 'Strong buy. Push to ₹8.2 Cr.' } },
    { name: 'Mitchell Harrow', country: 'Australia', style: 'Batting all-rounder', role: 'AR', tags: ['Overseas', 'Capped'], base: 200, last: 540, m: 12, set: 'Set 5 · All-rounders', cap: 1180, fans: ['BB', 'HH', 'KK'],
      fit: { ceil: 700, delta: '+18 pts', value: '51 pts / Cr' }, ai: {
        coach: ['MAYBE', 'warn', 'Useful, but you already have Pollard doing this job. He\'d be a luxury.'],
        analyst: ['NO-GO', 'bad', 'Burns overseas slot 6 for a modest gain. Your WK gap matters more.'],
        bottom: 'Let this one go unless it stalls near base.' } },
    { name: 'Arnav Kulkarni', country: 'India', style: 'Top-order batter', role: 'BAT', tags: ['Uncapped'], base: 30, last: 0, m: 0, set: 'Set 6 · Uncapped', cap: 140, fans: ['GG', 'KK', 'CC'],
      fit: { ceil: 120, delta: '+9 pts', value: '82 pts / Cr' }, ai: {
        coach: ['GO', 'ok', 'Cheap depth from a domestic standout. Low risk at this price.'],
        analyst: ['GO TO ₹1.2 CR', 'ok', 'Fills a squad slot cheaply and protects your reserve.'],
        bottom: 'Cheap filler. Go up to ₹1.2 Cr.' } },
    { name: 'Sami Qadir', country: 'Afghanistan', style: 'Leg-spin', role: 'BOWL', tags: ['Overseas', 'Capped'], base: 150, last: 571, m: 14, set: 'Set 7 · Spinners', cap: 1050, fans: ['CC', 'SS', 'BB', 'GG'],
      fit: { ceil: 950, delta: '+29 pts', value: '70 pts / Cr' }, ai: {
        coach: ['GO', 'ok', 'Wicket-taker in the middle overs on any surface. Pairs well with Webb.'],
        analyst: ['STOP AT ₹9.5 CR', 'warn', 'Good value to ₹9.5 Cr. Past that, the overseas slot costs more than he adds.'],
        bottom: 'Go, ceiling ₹9.5 Cr.' } }
  ];
  const LIFELINES = [
    { type: 'fit', label: 'Fit Check', icon: 'fact_check', q: 'Go/No-Go?' },
    { type: 'ceil', label: 'Price Ceiling', icon: 'vertical_align_top', q: 'My max?' },
    { type: 'scan', label: 'Rival Scan', icon: 'radar', q: 'Who wants him?' }
  ];
  const VERDICT_BG = { ok: 'var(--ok)', warn: 'var(--warn)', bad: 'var(--bad)' };
  const VERDICT_INK = { ok: '#fff', warn: 'oklch(0.25 0.05 70)', bad: '#fff' };

  function create(opts, onChange) {
    const scene = opts.scene || 'live';
    const teams = TEAMS.map((t) => Object.assign({}, t, t.need ? { need: Object.assign({}, t.need) } : {}));
    const me = { squad: MY_SQUAD.slice(), need: { WK: 2, BAT: 0, BOWL: 2, AR: 1 }, os: 5 };
    me.purse = PURSE - me.squad.reduce((a, p) => a + p.price, 0);
    const s = {
      scene, lotIdx: 0, lotN: 42, bid: null, leader: null, feed: [], endsAt: Date.now() + T_OPEN, frozenLeft: null,
      phase: 'open', pending: false, toast: null, nextRivalAt: Date.now() + 2500, life: { fit: 1, ceil: 1, scan: 1 }, used: {}, refunded: {},
      session: null, rtm: null, connected: true, myLast: null
    };
    const now = () => Date.now();
    const lot = () => LOTS[s.lotIdx % LOTS.length];
    const team = (id) => teams.find((t) => t.id === id);
    const nextAmt = () => s.bid == null ? lot().base : s.bid + inc(s.bid);
    function guard(amount) {
      const l = lot(); const need = Object.assign({}, me.need); if (need[l.role] > 0) need[l.role]--;
      const sumNeed = ROLES.reduce((a, r) => a + need[r], 0);
      const slots = Math.max(SQUAD_MIN - (me.squad.length + 1), sumNeed);
      const reserve = slots * MIN_BASE; const max = me.purse - reserve;
      if (l.tags.includes('Overseas') && me.os >= OS_MAX) return { ok: false, max, reason: 'Overseas slots full (8/8)' };
      if (me.squad.length >= SQUAD_MAX) return { ok: false, max, reason: 'Squad is full (25/25)' };
      if (amount > max) {
        const parts = ROLES.filter((r) => need[r] > 0).map((r) => need[r] + ' ' + r); const extra = slots - sumNeed; if (extra > 0) parts.push(extra + ' more');
        return { ok: false, max, reason: `You'd be ${fmt(amount - max)} short of filling ${parts.join(' + ')} at base` };
      }
      return { ok: true, max };
    }
    function toast(kind, title, body, sticky) { s.toast = { kind, title, body, at: now(), sticky: !!sticky }; }
    function push(id, amt) { s.feed.unshift({ id, amt, at: now() }); s.feed = s.feed.slice(0, 12); }
    function placeBid(id, amt) {
      const wasMe = s.leader === 'MR';
      s.bid = amt; s.leader = id; push(id, amt); s.endsAt = now() + T_BID;
      if (id !== 'MR' && wasMe) { const t = team(id); toast('outbid', 'Outbid!', `${t.name} went ${fmt(amt)}. Rude.`); }
    }
    function openLot() {
      s.lotIdx++; s.lotN++; s.bid = null; s.leader = null; s.feed = []; s.phase = 'open'; s.endsAt = now() + T_OPEN;
      s.nextRivalAt = now() + 2000 + Math.random() * 2500; s.session = null; s.rtm = null; s.myLast = null;
    }
    function resolve() {
      if (s.session) s.session.closed = true;
      if (s.leader) {
        s.phase = 'sold';
        if (s.leader === 'MR') {
          const l = lot(); me.squad.push({ name: l.name, role: l.role, price: s.bid, tags: l.tags.join(' · ') });
          me.purse -= s.bid; if (me.need[l.role] > 0) me.need[l.role]--; if (l.tags.includes('Overseas')) me.os++;
          toast('won', 'He\'s yours', `${l.name} joins Monsoon Riders for ${fmt(s.bid)}.`);
        } else { const t = team(s.leader); t.purse -= s.bid; t.squad++; }
      } else s.phase = 'unsold';
      s.resultUntil = now() + 3500;
    }
    // scenes
    const frozen = scene !== 'live';
    if (scene === 'outbid') { s.feed = []; placeBid('BB', 800); placeBid('MR', 840); placeBid('CC', 860); s.frozenLeft = 12000; if (s.toast) s.toast.sticky = true; }
    if (scene === 'rejected') { me.purse = 900; placeBid('CC', 800); placeBid('BB', 820); s.frozenLeft = 11000; const g = guard(nextAmt()); toast('rejected', 'Bid bounced', g.reason + '.', true); }
    if (scene === 'rtm') { placeBid('CC', 900); placeBid('MR', 980); s.phase = 'rtm'; s.rtm = { team: 'BB', stage: 'raise', raise: 1005 }; s.frozenLeft = 21000; }
    if (scene === 'paused') { placeBid('CC', 760); placeBid('SS', 780); s.phase = 'paused'; s.frozenLeft = 9000; }
    if (scene === 'adjourned') { s.phase = 'adjourned'; s.lotN = 87; s.frozenLeft = 30000; }
    if (scene === 'reconnecting') { placeBid('CC', 760); placeBid('SS', 780); s.connected = false; s.frozenLeft = 7000; }
    if (scene === 'sold') { placeBid('BB', 900); placeBid('CC', 980); s.phase = 'sold'; s.frozenLeft = 0; }
    if (scene === 'unsold') { s.phase = 'unsold'; s.frozenLeft = 0; }
    if (scene === 'advisor' || scene === 'advisorFail') {
      placeBid('CC', 640); placeBid('HH', 660); s.frozenLeft = 10000; s.life.fit = 0; s.used.fit = true;
      s.session = { type: 'fit', at: now(), coachN: 999, anN: 999, failed: scene === 'advisorFail', follow: [] };
      if (scene === 'advisorFail') { s.life.fit = 1; s.refunded.fit = true; s.used.fit = false; }
    }
    if (scene === 'live') { s.endsAt = now() + 22000; s.nextRivalAt = now() + 1200; }

    function step() {
      const t = now();
      if (s.toast && !s.toast.sticky && t - s.toast.at > 4000) s.toast = null;
      if (s.session && !s.session.failed) {
        const l = lot(); const el = t - s.session.at;
        if (el > 500) s.session.coachN = Math.min(999, Math.floor((el - 500) / 18));
        if (el > 500 + l.ai.coach[2].length * 18 + 300) s.session.anN = Math.min(999, Math.floor((el - 800 - l.ai.coach[2].length * 18) / 18));
      }
      if (frozen) return;
      if (s.phase === 'sold' || s.phase === 'unsold') { if (t > s.resultUntil) openLot(); return; }
      if (s.phase !== 'open') return;
      if (t >= s.nextRivalAt) {
        const l = lot(); const amt = nextAmt();
        const pool = l.fans.filter((id) => id !== s.leader && !team(id).offline);
        if (amt <= l.cap && pool.length && Math.random() < 0.85) placeBid(pool[Math.floor(Math.random() * pool.length)], amt);
        s.nextRivalAt = t + 1400 + Math.random() * 3800;
      }
      if (t >= s.endsAt) resolve();
    }
    const iv = setInterval(() => { step(); onChange(view()); }, 120);

    function view() {
      const l = lot(); const t = now();
      const left = s.frozenLeft != null ? s.frozenLeft : Math.max(0, s.endsAt - t);
      const total = s.bid == null ? T_OPEN : T_BID;
      const secs = Math.ceil(left / 1000);
      const nxt = nextAmt(); const g = guard(nxt); const leaderT = s.leader ? team(s.leader) : null;
      const blocked = !s.connected || s.phase === 'paused' || s.phase === 'adjourned' || s.phase === 'rtm';
      const resolved = s.phase === 'sold' || s.phase === 'unsold';
      let bidState = 'ready';
      if (resolved) bidState = 'resolved'; else if (blocked) bidState = 'blocked'; else if (s.pending) bidState = 'sending';
      else if (s.leader === 'MR') bidState = 'top'; else if (!g.ok) bidState = 'locked';
      const blockLabel = !s.connected ? 'Reconnecting…' : s.phase === 'paused' ? 'Auction paused' : s.phase === 'adjourned' ? 'Adjourned' : s.phase === 'rtm' ? 'RTM decision in progress' : '';
      const spent = PURSE - me.purse; const reserve = Math.max(0, me.purse - Math.max(0, g.max));
      const roles = ROLES.map((r) => { const have = me.squad.filter((p) => p.role === r).length; const met = have >= MINS[r];
        return { label: r, have, min: MINS[r], met, text: have + '/' + MINS[r], short: !met, bg: met ? 'var(--ok-tint)' : have === 0 ? 'var(--bad-tint)' : 'var(--surface-2)', ink: met ? 'var(--ok)' : have === 0 ? 'var(--bad)' : 'var(--ink)' }; });
      const sess = s.session; let session = null;
      if (sess) {
        const ll = LIFELINES.find((x) => x.type === sess.type);
        const c = l.ai.coach, a = l.ai.analyst;
        const maxSafe = Math.max(0, g.max);
        session = {
          label: ll.label, icon: ll.icon, failed: !!sess.failed, closed: !!sess.closed, live: !sess.failed && !sess.closed,
          fit: [
            { k: 'MAX SAFE BID', v: fmt(maxSafe) }, { k: 'PRICE CEILING', v: fmt(l.fit.ceil) }, { k: 'SQUAD Δ / MATCH', v: l.fit.delta }, { k: 'VALUE', v: l.fit.value }
          ],
          notes: [
            l.tags.includes('Overseas') ? { icon: 'warning', color: 'var(--warn)', text: `Burns overseas slot ${me.os + 1} of ${OS_MAX}` } : { icon: 'check_circle', color: 'var(--ok)', text: 'No overseas slot used' },
            me.need[l.role] > 0 ? { icon: 'check_circle', color: 'var(--ok)', text: `Fills ${l.role} minimum (${MINS[l.role] - me.need[l.role]}/${MINS[l.role]} → ${MINS[l.role] - me.need[l.role] + 1}/${MINS[l.role]})` } : { icon: 'info', color: 'var(--on-inverse-2)', text: `${l.role} minimum already met` },
            { icon: 'info', color: 'var(--on-inverse-2)', text: `${fmt(Math.round(me.purse / Math.max(1, SQUAD_MIN - me.squad.length)))} per remaining required slot` }
          ],
          coachText: c[2].slice(0, sess.coachN || 0), coachVerdict: c[0], coachBg: VERDICT_BG[c[1]], coachInk: VERDICT_INK[c[1]],
          coachThinking: !sess.failed && (sess.coachN || 0) === 0, coachShown: !sess.failed && (sess.coachN || 0) > 0, coachDone: (sess.coachN || 0) >= c[2].length,
          anText: a[2].slice(0, sess.anN || 0), anVerdict: a[0], anBg: VERDICT_BG[a[1]], anInk: VERDICT_INK[a[1]],
          anThinking: !sess.failed && (sess.coachN || 0) >= c[2].length && (sess.anN || 0) === 0, anShown: !sess.failed && (sess.anN || 0) > 0,
          bottom: l.ai.bottom, bottomShown: !sess.failed && (sess.anN || 0) >= a[2].length,
          intents: [{ q: 'Go/No-Go?' }, { q: 'My max?' }, { q: `Stop at ${fmtShort(l.fit.ceil + 100)}?` }],
          follow: sess.follow
        };
      }
      const rivals = teams.filter((x) => !x.me).map((x) => ({
        id: x.id, name: x.name, mgr: x.mgr, hue: x.hue, bg: `oklch(0.56 0.15 ${x.hue})`, purse: fmt(x.purse), purseShort: fmtShort(x.purse), squad: x.squad, os: x.os,
        needs: ROLES.filter((r) => x.need[r] > 0).map((r) => x.need[r] + ' ' + r).join(' · ') || 'All minimums met',
        leading: s.leader === x.id, rowBg: s.leader === x.id ? 'var(--surface-2)' : 'transparent', offline: !!x.offline, opacity: x.offline ? '0.4' : '1', lift: s.leader === x.id ? '-10px' : '0px',
        ring: s.leader === x.id ? `0 0 0 3px var(--surface), 0 0 0 5px oklch(0.56 0.15 ${x.hue})` : 'none',
        wantsRole: x.need[l.role] > 0
      }));
      const feed = s.feed.map((f, i) => { const tm = team(f.id); return { id: f.id, name: tm.me ? 'You' : tm.name, bg: `oklch(0.56 0.15 ${tm.hue})`, amt: fmt(f.amt), ago: i === 0 ? 'now' : Math.max(1, Math.round((t - f.at) / 1000)) + 's', isMe: !!tm.me, first: i === 0, rowBg: tm.me ? 'var(--team-tint)' : 'transparent', amtInk: i === 0 ? 'var(--ink)' : 'var(--ink-2)' }; });
      const osSlots = Array.from({ length: OS_MAX }, (_, i) => ({ bg: i < me.os ? 'var(--team)' : 'var(--team-tint-2)' }));
      const toastV = s.toast ? Object.assign({ show: true }, s.toast, {
        isOutbid: s.toast.kind === 'outbid', isRejected: s.toast.kind === 'rejected', isWon: s.toast.kind === 'won', isRefund: s.toast.kind === 'refund',
        leaderBg: leaderT ? `oklch(0.56 0.15 ${leaderT.hue})` : 'var(--ink)', leaderId: leaderT ? leaderT.id : ''
      }) : { show: false };
      const rtm = s.rtm ? { team: team(s.rtm.team).name, id: s.rtm.team, bg: `oklch(0.56 0.15 ${team(s.rtm.team).hue})`, amount: fmt(s.bid), raise: fmt(s.rtm.raise), time: '0:' + String(secs).padStart(2, '0'), stage: s.rtm.stage, isRaise: s.rtm.stage === 'raise', isWaiting: s.rtm.stage === 'waiting', isDone: s.rtm.stage === 'done', result: s.rtm.result } : null;
      const urgent = !resolved && !blocked && secs <= 5;
      return {
        lot: { n: s.lotN, name: l.name, country: l.country, style: l.style, role: l.role, roleBg: `var(--role${ROLE_SLOT[l.role]}-bg)`, roleInk: `var(--role${ROLE_SLOT[l.role]}-ink)`, tags: l.tags.map((x) => ({ t: x })), base: fmt(l.base), last: l.last ? l.last + ' pts' : 'New', matches: l.m || '–', set: l.set.toUpperCase(), setRaw: l.set },
        hasBid: s.bid != null, bid: s.bid != null ? fmt(s.bid) : fmt(l.base), bidLabel: s.bid != null ? 'CURRENT BID' : 'OPENS AT',
        leaderName: leaderT ? (leaderT.me ? 'You' : leaderT.name) : 'No bids yet', leaderId: leaderT ? leaderT.id : '', leaderBg: leaderT ? `oklch(0.56 0.15 ${leaderT.hue})` : 'var(--surface-2)', leaderIsMe: s.leader === 'MR',
        next: fmt(nxt), incr: '+' + (s.bid == null ? 'base' : fmt(inc(s.bid))),
        time: '0:' + String(secs).padStart(2, '0'), timePct: Math.max(0, Math.min(100, (left / total) * 100)).toFixed(1) + '%', urgent,
        timerColor: urgent ? 'var(--bad)' : blocked ? 'var(--ink-3)' : 'var(--ink)', barColor: urgent ? 'var(--bad)' : 'var(--team)', barTrack: urgent ? 'var(--bad-tint)' : 'var(--team-tint-2)',
        ring: `conic-gradient(${urgent ? 'var(--bad)' : blocked ? 'var(--ink-3)' : 'var(--team)'} ${Math.max(0, Math.min(100, (left / total) * 100)).toFixed(1)}%, ${urgent ? 'var(--bad-tint)' : 'var(--team-tint-2)'} 0)`,
        timerNote: blocked ? (s.phase === 'paused' ? 'PAUSED' : s.phase === 'rtm' ? 'RTM HOLD' : 'WAITING') : urgent ? 'GOING…' : s.bid == null ? 'opening clock' : 'resets to 15s',
        isOpen: s.phase === 'open', isSold: s.phase === 'sold', isUnsold: s.phase === 'unsold', isResolved: resolved, isPaused: s.phase === 'paused', isRtm: s.phase === 'rtm', isAdjourned: s.phase === 'adjourned', isOffline: !s.connected, showLot: s.phase !== 'adjourned',
        soldTo: leaderT ? (leaderT.me ? 'You' : leaderT.name) : '', soldBg: leaderT ? `oklch(0.56 0.15 ${leaderT.hue})` : '', soldId: leaderT ? leaderT.id : '',
        bidReady: bidState === 'ready', bidTop: bidState === 'top', bidSending: bidState === 'sending', bidLocked: bidState === 'locked', bidBlocked: bidState === 'blocked', bidResolved: bidState === 'resolved',
        lockReason: g.reason || '', blockLabel,
        me: { purse: fmt(me.purse), purseShort: fmtShort(me.purse), max: fmt(Math.max(0, g.max)), squad: me.squad.length, squadRange: SQUAD_MIN + '–' + SQUAD_MAX, os: me.os + '/' + OS_MAX,
          spentPct: (spent / PURSE * 100).toFixed(1) + '%', reservePct: (reserve / PURSE * 100).toFixed(1) + '%', roles, osSlots,
          players: me.squad.map((p) => ({ name: p.name, role: p.role, price: fmt(p.price), tags: p.tags, roleBg: `var(--role${ROLE_SLOT[p.role]}-bg)`, roleInk: `var(--role${ROLE_SLOT[p.role]}-ink)` })).reverse() },
        rivals, feed, hasFeed: feed.length > 0, toast: toastV, rtm, session, hasSession: !!session,
        lifelines: LIFELINES.map((x) => ({ type: x.type, label: x.label, icon: x.icon, count: s.life[x.type], used: !!s.used[x.type] && s.life[x.type] === 0, refunded: !!s.refunded[x.type], available: s.life[x.type] > 0, active: !!(sess && sess.type === x.type && !sess.closed),
          opacity: s.life[x.type] > 0 || (sess && sess.type === x.type && !sess.closed) ? '1' : '0.45', strike: s.life[x.type] > 0 ? 'none' : 'line-through',
          status: sess && sess.type === x.type && !sess.closed ? 'SESSION OPEN' : s.refunded[x.type] ? 'Refunded · ' + s.life[x.type] + ' left' : s.life[x.type] > 0 ? s.life[x.type] + ' left' : 'Used' })),
        lifelinesLeft: s.life.fit + s.life.ceil + s.life.scan
      };
    }
    const api = {
      view,
      bid() {
        if (s.pending || s.leader === 'MR' || s.phase !== 'open' || !s.connected) return;
        const amt = nextAmt(); const g = guard(amt);
        if (!g.ok) { toast('rejected', 'Bid bounced', g.reason + '.'); onChange(view()); return; }
        s.pending = true; onChange(view());
        setTimeout(() => { s.pending = false; if (s.phase === 'open') { placeBid('MR', amt); s.toast = null; s.frozenLeft = frozen ? 15000 : null; s.nextRivalAt = now() + 1500 + Math.random() * 3000; } onChange(view()); }, 320);
      },
      useLifeline(type) {
        if (s.phase !== 'open' && s.phase !== 'rtm') return;
        if (s.session && s.session.type === type && !s.session.closed) return;
        if (s.life[type] <= 0) return;
        s.life[type]--; s.used[type] = true; s.refunded[type] = false;
        s.session = { type, at: now(), coachN: 0, anN: 0, follow: [] }; onChange(view());
      },
      failSession() { if (!s.session) return; s.session.failed = true; s.life[s.session.type]++; s.refunded[s.session.type] = true; s.used[s.session.type] = false; toast('refund', 'Lifeline refunded', 'The table fumbled. It\'s back in your pocket.'); onChange(view()); },
      follow(q) {
        if (!s.session || s.session.closed || s.session.failed) return; const l = lot(); const g = guard(nextAmt());
        const a = /max/i.test(q) ? `Analyst: ${fmt(l.fit.ceil)} for value. Your hard limit is ${fmt(Math.max(0, g.max))}.` : /stop/i.test(q) ? `Analyst: Yes. Past ${fmt(l.fit.ceil)} you're paying for the name.` : `Bottom line: ${l.ai.bottom}`;
        s.session.follow.push({ q, a }); onChange(view());
      },
      rtmRaise() { if (!s.rtm) return; s.bid = s.rtm.raise; push('MR', s.bid); s.rtm.stage = 'waiting'; onChange(view()); setTimeout(() => { s.rtm.stage = 'done'; s.rtm.result = 'Bandra Blasters declined. He\'s yours at ' + fmt(s.bid) + '.'; onChange(view()); }, 2200); },
      rtmDecline() { if (!s.rtm) return; s.rtm.stage = 'done'; s.rtm.result = 'You held at ' + fmt(s.bid) + '. Bandra Blasters matched and keep him.'; onChange(view()); },
      rtmStep(d) { if (!s.rtm) return; s.rtm.raise = Math.max(s.bid + inc(s.bid), s.rtm.raise + d * inc(s.rtm.raise)); onChange(view()); },
      dismissToast() { s.toast = null; onChange(view()); },
      reconnect() { s.connected = true; onChange(view()); },
      destroy() { clearInterval(iv); }
    };
    return api;
  }
  window.PaddleSim = { create, applyTheme, watchWidth, fmt, TEAMS, LIFELINES };
})();
