// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const push = vi.fn();
const router = { push };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

vi.mock("../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../api/client")>()),
  getMe: vi.fn(),
  getAvatars: vi.fn(),
  updateProfile: vi.fn(),
  signOut: vi.fn(),
}));

import { ApiError, getAvatars, getMe, signOut, updateProfile } from "../../api/client";
import { ProfileView } from "./profile-view";

const profile = {
  id: "1",
  email: "ann@example.test",
  displayName: "Ann",
  avatar: "preset-01",
  googlePictureUrl: null,
};

beforeEach(() => {
  vi.mocked(getMe).mockResolvedValue(profile);
  vi.mocked(getAvatars).mockResolvedValue({ presets: ["preset-01", "preset-02", "preset-03"] });
  vi.mocked(signOut).mockResolvedValue();
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ProfileView", () => {
  it("sends a signed-out visitor to sign-in", async () => {
    vi.mocked(getMe).mockResolvedValue(null);
    render(<ProfileView />);

    await waitFor(() => expect(push).toHaveBeenCalledWith("/sign-in"));
  });

  it("shows the current name and avatar choices from the server", async () => {
    render(<ProfileView />);

    const name = await screen.findByLabelText<HTMLInputElement>(/display name/i);
    expect(name.value).toBe("Ann");
    const options = screen.getAllByRole("button", { name: /^avatar \d/i });
    expect(options).toHaveLength(3);
    expect(options[0].getAttribute("aria-pressed")).toBe("true");
    expect(options[0].querySelector("img")?.getAttribute("src")).toBe("/avatars/preset-01.svg");
  });

  it("is built to the mock: page title, back link, name counter, sign-out card apart", async () => {
    vi.mocked(getMe).mockResolvedValue({ ...profile, googlePictureUrl: "https://example.test/ann.png" });
    render(<ProfileView />);

    expect(await screen.findByRole("heading", { name: "Your profile", level: 1 })).toBeTruthy();
    expect(screen.getByRole("link", { name: /Rooms/ }).getAttribute("href")).toBe("/");
    expect(screen.getByText("3 / 30")).toBeTruthy();
    expect(screen.getByText("The last one is your Google picture.")).toBeTruthy();
    expect(screen.getByRole("region", { name: "Sign out" })).toBeTruthy();
  });

  it("edits the name and avatar, saves, and shows the updated values", async () => {
    vi.mocked(updateProfile).mockResolvedValue({
      ...profile,
      displayName: "Ann Lee",
      avatar: "preset-03",
    });
    const user = userEvent.setup();
    render(<ProfileView />);

    const name = await screen.findByLabelText<HTMLInputElement>(/display name/i);
    await user.clear(name);
    await user.type(name, "Ann Lee");
    await user.click(screen.getByRole("button", { name: "Avatar 3" }));
    await user.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() =>
      expect(updateProfile).toHaveBeenCalledWith({ displayName: "Ann Lee", avatar: "preset-03" }),
    );
    expect((await screen.findByRole("status")).textContent).toMatch(/saved/i);
    expect(screen.getByRole("heading", { name: "Ann Lee" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Avatar 3" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("img", { name: /your avatar/i }).getAttribute("src")).toBe(
      "/avatars/preset-03.svg",
    );
  });

  it("offers the Google picture when the account has one", async () => {
    vi.mocked(getMe).mockResolvedValue({
      ...profile,
      googlePictureUrl: "https://example.test/ann.png",
    });
    render(<ProfileView />);

    const google = await screen.findByRole("button", { name: /google picture/i });
    expect(google.querySelector("img")?.getAttribute("src")).toBe("https://example.test/ann.png");
  });

  it("shows the server's rejection and keeps the form", async () => {
    vi.mocked(updateProfile).mockRejectedValue(new ApiError(400, { error: "invalid_request" }));
    const user = userEvent.setup();
    render(<ProfileView />);

    const name = await screen.findByLabelText<HTMLInputElement>(/display name/i);
    await user.clear(name);
    await user.type(name, "x");
    await user.click(screen.getByRole("button", { name: /save/i }));

    expect(await screen.findByText(/2 to 30/i)).toBeTruthy();
    expect(name.value).toBe("x");
  });

  it("signs out and returns to sign-in", async () => {
    const user = userEvent.setup();
    render(<ProfileView />);

    await user.click(await screen.findByRole("button", { name: /sign out/i }));

    await waitFor(() => expect(signOut).toHaveBeenCalled());
    expect(push).toHaveBeenCalledWith("/sign-in");
  });
});
