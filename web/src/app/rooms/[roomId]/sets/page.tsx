import { SetOrderView } from "./set-order-view";

/** A room's set order (docs/design Room `sets`, `setsMember`). */
export default async function SetsPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <SetOrderView roomId={roomId} />;
}
