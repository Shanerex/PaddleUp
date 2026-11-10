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

/** The pack's short key for a role (tight spaces), else its label, else the humanised id. */
export function roleShort(roles: { id?: string; label?: string; short?: string }[], id: string): string {
  const role = roles.find((r) => r.id === id);
  return role?.short || role?.label || humanise(id);
}

/** Quota entries in the pack's restricted-category order; ids the pack does not list come last. */
export function orderedQuotas(quotas: Record<string, number>, categories: { id?: string }[]): [string, number][] {
  const rank = (id: string) => {
    const i = categories.findIndex((c) => c.id === id);
    return i < 0 ? categories.length : i;
  };
  return Object.entries(quotas)
    .map((entry, i) => ({ entry, i }))
    .sort((a, b) => rank(a.entry[0]) - rank(b.entry[0]) || a.i - b.i)
    .map(({ entry }) => entry);
}
