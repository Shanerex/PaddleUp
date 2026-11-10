import { RoomView } from "./room-view";

/** A room (docs/design Room `setup*`). The room view draws its own team-coloured header. */
export default async function RoomPage({
  params,
  searchParams,
}: {
  params: Promise<{ roomId: string }>;
  searchParams: Promise<{ note?: string }>;
}) {
  const { roomId } = await params;
  const { note } = await searchParams;
  return <RoomView roomId={roomId} note={note} />;
}
