// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const push = vi.fn();
const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, replace }) }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("../../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../api/client")>()),
  previewJoin: vi.fn(),
  joinRoom: vi.fn(),
}));

import { ApiError, joinRoom, previewJoin } from "../../../api/client";
import { JoinForm } from "./join-form";

const preview = {
  roomName: "Friends",
  takenColours: ["crimson", "sky"],
  full: false,
  alreadyJoined: false,
  packName: "Pack One",
  competition: "Spring Cup",
  franchiseCount: 2,
  maxManagers: 8,
};

beforeEach(() => {
  vi.mocked(previewJoin).mockResolvedValue(preview);
  vi.mocked(joinRoom).mockResolvedValue({
    roomId: "room-1",
    franchise: { id: "f1", ownerUserId: "u1", name: "Ann XI", colour: "tangerine", monogram: "AX" },
  });
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
});

const joinButton = () => screen.getByRole("button", { name: /^Join as/ });

describe("JoinForm (Home `join`)", () => {
  it("shows the room, the code and its seats from the preview", async () => {
    render(<JoinForm code="abcd-efgh-jkmn" />);

    expect(await screen.findByRole("heading", { name: "Join Friends" })).toBeTruthy();
    expect(screen.getByText("INVITE · ABCD-EFGH-JKMN")).toBeTruthy();
    expect(screen.getByText("Pack One · Spring Cup · 2 of 8 managers")).toBeTruthy();
    expect(previewJoin).toHaveBeenCalledWith("ABCDEFGHJKMN");
  });

  it("creates the franchise with a valid code and goes to the room", async () => {
    const user = userEvent.setup();
    render(<JoinForm code="ABCDEFGHJKMN" />);

    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "Ann XI");
    expect((screen.getByLabelText(/Monogram/) as HTMLInputElement).value).toBe("AX");
    await user.click(screen.getByRole("button", { name: "Join as Ann XI" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/rooms/room-1"));
    expect(joinRoom).toHaveBeenCalledWith("ABCDEFGHJKMN", {
      name: "Ann XI",
      colour: "tangerine",
      monogram: "AX",
    });
  });

  it("checks the name length before asking the server", async () => {
    const user = userEvent.setup();
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "A");

    await user.click(joinButton());

    expect(await screen.findByText("Use 2–30 characters.")).toBeTruthy();
    expect(joinRoom).not.toHaveBeenCalled();
  });

  it("disables colours already taken and starts on a free one", async () => {
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");

    expect((screen.getByRole("button", { name: "Crimson (taken)" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Sky (taken)" }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByRole("button", { name: "Tangerine" }).getAttribute("aria-pressed")).toBe("true");
  });

  it("lets the visitor pick another colour and keeps a monogram they typed", async () => {
    const user = userEvent.setup();
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");

    await user.click(screen.getByRole("button", { name: "Rose" }));
    await user.type(screen.getByLabelText(/Monogram/), "Q");
    await user.type(screen.getByLabelText("Franchise name"), "Zed Team");
    await user.click(joinButton());

    await waitFor(() => expect(joinRoom).toHaveBeenCalled());
    expect(vi.mocked(joinRoom).mock.calls[0][1]).toEqual({ name: "Zed Team", colour: "rose", monogram: "Q" });
  });

  it("explains a taken name on the field and lets the visitor try again", async () => {
    vi.mocked(joinRoom).mockRejectedValueOnce(
      new ApiError(409, { error: "duplicate", message: "Another franchise already has that name" }),
    );
    const user = userEvent.setup();
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "Ann XI");

    await user.click(joinButton());

    expect(await screen.findByText("Another franchise already has that name")).toBeTruthy();
    expect(screen.getByLabelText("Franchise name").getAttribute("aria-invalid")).toBe("true");
    expect((joinButton() as HTMLButtonElement).disabled).toBe(false);
  });

  it("goes straight to the room when the visitor already has a franchise there", async () => {
    vi.mocked(previewJoin).mockResolvedValue({ ...preview, alreadyJoined: true, roomId: "room-9" });
    render(<JoinForm code="ABC" />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/rooms/room-9"));
  });
});

describe("JoinForm errors (Home `joinError`)", () => {
  it("shows the invalid-code screen with no form for a revoked or expired code", async () => {
    vi.mocked(previewJoin).mockRejectedValue(new ApiError(404, { error: "invalid_invite" }));
    render(<JoinForm code="OLDCODE" />);

    expect(await screen.findByRole("heading", { name: "This code doesn't work" })).toBeTruthy();
    expect(screen.queryByLabelText("Franchise name")).toBeNull();
    expect(screen.getByRole("link", { name: "Try another code" }).getAttribute("href")).toBe("/join");
    expect(joinRoom).not.toHaveBeenCalled();
  });

  it("switches to the invalid-code screen when the code is revoked between the preview and the join", async () => {
    vi.mocked(joinRoom).mockRejectedValue(new ApiError(404, { error: "invalid_invite" }));
    const user = userEvent.setup();
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "Ann XI");

    await user.click(joinButton());

    expect(await screen.findByRole("heading", { name: "This code doesn't work" })).toBeTruthy();
    expect(push).not.toHaveBeenCalled();
  });

  it("does not offer joining a full room", async () => {
    vi.mocked(previewJoin).mockResolvedValue({ ...preview, full: true });
    render(<JoinForm code="ABC" />);

    expect(await screen.findByRole("heading", { name: "Friends is full" })).toBeTruthy();
    expect(screen.queryByLabelText("Franchise name")).toBeNull();
  });

  it("explains that joining closed once the auction went live", async () => {
    vi.mocked(joinRoom).mockRejectedValue(new ApiError(409, { error: "room_locked" }));
    const user = userEvent.setup();
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "Ann XI");

    await user.click(joinButton());

    expect(await screen.findByRole("heading", { name: "The auction has started" })).toBeTruthy();
  });

  it("counts down Retry-After before letting the visitor try again", async () => {
    vi.mocked(previewJoin).mockRejectedValueOnce(new ApiError(429, null, 2));
    render(<JoinForm code="ABC" />);

    expect(await screen.findByRole("heading", { name: "Too many tries" })).toBeTruthy();
    const retry = screen.getByRole("button", { name: /Try again in 0:02/ }) as HTMLButtonElement;
    expect(retry.disabled).toBe(true);
    await waitFor(() => expect(screen.getByRole("button", { name: "Try again" })), { timeout: 3500 });
  });
});
