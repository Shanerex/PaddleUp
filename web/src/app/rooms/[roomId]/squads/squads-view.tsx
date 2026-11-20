"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import type { Member } from "../../../../api/client";
import { squadView } from "../../../../auction/squads";
import { useLive } from "../../../../auction/useLive";
import { HOME } from "../../../../auth/redirect";
import { AppShell, shell } from "../../../../ui/AppShell";
import { Button, ButtonLink } from "../../../../ui/Button";
import { Icon } from "../../../../ui/Icon";
import { RoomFrame } from "../room-frame";
import r from "../room.module.css";
import { frameOf, usePoolData } from "../use-pool-data";
import { SquadChips, SquadPanel, type SquadPhase, type SquadWho } from "./squad-panel";
import s from "./squads.module.css";

/**
 * Squads (docs/design Room `squadLive`, `squadEmpty`): one chip per franchise and the picked
 * franchise's squad. The auction state's `standings` carry every franchise's squad and purse; the
 * live feed refreshes them on each hammer. Every room member sees every squad.
 */
export function SquadsView({ roomId }: { roomId: string }) {
  const { data, missing, loadFailed, load } = usePoolData(roomId);
  const { snapshot } = useLive(roomId);
  const state = snapshot.state;
  const [picked, setPicked] = useState<string | null>(null);

  const f = frameOf(data);
  const room = data?.room;
  const pool = data?.pool;
  const members = data?.members;

  const whoOf = useMemo(() => {
    const byId = new Map<string, SquadWho>();
    for (const m of (members ?? []) as Member[]) {
      if (m.franchise) byId.set(m.franchise.id, { name: m.franchise.name, monogram: m.franchise.monogram, colour: m.franchise.colour, manager: m.displayName, mine: m.franchise.id === room?.myFranchiseId });
    }
    return byId;
  }, [members, room?.myFranchiseId]);

  const squads = useMemo(() => (state && room && pool ? squadView(state, pool, room) : null), [state, room, pool]);

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
  const chips = franchiseIds.map((id) => {
    const who = whoOf.get(id) ?? unknown;
    return { id, label: who.mine ? "You" : who.name.split(" ")[0], monogram: who.monogram, colour: who.colour };
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
      sub={f.sub}
      mine={f.mine}
      loaded={!!room}
      commish={f.commish}
      pre={f.pre}
      active="squads"
      banners={
        loadFailed && data ? (
          <div className={r.banner} role="alert" style={{ background: "var(--bad-tint)" }}>
            <Icon name="error" style={{ color: "var(--bad)" }} />
            <span className={r.bannerText}>
              <b>Couldn&apos;t refresh the room.</b> <span className={r.bannerSub}>Showing what we had. {loadFailed}</span>
            </span>
            <Button size="S" variant="outline" onClick={() => void load()}>
              Retry
            </Button>
          </div>
        ) : undefined
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
