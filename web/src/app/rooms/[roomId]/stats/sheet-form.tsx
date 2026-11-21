"use client";

import type { Room, SheetField, StatSheet } from "../../../../api/client";
import { roleShort } from "../../../../rooms/format";
import { fieldLabel, keyOf, shown, type Draft } from "../../../../rooms/stat-sheet";
import { Icon } from "../../../../ui/Icon";
import s from "./stats.module.css";

/** Where the player's numbers come from: a manual value beats the provider's. */
function source(sheet: StatSheet): { label: string; tone: string } {
  if (sheet.fields.some((f) => f.manual !== null && f.manual !== undefined)) return { label: "MANUAL · BEATS PROVIDER", tone: s.warn };
  if (sheet.fields.some((f) => f.provider !== null && f.provider !== undefined)) return { label: "PROVIDER", tone: s.info };
  return { label: "NOT ENTERED", tone: s.empty };
}

/**
 * One player's stat sheet (App Screens 9f, 9g, 9h): a number field per stat, a count per event
 * type and a yes/no for a stat limited to 0 or 1, all from the pack's stat schema. A field with a
 * manual value has the team border; a refused field has the red border and its hint. Typed values
 * stay when the sheet is refused.
 */
export function SheetForm({
  sheet,
  labels,
  draft,
  hints,
  banner,
  busy,
  onChange,
  onSave,
  onClear,
}: {
  sheet: StatSheet;
  labels: Room["packLabels"];
  draft: Draft;
  hints: Record<string, string>;
  banner: string | null;
  busy: boolean;
  onChange: (key: string, value: string) => void;
  onSave: () => void;
  onClear: () => void;
}) {
  const src = source(sheet);
  const owner = sheet.owner;
  const sub = [
    owner?.name,
    roleShort(labels.roles, sheet.role),
    sheet.providerOnly !== null && sheet.providerOnly !== undefined ? `provider said ${sheet.providerOnly} pts` : null,
  ]
    .filter(Boolean)
    .join(" · ");
  const canClear = sheet.fields.some((f) => f.manual !== null && f.manual !== undefined);
  return (
    <form
      className={s.sheet}
      aria-label={`${sheet.name} stat sheet`}
      data-testid="stat-sheet"
      onSubmit={(e) => {
        e.preventDefault();
        if (!busy) onSave();
      }}
    >
      <div className={s.sheetHead}>
        <div className={s.sheetWho}>
          <span className={s.sheetName}>{sheet.name}</span>
          <span className={s.sub}>{sub}</span>
        </div>
        <span className={`${s.chip} ${src.tone}`}>{src.label}</span>
      </div>
      {banner && (
        <div className={s.banner} role="alert" data-testid="stat-banner">
          <Icon name="error" size={20} className={s.bannerIcon} />
          {banner}
        </div>
      )}
      <div className={s.fields}>
        {sheet.fields.map((f) => (
          <Field key={keyOf(f)} field={f} text={shown(f, draft)} hint={hints[keyOf(f)]} onChange={(v) => onChange(keyOf(f), v)} />
        ))}
      </div>
      <span className={s.note}>Fields, choices and limits come from the Sport Pack&apos;s stat schema.</span>
      <div className={s.actions}>
        {canClear && (
          <button type="button" className={s.clear} disabled={busy} onClick={onClear} data-testid="stat-clear">
            <Icon name="backspace" size={20} />
            Clear override
          </button>
        )}
        <button type="submit" className={s.save} disabled={busy} data-testid="stat-save">
          Save and recompute
        </button>
      </div>
    </form>
  );
}

function Field({ field, text, hint, onChange }: { field: SheetField; text: string; hint?: string; onChange: (v: string) => void }) {
  const manual = field.manual !== null && field.manual !== undefined;
  const border = `${s.control} ${hint ? s.controlBad : manual ? s.controlManual : ""}`;
  const label = fieldLabel(field);
  const id = `stat-${keyOf(field).replace(/\W+/g, "-")}`;
  return (
    <div className={s.field} data-testid="stat-field" data-key={keyOf(field)}>
      <label className={s.fieldLabel} htmlFor={id}>
        {label}
      </label>
      <div className={border}>
        {field.input === "yes_no" ? (
          <button
            id={id}
            type="button"
            role="switch"
            aria-checked={text === "1"}
            aria-label={label}
            className={s.choice}
            onClick={() => onChange(text === "1" ? "0" : "1")}
          >
            <span className={text === "" ? s.blank : undefined}>{text === "1" ? "Yes" : text === "0" ? "No" : "—"}</span>
            <Icon name={text === "1" ? "toggle_on" : "toggle_off"} size={18} className={s.fieldIcon} />
          </button>
        ) : (
          <input
            id={id}
            className={s.input}
            inputMode="decimal"
            autoComplete="off"
            value={text}
            placeholder="—"
            aria-invalid={hint ? true : undefined}
            aria-describedby={hint ? `${id}-hint` : undefined}
            onChange={(e) => onChange(e.target.value)}
          />
        )}
      </div>
      <span id={`${id}-hint`} className={s.hint}>
        {hint}
      </span>
    </div>
  );
}
