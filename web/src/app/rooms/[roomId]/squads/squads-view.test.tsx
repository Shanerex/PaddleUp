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
vi.mock("../../../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../api/client")>()),
  getMe: vi.fn().mockResolvedValue(null),
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  getPool: vi.fn(),
  getAuction: vi.fn(),
  getTicket: vi.fn(),
}));
// The real live feed runs; only the socket is a fake the test drives.
const sockets: { handlers: { onConnect(s: unknown): void; onClose(): void }; subs: Map<string, (b: string) => void> }[] = [];
vi.mock("../../../../auction/live", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../auction/live")>()),
  stompConnect: (_t: string, handlers: { onConnect(s: unknown): void; onClose(): void }) => {
    const entry = { handlers, subs: new Map<string, (b: string) => void>() };
    sockets.push(entry);
    queueMicrotask(() => handlers.onConnect({ subscribe: (d: string, cb: (b: string) => void) => entry.subs.set(d, cb), close() {} }));
    return { close() {} };
  },
}));

import { getAuction, getMembers, getPool, getRoom, getTicket, type AuctionState, type Member, type Pool, type Room } from "../../../../api/client";
import { SquadsView } from "./squads-view";

afterEach(() => {
  cleanup();
  sockets.length = 0;
  vi.useRealTimers();
});

// The stub football pack's words and money: nothing in this file is a cricket term.
const room = {
  id: "room-1",
  state: "auction_live",
  myRole: "manager",
  commissioner: { id: "u-ann", displayName: "Ann" },
  configVersion: 1,
  franchiseCount: 2,
  myFranchiseId: "fa",
  advanceTo: [],
  createdAt: "2031-01-01T00:00:00Z",
  packLabels: {
    packId: "stub-football",
    packVersion: "1",
    packName: "Stub Football",
    roles: [
      { id: "gk", label: "Goalkeeper", short: "GK" },
      { id: "def", label: "Defender", short: "DEF" },
    ],
    tags: [{ id: "foreign", label: "Foreign" }],
    restrictedCategories: [{ id: "foreign-cap", label: "Foreign players", tag: "foreign", max: 4 }],
    stats: [],
    terminology: {},
  },
  config: {
    name: "Friends",
    purse: 100_000_000,
    currencyCode: "EUR",
    displayUnit: { label: "M", divisor: 1_000_000 },
    squadMin: 3,
    squadMax: 6,
    roleMinimums: { gk: 1, def: 2 },
    categoryQuotas: { "foreign-cap": 2 },
  },
} as unknown as Room;

const members = [
  { userId: "u-ann", displayName: "Ann", avatar: "p", role: "manager", franchise: { id: "fa", ownerUserId: "u-ann", name: "Ann United", colour: "crimson", monogram: "AU" } },
  { userId: "u-bea", displayName: "Bea", avatar: "p", role: "manager", franchise: { id: "fb", ownerUserId: "u-bea", name: "Bea Rovers", colour: "sky", monogram: "BR" } },
] as unknown as Member[];

const pool = {
  hasPool: true,
  version: 1,
  editCount: 0,
  canEdit: false,
  sets: [],
  players: [
    { id: "p1", name: "Fake 1", role: "gk", tags: [], basePrice: 1, set: "S", country: "X", origin: "source" },
    { id: "p2", name: "Fake 2", role: "def", tags: ["foreign"], basePrice: 1, set: "S", country: "X", origin: "source" },
    { id: "p3", name: "Fake 3", role: "def", tags: [], basePrice: 1, set: "S", country: "X", origin: "source" },
  ],
} as unknown as Pool;

