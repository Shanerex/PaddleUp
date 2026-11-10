import { AppShell } from "../../../../ui/AppShell";
import { stepOfSection } from "../../../../rooms/sections";
import { RoomWizard, type Step } from "../../room-wizard";

/**
 * `/rooms/[roomId]/setup` has two modes. `?step=N` (and the `min`/`max` that carry manager limits
 * whose save failed at create time) is the create flow's reload-safe resume (Home `wizard:2-6`).
 * A bare URL or `?section=basics|squad|scoring|ai|invite` is Room settings (Home `settings:*`).
 */
export default async function RoomSetupPage({
  params,
  searchParams,
}: {
  params: Promise<{ roomId: string }>;
  searchParams: Promise<{ step?: string; section?: string; min?: string; max?: string }>;
}) {
  const { roomId } = await params;
  const { step, section, min, max } = await searchParams;
  const creating = step !== undefined || (min !== undefined && max !== undefined);
  if (!creating) {
    return (
      <AppShell bleed>
        <RoomWizard mode="settings" roomId={roomId} initialStep={stepOfSection(section) as Step} />
      </AppShell>
    );
  }
  const n = Number(step);
  const initial = (Number.isInteger(n) && n >= 1 && n <= 6 ? n : 2) as Step;
  const lo = Number(min);
  const hi = Number(max);
  const unsaved = min && max && Number.isInteger(lo) && Number.isInteger(hi) ? { min: lo, max: hi } : undefined;
  return (
    <AppShell bleed>
      <RoomWizard roomId={roomId} initialStep={initial} unsavedLimits={unsaved} />
    </AppShell>
  );
}
