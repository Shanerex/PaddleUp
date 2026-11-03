import { RoomView } from "./room-view";

/** A room (docs/design Room `setup*`). The room view draws its own team-coloured header. */
export default async function RoomPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <RoomView roomId={roomId} />;
}
