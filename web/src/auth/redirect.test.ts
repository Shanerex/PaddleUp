import { describe, expect, it } from "vitest";
import { HOME, SESSION_COOKIE, signInRedirect } from "./redirect";

describe("signInRedirect", () => {
  it("sends a visitor with no session cookie to sign-in", () => {
    expect(signInRedirect("/", "absent")).toBe("/sign-in");
    expect(signInRedirect("/profile", "absent")).toBe("/sign-in");
    expect(signInRedirect("/rooms/abc", "absent")).toBe("/sign-in");
  });

  it("sends a visitor whose cookie the server rejects to sign-in", () => {
    expect(signInRedirect("/", "invalid")).toBe("/sign-in");
    expect(signInRedirect("/profile", "invalid")).toBe("/sign-in");
  });

  it("lets a signed-in visitor through, in any tab", () => {
    expect(signInRedirect("/", "valid")).toBeNull();
    expect(signInRedirect("/profile", "valid")).toBeNull();
    expect(signInRedirect("/rooms/abc", "valid")).toBeNull();
  });

  it("sends a signed-in visitor away from the sign-in page", () => {
    expect(signInRedirect("/sign-in", "valid")).toBe(HOME);
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
