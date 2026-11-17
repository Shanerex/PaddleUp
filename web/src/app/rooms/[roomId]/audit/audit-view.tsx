"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { ApiError, getAudit, getMembers, getRoom, type AuditEntry, type Member, type Room } from "../../../../api/client";
import { AUDIT_FILTERS, AUDIT_TAG, auditText, inFilter, type AuditFilter } from "../../../../auction/audit";
import { explainRoomError } from "../../../../rooms/errors";
import { hueOf } from "../../../../rooms/hues";
import { formatMoney } from "../../../../rooms/money";
import { isPreAuction } from "../../../../rooms/state";
import { Button } from "../../../../ui/Button";
import { Icon } from "../../../../ui/Icon";
import { Monogram } from "../../../../ui/kit";
import { RoomFrame } from "../room-frame";
import s from "./audit.module.css";

const BRAND_HUE = 38;
const POLL_MS = 5000;

/** Local clock time for a row; the UTC instant is the tooltip, since times are stored in UTC. */
const clockTime = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false });
const dateTime = (iso: string) =>
  `${new Date(iso).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })}, ${new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}`;

/**
 * The audit log (Room `audit`, App Screens 9b and 9c): every bid, pass, lifeline, RTM and admin
 * action of the room's auction, read-only for every member, newest first. The server filters
 * nothing the screen needs: the filter chips narrow what was read, and new entries are polled.
 */
export function AuditView({ roomId }: { roomId: string }) {
  const [room, setRoom] = useState<Room | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [entries, setEntries] = useState<AuditEntry[] | null>(null);
  const [filter, setFilter] = useState<AuditFilter>("all");
  const [missing, setMissing] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);
  const last = useRef(0);

  /**
   * Reads every page after the last seq seen and appends it. A read asked for while another is
   * still running joins it (the poll, a retry and the first load can overlap), and rows are kept by
   * seq, so a page can never be listed twice.
   */
  const reading = useRef<Promise<void> | null>(null);
  const read = useCallback((): Promise<void> => {
    reading.current ??= (async () => {
      for (let guard = 0; guard < 100; guard++) {
        const page = await getAudit(roomId, "all", last.current);
        last.current = Math.max(last.current, page.lastSeq);
        setEntries((prev) => {
          const have = new Set((prev ?? []).map((e) => e.seq));
          const fresh = page.entries.filter((e) => !have.has(e.seq));
          return prev !== null && fresh.length === 0 ? prev : [...(prev ?? []), ...fresh];
        });
        if (!page.more) return;
      }
    })().finally(() => {
      reading.current = null;
    });
    return reading.current;
  }, [roomId]);

  const load = useCallback(
    (): Promise<void> =>
      Promise.all([getRoom(roomId), getMembers(roomId)])
        .then(([r, m]) => {
          setRoom(r);
          setMembers(m);
          return read();
        })
        .then(() => setFailed(null))
        .catch((e) => {
          if (e instanceof ApiError && e.status === 404) setMissing(true);
          else setFailed(explainRoomError(e).message);
        }),
    [roomId, read],
  );

  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    const id = setInterval(() => void read().catch(() => {}), POLL_MS);
    return () => clearInterval(id);
  }, [read]);

  const mine = members.find((m) => m.franchise && m.franchise.id === room?.myFranchiseId)?.franchise;
  const commish = room?.myRole === "commissioner";
  const franchises = useMemo(() => new Map(members.flatMap((m) => (m.franchise ? [[m.franchise.id, m.franchise] as const] : []))), [members]);
  const text = (e: AuditEntry) =>
    auditText(e, {
      name: (id) => (id && id === room?.myFranchiseId ? "You" : ((id && franchises.get(id)?.name) ?? "A franchise")),
      money: (n) => (room ? formatMoney(n, room.config) : String(n)),
      local: dateTime,
    });

  const shown = (entries ?? []).filter((e) => inFilter(e, filter)).reverse();
  const sub = !room ? "" : mine ? `${mine.name}${commish ? " · Commissioner" : ""}` : "Commissioner · no franchise";
  return (
    <RoomFrame
      roomId={roomId}
      hue={mine ? hueOf(mine.colour) : BRAND_HUE}
      roomName={room?.config.name ?? ""}
      state={room?.state}
      sub={missing ? "" : `${sub}${sub ? " · " : ""}Audit log`}
      mine={mine}
      loaded={!!room}
      commish={!!commish}
      pre={room ? isPreAuction(room.state) : false}
      active="room"
    >
      <div className={s.page} data-testid="audit">
        <div>
          <h2 className={s.title}>Audit log</h2>
          <p className={s.lede}>Every bid, pass, RTM, lifeline and admin action. Append-only. Lifelines show type and lot, never what the advisor said.</p>
        </div>
        {missing ? (
          <div className={s.empty} role="alert">
            <span className={s.emptyTitle}>Can&apos;t find that room</span>
            <span className={s.emptyBody}>It doesn&apos;t exist, or you&apos;re not in it.</span>
          </div>
        ) : failed ? (
          <div className={s.empty} role="alert">
            <span className={s.emptyTitle}>Couldn&apos;t load the audit log</span>
            <span className={s.emptyBody}>{failed}</span>
            <Button variant="primary" onClick={() => void load()}>
              Try again
            </Button>
          </div>
        ) : (
          <>
            <div className={s.filters} role="group" aria-label="Filter">
              {AUDIT_FILTERS.map((f) => (
                <button key={f.key} type="button" className={`${s.filter} ${filter === f.key ? s.filterOn : ""}`} aria-pressed={filter === f.key} data-testid={`filter-${f.key}`} onClick={() => setFilter(f.key)}>
                  {f.label}
                </button>
              ))}
            </div>
            {entries !== null && shown.length === 0 ? (
              <div className={s.empty} data-testid="audit-empty">
                <span className={s.emptyTile}>
                  <Icon name="receipt_long" size={28} />
                </span>
                <span className={s.emptyTitle}>Nothing logged yet</span>
                <span className={s.emptyBody}>The log starts with the first auction event. New entries appear here live, newest first.</span>
              </div>
            ) : (
              <ul className={s.list} aria-busy={entries === null}>
                {shown.map((e) => {
                  const who = e.franchiseId ? franchises.get(e.franchiseId) : undefined;
                  return (
                    <li key={e.seq} className={`${s.row} ${e.category === "admin" ? s.rowAdmin : ""}`} data-testid="audit-row" data-kind={e.kind}>
                      <time className={s.time} dateTime={e.at} title={`${e.at} (UTC)`}>
                        {clockTime(e.at)}
                      </time>
                      {who ? (
                        <Monogram text={who.monogram} colour={who.colour} size={24} />
                      ) : (
                        <span className={s.mark} style={{ "--m": 0 } as CSSProperties}>
                          <Icon name={e.category === "lifelines" ? "lightbulb" : "shield_person"} size={14} />
                        </span>
                      )}
                      <span className={s.text}>{text(e)}</span>
                      <span className={`${s.tag} ${s[`tag${e.category[0].toUpperCase()}${e.category.slice(1)}`] ?? ""}`}>{AUDIT_TAG[e.category] ?? e.category.toUpperCase()}</span>
                    </li>
                  );
                })}
              </ul>
            )}
          </>
        )}
      </div>
    </RoomFrame>
  );
}
