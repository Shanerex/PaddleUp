import { AppShell } from "../../ui/AppShell";
import { JoinCode } from "./join-code";

/** Join by typing a code (docs/design Home `joinCode`). */
export default function JoinCodePage() {
  return (
    <AppShell>
      <JoinCode />
    </AppShell>
  );
}
