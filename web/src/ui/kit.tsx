"use client";

import { useId, type CSSProperties, type InputHTMLAttributes, type ReactNode } from "react";
import type { RoomState } from "../api/client";
import { HUES, hueOf } from "../rooms/hues";
import { STATE_CHIP_LABELS } from "../rooms/state";
import { Icon } from "./Icon";
import s from "./kit.module.css";

const cx = (...c: (string | false | undefined | null)[]) => c.filter(Boolean).join(" ");
const hueVar = (key: string) => ({ "--mh": hueOf(key) }) as CSSProperties;

/* ---------- Monogram (Design System 08) ---------- */

const MONO_FONT: Record<number, number> = { 56: 20, 48: 17, 44: 15, 40: 14, 36: 13, 32: 11, 28: 11, 24: 9 };

/**
 * A franchise monogram. `rival` paints it in the franchise's own hue (how everyone else sees it);
 * `own` uses the room's team tokens; `admin` is the shield tile of a Commissioner without a franchise.
 */
export function Monogram({
  text,
  colour,
  size = 36,
  tone = "rival",
  className,
}: {
  text?: string;
  colour?: string;
  size?: number;
  tone?: "rival" | "own" | "inverseOwn" | "admin" | "adminTeam";
  className?: string;
}) {
  const admin = tone === "admin" || tone === "adminTeam";
  return (
    <span
      className={cx(s.mono, tone === "admin" ? s.adminTile : s[tone], className)}
      style={{
        width: size,
        height: size,
        fontSize: admin ? undefined : MONO_FONT[size] ?? Math.round(size * 0.36),
        ...(colour ? hueVar(colour) : {}),
      }}
      aria-hidden="true"
    >
      {admin ? <Icon name="shield_person" size={Math.round(size * 0.55)} /> : text}
    </span>
  );
}

/* ---------- Room state chip (Design System 08) ---------- */

const STATE_CHIP = STATE_CHIP_LABELS;

export function stateChipLabel(state: RoomState): string {
  return STATE_CHIP[state];
}

export function StateChip({ state, onTeam = false, label }: { state: RoomState; onTeam?: boolean; label?: string }) {
  return (
    <span className={cx(s.chip, onTeam ? s.onTeamChip : s[state])} data-testid="room-state">
      {state === "auction_live" && !onTeam && <span className={s.liveDot} />}
      {label ?? STATE_CHIP[state]}
    </span>
  );
}

/* ---------- Field (Design System 07) ---------- */

export function Field({
  label,
  helper,
  error,
  mono = false,
  narrow = false,
  ...input
}: {
  label: ReactNode;
  helper?: ReactNode;
  error?: string | null;
  mono?: boolean;
  narrow?: boolean;
} & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  const hint = `${id}-hint`;
  return (
    <div className={s.field}>
      <label className={s.label} htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className={cx(s.input, mono && s.monoInput, narrow && s.narrow)}
        aria-invalid={error ? true : undefined}
        aria-describedby={helper || error ? hint : undefined}
        {...input}
      />
      {error ? (
        <span id={hint} className={s.error}>
          {error}
        </span>
      ) : (
        helper && (
          <span id={hint} className={s.helper}>
            {helper}
          </span>
        )
      )}
    </div>
  );
}

/* ---------- Stepper (Design System 07) ---------- */

/**
 * The 48px well stepper. `prefix` renders "Min 2" style values; without it the value stands alone
 * (formatted by `format`). The middle value is a live region so screen readers hear changes.
 */
export function Stepper({
  label,
  value,
  onChange,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
  step = 1,
  prefix,
  format = (v) => String(v),
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  prefix?: string;
  format?: (value: number) => string;
}) {
  const set = (v: number) => onChange(Math.max(min, Math.min(max, v)));
  return (
    <div className={s.stepper} role="group" aria-label={label}>
      <button type="button" className={s.stepBtn} aria-label={`Decrease ${label}`} disabled={value <= min} onClick={() => set(value - step)}>
        <Icon name="remove" />
      </button>
      {prefix ? (
        <span className={s.stepValue} aria-live="polite">
          {prefix} <b>{format(value)}</b>
        </span>
      ) : (
        <span className={s.stepNum} aria-live="polite">
          {format(value)}
        </span>
      )}
      <button type="button" className={s.stepBtn} aria-label={`Increase ${label}`} disabled={value >= max} onClick={() => set(value + step)}>
        <Icon name="add" />
      </button>
    </div>
  );
}

/** The compact 36px stepper used inside role and lifeline cards. */
export function CompactStepper({
  label,
  value,
  onChange,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
  format = (v) => String(v),
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  format?: (value: number) => string;
}) {
  const set = (v: number) => onChange(Math.max(min, Math.min(max, v)));
  return (
    <div className={s.compact} role="group" aria-label={label}>
      <button type="button" className={s.stepBtn} aria-label={`Decrease ${label}`} disabled={value <= min} onClick={() => set(value - 1)}>
        <Icon name="remove" size={18} />
      </button>
      <span className={s.compactNum} aria-live="polite">
        {format(value)}
      </span>
      <button type="button" className={s.stepBtn} aria-label={`Increase ${label}`} disabled={value >= max} onClick={() => set(value + 1)}>
        <Icon name="add" size={18} />
      </button>
    </div>
  );
}

/* ---------- Segmented + toggle (Design System 07) ---------- */

export function Segmented<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}) {
  return (
    <div className={s.segmented} role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={o.value === value}
          className={s.segment}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Toggle({
  label,
  checked,
  onChange,
  onColour,
  offColour,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  onColour?: string;
  offColour?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      className={s.toggle}
      style={{ background: checked ? onColour : offColour }}
      onClick={() => onChange(!checked)}
    >
      <span className={s.knob} />
    </button>
  );
}

/* ---------- Banner / note (Design System 11) ---------- */

export function Note({
  tone,
  icon,
  title,
  children,
  action,
  role,
}: {
  tone: "info" | "warn" | "bad";
  icon: string;
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
  role?: "alert" | "status";
}) {
  return (
    <div className={cx(s.banner, s[tone])} role={role}>
      <Icon name={icon} />
      <div className={s.bannerBody}>
        {title && <span className={s.bannerTitle}>{title}</span>}
        {children && <span>{children}</span>}
      </div>
      {action}
    </div>
  );
}

export function SectionLabel({ children, as: Tag = "span" }: { children: ReactNode; as?: "span" | "h2" | "h3" }) {
  return <Tag className={s.section}>{children}</Tag>;
}

/* ---------- Franchise colour swatches (Home `join`) ---------- */

export function Swatches({
  value,
  taken,
  onChange,
  ringGap,
}: {
  value: string;
  taken: string[];
  onChange: (key: string) => void;
  ringGap?: string;
}) {
  return (
    <div className={s.swatches} role="group" aria-label="Team colour" style={ringGap ? ({ "--ring-gap": ringGap } as CSSProperties) : undefined}>
      {HUES.map((h) => {
        const isTaken = taken.includes(h.key) && h.key !== value;
        const picked = h.key === value;
        return (
          <button
            key={h.key}
            type="button"
            className={s.swatch}
            style={hueVar(h.key)}
            aria-pressed={picked}
            aria-label={isTaken ? `${h.label} (taken)` : h.label}
            title={h.label}
            disabled={isTaken}
            onClick={() => onChange(h.key)}
          >
            {isTaken ? <Icon name="close" size={18} /> : picked ? <Icon name="check" size={18} /> : null}
          </button>
        );
      })}
    </div>
  );
}

export { cx };
