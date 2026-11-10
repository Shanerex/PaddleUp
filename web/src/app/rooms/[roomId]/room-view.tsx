"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  advanceRoom,
  ApiError,
  createOwnFranchise,
  editFranchise,
  getAuction,
  getInvite,
  getMembers,
  getPool,
  getRoom,
  getTransitions,
  handOver,
  issueInvite,
  removeFranchise,
  revokeInvite,
  type Invite,
  type Member,
  type Pool,
  type Room,
  type RoomState,
  type Transition,
} from "../../../api/client";
import { pageClock } from "../../../auction/clock";
import { HOME } from "../../../auth/redirect";
import { formatCode } from "../../../rooms/codes";
import { explainRoomError } from "../../../rooms/errors";
import { checkFranchise, firstFreeColour, FranchiseFields, type FranchiseDraft } from "../../../rooms/franchise-fields";
import { hueOf } from "../../../rooms/hues";
import { orderedLifelines } from "../../../rooms/lifelines";
import { formatMoneyShort } from "../../../rooms/money";
import { ScheduleSheet } from "../../../rooms/schedule-sheet";
import { isPreAuction } from "../../../rooms/state";
import { AppShell, shell } from "../../../ui/AppShell";
import { Button, ButtonLink } from "../../../ui/Button";
import { Icon } from "../../../ui/Icon";
import { Monogram } from "../../../ui/kit";
import { ConfirmSheet, Sheet, sheetStyles, Toast } from "../../../ui/Sheet";
import { RoomFrame } from "./room-frame";
import s from "./room.module.css";

const BRAND_HUE = 38;
const PHASES = ["Setup", "Retention", "Auction", "Season", "Completed"];
const PHASE_OF: Record<RoomState, number> = {
  setup: 0,
  retention: 1,
  auction_scheduled: 2,
  auction_live: 2,
  season_live: 3,
  completed: 4,
};
const LIFELINE_SHORT: Record<string, string> = { "fit-check": "Fit", "price-ceiling": "Ceiling", "rival-scan": "Scan" };
const ADVANCE: Partial<Record<RoomState, { label: string; icon: string; title: string }>> = {
  retention: { label: "Open retention", icon: "bookmark", title: "Open retention?" },
};
/** Scheduling is not a plain advance: the sheet picks the start and the engine moves the room. */
const SCHEDULE = { label: "Schedule auction", icon: "gavel" };
const VERB: Record<RoomState, string> = {
  setup: "reopened setup",
  retention: "opened retention",
  auction_scheduled: "scheduled the auction",
  auction_live: "started the auction",
  season_live: "started the season",
  completed: "closed the season",
};

function when(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleString(undefined, { month: "short" })} ${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function dateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
}

type SheetState =
  | null
  | { kind: "member"; member: Member }
  | { kind: "remove"; member: Member }
  | { kind: "handover"; member: Member }
  | { kind: "advance"; to: RoomState }
  | { kind: "schedule" }
  | { kind: "revoke" }
  | { kind: "franchise"; create: boolean };

interface Data {
  room: Room;
  members: Member[];
  history: Transition[];
  invite: Invite | null;
  /** The pool summary for the card; null when it could not be read. */
  pool: Pool | null;
  /** The room is scheduled but has no auction (no start time was ever set). */
  noAuction: boolean;
}

/**
 * The room before the auction (docs/design Room `setup`, `setupReady`, `setupAdmin`,
 * `setupManager`, the sheets, `loading`, `loadError`, `staleAction`). The manager's franchise hue
 * drives the room; a Commissioner without a franchise gets the brand hue.
 */
