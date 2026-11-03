import { afterEach, describe, expect, it, vi } from "vitest";
import {
  advanceRoom,
  ApiError,
  createOwnFranchise,
  createRoom,
  editFranchise,
  getInvite,
  getMe,
  getMembers,
  getRoom,
  getTransitions,
  handOver,
  issueInvite,
  joinRoom,
  listPacks,
  previewJoin,
  removeFranchise,
  revokeInvite,
  saveConfig,
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
});
