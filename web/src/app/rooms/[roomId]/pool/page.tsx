import { PoolView } from "./pool-view";

/** A room's player pool (docs/design Room `pool`, `poolMember`, `poolLive`, `poolEmpty`, `poolStale`). */
export default async function PoolPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <PoolView roomId={roomId} />;
}