export function RoomView({ roomId, note }: { roomId: string; note?: string }) {
  const router = useRouter();
  const [data, setData] = useState<Data | null>(null);
  const [missing, setMissing] = useState(false);
  const [loadFailed, setLoadFailed] = useState<string | null>(null);
  const [loadedAt, setLoadedAt] = useState<Date | null>(null);
  const [actionError, setActionError] = useState<{ title: string; body: string } | null>(null);
  const [sheet, setSheet] = useState<SheetState>(null);
  const [sheetError, setSheetError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [draft, setDraft] = useState<FranchiseDraft>({ name: "", monogram: "", colour: "", monogramTouched: true });
  const loading = useRef(false);

  const load = useCallback((): Promise<void> => {
    if (loading.current) return Promise.resolve();
    loading.current = true;
    return Promise.all([getRoom(roomId), getMembers(roomId), getTransitions(roomId)])
      .then(async ([room, members, history]) => {
        // The invite is the Commissioner's only; failing to read it never breaks the page.
        const invite = room.myRole === "commissioner" ? await getInvite(roomId).catch(() => null) : null;
        // The pool card is a summary; failing to read it never breaks the page either.
        const pool = await getPool(roomId).catch(() => null);
        // A room that says "scheduled" with no auction has no start time yet.
        const noAuction =
          room.state === "auction_scheduled" &&
          (await getAuction(roomId).then(
            () => false,
            (e) => e instanceof ApiError && e.status === 404,
          ));
        return { room, members, history, invite, pool, noAuction };
      })
      .then((next) => {
        setData(next);
        setLoadedAt(new Date());
        setLoadFailed(null);
        setMissing(false);
      })
      .catch((e) => {
        if (e instanceof ApiError && e.status === 404) setMissing(true);
        else setLoadFailed(explainRoomError(e).message);
      })
      .finally(() => {
        loading.current = false;
      });
  }, [roomId]);

  useEffect(() => {
    void load();
    const onVisible = () => document.visibilityState === "visible" && void load();
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [load]);

  /** Opens a sheet with no leftover error: an error belongs to the sheet that caused it. */
  function open(next: SheetState) {
    setSheetError(null);
    setSheet(next);
  }

  /** Performs a room action; a 409 shows the stale-tab banner and reloads the truth. */
  async function act(work: () => Promise<unknown>) {
    setBusy(true);
    setSheetError(null);
    try {
      await work();
      setActionError(null);
      setSheet(null);
      await load();
    } catch (e) {
      const explained = explainRoomError(e);
      if (e instanceof ApiError && e.status === 409 && explained.refetch) {
        setSheet(null);
        const locked = (e.body as { error?: string } | null)?.error === "room_locked";
        setActionError({
          title: locked ? "Too late for that." : "The room moved on.",
          body: locked ? "The auction has started, so franchises are locked. Your change wasn't saved." : explained.message,
        });
        await load();
      } else if (e instanceof ApiError && e.status === 404) {
        setMissing(true);
      } else {
        setSheetError(explained.message);
        if (explained.refetch) await load();
      }
    } finally {
      setBusy(false);
    }
  }

  async function copyCode(code: string) {
    try {
      await navigator.clipboard.writeText(formatCode(code));
      setToast("Code copied");
    } catch {
      setActionError({ title: "Couldn't copy.", body: "Select the code and copy it by hand." });
    }
  }

  async function shareLink(invite: Invite, roomName: string) {
    if (typeof navigator.share === "function") {
      await navigator.share({ title: roomName, text: `Join ${roomName}: ${formatCode(invite.code)}`, url: invite.link }).catch(() => undefined);
      return;
    }
    try {
      await navigator.clipboard.writeText(invite.link);
      setToast("Link copied");
    } catch {
      setActionError({ title: "Couldn't copy.", body: invite.link });
    }
  }

  if (missing) {
    return (
      <AppShell>
        <div className={shell.centered}>
          <div className={shell.empty}>
            <span className={shell.emptyTile} style={{ background: "var(--surface-2)", color: "var(--ink-2)" }}>
              <Icon name="door_front" size={36} />
            </span>
            <h1 className={shell.emptyTitle}>Can&apos;t find that room</h1>
            <p className={shell.emptyBody}>It doesn&apos;t exist, or you&apos;re not in it. Ask the Commissioner for an invite code.</p>
            <div className={shell.rowCenter}>
              <ButtonLink href={HOME} variant="brand" size="M48">
                Back to rooms
              </ButtonLink>
              <ButtonLink href="/join" variant="outline" size="M48">
                Join with code
              </ButtonLink>
            </div>
          </div>
        </div>
      </AppShell>
    );
  }

  const room = data?.room;
  const members = data?.members ?? [];
  const mine = members.find((m) => m.franchise && m.franchise.id === room?.myFranchiseId)?.franchise;
  const commish = room?.myRole === "commissioner";
  const pre = room ? isPreAuction(room.state) : false;
  const hue = mine ? hueOf(mine.colour) : BRAND_HUE;
  const joined = room?.franchiseCount ?? 0;
  const min = room?.config.minManagers ?? 0;
  const max = room?.config.maxManagers ?? 0;
  const taken = members.map((m) => m.franchise?.colour).filter((c): c is string => !!c);
  const sub = !room ? "" : mine ? `${mine.name}${commish ? " · Commissioner" : ""}` : "Commissioner · no franchise";
  const advanceTo = (room?.advanceTo ?? []).filter((t) => ADVANCE[t]);
  const noAuction = data?.noAuction ?? false;
  const canSchedule = !!room && (room.advanceTo.includes("auction_scheduled") || (room.state === "auction_scheduled" && noAuction));
  const nextSteps = [
    ...advanceTo.map((to) => ({ key: to, ...ADVANCE[to]!, onClick: () => open({ kind: "advance", to }) })),
    ...(canSchedule ? [{ key: "schedule", ...SCHEDULE, onClick: () => open({ kind: "schedule" }) }] : []),
  ];
  const short = Math.max(0, min - joined);

  function openFranchise(create: boolean) {
    setDraft(
      create || !mine
        ? { name: "", monogram: "", colour: firstFreeColour(taken), monogramTouched: false }
        : { name: mine.name, monogram: mine.monogram, colour: mine.colour, monogramTouched: true },
    );
    open({ kind: "franchise", create });
  }

  function saveFranchise(create: boolean) {
    if (!room) return;
    const problems = checkFranchise(draft);
    if (Object.keys(problems).length) {
      setSheetError(problems.name ?? problems.monogram ?? problems.colour ?? null);
      return;
    }
    const body = { name: draft.name.trim(), monogram: draft.monogram.trim(), colour: draft.colour };
    void act(() => (create || !mine ? createOwnFranchise(room.id, body) : editFranchise(room.id, mine.id, body)));
  }

  return (
    <RoomFrame
      roomId={roomId}
      hue={hue}
      roomName={room?.config.name ?? ""}
      state={room?.state}
      sub={sub}
      mine={mine}
      loaded={!!room}
      commish={!!commish}
      pre={pre}
      active="room"
      banners={
        <>
          {loadFailed && data && (
            <div className={s.banner} role="alert" style={{ background: "var(--bad-tint)" }}>
              <Icon name="error" style={{ color: "var(--bad)" }} />
              <span className={s.bannerText}>
                <b>Couldn&apos;t refresh the room.</b> <span className={s.bannerSub}>
                  Showing what we had{loadedAt ? ` at ${loadedAt.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}` : ""}.
                </span>
              </span>
              <Button size="S" variant="outline" onClick={() => void load()}>
                Retry
              </Button>
            </div>
          )}
          {note === "settings" && room && (
            <div className={s.banner} role="status" style={{ background: "var(--info-tint)" }}>
              <Icon name="lock" style={{ color: "var(--info)" }} />
              <span className={s.bannerText}>
                <b>Only the Commissioner changes settings.</b>{" "}
                <span className={s.bannerSub}>{room.commissioner.displayName} can change the rules until the auction goes live.</span>
              </span>
            </div>
          )}
          {actionError && (
            <div className={s.banner} role="alert" style={{ background: "var(--bad-tint)" }}>
              <Icon name="block" style={{ color: "var(--bad)" }} />
              <span className={s.bannerText}>
                <b>{actionError.title}</b> <span className={s.bannerSub}>{actionError.body}</span>
              </span>
            </div>
          )}
        </>
      }
      overlays={
        <>
          {sheet?.kind === "member" && room && sheet.member.franchise && (
            <Sheet label={`Manage ${sheet.member.franchise.name}`} onClose={() => setSheet(null)}>
              <div className={s.sheetWho}>
                <Monogram text={sheet.member.franchise.monogram} colour={sheet.member.franchise.colour} size={40} />
                <div style={{ display: "flex", flexDirection: "column" }}>
                  <span style={{ fontSize: 17, fontWeight: 700 }}>{sheet.member.franchise.name}</span>
                  <span className={s.note}>{sheet.member.displayName}</span>
                </div>
              </div>
              <div className={s.menu}>
                <button type="button" className={s.menuItem} onClick={() => open({ kind: "handover", member: sheet.member })}>
                  <Icon name="swap_horiz" size={22} style={{ color: "var(--ink-2)" }} />
                  <span className={s.menuText}>
                    <span style={{ fontSize: 14, fontWeight: 700 }}>Make Commissioner</span>
                    <span className={s.small}>Hand the room to {sheet.member.displayName}</span>
                  </span>
                  <Icon name="chevron_right" style={{ color: "var(--ink-3)" }} />
                </button>
                <button
                  type="button"
                  className={s.menuItem}
                  style={pre ? { color: "var(--bad)" } : undefined}
                  disabled={!pre}
                  onClick={() => open({ kind: "remove", member: sheet.member })}
                >
                  <Icon name="person_remove" size={22} />
                  <span className={s.menuText}>
                    <span style={{ fontSize: 14, fontWeight: 700 }}>Remove from room</span>
                    <span className={s.small}>{pre ? "Frees their seat and colour" : "Not once the auction is live"}</span>
                  </span>
                  <Icon name="chevron_right" style={{ color: "var(--ink-3)" }} />
                </button>
              </div>
              <Button variant="outline" size="L" onClick={() => setSheet(null)}>
                Close
              </Button>
            </Sheet>
          )}

          {sheet?.kind === "remove" && room && sheet.member.franchise && (
            <ConfirmSheet
              icon="person_remove"
              tone="bad"
              title={`Remove ${sheet.member.franchise.name}?`}
              body={`${sheet.member.displayName} loses their franchise and seat. They can rejoin later with an active invite code.`}
              action="Remove"
              actionVariant="danger"
              busy={busy}
              error={sheetError}
              onClose={() => setSheet(null)}
              onConfirm={() => void act(() => removeFranchise(room.id, sheet.member.franchise!.id))}
            />
          )}

          {sheet?.kind === "handover" && room && sheet.member.franchise && (
            <ConfirmSheet
              icon="swap_horiz"
              tone="team"
              title={`Make ${sheet.member.displayName} the Commissioner?`}
              body={
                mine
                  ? `${sheet.member.displayName} takes charge right away and you can't take it back. You keep ${mine.name} and play as a manager.`
                  : `${sheet.member.displayName} takes charge right away and you can't take it back. You don't have a franchise, so you'll leave the room.`
              }
              action="Make Commissioner"
              actionVariant="primary"
              busy={busy}
              error={sheetError}
              onClose={() => setSheet(null)}
              onConfirm={() => void act(() => handOver(room.id, sheet.member.franchise!.id))}
            />
          )}

          {sheet?.kind === "advance" && room && (
            <ConfirmSheet
              icon={ADVANCE[sheet.to]!.icon}
              tone="team"
              title={ADVANCE[sheet.to]!.title}
              body="Rooms only move forward, so you can't go back. Friends can still join until the auction goes live."
              action={ADVANCE[sheet.to]!.label}
              actionVariant="primary"
              busy={busy}
              error={sheetError}
              onClose={() => setSheet(null)}
              onConfirm={() => void act(() => advanceRoom(room.id, sheet.to))}
            />
          )}

          {sheet?.kind === "schedule" && room && (
            <ScheduleSheet
              roomId={room.id}
              mode="new"
              serverNow={() => (pageClock.ready ? pageClock.now() : null)}
              poolHref={`/rooms/${room.id}/pool`}
              onClose={() => setSheet(null)}
              onDone={() => {
                setSheet(null);
                router.push(`/rooms/${room.id}/auction`);
              }}
            />
          )}

          {sheet?.kind === "revoke" && room && (
            <ConfirmSheet
              icon="key_off"
              tone="bad"
              title="Revoke this code?"
              body="The code and link stop working straight away. People already in the room stay in."
              action="Revoke"
              actionVariant="danger"
              busy={busy}
              error={sheetError}
              onClose={() => setSheet(null)}
              onConfirm={() => void act(() => revokeInvite(room.id))}
            />
          )}

          {sheet?.kind === "franchise" && room && (
            <Sheet label={sheet.create ? "Create your franchise" : "Edit your franchise"} onClose={() => setSheet(null)}>
              <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                <h2 className={sheetStyles.title}>Your franchise</h2>
                <span className={s.note}>
                  {sheet.create ? "You'll bid in the auction like everyone else." : "Change it any time before the auction goes live."}
                </span>
              </div>
              <FranchiseFields
                draft={draft}
                taken={taken.filter((c) => c !== mine?.colour)}
                onChange={setDraft}
                nameError={sheetError}
                ringGap="var(--surface)"
              />
              <div className={sheetStyles.actions}>
                <Button variant="outline" size="L" onClick={() => setSheet(null)}>
                  Cancel
                </Button>
                <Button variant="primary" size="L" busy={busy} onClick={() => saveFranchise(sheet.create)}>
                  {sheet.create ? "Create franchise" : "Save changes"}
                </Button>
              </div>
            </Sheet>
          )}

          {toast && (
            <Toast
              title={toast}
              body={toast === "Code copied" ? "Paste it in the group chat. It works for 7 days." : undefined}
              onDone={() => setToast(null)}
            />
          )}
        </>
      }
    >
      {!room ? (
        <div className={s.inner} aria-busy="true">
          {loadFailed ? (
            <div className={s.card} role="alert">
              <b>Couldn&apos;t load the room.</b>
              <span className={s.note}>{loadFailed}</span>
              <div>
                <Button variant="primary" onClick={() => void load()}>
                  Try again
                </Button>
              </div>
            </div>
          ) : (
            <>
              <div style={{ display: "flex", gap: 8 }}>
                {[64, 84, 72].map((w) => (
                  <span key={w} className={s.skeleton} style={{ width: w, height: 24, borderRadius: 999 }} />
                ))}
              </div>
              <div className={s.cols}>
                <div className={s.main}>
                  <div className={s.skeleton} style={{ height: 200, borderRadius: 28 }} />
                  <div className={s.skeleton} style={{ height: 150 }} />
                </div>
                <div className={s.side}>
                  <div className={s.skeleton} style={{ height: 260 }} />
                </div>
              </div>
            </>
          )}
        </div>
      ) : (
        <div className={s.inner}>
          <ol className={s.phases} aria-label="Room phase">
            {PHASES.map((p, i) => {
              const now = PHASE_OF[room.state];
              return (
                <li key={p} className={s.phase} aria-current={i === now ? "step" : undefined}>
                  <span className={`${s.phaseChip} ${i === now ? s.phaseNow : i < now ? s.phasePast : ""}`}>{p.toUpperCase()}</span>
                  {i < PHASES.length - 1 && <span className={s.sep} />}
                </li>
              );
            })}
          </ol>

          <div className={s.cols}>
            <div className={s.main}>
              <section className={s.hero} aria-label="Room status">
                {room.state === "setup" || (room.state === "retention" && commish) ? (
                  <>
                    <span className={s.heroLabel}>MANAGERS JOINED</span>
                    <div className={s.count}>
                      <span className={s.countBig}>{joined}</span>
                      <span className={s.countOf}>/ {min} needed</span>
                    </div>
                    <div className={s.heroBar}>
                      <div className={s.heroFill} style={{ width: `${Math.min(100, Math.round((100 * joined) / Math.max(1, min)))}%` }} />
                    </div>
                    <span className={s.heroLine}>
                      {!commish
                        ? "The Commissioner moves the room on once everyone's in."
                        : short > 0
                          ? `${short} more to go. Share the invite code.`
                          : `That's enough to start. Room for ${Math.max(0, max - joined)} more.`}
                    </span>
                  </>
                ) : (
                  <>
                    <span className={s.heroLabel}>NEXT UP</span>
                    <span className={s.headline}>
                      {room.state === "retention"
                        ? "Retention is open"
                        : room.state === "auction_scheduled"
                          ? "The auction is next"
                          : room.state === "auction_live"
                            ? "The auction is live"
                            : room.state === "season_live"
                              ? "The season is live"
                              : "Season complete"}
                    </span>
                    <span className={s.heroLine}>
                      {room.state === "auction_scheduled" && noAuction
                        ? commish
                          ? "No start time is set yet. Schedule the auction to open the lobby."
                          : `${room.commissioner.displayName} sets the start time. The lobby opens once it's set.`
                        : room.state === "auction_scheduled"
                        ? `The rules are set. ${commish ? "You start" : `${room.commissioner.displayName} starts`} the auction when everyone is ready.`
                        : room.state === "retention"
                          ? `${room.commissioner.displayName} schedules the auction when retention closes.`
                          : "Bids are final."}
                    </span>
                  </>
                )}
                {(room.state === "auction_live" || (room.state === "auction_scheduled" && !noAuction)) && (
                  <div className={s.actions}>
                    <ButtonLink
                      href={`/rooms/${roomId}/auction`}
                      size="M48"
                      variant="onTeam"
                      icon={room.state === "auction_live" ? "gavel" : "front_hand"}
                    >
                      {room.state === "auction_live" ? "Open the live auction" : "Open the lobby"}
                    </ButtonLink>
                  </div>
                )}
                {commish && nextSteps.length > 0 && (
                  <>
                    <div className={s.actions}>
                      {nextSteps.map((step, i) => (
                        <Button
                          key={step.key}
                          size="M48"
                          variant={i === 0 && short === 0 ? "onTeam" : "glass"}
                          icon={step.icon}
                          disabled={short > 0 || busy}
                          style={i === 0 ? { opacity: 1 } : undefined}
                          onClick={step.onClick}
                        >
                          {step.label}
                        </Button>
                      ))}
                    </div>
                    {short > 0 && <span className={s.heroNote}>Needs {min} managers to move on. Only franchises count.</span>}
                  </>
                )}
              </section>

              {commish && !mine && pre && (
                <div className={s.callout}>
                  <span className={s.calloutTile}>
                    <Icon name="person_add" size={22} />
                  </span>
                  <div className={s.calloutText}>
                    <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                      <span style={{ fontSize: 15, fontWeight: 800 }}>Playing too?</span>
                      <span className={s.note} style={{ lineHeight: 1.45 }}>
                        Right now you lead this room without a team. Create a franchise to bid in the auction like everyone else.
                      </span>
                    </div>
                    <Button variant="primary" size="S" onClick={() => openFranchise(true)}>
                      Create my franchise
                    </Button>
                  </div>
                </div>
              )}

              {commish && pre && (
                <section className={s.card} aria-label="Invite code">
                  <div className={s.cardHead}>
                    <span className={s.label}>INVITE CODE</span>
                    {data?.invite && (
                      <span className={s.small}>
                        Works until{" "}
                        <time className="num" style={{ color: "var(--ink)" }} dateTime={data.invite.expiresAt}>
                          {dateTime(data.invite.expiresAt)}
                        </time>
                      </span>
                    )}
                  </div>
                  {data?.invite ? (
                    <span className={s.code} data-testid="room-invite-code">
                      {formatCode(data.invite.code)}
                    </span>
                  ) : (
                    <span className={s.note}>There is no active invite. Make one to bring friends in.</span>
                  )}
                  <div className={s.actions}>
                    {data?.invite ? (
                      <>
                        <Button variant="primary" icon="content_copy" onClick={() => void copyCode(data.invite!.code)}>
                          Copy code
                        </Button>
                        <Button variant="secondary" icon="share" onClick={() => void shareLink(data.invite!, room.config.name)}>
                          Share link
                        </Button>
                        <Button variant="ghost" icon="refresh" disabled={busy} onClick={() => void act(() => issueInvite(room.id))}>
                          New code
                        </Button>
                        <Button variant="ghostBad" icon="key_off" disabled={busy} onClick={() => open({ kind: "revoke" })}>
                          Revoke
                        </Button>
                      </>
                    ) : (
                      <Button variant="primary" icon="add" busy={busy} onClick={() => void act(() => issueInvite(room.id))}>
                        Create invite
                      </Button>
                    )}
                  </div>
                  <span className={s.small}>A new code replaces this one. People already in the room stay in.</span>
                </section>
              )}

              <section className={s.card} aria-label="House rules">
                <div className={s.cardHead}>
                  <span className={s.label}>HOUSE RULES</span>
                  {commish && pre && (
                    <Link className={s.editLink} href={`/rooms/${room.id}/setup?section=squad`}>
                      Edit rules
                    </Link>
                  )}
                </div>
                <div className={s.rules}>
                  {[
                    ["PURSE", formatMoneyShort(room.config.purse, room.config)],
                    ["SQUAD", `${room.config.squadMin}–${room.config.squadMax}`],
                    [
                      "ROLE MINIMUMS",
                      Object.entries(room.config.roleMinimums)
                        .map(([id, n]) => `${n} ${id}`)
                        .join(" · ") || "None",
                    ],
                    ...Object.entries(room.config.categoryQuotas).map(([id, n]) => [
                      (room.packLabels.restrictedCategories.find((c) => c.id === id)?.label ?? id).toUpperCase(),
                      `≤ ${n}`,
                    ]),
                    [
                      "LIFELINES",
                      room.config.ai.enabled
                        ? orderedLifelines(room.config.ai.lifelines)
                            .map(([t, n]) => `${n} ${LIFELINE_SHORT[t] ?? t}`)
                            .join(" · ")
                        : "Off",
                    ],
                    ["REPORT CARD", room.config.ai.reportCardVisibility === "private" ? "Owner only" : "Whole room"],
                  ].map(([k, v]) => (
                    <div key={k} className={s.rule}>
                      <span className={s.ruleKey}>{k}</span>
                      <span className={s.ruleValue}>{v}</span>
                    </div>
                  ))}
                </div>
                <span className={s.note}>
                  {commish ? "You can change these until the auction goes live." : "Rules can still change until the auction goes live. Bids are final."}
                </span>
              </section>
            </div>

            <div className={s.side}>
              {data?.pool && (
                <section className={s.card} aria-label="Player pool">
                  <div className={s.cardHead}>
                    <span className={s.label}>PLAYER POOL</span>
                    <span className={s.small}>
                      {room.packLabels.packName ?? room.packLabels.packId}
                      {data.pool.hasPool ? ` · season data v${data.pool.datasetVersion}` : ""}
                    </span>
                  </div>
                  {data.pool.hasPool ? (
                    <div className={s.poolCounts}>
                      <span className={s.poolCount}>
                        <b className="num">{data.pool.players.length}</b> players
                      </span>
                      <span className={s.poolCount}>
                        <b className="num">{data.pool.sets.length}</b> sets
                      </span>
                    </div>
                  ) : (
                    <span className={s.note}>No season data loaded yet. The auction can&apos;t start without a pool.</span>
                  )}
                  <div className={s.actions}>
                    <ButtonLink href={`/rooms/${room.id}/pool`} variant="secondary" icon="groups">
                      {data.pool.canEdit ? "Edit pool" : "View pool"}
                    </ButtonLink>
                    <ButtonLink href={`/rooms/${room.id}/sets`} variant="ghost" icon="format_list_numbered" className={s.poolAlt}>
                      Set order
                    </ButtonLink>
                  </div>
                </section>
              )}
              <section className={s.listCard} aria-label="Members">
                <div className={s.listHead}>
                  <span className={s.label}>IN THE ROOM</span>
                  <span className="num" style={{ fontSize: 12, color: "var(--ink-2)" }}>
                    {joined} of {min}–{max}
                  </span>
                </div>
                <ul className={s.members}>
                  {members.map((m) => {
                    const me = m.franchise ? m.franchise.id === room.myFranchiseId : commish && m.role === "commissioner";
                    const isCommish = m.role === "commissioner";
                    const who = me ? `You${isCommish ? " · Commissioner" : ""}` : `${m.displayName}${isCommish ? " · Commissioner" : ""}`;
                    return (
                      <li key={m.userId} className={`${s.member} ${me ? s.memberMe : ""}`}>
                        {m.franchise ? (
                          <Monogram text={m.franchise.monogram} colour={m.franchise.colour} size={36} />
                        ) : (
                          <Monogram tone="adminTeam" size={36} />
                        )}
                        <div className={s.memberText}>
                          <span className={s.memberName}>{m.franchise?.name ?? m.displayName}</span>
                          <span className={s.small}>{m.franchise ? who : `${who} · no franchise`}</span>
                        </div>
                        {me && m.franchise && pre && (
                          <button type="button" className={s.iconBtn} aria-label="Edit your franchise" disabled={busy} onClick={() => openFranchise(false)}>
                            <Icon name="edit" />
                          </button>
                        )}
                        {!me && commish && m.franchise && room.state !== "completed" && (
                          <button
                            type="button"
                            className={s.iconBtn}
                            aria-label={`Manage ${m.franchise.name}`}
                            disabled={busy}
                            onClick={() => open({ kind: "member", member: m })}
                          >
                            <Icon name="more_horiz" />
                          </button>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </section>

              <section className={s.listCard} aria-label="Room log">
                <span className={s.label} style={{ padding: 10 }}>
                  ROOM LOG
                </span>
                {data && data.history.length === 0 ? (
                  <p className={s.logEmpty}>Nothing yet. Every state change lands here, with who did it and when.</p>
                ) : (
                  <ol className={s.log}>
                    {data?.history.map((t, i) => (
                      <li key={`${t.occurredAt}-${t.from}-${t.to}-${i}`} className={s.logRow}>
                        <time className={s.logTime} dateTime={t.occurredAt}>
                          {when(t.occurredAt)}
                        </time>
                        <span className={s.logMark}>
                          <Icon name="shield_person" size={14} />
                        </span>
                        <span className={s.logText}>
                          {t.actor?.displayName ?? "The room"} {VERB[t.to]}
                        </span>
                      </li>
                    ))}
                  </ol>
                )}
              </section>
            </div>
          </div>
        </div>
      )}
    </RoomFrame>
  );
}
