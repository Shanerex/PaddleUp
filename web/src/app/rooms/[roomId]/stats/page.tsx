import { StatEntryView } from "./stat-entry-view";

/** Manual stat entry (docs/design Room `statEntry`, App Screens 9f-9j). Commissioner only. */
export default async function StatEntryPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <StatEntryView roomId={roomId} />;
}
