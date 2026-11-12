// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  bid: vi.fn(),
  setReady: vi.fn(),
  scheduleAuction: vi.fn(),
}));
// The live feed is replaced by a store the test drives; useLive is its only seam.
const feed = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  const api = {
    snapshot: undefined as unknown,
    now: 0,
    live: { retry: vi.fn(), resync: vi.fn(), applyPresence: vi.fn() },
    set(next: unknown) {
      api.snapshot = next;
      listeners.forEach((l) => l());
    },
    subscribe(l: () => void) {
      listeners.add(l);
      return () => listeners.delete(l);
    },
  };
  return api;
});
vi.mock("../../../../auction/useLive", async () => {
  const React = await import("react");
  return {
    useLive: () => ({
      live: feed.live,
      clock: { ready: true, now: () => feed.now },
      snapshot: React.useSyncExternalStore(feed.subscribe, () => feed.snapshot as never, () => feed.snapshot as never),
    }),
    useTicker: () => 0,
  };
});

import { ApiError, bid, getMembers, getRoom, scheduleAuction, setReady, type AuctionState, type Member, type Room } from "../../../../api/client";
import { AuctionView } from "./auction-view";

const NOW = Date.parse("2030-03-01T10:00:00Z");
const labels = { packId: "stub-football", packName: "Stub", roles: [{ id: "gk", label: "Goalkeeper" }], tags: [], stats: [], terminology: {} };
const config = {
  name: "Friends",
  purse: 100_000_000,
  currencyCode: "EUR",
  displayUnit: { label: "M", divisor: 1_000_000 },
  incrementLadder: [{ from: 0, step: 100_000 }],
  squadMin: 3,
  squadMax: 18,
  roleMinimums: {},
};
const franchise = (id: string, name: string, colour: string) => ({ id, ownerUserId: `u-${id}`, name, colour, monogram: name.slice(0, 2).toUpperCase() });
const members = [
  { userId: "u-a", displayName: "Ann", avatar: "p", role: "manager", franchise: franchise("fa", "Ace FC", "crimson") },
  { userId: "u-b", displayName: "Ben", avatar: "p", role: "manager", franchise: franchise("fb", "Blue FC", "sky") },
] as unknown as Member[];
const room = (over: Record<string, unknown> = {}) =>
  ({ id: "r1", state: "auction_live", myRole: "manager", myFranchiseId: "fa", commissioner: { id: "u-c", displayName: "Cy" }, config, packLabels: labels, ...over }) as unknown as Room;

const lot = { no: 1, playerId: "p", name: "Fake 1", country: "Spain", role: "gk", tags: [], set: "Keepers", position: 0, basePrice: 300_000 };
const live = (over: Partial<AuctionState> = {}): AuctionState => ({
  phase: "live",
  startsAt: "2030-03-01T09:59:00Z",
  serverNow: "2030-03-01T10:00:00Z",
  lastSeq: 3,
  config: { openSeconds: 30, bidSeconds: 15, gapSeconds: 5, ladder: [] },
  lotCount: 6,
  lot,
  lotOpen: true,
  currentBid: 0,
  passedBy: [],
  standings: [
    { franchiseId: "fa", purse: 100_000_000, squad: [] },
    { franchiseId: "fb", purse: 100_000_000, squad: [] },
  ],
  unsold: [],
  connected: [],
  ready: [],
  nextBid: 300_000,
  deadline: "2030-03-01T10:00:30Z",
  ...over,
});
const scheduled = (over: Partial<AuctionState> = {}): AuctionState =>
  live({ phase: "scheduled", startsAt: "2030-03-01T10:10:00Z", lotOpen: false, lot: undefined, nextBid: undefined, deadline: undefined, config: undefined, lotCount: 0, ...over });

function snap(state: AuctionState | null, over: Record<string, unknown> = {}) {
  return { state, connection: "live", synced: true, connected: ["fa"], ready: [], missing: false, lastSeen: null, frozenLeft: null, ...over };
}

