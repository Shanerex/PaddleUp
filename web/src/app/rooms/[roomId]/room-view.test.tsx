// @vitest-environment jsdom
import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("../../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../api/client")>()),
  getMe: vi.fn().mockResolvedValue(null),
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  getTransitions: vi.fn(),
  getInvite: vi.fn(),
  getPool: vi.fn(),
  advanceRoom: vi.fn(),
  issueInvite: vi.fn(),
  revokeInvite: vi.fn(),
  removeFranchise: vi.fn(),
  handOver: vi.fn(),
  createOwnFranchise: vi.fn(),
  editFranchise: vi.fn(),
}));

import {
  advanceRoom,
  ApiError,
  createOwnFranchise,
  editFranchise,
  getInvite,
  getMembers,
  getPool,
  getRoom,
  getTransitions,
  handOver,
  issueInvite,
  removeFranchise,
  revokeInvite,
  type Member,
  type Pool,
  type Room,
  type RoomState,
} from "../../../api/client";
import { RoomView } from "./room-view";

const config = {
  name: "Friends",
  competition: "Season 1",
  minManagers: 2,
  maxManagers: 10,
  purse: 10000,
  currencyCode: "EUR",
  displayUnit: { label: "M", divisor: 100 },
  incrementLadder: [{ from: 0, step: 5 }],
  squadMin: 11,
  squadMax: 18,
  roleMinimums: { a: 1, b: 2 },
  categoryQuotas: { c: 3 },
  retention: {},
  scoringRules: { rules: [] },
  ai: { enabled: true, lifelines: { "fit-check": 1 }, reportCardVisibility: "room" },
};

