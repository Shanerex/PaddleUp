"use client";

import type { AuctionLot, RoomConfig } from "../../../../api/client";
import { formatMoney } from "../../../../rooms/money";
import { roleLabel, roleSlot, tagLabel, type PackLabels } from "../../../../rooms/pool";
import s from "./auction.module.css";
import { ResultStamp, type Who } from "./notices";

export type LotResult = { sold: boolean; who: Who | null; amount: number; youWon: boolean };

/**
 * The lot card (Auction A Spotlight): role, tags, name, country and the facts. Labels and money come
 * from the room's pack and config, never from this file (R-5). Photo and last-season points are in
 * no data today; they render only when given, so their absence leaves no gap (AC-4).
 */
export function LotCard({
  lot,
  labels,
  config,
  photoUrl,
  lastSeasonPoints,
  result,
}: {
  lot: AuctionLot;
  labels: PackLabels;
  config: Pick<RoomConfig, "currencyCode" | "displayUnit">;
  photoUrl?: string | null;
  lastSeasonPoints?: number | null;
  result?: LotResult | null;
}) {
  const slot = roleSlot(labels, lot.role);
  const facts: { label: string; value: string }[] = [{ label: "BASE", value: formatMoney(lot.basePrice, config) }];
  if (lastSeasonPoints != null) facts.push({ label: "LAST SEASON", value: `${lastSeasonPoints} pts` });
  return (
    <section className={s.lotCard} aria-label="Lot" data-testid="lot-card">
      {photoUrl && (
        <div className={s.photo} style={{ backgroundImage: `url(${JSON.stringify(photoUrl)})` }} role="img" aria-label={lot.name}>
          <span className={s.lotTag}>LOT {lot.no}</span>
        </div>
      )}
      <div className={s.chips}>
        <span className={s.roleChip} style={{ background: `var(--role${slot}-bg)`, color: `var(--role${slot}-ink)` }}>
          {roleLabel(labels, lot.role)}
        </span>
        {lot.tags.map((t) => (
          <span key={t} className={s.tagChip}>
            {tagLabel(labels, t)}
          </span>
        ))}
      </div>
      <div className={s.nameBlock}>
        <div className={s.playerName} data-testid="lot-name">
          {lot.name}
        </div>
        <div className={s.country}>{lot.country}</div>
      </div>
      <div className={s.facts} style={{ "--cells": facts.length } as React.CSSProperties}>
        {facts.map((f) => (
          <div key={f.label} className={s.fact}>
            <span className={s.factLabel}>{f.label}</span>
            <span className={s.factValue}>{f.value}</span>
          </div>
        ))}
      </div>
      {result && <ResultStamp sold={result.sold} who={result.who} amount={formatMoney(result.amount, config)} youWon={result.youWon} />}
    </section>
  );
}
