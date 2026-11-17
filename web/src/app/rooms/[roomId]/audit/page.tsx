import { AuditView } from "./audit-view";

/** A room's audit log (docs/design Room `audit`, App Screens 9b `audit` and 9c `auditEmpty`). */
export default async function AuditPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <AuditView roomId={roomId} />;
}
