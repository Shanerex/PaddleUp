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
import { startText } from "./squad-panel";
import { FALLBACK_MS, SquadsView } from "./squads-view";

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

  it("keeps the first word of a name on its chip, and the whole name when two franchises start alike", async () => {
    setup();
    vi.mocked(getMembers).mockResolvedValue([
      { ...members[0], franchise: { ...members[0].franchise!, name: "Club One" } },
      { ...members[1], franchise: { ...members[1].franchise!, name: "Club Two" } },
    ] as Member[]);
    render(<SquadsView roomId="room-1" />);

    const chips = within(await screen.findByRole("group", { name: "Franchises" })).getAllByRole("button");
    expect(chips.map((c) => c.textContent)).toEqual(["AUYou", "BRClub Two"]);
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

  it("reads the room again when the auction goes live, so the header follows", async () => {
    setup(auction({ phase: "scheduled", lotOpen: false, lotsClosed: 0, standings: [] }));
    render(<SquadsView roomId="room-1" />);
    await screen.findByTestId("squad-empty");
    const roomReads = vi.mocked(getRoom).mock.calls.length;

    vi.mocked(getAuction).mockResolvedValue(auction({ lastSeq: 9 }));
    await act(async () => {
      sockets[0].subs.get("/topic/auctions/room-1")?.(JSON.stringify({ seq: 9, at: "x", kind: "auction_started", payload: {} }));
    });

    await waitFor(() => expect(screen.getByTestId("squad-pill").textContent).toBe("LIVE"));
    await waitFor(() => expect(vi.mocked(getRoom).mock.calls.length).toBe(roomReads + 1));
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


describe("SquadsView before the first hammer and offline", () => {
  const scheduled = () =>
    auction({
      phase: "scheduled",
      lotOpen: false,
      lotsClosed: 0,
      lastSeq: 1,
      standings: [
        { franchiseId: "fa", purse: 100_000_000, squad: [] },
        { franchiseId: "fb", purse: 100_000_000, squad: [] },
      ],
    });

  it("shows the empty state with the scheduled start for every franchise before the first hammer", async () => {
    setup(scheduled());
    render(<SquadsView roomId="room-1" />);

    for (const who of [null, /Bea/]) {
      if (who) await userEvent.click(await screen.findByRole("button", { name: who }));
      const empty = await screen.findByTestId("squad-empty");
      expect(empty.textContent).toContain("No players yet");
      expect(empty.textContent).toContain(`The first hammer falls ${startText("2031-01-02T20:00:00Z")} your time.`);
      expect(screen.getByTestId("squad-pill").textContent).toMatch(/^AUCTION /);
      expect(screen.getByTestId("squad-purse").textContent).toBe("€100 M");
      expect(screen.getByTestId("squad-spent").textContent).toBe("—");
      expect(screen.getByTestId("squad-role-gk").textContent).toContain("Needs 1");
      expect(screen.getByTestId("squad-restricted-foreign-cap").textContent).toBe("0/2");
    }
  });

  it("takes the franchises and the full purse from the room when the state lists none yet", async () => {
    setup({ ...scheduled(), standings: [] });
    render(<SquadsView roomId="room-1" />);

    const chips = within(await screen.findByRole("group", { name: "Franchises" })).getAllByRole("button");
    expect(chips.map((c) => c.textContent)).toEqual(["AUYou", "BRBea"]);
    expect((await screen.findByTestId("squad-empty")).textContent).toContain("The first hammer falls");
    expect(screen.getByTestId("squad-purse").textContent).toBe("€100 M");
  });

  it("shows every franchise's empty squad and full purse when the room has no auction yet", async () => {
    const { ApiError } = await import("../../../../api/client");
    setup();
    vi.mocked(getAuction).mockRejectedValue(new ApiError(404, null));
    render(<SquadsView roomId="room-1" />);

    const empty = await screen.findByTestId("squad-empty");
    expect(empty.textContent).toContain("The auction isn't scheduled yet.");
    expect(screen.getByTestId("squad-pill").textContent).toBe("NOT SCHEDULED");
    expect(screen.getByTestId("squad-purse").textContent).toBe("€100 M");
  });

  it("says it couldn't load the squads, with a retry, when the first room read fails", async () => {
    const { ApiError } = await import("../../../../api/client");
    setup();
    vi.mocked(getRoom).mockRejectedValueOnce(new ApiError(500, null));
    render(<SquadsView roomId="room-1" />);

    expect((await screen.findByRole("alert")).textContent).toContain("Couldn't load the squads.");
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByTestId("squad-panel")).toBeTruthy();
  });

  it("says nothing bought yet, not the start line, for an empty squad once the auction is under way", async () => {
    setup();
    render(<SquadsView roomId="room-1" />);

    await userEvent.click(await screen.findByRole("button", { name: /Bea/ }));

    expect(screen.getByTestId("squad-empty").textContent).toContain("Nothing bought yet.");
    expect(screen.getByTestId("squad-pill").textContent).toBe("LIVE");
  });

  it("falls back to fetching the state on a lost connection and says so, then stops when the feed is back", async () => {
    setup();
    render(<SquadsView roomId="room-1" />);
    await screen.findAllByTestId("squad-player");
    expect(screen.queryByTestId("squads-offline")).toBeNull();
    const reads = vi.mocked(getAuction).mock.calls.length;

    // The socket drops and no ticket can be had, so the feed stays down.
    vi.mocked(getTicket).mockRejectedValue(new Error("down"));
    vi.mocked(getAuction).mockResolvedValue({
      ...auction(),
      lastSeq: 9,
      standings: [
        { franchiseId: "fa", purse: 70_000_000, squad: [...auction().standings[0].squad, { lot: 3, playerId: "p3", price: 5_000_000 }] },
        { franchiseId: "fb", purse: 100_000_000, squad: [] },
      ],
    });
    await act(async () => sockets[0].handlers.onClose());

    expect(await screen.findByTestId("squads-offline")).toBeTruthy();
    await waitFor(() => expect(screen.getAllByTestId("squad-player")).toHaveLength(3));
    expect(vi.mocked(getAuction).mock.calls.length).toBeGreaterThan(reads);
    expect(FALLBACK_MS).toBe(5000);

    // The feed comes back: the note goes away and the screen keeps the newest state.
    vi.mocked(getTicket).mockResolvedValue({ ticket: "t2" });
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(screen.queryByTestId("squads-offline")).toBeNull());
    expect(screen.getAllByTestId("squad-player")).toHaveLength(3);
  });
});