beforeEach(() => {
  feed.now = NOW;
  vi.mocked(getRoom).mockResolvedValue(room());
  vi.mocked(getMembers).mockResolvedValue(members);
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AuctionView", () => {
  it("shows the lobby while scheduled and the lot screen once the server says live, with no reload", async () => {
    feed.set(snap(scheduled()));
    render(<AuctionView roomId="r1" />);
    expect((await screen.findByTestId("lobby-countdown")).textContent).toBe("00:10:00");

    act(() => feed.set(snap(live())));

    expect(await screen.findByTestId("lot-card")).toBeTruthy();
    expect(screen.queryByTestId("lobby-countdown")).toBeNull();
    expect(screen.getByTestId("purse").textContent).toBe("€100 M");
    expect(screen.getByTestId("timer").textContent).toBe("0:30");
    expect(screen.getByTestId("bid-button").textContent).toContain("€0.3 M");
  });

  it("sends exactly the server's next bid and shows nothing new until the server says so", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000 })));
    vi.mocked(bid).mockResolvedValue({ outcome: "accepted", lot: 1, serverNow: "x" });
    render(<AuctionView roomId="r1" />);
    const button = await screen.findByTestId("bid-button");

    await userEvent.click(button);

    expect(bid).toHaveBeenCalledTimes(1);
    expect(vi.mocked(bid).mock.calls[0][1]).toMatchObject({ lot: 1, amount: 400_000 });
    expect(screen.getByTestId("current-bid").textContent).toBe("€0.3 M");
    expect(screen.getByTestId("leader").textContent).toContain("Blue FC");
  });

  it("answers a refused bid with the server's reason and leaves bid, purse and leader unchanged", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000 })));
    vi.mocked(bid).mockResolvedValue({ outcome: "rejected", reason: "purse", lot: 1, serverNow: "x" });
    render(<AuctionView roomId="r1" />);

    await userEvent.click(await screen.findByTestId("bid-button"));

    expect((await screen.findByTestId("notice-rejected")).textContent).toContain("more than your purse allows");
    expect(screen.getByTestId("current-bid").textContent).toBe("€0.3 M");
    expect(screen.getByTestId("purse").textContent).toBe("€100 M");
    expect(screen.getByTestId("leader").textContent).toContain("Blue FC");
  });

  it("rereads the state after a refusal, so a squad lock the patch could not know shows up", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000 })));
    vi.mocked(bid).mockResolvedValue({ outcome: "rejected", reason: "reserve", lot: 1, serverNow: "x" });
    render(<AuctionView roomId="r1" />);

    await userEvent.click(await screen.findByTestId("bid-button"));

    expect((await screen.findByTestId("notice-rejected")).textContent).toContain("too little purse");
    expect(feed.live.resync).toHaveBeenCalled();
  });

  it("shows the bidLocked dock with the reason when the server names a squad lock", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000, bidLock: { reason: "reserve", role: "gk" } })));
    vi.mocked(getRoom).mockResolvedValue(room({ packLabels: { ...labels, restrictedCategories: [] }, config: { ...config, categoryQuotas: {} } }));
    render(<AuctionView roomId="r1" />);

    const button = await screen.findByTestId("bid-button");
    expect(button.getAttribute("data-state")).toBe("bid_locked");
    expect(button.textContent).toContain("Can't bid €0.4 M");
    expect(button.textContent).toContain("Goalkeeper minimum");
  });

  it("shows outbid when the lead moves off me, and the new next amount on the button", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fa", nextBid: 400_000 })));
    render(<AuctionView roomId="r1" />);
    expect((await screen.findByTestId("bid-button")).getAttribute("data-state")).toBe("leading");

    act(() => feed.set(snap(live({ lastSeq: 4, currentBid: 400_000, leaderFranchiseId: "fb", nextBid: 500_000 }))));

    expect((await screen.findByTestId("notice-outbid")).textContent).toContain("Blue FC went €0.4 M. Rude.");
    expect(screen.getByTestId("bid-button").textContent).toContain("€0.5 M");
  });

  it("locks the button, freezes and greys the timer and says WAITING while reconnecting", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000 }), { connection: "reconnecting", synced: false, frozenLeft: 17_000 }));
    render(<AuctionView roomId="r1" />);

    expect((await screen.findByTestId("notice-reconnecting")).textContent).toContain("Reconnecting…");
    const button = screen.getByTestId("bid-button") as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain("Reconnecting…");
    expect(screen.getByTestId("timer").textContent).toBe("0:17");
    expect(screen.getByTestId("timer").getAttribute("data-frozen")).toBe("true");
    expect(screen.getByTestId("timer-note").textContent).toBe("WAITING");
  });

  it("stamps SOLD from the server's result and says what comes next", async () => {
    feed.set(
      snap(live({ lotOpen: false, currentBid: 400_000, leaderFranchiseId: "fb", nextBid: undefined, lastResult: { lot: 1, sold: true, franchiseId: "fb", amount: 400_000 } })),
    );
    render(<AuctionView roomId="r1" />);

    expect((await screen.findByTestId("stamp-sold")).textContent).toContain("Blue FC");
    expect(screen.getByTestId("bid-button").textContent).toContain("Next lot coming up…");
  });

  it("gives a Commissioner without a franchise the live lot, no button and the cause", async () => {
    vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: null }));
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000 })));
    render(<AuctionView roomId="r1" />);

    expect((await screen.findByTestId("bid-observer")).textContent).toContain("Franchises bid; you watch.");
    expect(screen.queryByTestId("bid-button")).toBeNull();
    expect(screen.getByText("WATCHING AS")).toBeTruthy();
    expect(screen.getByTestId("current-bid").textContent).toBe("€0.3 M");
  });

  it("shows a locked button and the pause notice for a paused fixture, and the adjourned card", async () => {
    feed.set(snap(live({ phase: "paused" })));
    render(<AuctionView roomId="r1" />);
    expect((await screen.findByTestId("notice-paused")).textContent).toContain("Commish hit pause");
    expect((screen.getByTestId("bid-button") as HTMLButtonElement).disabled).toBe(true);

    act(() => feed.set(snap(live({ phase: "adjourned", lotOpen: false }))));
    expect(await screen.findByTestId("adjourned-card")).toBeTruthy();
    expect((screen.getByTestId("bid-button") as HTMLButtonElement).textContent).toContain("Adjourned");
  });

  it("shows no auction UI for an ended auction, only the way back, and the same for no auction", async () => {
    feed.set(snap(live({ phase: "ended", lotOpen: false, nextBid: undefined })));
    render(<AuctionView roomId="r1" />);
    expect(await screen.findByTestId("closed-card")).toBeTruthy();
    expect(screen.queryByTestId("bid-button")).toBeNull();
    expect(screen.getByTestId("auction-pill").textContent).toBe("ENDED");

    act(() => feed.set(snap(null, { missing: true })));
    expect(await screen.findByTestId("no-auction-card")).toBeTruthy();
  });

  it("toggles ready through the server and renders its answer", async () => {
    feed.set(snap(scheduled()));
    vi.mocked(setReady).mockResolvedValue({ connected: ["fa"], ready: ["fa"] });
    render(<AuctionView roomId="r1" />);

    await userEvent.click(await screen.findByTestId("ready-button"));

    expect(setReady).toHaveBeenCalledWith("r1", true);
    expect(feed.live.applyPresence).toHaveBeenCalledWith({ connected: ["fa"], ready: ["fa"] });
    // The button still shows what the server last said, until presence arrives.
    expect(screen.getByTestId("ready-button").textContent).toContain("I'm ready");
  });

  it("asks the feed to resync when ready is refused because the auction moved on", async () => {
    feed.set(snap(scheduled()));
    vi.mocked(setReady).mockRejectedValue(new ApiError(409, { error: "not_scheduled" }));
    render(<AuctionView roomId="r1" />);

    await userEvent.click(await screen.findByTestId("ready-button"));

    await waitFor(() => expect(feed.live.resync).toHaveBeenCalled());
  });

  it("says the room is missing for a 404", async () => {
    vi.mocked(getRoom).mockRejectedValue(new ApiError(404, { error: "room_not_found" }));
    feed.set(snap(null));
    render(<AuctionView roomId="r1" />);
    expect(await screen.findByText("Can't find that room")).toBeTruthy();
  });

  describe("Change time and the schedule sheet (Room `lobbyReschedule`, R-13)", () => {
    const local = (iso: string) => {
      const d = new Date(iso);
      const p = (n: number) => String(n).padStart(2, "0");
      return { date: `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`, time: `${p(d.getHours())}:${p(d.getMinutes())}` };
    };

    it("gives the Commissioner Change time, with or without a franchise, and a manager nothing", async () => {
      feed.set(snap(scheduled()));
      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: "fa" }));
      const first = render(<AuctionView roomId="r1" />);
      expect(await screen.findByTestId("change-time")).toBeTruthy();
      first.unmount();

      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: null }));
      const second = render(<AuctionView roomId="r1" />);
      expect(await screen.findByTestId("change-time")).toBeTruthy();
      second.unmount();

      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "manager" }));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("lobby-countdown");
      expect(screen.queryByTestId("change-time")).toBeNull();
    });

    it("opens prefilled in the viewer's zone, sends one UTC instant and asks the feed to resync", async () => {
      feed.set(snap(scheduled()));
      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner" }));
      vi.mocked(scheduleAuction).mockResolvedValue(scheduled());
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("change-time"));

      const dialog = screen.getByRole("dialog", { name: "Change the start time?" });
      const want = local("2030-03-01T10:10:00Z");
      expect((screen.getByLabelText("Date") as HTMLInputElement).value).toBe(want.date);
      expect((screen.getByLabelText("Start time") as HTMLInputElement).value).toBe(want.time);
      expect(dialog.textContent).toContain("Ready taps stay as they are.");
      expect(screen.getByTestId("schedule-until").textContent).toBe("in 10m");

      const next = local("2030-03-01T12:30:00Z");
      fireEvent.change(screen.getByLabelText("Date"), { target: { value: next.date } });
      fireEvent.change(screen.getByLabelText("Start time"), { target: { value: next.time } });
      await user.click(screen.getByRole("button", { name: "Change start time" }));

      expect(scheduleAuction).toHaveBeenCalledTimes(1);
      expect(vi.mocked(scheduleAuction).mock.calls[0]).toEqual(["r1", "2030-03-01T12:30:00.000Z"]);
      await waitFor(() => expect(feed.live.resync).toHaveBeenCalled());
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("shows the server's field error for a past time and changes nothing", async () => {
      feed.set(snap(scheduled()));
      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner" }));
      vi.mocked(scheduleAuction).mockRejectedValue(new ApiError(400, { error: "invalid_request", message: "The start must be in the future", field: "startsAt" }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("change-time"));
      await user.click(screen.getByRole("button", { name: "Change start time" }));

      expect((await screen.findByTestId("schedule-past")).textContent).toBe("That time has already gone. Pick a later one.");
      expect(screen.getByTestId("schedule-until").textContent).toBe("—");
      expect(screen.getByRole("dialog")).toBeTruthy();
      expect(feed.live.resync).not.toHaveBeenCalled();
    });

    it("shows the server's refusal reason with a way to the pool", async () => {
      feed.set(snap(scheduled()));
      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner" }));
      vi.mocked(scheduleAuction).mockRejectedValue(new ApiError(409, { error: "not_ready", message: "The pool has 64 players but 72 are needed to fill every squad; add players first" }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("change-time"));
      await user.click(screen.getByRole("button", { name: "Change start time" }));

      const refused = await screen.findByTestId("schedule-refused");
      expect(refused.textContent).toContain("The pool has 64 players but 72 are needed to fill every squad; add players first. Nothing was saved.");
      expect(refused.querySelector("a")?.getAttribute("href")).toBe("/rooms/r1/pool");
    });

    it("offers a Commissioner a room with no auction the same sheet, and reconnects after it is set", async () => {
      feed.set(snap(null, { missing: true }));
      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", state: "auction_scheduled" }));
      vi.mocked(scheduleAuction).mockResolvedValue(scheduled());
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("schedule-from-card"));
      const sheet = screen.getByRole("dialog", { name: "Schedule the auction" });
      await user.click(within(sheet).getByRole("button", { name: "Schedule auction" }));

      expect(scheduleAuction).toHaveBeenCalledTimes(1);
      await waitFor(() => expect(feed.live.retry).toHaveBeenCalled());
    });

    it("shows a manager the missing-auction card with no schedule control", async () => {
      feed.set(snap(null, { missing: true }));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("no-auction-card");
      expect(screen.queryByTestId("schedule-from-card")).toBeNull();
    });
  });
});
