import { describe, expect, it, vi } from "vitest";
import { checkSession } from "./session";

const API = "http://api.test";

function answering(status: number) {
  return vi.fn(async () => new Response(null, { status }));
}

describe("checkSession", () => {
  it("is absent without asking the server when there is no cookie", async () => {
    const fetcher = answering(200);
    expect(await checkSession(undefined, API, fetcher)).toBe("absent");
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("asks the server's /api/me with only the session cookie", async () => {
    const fetcher = answering(200);
    await checkSession("abc123", API, fetcher);
    expect(fetcher).toHaveBeenCalledWith(
      "http://api.test/api/me",
      expect.objectContaining({ headers: { cookie: "pu_session=abc123" } }),
    );
  });

  it("is valid when the server knows the session", async () => {
    expect(await checkSession("abc", API, answering(200))).toBe("valid");
  });

  it("is invalid when the server answers 401", async () => {
    expect(await checkSession("abc", API, answering(401))).toBe("invalid");
  });

  it("is unknown when the server errors", async () => {
    expect(await checkSession("abc", API, answering(503))).toBe("unknown");
  });

  it("is unknown when the server cannot be reached", async () => {
    const fetcher = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    expect(await checkSession("abc", API, fetcher)).toBe("unknown");
  });
});
