"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type CSSProperties, type FormEvent } from "react";
import { ApiError, joinRoom, previewJoin, type JoinPreview } from "../../../api/client";
import { HOME } from "../../../auth/redirect";
import { formatCode, normaliseCode } from "../../../rooms/codes";
import { explainRoomError } from "../../../rooms/errors";
import {
  checkFranchise,
  firstFreeColour,
  FranchiseFields,
  type FranchiseDraft,
} from "../../../rooms/franchise-fields";
import { hueOf } from "../../../rooms/hues";
import { Button, ButtonLink } from "../../../ui/Button";
import { BackLink, shell } from "../../../ui/AppShell";
import { Icon } from "../../../ui/Icon";
import { useLatest } from "../../../ui/useLatest";
import s from "../join.module.css";

export type JoinFailure = "invalid" | "full" | "locked" | "limited";

/** Which Home `joinError` variant a failed preview or redeem shows, or null to show it inline. */
export function failureOf(error: unknown): JoinFailure | null {
  if (!(error instanceof ApiError)) return null;
  const code = (error.body as { error?: string } | null)?.error;
  if (error.status === 404) return "invalid";
  if (error.status === 429) return "limited";
  if (error.status === 409 && code === "room_full") return "full";
  if (error.status === 409 && code === "room_locked") return "locked";
  return null;
}

const FAILURES: Record<JoinFailure, { icon: string; bad: boolean; title: (room: string) => string; body: (seats?: number) => string }> = {
  invalid: {
    icon: "key_off",
    bad: true,
    title: () => "This code doesn't work",
    body: () => "It may have expired or been replaced by a new one. Ask your Commissioner for a fresh code.",
  },
  full: {
    icon: "group_off",
    bad: true,
    title: (room) => `${room || "This room"} is full`,
    body: (seats) =>
      `${seats ? `All ${seats} seats are` : "Every seat is"} taken. If the Commissioner frees one up, this code will work again.`,
  },
  locked: {
    icon: "gavel",
    bad: true,
    title: () => "The auction has started",
    body: () => "New managers can't join once bidding is live. Catch them next season.",
  },
  limited: {
    icon: "hourglass_top",
    bad: false,
    title: () => "Too many tries",
    body: () => "Take a breather. You can try a code again in a minute.",
  },
};

function clock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}

/** Home `joinError`: one layout, four causes. */
export function JoinError({
  kind,
  code,
  roomName = "",
  seats,
  retryAfter = 60,
  onRetry,
}: {
  kind: JoinFailure;
  code: string;
  roomName?: string;
  seats?: number;
  retryAfter?: number;
  onRetry?: () => void;
}) {
  const f = FAILURES[kind];
  const [wait, setWait] = useState(kind === "limited" ? retryAfter : 0);
  useEffect(() => {
    if (wait <= 0) return;
    const t = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(t);
  }, [wait]);
  return (
    <div className={shell.narrow}>
      <BackLink />
      <span
        className={s.tile}
        style={f.bad ? { background: "var(--bad-tint)", color: "var(--bad)" } : { background: "var(--warn-tint)", color: "var(--warn-ink)" }}
      >
        <Icon name={f.icon} size={28} />
      </span>
      <div className={s.titles} style={{ gap: 6 }}>
        <span className={s.eyebrowMuted}>
          INVITE · {formatCode(code)}
        </span>
        <h1 className={s.errTitle}>{f.title(roomName)}</h1>
        <p className={s.errBody}>{f.body(seats)}</p>
      </div>
      <div className={shell.row}>
        {kind === "limited" ? (
          <Button variant="brand" size="M48" disabled={wait > 0} onClick={onRetry}>
            {wait > 0 ? (
              <>
                Try again in <span className="num">{clock(wait)}</span>
              </>
            ) : (
              "Try again"
            )}
          </Button>
        ) : (
          <ButtonLink href="/join" variant="brand" size="M48">
            Try another code
          </ButtonLink>
        )}
        <ButtonLink href={HOME} variant="outline" size="M48">
          Back to rooms
        </ButtonLink>
      </div>
    </div>
  );
}

