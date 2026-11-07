import { ApiError } from "../api/client";
import { unfitClause } from "./pool";

/** What to tell the person, and whether the screen should reload the room to match the server. */
export interface Explained {
  message: string;
  /** True when the screen showed stale state: refetch so the next action starts from the truth. */
  refetch: boolean;
}

function errorCode(body: unknown): string | null {
  if (body && typeof body === "object" && "error" in body) {
    const code = (body as { error?: unknown }).error;
    return typeof code === "string" ? code : null;
  }
  return null;
}

function serverMessage(body: unknown): string | null {
  if (body && typeof body === "object" && "message" in body) {
    const message = (body as { message?: unknown }).message;
    return typeof message === "string" && message ? message : null;
  }
  return null;
}

function violations(body: unknown): string[] {
  if (body && typeof body === "object" && "violations" in body) {
    const list = (body as { violations?: unknown }).violations;
    if (Array.isArray(list)) {
      return list
        .map((v) => (v && typeof v === "object" ? (v as { message?: unknown }).message : null))
        .filter((m): m is string => typeof m === "string");
    }
  }
  return [];
}

/**
 * Turns a failed room request into a clear message. A 409 means the screen was out of date (the
 * auction went live, someone else saved, the state moved on), so it also asks for a refetch.
 */
export function explainRoomError(error: unknown): Explained {
  if (!(error instanceof ApiError)) {
    return { message: "Something went wrong. Check your connection and try again.", refetch: false };
  }
  const code = errorCode(error.body);
  switch (error.status) {
    case 400: {
      const problems = violations(error.body);
      if (problems.length > 0) return { message: problems.join(" "), refetch: false };
      return { message: serverMessage(error.body) ?? "Check the details and try again.", refetch: false };
    }
    case 403:
      return { message: "Only the Commissioner can do that.", refetch: true };
    case 422: {
      const problems = violations(error.body);
      return { message: problems.length > 0 ? problems.join(" ") : "Check the details and try again.", refetch: false };
    }
    case 404:
      if (code === "invalid_invite") {
        return {
          message: "This invite is not valid any more. Ask the Commissioner for a new one.",
          refetch: false,
        };
      }
      if (code === "no_invite") return { message: "There is no active invite yet.", refetch: false };
      if (code === "player_not_found") {
        return { message: "That player is no longer in the pool. We loaded the latest.", refetch: true };
      }
      return { message: "We could not find that room, or you are not in it.", refetch: false };
    case 409:
      switch (code) {
        case "room_locked":
          return {
            message: "The auction is live, so this can no longer be changed. We reloaded the room.",
            refetch: true,
          };
        case "stale_version":
          return {
            message:
              "Someone else changed these settings. We loaded the latest; check them and save again.",
            refetch: true,
          };
        case "illegal_state":
          return {
            message: `${serverMessage(error.body) ?? "That move is not allowed right now."} We reloaded the room.`,
            refetch: true,
          };
        case "pack_mismatch":
        case "price_mismatch":
          return {
            message: `The season data can't be loaded here: ${unfitClause(code === "pack_mismatch" ? "pack" : "price")}.`,
            refetch: true,
          };
        case "room_full":
          return { message: "This room is full.", refetch: true };
        case "duplicate":
          return { message: serverMessage(error.body) ?? "That is already taken.", refetch: false };
        default:
          return { message: "The room has changed. We reloaded it.", refetch: true };
      }
    case 429:
      return { message: "Too many tries. Wait a minute and try again.", refetch: false };
    default:
      return { message: "Something went wrong. Try again in a moment.", refetch: false };
  }
}
