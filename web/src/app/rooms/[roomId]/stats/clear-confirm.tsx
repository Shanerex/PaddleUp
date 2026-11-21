"use client";

import type { StatSheet } from "../../../../api/client";
import { matchTitle } from "../../../../rooms/stat-sheet";
import { ConfirmSheet } from "../../../../ui/Sheet";

/** What clearing every override of this player does to his points and his owner's (App Screens 9j). */
export function clearBody(sheet: StatSheet): string {
  const now = sheet.points ?? 0;
  const owner = sheet.owner;
  const to = sheet.providerOnly;
  const title = matchTitle({ n: sheet.match.n, label: null });
  // Rounded like the toast's signed(), so decimal points never show float noise.
  const delta = Math.round(((to ?? 0) - now) * 100) / 100;
  const ownerLine = owner ? `, and ${owner.name} ${delta < 0 ? `drop ${Math.abs(delta)}` : delta > 0 ? `gain ${delta}` : "stay level"}` : "";
  if (to === null || to === undefined) {
    return `${title} has no provider data for ${sheet.name}, so clearing leaves nothing: ${now} pts to none${ownerLine}. The audit log keeps both entries.`;
  }
  return `${title} goes back to provider data for ${sheet.name}: ${now} to ${to} pts${ownerLine}. The audit log keeps both entries.`;
}

/** The confirm sheet: Clear override removes every overridden field of the selected player. */
export function ClearConfirm({
  sheet,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  sheet: StatSheet;
  busy: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <ConfirmSheet
      icon="backspace"
      tone="bad"
      title={`Clear ${sheet.name}'s override?`}
      body={clearBody(sheet)}
      action="Clear override"
      actionVariant="danger"
      busy={busy}
      error={error}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  );
}
