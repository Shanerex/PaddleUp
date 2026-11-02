"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { verifyLink } from "../../../api/client";
import { HOME } from "../../../auth/redirect";

/**
 * The emailed link lands here. The token is only spent when the visitor confirms, so mail scanners
 * that open the link do not consume it.
 */
export function VerifyLink({ token }: { token?: string }) {
  const router = useRouter();
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);

  async function confirm() {
    if (!token) return;
    setBusy(true);
    try {
      await verifyLink(token);
      router.push(HOME);
    } catch {
      setFailed(true);
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <section className="panel">
        <h1>Sign-in link</h1>
        <p aria-live="assertive" className="alert">
          The sign-in token is missing from this link.
        </p>
        <a className="btn" href="/sign-in">
          Back to sign in
        </a>
      </section>
    );
  }

  return (
    <section className="panel">
      <h1>Confirm sign-in</h1>
      <p aria-live="assertive" className="alert">
        {failed ? "This link has expired or was already used. Ask for a new one." : ""}
      </p>
      {failed ? (
        <a className="btn btn-primary" href="/sign-in">
          Back to sign in
        </a>
      ) : (
        <button type="button" className="btn btn-primary" disabled={busy} onClick={confirm}>
          Confirm sign-in
        </button>
      )}
    </section>
  );
}
