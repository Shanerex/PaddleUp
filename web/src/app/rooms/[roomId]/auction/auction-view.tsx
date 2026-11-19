"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { ApiError, getMembers, getRoom, sendControl, sendPicks, setReady, type Member, type Room } from "../../../../api/client";
import { formatLotTime, pageClock } from "../../../../auction/clock";
import { controlRefusal, reopenedNotice, skippedNotice, type Notice } from "../../../../auction/copy";
import { useBid } from "../../../../auction/useBid";
import { useLive, useTicker } from "../../../../auction/useLive";
import { buildView } from "../../../../auction/view";
import { HOME } from "../../../../auth/redirect";
import { explainRoomError } from "../../../../rooms/errors";
import { hueOf } from "../../../../rooms/hues";
import { ScheduleSheet } from "../../../../rooms/schedule-sheet";
import { formatMoney } from "../../../../rooms/money";
import { AppShell, shell } from "../../../../ui/AppShell";
import { Icon } from "../../../../ui/Icon";
import { stateChipLabel } from "../../../../ui/kit";
import { Button, ButtonLink } from "../../../../ui/Button";
import s from "./auction.module.css";
import { MainDoneCard, NominatingPanel, SendPicksBar } from "./accelerated";
import { BidDock } from "./bid-dock";
import { Controls } from "./controls";
import { Lobby } from "./lobby";
import { LotCard, type LotResult } from "./lot-card";
import { AdjournedCard, ClosedCard, NoAuctionCard, Notices, hueStyle, type Toast, type Who } from "./notices";

const BRAND_HUE = 38;
const TOAST_MS = 5000;

interface Data {
  room: Room;
  members: Member[];
}

/**
 * One route for the whole auction (R-3, AC-3): the lobby while the server says `scheduled`, the lot
 * screen while it is live, the closed card when it has ended. Every number comes from the server's
 * state and broadcasts through `useLive`; a phase change re-renders, it never navigates.
 */
export function AuctionView({ roomId }: { roomId: string }) {
  const [data, setData] = useState<Data | null>(null);
  const [loadFailed, setLoadFailed] = useState<string | null>(null);
  const [roomMissing, setRoomMissing] = useState(false);

  const loadRoom = useCallback(
    (): Promise<void> =>
      Promise.all([getRoom(roomId), getMembers(roomId)])
        .then(([room, members]) => {
          setData({ room, members });
          setLoadFailed(null);
        })
        .catch((e) => {
          if (e instanceof ApiError && e.status === 404) setRoomMissing(true);
          else setLoadFailed(explainRoomError(e).message);
        }),
    [roomId],
  );

  useEffect(() => {
    void loadRoom();
  }, [loadRoom]);

  if (roomMissing) {
    return (
      <AppShell>
        <div className={shell.centered}>
          <div className={shell.empty}>
            <h1 className={shell.emptyTitle}>Can&apos;t find that room</h1>
            <p className={shell.emptyBody}>It doesn&apos;t exist, or you&apos;re not in it.</p>
            <ButtonLink href={HOME} variant="brand" size="M48">
              Back to rooms
            </ButtonLink>
          </div>
        </div>
      </AppShell>
    );
  }
  if (!data) {
    return (
      <div className={`team ${s.screen}`} style={{ "--h": BRAND_HUE } as CSSProperties} aria-busy="true">
        <div className={s.header}>
          <Link href={`/rooms/${roomId}`} className={s.back} aria-label="Back to the room">
            <Icon name="arrow_back" size={22} />
          </Link>
          <div className={s.headText}>
            <span className={s.roomName}>Auction</span>
          </div>
        </div>
        <div className={s.scroll}>
          {loadFailed ? (
            <div className={s.panelCard} role="alert">
              <div className={s.panelTitle}>Couldn&apos;t load the auction</div>
              <div className={s.panelBody}>{loadFailed}</div>
              <Button variant="primary" onClick={() => void loadRoom()}>
                Try again
              </Button>
            </div>
          ) : (
            <div className={s.grid}>
              <div className={`${s.lotCard} ${s.skeleton}`} style={{ height: 320 }} />
              <div className={`${s.col} ${s.skeleton}`} style={{ height: 180 }} />
            </div>
          )}
        </div>
      </div>
    );
  }
  return <Auction roomId={roomId} data={data} reloadRoom={loadRoom} />;
}

