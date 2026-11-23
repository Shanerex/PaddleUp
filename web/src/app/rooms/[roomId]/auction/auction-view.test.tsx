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
  getPool: vi.fn(),
  getEvents: vi.fn(),
  bid: vi.fn(),
  setReady: vi.fn(),
  scheduleAuction: vi.fn(),
  sendControl: vi.fn(),
  sendPicks: vi.fn(),
}));
// The live feed is replaced by a store the test drives; useLive is its only seam.
const feed = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  const api = {
    snapshot: undefined as unknown,
    now: 0,
    ticks: 0,
    live: { retry: vi.fn(), resync: vi.fn(), applyPresence: vi.fn() },
    set(next: unknown) {
      api.snapshot = next;
      listeners.forEach((l) => l());
    },
    /** One clock tick: the screen re-renders with nothing else changed. */
    tick() {
      api.ticks++;
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
    useTicker: () => React.useSyncExternalStore(feed.subscribe, () => feed.ticks, () => feed.ticks),
  };
});
// The derived views are spied on (they still run) to see when the screen rebuilds them.
vi.mock("../../../../auction/feed", async (importOriginal) => {
  const m = await importOriginal<typeof import("../../../../auction/feed")>();
  return { ...m, feedView: vi.fn(m.feedView) };
});
vi.mock("../../../../auction/rivals", async (importOriginal) => {
  const m = await importOriginal<typeof import("../../../../auction/rivals")>();
  return { ...m, rivalsView: vi.fn(m.rivalsView), needsView: vi.fn(m.needsView) };
});

import { ApiError, bid, getEvents, getMembers, getPool, getRoom, scheduleAuction, sendControl, sendPicks, setReady, type AuctionState, type Member, type Room } from "../../../../api/client";
import { feedView } from "../../../../auction/feed";
import { needsView, rivalsView } from "../../../../auction/rivals";
import { AuctionView } from "./auction-view";

const NOW = Date.parse("2030-03-01T10:00:00Z");
const labels = { packId: "stub-football", packName: "Stub", roles: [{ id: "gk", label: "Goalkeeper" }], tags: [], stats: [], terminology: {}, restrictedCategories: [] };
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
  bidLimits: [],
  heldMs: 0,
  round: "main",
  unsoldPool: [],
  pickCounts: [],
  myPicks: [],
  waiting: [],
  resumeLobbySeconds: 120,
  lotsClosed: 0,
  nextBid: 300_000,
  deadline: "2030-03-01T10:00:30Z",
  ...over,
});
const scheduled = (over: Partial<AuctionState> = {}): AuctionState =>
  live({ phase: "scheduled", startsAt: "2030-03-01T10:10:00Z", lotOpen: false, lot: undefined, nextBid: undefined, deadline: undefined, config: undefined, lotCount: 0, ...over });

function snap(state: AuctionState | null, over: Record<string, unknown> = {}) {
  return { state, connection: "live", synced: true, connected: ["fa"], ready: [], missing: false, lastSeen: null, frozenLeft: null, bids: [], adminEvent: null, ...over };
}

