import { AppShell } from "../ui/AppShell";
import { HomeHub } from "./home-hub";

/** Home hub: the signed-in landing page (docs/design Home `hubNow`, `empty`). */
export default function HomePage() {
  return (
    <AppShell>
      <HomeHub />
    </AppShell>
  );
}
