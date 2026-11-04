import { AppShell } from "../../../ui/AppShell";
import { RoomWizard } from "../room-wizard";

/** Create a room (docs/design Home `wizard:1-2`); after "Create room" the URL carries the room id. */
export default function NewRoomPage() {
  return (
    <AppShell bleed>
      <RoomWizard />
    </AppShell>
  );
}
