"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, getMembers, getPool, getRoom, type Member, type Pool, type Room } from "../../../api/client";
import { hueOf } from "../../../rooms/hues";
import { explainRoomError } from "../../../rooms/errors";
import { isPreAuction } from "../../../rooms/state";

const BRAND_HUE = 38;

export interface PoolData {
  room: Room;
  members: Member[];
  pool: Pool;
}

/**
 * Loads a room, its members and its pool together, and reloads when the tab becomes visible again.
 * The latest request wins, so a reload after a write is never overwritten by an older read.
 */
export function usePoolData(roomId: string) {
  const [data, setData] = useState<PoolData | null>(null);
  const [missing, setMissing] = useState(false);
  const [loadFailed, setLoadFailed] = useState<string | null>(null);
  const seq = useRef(0);

  const load = useCallback((): Promise<void> => {
    const mine = ++seq.current;
    return Promise.all([getRoom(roomId), getMembers(roomId), getPool(roomId)])
      .then(([room, members, pool]) => {
        if (mine !== seq.current) return;
        setData({ room, members, pool });
        setLoadFailed(null);
        setMissing(false);
      })
      .catch((e) => {
        if (mine !== seq.current) return;
        if (e instanceof ApiError && e.status === 404) setMissing(true);
        else setLoadFailed(explainRoomError(e).message);
      });
  }, [roomId]);

  useEffect(() => {
    void load();
    const onVisible = () => document.visibilityState === "visible" && void load();
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [load]);

  /** Shows a pool the server just returned from a write, without another round trip. */
  const setPool = useCallback((pool: Pool) => {
    seq.current++;
    setData((d) => (d ? { ...d, pool } : d));
  }, []);

  return { data, missing, loadFailed, load, setPool };
}

/** What the room frame needs from a loaded room: hue, header line and the caller's franchise. */
export function frameOf(data: PoolData | null) {
  const room = data?.room;
  const mine = data?.members.find((m) => m.franchise && m.franchise.id === room?.myFranchiseId)?.franchise ?? null;
  const commish = room?.myRole === "commissioner";
  return {
    room,
    mine,
    commish: !!commish,
    pre: room ? isPreAuction(room.state) : false,
    hue: mine ? hueOf(mine.colour) : BRAND_HUE,
    sub: !room ? "" : mine ? `${mine.name}${commish ? " · Commissioner" : ""}` : "Commissioner · no franchise",
  };
}