function room(state: RoomState, overrides: Record<string, unknown> = {}): Room {
  return {
    id: "room-1",
    state,
    myRole: "commissioner",
    commissioner: { id: "u-ann", displayName: "Ann" },
    configVersion: 1,
    franchiseCount: 2,
    myFranchiseId: null,
    advanceTo: state === "setup" ? ["retention", "auction_scheduled"] : state === "retention" ? ["auction_scheduled"] : [],
    createdAt: "2031-01-01T00:00:00Z",
    packLabels: {
      packId: "p",
      packVersion: "1",
      roles: [],
      tags: [],
      restrictedCategories: [{ id: "c", label: "Limited" }],
      stats: [],
      terminology: {},
    },
    config,
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

function poolOf(over: Partial<Pool> = {}): Pool {
  return {
    hasPool: true,
    version: 2,
    datasetVersion: 3,
    latestDatasetVersion: 3,
    editCount: 0,
    canEdit: true,
    players: [{ id: "a" }, { id: "b" }, { id: "c" }],
    sets: [{ name: "One", players: 2 }, { name: "Two", players: 1 }],
    ...over,
  } as unknown as Pool;
}

beforeEach(() => {
  vi.mocked(getPool).mockResolvedValue(poolOf());
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

const membersList = () => within(screen.getByRole("region", { name: "Members" }));

describe("RoomView as a manager (Room `setupManager`)", () => {
  beforeEach(() => {
    vi.mocked(getRoom).mockResolvedValue(room("auction_scheduled", { myRole: "manager", myFranchiseId: "f-ben", advanceTo: [] }));
  });

  it("shows the state, the house rules and the members, but no Commissioner controls", async () => {
    render(<RoomView roomId="room-1" />);

    expect((await screen.findByTestId("room-state")).textContent).toBe("AUCTION NEXT");
    expect(screen.getByRole("heading", { name: "Friends" })).toBeTruthy();
    expect(screen.getByText("The auction is next")).toBeTruthy();
    expect(screen.getByText("€100 M")).toBeTruthy();
    expect(screen.getByText("LIMITED")).toBeTruthy();
    expect(membersList().getByText("Ben XI")).toBeTruthy();
    expect(screen.queryByRole("region", { name: "Invite code" })).toBeNull();
    expect(screen.queryByRole("button", { name: /Manage/ })).toBeNull();
    expect(getInvite).not.toHaveBeenCalled();
  });

  it("paints the room in the manager's own franchise colour", async () => {
    const { container } = render(<RoomView roomId="room-1" />);
    await screen.findByTestId("room-state");

    expect((container.firstChild as HTMLElement).style.getPropertyValue("--h")).toBe("225");
  });

  it("shows the new state after a transition when the tab becomes visible again", async () => {
    render(<RoomView roomId="room-1" />);
    await screen.findByText("AUCTION NEXT");
    vi.mocked(getRoom).mockResolvedValue(room("auction_live", { myRole: "manager", myFranchiseId: "f-ben", advanceTo: [] }));
    vi.mocked(getTransitions).mockResolvedValue([
      { from: "auction_scheduled", to: "auction_live", actor: { id: "u-ann", displayName: "Ann" }, occurredAt: "2031-01-02T10:00:00Z" },
    ]);

    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    expect(await screen.findByText("AUCTION LIVE")).toBeTruthy();
    expect(screen.getByText("Ann started the auction")).toBeTruthy();
  });

  it("tells a removed manager the room is gone on the next refresh", async () => {
    render(<RoomView roomId="room-1" />);
    await screen.findByText("AUCTION NEXT");
    vi.mocked(getRoom).mockRejectedValue(new ApiError(404, { error: "not_found" }));

    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    expect(await screen.findByRole("heading", { name: "Can't find that room" })).toBeTruthy();
  });

  it("edits the franchise in a sheet", async () => {
    vi.mocked(editFranchise).mockResolvedValue(members[1].franchise!);
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await screen.findByText("AUCTION NEXT");

    await user.click(screen.getByRole("button", { name: "Edit your franchise" }));
    const sheet = within(screen.getByRole("dialog", { name: "Edit your franchise" }));
    await user.clear(sheet.getByLabelText("Franchise name"));
    await user.type(sheet.getByLabelText("Franchise name"), "Ben Eleven");
    await user.click(sheet.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(editFranchise).toHaveBeenCalledWith("room-1", "f-ben", { name: "Ben Eleven", monogram: "BX", colour: "sky" }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("answers a stale tab clearly: the auction went live, so it says so and reloads", async () => {
    vi.mocked(editFranchise).mockRejectedValue(new ApiError(409, { error: "room_locked" }));
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await screen.findByText("AUCTION NEXT");
    vi.mocked(getRoom).mockResolvedValue(room("auction_live", { myRole: "manager", myFranchiseId: "f-ben", advanceTo: [] }));

    await user.click(screen.getByRole("button", { name: "Edit your franchise" }));
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Too late for that.")).toBeTruthy();
    expect(await screen.findByText("AUCTION LIVE")).toBeTruthy();
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

describe("RoomView as the Commissioner (Room `setup`, `setupReady`, `setupAdmin`)", () => {
  it("counts managers against the minimum and offers the next states after a confirmation", async () => {
    vi.mocked(advanceRoom).mockResolvedValue({ from: "setup", to: "retention", occurredAt: "2031-01-02T00:00:00Z" });
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);

    expect(await screen.findByText("That's enough to start. Room for 8 more.")).toBeTruthy();
    vi.mocked(getRoom).mockResolvedValue(room("retention"));
    await user.click(screen.getByRole("button", { name: "Open retention" }));
    const sheet = within(screen.getByRole("dialog", { name: "Open retention?" }));
    await user.click(sheet.getByRole("button", { name: "Open retention" }));

    await waitFor(() => expect(advanceRoom).toHaveBeenCalledWith("room-1", "retention"));
    await waitFor(() => expect(screen.getByTestId("room-state").textContent).toBe("RETENTION"));
  });

  it("disables moving on until enough franchises have joined", async () => {
    vi.mocked(getRoom).mockResolvedValue(room("setup", { franchiseCount: 1, config: { ...config, minManagers: 3 } }));
    render(<RoomView roomId="room-1" />);

    expect(await screen.findByText("2 more to go. Share the invite code.")).toBeTruthy();
    expect((screen.getByRole("button", { name: "Open retention" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Schedule auction" }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("Needs 3 managers to move on. Only franchises count.")).toBeTruthy();
  });

  it("shows the invite code and expiry, and can replace or revoke it", async () => {
    vi.mocked(issueInvite).mockResolvedValue({ code: "NEWCODE12345", link: "l", expiresAt: "2031-01-09T00:00:00Z" });
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);

    expect((await screen.findByTestId("room-invite-code")).textContent).toBe("ABCD-EFGH-JKMN");
    expect(screen.getByText(/Works until/)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "New code" }));
    await waitFor(() => expect(issueInvite).toHaveBeenCalledWith("room-1"));

    await user.click(screen.getByRole("button", { name: "Revoke" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(revokeInvite).toHaveBeenCalledWith("room-1"));
  });

  it("keeps the page when the invite cannot be read", async () => {
    vi.mocked(getInvite).mockRejectedValue(new ApiError(403, null));
    render(<RoomView roomId="room-1" />);

    expect(await screen.findByText("There is no active invite. Make one to bring friends in.")).toBeTruthy();
    expect(membersList().getByText("Ben XI")).toBeTruthy();
  });

  it("removes a manager only after a confirmation", async () => {
    vi.mocked(removeFranchise).mockResolvedValue(members.filter((m) => m.userId !== "u-ben"));
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);

    await user.click(await screen.findByRole("button", { name: "Manage Ben XI" }));
    await user.click(screen.getByRole("button", { name: /Remove from room/ }));
    expect(removeFranchise).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole("dialog", { name: "Remove Ben XI?" })).getByRole("button", { name: "Remove" }));

    await waitFor(() => expect(removeFranchise).toHaveBeenCalledWith("room-1", "f-ben"));
  });

  it("hands over the role after a confirmation and warns an admin-only Commissioner they will leave", async () => {
    vi.mocked(handOver).mockResolvedValue({ commissioner: { id: "u-cat", displayName: "Cat" }, stillMember: false });
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);

    await user.click(await screen.findByRole("button", { name: "Manage Cat XI" }));
    await user.click(screen.getByRole("button", { name: /Make Commissioner/ }));
    const sheet = within(screen.getByRole("dialog", { name: "Make Cat the Commissioner?" }));
    expect(sheet.getByText(/you'll leave the room/)).toBeTruthy();
    await user.click(sheet.getByRole("button", { name: "Make Commissioner" }));

    await waitFor(() => expect(handOver).toHaveBeenCalledWith("room-1", "f-cat"));
  });

  it("shows an admin-only Commissioner with the brand colour and offers them a franchise", async () => {
    vi.mocked(createOwnFranchise).mockResolvedValue({ id: "f-ann", ownerUserId: "u-ann", name: "Ann XI", colour: "tangerine", monogram: "AX" });
    const user = userEvent.setup();
    const { container } = render(<RoomView roomId="room-1" />);

    expect(await screen.findByText("Commissioner · no franchise")).toBeTruthy();
    expect((container.firstChild as HTMLElement).style.getPropertyValue("--h")).toBe("38");
    expect(membersList().queryByRole("button", { name: "Manage Ann" })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Create my franchise" }));
    const sheet = within(screen.getByRole("dialog", { name: "Create your franchise" }));
    await user.type(sheet.getByLabelText("Franchise name"), "Ann XI");
    await user.click(sheet.getByRole("button", { name: "Create franchise" }));

    await waitFor(() =>
      expect(createOwnFranchise).toHaveBeenCalledWith("room-1", { name: "Ann XI", monogram: "AX", colour: "crimson" }),
    );
  });

  it("tells a stale tab that removing is locked once the auction is live, and reloads", async () => {
    vi.mocked(removeFranchise).mockRejectedValue(new ApiError(409, { error: "room_locked" }));
    const user = userEvent.setup();
    render(<RoomView roomId="room-1" />);
    await user.click(await screen.findByRole("button", { name: "Manage Ben XI" }));
    vi.mocked(getRoom).mockResolvedValue(room("auction_live"));

    await user.click(screen.getByRole("button", { name: /Remove from room/ }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remove" }));

    expect(await screen.findByText("Too late for that.")).toBeTruthy();
    expect(await screen.findByText("AUCTION LIVE")).toBeTruthy();
  });

  it("shows a retry when the room cannot be loaded at all", async () => {
    vi.mocked(getRoom).mockRejectedValue(new TypeError("offline"));
    render(<RoomView roomId="room-1" />);

    expect(await screen.findByText("Couldn't load the room.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Try again" })).toBeTruthy();
  });
});

describe("RoomView player pool card and rail links (Room setup*)", () => {
  it("points the Commissioner at the editable pool and the set order", async () => {
    render(<RoomView roomId="room-1" />);

    const card = await screen.findByRole("region", { name: "Player pool" });
    expect(within(card).getByText(/season data v3/)).toBeTruthy();
    expect(within(card).getByText("players", { exact: false })).toBeTruthy();
    expect(within(card).getByRole("link", { name: /Edit pool/ }).getAttribute("href")).toBe("/rooms/room-1/pool");
    expect(within(card).getByRole("link", { name: /Set order/ }).getAttribute("href")).toBe("/rooms/room-1/sets");
    const rail = screen.getByRole("navigation", { name: "Room" });
    expect(within(rail).getByRole("link", { name: "Player pool" }).getAttribute("href")).toBe("/rooms/room-1/pool");
    expect(within(rail).getByRole("link", { name: "Set order" }).getAttribute("href")).toBe("/rooms/room-1/sets");
  });

  it("offers a manager View pool rather than Edit pool", async () => {
    vi.mocked(getRoom).mockResolvedValue(room("setup", { myRole: "manager", myFranchiseId: "f-ben" }));
    vi.mocked(getPool).mockResolvedValue(poolOf({ canEdit: false }));
    render(<RoomView roomId="room-1" />);

    const card = await screen.findByRole("region", { name: "Player pool" });
    expect(within(card).getByRole("link", { name: /View pool/ }).getAttribute("href")).toBe("/rooms/room-1/pool");
    expect(within(card).queryByRole("link", { name: /Edit pool/ })).toBeNull();
  });

  it("says there is no pool yet, and a room whose pool cannot be read still loads", async () => {
    vi.mocked(getPool).mockResolvedValue(poolOf({ hasPool: false, datasetVersion: null, players: [], sets: [] }));
    const { unmount } = render(<RoomView roomId="room-1" />);
    expect(await screen.findByText(/No season data loaded yet/)).toBeTruthy();
    unmount();

    vi.mocked(getPool).mockRejectedValue(new Error("down"));
    render(<RoomView roomId="room-1" />);
    expect(await screen.findByText("Friends")).toBeTruthy();
    expect(screen.queryByRole("region", { name: "Player pool" })).toBeNull();
  });
});
