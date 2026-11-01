# PaddleUp — Product Requirements Document

**Version:** 1.1 | **Date:** 2026-11-01 | **Author:** Shane Rex (with Claude) | **Status:** Draft

> **Name note:** "PaddleUp" comes from the paddle bidders raise at a live auction. It is sport-agnostic by design. Verify trademark and domain availability before any public launch. (Previously "GavelXI".)

> **Changes in 1.1:** renamed to PaddleUp. Decoupled the core from any single sport (Sport Packs, §4.12; cricket specifics moved to §7). Added role minimums to the auction rules. Added the AI Advisor ("Team Table") and Auction Report Card (§4.11).

---

## 1. Overview

### 1.1 Vision
PaddleUp lets groups of sports-loving friends run their own **franchise-style player auction** and then compete across a real competition season using a **configurable fantasy scoring system**. Unlike per-match fantasy apps, PaddleUp is a **season-long ownership league**: you auction a squad once, you live with it (mostly), and every real match moves your leaderboard.

The core is **sport-agnostic**. Everything sport-specific (roles, stats, scoring, data source, default rules) lives in a pluggable **Sport Pack**. v1 ships one pack: **Cricket T20** (IPL-style, see §7). Football, Basketball, Kabaddi and others are future packs.

An optional, deliberately rationed **AI "Team Table" advisor** mirrors the strategy huddle at a real franchise auction table. An **AI Auction Report Card** grades every squad once the hammer falls. Both are bounded so that human strategy decides the game.

### 1.2 Problem
Friend groups currently run these auctions on paper, WhatsApp, or Excel. Bid tracking is error-prone, budget math is manual, match-right disputes are common, remote friends can't participate fairly, and season-long point tallying is tedious.

### 1.3 Goals
- Run a fair, fun, app-moderated remote auction with real franchise-auction rules (purse, retentions, right-to-match, bid increments, timers, squad composition rules).
- Support private **rooms** so any group, anywhere in the world, can run its own league with its own rules.
- Automatically score real matches via a sport data provider against per-room fantasy rules, with manual override.
- Offer a limited AI advisor that explains Go/No-Go decisions using **only** the manager's own squad, the current lot, and public room state. It must never reveal upcoming lots. A post-auction AI report card makes the auction the screenshot-able, argue-about-it moment.
- Keep the core free of any sport so new Sport Packs ship without core changes.
- Work well on desktop web and as an installable PWA on mobile.

### 1.4 Non-goals
- No real-money gameplay, entry fees, or prizes (avoids fantasy-sports/gambling regulation).
- No public matchmaking or open lobbies. Rooms are invite-only.
- v1 ships only the Cricket T20 Sport Pack. Other sports are future packs (§8), and the core must not prevent them (NFR-12).
- No native app store distribution in v1 (PWA instead).
- Monetization of any kind.
- The AI never bids, never acts on a manager's behalf, and never replaces human decision-making.

### 1.5 Success criteria (hobby-project scale)
- One full auction completed remotely by ≥6 users without manual arbitration.
- Season scoring runs through a full real season with <1 missed match (manual override fills gaps).
- Friends actually check the leaderboard the morning after matches.
- Managers use their AI lifelines strategically (not all on the first lots), and the report card gets shared in the group chat.

---

## 2. Users & Personas

| Persona | Description | Key needs |
|---|---|---|
| **Commissioner** (room admin) | Creates the room, picks the Sport Pack, configures rules, schedules the auction, resolves disputes | Powerful config, override tools, audit trail |
| **Manager** (player/franchise owner) | Joins a room, retains players, bids in the auction, follows the season | Fast bidding UX, clear budget and composition state, a limited advisor at key moments, live points |
| **Spectator** (v2, optional) | Watches an auction or leaderboard without owning a team | Read-only room link |

Assume 6–12 managers per room, one room per friend group, users spread across time zones.

---

## 3. Core Concepts & Glossary

