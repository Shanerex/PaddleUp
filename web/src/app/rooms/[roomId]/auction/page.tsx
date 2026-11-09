import { AuctionView } from "./auction-view";

/** The auction of a room: its lobby until the server starts it, then the lot screen. */
export default async function AuctionPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <AuctionView roomId={roomId} />;
}
