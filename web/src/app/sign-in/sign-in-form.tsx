"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, GOOGLE_SIGN_IN_URL, startEmail, verifyCode } from "../../api/client";
import { afterSignIn, rememberNext } from "../../auth/redirect";

const LINK_ERRORS: Record<string, string> = {
  unverified_email:
    "Google says that email address is not verified, so we cannot sign you in with it.",
  google: "Google sign-in did not complete. Try again or use your email.",
};

export function SignInForm({ error, next }: { error?: string; next?: string }) {
  const router = useRouter();
  const [step, setStep] = useState<"email" | "code">("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [message, setMessage] = useState<string | null>(error ? (LINK_ERRORS[error] ?? null) : null);
  const [busy, setBusy] = useState(false);

  // Kept in storage, not in the page, so it survives Google's round trip and a magic link that
  // opens in another tab.
  useEffect(() => {
    rememberNext(next);
  }, [next]);

  async function requestEmail(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await startEmail(email.trim());
      setStep("code");
    } catch (e) {
      setMessage(
        e instanceof ApiError && e.status === 400
          ? "Enter a valid email address."
          : e instanceof ApiError && e.status === 429
            ? "Too many sign-in emails requested. Wait a few minutes and try again."
            : "We could not send the email. Try again in a moment.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function submitCode(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await verifyCode(email.trim(), code.trim());
      router.push(afterSignIn());
    } catch (e) {
      setMessage(
        e instanceof ApiError && e.status === 401
          ? "That code did not work. Check it, or ask for a new email."
          : e instanceof ApiError && e.status === 429
            ? "Too many attempts. Wait a while and try again."
            : "Something went wrong. Try again.",
      );
      setBusy(false);
    }
  }

  return (
    <section className="auth-form">
      <div className="auth-card">
        <h1>{step === "email" ? "Sign in" : "Check your email"}</h1>
        <p aria-live="assertive" className="alert">
          {message}
        </p>

        {step === "email" ? (
          <>
            <a className="btn btn-google" href={GOOGLE_SIGN_IN_URL}>
              <GoogleLogo />
              Continue with Google
            </a>
            <div className="divider">or email</div>
            <form onSubmit={requestEmail} className="stack">
              <label className="field">
                <span>Email</span>
                <input
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                Send me a magic link
              </button>
            </form>
            <p className="helper">
              Got an invite link? Sign in first and we&apos;ll drop you straight into the room.
            </p>
          </>
        ) : (
          <form onSubmit={submitCode} className="stack">
            <p className="helper">
              We sent a link and a 6-digit code to <strong>{email}</strong>. Open the link, or type
              the code here.
            </p>
            <label className="field">
              <span>Code</span>
              <input
                className="mono"
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                maxLength={6}
                placeholder="000000"
                required
                value={code}
                onChange={(e) => setCode(e.target.value)}
              />
            </label>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              Sign in
            </button>
            <button
              type="button"
              className="btn btn-quiet"
              onClick={() => {
                setStep("email");
                setCode("");
                setMessage(null);
              }}
            >
              Use a different email
            </button>
          </form>
        )}
      </div>
    </section>
  );
}

function GoogleLogo() {
  return (
    <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
      <path
        fill="#EA4335"
        d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
      />
      <path
        fill="#4285F4"
        d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
      />
      <path
        fill="#FBBC05"
        d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
      />
      <path
        fill="#34A853"
        d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
      />
    </svg>
  );
}
