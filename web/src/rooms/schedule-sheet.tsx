"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError, scheduleAuction, type AuctionState } from "../api/client";
import { useTicker } from "../auction/useLive";
import { Button } from "../ui/Button";
import { Icon } from "../ui/Icon";
import { Sheet, sheetStyles } from "../ui/Sheet";
import { explainRoomError } from "./errors";
import s from "./schedule-sheet.module.css";

const DEFAULT_HOUR = "20:00";
const DAY_MS = 24 * 60 * 60 * 1000;

const pad = (n: number) => String(n).padStart(2, "0");

/** A date and time as the native inputs hold them, read in the viewer's zone. */
function fields(at: Date): { date: string; time: string } {
  return { date: `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`, time: `${pad(at.getHours())}:${pad(at.getMinutes())}` };
}

/** The picked local date and time as one instant, or null while either field is empty or invalid. */
export function pickedInstant(date: string, time: string): Date | null {
  const d = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const t = /^(\d{2}):(\d{2})$/.exec(time);
  if (!d || !t) return null;
  const at = new Date(Number(d[1]), Number(d[2]) - 1, Number(d[3]), Number(t[1]), Number(t[2]));
  return Number.isNaN(at.getTime()) ? null : at;
}

/** "in 5d 22h", "in 3h 14m", "in 12m". */
export function untilLabel(ms: number): string {
  const mins = Math.max(0, Math.floor(ms / 60_000));
  const days = Math.floor(mins / 1440);
  const hours = Math.floor(mins / 60) % 24;
  if (days > 0) return `in ${days}d ${hours}h`;
  if (hours > 0) return `in ${hours}h ${mins % 60}m`;
  return `in ${mins}m`;
}

function zoneLine(): string {
  let city = "";
  let offset = "";
  try {
    city = (Intl.DateTimeFormat().resolvedOptions().timeZone.split("/").pop() ?? "").replace(/_/g, " ");
    offset = new Intl.DateTimeFormat(undefined, { timeZoneName: "shortOffset" }).formatToParts(new Date()).find((p) => p.type === "timeZoneName")?.value ?? "";
  } catch {
    /* an odd runtime: say less */
  }
  const where = [city, offset && `(${offset})`].filter(Boolean).join(" ");
  return `Your time${where ? ` · ${where}` : ""}. Every manager sees it in their own time.`;
}

type Problem = { kind: "past"; message: string } | { kind: "refused"; message: string } | { kind: "other"; message: string };

/**
 * The schedule sheet (Room `sheetSchedule`, `sheetScheduleError`, `sheetScheduleRefused` and
 * `lobbyReschedule`, auction-screen R-13). Native date and time inputs are read in the viewer's zone
 * and sent as one UTC instant. The preview line uses the server clock; every error shown is the
 * server's reply, and a failed call changes nothing.
 */
