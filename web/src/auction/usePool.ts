"use client";

import { useCallback, useEffect, useState } from "react";
import { getPool, type Pool } from "../api/client";
import { isRetryable } from "./retry";

const RETRY_MIN_MS = 1000;
const RETRY_MAX_MS = 15_000;

/**
 * The room's player pool, read once for the auction screen: the Rivals sheet and the Your needs card
 * read each squad's roles and tags from it. The pool is locked while the auction is live, so one
 * successful read is enough. A failed read is tried again after a growing wait (1 s, 2 s, 4 s, up to
 * 15 s) until it lands, unless the error is permanent (a 4xx other than 408 or 429), which stays failed; `retry` reads again at once. Nothing is read while `enabled` is false (before
 * go-live the pool can still change, after the end the screen is the closed card) and nothing once the pool is held.
 */
export function usePool(roomId: string, enabled = true) {
  const [pool, setPool] = useState<Pool | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!enabled || pool) return; // the pool is locked while live: one good read is the last
    let current = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let wait = RETRY_MIN_MS;
    const read = () => {
      getPool(roomId)
        .then((p) => {
          if (!current) return;
          setPool(p);
          setFailed(false);
        })
        .catch((e) => {
          if (!current) return;
          setFailed(true);
          if (!isRetryable(e)) return; // a permanent error answers the same again: stay failed until `retry`
          timer = setTimeout(read, wait);
          wait = Math.min(wait * 2, RETRY_MAX_MS);
        });
    };
    read();
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [roomId, attempt, enabled, pool]);
  const retry = useCallback(() => {
    setFailed(false);
    setAttempt((n) => n + 1);
  }, []);
  return { pool, failed, retry };
}
