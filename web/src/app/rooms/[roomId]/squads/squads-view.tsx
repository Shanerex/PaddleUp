"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { getAuction, type AuctionState, type Member } from "../../../../api/client";
import { squadView } from "../../../../auction/squads";
import { useLive } from "../../../../auction/useLive";
import { HOME } from "../../../../auth/redirect";
import { AppShell, shell } from "../../../../ui/AppShell";
import { Button, ButtonLink } from "../../../../ui/Button";
import { Icon } from "../../../../ui/Icon";
import { RoomFrame } from "../room-frame";
import r from "../room.module.css";
import { frameOf, usePoolData } from "../use-pool-data";
import { pillText, SquadChips, SquadPanel, type SquadPhase, type SquadWho } from "./squad-panel";
import s from "./squads.module.css";

/** How often the page asks for the state itself while the live feed is down. */
export const FALLBACK_MS = 5000;

/**
 * The state fetched directly while the live feed is down or stale, so the squads still move on a
 * lost connection. Nothing is fetched while the feed is live; the answer is dropped once it is.
 */
function useFallbackState(roomId: string, active: boolean): AuctionState | null {
  const [fetched, setFetched] = useState<AuctionState | null>(null);
  useEffect(() => {
    if (!active) return;
    let stopped = false;
    const pull = () =>
      getAuction(roomId)
        .then((next) => !stopped && setFetched(next))
        .catch(() => {});
    void pull();
    const id = setInterval(() => void pull(), FALLBACK_MS);
    return () => {
      stopped = true;
      clearInterval(id);
    };
  }, [roomId, active]);
  return active ? fetched : null;
}

/**
 * Squads (docs/design Room `squadLive`, `squadEmpty`): one chip per franchise and the picked
 * franchise's squad. The auction state's `standings` carry every franchise's squad and purse; the
 * live feed refreshes them on each hammer. Every room member sees every squad.
 */
export function SquadsView({ roomId }: { roomId: string }) {
  const { data, missing, loadFailed, load } = usePoolData(roomId);
  const { live, snapshot } = useLive(roomId);
  // A lost or stale connection falls back to fetching the state until the feed is back.
  const offline = snapshot.connection === "reconnecting";
  const fetched = useFallbackState(roomId, offline);
  const state = fetched && (!snapshot.state || fetched.lastSeq >= snapshot.state.lastSeq) ? fetched : snapshot.state;
  const [picked, setPicked] = useState<string | null>(null);

  // The room's own state (the header chip) changes when the auction goes live or ends: read it again.
  const phase0 = state?.phase ?? null;
  const lastPhase = useRef(phase0);
  useEffect(() => {
    if (lastPhase.current !== null && phase0 !== null && lastPhase.current !== phase0) void load();
    lastPhase.current = phase0;
  }, [phase0, load]);

  const f = frameOf(data);
  const room = data?.room;
  const pool = data?.pool;
  const members = data?.members;

  const whoOf = useMemo(() => {
    const byId = new Map<string, SquadWho>();
    for (const m of (members ?? []) as Member[]) {
      if (m.franchise) byId.set(m.franchise.id, { name: m.franchise.name, monogram: m.franchise.monogram, colour: m.franchise.colour, manager: m.franchise.id === room?.myFranchiseId ? "You" : m.displayName, mine: m.franchise.id === room?.myFranchiseId });
    }
    return byId;
  }, [members, room?.myFranchiseId]);

  const squads = useMemo(() => (state && room && pool ? squadView(state, pool, room, [...whoOf.keys()]) : null), [state, room, pool, whoOf]);

  if (missing) {
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

  // Franchises in the auction's order; before the auction exists, the room's own.
  const franchiseIds = squads ? squads.map((x) => x.franchiseId) : [...whoOf.keys()];
  const shownId = picked && franchiseIds.includes(picked) ? picked : room?.myFranchiseId && franchiseIds.includes(room.myFranchiseId) ? room.myFranchiseId : (franchiseIds[0] ?? null);
  const unknown: SquadWho = { name: "Franchise", monogram: "", colour: "", manager: null, mine: false };
  const firstWords = franchiseIds.map((id) => (whoOf.get(id) ?? unknown).name.split(" ")[0]);
  const chips = franchiseIds.map((id, i) => {
    const who = whoOf.get(id) ?? unknown;
    // The first word of the name, as the mock draws it, unless another franchise starts the same way.
    const short = firstWords.filter((w) => w === firstWords[i]).length > 1 ? who.name : firstWords[i];
    return { id, label: who.mine ? "You" : short, monogram: who.monogram, colour: who.colour };
  });
  const phase: SquadPhase = !state ? "none" : state.phase === "scheduled" ? "scheduled" : state.phase === "ended" ? "ended" : "live";
  const firstHammer = !!state && state.lotsClosed === 0 && !!squads && squads.every((x) => x.empty);
  const shown = squads?.find((x) => x.franchiseId === shownId) ?? null;

  return (
    <RoomFrame
      roomId={roomId}
      hue={f.hue}
      roomName={room?.config.name ?? ""}
      state={room?.state}
      chip={phase === "scheduled" && state?.startsAt ? pillText(phase, state.startsAt) : undefined}
      sub={f.sub}
      mine={f.mine}
      loaded={!!room}
      commish={f.commish}
      pre={f.pre}
      active="squads"
      banners={
        <>
          {offline && (
            <div className={r.banner} role="status" style={{ background: "var(--warn-tint)" }} data-testid="squads-offline">
              <Icon name="sync_problem" style={{ color: "var(--warn-ink)" }} />
              <span className={r.bannerText}>
                <b>Live updates paused.</b> <span className={r.bannerSub}>Checking for new buys every few seconds.</span>
              </span>
              <Button size="S" variant="outline" onClick={live.retry}>
                Retry
              </Button>
            </div>
          )}
          {loadFailed && data && (
            <div className={r.banner} role="alert" style={{ background: "var(--bad-tint)" }}>
              <Icon name="error" style={{ color: "var(--bad)" }} />
              <span className={r.bannerText}>
                <b>Couldn&apos;t refresh the room.</b> <span className={r.bannerSub}>Showing what we had. {loadFailed}</span>
              </span>
              <Button size="S" variant="outline" onClick={() => void load()}>
                Retry
              </Button>
            </div>
          )}
        </>
      }
    >
      <div className={s.page}>
        <Link href={`/rooms/${roomId}`} className={s.back}>
          <Icon name="arrow_back" size={20} />
          Room
        </Link>
        {!room || !pool ? (
          <div aria-busy="true" className={s.skeleton} />
        ) : (
          <>
            {chips.length > 0 && <SquadChips items={chips} pickedId={shownId} onPick={setPicked} />}
            {shown && (
              <SquadPanel
                squad={shown}
                who={whoOf.get(shown.franchiseId) ?? unknown}
                config={room.config}
                phase={phase}
                startsAt={state?.startsAt ?? null}
                firstHammer={firstHammer}
              />
            )}
          </>
        )}
      </div>
    </RoomFrame>
  );
}
