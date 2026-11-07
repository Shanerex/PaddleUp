import { describe, expect, it } from "vitest";
import { ApiError } from "../api/client";
import { explainRoomError } from "./errors";

const api = (status: number, body: unknown) => new ApiError(status, body);

describe("explainRoomError", () => {
  it("tells a stale tab that the auction is live and asks for a reload", () => {
    const e = explainRoomError(api(409, { error: "room_locked" }));

    expect(e.message).toMatch(/auction is live/i);
    expect(e.refetch).toBe(true);
  });

  it("explains a stale config version and refetches", () => {
    const e = explainRoomError(api(409, { error: "stale_version" }));

    expect(e.message).toMatch(/changed these settings/i);
    expect(e.refetch).toBe(true);
  });

  it("shows the server's reason for an illegal state change and refetches", () => {
    const e = explainRoomError(
      api(409, { error: "illegal_state", message: "Needs at least 2 franchises to leave setup; has 1" }),
    );

    expect(e.message).toContain("Needs at least 2 franchises");
    expect(e.refetch).toBe(true);
  });

  it("does not refetch for a duplicate name, and shows why", () => {
    const e = explainRoomError(
      api(409, { error: "duplicate", message: "Another franchise already has that name" }),
    );

    expect(e.message).toBe("Another franchise already has that name");
    expect(e.refetch).toBe(false);
  });

  it("joins every config violation into one message", () => {
    const e = explainRoomError(
      api(400, {
        error: "invalid_config",
        violations: [
          { field: "purse", message: "Must be greater than 0" },
          { field: "incrementLadder", message: "Needs at least one step" },
        ],
      }),
    );

    expect(e.message).toBe("Must be greater than 0 Needs at least one step");
  });

  it("separates an invalid invite from a missing room", () => {
    expect(explainRoomError(api(404, { error: "invalid_invite" })).message).toMatch(/not valid any more/);
    expect(explainRoomError(api(404, { error: "room_not_found" })).message).toMatch(/could not find/);
  });

  it("covers forbidden, throttled, full and unknown failures", () => {
    expect(explainRoomError(api(403, { error: "forbidden" })).message).toMatch(/Commissioner/);
    expect(explainRoomError(api(429, { error: "too_many_requests" })).message).toMatch(/Wait a minute/);
    expect(explainRoomError(api(409, { error: "room_full" })).message).toMatch(/full/);
    expect(explainRoomError(api(500, null)).message).toMatch(/Something went wrong/);
    expect(explainRoomError(new Error("offline")).message).toMatch(/connection/);
    expect(explainRoomError(api(409, null)).refetch).toBe(true);
  });

  it("explains the pool codes: a mismatched pack, a field error and a vanished player", () => {
    expect(explainRoomError(api(409, { error: "pack_mismatch" }))).toMatchObject({ refetch: true, message: expect.stringMatching(/roles or tags this room's pack doesn't have/) });
    expect(explainRoomError(api(409, { error: "price_mismatch" }))).toMatchObject({ refetch: true, message: expect.stringMatching(/purse or price steps don't allow/) });
    expect(explainRoomError(api(422, { error: "invalid_player", violations: [{ field: "role", message: "Not a role." }] })).message).toBe("Not a role.");
    expect(explainRoomError(api(404, { error: "player_not_found" }))).toMatchObject({ refetch: true });
    expect(explainRoomError(api(409, { error: "stale_version" })).refetch).toBe(true);
  });
});