function Auction({ roomId, data, reloadRoom }: { roomId: string; data: Data; reloadRoom: () => Promise<void> }) {
  const { room, members } = data;
  const { live, clock, snapshot } = useLive(roomId);
  const state = snapshot.state;
  const phase = state?.phase ?? null;
  const offline = snapshot.connection === "reconnecting";
  const me = room.myFranchiseId ?? null;
  const roomHref = `/rooms/${roomId}`;

  // Clocks tick: once a second in the lobby, ten times a second on a lot.
  useTicker(phase === "scheduled" ? 1000 : 100, phase !== null);
  const serverNow = clock.ready ? clock.now() : null;

  // Who the room's franchises are, for names and colours.
  const whoOf = useMemo(() => {
    const byId = new Map<string, Who>();
    for (const m of members) if (m.franchise) byId.set(m.franchise.id, { name: m.franchise.name, monogram: m.franchise.monogram, colour: m.franchise.colour });
    return (id: string | null | undefined): Who | null => (id ? (byId.get(id) ?? null) : null);
  }, [members]);

  // A room that just went live may have changed: read it again when the phase changes.
  const lastPhase = useRef(phase);
  useEffect(() => {
    if (lastPhase.current !== phase && phase !== null) void reloadRoom();
    lastPhase.current = phase;
  }, [phase, reloadRoom]);

  const bidder = useBid(roomId);
  const lotNo = state?.lot?.no ?? null;

  // Having led a lot is what makes losing it "outbid"; a bid I sent counts too.
  const [ledLot, setLedLot] = useState<number | null>(null);
  if (me && state?.leaderFranchiseId === me && lotNo !== null && ledLot !== lotNo) setLedLot(lotNo);
  const iBidOnLot = lotNo !== null && (bidder.snapshot.bidLot === lotNo || ledLot === lotNo);

  const baseInput = {
    state,
    connection: snapshot.connection,
    synced: snapshot.synced,
    me,
    iBidOnLot,
    serverNow,
    sending: bidder.snapshot.sending,
  };
  const view = buildView({ ...baseInput, frozenLeft: snapshot.frozenLeft });

  // Notices: outbid when the lead moves off me (cleared by my next accepted bid); a refusal or a
  // bid with no answer shows the server's reply or the lack of one. Each fades after a few seconds.
  const money = useCallback((n: number) => formatMoney(n, room.config), [room.config]);
  const [outbid, setOutbid] = useState<Extract<Toast, { kind: "outbid" }> | null>(null);
  const [prevMine, setPrevMine] = useState(view.myStatus);
  if (prevMine !== view.myStatus) {
    setPrevMine(view.myStatus);
    if (prevMine === "leading" && view.myStatus === "outbid") {
      setOutbid({ kind: "outbid", who: whoOf(view.leaderId), amount: money(view.currentBid) });
    }
  }
  const outcome = bidder.snapshot.outcome;
  const [seenOutcome, setSeenOutcome] = useState(outcome);
  if (outcome !== seenOutcome) {
    setSeenOutcome(outcome);
    if (outcome?.kind === "accepted") setOutbid(null);
  }
  const refusal: Toast | null = outcome && outcome.kind !== "accepted" ? { kind: "rejected", notice: outcome.notice } : null;
  // A refused control, and the Commissioner's skip or re-open as every manager sees it (3q, 3s, 3t).
  const [controlNotice, setControlNotice] = useState<Notice | null>(null);
  const adminEvent = snapshot.adminEvent;
  const [seenAdmin, setSeenAdmin] = useState(adminEvent?.seq ?? 0);
  const [adminToast, setAdminToast] = useState<Notice | null>(null);
  if (adminEvent && adminEvent.seq !== seenAdmin) {
    setSeenAdmin(adminEvent.seq);
    const closedName = state?.lastResult?.card?.name ?? null;
    if (adminEvent.kind === "lot_skipped") {
      setAdminToast(skippedNotice(adminEvent.lot, state?.lot?.no === adminEvent.lot ? (state?.lot?.name ?? null) : closedName));
    } else {
      setAdminToast(
        reopenedNotice(adminEvent.lot, adminEvent.reason, adminEvent.franchiseId && adminEvent.amount !== null ? { name: whoOf(adminEvent.franchiseId)?.name ?? "The buyer", amount: money(adminEvent.amount) } : null),
      );
    }
  }
  const toast: Toast | null = refusal ?? (controlNotice ? { kind: "rejected", notice: controlNotice } : null) ?? outbid ?? (adminToast ? { kind: "admin", notice: adminToast } : null);
  const { dismiss } = bidder;
  const dismissToast = useCallback(() => {
    setOutbid(null);
    setControlNotice(null);
    setAdminToast(null);
    dismiss();
  }, [dismiss]);
  // A refusal means the screen was behind the server (say a lot closed as the bid went): read again.
  const refused = outcome?.kind === "rejected";
  useEffect(() => {
    if (refused) live.resync();
  }, [refused, outcome, live]);
  useEffect(() => {
    if (!outbid && !outcome && !controlNotice && !adminToast) return;
    const id = setTimeout(dismissToast, TOAST_MS);
    return () => clearTimeout(id);
  }, [outbid, outcome, controlNotice, adminToast, dismissToast]);

  // Ready toggle: only the server's answer changes what shows.
  const [readyBusy, setReadyBusy] = useState(false);
  const toggleReady = async () => {
    if (readyBusy || !me) return;
    setReadyBusy(true);
    try {
      live.applyPresence(await setReady(roomId, !snapshot.ready.includes(me)));
    } catch (e) {
      // A 409 means the auction moved on; the next fetch shows where.
      if (e instanceof ApiError && e.status === 409) live.resync();
    } finally {
      setReadyBusy(false);
    }
  };

  const mine = me ? (whoOf(me) ?? null) : null;
  const hue = mine ? hueOf(mine.colour) : BRAND_HUE;

  // The Commissioner sets or moves the start. The sheet only asks; what shows is the server's reply.
  const [sheet, setSheet] = useState<"new" | "move" | "retime" | null>(null);
  const commish = room.myRole === "commissioner";

  // Commissioner intents that need no form: each only asks, and the server's answer or a refusal shows.
  const [controlBusy, setControlBusy] = useState(false);
  const control = async (action: "start_now" | "resume" | "resume_now" | "open_round" | "end_auction" | "end_round") => {
    if (controlBusy) return;
    setControlBusy(true);
    try {
      const answer = await sendControl(roomId, action);
      if (answer.outcome === "rejected") setControlNotice(controlRefusal(action, answer.reason, state?.lot?.no ?? null, null));
      live.resync();
    } catch (e) {
      setControlNotice({ title: "Couldn't do that", body: `${explainRoomError(e).message} Nothing changed.` });
    } finally {
      setControlBusy(false);
    }
  };

  // The accelerated round's picks (Explorations 3w): what I have ticked until I send them. Once sent
  // (the server no longer lists me as waiting) the server's copy shows and the list is read-only.
  const [ticked, setTicked] = useState<number[] | null>(null);
  const [picksBusy, setPicksBusy] = useState(false);
  const picking = phase === "nominating";
  const sentPicks = picking && me !== null && !!state && !state.waiting.includes(me);
  const selected = sentPicks ? (state?.myPicks ?? []) : (ticked ?? state?.myPicks ?? []);
  const togglePick = (no: number) => setTicked(selected.includes(no) ? selected.filter((x) => x !== no) : [...selected, no]);
  const sendMyPicks = async () => {
    if (picksBusy || selected.length === 0) return;
    setPicksBusy(true);
    try {
      const answer = await sendPicks(roomId, selected);
      if (answer.outcome === "rejected") setControlNotice(controlRefusal("picks", answer.reason, null, null));
      live.resync();
    } catch (e) {
      setControlNotice({ title: "Couldn't send your picks", body: `${explainRoomError(e).message} Nothing changed.` });
    } finally {
      setPicksBusy(false);
    }
  };

  const scheduleSheet = sheet && (
    <div className="team" style={{ "--h": hue } as CSSProperties}>
      <ScheduleSheet
        roomId={roomId}
        mode={sheet}
        startsAt={sheet === "retime" ? (state?.resumeAt ?? undefined) : state?.startsAt}
        send={sheet === "retime" ? (iso) => sendControl(roomId, "change_time", { resumeAt: iso }) : undefined}
        // With no auction there is no state to sample; the page clock is fed by every request's Date header.
        serverNow={() => (clock.ready ? clock.now() : pageClock.ready ? pageClock.now() : null)}
        poolHref={`/rooms/${roomId}/pool`}
        onClose={() => setSheet(null)}
        onDone={() => {
          setSheet(null);
          // A first schedule has no socket yet; a move reaches everyone, this tab included, as an event.
          if (sheet === "new") live.retry();
          else live.resync();
        }}
      />
    </div>
  );

  if (snapshot.missing) {
    return (
      <div className={`team ${s.screen}`} style={{ "--h": hue } as CSSProperties}>
        <Header roomName={room.config.name} pill={stateChipLabel(room.state)} sub="Auction" roomHref={roomHref} />
        <div className={s.scroll}>
          <NoAuctionCard roomHref={roomHref} onSchedule={commish ? () => setSheet("new") : undefined} />
        </div>
        {scheduleSheet}
      </div>
    );
  }
  if (!state) {
    return (
      <div className={`team ${s.screen}`} style={{ "--h": hue } as CSSProperties} aria-busy="true">
        <Header roomName={room.config.name} pill="" sub="Auction" roomHref={roomHref} />
        <Notices reconnecting={offline} paused={false} pausedLeftMs={null} toast={null} onRetry={live.retry} onDismiss={dismissToast} />
        <div className={s.scroll}>
          <div className={s.grid}>
            <div className={`${s.lotCard} ${s.skeleton}`} style={{ height: 320 }} />
            <div className={`${s.col} ${s.skeleton}`} style={{ height: 180 }} />
          </div>
        </div>
      </div>
    );
  }

  if (phase === "scheduled") {
    return (
      <>
        <Lobby
          roomId={roomId}
          room={room}
          members={members}
          startsAt={state.startsAt}
          timer={state.config}
          untilStartMs={offline ? snapshot.frozenLeft : serverNow === null ? null : Date.parse(state.startsAt) - serverNow}
          connected={snapshot.connected}
          ready={snapshot.ready}
          offline={offline}
          lastSeen={snapshot.lastSeen === null ? null : new Date(snapshot.lastSeen)}
          busy={readyBusy}
          onToggleReady={() => void toggleReady()}
          onRetry={live.retry}
          onChangeTime={commish ? () => setSheet("move") : undefined}
          onStartNow={commish ? () => void control("start_now") : undefined}
          startBusy={controlBusy}
        />
        {scheduleSheet}
      </>
    );
  }

  if (view.status === "ended") {
    return (
      <div className={`team ${s.screen}`} style={{ "--h": hue } as CSSProperties}>
        <Header roomName={room.config.name} pill="ENDED" sub={`${state.lotCount} lots`} roomHref={roomHref} />
        <div className={s.scroll}>
          <ClosedCard roomHref={roomHref} />
        </div>
      </div>
    );
  }

  const lot = state.lot ?? null;
  // The lot that plays next: the open (held) lot, or in a gap the one the server queued after it.
  const playsNext = state.lotOpen ? lot : (state.nextLot ?? null);
  const result: LotResult | null =
    view.result
      ? { sold: view.result.sold, who: whoOf(view.result.franchiseId), amount: view.result.amount, youWon: !!me && view.result.franchiseId === me }
      : null;
  const leader = whoOf(view.leaderId);
  const myStanding = me ? state.standings.find((x) => x.franchiseId === me) : undefined;
  const accelerated = state.round === "accelerated";
  const headSub =
    view.status === "main_done"
      ? "Main pool done"
      : view.status === "nominating"
        ? "Accelerated round · picking"
        : lot
          ? accelerated
            ? state.accelPosition != null && state.accelSize != null
              ? `Accelerated round · ${state.accelPosition} of ${state.accelSize}`
              : `Accelerated round · Lot ${lot.no}`
            : `${lot.set} · Lot ${lot.no}`
          : "Auction";
  const franchiseChips = state.standings.map((x) => {
    const who = whoOf(x.franchiseId);
    return { id: x.franchiseId, name: who?.name ?? "Franchise", monogram: who?.monogram ?? "", colour: who?.colour ?? "", done: !state.waiting.includes(x.franchiseId) };
  });
  const timeText = view.timeLeftMs === null ? "–:––" : formatLotTime(view.timeLeftMs);

  return (
    <div className={`team ${s.screen}`} style={{ "--h": hue } as CSSProperties}>
      <Header
        roomName={room.config.name}
        pill="LIVE"
        sub={headSub}
        roomHref={roomHref}
        purse={view.hasFranchise ? { label: "YOUR PURSE", value: view.purse !== null ? money(view.purse) : "" } : null}
        observer={!view.hasFranchise}
        mono={mine?.monogram ?? null}
      />
      <div className={s.main}>
        {commish && (
          <Controls
            ctx={{
              roomId,
              state,
              money,
              labels: room.packLabels,
              nameOf: (id) => whoOf(id)?.name ?? null,
              buyerOf: (id) => {
                const who = whoOf(id);
                return who ? { monogram: who.monogram, colour: who.colour } : null;
              },
              timeLeftMs: view.timeLeftMs,
              serverNow: () => (clock.ready ? clock.now() : null),
              onNotice: setControlNotice,
              onResync: () => live.resync(),
            }}
          />
        )}
        <div className={s.scroll}>
          {view.status === "main_done" ? (
            <MainDoneCard
              sold={state.standings.reduce((n, x) => n + x.squad.length, 0)}
              unsold={state.unsoldPool.length}
              franchises={state.standings.length}
              commish={commish ? { busy: controlBusy, onOpenRound: () => void control("open_round"), onEnd: () => void control("end_auction") } : undefined}
            />
          ) : view.status === "nominating" ? (
            <NominatingPanel
              pool={state.unsoldPool}
              counts={new Map(state.pickCounts.map((c) => [c.lot, c.count]))}
              openSlots={state.openSlots}
              selected={selected}
              sent={sentPicks}
              onToggle={togglePick}
              labels={room.packLabels}
              config={room.config}
              franchises={franchiseChips}
              doneCount={franchiseChips.filter((f) => f.done).length}
              commish={
                commish
                  ? { queued: state.pickCounts.length, waiting: state.waiting.length, busy: controlBusy, onEndRound: () => void control("end_round") }
                  : undefined
              }
            />
          ) : view.status === "adjourned" || view.status === "resume_lobby" ? (
            <AdjournedCard
              lotsDone={state.lotsClosed}
              squad={myStanding ? myStanding.squad.length : null}
              purse={view.purse !== null ? money(view.purse) : null}
              resumes={view.status === "adjourned" && state.resumeAt ? resumeLabel(state.resumeAt) : null}
              commish={
                commish
                  ? {
                      onResumeNow: () => void control("resume_now"),
                      onStartNow: () => void control("start_now"),
                      onChangeTime: () => setSheet("retime"),
                      busy: controlBusy,
                      nextLot: playsNext?.no ?? null,
                      lobbySeconds: state.resumeLobbySeconds,
                      inLobby: view.status === "resume_lobby",
                    }
                  : undefined
              }
            />
          ) : (
            lot && (
              <div className={s.grid}>
                <LotCard lot={lot} labels={room.packLabels} config={room.config} result={result} pickedBy={accelerated ? state.lotPicks : null} />
                <div className={s.col}>
                  <section className={s.bidPanel} aria-label="Current bid">
                    <div className={s.bidHead}>
                      <span data-testid="bid-label">{view.bidLabel}</span>
                      <span className={s.timerNote} data-testid="timer-note">
                        {view.timerNote}
                      </span>
                    </div>
                    <div className={s.bidRow}>
                      <span className={s.bidNum} data-testid="current-bid">
                        <span key={view.currentBid} className={view.currentBid > 0 ? s.roll : undefined}>
                          {money(view.currentBid > 0 ? view.currentBid : lot.basePrice)}
                        </span>
                      </span>
                      <span
                        className={`${s.timeNum} ${view.urgent ? s.timeUrgent : view.timerGrey ? s.timeFrozen : ""}`}
                        data-testid="timer"
                        data-frozen={view.timerFrozen || undefined}
                      >
                        {timeText}
                      </span>
                    </div>
                    <div className={`${s.bar} ${view.urgent ? s.barUrgent : ""}`} aria-hidden="true">
                      <div className={`${s.barFill} ${view.urgent ? s.barFillUrgent : ""}`} style={{ width: `${view.timePct}%` }} />
                    </div>
                    <div className={s.leader} data-testid="leader">
                      {leader ? (
                        <>
                          <span className={s.leaderMono} style={hueStyle(leader.colour)}>
                            {leader.monogram}
                          </span>
                          {me && view.leaderId === me ? "You" : leader.name}
                        </>
                      ) : (
                        <>
                          <span className={`${s.leaderMono} ${s.leaderNone}`} />
                          No bids yet
                        </>
                      )}
                    </div>
                  </section>
                </div>
              </div>
            )
          )}
        </div>
        {view.status === "nominating" ? (
          me !== null &&
          state.openSlots !== undefined && <SendPicksBar picked={selected.length} sent={sentPicks} noSlots={state.openSlots === 0} waiting={state.waiting.length} busy={picksBusy} onSend={() => void sendMyPicks()} />
        ) : (
          view.status !== "main_done" && (
            <div className={s.dockBar}>
              <BidDock view={view} config={room.config} labels={room.packLabels} onBid={() => state.lot && view.nextBid !== null && void bidder.press(state.lot.no, view.nextBid)} />
            </div>
          )
        )}
        <Notices
          reconnecting={offline}
          paused={view.status === "paused"}
          pausedLeftMs={state.heldMs}
          pausedBetween={view.betweenLots}
          onResume={commish ? () => void control("resume") : undefined}
          resumeLobby={view.status === "resume_lobby" && state.liveAt && serverNow !== null ? { lot: playsNext?.no ?? null, leftMs: Math.max(0, Date.parse(state.liveAt) - serverNow) } : null}
          toast={toast}
          onRetry={live.retry}
          onDismiss={dismissToast}
        />
      </div>
      {scheduleSheet}
    </div>
  );
}

