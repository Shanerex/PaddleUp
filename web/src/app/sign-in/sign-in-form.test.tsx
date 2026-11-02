// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const push = vi.fn();
const router = { push };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

vi.mock("../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../api/client")>()),
  startEmail: vi.fn(),
  verifyCode: vi.fn(),
}));

import { ApiError, startEmail, verifyCode } from "../../api/client";
import { SignInForm } from "./sign-in-form";

const profile = { id: "1", email: "ann@example.test", displayName: "ann", avatar: "preset-01" };

beforeEach(() => {
  vi.mocked(startEmail).mockResolvedValue();
  vi.mocked(verifyCode).mockResolvedValue(profile);
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SignInForm", () => {
  it("offers a Google button pointing at the server's oauth endpoint", () => {
    render(<SignInForm />);

    const google = screen.getByRole("link", { name: /continue with google/i });
    expect(google.getAttribute("href")).toBe("/api/oauth2/authorization/google");
  });

  it("requests a link and code, then signs in with the code", async () => {
    const user = userEvent.setup();
    render(<SignInForm />);

    await user.type(screen.getByLabelText(/email/i), "ann@example.test");
    await user.click(screen.getByRole("button", { name: /email me/i }));

    expect(startEmail).toHaveBeenCalledWith("ann@example.test");
    await user.type(await screen.findByLabelText(/code/i), "123456");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(verifyCode).toHaveBeenCalledWith("ann@example.test", "123456"));
    expect(push).toHaveBeenCalledWith("/profile");
  });

  it("shows an error and stays on the page when the code is wrong", async () => {
    vi.mocked(verifyCode).mockRejectedValue(new ApiError(401, { error: "invalid_login" }));
    const user = userEvent.setup();
    render(<SignInForm />);
    await user.type(screen.getByLabelText(/email/i), "ann@example.test");
    await user.click(screen.getByRole("button", { name: /email me/i }));

    await user.type(await screen.findByLabelText(/code/i), "000000");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText(/did not work/i)).toBeTruthy();
    expect(push).not.toHaveBeenCalled();
  });

  it("lets the visitor go back and use a different email", async () => {
    const user = userEvent.setup();
    render(<SignInForm />);
    await user.type(screen.getByLabelText(/email/i), "ann@example.test");
    await user.click(screen.getByRole("button", { name: /email me/i }));

    await user.click(await screen.findByRole("button", { name: /different email/i }));

    expect(screen.getByLabelText(/email/i)).toBeTruthy();
  });

  it("explains an unverified Google email", () => {
    render(<SignInForm error="unverified_email" />);

    expect(screen.getByText(/not verified/i)).toBeTruthy();
  });

  it("shows a generic message when Google sign-in fails", () => {
    render(<SignInForm error="google" />);

    expect(screen.getByText(/google sign-in did not complete/i)).toBeTruthy();
  });

  it("reports a failure to send the email", async () => {
    vi.mocked(startEmail).mockRejectedValue(new ApiError(503, { error: "email_unavailable" }));
    const user = userEvent.setup();
    render(<SignInForm />);

    await user.type(screen.getByLabelText(/email/i), "ann@example.test");
    await user.click(screen.getByRole("button", { name: /email me/i }));

    expect(await screen.findByText(/could not send/i)).toBeTruthy();
  });
});
