/** Lifeline types are core, not sport: this is their display order (the server's type list). */
export const LIFELINE_ORDER = ["fit-check", "price-ceiling", "rival-scan"] as const;

/** Lifeline counts in the core order; a type outside it comes last, in its stored order. */
export function orderedLifelines(lifelines: Record<string, number>): [string, number][] {
  const rank = (type: string) => {
    const i = (LIFELINE_ORDER as readonly string[]).indexOf(type);
    return i < 0 ? LIFELINE_ORDER.length : i;
  };
  return Object.entries(lifelines)
    .map((entry, i) => ({ entry, i }))
    .sort((a, b) => rank(a.entry[0]) - rank(b.entry[0]) || a.i - b.i)
    .map(({ entry }) => entry);
}