/** Home `join`: name the new franchise, preview the room header in its colour, join. */
export function JoinForm({ code: rawCode }: { code: string }) {
  const code = normaliseCode(rawCode);
  const router = useRouter();
  const routerRef = useLatest(router);
  const [preview, setPreview] = useState<JoinPreview | null>(null);
  const [failure, setFailure] = useState<{ kind: JoinFailure; retryAfter: number } | null>(null);
  const [draft, setDraft] = useState<FranchiseDraft>({ name: "", monogram: "", colour: "", monogramTouched: false });
  const [error, setError] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    let active = true;
    previewJoin(code)
      .then((next) => {
        if (!active) return;
        if (next.alreadyJoined && next.roomId) {
          routerRef.current.replace(`/rooms/${next.roomId}`);
          return;
        }
        setFailure(null);
        if (next.full) {
          setPreview(next);
          setFailure({ kind: "full", retryAfter: 0 });
          return;
        }
        setPreview(next);
        setDraft((d) => ({
          ...d,
          colour: d.colour && !next.takenColours.includes(d.colour) ? d.colour : firstFreeColour(next.takenColours),
        }));
      })
      .catch((e) => {
        if (!active) return;
        const kind = failureOf(e);
        if (kind) setFailure({ kind, retryAfter: (e as ApiError).retryAfter ?? 60 });
        else setError(explainRoomError(e).message);
      });
    return () => {
      active = false;
    };
  }, [code, routerRef]);

  useEffect(() => load(), [load]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const problems = checkFranchise(draft);
    setNameError(problems.name ?? null);
    if (Object.keys(problems).length > 0) {
      setError(problems.monogram ?? problems.colour ?? null);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const joined = await joinRoom(code, { name: draft.name.trim(), colour: draft.colour, monogram: draft.monogram.trim() });
      router.push(`/rooms/${joined.roomId}`);
    } catch (e) {
      setBusy(false);
      const kind = failureOf(e);
      if (kind) {
        setFailure({ kind, retryAfter: (e as ApiError).retryAfter ?? 60 });
        return;
      }
      const explained = explainRoomError(e);
      if (e instanceof ApiError && e.status === 409) setNameError(explained.message);
      else setError(explained.message);
      if (explained.refetch) load();
    }
  }

  if (failure) {
    return (
      <JoinError
        kind={failure.kind}
        code={code}
        roomName={preview?.roomName}
        seats={preview?.maxManagers}
        retryAfter={failure.retryAfter}
        onRetry={load}
      />
    );
  }

  if (!preview) {
    return (
      <div className={shell.narrow} aria-busy={!error}>
        <BackLink />
        {error ? (
          <p role="alert" className={s.alert}>
            {error}
          </p>
        ) : (
          <>
            <span className={s.skeletonTitle} />
            <span className={s.skeletonBlock} />
            <span className={s.skeletonBlock} />
          </>
        )}
      </div>
    );
  }

  const hue = { "--mh": draft.colour ? hueOf(draft.colour) : 38 } as CSSProperties;
  const meta = [preview.packName, preview.competition, `${preview.franchiseCount} of ${preview.maxManagers} managers`]
    .filter(Boolean)
    .join(" · ");
  const shownName = draft.name.trim() || "your franchise";

  return (
    <div className={s.wrap} style={hue}>
      <form className={s.left} onSubmit={submit} noValidate>
        <BackLink />
        <div className={s.titles}>
          <span className={s.eyebrow}>
            INVITE · {formatCode(code)}
          </span>
          <h1 className={s.title}>Join {preview.roomName}</h1>
          <span className={s.meta}>{meta}</span>
        </div>
        <FranchiseFields draft={draft} taken={preview.takenColours} onChange={setDraft} nameError={nameError} />
        {error && (
          <p role="alert" className={s.alert}>
            {error}
          </p>
        )}
        <button type="submit" className={s.join} disabled={busy || !draft.colour}>
          {busy ? "Joining…" : `Join as ${shownName}`}
        </button>
      </form>
      <div className={s.right} aria-hidden="true">
        <span className={s.previewLabel}>PREVIEW · YOUR ROOM HEADER</span>
        <div className={s.preview}>
          <div className={s.previewHead}>
            <div className={s.previewRoom}>
              <Icon name="arrow_back" size={22} />
              {preview.roomName}
            </div>
            <div className={s.previewTeam}>
              <span className={s.previewMono}>{draft.monogram || "?"}</span>
              <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                <span className={s.previewName}>{draft.name.trim() || "Your franchise"}</span>
                <span className={s.previewSub}>
                  {[preview.competition, `${preview.franchiseCount + 1} of ${preview.maxManagers} managers`].filter(Boolean).join(" · ")}
                </span>
              </div>
            </div>
          </div>
          <div className={s.previewBody}>
            <span className={s.previewBtn}>Edit franchise</span>
            <span className={s.previewNote}>Your colour tints the whole room for you. Rivals see it on your monogram.</span>
          </div>
        </div>
      </div>
    </div>
  );
}
