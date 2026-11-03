"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { afterSignIn } from "../../../auth/redirect";

/**
 * Google's sign-in ends here. The page the visitor was heading for was remembered before they
 * left, so this sends them there, or home when there was none.
 */
export function ContinueRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace(afterSignIn());
  }, [router]);
  return <p>Signing you in.</p>;
}
