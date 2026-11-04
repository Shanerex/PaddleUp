"use client";

import { Field, Swatches } from "../ui/kit";
import { suggestMonogram } from "./format";
import { HUES } from "./hues";

export const NAME_MIN = 2;
export const NAME_MAX = 30;
export const MONO_MAX = 3;

export interface FranchiseDraft {
  name: string;
  monogram: string;
  colour: string;
  /** False until the person types a monogram; until then it follows the name. */
  monogramTouched: boolean;
}

/** Spec R-6 limits, checked before the server sees them. Returns a message per field. */
export function checkFranchise(d: FranchiseDraft): { name?: string; monogram?: string; colour?: string } {
  const name = d.name.trim();
  const out: { name?: string; monogram?: string; colour?: string } = {};
  if (name.length < NAME_MIN || name.length > NAME_MAX) out.name = `Use ${NAME_MIN}–${NAME_MAX} characters.`;
  if (d.monogram.trim().length < 1 || d.monogram.trim().length > MONO_MAX) out.monogram = "Use 1–3 letters.";
  if (!d.colour) out.colour = "Pick a colour.";
  return out;
}

/** The first colour nobody has taken, or "" when all 12 are gone. */
export function firstFreeColour(taken: string[]): string {
  return HUES.find((h) => !taken.includes(h.key))?.key ?? "";
}

/** Name, monogram and team colour (Home `join`, Room `editFranchise`). */
export function FranchiseFields({
  draft,
  taken,
  onChange,
  nameError,
  ringGap,
}: {
  draft: FranchiseDraft;
  taken: string[];
  onChange: (next: FranchiseDraft) => void;
  nameError?: string | null;
  ringGap?: string;
}) {
  const allTaken = HUES.every((h) => taken.includes(h.key) && h.key !== draft.colour);
  return (
    <>
      <Field
        label="Franchise name"
        value={draft.name}
        maxLength={NAME_MAX}
        autoComplete="off"
        error={nameError}
        helper="2–30 characters. Make it chantable."
        onChange={(e) => {
          const name = e.target.value;
          onChange({
            ...draft,
            name,
            monogram: draft.monogramTouched ? draft.monogram : suggestMonogram(name).slice(0, MONO_MAX),
          });
        }}
      />
      <Field
        label="Monogram"
        mono
        narrow
        value={draft.monogram}
        maxLength={MONO_MAX}
        autoComplete="off"
        helper="1–3 letters, auto-filled from the name."
        onChange={(e) => onChange({ ...draft, monogram: e.target.value.toUpperCase(), monogramTouched: true })}
      />
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        <span style={{ fontSize: 13, fontWeight: 700 }}>Team colour</span>
        <Swatches value={draft.colour} taken={taken} ringGap={ringGap} onChange={(colour) => onChange({ ...draft, colour })} />
        <span style={{ fontSize: 12, color: "var(--ink-2)" }}>
          {allTaken ? "Every colour is taken in this room, so there's no seat left." : "Crossed-out colours are taken in this room."}
        </span>
      </div>
    </>
  );
}
