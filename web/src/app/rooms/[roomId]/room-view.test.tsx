// @vitest-environment jsdom
import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../api/client")>()),
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  getTransitions: vi.fn(),
  getInvite: vi.fn(),
  advanceRoom: vi.fn(),
  issueInvite: vi.fn(),
  revokeInvite: vi.fn(),
  removeFranchise: vi.fn(),
  handOver: vi.fn(),
}));

import {
  advanceRoom,
  ApiError,
  getInvite,
  getMembers,
  getRoom,
  getTransitions,
  handOver,
  issueInvite,
  removeFranchise,
  revokeInvite,
  type Member,
  type Room,
  type RoomState,
} from "../../../api/client";
import { RoomView } from "./room-view";

function room(state: RoomState, overrides: Record<string, unknown> = {}): Room {
  return {
    id: "room-1",
    state,
    myRole: "commissioner",
    commissioner: { id: "u-ann", displayName: "Ann" },
    configVersion: 1,
    franchiseCount: 2,
    myFranchiseId: null,
    advanceTo: state === "setup" ? ["retention", "auction_scheduled"] : [],
    createdAt: "2031-01-01T00:00:00Z",
    packLabels: { packId: "p", packVersion: "1", roles: [], tags: [], restrictedCategories: [], stats: [], terminology: {} },
    config: { name: "Friends", competition: "Season 1", minManagers: 2, maxManagers: 10 },
    ...overrides,
  } as unknown as Room;
}

const members = [
  { userId: "u-ann", displayName: "Ann", avatar: "preset-01", role: "commissioner", franchise: null },
  {
    userId: "u-ben",
    displayName: "Ben",
    avatar: "preset-02",
    role: "manager",
    franchise: { id: "f-ben", ownerUserId: "u-ben", name: "Ben XI", colour: "sky", monogram: "BX" },
  },
  {
    userId: "u-cat",
    displayName: "Cat",
    avatar: "preset-03",
    role: "manager",
    franchise: { id: "f-cat", ownerUserId: "u-cat", name: "Cat XI", colour: "teal", monogram: "CX" },
  },
] as unknown as Member[];

beforeEach(() => {
  vi.mocked(getRoom).mockResolvedValue(room("setup"));
  vi.mocked(getMembers).mockResolvedValue(members);
  vi.mocked(getTransitions).mockResolvedValue([]);
  vi.mocked(getInvite).mockResolvedValue({
    code: "ABCDEFGHJKMN",
    link: "http://localhost:3000/join/ABCDEFGHJKMN",
    expiresAt: "2031-01-08T00:00:00Z",
  });
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RoomView as a manager", () => {
  beforeEach(() => {
    vi.mocked(getRoom).mockResolvedValue(
      room("auction_scheduled", { myRole: "manager", myFranchiseId: "f-ben", advanceTo: [] }),
    );
  });

  it("shows the state and the members but no Commissioner controls", async () => {
    render(<RoomView roomId="room-1" />);

    expect((await screen.findByTestId("room-state")).textContent).toBe("Auction scheduled");
    expect(screen.getByText("Ben XI")).toBeTruthy();
    expect(screen.getByText(/Commissioner: Ann/)).toBeTruthy();
    expect(screen.queryByLabelText("Commissioner controls")).toBeNull();
    expect(screen.queryByRole("button", { name: /remove/i })).toBeNull();
    expect(getInvite).not.toHaveBeenCalled();
  });

  it("shows the new state after a transition when the tab is focused again", async () => {
    render(<RoomView roomId="room-1" />);
    expect((await screen.findByTestId("room-state")).textContent).toBe("Auction scheduled");

    vi.mocked(getRoom).mockResolvedValue(
      room("auction_live", { myRole: "manager", myFranchiseId: "f-ben", advanceTo: [] }),
    );
    vi.mocked(getTransitions).mockResolvedValue([
      {
        from: "auction_scheduled",
        to: "auction_live",
        actor: { id: "u-ann", displayName: "Ann" },
        occurredAt: "2031-01-02T10:00:00Z",
      },
    ]);
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });

    await waitFor(() => expect(screen.getByTestId("room-state").textContent).toBe("Auction live"));
    expect(screen.getByText(/Auction scheduled to Auction live by Ann/)).toBeTruthy();
  });

  it("tells a removed manager the room is gone on the next refresh", async () => {
    render(<RoomView roomId="room-1" />);
    await screen.findByTestId("room-state");

    vi.mocked(getRoom).mockRejectedValue(new ApiError(404, { error: "room_not_found" }));
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });

    expect(await screen.findByText(/could not find that room/i)).toBeTruthy();
    expect(screen.queryByTestId("room-state")).toBeNull();
  });
});

