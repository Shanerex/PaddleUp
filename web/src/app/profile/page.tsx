import { AppShell, shell } from "../../ui/AppShell";
import { ProfileView } from "./profile-view";

/** Profile, reached from the top-bar avatar. Keeps the account panel inside the app shell. */
export default function ProfilePage() {
  return (
    <AppShell>
      <div className={shell.centered}>
        <ProfileView />
      </div>
    </AppShell>
  );
}
