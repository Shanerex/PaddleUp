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
});

describe("JoinForm", () => {
  it("creates the franchise with a valid code and goes to the room", async () => {
    const user = userEvent.setup();
    render(<JoinForm code="ABCDEFGHJKMN" />);

    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "Ann XI");
    expect((screen.getByLabelText(/Monogram/) as HTMLInputElement).value).toBe("AX");
    await user.click(screen.getByRole("button", { name: "Join room" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/rooms/room-1"));
    expect(previewJoin).toHaveBeenCalledWith("ABCDEFGHJKMN");
    expect(joinRoom).toHaveBeenCalledWith("ABCDEFGHJKMN", {
      name: "Ann XI",
      colour: "tangerine",
      monogram: "AX",
    });
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
    await user.click(screen.getByRole("button", { name: "Join room" }));

    await waitFor(() => expect(joinRoom).toHaveBeenCalled());
    expect(vi.mocked(joinRoom).mock.calls[0][1]).toEqual({ name: "Zed Team", colour: "rose", monogram: "Q" });
  });

  it("shows an error and no form for a revoked or expired code", async () => {
    vi.mocked(previewJoin).mockRejectedValue(new ApiError(404, { error: "invalid_invite" }));
    render(<JoinForm code="OLDCODE" />);

    expect(await screen.findByText(/not valid any more/i)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Join room" })).toBeNull();
    expect(joinRoom).not.toHaveBeenCalled();
  });

  it("shows the error when the code is revoked between the preview and the join", async () => {
    vi.mocked(joinRoom).mockRejectedValue(new ApiError(404, { error: "invalid_invite" }));
    const user = userEvent.setup();
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "Ann XI");

    await user.click(screen.getByRole("button", { name: "Join room" }));

    expect(await screen.findByText(/not valid any more/i)).toBeTruthy();
    expect(push).not.toHaveBeenCalled();
  });

  it("explains a taken name and lets the visitor try again", async () => {
    vi.mocked(joinRoom).mockRejectedValueOnce(
      new ApiError(409, { error: "duplicate", message: "Another franchise already has that name" }),
    );
    const user = userEvent.setup();
    render(<JoinForm code="ABC" />);
    await screen.findByText("Join Friends");
    await user.type(screen.getByLabelText("Franchise name"), "Ann XI");

    await user.click(screen.getByRole("button", { name: "Join room" }));

    expect(await screen.findByText("Another franchise already has that name")).toBeTruthy();
    expect((screen.getByRole("button", { name: "Join room" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("goes straight to the room when the visitor already has a franchise there", async () => {
    vi.mocked(previewJoin).mockResolvedValue({ ...preview, alreadyJoined: true, roomId: "room-9" });
    render(<JoinForm code="ABC" />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/rooms/room-9"));
  });

  it("does not offer joining a full room", async () => {
    vi.mocked(previewJoin).mockResolvedValue({ ...preview, full: true });
    render(<JoinForm code="ABC" />);

    expect(await screen.findByText("This room is full.")).toBeTruthy();
    expect((screen.getByRole("button", { name: "Join room" }) as HTMLButtonElement).disabled).toBe(true);
  });
});