function auction(over: Partial<AuctionState> = {}): AuctionState {
  return {
    phase: "live",
    startsAt: "2031-01-02T20:00:00Z",
    serverNow: "2031-01-02T20:10:00Z",
    lastSeq: 8,
    lotCount: 6,
    lotOpen: true,
    currentBid: 0,
    passedBy: [],
    standings: [
      {
        franchiseId: "fa",
        purse: 70_000_000,
        squad: [
          { lot: 1, playerId: "p1", price: 10_000_000 },
          { lot: 2, playerId: "p2", price: 20_000_000 },
        ],
      },
      { franchiseId: "fb", purse: 100_000_000, squad: [] },
    ],
    unsold: [],
    connected: [],
    ready: [],
    bidLimits: [],
    heldMs: 0,
    round: "main",
    unsoldPool: [],
    pickCounts: [],
    myPicks: [],
    waiting: [],
    resumeLobbySeconds: 120,
    lotsClosed: 2,
    ...over,
  } as AuctionState;
}

function setup(state: AuctionState = auction(), over: Partial<Room> = {}) {
  vi.mocked(getRoom).mockResolvedValue({ ...room, ...over } as Room);
  vi.mocked(getMembers).mockResolvedValue(members);
  vi.mocked(getPool).mockResolvedValue(pool);
  vi.mocked(getAuction).mockResolvedValue(state);
  vi.mocked(getTicket).mockResolvedValue({ ticket: "t" });
}

beforeEach(() => vi.clearAllMocks());

describe("SquadsView", () => {
  it("shows one chip per franchise and opens on my own squad, read from the state's standings", async () => {
    setup();
    render(<SquadsView roomId="room-1" />);

    const chips = within(await screen.findByRole("group", { name: "Franchises" })).getAllByRole("button");
    expect(chips.map((c) => c.textContent)).toEqual(["AUYou", "BRBea"]);
    expect(chips[0].getAttribute("aria-pressed")).toBe("true");
    const panel = await screen.findByTestId("squad-panel");
    expect(panel.getAttribute("data-franchise")).toBe("fa");
    expect(within(panel).getByTestId("squad-purse").textContent).toBe("€70 M");
    expect(within(panel).getByTestId("squad-spent").textContent).toBe("€30 M");
    expect(within(panel).getByTestId("squad-count").textContent).toContain("2");
    expect(within(panel).getByTestId("squad-count").textContent).toContain("3–6");
  });

  it("groups players by pack role, newest first, with the pack's tag words and the price", async () => {
    setup();
    render(<SquadsView roomId="room-1" />);

    const rows = await screen.findAllByTestId("squad-player");
    expect(rows.map((r) => r.textContent)).toEqual([
      expect.stringContaining("Fake 1"),
      expect.stringContaining("Fake 2"),
    ]);
    expect(rows[1].textContent).toContain("Foreign · Lot 2");
    expect(rows[1].textContent).toContain("€20 M");
    expect(screen.getByRole("region", { name: "Goalkeeper" })).toBeTruthy();
    expect(screen.getByRole("region", { name: "Defender" })).toBeTruthy();
  });

  it("draws a bar and a Met or Needs note for each role with a minimum, and the restricted count against the room's cap", async () => {
    setup();
    render(<SquadsView roomId="room-1" />);

    const gk = await screen.findByTestId("squad-role-gk");
    expect(gk.textContent).toContain("1/1");
    expect(gk.textContent).toContain("Met");
    const def = screen.getByTestId("squad-role-def");
    expect(def.textContent).toContain("1/2");
    expect(def.textContent).toContain("Needs 1");
    expect(screen.getByTestId("squad-restricted-foreign-cap").textContent).toBe("1/2");
    expect(screen.getByText("FOREIGN PLAYERS")).toBeTruthy();
  });

  it("switches to another franchise's squad and its purse, every member can see them all", async () => {
    setup();
    render(<SquadsView roomId="room-1" />);

    await userEvent.click(await screen.findByRole("button", { name: /Bea/ }));

    const panel = screen.getByTestId("squad-panel");
    expect(panel.getAttribute("data-franchise")).toBe("fb");
    expect(within(panel).getByTestId("squad-purse").textContent).toBe("€100 M");
    expect(within(panel).getByTestId("squad-empty")).toBeTruthy();
    expect(within(panel).getByTestId("squad-restricted-foreign-cap").textContent).toBe("0/2");
  });

  it("opens on the first franchise for a Commissioner without a franchise", async () => {
    setup(auction(), { myFranchiseId: null, myRole: "commissioner" });
    render(<SquadsView roomId="room-1" />);

    await waitFor(() => expect(screen.getByTestId("squad-panel").getAttribute("data-franchise")).toBe("fa"));
    expect(screen.getAllByRole("button", { name: /Ann/ }).length).toBe(1);
  });

  it("says the room can't be found for a 404", async () => {
    const { ApiError } = await import("../../../../api/client");
    vi.mocked(getRoom).mockRejectedValue(new ApiError(404, null));
    vi.mocked(getMembers).mockRejectedValue(new ApiError(404, null));
    vi.mocked(getPool).mockRejectedValue(new ApiError(404, null));
    vi.mocked(getAuction).mockRejectedValue(new ApiError(404, null));
    vi.mocked(getTicket).mockResolvedValue({ ticket: "t" });
    render(<SquadsView roomId="room-1" />);

    expect(await screen.findByText("Can't find that room")).toBeTruthy();
  });
});

