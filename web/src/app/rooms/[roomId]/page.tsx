import { RoomView } from "./room-view";

export default async function RoomPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return (
    <main className="page">
      <RoomView roomId={roomId} />
    </main>
  );
}
