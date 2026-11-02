import { SignInForm } from "./sign-in-form";

function PaddleMark({ className, size }: { className?: string; size?: number }) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <g transform="rotate(-16 32 32)" fill="currentColor">
        <rect x="28" y="35" width="8" height="24" rx="4" />
        <circle cx="32" cy="23" r="18" />
      </g>
    </svg>
  );
}

export default async function SignInPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  return (
    <main className="auth">
      <aside className="auth-brand">
        <PaddleMark className="watermark" />
        <div className="brand-lockup">
          <PaddleMark size={34} />
          PaddleUp
        </div>
        <div>
          <h2>Run the auction. Own the season.</h2>
          <p>
            Private franchise auctions for your group of friends, then a season-long leaderboard
            scored from real matches.
          </p>
        </div>
        <p className="fine">No money. No prizes. Just bragging rights.</p>
      </aside>
      <SignInForm error={error} />
    </main>
  );
}
