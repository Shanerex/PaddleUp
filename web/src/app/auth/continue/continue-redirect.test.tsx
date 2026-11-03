// @vitest-environment jsdom
import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

import { NEXT_KEY, rememberNext } from "../../../auth/redirect";
import { ContinueRedirect } from "./continue-redirect";

beforeEach(() => localStorage.clear());
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ContinueRedirect", () => {
  it("sends the visitor to the page they were heading for, once", () => {
    rememberNext("/join/ABCDEFGHJKMN");

    render(<ContinueRedirect />);

    expect(replace).toHaveBeenCalledWith("/join/ABCDEFGHJKMN");
    expect(localStorage.getItem(NEXT_KEY)).toBeNull();
  });

  it("goes home when there is nothing to return to", () => {
    render(<ContinueRedirect />);

    expect(replace).toHaveBeenCalledWith("/");
  });

  it("ignores an unsafe address that was planted in storage", () => {
    localStorage.setItem(NEXT_KEY, JSON.stringify({ next: "https://evil.test/x", at: Date.now() }));

    render(<ContinueRedirect />);

    expect(replace).toHaveBeenCalledWith("/");
  });
});
