"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import {
  createRoom,
  getRoom,
  issueInvite,
  listPacks,
  saveConfig,
  type Invite,
  type PackChoice,
  type Room,
  type RoomConfig,
} from "../../../api/client";
import { explainRoomError } from "../../../rooms/errors";
import { fromDisplay, humanise, labelFor, toDisplay } from "../../../rooms/format";

const STEPS = ["Sport", "Basics", "Squad rules", "Scoring", "AI", "Invite"] as const;
type Step = 0 | 1 | 2 | 3 | 4 | 5;

const NAME_MAX = 40;

function NumberField({
  label,
  value,
  onChange,
  min = 0,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  min?: number;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        className="num"
        type="number"
        inputMode="numeric"
        min={min}
        value={Number.isFinite(value) ? value : ""}
        onChange={(e) => onChange(e.target.value === "" ? NaN : Number(e.target.value))}
      />
    </label>
  );
}

/** The 6-step create flow. The room exists from the moment Basics is submitted. */
export function RoomWizard() {
  const [step, setStep] = useState<Step>(0);
  const [packs, setPacks] = useState<PackChoice[] | null>(null);
  const [packId, setPackId] = useState("");
  const [presetId, setPresetId] = useState("");
  const [name, setName] = useState("");
  const [competition, setCompetition] = useState("");
  const [room, setRoom] = useState<Room | null>(null);
  const [draft, setDraft] = useState<RoomConfig | null>(null);
  const [invite, setInvite] = useState<Invite | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    listPacks()
      .then((list) => {
        if (!active) return;
        setPacks(list);
        setPackId(list[0]?.id ?? "");
        setPresetId(list[0]?.presets[0]?.id ?? "");
      })
      .catch((e) => active && setError(explainRoomError(e).message));
    return () => {
      active = false;
    };
  }, []);

  function adopt(next: Room) {
    setRoom(next);
    setDraft(next.config);
  }

  /** Does one server action; a failure shows its message and reloads a room that went stale. */
  async function act(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      const explained = explainRoomError(e);
      setError(explained.message);
      if (explained.refetch && room) {
        try {
          adopt(await getRoom(room.id));
        } catch {
          // the message above already tells them to retry
        }
      }
    } finally {
      setBusy(false);
    }
  }

  function chooseSport(event: FormEvent) {
    event.preventDefault();
    setStep(1);
  }

  function submitBasics(event: FormEvent) {
    event.preventDefault();
    if (room) {
      setStep(2);
      return;
    }
    void act(async () => {
      adopt(await createRoom({ packId, presetId, name, competition }));
      setStep(2);
    });
  }

  /** Saves the draft with the version it started from, then moves on. */
  function saveAndGo(to: Step, after?: (saved: Room) => Promise<void>) {
    if (!room || !draft) return;
    void act(async () => {
      const saved = await saveConfig(room.id, room.configVersion, draft);
      adopt(saved);
      if (after) await after(saved);
      setStep(to);
    });
  }

  function update(change: Partial<RoomConfig>) {
    setDraft((d) => (d ? { ...d, ...change } : d));
  }

  const labels = room?.packLabels;
  const pack = packs?.find((p) => p.id === packId);

  return (
    <div className="page-stack">
      <h1>Create a room</h1>
      <ol className="steps" aria-label="Steps">
        {STEPS.map((label, i) => (
          <li key={label} aria-current={i === step ? "step" : undefined}>
            {i + 1}. {label}
          </li>
        ))}
      </ol>
      <p aria-live="assertive" className="alert">
        {error}
      </p>

      {step === 0 && (
        <form className="card" onSubmit={chooseSport}>
          <h2>Pick a sport</h2>
          {!packs && !error && <p>Loading sports.</p>}
          {packs?.length === 0 && <p>No sports are installed on this server yet.</p>}
          {packs?.map((p) => (
            <label key={p.id} className="choice">
              <input
                type="radio"
                name="pack"
                disabled={!!room}
                checked={packId === p.id}
                onChange={() => {
                  setPackId(p.id);
                  setPresetId(p.presets[0]?.id ?? "");
                }}
              />
              <span>
                <strong>{humanise(p.id)}</strong>
                <br />
                <span className="helper">Version {p.version}</span>
              </span>
            </label>
          ))}
          {pack && (
            <fieldset className="stack" style={{ border: 0, padding: 0, margin: 0 }}>
              <legend className="helper">Starting rules</legend>
              {pack.presets.map((preset) => (
                <label key={preset.id} className="choice">
                  <input
                    type="radio"
                    name="preset"
                    disabled={!!room}
                    checked={presetId === preset.id}
                    onChange={() => setPresetId(preset.id)}
                  />
                  <span>{preset.label}</span>
                </label>
              ))}
            </fieldset>
          )}
          <button type="submit" className="btn btn-primary" disabled={!pack || !presetId}>
            Next
          </button>
          {room && <p className="helper">The sport is fixed once the room is created.</p>}
        </form>
      )}

      {step === 1 && (
        <form className="card" onSubmit={submitBasics}>
          <h2>Basics</h2>
          <label className="field">
            <span>Room name</span>
            <input
              value={room ? (draft?.name ?? name) : name}
              maxLength={NAME_MAX}
              required
              onChange={(e) => (room ? update({ name: e.target.value }) : setName(e.target.value))}
            />
          </label>
          <label className="field">
            <span>Competition or season (optional)</span>
            <input
              value={room ? (draft?.competition ?? competition) : competition}
              maxLength={60}
              onChange={(e) =>
                room ? update({ competition: e.target.value }) : setCompetition(e.target.value)
              }
            />
          </label>
          <div className="row">
            <button type="button" className="btn" onClick={() => setStep(0)}>
              Back
            </button>
            {room ? (
              <button
                type="button"
                className="btn btn-primary"
                disabled={busy}
                onClick={() => saveAndGo(2)}
              >
                Next
              </button>
            ) : (
              <button type="submit" className="btn btn-primary" disabled={busy || name.trim().length < 2}>
                Create room
              </button>
            )}
          </div>
        </form>
      )}

      {step === 2 && room && draft && labels && (
        <form
          className="card"
          onSubmit={(e) => {
            e.preventDefault();
            saveAndGo(3);
          }}
        >
          <h2>Squad rules</h2>
          <div className="grid-2">
            <NumberField
              label="Minimum managers"
              min={2}
              value={draft.minManagers}
              onChange={(v) => update({ minManagers: v })}
            />
            <NumberField
              label="Maximum managers"
              min={2}
              value={draft.maxManagers}
              onChange={(v) => update({ maxManagers: v })}
            />
            <NumberField
              label={`Purse (${draft.displayUnit.label ?? draft.currencyCode})`}
              value={toDisplay(draft.purse, draft.displayUnit.divisor ?? 1)}
              onChange={(v) => update({ purse: fromDisplay(v, draft.displayUnit.divisor ?? 1) })}
            />
            <NumberField
              label="Squad minimum"
              min={1}
              value={draft.squadMin}
              onChange={(v) => update({ squadMin: v })}
            />
            <NumberField
              label="Squad maximum"
              min={1}
              value={draft.squadMax}
              onChange={(v) => update({ squadMax: v })}
            />
          </div>
          <h3>Minimum per role</h3>
          <div className="grid-2">
            {Object.entries(draft.roleMinimums).map(([id, count]) => (
              <NumberField
                key={id}
                label={labelFor(labels.roles, id)}
                value={count}
                onChange={(v) => update({ roleMinimums: { ...draft.roleMinimums, [id]: v } })}
              />
            ))}
          </div>
          {Object.keys(draft.categoryQuotas).length > 0 && (
            <>
              <h3>Limits per category</h3>
              <div className="grid-2">
                {Object.entries(draft.categoryQuotas).map(([id, count]) => (
                  <NumberField
                    key={id}
                    label={labelFor(labels.restrictedCategories, id)}
                    value={count}
                    onChange={(v) => update({ categoryQuotas: { ...draft.categoryQuotas, [id]: v } })}
                  />
                ))}
              </div>
            </>
          )}
          <div className="row">
            <button type="button" className="btn" onClick={() => setStep(1)}>
              Back
            </button>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              Next
            </button>
          </div>
        </form>
      )}

      {step === 3 && room && draft && (
        <section className="card">
          <h2>Scoring</h2>
          <p>
            Your room starts with the pack&apos;s scoring rules ({draft.scoringRules.rules?.length ?? 0}{" "}
            rules). They can be tuned after the room is created.
          </p>
          <ul className="rules">
            {(draft.scoringRules.rules ?? []).map((rule, i) => (
              <li key={`${(rule as { id?: string }).id ?? i}`}>
                {humanise((rule as { id?: string }).id ?? "rule")}
              </li>
            ))}
          </ul>
          <div className="row">
            <button type="button" className="btn" onClick={() => setStep(2)}>
              Back
            </button>
            <button type="button" className="btn btn-primary" onClick={() => setStep(4)}>
              Next
            </button>
          </div>
        </section>
      )}

      {step === 4 && room && draft && (
        <form
          className="card"
          onSubmit={(e) => {
            e.preventDefault();
            saveAndGo(5, async () => {
              setInvite(await issueInvite(room.id));
            });
          }}
        >
          <h2>AI advisor</h2>
          <label className="field">
            <span>
              <input
                type="checkbox"
                checked={draft.ai.enabled}
                onChange={(e) => update({ ai: { ...draft.ai, enabled: e.target.checked } })}
              />{" "}
              Allow the AI advisor in this room
            </span>
          </label>
          <div className="grid-2">
            {Object.entries(draft.ai.lifelines).map(([type, count]) => (
              <NumberField
                key={type}
                label={`${humanise(type)} lifelines`}
                value={count}
                onChange={(v) =>
                  update({ ai: { ...draft.ai, lifelines: { ...draft.ai.lifelines, [type]: v } } })
                }
              />
            ))}
          </div>
          <label className="field">
            <span>Who sees the report card</span>
            <select
              value={draft.ai.reportCardVisibility}
              onChange={(e) => update({ ai: { ...draft.ai, reportCardVisibility: e.target.value } })}
            >
              <option value="room">Everyone in the room</option>
              <option value="private">Each manager sees their own</option>
            </select>
          </label>
          <div className="row">
            <button type="button" className="btn" onClick={() => setStep(3)}>
              Back
            </button>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              Save and invite
            </button>
          </div>
        </form>
      )}

      {step === 5 && room && (
        <section className="card">
          <h2>Invite your friends</h2>
          {invite ? (
            <div className="code-box">
              <span className="helper">Invite code</span>
              <span className="code" data-testid="invite-code">
                {invite.code}
              </span>
              <span className="link">{invite.link}</span>
              <span className="helper">
                Works until <time dateTime={invite.expiresAt}>{new Date(invite.expiresAt).toLocaleString()}</time>
              </span>
            </div>
          ) : (
            <p>No invite yet.</p>
          )}
          <div className="row">
            {invite && (
              <button
                type="button"
                className="btn"
                onClick={() => void navigator.clipboard?.writeText(invite.link)}
              >
                Copy link
              </button>
            )}
            <button
              type="button"
              className="btn"
              disabled={busy}
              onClick={() => void act(async () => setInvite(await issueInvite(room.id)))}
            >
              {invite ? "New code" : "Create invite"}
            </button>
            <Link className="btn btn-primary" href={`/rooms/${room.id}`}>
              Open room
            </Link>
          </div>
        </section>
      )}
    </div>
  );
}
