// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("../../../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../api/client")>()),
  getMe: vi.fn().mockResolvedValue(null),
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  getPool: vi.fn(),
  saveSetOrder: vi.fn(),
}));

import { ApiError, getMembers, getPool, getRoom, saveSetOrder, type Member, type Pool, type Room, type RoomState } from "../../../../api/client";
import { SetOrderView } from "./set-order-view";

function room(state: RoomState = "setup"): Room {
  return {
    id: "room-1",
    state,
    myRole: "commissioner",
    commissioner: { id: "u-ann", displayName: "Ann" },
    configVersion: 1,
    franchiseCount: 2,
    myFranchiseId: null,
    advanceTo: [],
    createdAt: "2031-01-01T00:00:00Z",
    packLabels: { packId: "p", packVersion: "1", roles: [], tags: [], restrictedCategories: [], stats: [], terminology: {} },
    config: { name: "Friends" },
  } as unknown as Room;
}

const members = [{ userId: "u-ann", displayName: "Ann", avatar: "preset-01", role: "commissioner", franchise: null }] as unknown as Member[];

function pool(order: string[] = ["Marquee", "Batters", "Empty set"], over: Partial<Pool> = {}): Pool {
  return {
    hasPool: true,
    version: 4,
    datasetVersion: 3,
    latestDatasetVersion: 3,
    editCount: 0,
    canEdit: true,
    players: [],
    sets: order.map((name) => ({ name, players: name === "Empty set" ? 0 : name === "Marquee" ? 1 : 12 })),
    ...over,
  } as Pool;
}

function setup(opts: { state?: RoomState; pool?: Pool; role?: string } = {}) {
  vi.mocked(getRoom).mockResolvedValue({ ...room(opts.state), myRole: opts.role ?? "commissioner" } as Room);
  vi.mocked(getMembers).mockResolvedValue(members);
  vi.mocked(getPool).mockResolvedValue(opts.pool ?? pool());
  return render(<SetOrderView roomId="room-1" />);
}

const names = () => screen.getAllByTestId("set-row").map((li) => within(li).getByText(/^(Marquee|Batters|Empty set)$/).textContent);

beforeEach(() => {
  vi.mocked(saveSetOrder).mockReset();
});
afterEach(cleanup);

describe("SetOrderView (Room sets, setsMember)", () => {
  it("lists the sets in order with their player counts, and 0 for an emptied set", async () => {
    setup();

    await screen.findByText("Marquee");
    expect(names()).toEqual(["Marquee", "Batters", "Empty set"]);
    expect(within(screen.getAllByTestId("set-row")[2]).getByText("0")).toBeTruthy();
    expect(screen.queryByText("Order changed")).toBeNull();
  });

  it("highlights the moved row and sends the whole order with the pool version on one Save", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(saveSetOrder).mockResolvedValue(pool(["Batters", "Marquee", "Empty set"], { version: 5 }));

    await screen.findByText("Marquee");
    await user.click(screen.getByRole("button", { name: "Move Batters up" }));
    expect(names()).toEqual(["Batters", "Marquee", "Empty set"]);
    expect(screen.getByText("MOVED")).toBeTruthy();
    expect(screen.getByText("Order changed")).toBeTruthy();
    expect(saveSetOrder).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Save order" }));

    expect(saveSetOrder).toHaveBeenCalledTimes(1);
    expect(saveSetOrder).toHaveBeenCalledWith("room-1", 4, ["Batters", "Marquee", "Empty set"]);
    await waitFor(() => expect(screen.queryByText("Order changed")).toBeNull());
    expect(names()).toEqual(["Batters", "Marquee", "Empty set"]);
  });

  it("restores the saved order on Discard", async () => {
    const user = userEvent.setup();
    setup();

    await screen.findByText("Marquee");
    await user.click(screen.getByRole("button", { name: "Move Marquee down" }));
    await user.click(screen.getByRole("button", { name: "Discard" }));

    expect(names()).toEqual(["Marquee", "Batters", "Empty set"]);
    expect(screen.queryByText("Order changed")).toBeNull();
    expect(saveSetOrder).not.toHaveBeenCalled();
  });

  it("disables the first up and last down moves", async () => {
    setup();
    await screen.findByText("Marquee");

    expect((screen.getByRole("button", { name: "Move Marquee up" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Move Empty set down" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("gives a member the order read-only with the member note and no controls", async () => {
    setup({ role: "manager", pool: pool(undefined, { canEdit: false }) });

    await screen.findByText("Marquee");
    expect(screen.getByText("Ann sets the order until the auction goes live.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Move/ })).toBeNull();
    expect(screen.queryByText("Order changed")).toBeNull();
  });

  it("reloads and shows the stale banner when the version is old", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(saveSetOrder).mockImplementation(() => Promise.reject(new ApiError(409, { error: "stale_version" })));
    await screen.findByText("Marquee");
    await user.click(screen.getByRole("button", { name: "Move Batters up" }));
    vi.mocked(getPool).mockResolvedValue(pool(["Empty set", "Marquee", "Batters"], { version: 6 }));

    await user.click(screen.getByRole("button", { name: "Save order" }));

    expect(await screen.findByText("The set order changed in another tab.")).toBeTruthy();
    await waitFor(() => expect(names()).toEqual(["Empty set", "Marquee", "Batters"]));
    expect(screen.queryByText("Order changed")).toBeNull();
  });

  it("removes the controls with a locked banner when the auction has started", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(saveSetOrder).mockImplementation(() => Promise.reject(new ApiError(409, { error: "room_locked" })));
    await screen.findByText("Marquee");
    await user.click(screen.getByRole("button", { name: "Move Batters up" }));
    vi.mocked(getRoom).mockResolvedValue(room("auction_live"));
    vi.mocked(getPool).mockResolvedValue(pool(undefined, { canEdit: false }));

    await user.click(screen.getByRole("button", { name: "Save order" }));

    expect(await screen.findByText("Too late for that.")).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole("button", { name: /Move/ })).toBeNull());
    expect(screen.getByText("Locked. The auction is live, so nobody can change the set order now.")).toBeTruthy();
  });

  it("says there are no sets before the room has a pool", async () => {
    setup({ pool: pool([], { hasPool: false, version: 0, canEdit: true }) });

    expect(await screen.findByText(/No player pool yet, so there are no sets\./)).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Move/ })).toBeNull();
  });
});
