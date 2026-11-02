// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const push = vi.fn();
const router = { push };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

vi.mock("../../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../api/client")>()),
  verifyLink: vi.fn(),
}));

import { ApiError, verifyLink } from "../../../api/client";
import { VerifyLink } from "./verify-link";

beforeEach(() => {
  vi.mocked(verifyLink).mockResolvedValue({
    id: "1",
    email: "ann@example.test",
    displayName: "ann",
    avatar: "preset-01",
  });
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("VerifyLink", () => {
  it("does not spend the token until the visitor confirms", () => {
    render(<VerifyLink token="tok" />);

    expect(verifyLink).not.toHaveBeenCalled();
  });

  it("posts the token on confirm and goes to the profile", async () => {
    const user = userEvent.setup();
    render(<VerifyLink token="tok" />);

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    await waitFor(() => expect(verifyLink).toHaveBeenCalledWith("tok"));
    expect(push).toHaveBeenCalledWith("/profile");
  });

  it("explains a link that no longer works and offers sign-in", async () => {
    vi.mocked(verifyLink).mockRejectedValue(new ApiError(401, { error: "invalid_login" }));
    const user = userEvent.setup();
    render(<VerifyLink token="old" />);

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(await screen.findByText(/expired or was already used/i)).toBeTruthy();
    expect(screen.getByRole("link", { name: /sign in/i }).getAttribute("href")).toBe("/sign-in");
    expect(push).not.toHaveBeenCalled();
  });

  it("handles a missing token", () => {
    render(<VerifyLink />);

    expect(screen.getByText(/token is missing/i)).toBeTruthy();
    expect(screen.queryByRole("button", { name: /confirm/i })).toBeNull();
  });
});
