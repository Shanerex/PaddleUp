"use client";

import { useState } from "react";
import type { Pool, PoolPlayer, PoolPlayerInput, Room } from "../../../../api/client";
import { formatPrice, parsePrice, roleSlot, tagLabel, type PackLabels } from "../../../../rooms/pool";
import { Button } from "../../../../ui/Button";
import { Icon } from "../../../../ui/Icon";
import { Field } from "../../../../ui/kit";
import { Sheet } from "../../../../ui/Sheet";
import s from "../pool.module.css";

interface Draft {
  name: string;
  country: string;
  role: string;
  tags: string[];
  price: string;
  set: string;
  providerId: string;
}

/**
 * Add or edit one player (Room `poolAdd`, `poolEdit`). Role options and tag toggles come from the
 * room's pack, the price is typed in the room's own unit, and the set is one of the pool's sets.
 * The server's field errors are shown in place; nothing is saved when any field fails.
 */
export function PlayerSheet({
  player,
  pool,
  room,
  busy,
  errors,
  onSave,
  onRemove,
  onClose,
}: {
  /** The player being edited; null adds a new one. */
  player: PoolPlayer | null;
  pool: Pool;
  room: Room;
  busy: boolean;
  errors: Record<string, string>;
  onSave: (input: PoolPlayerInput) => void;
  onRemove?: () => void;
  onClose: () => void;
}) {
  const labels = room.packLabels as PackLabels;
  const adding = player === null;
  const [draft, setDraft] = useState<Draft>(() => ({
    name: player?.name ?? "",
    country: player?.country ?? "",
    role: player?.role ?? labels.roles[0]?.id ?? "",
    tags: player?.tags ?? [],
    price: player ? formatPrice(player.basePrice, room.config) : "",
    set: player?.set ?? pool.sets[0]?.name ?? "",
    providerId: player?.providerId ?? "",
  }));
  const [localPrice, setLocalPrice] = useState<string | null>(null);
  const set = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }));

  function save() {
    const price = parsePrice(draft.price, room.config);
    if (price === null) {
      setLocalPrice(`Enter a base price like ${formatPrice(Math.max(1, room.config.incrementLadder[0]?.step ?? 1), room.config)}. Nothing was saved.`);
      return;
    }
    setLocalPrice(null);
    onSave({
      version: pool.version,
      name: draft.name,
      country: draft.country,
      role: draft.role,
      tags: draft.tags,
      basePrice: price,
      set: draft.set,
      providerId: draft.providerId.trim() || undefined,
    });
  }

  const priceError = errors.price ?? localPrice;
  return (
    <Sheet label={adding ? "Add player" : "Edit player"} onClose={onClose}>
      <div className={s.sheetHead}>
        <h2 className={s.sheetTitle}>{adding ? "Add player" : "Edit player"}</h2>
        <span className={s.sheetSub}>Changes apply to this room only. The season data stays as it is.</span>
      </div>

      <Field label="Name" value={draft.name} maxLength={60} autoComplete="off" error={errors.name} onChange={(e) => set({ name: e.target.value })} />
      <Field label="Country" value={draft.country} maxLength={40} autoComplete="off" error={errors.country} onChange={(e) => set({ country: e.target.value })} />

      <div className={s.group} role="radiogroup" aria-label="Role">
        <span className={s.groupLabel}>Role</span>
        <div className={s.roleGrid}>
          {labels.roles.map((r) => {
            const slot = roleSlot(labels, r.id ?? "");
            return (
              <button
                key={r.id}
                type="button"
                role="radio"
                aria-checked={draft.role === r.id}
                className={s.roleOpt}
                onClick={() => set({ role: r.id ?? "" })}
              >
                <span className={s.roleKey} style={{ background: `var(--role${slot}-bg)`, color: `var(--role${slot}-ink)` }}>
                  {r.id}
                </span>
                {r.label}
              </button>
            );
          })}
        </div>
        {errors.role && <span className={s.fieldError} style={{ marginTop: 0 }}>{errors.role}</span>}
      </div>

      {labels.tags.length > 0 && (
        <div className={s.group} role="group" aria-label="Tags">
          <span className={s.groupLabel}>Tags</span>
          <div className={s.tags}>
            {labels.tags.map((t) => {
              const on = draft.tags.includes(t.id ?? "");
              return (
                <button
                  key={t.id}
                  type="button"
                  className={s.tag}
                  aria-pressed={on}
                  onClick={() => set({ tags: on ? draft.tags.filter((x) => x !== t.id) : [...draft.tags, t.id ?? ""] })}
                >
                  <Icon name={on ? "check" : "add"} size={18} />
                  {tagLabel(labels, t.id ?? "")}
                </button>
              );
            })}
          </div>
          {errors.tags && <span className={s.fieldError} style={{ marginTop: 0 }}>{errors.tags}</span>}
        </div>
      )}

      <div className={s.pair}>
        <Field
          label="Base price"
          mono
          value={draft.price}
          inputMode="decimal"
          autoComplete="off"
          placeholder={formatPrice(Math.max(1, room.config.incrementLadder[0]?.step ?? 1), room.config)}
          aria-invalid={priceError ? true : undefined}
          onChange={(e) => set({ price: e.target.value })}
        />
        <label className={s.selectWrap}>
          <span className={s.groupLabel}>Set</span>
          <select className={s.select} value={draft.set} onChange={(e) => set({ set: e.target.value })} aria-label="Set">
            {pool.sets.map((x) => (
              <option key={x.name} value={x.name}>
                {x.name}
              </option>
            ))}
          </select>
          <Icon name="expand_more" className={s.selectIcon} />
        </label>
      </div>
      {priceError && (
        <span className={s.fieldError} role="alert">
          {priceError}
        </span>
      )}
      {errors.set && (
        <span className={s.fieldError} role="alert">
          {errors.set}
        </span>
      )}

      <Field
        label={
          <>
            Data provider ID <span className={s.optional}>(optional)</span>
          </>
        }
        mono
        value={draft.providerId}
        maxLength={64}
        autoComplete="off"
        placeholder="e.g. 50417"
        helper="Links match stats to this player."
        error={errors.providerId}
        onChange={(e) => set({ providerId: e.target.value })}
      />

      {errors.pool && (
        <span className={s.fieldError} role="alert">
          {errors.pool}
        </span>
      )}
      {errors.form && (
        <span className={s.fieldError} role="alert">
          {errors.form}
        </span>
      )}

      <div className={s.sheetActions}>
        {!adding && onRemove && (
          <Button variant="danger" size="L" icon="person_remove" onClick={onRemove} aria-label="Remove from pool" title="Remove from pool">
            <span className={s.removeLabel}>Remove</span>
          </Button>
        )}
        <span className={s.grow} />
        <Button variant="outline" size="L" onClick={onClose}>
          Cancel
        </Button>
        <Button variant="primary" size="L" busy={busy} onClick={save}>
          {adding ? "Add to pool" : "Save changes"}
        </Button>
      </div>
    </Sheet>
  );
}