describe("SquadsView live updates", () => {
  const hammer = {
    seq: 9,
    at: "2031-01-02T20:11:00Z",
    kind: "lot_closed",
    franchiseId: "fa",
    lot: 3,
    payload: { sold: true, amount: 15_000_000 },
  };
  const afterHammer = () =>
    auction({
      lastSeq: 9,
      lotOpen: false,
      lotsClosed: 3,
      lastResult: { lot: 3, sold: true, franchiseId: "fa", amount: 15_000_000 },
      standings: [
        {
          franchiseId: "fa",
          purse: 55_000_000,
          squad: [
            { lot: 1, playerId: "p1", price: 10_000_000 },
            { lot: 2, playerId: "p2", price: 20_000_000 },
            { lot: 3, playerId: "p3", price: 15_000_000 },
          ],
        },
        { franchiseId: "fb", purse: 100_000_000, squad: [] },
      ],
    });

  it("shows the new player, count, purse and bars after a hammer with no reload, marked until the next lot opens", async () => {
    setup();
    render(<SquadsView roomId="room-1" />);
    await screen.findAllByTestId("squad-player");
    expect(screen.queryByTestId("just-in")).toBeNull();
    const roomReads = vi.mocked(getRoom).mock.calls.length;

    vi.mocked(getAuction).mockResolvedValue(afterHammer());
    await act(async () => {
      sockets[0].subs.get("/topic/auctions/room-1")?.(JSON.stringify(hammer));
    });

    await waitFor(() => expect(screen.getAllByTestId("squad-player")).toHaveLength(3));
    const panel = screen.getByTestId("squad-panel");
    expect(within(panel).getByTestId("squad-purse").textContent).toBe("€55 M");
    expect(within(panel).getByTestId("squad-spent").textContent).toBe("€45 M");
    expect(within(panel).getByTestId("squad-role-def").textContent).toContain("Met");
    const fresh = within(panel).getByTestId("just-in").closest("[data-testid=squad-player]");
    expect(fresh?.textContent).toContain("Fake 3");
    expect(vi.mocked(getRoom).mock.calls.length).toBe(roomReads);
  });

  it("drops the marker once the next lot opens", async () => {
    setup(afterHammer());
    render(<SquadsView roomId="room-1" />);
    expect(await screen.findByTestId("just-in")).toBeTruthy();

    vi.mocked(getAuction).mockResolvedValue({ ...afterHammer(), lastSeq: 10, lotOpen: true });
    await act(async () => {
      sockets[0].subs.get("/topic/auctions/room-1")?.(JSON.stringify({ seq: 10, at: "x", kind: "lot_opened", lot: 4, payload: {} }));
    });

    await waitFor(() => expect(screen.queryByTestId("just-in")).toBeNull());
    expect(screen.getAllByTestId("squad-player")).toHaveLength(3);
  });
});