/** "Sat 2 Nov, 8:00 PM" in the viewer's own time. */
function resumeLabel(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })}, ${d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}`;
}

function Header({
  roomName,
  pill,
  sub,
  roomHref,
  purse,
  observer,
  mono,
}: {
  roomName: string;
  pill: string;
  sub: string;
  roomHref: string;
  purse?: { label: string; value: string } | null;
  observer?: boolean;
  mono?: string | null;
}) {
  return (
    <header className={s.header}>
      <Link href={roomHref} className={s.back} aria-label="Back to the room">
        <Icon name="arrow_back" size={22} />
      </Link>
      <div className={s.headText}>
        <div className={s.headLine}>
          <h1 className={s.roomName}>{roomName}</h1>
          {pill && (
            <span className={s.pill} data-testid="auction-pill">
              {pill === "LIVE" && <span className={s.pillDot} />}
              {pill}
            </span>
          )}
        </div>
        <span className={s.headSub}>{sub}</span>
      </div>
      {purse && (
        <div className={s.purse}>
          <span className={s.purseLabel}>{purse.label}</span>
          <span className={s.purseValue} data-testid="purse">
            {purse.value}
          </span>
        </div>
      )}
      {observer && (
        <div className={s.purse}>
          <span className={s.purseLabel}>WATCHING AS</span>
          <span className={s.watching}>Commissioner</span>
        </div>
      )}
      {mono && <span className={s.headTile}>{mono}</span>}
      {observer && (
        <span className={s.headTile}>
          <Icon name="shield_person" size={20} />
        </span>
      )}
    </header>
  );
}
