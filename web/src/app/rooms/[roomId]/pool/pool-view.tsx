"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  addPoolPlayer,
  editPoolPlayer,
  removePoolPlayer,
  resetPool,
  type PoolPlayer,
  type PoolPlayerInput,
} from "../../../../api/client";
import { HOME } from "../../../../auth/redirect";
import {
  classifyPoolError,
  formatPrice,
  hasNewerData,
  originFlag,
  roleCounts,
  roleSlot,
  sourceLine,
  tagText,
  type PackLabels,
} from "../../../../rooms/pool";
import { AppShell, shell } from "../../../../ui/AppShell";
import { Button, ButtonLink } from "../../../../ui/Button";
import { Icon } from "../../../../ui/Icon";
import { ConfirmSheet } from "../../../../ui/Sheet";
import { RoomFrame } from "../room-frame";
import s from "../pool.module.css";
import r from "../room.module.css";
import { frameOf, usePoolData } from "../use-pool-data";
import { PlayerSheet } from "./player-sheet";

const PAGE = 100;

type SheetState = null | { kind: "edit"; player: PoolPlayer } | { kind: "add" } | { kind: "remove"; player: PoolPlayer } | { kind: "reset" };
type Banner = null | { tone: "stale" | "locked"; body: string };

/**
 * A room's player pool (docs/design Room `pool`, `poolMember`, `poolLive`, `poolEmpty`,
 * `poolStale`; sheets `poolEdit`, `poolAdd`, `poolRemove`, `poolReset`). Every member reads it; the
 * Commissioner edits while `canEdit` is true, and the controls are gone once the auction is live.
 * A 409 reloads the pool and explains why the change was not saved.
 */