beforeEach(() => {
  feed.now = NOW;
  vi.mocked(getRoom).mockResolvedValue(room());
  vi.mocked(getMembers).mockResolvedValue(members);
  vi.mocked(getPool).mockResolvedValue({ players: [] } as never);
  vi.mocked(getEvents).mockResolvedValue({ events: [], lastSeq: 0, more: false, serverNow: "2030-03-01T10:00:00Z" });
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

  it("rereads the state after a refusal, in case the screen was behind the server", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000 })));
    vi.mocked(bid).mockResolvedValue({ outcome: "rejected", reason: "reserve", lot: 1, serverNow: "x" });
    render(<AuctionView roomId="r1" />);

    await userEvent.click(await screen.findByTestId("bid-button"));

    expect((await screen.findByTestId("notice-rejected")).textContent).toContain("too little purse");
    expect(feed.live.resync).toHaveBeenCalled();
  });

  it("shows the bidLocked dock with the reason when the server names a squad lock", async () => {
    feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fb", nextBid: 400_000, bidLimits: [{ reason: "reserve", role: "gk" }] })));
    vi.mocked(getRoom).mockResolvedValue(room({ packLabels: { ...labels, restrictedCategories: [] } }));
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

  it("shows a locked button and the pause notice for the paused phase, and the adjourned card", async () => {
    feed.set(snap(live({ phase: "paused", heldMs: 9000, deadline: undefined })));
    render(<AuctionView roomId="r1" />);
    expect((await screen.findByTestId("notice-paused")).textContent).toContain("Commish hit pause");
    expect((screen.getByTestId("bid-button") as HTMLButtonElement).disabled).toBe(true);

    act(() => feed.set(snap(live({ phase: "adjourned", lotOpen: false }))));
    expect(await screen.findByTestId("adjourned-card")).toBeTruthy();
    expect((screen.getByTestId("bid-button") as HTMLButtonElement).textContent).toContain("Adjourned");
  });

  it("a pause in a gap says it paused between lots and shows no frozen lot clock (CR3-3)", async () => {
    feed.set(
      snap(live({ phase: "paused", lotOpen: false, heldMs: 3000, deadline: undefined, nextBid: undefined, currentBid: 400_000, leaderFranchiseId: "fb", lastResult: { lot: 1, sold: true, franchiseId: "fb", amount: 400_000 } })),
    );
    render(<AuctionView roomId="r1" />);

    const notice = (await screen.findByTestId("notice-paused")).textContent ?? "";
    expect(notice).toContain("Paused between lots");
    expect(notice).toContain("0:03");
    expect(notice).not.toContain("Timer frozen at");
    expect(screen.getByTestId("timer").textContent).toBe("0:00");
    expect(screen.getByTestId("timer-note").textContent).toBe("PAUSED");
    expect(screen.getByTestId("stamp-sold").textContent).toContain("Blue FC");
  });

  it("a pause on the open lot still shows the frozen lot clock", async () => {
    feed.set(snap(live({ phase: "paused", heldMs: 9000, deadline: undefined })));
    render(<AuctionView roomId="r1" />);
    expect((await screen.findByTestId("notice-paused")).textContent).toContain("Timer frozen at 0:09");
    expect(screen.getByTestId("timer").textContent).toBe("0:09");
  });

  describe("the Commissioner's skip and re-open notice", () => {
    const reopen = { seq: 4, kind: "lot_reopened", lot: 3, newLot: 12, reason: "Wrong paddle", franchiseId: null, amount: null };

    it("names the lot number the re-opened lot plays under, not the old one (CR3-4)", async () => {
      feed.set(snap(live({ lot: { ...lot, no: 12 } })));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("bid-button");
      act(() => feed.set(snap(live({ lastSeq: 4, lot: { ...lot, no: 12 } }), { adminEvent: reopen })));
      const text = (await screen.findByTestId("notice-admin")).textContent ?? "";
      expect(text).toContain("Lot 12 is back");
      expect(text).not.toContain("Lot 3");
    });

    it("is not hidden or cleared by the outbid toast (CR3-6)", async () => {
      feed.set(snap(live({ currentBid: 300_000, leaderFranchiseId: "fa", nextBid: 400_000 })));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("bid-button");

      act(() => feed.set(snap(live({ lastSeq: 5, currentBid: 400_000, leaderFranchiseId: "fb", nextBid: 500_000 }), { adminEvent: { ...reopen, seq: 5 } })));

      expect(await screen.findByTestId("notice-outbid")).toBeTruthy();
      expect(screen.getByTestId("notice-admin").textContent).toContain("Lot 12 is back");

      // Closing the outbid toast leaves the Commissioner's notice, and the other way round.
      await userEvent.click(within(screen.getByTestId("notice-outbid")).getByRole("button", { name: "Dismiss" }));
      expect(screen.queryByTestId("notice-outbid")).toBeNull();
      expect(screen.getByTestId("notice-admin")).toBeTruthy();
    });
  });

  it("says the adjourned auction stopped after the lots the server counts as closed, in either round", async () => {
    // A re-opened or accelerated lot carries number 12; nine lots have closed (CR2-4).
    feed.set(snap(live({ phase: "adjourned", lotOpen: true, lot: { ...live().lot!, no: 12 }, lotsClosed: 9 })));
    render(<AuctionView roomId="r1" />);
    expect((await screen.findByTestId("adjourned-card")).textContent).toContain("Adjourned after 9 lots.");
  });

  describe("the resume lobby for the Commissioner and the lot that plays next", () => {
    const next = { ...lot, no: 7, name: "Next Up" };
    const lobby = (over: Partial<AuctionState> = {}) =>
      live({ phase: "resume_lobby", heldMs: 9000, deadline: undefined, lotOpen: false, lot: { ...lot, no: 6 }, nextLot: next, liveAt: new Date(NOW + 102_000).toISOString(), resumeLobbySeconds: 180, lotsClosed: 6, ...over });

    it("names the next lot, not the one that just closed, in the notice (CR2-3)", async () => {
      feed.set(snap(lobby()));
      render(<AuctionView roomId="r1" />);
      expect((await screen.findByTestId("notice-resume-lobby")).textContent).toContain("Lot 7 goes live in");
    });

    it("gives the Commissioner Start now and Change time, and Start now goes live at once (CR2-2)", async () => {
      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: null }));
      vi.mocked(sendControl).mockResolvedValue({ outcome: "accepted", lot: 0, serverNow: "x" });
      feed.set(snap(lobby()));
      render(<AuctionView roomId="r1" />);
      expect(await screen.findByTestId("start-now")).toBeTruthy();
      expect(screen.getByTestId("change-resume-time")).toBeTruthy();
      expect(screen.queryByTestId("resume-now")).toBeNull();
      expect(screen.getByTestId("adjourned-card").textContent).toContain("Start now opens Lot 7 at once.");
      await userEvent.click(screen.getByTestId("start-now"));
      expect(vi.mocked(sendControl).mock.calls.at(-1)?.slice(0, 2)).toEqual(["r1", "start_now"]);
    });

    it("tells a manager no Commissioner buttons, and the adjourned card names the lobby length and the next lot (CR2-3, CR2-6)", async () => {
      feed.set(snap(lobby()));
      const { unmount } = render(<AuctionView roomId="r1" />);
      await screen.findByTestId("adjourned-card");
      expect(screen.queryByTestId("start-now")).toBeNull();
      unmount();

      vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: null }));
      feed.set(snap(lobby({ phase: "adjourned", liveAt: undefined, resumeAt: "2030-03-02T10:00:00Z" })));
      render(<AuctionView roomId="r1" />);
      expect((await screen.findByTestId("adjourned-card")).textContent).toContain("3-minute countdown, then Lot 7.");
    });
  });

  it("shows the Controls chip to the Commissioner only, in the live phases", async () => {
    feed.set(snap(live()));
    const { unmount } = render(<AuctionView roomId="r1" />);
    await screen.findByTestId("bid-button");
    expect(screen.queryByTestId("controls-chip")).toBeNull();
    unmount();

    vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: null }));
    render(<AuctionView roomId="r1" />);
    expect(await screen.findByTestId("controls-chip")).toBeTruthy();
  });

  it("counts down the resume lobby and keeps the lot locked, from the server's liveAt", async () => {
    const liveAt = new Date(Date.parse("2030-03-01T10:00:20Z") + 102_000).toISOString();
    feed.set(snap(live({ phase: "resume_lobby", heldMs: 9000, deadline: undefined, liveAt })));
    render(<AuctionView roomId="r1" />);
    expect(await screen.findByTestId("notice-resume-lobby")).toBeTruthy();
    expect(screen.getByTestId("adjourned-card")).toBeTruthy();
    expect((screen.getByTestId("bid-button") as HTMLButtonElement).textContent).toContain("Resuming soon");
  });

  describe("end of the main pool and the accelerated round", () => {
    const commishRoom = () => vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: null }));
    const unsoldCards = [
      { ...lot, no: 4, name: "Zed Zero" },
      { ...lot, no: 7, name: "Mo Most" },
    ];
    const done = (over: Partial<AuctionState> = {}) =>
      live({ phase: "main_done", lotOpen: false, lot: undefined, nextBid: undefined, deadline: undefined, unsold: [4, 7], unsoldPool: unsoldCards, lotCount: 9, ...over });
    const picking = (over: Partial<AuctionState> = {}) =>
      done({ phase: "nominating", openSlots: 2, waiting: ["fa", "fb"], pickCounts: [{ lot: 7, count: 1 }], ...over });

    it("shows a manager the main-done totals read-only, with no bid dock", async () => {
      feed.set(snap(done({ standings: [{ franchiseId: "fa", purse: 1, squad: [{ lot: 1, playerId: "p", price: 1 }] }, { franchiseId: "fb", purse: 1, squad: [] }] })));
      render(<AuctionView roomId="r1" />);
      expect(await screen.findByTestId("main-done-waiting")).toBeTruthy();
      expect(screen.getByTestId("main-done-sold").textContent).toBe("1");
      expect(screen.getByTestId("main-done-unsold").textContent).toBe("2");
      expect(screen.getAllByText("Main pool done")).toHaveLength(2);
      expect(screen.queryByTestId("open-round")).toBeNull();
      expect(screen.queryByTestId("bid-button")).toBeNull();
    });

    it("lets the Commissioner open the round or end the auction, and shows a refusal", async () => {
      commishRoom();
      feed.set(snap(done()));
      vi.mocked(sendControl).mockResolvedValueOnce({ outcome: "accepted", lot: 0, serverNow: "x" }).mockResolvedValueOnce({ outcome: "rejected", reason: "wrong_phase", lot: 0, serverNow: "x" });
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("open-round"));
      expect(vi.mocked(sendControl).mock.calls[0].slice(0, 2)).toEqual(["r1", "open_round"]);
      await waitFor(() => expect(feed.live.resync).toHaveBeenCalled());

      await user.click(screen.getByTestId("end-auction"));
      expect(vi.mocked(sendControl).mock.calls[1].slice(0, 2)).toEqual(["r1", "end_auction"]);
      expect((await screen.findByTestId("notice-rejected")).textContent).toContain("The auction moved on before that landed. Nothing changed.");
    });

    it("lets a manager pick up to the open slots, send them once, and then waits read-only", async () => {
      feed.set(snap(picking()));
      vi.mocked(sendPicks).mockResolvedValue({ outcome: "accepted", lot: 0, serverNow: "x" });
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      const rows = await screen.findAllByTestId("pick-row");
      expect(rows.map((r) => within(r).getByTestId("pick-name").textContent)).toEqual(["Mo Most", "Zed Zero"]);
      expect(within(rows[0]).getByTestId("pick-count").textContent).toBe("1 pick");
      expect((screen.getByTestId("send-picks") as HTMLButtonElement).disabled).toBe(true);

      await user.click(rows[0]);
      await user.click(rows[1]);
      await user.click(screen.getByTestId("send-picks"));
      expect(sendPicks).toHaveBeenCalledWith("r1", [7, 4], expect.any(String));

      act(() => feed.set(snap(picking({ waiting: ["fb"], myPicks: [7, 4] }))));
      expect((await screen.findByTestId("picks-in")).textContent).toContain("Waiting on 1 franchise.");
      expect(screen.getAllByTestId("pick-row")[0].getAttribute("aria-pressed")).toBe("true");
    });

    it("shows a full squad as done with nothing to pick, not as picks sent", async () => {
      feed.set(snap(picking({ openSlots: 0, waiting: ["fb"] })));
      render(<AuctionView roomId="r1" />);
      expect((await screen.findByTestId("no-slots")).textContent).toContain("Waiting on 1 franchise.");
      expect(screen.queryByTestId("picks-in")).toBeNull();
      expect(screen.queryByTestId("send-picks")).toBeNull();
      expect(screen.getByTestId("nom-mine").textContent).toBe("0 of 0");
    });

    it("restores my sent picks in a new tab and shows a refused send", async () => {
      feed.set(snap(picking({ waiting: ["fb"], myPicks: [4] })));
      render(<AuctionView roomId="r1" />);
      expect((await screen.findByTestId("picks-in")).textContent).toContain("Waiting on 1 franchise.");
      expect(screen.getByTestId("nom-mine").textContent).toBe("1 of 2");

      cleanup();
      feed.set(snap(picking({ openSlots: 1 })));
      vi.mocked(sendPicks).mockResolvedValue({ outcome: "rejected", reason: "over_slots", lot: 0, serverNow: "x" });
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click((await screen.findAllByTestId("pick-row"))[0]);
      await user.click(screen.getByTestId("send-picks"));
      expect((await screen.findByTestId("notice-rejected")).textContent).toContain("more than your open squad slots");
    });

    it("gives the Commissioner without a franchise End round and no picking", async () => {
      commishRoom();
      feed.set(snap(picking({ openSlots: undefined, waiting: ["fb"] })));
      vi.mocked(sendControl).mockResolvedValue({ outcome: "accepted", lot: 0, serverNow: "x" });
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      const end = await screen.findByTestId("end-round");
      expect(end.textContent).toContain("End round · play 1 lot");
      expect(screen.queryByTestId("send-picks")).toBeNull();
      expect(screen.queryByTestId("picks-in")).toBeNull();
      await user.click(end);
      expect(vi.mocked(sendControl).mock.calls[0].slice(0, 2)).toEqual(["r1", "end_round"]);
    });

    it("runs an accelerated lot as a normal lot, headed 'n of m' with the Picked by tag (mock 3y)", async () => {
      feed.set(
        snap(
          live({
            round: "accelerated",
            currentBid: 300_000,
            leaderFranchiseId: "fb",
            nextBid: 400_000,
            lot: { ...lot, no: 12 },
            accelPosition: 3,
            accelSize: 11,
            lotPicks: 3,
          }),
        ),
      );
      render(<AuctionView roomId="r1" />);
      expect(await screen.findByText("Accelerated round · 3 of 11")).toBeTruthy();
      expect(screen.getByTestId("picked-by").textContent).toBe("Picked by 3");
      expect(screen.getByTestId("bid-button").textContent).toContain("€0.4 M");
    });

    it("names the lot when the state carries no queue position, and shows no tag for a main lot", async () => {
      feed.set(snap(live({ round: "accelerated", lot: { ...lot, no: 12 } })));
      const { unmount } = render(<AuctionView roomId="r1" />);
      expect(await screen.findByText("Accelerated round · Lot 12")).toBeTruthy();
      expect(screen.queryByTestId("picked-by")).toBeNull();
      unmount();

      feed.set(snap(live()));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("lot-card");
      expect(screen.queryByTestId("picked-by")).toBeNull();
    });
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

