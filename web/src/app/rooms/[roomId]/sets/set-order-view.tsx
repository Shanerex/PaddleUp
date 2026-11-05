"use client";

import Link from "next/link";
import { useState } from "react";
import { saveSetOrder } from "../../../../api/client";
import { HOME } from "../../../../auth/redirect";
import { classifyPoolError } from "../../../../rooms/pool";
import { AppShell, shell } from "../../../../ui/AppShell";
import { Button, ButtonLink } from "../../../../ui/Button";
import { Icon } from "../../../../ui/Icon";
import { RoomFrame } from "../room-frame";
import s from "../pool.module.css";
import r from "../room.module.css";
import { frameOf, usePoolData } from "../use-pool-data";

interface Draft {
  /** The pool version the order was started from; a newer pool discards the draft. */
  version: number;
  order: string[];
  moved: string | null;
}

type Banner = null | { tone: "stale" | "locked"; body: string };

/**
 * Set order (docs/design Room `sets`, `setsMember`). The Commissioner moves sets up and down (or
 * drags the handle) and saves the whole order once with the pool version; members read it. A 409
 * reloads the pool and explains why the order was not saved.
 */
export function SetOrderView({ roomId }: { roomId: string }) {
  const { data, missing, loadFailed, load, setPool } = usePoolData(roomId);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [banner, setBanner] = useState<Banner>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [dragging, setDragging] = useState<string | null>(null);
  const [gone, setGone] = useState(false);

  const f = frameOf(data);
  const room = data?.room;
  const pool = data?.pool;
  const canEdit = !!pool?.canEdit && !!pool.hasPool;
  const saved = pool?.sets.map((x) => x.name) ?? [];
  const active = draft && pool && draft.version === pool.version ? draft : null;
  const order = active?.order ?? saved;
  const dirty = !!active && active.order.join("\u0000") !== saved.join("\u0000");
  const counts = new Map((pool?.sets ?? []).map((x) => [x.name, x.players]));

  function place(name: string, to: number) {
    if (!pool) return;
    const from = order.indexOf(name);
    if (from < 0 || to < 0 || to >= order.length || to === from) return;
    const next = [...order];
    next.splice(from, 1);
    next.splice(to, 0, name);
    setDraft({ version: pool.version, order: next, moved: name });
  }

  async function save() {
    if (!pool || !active) return;
    setBusy(true);
    setError(null);
    try {
      const next = await saveSetOrder(roomId, pool.version, active.order);
      setPool(next);
      setDraft(null);
      setBanner(null);
    } catch (e) {
      const failure = classifyPoolError(e);
      if (failure.kind === "stale") {
        setDraft(null);
        setBanner({ tone: "stale", body: "We loaded the latest version. Your new order wasn't saved, so make it again." });
        await load();
      } else if (failure.kind === "locked") {
        setDraft(null);
        setBanner({ tone: "locked", body: "The auction has started, so the set order is locked. Your change wasn't saved." });
        await load();
      } else if (failure.kind === "gone") {
        setGone(true);
      } else {
        setError(failure.kind === "fields" ? Object.values(failure.fields)[0] ?? "That order isn't allowed." : failure.message);
        if (failure.kind === "other" && failure.refetch) await load();
      }
    } finally {
      setBusy(false);
    }
  }

  if (missing || gone) {
    return (
      <AppShell>
        <div className={shell.centered}>
          <div className={shell.empty}>
            <span className={shell.emptyTile} style={{ background: "var(--surface-2)", color: "var(--ink-2)" }}>
              <Icon name="door_front" size={36} />
            </span>
            <h1 className={shell.emptyTitle}>Can&apos;t find that room</h1>
            <p className={shell.emptyBody}>It doesn&apos;t exist, or you&apos;re not in it. Ask the Commissioner for an invite code.</p>
            <ButtonLink href={HOME} variant="brand" size="M48">
              Back to rooms
            </ButtonLink>
          </div>
        </div>
      </AppShell>
    );
  }

  const locked = room ? !f.pre : false;
  const noteText = !pool
    ? null
    : locked
      ? "Locked. The auction is live, so nobody can change the set order now."
      : !f.commish && room
        ? `${room.commissioner.displayName} sets the order until the auction goes live.`
        : null;

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
      active="sets"
      banners={
        <>
          {loadFailed && data && (
            <div className={r.banner} role="alert" style={{ background: "var(--bad-tint)" }}>
              <Icon name="error" style={{ color: "var(--bad)" }} />
              <span className={r.bannerText}>
                <b>Couldn&apos;t refresh the set order.</b> <span className={r.bannerSub}>Showing what we had. {loadFailed}</span>
              </span>
              <Button size="S" variant="outline" onClick={() => void load()}>
                Retry
              </Button>
            </div>
          )}
          {banner && (
            <div className={r.banner} role="alert" style={{ background: banner.tone === "stale" ? "var(--warn-tint)" : "var(--bad-tint)" }}>
              <Icon name={banner.tone === "stale" ? "sync_problem" : "block"} style={{ color: banner.tone === "stale" ? "var(--warn-ink)" : "var(--bad)" }} />
              <span className={r.bannerText}>
                <b>{banner.tone === "stale" ? "The set order changed in another tab." : "Too late for that."}</b> <span className={r.bannerSub}>{banner.body}</span>
              </span>
            </div>
          )}
        </>
      }
    >
      <div className={`${s.page} ${s.narrow}`}>
        <Link href={`/rooms/${roomId}`} className={s.back}>
          <Icon name="arrow_back" size={20} />
          Room
        </Link>

        <div className={s.headText}>
          <h2 className={s.title}>Set order</h2>
          <span className={s.lede}>Lots come up set by set, top to bottom. Every player is in exactly one set.</span>
        </div>

        {noteText && (
          <div className={s.note}>
            <Icon name={locked ? "lock" : "visibility"} size={20} />
            <span className={s.noteText}>{noteText}</span>
          </div>
        )}

        {!pool ? (
          <div aria-busy="true">
            {loadFailed ? (
              <div className={r.card} role="alert">
                <b>Couldn&apos;t load the set order.</b>
                <span className={r.note}>{loadFailed}</span>
                <div>
                  <Button variant="primary" onClick={() => void load()}>
                    Try again
                  </Button>
                </div>
              </div>
            ) : (
              <div className={s.skeleton} style={{ height: 360 }} />
            )}
          </div>
        ) : !pool.hasPool ? (
          <div className={s.note}>
            <Icon name="inventory_2" size={20} />
            <span className={s.noteText}>
              No player pool yet, so there are no sets. <Link href={`/rooms/${roomId}/pool`}>Open the player pool</Link>.
            </span>
          </div>
        ) : (
          <>
            <ol className={s.setList} aria-label="Sets in auction order">
              {order.map((name, i) => (
                <li
                  key={name}
                  className={`${s.setItem} ${dirty && active?.moved === name ? s.moved : ""}`}
                  data-testid="set-row"
                  draggable={canEdit}
                  onDragStart={() => canEdit && setDragging(name)}
                  onDragOver={(e) => canEdit && dragging && e.preventDefault()}
                  onDrop={(e) => {
                    e.preventDefault();
                    if (dragging) place(dragging, i);
                    setDragging(null);
                  }}
                  onDragEnd={() => setDragging(null)}
                >
                  {canEdit && <Icon name="drag_indicator" size={20} className={s.handle} />}
                  <span className={s.setNo}>{String(i + 1).padStart(2, "0")}</span>
                  <div className={s.setText}>
                    <div className={s.setTitleLine}>
                      <span className={s.setTitle}>{name}</span>
                      {dirty && active?.moved === name && <span className={s.movedFlag}>MOVED</span>}
                    </div>
                    <span className={s.setCount}>
                      <b>{counts.get(name) ?? 0}</b> {counts.get(name) === 1 ? "player" : "players"}
                    </span>
                  </div>
                  {canEdit && (
                    <div className={s.moveBtns}>
                      <button type="button" className={s.iconBtn} title="Move up" aria-label={`Move ${name} up`} disabled={i === 0} onClick={() => place(name, i - 1)}>
                        <Icon name="arrow_upward" />
                      </button>
                      <button
                        type="button"
                        className={s.iconBtn}
                        title="Move down"
                        aria-label={`Move ${name} down`}
                        disabled={i === order.length - 1}
                        onClick={() => place(name, i + 1)}
                      >
                        <Icon name="arrow_downward" />
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ol>

            {error && (
              <span className={s.fieldError} role="alert" style={{ margin: 0 }}>
                {error}
              </span>
            )}

            {canEdit && dirty && (
              <div className={s.saveBar}>
                <span className={s.saveText}>Order changed</span>
                <button type="button" className={s.discard} disabled={busy} onClick={() => setDraft(null)}>
                  Discard
                </button>
                <button type="button" className={s.save} disabled={busy} aria-busy={busy || undefined} onClick={() => void save()}>
                  Save order
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </RoomFrame>
  );
}
