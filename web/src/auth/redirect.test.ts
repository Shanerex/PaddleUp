import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { afterSignIn, HOME, NEXT_KEY, rememberNext, safeNext, SESSION_COOKIE, signInRedirect, takeNext } from "./redirect";

describe("signInRedirect", () => {
  it("sends a visitor with no session cookie to sign-in", () => {
    expect(signInRedirect("/", "absent")).toBe("/sign-in");
    expect(signInRedirect("/profile", "absent")).toBe("/sign-in?next=%2Fprofile");
  });

  it("remembers the page a signed-out visitor asked for", () => {
    expect(signInRedirect("/rooms/abc", "absent")).toBe("/sign-in?next=%2Frooms%2Fabc");
    expect(signInRedirect("/join/ABC123", "invalid")).toBe("/sign-in?next=%2Fjoin%2FABC123");
    expect(signInRedirect("/rooms/abc", "absent", "?tab=members")).toBe(
      "/sign-in?next=%2Frooms%2Fabc%3Ftab%3Dmembers",
    );
  });

  it("drops a page that is not safe to return to", () => {
    expect(signInRedirect("//evil.test/x", "absent")).toBe("/sign-in");
    expect(signInRedirect("/auth/continue", "absent")).toBe("/sign-in");
  });

  it("sends a visitor whose cookie the server rejects to sign-in", () => {
    expect(signInRedirect("/", "invalid")).toBe("/sign-in");
    expect(signInRedirect("/profile", "invalid")).toBe("/sign-in?next=%2Fprofile");
  });

  it("lets a signed-in visitor through, in any tab", () => {
    expect(signInRedirect("/", "valid")).toBeNull();
    expect(signInRedirect("/profile", "valid")).toBeNull();
    expect(signInRedirect("/rooms/abc", "valid")).toBeNull();
  });

  it("sends a signed-in visitor away from the sign-in page", () => {
    expect(signInRedirect("/sign-in", "valid")).toBe(HOME);
  });

  it("sends a signed-in visitor on to the page they were heading for, if it is safe", () => {
    expect(signInRedirect("/sign-in", "valid", "?next=%2Fjoin%2FABC")).toBe("/join/ABC");
    expect(signInRedirect("/sign-in", "valid", "?next=https%3A%2F%2Fevil.test")).toBe(HOME);
    expect(signInRedirect("/sign-in", "valid", "?next=%2F%2Fevil.test")).toBe(HOME);
  });

  it("lets the sign-in pages through for anyone not signed in", () => {
    for (const state of ["absent", "invalid", "unknown"] as const) {
      expect(signInRedirect("/sign-in", state)).toBeNull();
      expect(signInRedirect("/auth/verify", state)).toBeNull();
    }
  });

  it("lets a magic link through even when signed in, so it can switch accounts", () => {
    expect(signInRedirect("/auth/verify", "valid")).toBeNull();
  });

  it("does not sign anyone out when the server cannot be reached", () => {
    expect(signInRedirect("/profile", "unknown")).toBeNull();
  });

  it("never redirects the api, which answers 401 itself", () => {
    expect(signInRedirect("/api/me", "absent")).toBeNull();
    expect(signInRedirect("/api/me", "invalid")).toBeNull();
  });

  it("uses the server's cookie name", () => {
    expect(SESSION_COOKIE).toBe("pu_session");
  });
});

describe("safeNext", () => {
  it("accepts a same-origin path with a query", () => {
    expect(safeNext("/join/ABC123")).toBe("/join/ABC123");
    expect(safeNext("/rooms/1?tab=members")).toBe("/rooms/1?tab=members");
  });

  it("rejects anything that could leave the site", () => {
    for (const bad of [
      "https://evil.test",
      "http://evil.test/join",
      "//evil.test",
      "///evil.test",
      "/\\evil.test",
      "/\tevil",
      "javascript:alert(1)",
      "evil.test/path",
      "",
      " /join/x",
      "/join/\nx",
      "/a\u0000b",
    ]) {
      expect(safeNext(bad), JSON.stringify(bad)).toBeNull();
    }
    expect(safeNext(null)).toBeNull();
    expect(safeNext(undefined)).toBeNull();
    expect(safeNext("/" + "a".repeat(600))).toBeNull();
  });

  it("never returns to a sign-in page or the api", () => {
    expect(safeNext("/sign-in")).toBeNull();
    expect(safeNext("/sign-in?next=/x")).toBeNull();
    expect(safeNext("/auth/verify?token=x")).toBeNull();
    expect(safeNext("/auth/continue")).toBeNull();
    expect(safeNext("/api/me")).toBeNull();
  });
});

describe("remembering the page to return to", () => {
  const store = new Map<string, string>();

  beforeEach(() => {
    store.clear();
    vi.stubGlobal("localStorage", {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
      removeItem: (k: string) => void store.delete(k),
    });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it("returns a remembered page once, then forgets it", () => {
    rememberNext("/join/ABC123");

    expect(takeNext()).toBe("/join/ABC123");
    expect(takeNext()).toBeNull();
    expect(store.has(NEXT_KEY)).toBe(false);
  });

  it("does not store an unsafe page", () => {
    rememberNext("https://evil.test");
    rememberNext("//evil.test");

    expect(store.size).toBe(0);
    expect(afterSignIn()).toBe(HOME);
  });

  it("rejects an unsafe value that was planted in storage", () => {
    store.set(NEXT_KEY, JSON.stringify({ next: "https://evil.test", at: Date.now() }));

    expect(takeNext()).toBeNull();
  });

  it("ignores garbage and a page remembered long ago", () => {
    store.set(NEXT_KEY, "not json");
    expect(takeNext()).toBeNull();

    vi.useFakeTimers();
    rememberNext("/join/ABC123");
    vi.advanceTimersByTime(2 * 60 * 60 * 1000);
    expect(takeNext()).toBeNull();
  });

  it("goes home when nothing is remembered and when storage is blocked", () => {
    expect(afterSignIn()).toBe(HOME);
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("blocked");
      },
      setItem: () => {
        throw new Error("blocked");
      },
      removeItem: () => {
        throw new Error("blocked");
      },
    });
    expect(() => rememberNext("/join/x")).not.toThrow();
    expect(afterSignIn()).toBe(HOME);
  });
});
