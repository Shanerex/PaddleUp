import { SquadsView } from "./squads-view";

/** Every franchise's squad during the auction (docs/design Room `squadLive`, `squadEmpty`). */
export default async function SquadsPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <SquadsView roomId={roomId} />;
}