export function PoolView({ roomId }: { roomId: string }) {
  const { data, missing, loadFailed, load, setPool } = usePoolData(roomId);
  const [sheet, setSheet] = useState<SheetState>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [sheetError, setSheetError] = useState<string | null>(null);
  const [banner, setBanner] = useState<Banner>(null);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState<string | null>(null);
  const [shown, setShown] = useState(PAGE);
  const [gone, setGone] = useState(false);
  const more = useRef<HTMLSpanElement>(null);

  const f = frameOf(data);
  const room = data?.room;
  const pool = data?.pool;
  const labels = room?.packLabels as PackLabels | undefined;
  const locked = room ? !f.pre : false;
  const canEdit = !!pool?.canEdit;

  const visible = useMemo(() => {
    if (!pool) return [];
    const q = query.trim().toLowerCase();
    return pool.players.filter(
      (p) =>
        (roleFilter === null || p.role === roleFilter) &&
        (q === "" || p.name.toLowerCase().includes(q) || p.country.toLowerCase().includes(q)),
    );
  }, [pool, query, roleFilter]);
  const chips = useMemo(() => (pool && labels ? roleCounts(labels, pool.players) : []), [pool, labels]);
  const hasMore = visible.length > shown;

  useEffect(() => {
    const el = more.current;
    if (!el || !hasMore || typeof IntersectionObserver === "undefined") return;
    const io = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && setShown((n) => n + PAGE), { rootMargin: "400px" });
    io.observe(el);
    return () => io.disconnect();
  }, [hasMore, shown]);

  function open(next: SheetState) {
    setFieldErrors({});
    setSheetError(null);
    setSheet(next);
  }

  /** Performs one write. `what` finishes "…wasn't saved" in the stale banner. */
  async function write(what: string, work: () => Promise<Parameters<typeof setPool>[0]>) {
    setBusy(true);
    setFieldErrors({});
    setSheetError(null);
    try {
      const next = await work();
      setPool(next);
      setBanner(null);
      setSheet(null);
    } catch (e) {
      const failure = classifyPoolError(e);
      if (failure.kind === "stale") {
        setSheet(null);
        setBanner({ tone: "stale", body: `We loaded the latest version. ${what} wasn't saved, so make it again.` });
        await load();
      } else if (failure.kind === "locked") {
        setSheet(null);
        setBanner({ tone: "locked", body: "The auction has started, so the pool is locked. Your change wasn't saved." });
        await load();
      } else if (failure.kind === "gone") {
        setGone(true);
      } else if (failure.kind === "fields") {
        setFieldErrors(failure.fields);
      } else {
        setSheetError(failure.message);
        if (failure.refetch) await load();
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

  const noteText =
    pool && room
      ? locked
        ? "Locked. The auction is live, so nobody can change the pool now."
        : !f.commish
          ? `${room.commissioner.displayName} can change the pool until the auction goes live. You can look, not touch.`
          : null
      : null;
  const newer = !!pool && f.commish && canEdit && hasNewerData(pool);
  const edits = pool?.editCount ?? 0;
  const sheetPlayer = sheet?.kind === "edit" || sheet?.kind === "remove" ? sheet.player : null;

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
      active="pool"
      banners={
        <>
          {loadFailed && data && (
            <div className={r.banner} role="alert" style={{ background: "var(--bad-tint)" }}>
              <Icon name="error" style={{ color: "var(--bad)" }} />
              <span className={r.bannerText}>
                <b>Couldn&apos;t refresh the pool.</b> <span className={r.bannerSub}>Showing what we had. {loadFailed}</span>
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
                <b>{banner.tone === "stale" ? "The pool changed in another tab." : "Too late for that."}</b> <span className={r.bannerSub}>{banner.body}</span>
              </span>
            </div>
          )}
        </>
      }
      overlays={
        <>
          {sheet && pool && room && (sheet.kind === "add" || sheet.kind === "edit") && (
            <PlayerSheet
              player={sheet.kind === "edit" ? sheet.player : null}
              pool={pool}
              room={room}
              busy={busy}
              errors={sheetError ? { ...fieldErrors, form: sheetError } : fieldErrors}
              onClose={() => setSheet(null)}
              onRemove={sheet.kind === "edit" ? () => open({ kind: "remove", player: sheet.player }) : undefined}
              onSave={(input: PoolPlayerInput) =>
                void write(
                  sheet.kind === "edit" ? `Your edit to ${sheet.player.name}` : "Your new player",
                  () => (sheet.kind === "edit" ? editPoolPlayer(roomId, sheet.player.id, input) : addPoolPlayer(roomId, input)),
                )
              }
            />
          )}
          {sheet?.kind === "remove" && pool && sheetPlayer && (
            <ConfirmSheet
              icon="person_remove"
              tone="bad"
              title={`Remove ${sheetPlayer.name} from the pool?`}
              body={`${sheetPlayer.name} won't come up in this room's auction. The season data and other rooms don't change.`}
              action="Remove"
              actionVariant="danger"
              busy={busy}
              error={sheetError}
              onClose={() => setSheet(null)}
              onConfirm={() => void write(`Removing ${sheetPlayer.name}`, () => removePoolPlayer(roomId, sheetPlayer.id, pool.version))}
            />
          )}
          {sheet?.kind === "reset" && pool && (
            <ConfirmSheet
              icon="restart_alt"
              tone="bad"
              title={`Reset the pool to v${pool.latestDatasetVersion}?`}
              body={`This room's pool and set order are replaced with season data v${pool.latestDatasetVersion}. ${
                edits > 0 ? `Your ${edits} ${edits === 1 ? "edit is" : "edits are"} thrown away, and you` : "You"
              } can't undo this.`}
              action="Reset pool"
              actionVariant="danger"
              busy={busy}
              error={sheetError}
              onClose={() => setSheet(null)}
              onConfirm={() => void write("Your reset", () => resetPool(roomId, pool.version))}
            />
          )}
        </>
      }
    >
      <div className={s.page}>
        <Link href={`/rooms/${roomId}`} className={s.back}>
          <Icon name="arrow_back" size={20} />
          Room
        </Link>

        {!pool || !room || !labels ? (
          <div aria-busy="true" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {loadFailed ? (
              <div className={r.card} role="alert">
                <b>Couldn&apos;t load the pool.</b>
                <span className={r.note}>{loadFailed}</span>
                <div>
                  <Button variant="primary" onClick={() => void load()}>
                    Try again
                  </Button>
                </div>
              </div>
            ) : (
              <>
                <span className={s.skeleton} style={{ width: 160, height: 32 }} />
                <div className={s.skeleton} style={{ height: 44 }} />
                <div className={s.skeleton} style={{ height: 320 }} />
              </>
            )}
          </div>
        ) : (
          <>
            <div className={s.head}>
              <div className={s.headText}>
                <h2 className={s.title}>Player pool</h2>
                <span className={s.sub}>{sourceLine(pool, labels, { commish: f.commish, locked })}</span>
              </div>
              {canEdit && pool.hasPool && (
                <div className={s.actions}>
                  <Button variant="primary" icon="person_add" onClick={() => open({ kind: "add" })}>
                    Add player
                  </Button>
                  <Button variant="outline" icon="restart_alt" onClick={() => open({ kind: "reset" })}>
                    Reset to latest
                  </Button>
                </div>
              )}
            </div>

            {newer && (
              <div className={s.notice}>
                <Icon name="update" size={22} className={s.noticeIcon} />
                <div className={s.noticeText}>
                  <b>Season data v{pool.latestDatasetVersion} is out.</b>
                  <span className={s.noticeSub}>
                    Your pool is a copy of v{pool.datasetVersion} with {edits} {edits === 1 ? "edit" : "edits"}. Reset to switch to v{pool.latestDatasetVersion}. That discards your edits and set order.
                  </span>
                </div>
              </div>
            )}

            {noteText && (
              <div className={s.note}>
                <Icon name={locked ? "lock" : "visibility"} size={20} />
                <span className={s.noteText}>{noteText}</span>
              </div>
            )}

            {!pool.hasPool ? (
              <div className={s.empty}>
                <span className={s.emptyTile}>
                  <Icon name="inventory_2" size={32} />
                </span>
                <h3 className={s.emptyTitle}>No player pool yet</h3>
                <p className={s.emptyBody}>
                  {pool.latestDatasetVersion == null
                    ? `Nobody has loaded season data for ${labels.packName ?? labels.packId} yet. The auction can't start until this room has a pool.`
                    : `Season data v${pool.latestDatasetVersion} is ready for ${labels.packName ?? labels.packId}. The auction can't start until this room has a pool.`}
                </p>
                {canEdit && (
                  <>
                    <Button
                      variant="primary"
                      icon="download"
                      disabled={pool.latestDatasetVersion == null}
                      busy={busy}
                      onClick={() => void write("Loading the season data", () => resetPool(roomId, 0))}
                    >
                      Load season data
                    </Button>
                    {pool.latestDatasetVersion == null && <span className={s.emptyHint}>Turns on as soon as there&apos;s season data to load.</span>}
                    {sheetError && (
                      <span className={s.fieldError} role="alert" style={{ margin: 0 }}>
                        {sheetError}
                      </span>
                    )}
                  </>
                )}
              </div>
            ) : (
              <div className={s.cols}>
                <div className={s.listCol}>
                  <div className={s.tools}>
                    <label className={s.search}>
                      <Icon name="search" size={20} />
                      <input
                        className={s.searchInput}
                        type="search"
                        placeholder="Search players or countries"
                        aria-label="Search players or countries"
                        value={query}
                        onChange={(e) => {
                          setQuery(e.target.value);
                          setShown(PAGE);
                        }}
                      />
                    </label>
                    <div className={s.chips} role="group" aria-label="Filter by role">
                      <button
                        type="button"
                        className={s.chip}
                        data-all
                        aria-pressed={roleFilter === null}
                        style={{ background: "var(--ink)", color: "var(--bg)" }}
                        onClick={() => {
                          setRoleFilter(null);
                          setShown(PAGE);
                        }}
                      >
                        All <span className={s.count}>{pool.players.length}</span>
                      </button>
                      {chips.map((c) => (
                        <button
                          key={c.key}
                          type="button"
                          className={s.chip}
                          aria-pressed={roleFilter === c.key}
                          style={{ background: `var(--role${c.slot}-bg)`, color: `var(--role${c.slot}-ink)` }}
                          onClick={() => {
                            setRoleFilter(roleFilter === c.key ? null : c.key);
                            setShown(PAGE);
                          }}
                        >
                          {c.label} <span className={s.count}>{c.count}</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className={s.table}>
                    <div className={s.thead}>
                      <span style={{ flex: 1 }}>PLAYER</span>
                      <span style={{ width: 52 }}>ROLE</span>
                      <span style={{ width: 120 }}>SET</span>
                      <span style={{ width: 112, textAlign: "right" }}>BASE</span>
                      {canEdit && <span style={{ width: 36 }} />}
                    </div>
                    {visible.slice(0, shown).map((p) => {
                      const flag = f.commish ? originFlag(p.origin) : null;
                      const tags = tagText(labels, p.tags);
                      const slot = roleSlot(labels, p.role);
                      return (
                        <div key={p.id} className={s.row} data-testid="pool-row">
                          <div className={s.who}>
                            <div className={s.nameLine}>
                              <span className={s.name}>{p.name}</span>
                              {flag && <span className={`${s.flag} ${s.flagTop}`}>{flag}</span>}
                            </div>
                            <div className={s.subLine}>
                              {flag && <span className={`${s.flag} ${s.flagSub}`}>{flag}</span>}
                              <span className={s.subText}>
                                <span className={s.subWide}>{[p.country, tags].filter(Boolean).join(" · ")}</span>
                                <span className={s.subPhone}>{[p.country, tags, p.set].filter(Boolean).join(" · ")}</span>
                              </span>
                            </div>
                          </div>
                          <span className={s.colRole}>
                            <span className={s.roleChip} style={{ background: `var(--role${slot}-bg)`, color: `var(--role${slot}-ink)` }}>
                              {p.role}
                            </span>
                          </span>
                          <span className={s.colSet}>{p.set}</span>
                          <span className={s.colBase}>{formatPrice(p.basePrice, room.config)}</span>
                          {canEdit && (
                            <button type="button" className={s.iconBtn} title="Edit player" aria-label={`Edit ${p.name}`} onClick={() => open({ kind: "edit", player: p })}>
                              <Icon name="edit" />
                            </button>
                          )}
                        </div>
                      );
                    })}
                    {visible.length === 0 && <div className={s.noMatch}>No players match.</div>}
                    <span ref={more} className={s.foot}>
                      Showing {Math.min(shown, visible.length)} of {visible.length}.{hasMore ? " Scroll for more." : ""}
                    </span>
                  </div>
                </div>

                <aside className={s.setsCard} aria-label="Sets">
                  <div className={s.cardHead}>
                    <span className={s.label}>SETS · IN AUCTION ORDER</span>
                    <span className={s.num}>{pool.sets.length}</span>
                  </div>
                  {pool.sets.slice(0, 5).map((x, i) => (
                    <div key={x.name} className={s.setRow}>
                      <span className={s.setIdx}>{String(i + 1).padStart(2, "0")}</span>
                      <span className={s.setName}>{x.name}</span>
                      <span className={s.num}>{x.players}</span>
                    </div>
                  ))}
                  <Link href={`/rooms/${roomId}/sets`} className={s.setsLink}>
                    <Icon name="format_list_numbered" size={20} />
                    {canEdit ? "Change set order" : "See set order"}
                  </Link>
                </aside>
              </div>
            )}
          </>
        )}
      </div>
    </RoomFrame>
  );
}
