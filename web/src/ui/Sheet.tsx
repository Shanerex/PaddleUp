"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { Button, type ButtonVariant } from "./Button";
import { Icon } from "./Icon";
import { useLatest } from "./useLatest";
import s from "./Sheet.module.css";

/**
 * A modal sheet (Room `sheet*` frames). Escape and the scrim close it; focus moves into it on open
 * and back to the opener on close; Tab stays inside. With a `title` it draws the auction sheets'
 * title row (title left, 36px close button right, Auction A Spotlight) instead of the drag handle;
 * from 1024px those sheets are a full-height panel on the right, as the mock draws them (Explorations
 * 3n-3v at 1440).
 */
export function Sheet({
  label,
  title,
  onClose,
  children,
}: {
  label: string;
  title?: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const close = useLatest(onClose);

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const box = ref.current;
    const focusables = () =>
      Array.from(
        box?.querySelectorAll<HTMLElement>("button:not([disabled]), input:not([disabled]), a[href]") ?? [],
      );
    (focusables()[0] ?? box)?.focus();
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.preventDefault();
        close.current();
      } else if (e.key === "Tab") {
        const list = focusables();
        if (list.length === 0) return;
        const first = list[0];
        const last = list[list.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      opener?.focus?.();
    };
  }, [close]);

  return (
    <div className={`${s.scrim} ${title ? s.scrimSide : ""}`} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={ref} className={`${s.sheet} ${title ? s.sheetSide : ""}`} role="dialog" aria-modal="true" aria-label={label} tabIndex={-1}>
        {title ? (
          <div className={s.head}>
            <h2 className={s.headTitle}>{title}</h2>
            <button type="button" className={s.close} aria-label="Close" onClick={onClose}>
              <Icon name="close" size={20} />
            </button>
          </div>
        ) : (
          <span className={s.handle} />
        )}
        {children}
      </div>
    </div>
  );
}

/** A confirm sheet: tile, title, body, Cancel and one action (Room `sheetRemove/Handover/Advance`). */
export function ConfirmSheet({
  icon,
  tone,
  title,
  body,
  action,
  actionVariant,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  icon: string;
  tone: "bad" | "team";
  title: string;
  body: ReactNode;
  action: string;
  actionVariant: ButtonVariant;
  busy?: boolean;
  error?: string | null;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const id = useId();
  return (
    <Sheet label={title} onClose={onClose}>
      <span
        className={s.tile}
        style={
          tone === "bad"
            ? { background: "var(--bad-tint)", color: "var(--bad)" }
            : { background: "var(--team-tint)", color: "var(--team-ink)" }
        }
      >
        <Icon name={icon} size={28} />
      </span>
      <div className={s.text}>
        <h2 className={s.title} id={id}>
          {title}
        </h2>
        <p className={s.body}>{body}</p>
      </div>
      {error && (
        <p role="alert" className={s.body} style={{ color: "var(--bad)", fontSize: 13, fontWeight: 600 }}>
          {error}
        </p>
      )}
      <div className={s.actions}>
        <Button variant="outline" size="L" onClick={onClose}>
          Cancel
        </Button>
        <Button variant={actionVariant} size="L" busy={busy} onClick={onConfirm}>
          {action}
        </Button>
      </div>
    </Sheet>
  );
}

/** Design System 11 toast. Auto-dismisses after 4 seconds. */
export function Toast({
  title,
  body,
  onDone,
}: {
  title: string;
  body?: string;
  onDone: () => void;
}) {
  const done = useLatest(onDone);
  useEffect(() => {
    const t = setTimeout(() => done.current(), 4000);
    return () => clearTimeout(t);
  }, [done]);
  return (
    <div className={s.toastWrap} role="status" aria-live="polite">
      <div className={s.toast}>
        <Icon name="check_circle" size={22} style={{ color: "var(--ok)" }} />
        <div style={{ display: "flex", flexDirection: "column", gap: 2, flex: 1 }}>
          <span className={s.toastTitle}>{title}</span>
          {body && <span className={s.toastBody}>{body}</span>}
        </div>
      </div>
    </div>
  );
}

export { s as sheetStyles };
