import { AppShell } from "../../ui/AppShell";
import { ProfileView } from "./profile-view";

/** Profile (Home `profile`), reached from the top-bar avatar. */
export default function ProfilePage() {
  return (
    <AppShell>
      <ProfileView />
    </AppShell>
  );
}
