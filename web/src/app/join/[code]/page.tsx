import { JoinForm } from "./join-form";

export default async function JoinPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  return (
    <main className="page">
      <JoinForm code={decodeURIComponent(code)} />
    </main>
  );
}
