import type { DatasetSummary } from "../../../api/client";

/** "41 KB", "1.2 MB": the size under the file name. */
export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** How many player rows a CSV or JSON text holds, or null when it cannot be told cheaply. */
export function countRows(name: string, text: string): number | null {
  if (name.toLowerCase().endsWith(".json")) {
    try {
      const players = (JSON.parse(text) as { players?: unknown }).players;
      return Array.isArray(players) ? players.length : null;
    } catch {
      return null;
    }
  }
  const lines = text.split(/\r?\n/).filter((l) => l.trim() !== "");
  return Math.max(0, lines.length - 1);
}

/** "218 rows · 41 KB" (or just the size when the rows cannot be counted). */
export function fileMeta(name: string, size: number, text: string | null): string {
  const rows = text === null ? null : countRows(name, text);
  return rows === null ? fileSize(size) : `${rows} ${rows === 1 ? "row" : "rows"} · ${fileSize(size)}`;
}

/** "Today 21:40", otherwise "Sat 2 Nov", then " · 3 skipped". */
export function versionSub(v: DatasetSummary, now: Date = new Date()): string {
  const at = new Date(v.importedAt);
  const sameDay = at.toDateString() === now.toDateString();
  const clock = `${String(at.getHours()).padStart(2, "0")}:${String(at.getMinutes()).padStart(2, "0")}`;
  const day = at.toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" }).replace(",", "");
  return `${sameDay ? `Today ${clock}` : day} · ${v.rejectedCount} skipped`;
}
