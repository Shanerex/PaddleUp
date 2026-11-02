import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, getMe, signOut, startEmail, updateProfile, verifyCode, verifyLink } from "./client";

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