| Term | Definition |
|---|---|
| **Sport Pack** | Declarative module defining everything sport-specific: roles, player tags, restricted categories, room presets, stat schema, default scoring rules, appearance rule, data adapter, UI terminology, AI domain context (§4.12) |
| **Room** | A private league instance: its own Sport Pack, managers, rules, auction, and season leaderboard |
| **Season** | One real competition (a league season or a tournament) that a room plays against |
| **Franchise** | A manager's team within a room (name, logo/color, purse, squad) |
| **Purse** | A franchise's total budget for retentions + auction, in the room's currency and display unit (default from the Sport Pack) |
| **Player pool** | All real players available in the season, with base price, pack-defined role, pack-defined tags, and an external data-provider ID |
| **Role** | A pack-defined player position/type (e.g., Cricket pack: BAT/BOWL/AR/WK) |
| **Role minimum** | The minimum number of players of a role every squad must hold |
| **Restricted category** | A pack-defined player tag with a per-squad maximum (e.g., Cricket pack: overseas ≤ 8) |
| **Lot** | One player currently under the hammer |
| **Set** | Ordered grouping of lots (e.g., Marquee, then role-based sets) |
| **Retention** | Pre-auction keep of a player from the franchise's previous-season squad, at a rule-defined cost |
| **RTM (Right to Match)** | Card letting a player's previous franchise match the winning bid, with a final counter-raise by the winning bidder |
| **Accelerated round** | Late-auction round where unsold/skipped players return in bulk |
| **Scoring rule set** | Per-room mapping from pack-defined match stats to fantasy points |
| **Appearance bonus** | Points awarded to a player for being in the real starting lineup of a match (pack-defined) |
| **Mega vs Mini auction** | Mega = full pool re-auctioned (a room's first season is always mega). Mini = only new/released players (v2) |
| **Player Intel** | Per-player projections derived from historical stats scored with the room's rules (FR-120) |
| **Fit Engine** | Deterministic (non-AI) calculator that evaluates the current lot against a franchise's squad, purse and rules (FR-121) |
| **Lifeline** | A limited-use AI advisor ask. Typed: Fit Check, Price Ceiling, Rival Scan (FR-122) |
| **Advisor Session** | The window opened by spending a lifeline on a lot. Follow-ups are allowed until the lot fully resolves |
| **Head Coach / Analyst** | The two advisor voices: sport instinct vs numbers (FR-124) |
| **Report Card** | AI-generated, room-wide grading of every franchise's auction (FR-126) |

---

## 4. Functional Requirements

Priorities: **P0** = MVP must-have, **P1** = should-have before first real season, **P2** = later.

### 4.1 Accounts & Profiles
- **FR-1 (P0):** Email or OAuth sign-in; display name and avatar.
- **FR-2 (P0):** A user can belong to multiple rooms.
- **FR-3 (P2):** Basic profile stats (rooms won, auctions played).

### 4.2 Rooms & League Setup
- **FR-10 (P0):** Any user can create a room and becomes its Commissioner. The following are configurable at creation, prefilled from the selected Sport Pack's presets (FR-15):
  - room name, competition/season, min/max managers (default 4–10)
  - purse size, currency and display unit
  - bid increment ladder
  - squad size min/max
  - **role minimums**: the sum must be ≤ the squad minimum; validated on save
  - **restricted-category quotas**
  - retention rules, scoring rule set
  - AI settings: `ai_enabled` (default on); lifeline count per type (default 1 each, 0 disables that type); report card visibility (room-wide by default)
- **FR-11 (P0):** Invite via shareable code/link. Joining users create a franchise (name, color). Room locks when full or when the Commissioner starts the auction.
- **FR-12 (P0):** Commissioner can remove a manager before auction start, transfer commissionership, and edit config until auction lock.
- **FR-13 (P1):** Room states: `setup → retention → auction_scheduled → auction_live → season_live → completed`. State transitions are explicit and visible to all members.
- **FR-14 (P2):** Multiple seasons per room with carried-over squads (enables retention/mini-auction in year 2).
- **FR-15 (P0):** Sport Pack selection at room creation. The room copies the pack's presets as editable defaults. The pack cannot be changed after the auction is scheduled. v1 offers one pack (Cricket T20).

### 4.3 Player Pool
- **FR-20 (P0):** Each season has a player pool. Each player has: name, country, pack-defined role, pack-defined tags (which drive restricted categories and retention constraints), base price, auction set, and external data-provider player ID for stat mapping.
- **FR-21 (P0):** Pool is seeded from an admin-maintained dataset (CSV/JSON import, validated against the pack's role and tag schema). Commissioners can add/edit players in their room's copy before auction (fix errors, add missed players).
- **FR-22 (P1):** Set ordering editable by Commissioner (which sets go first).

### 4.4 Pre-Auction: Retentions & RTM Allocation
Retention and RTM are optional core mechanisms. A Sport Pack's presets decide whether they are on by default. A room's first season has no previous squads, so the retention phase is skipped and no RTM cards exist. From season 2 (or if the Commissioner manually assigns "previous squads" to mirror real teams):

- **FR-30 (P1):** Retention window: each franchise retains up to **R** players before the auction, subject to optional per-tag limits (e.g., Cricket pack: max 5 capped + 2 uncapped).
- **FR-31 (P1):** Retention costs deducted from purse. Two modes: **slab mode** (fixed costs per retention pick, optionally varying by tag; pack provides defaults) or **custom mode** (Commissioner sets costs).
- **FR-32 (P1):** RTM cards = R − (retentions used), automatically computed per franchise. Per-tag retention limits carry over to RTM eligibility (a franchise that used all its slots for one tag can only RTM a player with another tag).

### 4.5 Auction Engine (the core)
The server is the sole authority on auction state. Clients only send intents (bid, pass, use-RTM, use-lifeline); the server validates, sequences, and broadcasts.

**Lifecycle**
- **FR-40 (P0):** Commissioner schedules auction start time; managers see a lobby with countdown and readiness status.
- **FR-41 (P0):** Auction proceeds set by set, lot by lot. Each lot shows a player card (photo optional, role, tags, base price, last-season points if available) and the live bid state.
- **FR-42 (P0):** Bidding: any eligible franchise may bid the next increment. The increment ladder is configurable as tiered steps (increment size per price band); the Sport Pack provides the default. One-tap "bid next increment" is the primary action.
- **FR-43 (P0):** Timer: lot opens with **T_open** seconds (default 30); every bid resets the clock to **T_bid** seconds (default 15). At zero: highest bidder wins, or player goes unsold if no bids. The timer is never paused for AI advisor use.
- **FR-44 (P0):** Eligibility guard. A franchise cannot place a bid that, if won, would leave it unable to:
  - fill its **remaining role minimums** and the **squad minimum** at base prices with the purse left (the "purse minus remaining-slots reserve" rule, extended to roles);
  - stay within any **restricted-category quota**;
  - stay within the **squad maximum**.
- **FR-45 (P0):** Concurrency: simultaneous bids at the same increment are resolved by server arrival order; losers see "outbid" instantly. All state changes are monotonic and idempotent.
- **FR-46 (P0):** Unsold players enter the unsold pool; Commissioner can trigger accelerated round(s) where managers nominate players to bring back.
- **FR-47 (P0):** Commissioner controls: pause/resume auction, skip lot, re-open a mistakenly closed lot (with audit entry), adjourn and resume later (multi-day auctions).
- **FR-48 (P0):** Full **audit log**: every bid, pass, RTM, lifeline use, and admin action is timestamped and viewable by all managers. For lifelines, only the lifeline type, lot, and timestamp are logged, never advisor content (FR-129). This is the dispute-resolution mechanism.

**RTM flow (P1, ships with retention support)**
- **FR-49:** When a lot is won and the player's previous franchise holds an eligible RTM card, the server pauses the lot and offers RTM to that franchise (decision timer, default 30s). If invoked, the winning bidder gets **one final raise** (any amount ≥ next increment, single decision, timer-bound). The RTM franchise then matches the final amount or declines. Matching consumes the RTM card and deducts the final amount from the RTM franchise's purse.

**Resilience**
- **FR-50 (P0):** Disconnect handling: a dropped manager's franchise simply doesn't bid; on reconnect the client resyncs full auction state. No client-side state is trusted.
- **FR-51 (P1):** Optional proxy bid ("auto-bid up to X for this player") for managers in bad time zones. Off by default; Commissioner toggles per room.
- **FR-52 (P1):** Auction summary screen at close: every squad, spend, remaining purse, and validation report. Any franchise below the squad minimum or a role minimum is flagged for Commissioner resolution. Links to the Auction Report Card (FR-126).

### 4.6 Squad Management
- **FR-60 (P0):** Squad view per franchise: players, prices paid, roles, role-minimum progress, restricted-category counts, purse remaining.
- **FR-61 (P2):** Mid-season replacement rule (like injury replacements): Commissioner may allow adding an unsold-pool player using leftover purse. Off by default.
- **FR-62 (P2):** Trades between franchises with Commissioner approval.

### 4.7 Fantasy Scoring
**Rules engine**
- **FR-70 (P0):** Each room has a scoring rule set, editable by the Commissioner **until the first match starts**, then frozen. Changes after that require unanimous manager approval, enforced as a Commissioner action logged in the audit trail.
- **FR-71 (P0):** Rules are declarative config (stat → points), evaluated per player per match over the pack-defined stat schema. The engine supports these sport-agnostic primitives:
  - per-unit points
  - threshold/milestone bonuses
  - banded rates with a minimum-volume condition
  - role-conditional rules
  - event-type bonuses
  - the appearance bonus
- **FR-72 (P0):** Each Sport Pack ships a sensible **default rule set** (Cricket: §7.4). Rooms can use it as-is or clone and edit.
- **FR-73 (P2):** Multipliers/roles (e.g., each franchise names a captain per match for 2x). Off by default; adds per-match management burden.

**Data ingestion**
- **FR-80 (P0):** A nightly (and on-demand) job fetches completed-match results through the Sport Pack's data adapter, maps players via external ID, computes points per player per match, and updates standings. Recompute must be idempotent.
- **FR-81 (P0):** Manual override: Commissioner (or app admin) can enter/correct a full match stat sheet by hand, using a form generated from the pack's stat schema. Manual entries survive re-ingestion (source precedence: manual > provider).
- **FR-82 (P0):** Every player-match point total is drillable: leaderboard → franchise → player → match → stat-by-stat point breakdown. Transparency is the anti-dispute feature.
- **FR-83 (P1):** Unmapped-player alerts: if a match result contains a player the pool can't match, flag for admin resolution instead of silently dropping.
- **FR-84 (P2):** Live in-match scoring (live event feed) layered on the same rules engine, where the pack's adapter supports it.

### 4.8 Leaderboard & Season Views
- **FR-90 (P0):** Room standings: franchise rank, total points, points-per-match trend, movement vs. last match.
- **FR-91 (P0):** Match view: per real match, points earned by each franchise's players in that match.
- **FR-92 (P1):** Player leaderboard across the room (top point scorers, best value = points per purse unit spent).
- **FR-93 (P2):** Season awards auto-computed at completion (Best Auction Buy, Most Expensive Flop, etc.), including report card re-grades (FR-128). This is the fun screenshot-able ending.

### 4.9 Notifications & Engagement
- **FR-100 (P1):** Web push (PWA): auction starting soon, you were outbid (if not on the auction screen), post-match points summary, report card ready.
- **FR-101 (P2):** Auction-room chat / emoji reactions during bidding.

### 4.10 Platform & PWA
- **FR-110 (P0):** Responsive web app; auction screen usable one-handed on a phone.
- **FR-111 (P0):** Installable PWA (manifest + service worker); leaderboard and squads viewable from cache when offline; auction requires connectivity (show clear "reconnecting" state).
- **FR-112 (P1):** Auction screen optimized for speed: bid action reachable by thumb, current bid and purse always visible, no layout shift when bids arrive. Lifeline entry point never displaces the bid button.

### 4.11 AI Advisor ("Team Table") & Auction Report Card
Design principles: the AI advises and never acts. It sees only what a fair human advisor at your table could see. It is rationed so that strategy stays human. A deterministic engine does the math, and the LLM explains it.

**Data foundation**
- **FR-120 (P1) Player Intel.** Backfill 1–3 past seasons of match results through the pack's data adapter. Score them with the room's rule set (reuses FR-71/FR-80) to produce, per player: projected points per match, expected matches, appearance (starting-lineup) probability, and recent form. Refreshed each season.
- **FR-121 (P1) Fit Engine.** Deterministic, no LLM, returns in <100 ms.
  - Inputs: the franchise's squad and purse, the eligibility guard (FR-44), the room scoring rules, the current lot, and public rival state (rival squads and purses).
  - Outputs: max safe bid, value per purse unit, projected squad-points delta, real-team concentration risk, price ceiling, and **slot scarcity**. Slot scarcity covers: unmet role minimums vs slots remaining; restricted-category slots used/left (a low-impact player in a scarce category is flagged as burning a slot); purse per remaining required slot.
  - Scarcity uses only the franchise's own squad and room rules. It never uses counts of remaining pool players, which would violate FR-123.

**In-auction advisor**
- **FR-122 (P1) Typed lifelines.** Three types, each with a room-configurable count (default 1 each):
  - **Fit Check**: Go/No-Go plus why, judged on own squad and the current lot.
  - **Price Ceiling**: the max sensible bid for this lot given purse, reserve, and slot scarcity.
  - **Rival Scan**: which rivals need and can afford this player, from public squad/purse data only.

  Spending a lifeline on a lot opens an **Advisor Session** of that type. Within the session, unlimited follow-ups of that type are allowed until the lot fully resolves (hammer, plus any RTM decision and final raise per FR-49). Multiple lifeline types may be stacked on one lot. An unsold player returning in an accelerated round is a new lot. Lifeline spend is recorded as an auction event (NFR-4) and enforced server-side (NFR-1). A failed, timed-out, rate-limited, or invalid response refunds the lifeline.
- **FR-123 (P1) Information boundary.** Advisor context = own squad + current lot + room rules + public rival squads/purses. **Never** upcoming lots, the remaining pool, or set order. A server-side leak filter rejects any output that names a pool player who is not in the manager's squad, the current lot, or a rival squad. It then regenerates once or falls back to the Fit card. This guards against the model's own world knowledge of real players.
- **FR-124 (P1) Two-voice table.** Each advisor response is one structured (JSON) LLM call with two voices: **Head Coach** (sport instinct, form, role; uses the pack's AI domain context) and **Analyst** (numbers from the Fit Engine). Each voice gives its own verdict, so they may disagree, followed by a combined bottom line. Both voices obey FR-123.
- **FR-125 (P1) Speed UX.** The Fit card renders instantly and the voices stream in. Each lifeline type offers preset quick intents (e.g., "Go/No-Go?", "My max?", "Stop at X?"), plus optional short free text (length-capped). The bid timer is never paused for the advisor (FR-43).

**Post-auction**
- **FR-126 (P1) Auction Report Card.** Generated at auction close (extends FR-52). Grades every franchise A–F on projected points, value per purse unit, depth, role balance, and concentration risk. Names each franchise's best buy and biggest overpay, and gives a projected room rank. Visible room-wide by default (FR-10) and shareable as an image. Generated once per franchise and cached.
- **FR-127 (P1) Advisor accountability.** Each franchise's report card includes an "advice vs action" section (e.g., Cricket pack: "Analyst said stop at ₹8 Cr; you paid ₹12 Cr"). This section is **private to the owner by default**, and the owner may choose to share it.
- **FR-128 (P2) Re-grades.** The report card is re-run at mid-season and season end against actual points ("Auction grade B+ → actual rank 2nd"). Feeds season awards (FR-93).

**Guardrails & infrastructure**
- **FR-129 (P1) Privacy and audit.** Advisor content is private to its manager. The audit log records lifeline type, lot, and timestamp only (FR-48). Remaining lifeline counts are not shown to rivals.
- **FR-130 (P1) Safety.** Sanitize and length-limit all user/Commissioner-editable text (player names, franchise names, free-text questions) before it enters prompts. Pass data as delimited JSON. Validate output against a schema. The advisor has no tools and no ability to act (no bids, no state changes).
- **FR-131 (P1) LLM gateway.** Provider-agnostic, OpenAI-compatible client with configurable `base_url` and model per feature (small, fast model for the advisor; larger model for the report card). Default target: hosted open-weight model inference on a free or low-cost tier. A local runtime (e.g., Ollama) is supported for development and as a fallback. Per-room call cap; rate-limit errors count as failures (refund per FR-122). Prompts are assembled from a core template plus the Sport Pack's domain fragment.

### 4.12 Sport Pack Framework
- **FR-140 (P0) Pack contract.** A Sport Pack is versioned, declarative config + data and defines:
  - **Roles:** role list + labels.
  - **Player tags:** e.g., nationality-based or experience-based tags.
  - **Restricted categories:** tag + default per-squad max.
  - **Room presets:** purse, currency/unit, increment ladder, squad min/max, role minimums, retention/RTM defaults.
  - **Stat schema:** names, types, units.
  - **Default scoring rule set:** built from FR-71 primitives.
  - **Appearance rule.**
  - **Data adapter.**
  - **UI terminology:** e.g., lineup name, match unit, competition name.
  - **AI domain context:** Head Coach knowledge, stat glossary, fit heuristics.
- **FR-141 (P0) Pack loading.** Core code reads all sport semantics from the room's pack at runtime. No core module branches on sport identity.
- **FR-142 (P0) Data adapter interface.** One interface per pack: list fixtures, fetch completed-match stats in the pack's stat schema, map external player IDs. Manual entry (FR-81) uses the same schema.
- **FR-143 (P1) Pack validation.** Packs are validated on load: roles referenced in rules exist, role-minimum defaults fit the squad minimum, and scoring rules reference only schema stats.

---

## 5. Key User Flows

1. **Create a league:** Sign up → Create room → pick Sport Pack → adjust purse/squad/role minimums/scoring/AI lifelines (or accept pack defaults) → share invite link → friends join and name franchises → Commissioner schedules auction.
2. **Auction night:** Lobby countdown → sets proceed lot by lot → managers tap-bid, timer resets → *optional: "Ask the table." Spend a lifeline, see the instant Fit card, then Head Coach and Analyst stream in, with follow-ups until the lot resolves* → hammer falls → (RTM interrupt if applicable) → squad, purse, and role/category counters update live → accelerated round → summary + validation → **Report Card** published to the room.
3. **Season:** Real match ends → nightly ingestion → push summary → friends argue over the leaderboard → repeat across the season → (P2) mid-season and final report card re-grades → winner crowned, awards screen shared to the group chat.
4. **Dispute:** Manager questions a result → opens audit log (auction) or point breakdown (scoring) → self-serve resolution; Commissioner override as last resort.

---

## 6. Non-Functional Requirements

| # | Requirement |
|---|---|
| NFR-1 | **Server-authoritative auction state.** Clients render and send intents only; all validation (including lifeline balances) is server-side. No trust in client clocks or state. |
| NFR-2 | **Bid latency:** bid-to-broadcast under ~500 ms for a smooth remote auction; timer displayed from server timestamps so all clients agree. |
| NFR-3 | **Concurrency scale is small:** ≤12 bidders per room, few concurrent rooms. Design for correctness, not throughput. |
| NFR-4 | **Durability:** every auction event (including lifeline spend/refund) persisted before broadcast; a server restart mid-auction must resume from the last event without data loss. |
| NFR-5 | **Idempotent scoring:** re-running ingestion for any match yields identical totals; manual overrides always win. |
| NFR-6 | **Cost:** runnable on free/hobby tiers (~$0–10/month). The sport data provider must have a free tier covering a full season of match results. AI runs on hosted open-weight model inference (free or low-cost tier) with a per-room call cap; the expected load is a few hundred small calls per auction. |
| NFR-7 | **Security:** rooms private by default; invite codes revocable; only Commissioner can mutate config; rate-limit bid and advisor endpoints; prompt-injection hardening per FR-130. |
| NFR-8 | **Auditability:** append-only event log for auction and scoring adjustments. |
| NFR-9 | **Time zones:** all times stored UTC, rendered local; auction scheduling shows each member's local time. |
| NFR-10 | **Data source ToS:** use providers that permit hobby use; do not scrape sources that prohibit it. |
| NFR-11 | **AI is optional and failure-tolerant.** If the LLM is down, slow, or rate-limited, the auction is unaffected, the Fit card still works, and lifelines are refunded. Advisor responses start streaming within ~2 s. |
| NFR-12 | **Sport-agnostic core.** No core requirement, module, schema, or UI string hard-codes a sport, league, role, stat, or currency. Acceptance test: a stub second pack (e.g., a minimal football pack) loads and runs a scripted auction and scoring cycle in automated tests with zero core code changes. |

---

## 7. Sport Pack: Cricket T20 (v1)

The only pack shipped in v1. It targets IPL-style T20 franchise leagues. Everything in this section is pack data, not core behavior.

### 7.1 Roles, tags, restricted categories
- **Roles:** BAT (batter), BOWL (bowler), AR (all-rounder), WK (wicketkeeper).
- **Tags:** capped / uncapped; overseas.
- **Restricted category:** overseas, max 8 per squad.

### 7.2 Room presets
- Purse ₹120 Cr (currency INR, display unit Crore).
- Squad 18–25.
- Role minimums: WK 2, BAT 5, BOWL 5, AR 3.
- Increment ladder: +₹5L up to ₹1 Cr, +₹10L to ₹2 Cr, +₹20L to ₹5 Cr, +₹25L beyond.
- Retention: R = 6, max 5 capped + 2 uncapped. Slab defaults: ₹18/14/11 Cr for capped picks 1–3, ₹4 Cr uncapped. Matches IPL 2025 rules.
- RTM enabled from season 2, with the 2025-style final counter-raise (FR-49).
- Optional **short-pool** import (e.g., top 80 players) for one-evening auctions.

### 7.3 Stat schema
Runs, fours, sixes, balls faced, strike rate, dismissal (incl. duck), wickets (with dismissal type: LBW/bowled), overs, maidens, runs conceded/economy, catches, stumpings, run-outs (direct/assisted), Player of the Match, in playing XI.

### 7.4 Default scoring rule set
Editable per room.
- **Batting:** +1/run, +2 boundary bonus per four, +4 per six, +10 at 50, +20 additional at 100, −5 duck (dismissed for 0; BAT/WK/AR only).
- **Strike-rate bonus** (min 10 balls): +6 above 170, +4 for 150–170, +2 for 130–150, −2 below 70, −4 below 60.
- **Bowling:** +25/wicket, +8 bonus LBW/bowled, +10 at 3 wickets, +20 additional at 5, +12/maiden.
- **Economy bonus** (min 2 overs): +6 below 5 RPO, +4 for 5–6, +2 for 6–7, −2 above 10, −4 above 12.
- **Fielding:** +8/catch, +4 bonus for 3+ catches, +12/stumping, +12 direct-hit run-out, +6 assisted run-out.
- **Other:** +25 Player of the Match.
- **Appearance bonus:** +4 to every playing-XI member (rewards drafting players who actually play).

### 7.5 Data adapter
Candidates: cricketdata.org free tier vs paid Roanuz / Entity Sport. Validate free-tier scorecard completeness for IPL before the season. The fallback is manual entry (FR-81), so this doesn't block the MVP.

### 7.6 AI domain context
Head Coach knowledge: T20 role value (death-over bowling, powerplay hitting, finisher role, keeper-batter scarcity), venue/conditions, and overseas-slot opportunity cost. The stat glossary maps §7.3 stats to plain-language explanations.

### 7.7 Real-rule references
[ESPNcricinfo — IPL 2025 auction & retention rules](https://www.espncricinfo.com/story/ipl-2025-auction-retention-rules-six-retentions-per-team-right-to-match-returns-impact-player-to-stay-1452396), [Rajasthan Royals — mega auction rules explainer](https://www.rajasthanroyals.com/latest-news/indian-premier-league-ipl-2025-mega-auction-rules).

---

## 8. Release Phasing

| Phase | Scope | Exit criteria |
|---|---|---|
| **P0 — MVP** | Accounts, rooms, Sport Pack framework + Cricket T20 pack, player pool import, full remote auction engine (no RTM) with role minimums and restricted-category quotas, squads, scoring engine + nightly ingestion + manual override, leaderboard, PWA basics. **No AI.** | One real friend-group auction + a scored (or replayed historical) match week; stub second pack passes NFR-12 test |
| **P1 — Season-ready** | Retentions + RTM flow, proxy bids, push notifications, unmapped-player alerts, auction summary/validation, player leaderboards. **AI, in order:** Player Intel → Fit Engine → Report Card + advisor accountability → in-auction lifelines + two-voice table | Full season run end-to-end; one auction run with lifelines on |
| **P2 — Delight** | Mini auctions/year 2 carryover, trades, captains/multipliers, live scoring, chat, awards, report card re-grades, spectators | As motivation allows |
| **Future — More sports** | Additional Sport Packs (e.g., Football, Basketball, Kabaddi): roles, stats, scoring, data adapter, AI domain context | Each new pack ships with zero core code changes |

**Build order:** rooms → auction engine (prototype first with a fake 20-player pool; highest-risk component) → scoring engine + Cricket pack → polish (P0 done) → historical backfill + Player Intel → Fit Engine → Report Card → in-auction advisor.

**Dependency notes:**
- Every AI feature depends on the scoring engine and the historical backfill, so neither can slip behind the AI work.
- The Fit Engine depends on the extended eligibility guard (FR-44).
- The advisor depends on the lot lifecycle (FR-41/43/49) and the event log (NFR-4).
- The Report Card depends on auction close (FR-52).

---

## 9. Open Questions

1. **Sport data provider choice** (per pack). For Cricket, see §7.5.
2. **Auction pacing:** a full 200+ player auction takes hours. Multi-evening adjournment is supported (FR-47), and packs may offer a "short pool" import (recommended).
3. **Tie-breakers:** on equal season points, does fewer purse spent win? Should it be Commissioner-configurable?
4. **Bid misclicks:** allow an "undo within 3 seconds if no subsequent bid" grace, or keep bids irrevocable? Irrevocable is simpler and more authentic.
5. **Player pool sourcing:** who maintains each season's dataset (auction list + base prices)? Likely a shared CSV per pack in the repo, updated after the real auction list is announced.
6. **LLM provider:** which hosted open-weight model inference provider and model to use. Validate free-tier rate limits against auction-night burst load before committing.
7. **Lifeline defaults:** should the default lifeline counts scale with pool size (short pool vs full auction)?

**Decided (2026-11-01):**
- Role minimums are enforced by the eligibility guard. Team composition is a real auction constraint, not just advice.
- Rival Scan may use public rival squads and purses. This is part of strategy, and the data is already visible to all.
- The core is sport-agnostic. v1 ships the Cricket T20 pack only.

**Future ideas (not committed):** AI bot franchises / solo practice mode built on the Fit Engine; AI auctioneer commentary on big bids and steals.

---

## 10. Out of Scope (explicit)

- Real money in any form.
- Public rooms/discovery.
- Sport Packs other than Cricket T20 in v1 (the architecture supports them).
- Native iOS/Android binaries.
- Social features beyond room chat.
- Trained ML price-prediction models (AI advice is bounded per §4.11).
- AI auto-bidding or any AI action on a manager's behalf.
- Pausing the bid timer for AI.
- Advisor access to upcoming lots, the remaining pool, or set order.
