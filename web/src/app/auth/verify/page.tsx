import { VerifyLink } from "./verify-link";

export default async function VerifyPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  return (
    <main>
      <VerifyLink token={token} />
    </main>
  );
}
