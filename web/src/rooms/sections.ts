/** The five sections of Room settings (Home `settings:*`), by their `?section=` key. Step 1 (the sport) is create-only. */
export const SECTIONS = [
  { key: "basics", step: 2, icon: "badge" },
  { key: "squad", step: 3, icon: "groups" },
  { key: "scoring", step: 4, icon: "calculate" },
  { key: "ai", step: 5, icon: "smart_toy" },
  { key: "invite", step: 6, icon: "person_add" },
] as const;
export type Section = (typeof SECTIONS)[number]["key"];

/** The create-flow step a `?section=` value maps to; anything unknown opens Basics. */
export function stepOfSection(section: string | undefined): 2 | 3 | 4 | 5 | 6 {
  return SECTIONS.find((x) => x.key === section)?.step ?? 2;
}

/** The `?section=` key of a step (Basics for a step that has none). */
export function sectionKey(step: number): Section {
  return SECTIONS.find((x) => x.step === step)?.key ?? "basics";
}
