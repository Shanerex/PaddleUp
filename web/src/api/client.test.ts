import { afterEach, describe, expect, it, vi } from "vitest";
import { pageClock } from "../auction/clock";
import {
  advanceRoom,
  bid,
  ApiError,
  createOwnFranchise,
  createRoom,
  editFranchise,
  getAuction,
  getInvite,
  getMe,
  getMembers,
  getRoom,
  getTicket,
  getTransitions,
  handOver,
  issueInvite,
  joinRoom,
  listMyRooms,
  listPacks,
  previewJoin,
  removeFranchise,
  revokeInvite,
  saveConfig,
  scheduleAuction,
  setReady,
  signOut,
  startEmail,
  updateProfile,
  verifyCode,
  verifyLink,
  type RoomConfig,
} from "./client";

function respond(status: number, body?: unknown) {
  return vi.fn().mockImplementation(async () =>
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
    }),
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("api client", () => {
  it("reads the profile with the session cookie", async () => {
    const fetchMock = respond(200, { id: "1", email: "a@b.c", displayName: "Al", avatar: "preset-01" });
    vi.stubGlobal("fetch", fetchMock);

    const me = await getMe();

    expect(me?.displayName).toBe("Al");
    expect(fetchMock).toHaveBeenCalledWith("/api/me", expect.objectContaining({ credentials: "same-origin" }));
  });

  it("returns null from getMe when signed out", async () => {
    vi.stubGlobal("fetch", respond(401, { error: "unauthorised" }));
    expect(await getMe()).toBeNull();
  });

  it("posts json for email start, verify and profile updates", async () => {
    const fetchMock = respond(200, { id: "1", email: "a@b.c", displayName: "Al", avatar: "preset-01" });
    vi.stubGlobal("fetch", fetchMock);

    await startEmail("a@b.c");
    await verifyCode("a@b.c", "123456");
    await verifyLink("tok");
    await updateProfile({ displayName: "Bo" });

    const calls = fetchMock.mock.calls.map(([url, init]) => [url, init.method, init.body]);
    expect(calls).toEqual([
      ["/api/auth/email/start", "POST", JSON.stringify({ email: "a@b.c" })],
      ["/api/auth/email/verify", "POST", JSON.stringify({ email: "a@b.c", code: "123456" })],
      ["/api/auth/email/verify", "POST", JSON.stringify({ token: "tok" })],
      ["/api/me", "PATCH", JSON.stringify({ displayName: "Bo" })],
    ]);
  });

  it("throws ApiError with the status for failures", async () => {
    vi.stubGlobal("fetch", respond(401, { error: "invalid_login" }));
    await expect(verifyCode("a@b.c", "000000")).rejects.toMatchObject({ status: 401 });
    await expect(verifyCode("a@b.c", "000000")).rejects.toBeInstanceOf(ApiError);
  });

  it("signs out with POST", async () => {
    const fetchMock = respond(204);
    vi.stubGlobal("fetch", fetchMock);
    await signOut();
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/sign-out", expect.objectContaining({ method: "POST" }));
  });
});

