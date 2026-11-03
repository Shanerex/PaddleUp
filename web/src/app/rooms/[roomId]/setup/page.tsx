import { AppShell } from "../../../../ui/AppShell";
import { RoomWizard, type Step } from "../../room-wizard";

/** Room setup for an existing room (docs/design Home `wizard:2-6`); reload-safe. */
export default async function RoomSetupPage({
  params,
  searchParams,
}: {
  params: Promise<{ roomId: string }>;
  searchParams: Promise<{ step?: string }>;
}) {
  const { roomId } = await params;
  const { step } = await searchParams;
  const n = Number(step);
  const initial = (Number.isInteger(n) && n >= 1 && n <= 6 ? n : 2) as Step;
  return (
    <AppShell bleed>
      <RoomWizard roomId={roomId} initialStep={initial} />
    </AppShell>
  );
}
