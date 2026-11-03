"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  ApiError,
  createOwnFranchise,
  createRoom,
  getInvite,
  getMembers,
  getRoom,
  issueInvite,
  listPacks,
  removeFranchise,
  revokeInvite,
  saveConfig,
  type Invite,
  type Member,
  type PackChoice,
  type Room,
  type RoomConfig,
} from "../../api/client";
import { HOME } from "../../auth/redirect";
import { formatCode } from "../../rooms/codes";
import { explainRoomError } from "../../rooms/errors";
import { humanise, labelFor } from "../../rooms/format";
import { checkFranchise, firstFreeColour, FranchiseFields, type FranchiseDraft } from "../../rooms/franchise-fields";
import { formatMoney, ladderRows } from "../../rooms/money";
import { scoringGroups } from "../../rooms/scoring";
import { Button } from "../../ui/Button";
import { Icon } from "../../ui/Icon";
import { CompactStepper, Field, Monogram, Note, Segmented, Stepper, Toggle } from "../../ui/kit";
import { ConfirmSheet, Sheet, sheetStyles, Toast } from "../../ui/Sheet";
import s from "./wizard.module.css";

export const STEPS = [
  { name: "Sport", sub: "Pick a Sport Pack", title: "Choose a sport" },
  { name: "Basics", sub: "Name, season, managers", title: "Room basics" },
  { name: "Squad rules", sub: "Purse, roles, quotas", title: "Squad rules" },
  { name: "Scoring", sub: "Points per stat", title: "Scoring" },
  { name: "AI", sub: "Lifelines, report card", title: "AI settings" },
  { name: "Invite", sub: "Get friends in", title: "Invite your friends" },
] as const;

export type Step = 1 | 2 | 3 | 4 | 5 | 6;

const NAME_MAX = 40;
const MANAGERS = { min: 2, max: 12 };
const LIFELINES: Record<string, { label: string; icon: string; desc: string }> = {
  "fit-check": { label: "Fit Check", icon: "fact_check", desc: "Go/No-Go on your own squad" },
  "price-ceiling": { label: "Price Ceiling", icon: "vertical_align_top", desc: "Max sensible bid for this lot" },
  "rival-scan": { label: "Rival Scan", icon: "radar", desc: "Who needs the player and can afford them" },
};
const ROLE_SLOT = (i: number) => (i % 4) + 1;

function dateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
}

/**
 * The 6-step create flow (docs/design Home `wizard:1-6`). The room exists from step 2 ("Create
 * room"); from then on its id lives in the URL (/rooms/{id}/setup?step=N), so a reload resumes
 * the same room instead of creating another.
 */
