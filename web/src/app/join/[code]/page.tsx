import { AppShell } from "../../../ui/AppShell";
import { JoinForm } from "./join-form";

/** A join link (docs/design Home `join`, `joinError`). A malformed %-escape is passed through raw. */
export default async function JoinPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  let decoded = code;
  try {
    decoded = decodeURIComponent(code);
  } catch {
    decoded = code;
  }
  return (
    <AppShell>
      <JoinForm code={decoded} />
    </AppShell>
  );
}
