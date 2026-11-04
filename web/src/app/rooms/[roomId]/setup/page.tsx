import { AppShell } from "../../../../ui/AppShell";
import { RoomWizard, type Step } from "../../room-wizard";

/**
 * Room setup for an existing room (docs/design Home `wizard:2-6`); reload-safe. `min`/`max` carry
 * manager limits chosen at create time whose save failed, so step 2 can offer them again.
 */
export default async function RoomSetupPage({
  params,
  searchParams,
}: {
  params: Promise<{ roomId: string }>;
  searchParams: Promise<{ step?: string; min?: string; max?: string }>;
}) {
  const { roomId } = await params;
  const { step, min, max } = await searchParams;
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