export function ScheduleSheet({
  roomId,
  mode,
  startsAt,
  serverNow,
  poolHref,
  onClose,
  onDone,
}: {
  roomId: string;
  /** `new` sets the first start from the room; `move` changes it from the lobby. */
  mode: "new" | "move";
  /** The current start (ISO) to prefill when moving it. */
  startsAt?: string;
  /** The estimated server time in epoch ms, or null before the first sample. */
  serverNow: () => number | null;
  poolHref: string;
  onClose: () => void;
  /** Called with the server's state after it accepted the time. */
  onDone: (state: AuctionState) => void;
}) {
  const [initial] = useState(() => {
    if (mode === "move" && startsAt) return fields(new Date(startsAt));
    const now = serverNow();
    if (now === null) return { date: "", time: "" };
    return { date: fields(new Date(now + DAY_MS)).date, time: DEFAULT_HOUR };
  });
  const [date, setDate] = useState(initial.date);
  const [time, setTime] = useState(initial.time);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [zone] = useState(zoneLine);
  useTicker(10_000);

  const move = mode === "move";
  const picked = pickedInstant(date, time);
  const now = serverNow();
  const ahead = picked !== null && now !== null ? picked.getTime() - now : null;
  const showUntil = ahead !== null && ahead > 0 && problem?.kind !== "past";
  const when =
    picked === null
      ? "Pick a date and time"
      : `${picked.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })} · ${picked.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })} your time`;
  const past = problem?.kind === "past";

  function edit(set: (v: string) => void, v: string) {
    set(v);
    setProblem(null);
  }

  async function submit() {
    if (!picked || busy) return;
    setBusy(true);
    setProblem(null);
    try {
      onDone(await scheduleAuction(roomId, picked.toISOString()));
    } catch (e) {
      const body = e instanceof ApiError ? (e.body as { error?: string; message?: string; field?: string } | null) : null;
      if (e instanceof ApiError && e.status === 400 && body?.field === "startsAt") {
        setProblem({ kind: "past", message: "That time has already gone. Pick a later one." });
      } else if (e instanceof ApiError && e.status === 409 && body?.error === "not_ready" && body.message) {
        setProblem({ kind: "refused", message: `${body.message}. Nothing was saved.` });
      } else {
        setProblem({ kind: "other", message: explainRoomError(e).message });
      }
    } finally {
      setBusy(false);
    }
  }

  const title = move ? "Change the start time?" : "Schedule the auction";
  return (
    <Sheet label={title} onClose={onClose}>
      <span className={sheetStyles.tile} style={{ background: "var(--team-tint)", color: "var(--team-ink)" }}>
        <Icon name={move ? "edit_calendar" : "event"} size={28} />
      </span>
      <div className={sheetStyles.text}>
        <h2 className={sheetStyles.title}>{title}</h2>
        <p className={sheetStyles.body}>
          {move
            ? "Everyone's countdown moves right away. Ready taps stay as they are."
            : "Pick when bidding opens. The room moves to the lobby, and rooms only move forward. Rules and the pool stay editable until it goes live."}
        </p>
      </div>
      <div className={s.pickers}>
        <label className={s.pick}>
          <span className={s.pickLabel}>Date</span>
          <span className={s.well}>
            <input type="date" className={s.input} value={date} onChange={(e) => edit(setDate, e.target.value)} aria-label="Date" />
            <Icon name="calendar_month" size={20} className={s.wellIcon} />
          </span>
        </label>
        <label className={s.pick}>
          <span className={s.pickLabel}>Start time</span>
          <span className={`${s.well} ${past ? s.wellBad : ""}`}>
            <input type="time" className={`${s.input} ${s.mono}`} value={time} onChange={(e) => edit(setTime, e.target.value)} aria-label="Start time" />
            <Icon name="schedule" size={20} className={s.wellIcon} />
          </span>
        </label>
      </div>
      {past && (
        <span className={s.fieldError} role="alert" data-testid="schedule-past">
          {problem.message}
        </span>
      )}
      <span className={s.zone}>{zone}</span>
      <div className={s.preview}>
        <div className={s.previewText}>
          <span className={s.previewLabel}>BIDDING OPENS</span>
          <span className={s.previewWhen} data-testid="schedule-when">
            {when}
          </span>
        </div>
        <span className={`${s.previewIn} ${showUntil ? "" : s.previewNone}`} data-testid="schedule-until">
          {showUntil ? untilLabel(ahead) : "—"}
        </span>
      </div>
      {problem && problem.kind !== "past" && (
        <div className={s.refused} role="alert" data-testid="schedule-refused">
          <Icon name="block" size={22} style={{ color: "var(--bad)" }} />
          <div className={s.refusedText}>
            <span className={s.refusedTitle}>{problem.kind === "refused" ? "Can't schedule yet" : "Couldn't schedule"}</span>
            <span className={s.refusedBody}>{problem.message}</span>
            {problem.kind === "refused" && (
              <Link href={poolHref} className={s.refusedLink}>
                Open player pool
              </Link>
            )}
          </div>
        </div>
      )}
      <div className={sheetStyles.actions}>
        <Button variant="outline" size="L" onClick={onClose}>
          Cancel
        </Button>
        <Button variant="primary" size="L" busy={busy} disabled={!picked} onClick={() => void submit()}>
          {move ? "Change start time" : "Schedule auction"}
        </Button>
      </div>
    </Sheet>
  );
}
