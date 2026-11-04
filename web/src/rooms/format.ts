/** "fit-check" becomes "Fit check". Ids are opaque; this only makes them readable. */
export function humanise(id: string): string {
  const text = id.replace(/[-_]+/g, " ").trim();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** A stored amount (smallest unit) shown in the pack's display unit. */
export function toDisplay(amount: number, divisor: number): number {
  return divisor > 0 ? amount / divisor : amount;
}

/** An amount typed in the display unit, back to the smallest unit. */
export function fromDisplay(value: number, divisor: number): number {
  return Math.round(value * (divisor > 0 ? divisor : 1));
}

/** Label for an id from the room's pack snapshot, or the humanised id when it is not there. */
export function labelFor(items: { id?: string; label?: string }[], id: string): string {
  return items.find((i) => i.id === id)?.label ?? humanise(id);
}

/** The first two letters of the words of a name, upper case: a starting monogram. */
export function suggestMonogram(name: string): string {
  const words = name
    .replace(/<[^>]*>/g, " ")
    .replace(/[^\p{L}\p{N}\s]/gu, "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  const letters = words.length > 1 ? words.slice(0, 2).map((w) => w[0]).join("") : (words[0] ?? "").slice(0, 2);
  return letters.toUpperCase();
}