describe("RoomView as the Commissioner", () => {
  it("offers the next states and reloads after advancing", async () => {
    vi.mocked(advanceRoom).mockResolvedValue({
      from: "setup",
      to: "retention",
      occurredAt: "2031-01-02T10:00:00Z",
    });
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await screen.findByLabelText("Commissioner controls");
    expect(screen.getByRole("button", { name: "Move to Auction scheduled" })).toBeTruthy();
    vi.mocked(getRoom).mockResolvedValue(room("retention", { advanceTo: ["auction_scheduled"] }));

    await user.click(screen.getByRole("button", { name: "Move to Retention" }));

    expect(advanceRoom).toHaveBeenCalledWith("room-1", "retention");
    await waitFor(() => expect(screen.getByTestId("room-state").textContent).toBe("Retention"));
  });

  it("shows the invite code, link and expiry, and can replace or revoke it", async () => {
    vi.mocked(issueInvite).mockResolvedValue({ code: "NEWCODE12345", link: "l", expiresAt: "t" });
    vi.mocked(revokeInvite).mockResolvedValue();
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);

    expect((await screen.findByTestId("room-invite-code")).textContent).toBe("ABCDEFGHJKMN");
    expect(screen.getByText("http://localhost:3000/join/ABCDEFGHJKMN")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "New code" }));
    expect(issueInvite).toHaveBeenCalledWith("room-1");

    vi.mocked(getInvite).mockResolvedValue(null);
    await user.click(screen.getByRole("button", { name: "Revoke" }));
    expect(revokeInvite).toHaveBeenCalledWith("room-1");
    expect(await screen.findByText("There is no active invite.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Create invite" })).toBeTruthy();
  });

  it("removes a manager only after a confirmation", async () => {
    vi.mocked(removeFranchise).mockResolvedValue([members[0], members[2]]);
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await screen.findByText("Ben XI");

    await user.click(screen.getByRole("button", { name: "Remove Ben XI" }));
    expect(removeFranchise).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Confirm remove" }));

    expect(removeFranchise).toHaveBeenCalledWith("room-1", "f-ben");
  });

  it("hands over the role after a confirmation", async () => {
    vi.mocked(handOver).mockResolvedValue({ commissioner: { id: "u-cat", displayName: "Cat" }, stillMember: false });
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await screen.findByText("Cat XI");

    await user.click(screen.getByRole("button", { name: "Make Cat XI Commissioner" }));
    await user.click(screen.getByRole("button", { name: "Confirm handover" }));

    expect(handOver).toHaveBeenCalledWith("room-1", "f-cat");
  });

  it("shows an admin-only Commissioner by name and offers no remove for them", async () => {
    render(<RoomView roomId="room-1" />);
    const list = await screen.findByRole("list");
    const first = within(list).getAllByRole("listitem")[0];

    expect(first.textContent).toContain("Ann");
    expect(first.textContent).toContain("admin only");
    expect(within(first).queryByRole("button")).toBeNull();
  });

  it("tells a stale tab that removing is locked once the auction is live, and reloads", async () => {
    vi.mocked(removeFranchise).mockRejectedValue(new ApiError(409, { error: "room_locked" }));
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await screen.findByText("Ben XI");
    vi.mocked(getRoom).mockResolvedValue(room("auction_live"));

    await user.click(screen.getByRole("button", { name: "Remove Ben XI" }));
    await user.click(screen.getByRole("button", { name: "Confirm remove" }));

    expect(await screen.findByText(/The auction is live, so this can no longer be changed/)).toBeTruthy();
    await waitFor(() => expect(screen.getByTestId("room-state").textContent).toBe("Auction live"));
    expect(screen.queryByRole("button", { name: /^Remove/ })).toBeNull();
    expect(removeFranchise).toHaveBeenCalledTimes(1);
  });

  it("answers a stale tab clearly: the auction went live, so it says so and reloads", async () => {
    vi.mocked(advanceRoom).mockRejectedValue(new ApiError(409, { error: "illegal_state", message: "A room cannot move from auction_live to retention" }));
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await screen.findByLabelText("Commissioner controls");
    vi.mocked(getRoom).mockResolvedValue(room("auction_live"));

    await user.click(screen.getByRole("button", { name: "Move to Retention" }));

    expect(await screen.findByText(/A room cannot move from auction_live to retention/)).toBeTruthy();
    await waitFor(() => expect(screen.getByTestId("room-state").textContent).toBe("Auction live"));
    expect(screen.queryByRole("button", { name: "Move to Retention" })).toBeNull();
    expect(screen.queryByRole("button", { name: /Remove/ })).toBeNull();
  });
});
