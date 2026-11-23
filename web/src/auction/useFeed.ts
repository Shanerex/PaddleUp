"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { getEvents, type AuctionEvent, type EventsPage } from "../api/client";
import { mergeFeedEvents } from "./feed";
import { isRetryable } from "./retry";

const RETRY_MIN_MS = 1000;
const RETRY_MAX_MS = 15_000;

/**
 * The events the Bids feed is built from: what the events page says (read when the feed first goes
 * live, and again after every reconnect or resync, from where the last read ended) plus the bids
 * and passes the live feed applied. `feedView` takes the union, so a reconnect or a new tab shows
 * the same rows as a tab that watched every bid. A failed read keeps what there is and tries again
 * from the same place after a growing wait (1 s up to 15 s) for as long as the feed stays synced (not after a permanent 4xx). One hook instance serves one room: the screen remounts per room.
 */
export function useFeed(
  roomId: string,
  live: { synced: boolean; bids: readonly AuctionEvent[] },
  lotNo: number | null,
  read: (roomId: string, after: number) => Promise<EventsPage> = getEvents,
): AuctionEvent[] {
  const [fetched, setFetched] = useState<AuctionEvent[]>([]);
  const cursor = useRef(0);
  const lot = useRef(lotNo);
  useEffect(() => {
    lot.current = lotNo;
  }, [lotNo]);
  const reader = useRef(read);
  useEffect(() => {
    reader.current = read;
  }, [read]);

  const { synced } = live;
  const [retryTick, setRetryTick] = useState(0);
  const wait = useRef(RETRY_MIN_MS);
  useEffect(() => {
    if (!synced) return;
    let stale = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    // One live read at a time: an effect that is replaced is stale, so its loop stops and its page is
    // dropped, and a read that never answers cannot hold up the next one.
    void (async () => {
      try {
        let more = true;
        while (more && !stale) {
          const page = await reader.current(roomId, cursor.current);
          if (stale) return;
          cursor.current = Math.max(cursor.current, page.lastSeq);
          setFetched((have) => mergeFeedEvents(have, page.events, lot.current));
          more = page.more;
        }
        wait.current = RETRY_MIN_MS;
      } catch (e) {
        // Keep what is shown and read again from the same place, while this effect still stands; a
        // permanent error (a 4xx other than 408 or 429) is not read again until the feed next resyncs.
        if (!stale && isRetryable(e)) {
          timer = setTimeout(() => setRetryTick((n) => n + 1), wait.current);
          wait.current = Math.min(wait.current * 2, RETRY_MAX_MS);
        }
      }
    })();
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [roomId, synced, retryTick]);

  const bids = live.bids;
  return useMemo(() => mergeFeedEvents(fetched, bids, lotNo).filter((e) => e.kind !== "lot_opened"), [fetched, bids, lotNo]);
}
