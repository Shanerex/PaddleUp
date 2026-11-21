import { useState } from "react";

/**
 * One intent id per user action, kept across retries. A request whose answer was lost may have been
 * committed; resending it with the same id makes the server return its first answer instead of a
 * misleading `wrong_phase`. Call `id(key)` before sending (the key names the action and its
 * arguments) and `settle()` once the server has answered, accepted or rejected. A failed request
 * is not settled, so the next tap of the same action reuses the id. An id older than `windowMs`
 * is dropped: by then the board has moved on and a tap is a new action.
 */
export class IntentRetry {
  private held: { key: string; id: string; at: number } | null = null;

  constructor(
    private readonly windowMs = 30_000,
    private readonly now: () => number = Date.now,
  ) {}

  id(key: string): string {
    const t = this.now();
    if (this.held && this.held.key === key && t - this.held.at <= this.windowMs) return this.held.id;
    this.held = { key, id: crypto.randomUUID(), at: t };
    return this.held.id;
  }

  settle(): void {
    this.held = null;
  }

  /** Sends with this action's id; any answer settles it, a thrown error keeps it for the retry. */
  async submit<T>(key: string, send: (intentId: string) => Promise<T>): Promise<T> {
    const answer = await send(this.id(key));
    this.settle();
    return answer;
  }
}

/** An {@link IntentRetry} that lives as long as the component. */
export function useIntentRetry(): IntentRetry {
  const [retry] = useState(() => new IntentRetry());
  return retry;
}