describe("AuctionView squad chip and sheet", () => {
  const players = [
    { id: "p1", name: "Fake 1", role: "gk", tags: [], basePrice: 1, set: "S", country: "X", origin: "source" },
    { id: "p2", name: "Fake 2", role: "gk", tags: [], basePrice: 1, set: "S", country: "X", origin: "source" },
  ];
  const withSquad = (n: number, purse: number): AuctionState =>
    live({
      lastSeq: 3 + n,
      standings: [
        { franchiseId: "fa", purse, squad: players.slice(0, n).map((p, i) => ({ lot: i + 1, playerId: p.id, price: 5_000_000 })) },
        { franchiseId: "fb", purse: 100_000_000, squad: [] },
      ],
    });
  const squadRoom = () =>
    room({
      config: { ...config, roleMinimums: { gk: 2 } },
      packLabels: { ...labels, roles: [{ id: "gk", label: "Goalkeeper", short: "GK" }], restrictedCategories: [] },
    });

  beforeEach(() => {
    vi.mocked(getPool).mockResolvedValue({ players } as never);
    vi.mocked(getRoom).mockResolvedValue(squadRoom());
  });

  it("opens the viewer's own squad in a sheet built from the squad panel, and closes it", async () => {
    feed.set(snap(withSquad(1, 95_000_000)));
    const user = userEvent.setup();
    render(<AuctionView roomId="r1" />);

    await user.click(await screen.findByTestId("squad-chip"));

    const sheet = await screen.findByRole("dialog", { name: "My squad" });
    const body = await within(sheet).findByTestId("squad-sheet");
    expect(body.getAttribute("data-franchise")).toBe("fa");
    expect(within(sheet).getByRole("heading", { name: "Ace FC" })).toBeTruthy();
    expect(within(body).getByTestId("squad-purse").textContent).toBe("€95 M");
    expect(within(body).getByTestId("sheet-role-gk").textContent).toContain("1/2");
    expect(within(body).getByTestId("squad-player").textContent).toContain("Fake 1");

    await user.click(within(sheet).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog", { name: "My squad" })).toBeNull();
  });

  it("updates the open sheet when a hammer changes the state, with no reload", async () => {
    feed.set(snap(withSquad(1, 95_000_000)));
    const user = userEvent.setup();
    render(<AuctionView roomId="r1" />);
    await user.click(await screen.findByTestId("squad-chip"));
    await screen.findByTestId("squad-sheet");

    act(() => feed.set(snap(withSquad(2, 90_000_000))));

    await waitFor(() => expect(screen.getAllByTestId("squad-player")).toHaveLength(2));
    expect(screen.getByTestId("squad-purse").textContent).toBe("€90 M");
    expect(screen.getByTestId("sheet-role-gk").textContent).toContain("2/2");
    expect(getPool).toHaveBeenCalledTimes(1);
  });

  it("puts the chip in the Commissioner's row beside Controls, and gives no chip without a franchise", async () => {
    feed.set(snap(withSquad(0, 100_000_000)));
    vi.mocked(getRoom).mockResolvedValue({ ...squadRoom(), myRole: "commissioner" } as Room);
    const { unmount } = render(<AuctionView roomId="r1" />);
    const controls = await screen.findByTestId("controls-chip");
    expect(controls.parentElement?.contains(screen.getByTestId("squad-chip"))).toBe(true);
    unmount();

    vi.mocked(getRoom).mockResolvedValue({ ...squadRoom(), myRole: "commissioner", myFranchiseId: null } as Room);
    render(<AuctionView roomId="r1" />);
    await screen.findByTestId("controls-chip");
    expect(screen.queryByTestId("squad-chip")).toBeNull();
  });
});

describe("AuctionView chip row and panel sheets", () => {
  const order = () => screen.getAllByRole("button").map((b) => b.getAttribute("data-testid")).filter((id) => id?.endsWith("-chip"));

  it("shows Rivals, Bids and Squad to a franchise owner, in that order", async () => {
    feed.set(snap(live()));
    render(<AuctionView roomId="r1" />);
    await screen.findByTestId("squad-chip");
    expect(order()).toEqual(["rivals-chip", "feed-chip", "squad-chip"]);
  });

  it("puts Controls first for a Commissioner, and leaves Squad out when they have no franchise", async () => {
    feed.set(snap(live()));
    vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner" }));
    const { unmount } = render(<AuctionView roomId="r1" />);
    await screen.findByTestId("controls-chip");
    expect(order()).toEqual(["controls-chip", "rivals-chip", "feed-chip", "squad-chip"]);
    unmount();

    vi.mocked(getRoom).mockResolvedValue(room({ myRole: "commissioner", myFranchiseId: null }));
    render(<AuctionView roomId="r1" />);
    await screen.findByTestId("controls-chip");
    expect(order()).toEqual(["controls-chip", "rivals-chip", "feed-chip"]);
  });

  it("gives a member watching Rivals and Bids but no Squad chip", async () => {
    feed.set(snap(live()));
    vi.mocked(getRoom).mockResolvedValue(room({ myFranchiseId: null }));
    render(<AuctionView roomId="r1" />);
    await screen.findByTestId("rivals-chip");
    expect(order()).toEqual(["rivals-chip", "feed-chip"]);
  });

  it("opens the Rivals and Bids sheets from their chips and closes them", async () => {
    feed.set(snap(live()));
    const user = userEvent.setup();
    render(<AuctionView roomId="r1" />);

    await user.click(await screen.findByTestId("rivals-chip"));
    const rivals = await screen.findByRole("dialog", { name: "The room" });
    await user.click(within(rivals).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).toBeNull();

    await user.click(screen.getByTestId("feed-chip"));
    const bids = await screen.findByRole("dialog", { name: "Bids on Lot 1" });
    await user.click(within(bids).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

// The panels read the room's pack, never a sport: the stub football pack drives these.
describe("AuctionView panels (stub football pack)", () => {
  const players = [
    { id: "p1", name: "Fake 1", role: "gk", tags: ["foreign"], basePrice: 1, set: "S", country: "X", origin: "source" },
    { id: "p2", name: "Fake 2", role: "def", tags: [], basePrice: 1, set: "S", country: "X", origin: "source" },
  ];
  const footRoom = (over: Record<string, unknown> = {}) =>
    room({
      config: { ...config, squadMin: 3, squadMax: 18, roleMinimums: { gk: 1, def: 2 }, categoryQuotas: { cap: 2 } },
      packLabels: {
        ...labels,
        roles: [
          { id: "gk", label: "Goalkeeper", short: "GK" },
          { id: "def", label: "Defender", short: "DEF" },
        ],
        tags: [{ id: "foreign", label: "Foreign" }],
        restrictedCategories: [{ id: "cap", label: "Foreign players", tag: "foreign", max: 4 }],
      },
      ...over,
    });
  const ev = (seq: number, franchiseId: string, amount: number | null, over: Record<string, unknown> = {}) => ({
    seq,
    at: "2030-03-01T10:00:00Z",
    kind: amount === null ? "passed" : "bid_accepted",
    franchiseId,
    lot: 1,
    payload: amount === null ? {} : { amount },
    ...over,
  });
  const bidsState = (over: Partial<AuctionState> = {}) =>
    live({ currentBid: 500_000, leaderFranchiseId: "fb", nextBid: 600_000, lastSeq: 6, ...over });

  beforeEach(() => {
    vi.mocked(getRoom).mockResolvedValue(footRoom());
    vi.mocked(getPool).mockResolvedValue({ players } as never);
  });

  describe("Bids feed", () => {
    it("lists the open lot's bids and passes newest first, with a 'You' row and the opening line", async () => {
      const bids = [ev(4, "fa", 300_000), ev(5, "fb", 400_000), ev(6, "fb", 500_000), ev(7, "fa", null)];
      feed.set(snap(bidsState(), { bids }));
      render(<AuctionView roomId="r1" />);

      const card = await screen.findByTestId("feed-card");
      const rows = within(card).getAllByTestId("feed-row");
      expect(rows.map((r) => r.getAttribute("data-kind"))).toEqual(["pass", "bid", "bid", "bid"]);
      expect(rows[0].textContent).toContain("You");
      expect(rows[0].textContent).toContain("Passed");
      expect(rows[1].textContent).toContain("Blue FC");
      expect(rows[1].textContent).toContain("€0.5 M");
      expect(rows[1].textContent).toContain("now");
      expect(rows[3].textContent).toContain("€0.3 M");
      expect(within(card).getByTestId("feed-opened").textContent).toContain("Lot 1 opened at €0.3 M");
    });

    it("shows a new bid within one live update, and drops other lots' bids", async () => {
      feed.set(snap(bidsState(), { bids: [ev(5, "fb", 400_000)] }));
      render(<AuctionView roomId="r1" />);
      const card = await screen.findByTestId("feed-card");
      expect(within(card).getAllByTestId("feed-row")).toHaveLength(1);

      act(() => feed.set(snap(bidsState({ lastSeq: 7, currentBid: 600_000, leaderFranchiseId: "fa" }), { bids: [ev(5, "fb", 400_000), ev(7, "fa", 600_000)] })));

      await waitFor(() => expect(within(screen.getByTestId("feed-card")).getAllByTestId("feed-row")).toHaveLength(2));
      expect(within(screen.getByTestId("feed-card")).getAllByTestId("feed-row")[0].textContent).toContain("€0.6 M");

      act(() => feed.set(snap(bidsState({ lastSeq: 9, lot: { ...lot, no: 2 }, currentBid: 0, leaderFranchiseId: undefined }), { bids: [ev(5, "fb", 400_000), ev(7, "fa", 600_000)] })));
      await waitFor(() => expect(within(screen.getByTestId("feed-card")).queryAllByTestId("feed-row")).toHaveLength(0));
      expect(screen.getByTestId("feed-opened").textContent).toContain("Lot 2 opened at €0.3 M");
    });

    it("rebuilds the same rows in a new tab from the events page, and shows them in the Bids sheet", async () => {
      const events = [ev(5, "fb", 400_000), ev(6, "fb", 500_000)];
      vi.mocked(getEvents).mockResolvedValue({ events, lastSeq: 6, more: false, serverNow: "2030-03-01T10:00:00Z" } as never);
      feed.set(snap(bidsState(), { bids: [] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);

      const card = await screen.findByTestId("feed-card");
      await waitFor(() => expect(within(card).getAllByTestId("feed-row")).toHaveLength(2));
      expect(getEvents).toHaveBeenCalledWith("r1", 0);

      await user.click(screen.getByTestId("feed-chip"));
      const sheet = await screen.findByRole("dialog", { name: "Bids on Lot 1" });
      expect(within(sheet).getAllByTestId("feed-row").map((r) => r.textContent)).toEqual(within(card).getAllByTestId("feed-row").map((r) => r.textContent));
    });

    it("keeps the feed on screen for a Commissioner without a franchise", async () => {
      vi.mocked(getRoom).mockResolvedValue(footRoom({ myRole: "commissioner", myFranchiseId: null }));
      feed.set(snap(bidsState(), { bids: [ev(5, "fb", 400_000)] }));
      render(<AuctionView roomId="r1" />);
      const card = await screen.findByTestId("feed-card");
      expect(within(card).getAllByTestId("feed-row")[0].textContent).toContain("Blue FC");
    });
  });

  describe("Rivals sheet", () => {
    const squadState = (over: Partial<AuctionState> = {}) =>
      bidsState({
        standings: [
          { franchiseId: "fa", purse: 99_000_000, squad: [{ lot: 1, playerId: "p1", price: 1_000_000 }] },
          { franchiseId: "fb", purse: 100_000_000, squad: [] },
        ],
        ...over,
      });
    const open = async () => {
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("rivals-chip"));
      return within(await screen.findByRole("dialog", { name: "The room" }));
    };

    it("lists every franchise with manager, purse, squad, the pack's category cap and unmet needs", async () => {
      feed.set(snap(squadState(), { connected: ["fa", "fb"] }));
      const sheet = await open();
      await waitFor(() => expect(sheet.getAllByTestId("rival-row")).toHaveLength(2));
      const [a, b] = sheet.getAllByTestId("rival-row");
      expect(a.textContent).toContain("Ace FC");
      expect(a.textContent).toContain("Ann · needs 2 DEF");
      expect(a.textContent).toContain("€99 M");
      expect(a.textContent).toContain("1 pl");
      expect(within(a).getByTestId("rival-category").textContent).toBe("1/2 Foreign players");
      expect(b.textContent).toContain("Ben · needs 1 GK · 2 DEF");
      expect(within(b).getByTestId("rival-category").textContent).toBe("0/2 Foreign players");
    });

    it("marks the lot leader LEADING and a franchise that is not connected OFFLINE, and follows each change", async () => {
      feed.set(snap(squadState(), { connected: ["fa"] }));
      const sheet = await open();
      await waitFor(() => expect(sheet.getAllByTestId("rival-row")).toHaveLength(2));
      const row = (id: string) => sheet.getAllByTestId("rival-row").find((r) => r.getAttribute("data-franchise") === id)!;
      expect(row("fb").textContent).toContain("LEADING");
      expect(row("fb").textContent).toContain("OFFLINE");
      expect(row("fa").textContent).not.toContain("LEADING");
      expect(row("fa").textContent).not.toContain("OFFLINE");

      // A hammer moves the lead to Ace and a presence change brings Blue online.
      act(() => feed.set(snap(squadState({ lastSeq: 7, leaderFranchiseId: "fa" }), { connected: ["fa", "fb"] })));
      await waitFor(() => expect(row("fa").textContent).toContain("LEADING"));
      expect(row("fb").textContent).not.toContain("LEADING");
      expect(row("fb").textContent).not.toContain("OFFLINE");

      act(() =>
        feed.set(
          snap(
            squadState({
              lastSeq: 8,
              standings: [
                { franchiseId: "fa", purse: 97_000_000, squad: [{ lot: 1, playerId: "p1", price: 1_000_000 }, { lot: 2, playerId: "p2", price: 2_000_000 }] },
                { franchiseId: "fb", purse: 100_000_000, squad: [] },
              ],
            }),
            { connected: ["fa", "fb"] },
          ),
        ),
      );
      await waitFor(() => expect(row("fa").textContent).toContain("€97 M"));
      expect(row("fa").textContent).toContain("needs 1 DEF");
    });
  });

  describe("Rivals sheet after the hammer (CR-18)", () => {
    it("shows no LEADING marker once the lot has closed", async () => {
      feed.set(
        snap(
          bidsState({
            standings: [
              { franchiseId: "fa", purse: 99_000_000, squad: [{ lot: 1, playerId: "p1", price: 1_000_000 }] },
              { franchiseId: "fb", purse: 96_000_000, squad: [{ lot: 2, playerId: "p2", price: 4_000_000 }] },
            ],
            lotOpen: false,
            phase: "paused",
            heldMs: 3000,
            deadline: undefined,
            nextBid: undefined,
            leaderFranchiseId: "fb",
            lastResult: { lot: 2, sold: true, franchiseId: "fb", amount: 4_000_000 },
          }),
          { connected: ["fa", "fb"] },
        ),
      );
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("rivals-chip"));
      const sheet = within(await screen.findByRole("dialog", { name: "The room" }));
      await waitFor(() => expect(sheet.getAllByTestId("rival-row")).toHaveLength(2));
      for (const r of sheet.getAllByTestId("rival-row")) expect(r.textContent).not.toContain("LEADING");
    });
  });

  describe("pool read fails", () => {
    const failState = () =>
      bidsState({
        standings: [
          { franchiseId: "fa", purse: 99_000_000, squad: [{ lot: 1, playerId: "p1", price: 1_000_000 }] },
          { franchiseId: "fb", purse: 100_000_000, squad: [] },
        ],
      });

    it("shows a failed state, never zero counts, in the Rivals sheet and the Your needs card, and recovers on a retry", async () => {
      vi.mocked(getPool).mockRejectedValueOnce(new Error("down"));
      feed.set(snap(failState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("rivals-chip"));
      const sheet = within(await screen.findByRole("dialog", { name: "The room" }));
      const note = await sheet.findByTestId("rivals-failed");
      expect(note.textContent).toContain("Couldn't load the squads.");
      expect(sheet.queryAllByTestId("rival-row")).toHaveLength(0);
      expect(sheet.queryByTestId("rivals-loading")).toBeNull();

      await user.click(sheet.getByRole("button", { name: "Retry" }));
      await waitFor(() => expect(sheet.getAllByTestId("rival-row")).toHaveLength(2));
      expect(sheet.queryByTestId("rivals-failed")).toBeNull();
    });

    it("keeps the Your needs card's Max bid and squad with the load-error Note in place of the role tiles, never a skeleton for good", async () => {
      vi.mocked(getPool).mockRejectedValue(new Error("down"));
      feed.set(snap(failState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      const failed = await screen.findByTestId("needs-failed");
      const card = screen.getByTestId("needs-card");
      expect(card.contains(failed)).toBe(true);
      expect(within(card).getByTestId("max-bid").textContent).toMatch(/^Max bid /);
      expect(within(card).getByTestId("needs-squad").textContent).toBe("1 / 3–18");
      expect(within(card).queryByTestId("need-gk")).toBeNull();
      expect(within(card).getByTestId("needs-restricted").querySelector('[data-testid="pending"]')).not.toBeNull();
      expect(failed.textContent).toContain("Couldn't load the player list.");
      expect(card.getAttribute("aria-busy")).toBeNull();
      expect(document.querySelector('[aria-busy="true"]')).toBeNull();

      vi.mocked(getPool).mockResolvedValue({ players: [] } as never);
      await user.click(within(failed).getByRole("button", { name: "Retry" }));
      await screen.findByTestId("need-gk");
      expect(screen.queryByTestId("needs-failed")).toBeNull();
    });

    it("keeps the failed Your needs card openable: the card opens the squad sheet, Retry reads the pool again and does not", async () => {
      vi.mocked(getPool).mockRejectedValue(new Error("down"));
      feed.set(snap(failState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      const failed = await screen.findByTestId("needs-failed");
      const calls = vi.mocked(getPool).mock.calls.length;

      await user.click(within(failed).getByRole("button", { name: "Retry" }));
      await waitFor(() => expect(vi.mocked(getPool).mock.calls.length).toBeGreaterThan(calls));
      expect(screen.queryByRole("dialog", { name: "My squad" })).toBeNull();

      await user.click(within(screen.getByTestId("needs-card")).getByTestId("max-bid"));
      expect(await screen.findByRole("dialog", { name: "My squad" })).toBeTruthy();
    });

    it("opens the squad sheet from the failed card with the keyboard", async () => {
      vi.mocked(getPool).mockRejectedValue(new Error("down"));
      feed.set(snap(failState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("needs-failed");
      const open = within(screen.getByTestId("needs-card")).getAllByRole("button").filter((b) => b.textContent !== "Retry");
      expect(open.length).toBeGreaterThan(0);
      open[0].focus();
      await user.keyboard("{Enter}");
      expect(await screen.findByRole("dialog", { name: "My squad" })).toBeTruthy();
    });

    it("keeps the squad sheet's purse, count and names from the state, with the failure as a notice and a retry", async () => {
      vi.mocked(getPool).mockRejectedValue(new Error("down"));
      feed.set(snap(failState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("squad-chip"));
      const sheet = within(await screen.findByRole("dialog", { name: "My squad" }));
      const body = await sheet.findByTestId("squad-sheet");
      expect(within(body).getByTestId("squad-purse").textContent).toBe("€99 M");
      expect(sheet.getByTestId("squad-failed").textContent).toContain("Couldn't load the player list.");
      expect(within(body).getByTestId("sheet-role-gk").querySelector('[data-testid="pending"]')).not.toBeNull();
      expect(within(body).queryByTestId("squad-slots")).toBeNull();
      expect(body.getAttribute("aria-busy")).toBeNull();
      // CR-22: no aria-hidden bars for a name the failed read leaves unknown, no "after the list loads", labelled placeholders.
      expect(within(body).queryByTestId("pending-name")).toBeNull();
      expect(within(body).getByTestId("squad-player").textContent).toContain("Unknown player");
      expect(within(body).queryByTestId("squad-slots-pending")).toBeNull();
      expect(body.textContent).not.toContain("after the list loads");
      expect(within(body).getByTestId("squad-player").querySelector('[data-testid="pending"]')?.getAttribute("aria-label")).toBe("Role unknown");

      vi.mocked(getPool).mockResolvedValue({ players: [] } as never);
      await user.click(sheet.getByRole("button", { name: "Retry" }));
      await waitFor(() => expect(sheet.queryByTestId("squad-failed")).toBeNull());
      expect(sheet.getByTestId("squad-sheet")).toBeTruthy();
    });
  });

  describe("pool still loading", () => {
    const loadingState = () =>
      bidsState({
        bidLimits: [{ kind: "purse", above: 9_000_000 }] as never,
        standings: [
          { franchiseId: "fa", purse: 99_000_000, squad: [{ lot: 1, playerId: "p1", price: 1_000_000 }] },
          { franchiseId: "fb", purse: 100_000_000, squad: [] },
        ],
      });

    it("shows the Your needs figures the state holds and a placeholder for the pool's, never a zero (CR-15)", async () => {
      vi.mocked(getPool).mockReturnValue(new Promise(() => {}));
      feed.set(snap(loadingState(), { connected: ["fa", "fb"] }));
      render(<AuctionView roomId="r1" />);
      const card = await screen.findByTestId("needs-card");
      expect(within(card).getByTestId("needs-squad").textContent).toBe("1 / 3–18");
      expect(within(card).getByTestId("max-bid").textContent).toBe("Max bid €9 M");
      expect(within(card).getByTestId("need-gk").textContent).not.toMatch(/0\//);
      expect(within(card).getByTestId("need-gk").querySelector('[data-testid="pending"]')).not.toBeNull();
      expect(within(card).getByTestId("needs-restricted").querySelector('[data-testid="pending"]')).not.toBeNull();
      expect(within(card).getByTestId("needs-restricted").textContent).toBe("/2");
    });

    it("marks the Your needs card busy while only a restricted count waits on the pool, in a room with no role minimums (CR-31)", async () => {
      vi.mocked(getPool).mockReturnValue(new Promise(() => {}));
      vi.mocked(getRoom).mockResolvedValue(
        room({
          config: { ...config, squadMin: 3, squadMax: 18, roleMinimums: {}, categoryQuotas: { cap: 2 } },
          packLabels: { ...labels, restrictedCategories: [{ id: "cap", label: "Foreign players", tag: "foreign", max: 4 }] },
        }),
      );
      feed.set(snap(loadingState(), { connected: ["fa", "fb"] }));
      render(<AuctionView roomId="r1" />);
      const card = await screen.findByTestId("needs-card");
      expect(within(card).queryByTestId("need-gk")).toBeNull();
      expect(within(card).getByTestId("needs-restricted").querySelector('[data-testid="pending"]')).not.toBeNull();
      expect(card.getAttribute("aria-busy")).toBe("true");
    });

    it("labels every pending figure for a screen reader, so a bare /min is never read alone (CR-32)", async () => {
      vi.mocked(getPool).mockReturnValue(new Promise(() => {}));
      feed.set(snap(loadingState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      const card = await screen.findByTestId("needs-card");
      for (const el of card.querySelectorAll('[data-testid="pending"]')) {
        expect(el.getAttribute("role")).toBe("img");
        expect(el.getAttribute("aria-label")).toBe("Loading");
        expect(el.getAttribute("aria-hidden")).toBeNull();
      }
      await user.click(await screen.findByTestId("squad-chip"));
      const sheet = within(await screen.findByRole("dialog", { name: "My squad" }));
      const body = within(await sheet.findByTestId("squad-sheet"));
      const figures = body.getByTestId("sheet-role-gk").querySelectorAll('[data-testid="pending"]');
      expect(figures.length).toBeGreaterThan(0);
      for (const el of figures) expect(el.getAttribute("aria-label")).toBe("Loading");
      for (const el of body.getByTestId("squad-restricted-cap").querySelectorAll('[data-testid="pending"]')) expect(el.getAttribute("aria-label")).toBe("Loading");
    });

    it("shows three skeleton rows in the Rivals sheet while the pool loads", async () => {
      vi.mocked(getPool).mockReturnValue(new Promise(() => {}));
      feed.set(snap(loadingState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("rivals-chip"));
      const sheet = within(await screen.findByRole("dialog", { name: "The room" }));
      const loading = await sheet.findByTestId("rivals-loading");
      expect(loading.getAttribute("aria-busy")).toBe("true");
      expect(loading.children).toHaveLength(3);
    });

    it("shows the squad sheet's purse and count from the state and a placeholder for role and category counts (CR-15)", async () => {
      vi.mocked(getPool).mockReturnValue(new Promise(() => {}));
      feed.set(snap(loadingState(), { connected: ["fa", "fb"] }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("squad-chip"));
      const sheet = within(await screen.findByRole("dialog", { name: "My squad" }));
      const body = within(await sheet.findByTestId("squad-sheet"));
      expect(body.getByTestId("squad-purse").textContent).toBe("€99 M");
      expect(body.getByTestId("squad-count").textContent).toContain("1");
      expect(body.getByTestId("sheet-role-gk").textContent).not.toMatch(/0/);
      expect(body.getByTestId("sheet-role-gk").querySelector('[data-testid="pending"]')).not.toBeNull();
      expect(body.getByTestId("squad-restricted-cap").querySelector('[data-testid="pending"]')).not.toBeNull();
      expect(body.queryByTestId("squad-slots")).toBeNull();
      expect(body.getByTestId("squad-slots-pending")).toBeTruthy();
      expect(body.getByTestId("sheet-role-gk").getAttribute("style")).toBeNull();
    });
  });

  describe("while the viewer's own socket is reconnecting", () => {
    it("claims no franchise is OFFLINE, since the presence list is stale", async () => {
      feed.set(snap(bidsState(), { connected: ["fa"], connection: "reconnecting", synced: false, frozenLeft: 17_000 }));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("rivals-chip"));
      const sheet = within(await screen.findByRole("dialog", { name: "The room" }));
      await waitFor(() => expect(sheet.queryAllByTestId("rival-row").length).toBeGreaterThan(0));
      expect(sheet.queryByText("OFFLINE")).toBeNull();
    });
  });

  describe("clock ticks (CR-26)", () => {
    const calls = () => [vi.mocked(feedView).mock.calls.length, vi.mocked(rivalsView).mock.calls.length, vi.mocked(needsView).mock.calls.length];

    it("does not rebuild the Bids, Rivals or needs views on a tick when nothing they read changed", async () => {
      feed.set(snap(bidsState(), { bids: [ev(4, "fa", 300_000), ev(5, "fb", 400_000)], connected: ["fa", "fb"] }));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("feed-card");
      await waitFor(() => expect(within(screen.getByTestId("needs-card")).getByTestId("need-gk")).toBeTruthy());
      await act(async () => {});
      const before = calls();
      expect(before.every((n) => n > 0)).toBe(true);

      for (let i = 0; i < 10; i++) act(() => feed.tick());
      expect(calls()).toEqual(before);
    });

    it("still ages the feed rows as the clock moves, once a second", async () => {
      feed.set(snap(bidsState(), { bids: [ev(4, "fa", 300_000)] }));
      render(<AuctionView roomId="r1" />);
      const age = async () => within(await screen.findByTestId("feed-card")).getAllByTestId("feed-row")[0].textContent;
      expect(await age()).toContain("now");

      feed.now = NOW + 12_000;
      act(() => feed.tick());
      expect(await age()).toContain("12s");
    });

    it("rebuilds the views when what they read does change", async () => {
      feed.set(snap(bidsState(), { bids: [ev(4, "fa", 300_000)], connected: ["fa"] }));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("feed-card");
      await act(async () => {});
      const before = calls();
      act(() => feed.set(snap(bidsState({ lastSeq: 7, currentBid: 600_000, leaderFranchiseId: "fa", nextBid: 700_000 }), { bids: [ev(4, "fa", 300_000), ev(7, "fa", 600_000)], connected: ["fa", "fb"] })));
      await waitFor(() => expect(within(screen.getByTestId("feed-card")).getAllByTestId("feed-row")).toHaveLength(2));
      const after = calls();
      expect(after[0]).toBeGreaterThan(before[0]);
      expect(after[1]).toBeGreaterThan(before[1]);
      expect(after[2]).toBeGreaterThan(before[2]);
    });
  });

  describe("Bids card", () => {
    it("is a button with no label overriding its bids, holding no block elements", async () => {
      feed.set(snap(bidsState(), { bids: [ev(5, "fb", 400_000)] }));
      render(<AuctionView roomId="r1" />);
      const card = await screen.findByTestId("feed-card");
      expect(card.getAttribute("aria-label")).toBeNull();
      expect(card.querySelector("div")).toBeNull();
      expect(card.textContent).toContain("Blue FC");
    });
  });

  describe("Your needs card", () => {
    const needsState = (limits: AuctionState["bidLimits"]) =>
      bidsState({
        bidLimits: limits,
        standings: [
          { franchiseId: "fa", purse: 99_000_000, squad: [{ lot: 1, playerId: "p1", price: 1_000_000 }] },
          { franchiseId: "fb", purse: 100_000_000, squad: [] },
        ],
      });

    it("shows have/min per pack role, the squad range, the category count and the lowest limit as Max bid", async () => {
      feed.set(snap(needsState([{ kind: "purse", above: 9_000_000 }, { kind: "reserve", above: 4_000_000 }] as never)));
      render(<AuctionView roomId="r1" />);

      const card = await screen.findByTestId("needs-card");
      await waitFor(() => expect(within(card).getByTestId("need-gk").textContent).toContain("1/1"));
      expect(within(card).getByTestId("need-gk").getAttribute("data-tone")).toBe("met");
      expect(within(card).getByTestId("need-gk").textContent).toContain("GK");
      expect(within(card).getByTestId("need-def").textContent).toContain("0/2");
      expect(within(card).getByTestId("need-def").getAttribute("data-tone")).toBe("empty");
      expect(within(card).getByTestId("needs-squad").textContent).toBe("1 / 3–18");
      expect(within(card).getByTestId("needs-restricted").textContent).toBe("1/2");
      expect(within(card).getByTestId("max-bid").textContent).toBe("Max bid €4 M");
    });

    it("says no bid is possible when a limit has no `above`", async () => {
      feed.set(snap(needsState([{ kind: "squad_full" }] as never)));
      render(<AuctionView roomId="r1" />);
      await screen.findByTestId("needs-card");
      expect(screen.getByTestId("max-bid").textContent).toBe("No bid possible");
    });

    it("opens the squad sheet, and follows the live state", async () => {
      feed.set(snap(needsState([{ kind: "purse", above: 9_000_000 }] as never)));
      const user = userEvent.setup();
      render(<AuctionView roomId="r1" />);
      await user.click(await screen.findByTestId("needs-card"));
      expect(await screen.findByRole("dialog", { name: "My squad" })).toBeTruthy();
    });

    it("is not drawn for a Commissioner without a franchise or a member watching, who still get the chips and feed", async () => {
      for (const over of [{ myRole: "commissioner", myFranchiseId: null }, { myFranchiseId: null }]) {
        vi.mocked(getRoom).mockResolvedValue(footRoom(over));
        feed.set(snap(needsState([])));
        const { unmount } = render(<AuctionView roomId="r1" />);
        await screen.findByTestId("feed-card");
        expect(screen.queryByTestId("needs-card")).toBeNull();
        expect(screen.queryByTestId("squad-chip")).toBeNull();
        expect(screen.getByTestId("rivals-chip")).toBeTruthy();
        expect(screen.getByTestId("feed-chip")).toBeTruthy();
        unmount();
      }
    });
  });
});
