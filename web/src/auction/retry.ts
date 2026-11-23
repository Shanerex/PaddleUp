/**
 * Whether a failed read is worth reading again by itself: a network failure, a 5xx, a 408 or a 429 may
 * pass; any other 4xx (not found, forbidden, ...) will answer the same every time. Anything without a
 * numeric `status` (a thrown network error) counts as retryable.
 */
export function isRetryable(error: unknown): boolean {
  const status = (error as { status?: unknown } | null)?.status;
  if (typeof status !== "number") return true;
  return status < 400 || status >= 500 || status === 408 || status === 429;
}
