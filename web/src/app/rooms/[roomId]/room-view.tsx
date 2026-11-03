"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ApiError,
  getInvite,
  getMembers,
  getRoom,
  getTransitions,
  type Invite,
  type Member,
  type Room,
  type Transition,
} from "../../../api/client";
import { explainRoomError } from "../../../rooms/errors";
import { teamStyle } from "../../../rooms/hues";
import { STATE_LABELS } from "../../../rooms/state";
import { RoomHeader } from "./room-header";

interface Loaded {
  room: Room;
  members: Member[];
  invite: Invite | null;
  history: Transition[];
}

async function load(roomId: string): Promise<Loaded> {
  const [room, members, history] = await Promise.all([
    getRoom(roomId),
    getMembers(roomId),
    getTransitions(roomId),
  ]);
  const invite = room.myRole === "commissioner" ? await getInvite(roomId) : null;
  return { room, members, invite, history };
}

/**
 * A room as a member sees it. The server is the only authority on its state, so the screen is
 * reloaded when it opens, when the tab regains focus and after every change.
 */
export function RoomView({ roomId }: { roomId: string }) {
  const [data, setData] = useState<Loaded | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloads, setReloads] = useState(0);
  const refresh = useCallback(() => setReloads((n) => n + 1), []);

  // Each reload cancels the one before it, so a slow older answer never overwrites a newer one.
  useEffect(() => {
    let active = true;
    load(roomId)
      .then((next) => {
        if (!active) return;
        setData(next);
        setError((e) => (e && /could not find|not in it/.test(e) ? null : e));
      })
      .catch((e) => {
        if (!active) return;
        // Removed from the room, or it never existed: stop showing what we last knew.
        if (e instanceof ApiError && e.status === 404) setData(null);
        setError(explainRoomError(e).message);
      });
    return () => {
      active = false;
    };
  }, [roomId, reloads]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible") refresh();
    };
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [refresh]);

  if (!data) {
    return (
      <section className="card">
        <p aria-live="assertive" className="alert">
          {error}
        </p>
        {!error && <p>Loading the room.</p>}
      </section>
    );
  }

  const { room, members, invite, history } = data;
  const mine = room.myFranchiseId
    ? members.find((m) => m.franchise?.id === room.myFranchiseId)?.franchise
    : undefined;

  return (
    <div className={`page-stack ${mine ? "team" : ""}`} style={mine ? teamStyle(mine.colour) : undefined}>
      <p aria-live="assertive" className="alert">
        {error}
      </p>
      <RoomHeader
        room={room}
        members={members}
        invite={invite}
        onChanged={refresh}
        onError={setError}
      />
      <section className="card" aria-label="History">
        <h2>History</h2>
        {history.length === 0 ? (
          <p>No changes yet. The room is in {STATE_LABELS[room.state]}.</p>
        ) : (
          <ul className="history">
            {history.map((t) => (
              <li key={`${t.occurredAt}-${t.to}`}>
                {STATE_LABELS[t.from]} to {STATE_LABELS[t.to]}
                {t.actor ? ` by ${t.actor.displayName}` : ""} on{" "}
                <time dateTime={t.occurredAt}>{new Date(t.occurredAt).toLocaleString()}</time>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
