"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { ApiError, getMembers, getRoom, setReady, type Member, type Room } from "../../../../api/client";
import { formatLotTime, pageClock } from "../../../../auction/clock";
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
import { BidDock } from "./bid-dock";
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
  const draft = buildView({ ...baseInput, frozenLeft: snapshot.frozenLeft });

  // The lot clock is held at its value when paused or adjourned.
  const holdable = draft.status === "paused" || draft.status === "adjourned";
  const [held, setHeld] = useState<{ status: string; ms: number } | null>(null);
  if (holdable && (!held || held.status !== draft.status)) setHeld({ status: draft.status, ms: draft.timeLeftMs ?? 0 });
  else if (!holdable && held) setHeld(null);
  const view = holdable && held ? buildView({ ...baseInput, frozenLeft: held.ms }) : draft;

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
  const toast: Toast | null = refusal ?? outbid;
  const { dismiss } = bidder;
  const dismissToast = useCallback(() => {
    setOutbid(null);
    dismiss();
  }, [dismiss]);
  // A refusal may mean the screen was behind (a squad lock the bid patch cannot know): read again.
  const refused = outcome?.kind === "rejected";
  useEffect(() => {
    if (refused) live.resync();
  }, [refused, outcome, live]);
  useEffect(() => {
    if (!outbid && !outcome) return;
    const id = setTimeout(dismissToast, TOAST_MS);
    return () => clearTimeout(id);
  }, [outbid, outcome, dismissToast]);

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
  const [sheet, setSheet] = useState<"new" | "move" | null>(null);
  const commish = room.myRole === "commissioner";
  const scheduleSheet = sheet && (
    <div className="team" style={{ "--h": hue } as CSSProperties}>
      <ScheduleSheet
        roomId={roomId}
        mode={sheet}
        startsAt={state?.startsAt}
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
  const result: LotResult | null =
    view.result && (view.status === "sold" || view.status === "unsold")
      ? { sold: view.result.sold, who: whoOf(view.result.franchiseId), amount: view.result.amount, youWon: !!me && view.result.franchiseId === me }
      : null;
  const leader = whoOf(view.leaderId);
  const myStanding = me ? state.standings.find((x) => x.franchiseId === me) : undefined;
  const timeText = view.timeLeftMs === null ? "–:––" : formatLotTime(view.timeLeftMs);

  return (
    <div className={`team ${s.screen}`} style={{ "--h": hue } as CSSProperties}>
      <Header
        roomName={room.config.name}
        pill="LIVE"
        sub={lot ? `${lot.set} · Lot ${lot.no}` : "Auction"}
        roomHref={roomHref}
        purse={view.hasFranchise ? { label: "YOUR PURSE", value: view.purse !== null ? money(view.purse) : "" } : null}
        observer={!view.hasFranchise}
        mono={mine?.monogram ?? null}
      />
      <div className={s.main}>
        <div className={s.scroll}>
          {view.status === "adjourned" ? (
            <AdjournedCard
              lotsDone={Math.max(0, (lot?.no ?? 1) - 1)}
              squad={myStanding ? myStanding.squad.length : null}
              purse={view.purse !== null ? money(view.purse) : null}
              resumes={null}
            />
          ) : (
            lot && (
              <div className={s.grid}>
                <LotCard lot={lot} labels={room.packLabels} config={room.config} result={result} />
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
        <div className={s.dockBar}>
          <BidDock view={view} config={room.config} labels={room.packLabels} onBid={() => state.lot && view.nextBid !== null && void bidder.press(state.lot.no, view.nextBid)} />
        </div>
        <Notices
          reconnecting={offline}
          paused={view.status === "paused"}
          pausedLeftMs={held?.ms ?? null}
          toast={toast}
          onRetry={live.retry}
          onDismiss={dismissToast}
        />
      </div>
    </div>
  );
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