describe("room api", () => {
  const franchise = { name: "Kings", colour: "sky", monogram: "KG" };

  function calls(fetchMock: ReturnType<typeof respond>) {
    return fetchMock.mock.calls.map(([url, init]) => [url, init.method ?? "GET", init.body]);
  }

  it("calls each room endpoint with the right method, path and body", async () => {
    const fetchMock = respond(200, {});
    vi.stubGlobal("fetch", fetchMock);
    const config = { name: "Friends" } as RoomConfig;

    await listMyRooms();
    await listPacks();
    await createRoom({ packId: "p", presetId: "standard", name: "Friends" });
    await getRoom("r1");
    await saveConfig("r1", 3, config);
    await issueInvite("r1");
    await revokeInvite("r1");
    await getMembers("r1");
    await createOwnFranchise("r1", franchise);
    await editFranchise("r1", "f1", { name: "New name" });
    await removeFranchise("r1", "f1");
    await handOver("r1", "f2");
    await advanceRoom("r1", "retention");
    await getTransitions("r1");
    await previewJoin("ABC123");
    await joinRoom("ABC123", franchise);

    expect(calls(fetchMock)).toEqual([
      ["/api/rooms", "GET", undefined],
      ["/api/rooms/packs", "GET", undefined],
      ["/api/rooms", "POST", JSON.stringify({ packId: "p", presetId: "standard", name: "Friends" })],
      ["/api/rooms/r1", "GET", undefined],
      ["/api/rooms/r1/config", "PUT", JSON.stringify({ version: 3, config })],
      ["/api/rooms/r1/invite", "POST", "{}"],
      ["/api/rooms/r1/invite", "DELETE", undefined],
      ["/api/rooms/r1/members", "GET", undefined],
      ["/api/rooms/r1/franchises", "POST", JSON.stringify(franchise)],
      ["/api/rooms/r1/franchises/f1", "PATCH", JSON.stringify({ name: "New name" })],
      ["/api/rooms/r1/franchises/f1", "DELETE", undefined],
      ["/api/rooms/r1/commissioner", "POST", JSON.stringify({ franchiseId: "f2" })],
      ["/api/rooms/r1/transitions", "POST", JSON.stringify({ to: "retention" })],
      ["/api/rooms/r1/transitions", "GET", undefined],
      ["/api/join/preview", "POST", JSON.stringify({ code: "ABC123" })],
      ["/api/join", "POST", JSON.stringify({ code: "ABC123", franchise })],
    ]);
  });

  it("encodes ids so a crafted id cannot change the path", async () => {
    const fetchMock = respond(200, {});
    vi.stubGlobal("fetch", fetchMock);

    await getRoom("a/../b?x=1");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/rooms/a%2F..%2Fb%3Fx%3D1");
  });

  it("reads the active invite, and null when there is none", async () => {
    vi.stubGlobal("fetch", respond(200, { code: "ABC", link: "http://x/join/ABC", expiresAt: "t" }));
    expect((await getInvite("r1"))?.code).toBe("ABC");

    vi.stubGlobal("fetch", respond(404, { error: "no_invite" }));
    expect(await getInvite("r1")).toBeNull();

    vi.stubGlobal("fetch", respond(403, { error: "forbidden" }));
    await expect(getInvite("r1")).rejects.toMatchObject({ status: 403 });
  });

  it("surfaces the server's error body on a failed room call", async () => {
    vi.stubGlobal("fetch", respond(409, { error: "stale_version" }));

    await expect(saveConfig("r1", 1, {} as RoomConfig)).rejects.toMatchObject({
      status: 409,
      body: { error: "stale_version" },
    });
  });

  it("reads the auction, sends bids and the ready toggle, and asks for a ticket", async () => {
    const fetchMock = respond(200, { phase: "scheduled" });
    vi.stubGlobal("fetch", fetchMock);

    await getAuction("r 1");
    await bid("r1", { intentId: "i-1", lot: 3, amount: 25 });
    await setReady("r1", true);
    await getTicket("r1");

    const calls = fetchMock.mock.calls.map(([url, init]) => [url, init.method, init.body]);
    expect(calls).toEqual([
      ["/api/rooms/r%201/auction", undefined, undefined],
      ["/api/rooms/r1/auction/bids", "POST", JSON.stringify({ intentId: "i-1", lot: 3, amount: 25 })],
      ["/api/rooms/r1/auction/ready", "PUT", JSON.stringify({ ready: true })],
      ["/api/rooms/r1/auction/ticket", "POST", undefined],
    ]);
  });

  it("schedules the auction with one UTC instant", async () => {
    const fetchMock = respond(200, { phase: "scheduled" });
    vi.stubGlobal("fetch", fetchMock);

    await scheduleAuction("r 1", "2031-01-02T20:00:00.000Z");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/rooms/r%201/auction/schedule");
    expect(init.method).toBe("PUT");
    expect(init.body).toBe(JSON.stringify({ startsAt: "2031-01-02T20:00:00.000Z" }));
  });

  it("surfaces a startsAt field error and a pool refusal from the server", async () => {
    vi.stubGlobal("fetch", respond(400, { error: "invalid_request", message: "The start must be in the future", field: "startsAt" }));
    const past = await scheduleAuction("r1", "2000-01-01T00:00:00Z").catch((e) => e);
    expect(past).toBeInstanceOf(ApiError);
    expect(past.status).toBe(400);
    expect(past.body).toMatchObject({ field: "startsAt" });

    vi.stubGlobal("fetch", respond(409, { error: "not_ready", message: "The pool has 64 players and this room needs at least 72." }));
    const refused = await scheduleAuction("r1", "2031-01-02T20:00:00Z").catch((e) => e);
    expect(refused.status).toBe(409);
    expect(refused.body).toMatchObject({ error: "not_ready", message: expect.stringContaining("64 players") });
  });

  it("feeds the page clock from the server's Date header, never from the browser's", async () => {
    const server = Date.parse("2031-01-02T10:00:00Z");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 200, headers: { "Content-Type": "application/json", Date: new Date(server).toUTCString() } })));

    await getAuction("r1");

    expect(pageClock.ready).toBe(true);
    expect(Math.abs(pageClock.now() - server)).toBeLessThan(2000);
  });
});
