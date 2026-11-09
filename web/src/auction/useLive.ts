"use client";

import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { getAuction, getTicket } from "../api/client";
import { ServerClock } from "./clock";
import { createLive, stompConnect } from "./live";

/** The live feed of a room's auction, started on mount and stopped on unmount. */
export function useLive(roomId: string) {
  const { live, clock } = useMemo(() => {
    const clock = new ServerClock();
    return { clock, live: createLive({ roomId, getAuction, getTicket, connect: stompConnect, clock }) };
  }, [roomId]);
  useEffect(() => {
    live.start();
    return () => live.stop();
  }, [live]);
  const snapshot = useSyncExternalStore(live.subscribe, live.snapshot, live.snapshot);
  return { live, clock, snapshot };
}

/** Re-renders every `ms` while `active`, so clocks tick. Returns a counter nothing needs to read. */
export function useTicker(ms: number, active = true): number {
  const [n, setN] = useState(0);
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => setN((x) => x + 1), ms);
    return () => clearInterval(id);
  }, [ms, active]);
  return n;
}