export function RoomWizard({ roomId, initialStep = 1 }: { roomId?: string; initialStep?: Step }) {
  const router = useRouter();
  const [step, setStepState] = useState<Step>(roomId ? (Math.max(2, initialStep) as Step) : 1);
  const [packs, setPacks] = useState<PackChoice[] | null>(null);
  const [packId, setPackId] = useState("");
  const [presetId, setPresetId] = useState("");
  const [name, setName] = useState("");
  const [competition, setCompetition] = useState("");
  const [managers, setManagers] = useState({ min: 2, max: 10, touched: false });
  const [room, setRoom] = useState<Room | null>(null);
  const [draft, setDraft] = useState<RoomConfig | null>(null);
  const [invite, setInvite] = useState<Invite | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [error, setError] = useState<{ message: string; locked?: boolean } | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [sheet, setSheet] = useState<null | { kind: "own" } | { kind: "remove"; member: Member } | { kind: "revoke" }>(null);
  const [sheetError, setSheetError] = useState<string | null>(null);
  const [own, setOwn] = useState<FranchiseDraft>({ name: "", monogram: "", colour: "", monogramTouched: false });

  const loadPacks = useCallback(() => {
    listPacks()
      .then((list) => {
        setError(null);
        setPacks(list);
        setPackId((p) => p || list[0]?.id || "");
        setPresetId((p) => p || list[0]?.presets[0]?.id || "");
      })
      .catch((e) => setError({ message: explainRoomError(e).message }));
  }, []);

  const loadRoom = useCallback(
    (id: string) =>
      Promise.all([getRoom(id), getMembers(id)]).then(([next, people]) => {
        setRoom(next);
        setDraft(next.config);
        setPackId(next.packLabels.packId);
        setMembers(people);
        return next;
      }),
    [],
  );

  useEffect(() => {
    loadPacks();
    if (roomId) {
      loadRoom(roomId).catch((e) => setError({ message: explainRoomError(e).message }));
      getInvite(roomId).then(setInvite).catch(() => undefined);
    }
  }, [roomId, loadPacks, loadRoom]);

  const setStep = useCallback(
    (next: Step) => {
      setStepState(next);
      setError(null);
      if (room) router.replace(`/rooms/${room.id}/setup?step=${next}`, { scroll: false });
    },
    [room, router],
  );

  const pack = packs?.find((p) => p.id === packId);
  const labels = room?.packLabels;

  function update(patch: Partial<RoomConfig>) {
    setDraft((d) => (d ? { ...d, ...patch } : d));
  }

  /** Saves the draft, then moves on. A stale version keeps the person's edits for the next save. */
  async function saveAndGo(next: Step) {
    if (!room || !draft) return;
    setBusy(true);
    setError(null);
    try {
      const saved = await saveConfig(room.id, room.configVersion, draft);
      setRoom(saved);
      setDraft(saved.config);
      if (next === 6 && !invite) setInvite(await issueInvite(room.id));
      setStep(next);
    } catch (e) {
      const explained = explainRoomError(e);
      const code = e instanceof ApiError ? (e.body as { error?: string } | null)?.error : null;
      if (code === "stale_version") {
        const fresh = await getRoom(room.id).catch(() => null);
        if (fresh) setRoom(fresh);
        setError({ message: "Someone else saved changes while you were editing. Save again to keep yours." });
      } else {
        setError({ message: explained.message, locked: code === "room_locked" });
      }
    } finally {
      setBusy(false);
    }
  }

  async function create() {
    setNameError(null);
    if (name.trim().length < 2) {
      setNameError("Use 2–40 characters.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      let created = await createRoom({ packId, presetId, name: name.trim(), competition: competition.trim() });
      if (managers.touched) {
        created = await saveConfig(created.id, created.configVersion, {
          ...created.config,
          minManagers: managers.min,
          maxManagers: managers.max,
        });
      }
      router.replace(`/rooms/${created.id}/setup?step=3`);
      setRoom(created);
      setDraft(created.config);
      setStepState(3);
    } catch (e) {
      setError({ message: explainRoomError(e).message });
    } finally {
      setBusy(false);
    }
  }

  function next(event?: FormEvent) {
    event?.preventDefault();
    if (step === 1) setStep(2);
    else if (step === 2 && !room) void create();
    else if (step === 6 && room) router.push(`/rooms/${room.id}`);
    else void saveAndGo((step + 1) as Step);
  }

  function back() {
    if (step === 1) router.push(HOME);
    else setStep((step - 1) as Step);
  }

  function openSheet(next: NonNullable<typeof sheet>) {
    setSheetError(null);
    setSheet(next);
  }

  async function act(work: () => Promise<void>) {
    setBusy(true);
    setSheetError(null);
    try {
      await work();
      setSheet(null);
    } catch (e) {
      setSheetError(explainRoomError(e).message);
    } finally {
      setBusy(false);
    }
  }

  async function copy(text: string, what: string) {
    try {
      await navigator.clipboard.writeText(text);
      setToast(what);
    } catch {
      setError({ message: "Couldn't copy. Select the code and copy it by hand." });
    }
  }

  async function share() {
    if (!invite) return;
    const data = { title: room?.config.name ?? "PaddleUp", text: `Join my room: ${formatCode(invite.code)}`, url: invite.link };
    if (typeof navigator.share === "function") {
      await navigator.share(data).catch(() => undefined);
    } else {
      await copy(invite.link, "Link copied");
    }
  }

  const roleSum = draft ? Object.values(draft.roleMinimums).reduce((a, b) => a + (b || 0), 0) : 0;
  const roleOver = !!draft && roleSum > draft.squadMin;
  const blocked = busy || (step === 1 && (!pack || !presetId)) || (step === 3 && roleOver) || (step >= 3 && !draft);
  const nextLabel = step === 6 ? "Open room" : step === 2 && !room ? "Create room" : "Continue";
  const reachable = (n: number) => (room ? n >= 1 : n <= 2);
  const me = members.find((m) => m.role === "commissioner");
  const adminOnly = !!room && !room.myFranchiseId;
  const taken = members.map((m) => m.franchise?.colour).filter((c): c is string => !!c);

  return (
    <div className={s.frame}>
      <nav className={s.rail} aria-label="Room setup">
        <span className={s.railLabel}>NEW ROOM</span>
        <ol aria-label="Steps" className={s.list} style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          {STEPS.map((st, i) => {
            const n = (i + 1) as Step;
            const done = n < step;
            return (
              <li key={st.name} style={{ listStyle: "none" }}>
                <button
                  type="button"
                  className={s.railStep}
                  aria-current={n === step ? "step" : undefined}
                  disabled={!reachable(n) || busy}
                  onClick={() => setStep(n)}
                  style={{ width: "100%" }}
                >
                  <span className={`${s.dot} ${done || n === step ? s.dotOn : ""}`}>{done ? "✓" : n}</span>
                  <span className={s.stepText}>
                    <span className={s.stepName}>
                      <span className="sr-only">{n}. </span>
                      {st.name}
                    </span>
                    <span className={s.stepSub}>{st.sub}</span>
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      </nav>

      <form className={s.column} onSubmit={next} noValidate>
        <div className={s.top}>
          <ol className={s.bars} aria-hidden="true">
            {STEPS.map((st, i) => (
              <li key={st.name} className={`${s.barSeg} ${i + 1 <= step ? s.barOn : ""}`} />
            ))}
          </ol>
          <div className={s.titleRow}>
            <h1 className={s.title}>{STEPS[step - 1].title}</h1>
            <span className={s.count}>{step} of 6</span>
          </div>
        </div>

        <div className={s.body}>
          <div className={s.content}>
            {error && (
              <Note tone="bad" icon="error" role="alert" title={error.message}>
                {error.locked && room && <Link href={`/rooms/${room.id}`}>Go to the room</Link>}
              </Note>
            )}
            {error && !packs && step === 1 && (
              <div>
                <Button variant="outline" onClick={loadPacks}>
                  Try again
                </Button>
              </div>
            )}

            {step === 1 && (
              <>
                <p className={s.intro}>
                  The Sport Pack sets roles, stats, scoring and sensible defaults. You can tweak the rules in the next
                  steps. The sport is fixed once the room is created.
                </p>
                {!packs && !error && <p className={s.intro}>Loading Sport Packs…</p>}
                {packs?.length === 0 && <Note tone="warn" icon="info" title="No Sport Packs are installed on this server yet." />}
                {packs?.map((p) => {
                  const on = p.id === packId;
                  return (
                    <button
                      key={p.id}
                      type="button"
                      className={`${s.pack} ${on ? "" : s.packOff}`}
                      aria-pressed={on}
                      disabled={!!room}
                      onClick={() => {
                        setPackId(p.id);
                        setPresetId(p.presets[0]?.id ?? "");
                      }}
                    >
                      <span className={s.packTile}>
                        <Icon name={p.icon ?? "sports"} size={26} />
                      </span>
                      <span className={s.packText}>
                        <span className={s.packHead}>
                          <span>{p.name ?? p.id}</span>
                          {on && <Icon name="check_circle" size={22} fill style={{ color: "var(--brand)" }} />}
                        </span>
                        {p.summary && <span className={s.packSummary}>{p.summary}</span>}
                        <span className={s.packVersion}>pack v{p.version}</span>
                      </span>
                    </button>
                  );
                })}
                {pack && (
                  <div className={s.group} role="radiogroup" aria-label="Starting rules">
                    <span className={s.label}>Starting rules</span>
                    {pack.presets.map((preset) => (
                      <button
                        key={preset.id}
                        type="button"
                        role="radio"
                        aria-checked={preset.id === presetId}
                        className={s.preset}
                        disabled={!!room}
                        onClick={() => setPresetId(preset.id)}
                      >
                        <span className={s.radio} />
                        {preset.label}
                      </button>
                    ))}
                    <span className={s.helper}>
                      {room
                        ? "The sport and starting rules are fixed now that the room exists."
                        : "Each pack ships one or more presets. They only change the defaults you start from."}
                    </span>
                  </div>
                )}
                <div className={s.later}>
                  <span className={s.laterTile}>
                    <Icon name="add_circle" size={26} />
                  </span>
                  <span style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    <span style={{ fontWeight: 700, fontSize: 15 }}>More Sport Packs later</span>
                    <span style={{ fontSize: 13 }}>New packs show up here once they&apos;re installed.</span>
                  </span>
                </div>
              </>
            )}

            {step === 2 && (
              <>
                <Field
                  label="Room name"
                  value={room ? (draft?.name ?? "") : name}
                  maxLength={NAME_MAX}
                  autoComplete="off"
                  error={nameError}
                  onChange={(e) => (room ? update({ name: e.target.value }) : setName(e.target.value))}
                />
                <Field
                  label="Season"
                  value={room ? (draft?.competition ?? "") : competition}
                  maxLength={60}
                  autoComplete="off"
                  helper="Optional. Shown under the room name."
                  onChange={(e) => (room ? update({ competition: e.target.value }) : setCompetition(e.target.value))}
                />
                <div className={s.group}>
                  <span className={s.label}>Managers</span>
                  <div className={s.pair}>
                    <Stepper
                      label="Minimum managers"
                      prefix="Min"
                      min={MANAGERS.min}
                      max={room ? (draft?.maxManagers ?? MANAGERS.max) : managers.max}
                      value={room ? (draft?.minManagers ?? 2) : managers.min}
                      onChange={(v) => (room ? update({ minManagers: v }) : setManagers((m) => ({ ...m, min: v, touched: true })))}
                    />
                    <Stepper
                      label="Maximum managers"
                      prefix="Max"
                      min={Math.max(MANAGERS.min, room ? (draft?.minManagers ?? 2) : managers.min, room?.franchiseCount ?? 0)}
                      max={MANAGERS.max}
                      value={room ? (draft?.maxManagers ?? 10) : managers.max}
                      onChange={(v) => (room ? update({ maxManagers: v }) : setManagers((m) => ({ ...m, max: v, touched: true })))}
                    />
                  </div>
                  <span className={s.helper}>At least 2. The room is full at the max.</span>
                </div>
              </>
            )}

            {step === 3 && draft && labels && (
              <>
                <div className={s.group}>
                  <span className={s.label}>Purse</span>
                  <Stepper
                    label="Purse"
                    value={draft.purse}
                    min={draft.displayUnit.divisor ?? 1}
                    step={5 * (draft.displayUnit.divisor ?? 1)}
                    format={(v) => formatMoney(v, draft)}
                    onChange={(v) => update({ purse: v })}
                  />
                </div>
                <div className={s.group}>
                  <span className={s.label}>Squad size</span>
                  <div className={s.pair}>
                    <Stepper label="Squad minimum" prefix="Min" min={1} max={draft.squadMax} value={draft.squadMin} onChange={(v) => update({ squadMin: v })} />
                    <Stepper label="Squad maximum" prefix="Max" min={draft.squadMin} max={99} value={draft.squadMax} onChange={(v) => update({ squadMax: v })} />
                  </div>
                </div>
                <div className={s.group}>
                  <div className={s.groupHead}>
                    <span className={s.label}>Role minimums</span>
                    <span className="num" style={{ fontSize: 12, color: roleOver ? "var(--bad)" : "var(--ink-2)" }}>
                      {roleSum} of {draft.squadMin} min
                    </span>
                  </div>
                  <div className={s.roles}>
                    {labels.roles.map((role, i) => {
                      const id = role.id ?? "";
                      return (
                        <div key={id} className={s.roleCard}>
                          <span className={s.roleChip} style={{ background: `var(--role${ROLE_SLOT(i)}-bg)`, color: `var(--role${ROLE_SLOT(i)}-ink)` }}>
                            {role.label ?? id}
                          </span>
                          <CompactStepper
                            label={`${role.label ?? id} minimum`}
                            value={draft.roleMinimums[id] ?? 0}
                            onChange={(v) => update({ roleMinimums: { ...draft.roleMinimums, [id]: v } })}
                          />
                        </div>
                      );
                    })}
                  </div>
                  {roleOver && (
                    <Note tone="bad" icon="error" role="alert">
                      Role minimums add up to {roleSum}, but the squad minimum is {draft.squadMin}. Drop a role or raise the squad size.
                    </Note>
                  )}
                </div>
                {Object.entries(draft.categoryQuotas).map(([id, count]) => (
                  <div key={id} className={s.quota}>
                    <div className={s.quotaText}>
                      <span className={s.quotaName}>{labelFor(labels.restrictedCategories, id)}</span>
                      <span className={s.helper}>Restricted category from the pack</span>
                    </div>
                    <CompactStepper
                      label={`${labelFor(labels.restrictedCategories, id)} limit`}
                      value={count}
                      format={(v) => `≤ ${v}`}
                      onChange={(v) => update({ categoryQuotas: { ...draft.categoryQuotas, [id]: v } })}
                    />
                  </div>
                ))}
                <div className={s.group}>
                  <div className={s.groupHead}>
                    <span className={s.label}>Bid increments</span>
                    <span className={s.helper}>From the pack</span>
                  </div>
                  <div className={s.table}>
                    {ladderRows(draft).map((r) => (
                      <div key={r.range} className={s.tableRow}>
                        <span className={s.tableKey}>{r.range}</span>
                        <span className="num">{r.step}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}

            {step === 4 && draft && labels && (
              <>
                <Note tone="info" icon="info">
                  Points come straight from the Sport Pack. Every room starts with these, and they can&apos;t be edited yet.
                </Note>
                {scoringGroups((draft.scoringRules.rules ?? []) as Record<string, unknown>[], labels).map((g) => (
                  <section key={g.title} className={s.scoreCard}>
                    <h2 className={s.scoreHead}>
                      <span>{g.title}</span>
                      <span className={s.scoreNote}>{g.note}</span>
                    </h2>
                    {g.rows.map((r, i) => (
                      <div key={`${r.label}-${i}`} className={s.scoreRow}>
                        <span className={s.tableKey}>{r.label}</span>
                        <span className={s.points}>{r.points}</span>
                      </div>
                    ))}
                  </section>
                ))}
              </>
            )}

            {step === 5 && draft && (
              <>
                <div className={s.ai}>
                  <div className={s.aiText}>
                    <Icon name="forum" size={26} style={{ color: "var(--coach)" }} />
                    <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                      <span className={s.aiTitle}>Team Table advisor</span>
                      <span className={s.aiSub}>Rationed AI advice. It never bids or sees upcoming lots.</span>
                    </div>
                  </div>
                  <Toggle
                    label="Team Table advisor"
                    checked={draft.ai.enabled}
                    onColour="var(--ok)"
                    offColour="var(--inverse-2)"
                    onChange={(enabled) => update({ ai: { ...draft.ai, enabled } })}
                  />
                </div>
                {draft.ai.enabled && (
                  <>
                    <div className={s.group}>
                      <span className={s.label}>Lifelines per manager</span>
                      {Object.entries(draft.ai.lifelines).map(([type, count]) => {
                        const l = LIFELINES[type] ?? { label: humanise(type), icon: "help", desc: "" };
                        return (
                          <div key={type} className={s.lifeline}>
                            <Icon name={l.icon} size={22} style={{ color: "var(--ink-2)" }} />
                            <div className={s.lifeText}>
                              <span style={{ fontSize: 14, fontWeight: 700 }}>{l.label}</span>
                              {l.desc && <span className={s.helper}>{l.desc}</span>}
                            </div>
                            <CompactStepper
                              label={`${l.label} lifelines`}
                              value={count}
                              min={0}
                              max={5}
                              onChange={(v) => update({ ai: { ...draft.ai, lifelines: { ...draft.ai.lifelines, [type]: v } } })}
                            />
                          </div>
                        );
                      })}
                      <span className={s.helper}>Set a type to 0 to switch it off.</span>
                    </div>
                    <div className={s.group}>
                      <span className={s.label}>Auction Report Card</span>
                      <Segmented
                        label="Auction Report Card"
                        value={draft.ai.reportCardVisibility}
                        options={[
                          { value: "room", label: "Whole room" },
                          { value: "private", label: "Owner only" },
                        ]}
                        onChange={(v) => update({ ai: { ...draft.ai, reportCardVisibility: v } })}
                      />
                      <span className={s.helper}>Each manager&apos;s &quot;advice vs action&quot; section stays private unless they share it.</span>
                    </div>
                  </>
                )}
              </>
            )}

            {step === 6 && room && (
              <>
                <div className={s.invite}>
                  <span className={s.inviteLabel}>INVITE CODE</span>
                  {invite ? (
                    <>
                      <span className={s.inviteCode} data-testid="invite-code">
                        {formatCode(invite.code)}
                      </span>
                      <span className={s.inviteExpiry}>
                        Works until{" "}
                        <time className="num" dateTime={invite.expiresAt}>
                          {dateTime(invite.expiresAt)}
                        </time>{" "}
                        · 7 days
                      </span>
                    </>
                  ) : (
                    <span className={s.inviteExpiry}>No active code. Make one to invite friends.</span>
                  )}
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {invite && (
                      <>
                        <Button variant="onBrand" icon="share" onClick={() => void share()}>
                          Share link
                        </Button>
                        <Button variant="glassBrand" icon="content_copy" onClick={() => void copy(formatCode(invite.code), "Code copied")}>
                          Copy code
                        </Button>
                      </>
                    )}
                    <Button variant="glassBrand" icon="refresh" busy={busy && !sheet} onClick={() => void act(async () => setInvite(await issueInvite(room.id)))}>
                      {invite ? "New code" : "Create invite"}
                    </Button>
                  </div>
                </div>

                {adminOnly && (
                  <div className={s.callout}>
                    <span className={s.calloutTile}>
                      <Icon name="person_add" size={22} />
                    </span>
                    <div className={s.calloutText}>
                      <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                        <span style={{ fontSize: 15, fontWeight: 800 }}>Playing too?</span>
                        <span style={{ fontSize: 13, color: "var(--ink-2)", lineHeight: 1.45 }}>
                          Right now you lead this room without a team. Create a franchise to bid in the auction like everyone else.
                        </span>
                      </div>
                      <Button
                        variant="brandSoft"
                        size="S"
                        onClick={() => {
                          setOwn((d) => ({ ...d, colour: d.colour || firstFreeColour(taken) }));
                          openSheet({ kind: "own" });
                        }}
                      >
                        Create my franchise
                      </Button>
                    </div>
                  </div>
                )}

                <div className={s.joined}>
                  <div className={s.joinedHead}>
                    <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", color: "var(--ink-2)" }}>
                      JOINED · {room.franchiseCount} OF {draft?.minManagers ?? room.config.minManagers}–{draft?.maxManagers ?? room.config.maxManagers}
                    </span>
                    {invite && (
                      <button type="button" className={s.linkBtn} style={{ color: "var(--brand-ink)" }} onClick={() => openSheet({ kind: "revoke" })}>
                        Revoke code
                      </button>
                    )}
                  </div>
                  <ul className={s.list}>
                    {adminOnly && me && (
                      <li className={s.member}>
                        <Monogram tone="admin" size={32} />
                        <span className={s.memberName}>
                          {me.displayName} <span className={s.memberWho}>· you, commissioner · no franchise</span>
                        </span>
                      </li>
                    )}
                    {members
                      .filter((m) => m.franchise)
                      .map((m) => {
                        const mine = m.franchise?.id === room.myFranchiseId;
                        return (
                          <li key={m.userId} className={s.member}>
                            <Monogram text={m.franchise?.monogram} colour={m.franchise?.colour} size={32} />
                            <span className={s.memberName}>
                              {m.franchise?.name} <span className={s.memberWho}>· {mine ? "you, commissioner" : m.displayName}</span>
                            </span>
                            {!mine && (
                              <button type="button" className={s.linkBtn} style={{ color: "var(--bad)" }} onClick={() => openSheet({ kind: "remove", member: m })}>
                                Remove
                              </button>
                            )}
                          </li>
                        );
                      })}
                  </ul>
                </div>
                <Note tone="warn" icon="lock_clock">
                  The Sport Pack is fixed now. You can change the rules from the room until the auction goes live.
                </Note>
              </>
            )}
          </div>
        </div>

        <div className={s.footer}>
          <Button variant="outline" size="L" onClick={back} disabled={busy}>
            {step === 1 ? "Cancel" : "Back"}
          </Button>
          <Button type="submit" variant="brand" size="L" className={s.next} disabled={blocked} busy={busy && !sheet}>
            {nextLabel}
          </Button>
        </div>
      </form>

      {sheet?.kind === "own" && room && (
        <Sheet label="Create your franchise" onClose={() => setSheet(null)}>
          <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <h2 className={sheetStyles.title}>Your franchise</h2>
            <span style={{ fontSize: 13, color: "var(--ink-2)" }}>You&apos;ll bid in the auction like everyone else.</span>
          </div>
          <FranchiseFields draft={own} taken={taken} onChange={setOwn} nameError={sheetError} ringGap="var(--surface)" />
          <div className={sheetStyles.actions}>
            <Button variant="outline" size="L" onClick={() => setSheet(null)}>
              Cancel
            </Button>
            <Button
              variant="brand"
              size="L"
              busy={busy}
              onClick={() => {
                const problems = checkFranchise(own);
                if (Object.keys(problems).length) {
                  setSheetError(problems.name ?? problems.monogram ?? problems.colour ?? null);
                  return;
                }
                void act(async () => {
                  await createOwnFranchise(room.id, { name: own.name.trim(), monogram: own.monogram.trim(), colour: own.colour });
                  await loadRoom(room.id);
                });
              }}
            >
              Create franchise
            </Button>
          </div>
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
          onConfirm={() =>
            void act(async () => {
              setMembers(await removeFranchise(room.id, sheet.member.franchise!.id));
              await loadRoom(room.id);
            })
          }
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
          onConfirm={() =>
            void act(async () => {
              await revokeInvite(room.id);
              setInvite(null);
            })
          }
        />
      )}

      {toast && <Toast title={toast} body={toast === "Code copied" ? "Paste it in the group chat. It works for 7 days." : undefined} onDone={() => setToast(null)} />}
    </div>
  );
}
