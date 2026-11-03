/** Invite codes are 12 Crockford base32 characters; the server ignores case, spaces and dashes. */

/** Upper case, letters and digits only, capped at 64 like the server. */
export function normaliseCode(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 64);
}

/** "7K2M9QXD4HPA" shown as "7K2M-9QXD-4HPA" (docs/design Home `wizard:6`, `join`). */
export function formatCode(code: string): string {
  return normaliseCode(code).match(/.{1,4}/g)?.join("-") ?? "";
}
