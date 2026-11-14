// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

import type { Member, Room } from "../../../../api/client";
import { buildView, type ViewInput } from "../../../../auction/view";
import { BidDock } from "./bid-dock";
import { Lobby } from "./lobby";
import { LotCard } from "./lot-card";
import { AdjournedCard, ClosedCard, Notices } from "./notices";

afterEach(cleanup);

// The stub football pack's words and money: nothing in this file is a cricket term.
const labels = {
  packId: "stub-football",
  packName: "Stub Football",
  roles: [
    { id: "gk", label: "Goalkeeper" },
    { id: "def", label: "Defender" },
  ],
  tags: [{ id: "foreign", label: "Foreign" }],
  restrictedCategories: [{ id: "foreign-cap", label: "Foreign players", tag: "foreign", max: 4 }],
  stats: [],
  terminology: {},
} as unknown as Room["packLabels"];
const config = {
  name: "Friends",
  purse: 100_000_000,
  currencyCode: "EUR",
  displayUnit: { label: "M", divisor: 1_000_000 },
  incrementLadder: [{ from: 0, step: 100_000 }],
  squadMin: 11,
  squadMax: 18,
  roleMinimums: { gk: 1, def: 3 },
  categoryQuotas: { "foreign-cap": 4 },
  openSeconds: 30,
  bidSeconds: 15,
} as unknown as Room["config"];
const lot = { no: 3, playerId: "p", name: "Fake 3", country: "Spain", role: "gk", tags: ["foreign"], set: "Keepers", position: 2, basePrice: 300_000 };

describe("LotCard", () => {
  it("shows role, tags, name, country and base price from the pack's labels and money", () => {
    render(<LotCard lot={lot} labels={labels} config={config} />);
    const card = within(screen.getByTestId("lot-card"));
    expect(card.getByText("Goalkeeper")).toBeTruthy();
    expect(card.getByText("Foreign")).toBeTruthy();
    expect(card.getByText("Fake 3")).toBeTruthy();
    expect(card.getByText("Spain")).toBeTruthy();
    expect(card.getByText("€0.3 M")).toBeTruthy();
    expect(screen.getByTestId("lot-card").textContent).not.toMatch(/bat|bowl|wicket|cricket|overseas|₹|cr\b/i);
  });

  it("leaves no gap when the photo and last-season points are missing, and shows them when present", () => {
    const { container, rerender } = render(<LotCard lot={lot} labels={labels} config={config} />);
    expect(container.querySelector('[role="img"]')).toBeNull();
    expect(screen.queryByText("LAST SEASON")).toBeNull();
    expect(screen.getAllByText("BASE")).toHaveLength(1);

    rerender(<LotCard lot={lot} labels={labels} config={config} photoUrl="/p.png" lastSeasonPoints={410} />);
    expect(container.querySelector('[role="img"]')).not.toBeNull();
    expect(screen.getByText("410 pts")).toBeTruthy();
    expect(screen.getByText("LOT 3")).toBeTruthy();
  });

  it("stamps SOLD with the buyer and amount, and UNSOLD with the note", () => {
    const who = { name: "Club 2", monogram: "C2", colour: "sky" };
    const { rerender } = render(<LotCard lot={lot} labels={labels} config={config} result={{ sold: true, who, amount: 400_000, youWon: false }} />);
    expect(screen.getByTestId("stamp-sold").textContent).toContain("SOLD");
    expect(screen.getByTestId("stamp-sold").textContent).toContain("Club 2");
    expect(screen.getByTestId("stamp-sold").textContent).toContain("€0.4 M");
    rerender(<LotCard lot={lot} labels={labels} config={config} result={{ sold: false, who: null, amount: 0, youWon: false }} />);
    expect(screen.getByTestId("stamp-unsold").textContent).toContain("Nobody blinked");
  });
});

function view(over: Partial<ViewInput> = {}, st: Record<string, unknown> = {}) {
  return buildView({
    state: {
      phase: "live",
      startsAt: "2030-01-01T00:00:00Z",
      serverNow: "2030-01-01T00:00:00Z",
      lastSeq: 4,
      lotCount: 6,
      lot,
      lotOpen: true,
      currentBid: 300_000,
      leaderFranchiseId: "rival",
      passedBy: [],
      standings: [{ franchiseId: "me", purse: 90_000_000, squad: [] }],
      unsold: [],
      connected: [],
      ready: [],
      nextBid: 400_000,
      deadline: "2030-01-01T00:00:10Z",
      config: { openSeconds: 30, bidSeconds: 15, gapSeconds: 5, ladder: [] },
      ...st,
    } as never,
    connection: "live",
    synced: true,
    me: "me",
    iBidOnLot: false,
    serverNow: Date.parse("2030-01-01T00:00:00Z"),
    frozenLeft: null,
    sending: false,
    ...over,
  });
}

describe("BidDock", () => {
  it("shows one 72 px bid button with the server's next bid and the step, beside an inert lifeline slot", async () => {
    const onBid = vi.fn();
    render(<BidDock view={view()} config={config} labels={labels} onBid={onBid} />);
    const button = screen.getByTestId("bid-button");
    expect(button.getAttribute("data-state")).toBe("ready");
    expect(button.textContent).toContain("RAISE PADDLE");
    expect(button.textContent).toContain("€0.4 M");
    expect(button.textContent).toContain("+€0.1 M");
    const slot = screen.getByTestId("lifeline-slot");
    expect(slot.hasAttribute("inert")).toBe(true);
    expect(slot.contains(button)).toBe(false);
    await userEvent.click(button);
    expect(onBid).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["reconnecting", { connection: "reconnecting" as const, synced: false }, "Reconnecting…"],
    ["syncing", { synced: false }, "Syncing…"],
    ["leading", { me: "rival" }, "You're on top"],
    ["sending", { sending: true }, "Raising paddle…"],
  ])("keeps the same single button, disabled, while %s", async (state, over, words) => {
    const onBid = vi.fn();
    render(<BidDock view={view(over)} config={config} labels={labels} onBid={onBid} />);
    const button = screen.getByTestId("bid-button") as HTMLButtonElement;
    expect(button.getAttribute("data-state")).toBe(state);
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain(words);
    await userEvent.click(button);
    expect(onBid).not.toHaveBeenCalled();
    expect(screen.getAllByTestId("bid-button")).toHaveLength(1);
  });

  it.each([
    ["paused", { phase: "paused" }, "Auction paused"],
    ["adjourned", { phase: "adjourned", lotOpen: false }, "Adjourned"],
    ["ended", { phase: "ended", lotOpen: false }, "Auction ended"],
    ["locked", { phase: "mystery" }, "Bidding's locked"],
    ["resolved", { lotOpen: false, lastResult: { lot: 3, sold: true, franchiseId: "rival", amount: 300_000 } }, "Next lot coming up…"],
  ])("locks for %s", (state, st, words) => {
    render(<BidDock view={view({}, st)} config={config} labels={labels} onBid={() => {}} />);
    const button = screen.getByTestId("bid-button") as HTMLButtonElement;
    expect(button.getAttribute("data-state")).toBe(state);
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain(words);
  });

  it.each([
    ["squad_full", { reason: "squad_full" }, "Your squad is full."],
    ["quota", { reason: "quota", category: "foreign-cap" }, "You're at the cap of 4 for Foreign players."],
    ["reserve with a role", { reason: "reserve", role: "gk" }, "short on purse to fill your Goalkeeper minimum at base price."],
    ["reserve for the squad minimum", { reason: "reserve" }, "short on purse to fill your squad minimum at base price."],
  ])("shows the locked box for %s: the next bid and the reason in the pack's words", async (_n, lock, words) => {
    const onBid = vi.fn();
    render(<BidDock view={view({}, { bidLock: lock })} config={config} labels={labels} onBid={onBid} />);
    const button = screen.getByTestId("bid-button") as HTMLButtonElement;
    expect(button.getAttribute("data-state")).toBe("bid_locked");
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain("Can't bid €0.4 M");
    expect(button.textContent).toContain(words);
    expect(screen.getAllByTestId("bid-button")).toHaveLength(1);
    expect(screen.getByTestId("lifeline-slot")).toBeTruthy();
    await userEvent.click(button);
    expect(onBid).not.toHaveBeenCalled();
  });

  it("names no sport in the locked reason", () => {
    render(<BidDock view={view({}, { bidLock: { reason: "reserve", role: "def" } })} config={config} labels={labels} onBid={() => {}} />);
    expect(screen.getByTestId("bid-button").textContent).toContain("Defender");
    expect(screen.getByTestId("bid-button").textContent).not.toMatch(/bat|bowl|wicket|cricket|overseas|₹|cr\b/i);
  });

  it("gives a Commissioner without a franchise no button and says why", () => {
    render(<BidDock view={view({ me: null })} config={config} labels={labels} onBid={() => {}} />);
    expect(screen.queryByTestId("bid-button")).toBeNull();
    expect(screen.queryByTestId("lifeline-slot")).toBeNull();
    expect(screen.getByTestId("bid-observer").textContent).toContain("You lead this room without a franchise");
  });
});

describe("Notices", () => {
  it("shows the reconnecting notice with a Retry that calls back", async () => {
    const onRetry = vi.fn();
    render(<Notices reconnecting paused={false} pausedLeftMs={null} toast={null} onRetry={onRetry} onDismiss={() => {}} />);
    const n = screen.getByTestId("notice-reconnecting");
    expect(n.textContent).toContain("Bidding's locked until you're back. Nothing is lost.");
    await userEvent.click(within(n).getByRole("button", { name: "Retry" }));
    expect(onRetry).toHaveBeenCalled();
  });

  it("shows pause with the frozen time, outbid with who and how much, and a refusal with the server's words", async () => {
    const onDismiss = vi.fn();
    const { rerender } = render(<Notices reconnecting={false} paused pausedLeftMs={9000} toast={null} onRetry={() => {}} onDismiss={onDismiss} />);
    expect(screen.getByTestId("notice-paused").textContent).toContain("Timer frozen at 0:09");

    rerender(
      <Notices reconnecting={false} paused={false} pausedLeftMs={null} toast={{ kind: "outbid", who: { name: "Club 2", monogram: "C2", colour: "sky" }, amount: "€0.5 M" }} onRetry={() => {}} onDismiss={onDismiss} />,
    );
    expect(screen.getByTestId("notice-outbid").textContent).toContain("Outbid!");
    expect(screen.getByTestId("notice-outbid").textContent).toContain("Club 2 went €0.5 M. Rude.");

    rerender(
      <Notices reconnecting={false} paused={false} pausedLeftMs={null} toast={{ kind: "rejected", notice: { title: "Bid bounced", body: "That bid is more than your purse allows. Nothing changed." } }} onRetry={() => {}} onDismiss={onDismiss} />,
    );
    expect(screen.getByTestId("notice-rejected").textContent).toContain("Nothing changed.");
    await userEvent.click(screen.getByRole("button", { name: "Dismiss" }));
    expect(onDismiss).toHaveBeenCalled();
  });

  it("renders nothing when there is nothing to say", () => {
    const { container } = render(<Notices reconnecting={false} paused={false} pausedLeftMs={null} toast={null} onRetry={() => {}} onDismiss={() => {}} />);
    expect(container.firstChild).toBeNull();
  });

  it("adjourned says where it stopped, and closed links back to the room", () => {
    render(<AdjournedCard lotsDone={86} squad={4} purse="€12 M" resumes={null} />);
    expect(screen.getByTestId("adjourned-card").textContent).toContain("Adjourned after Lot 86");
    cleanup();
    render(<ClosedCard roomHref="/rooms/r1" />);
    expect(screen.getByRole("link", { name: /Back to room/ }).getAttribute("href")).toBe("/rooms/r1");
  });
});

const franchise = (id: string, name: string, colour: string) => ({ id, ownerUserId: `u-${id}`, name, colour, monogram: name.slice(0, 2).toUpperCase() });
const members = [
  { userId: "u-a", displayName: "Ann", avatar: "p", role: "manager", franchise: franchise("fa", "Ace FC", "crimson") },
  { userId: "u-b", displayName: "Ben", avatar: "p", role: "manager", franchise: franchise("fb", "Blue FC", "sky") },
  { userId: "u-c", displayName: "Cy", avatar: "p", role: "commissioner" },
] as unknown as Member[];
const room = (over: Record<string, unknown> = {}) =>
  ({ id: "r1", state: "auction_scheduled", myRole: "manager", myFranchiseId: "fa", commissioner: { id: "u-c", displayName: "Cy" }, config, packLabels: labels, ...over }) as unknown as Room;

function lobby(over: Partial<React.ComponentProps<typeof Lobby>> = {}) {
  return render(
    <Lobby
      roomId="r1"
      room={room()}
      members={members}
      startsAt="2030-03-01T10:00:00Z"
      untilStartMs={(2 * 3600 + 14 * 60 + 36) * 1000}
      connected={["fa"]}
      ready={[]}
      offline={false}
      lastSeen={null}
      busy={false}
      onToggleReady={() => {}}
      onRetry={() => {}}
      {...over}
    />,
  );
}

describe("Lobby", () => {
  it("shows the timer rule from the server's auction config, and no invented numbers without it (review 7)", () => {
    const { unmount } = lobby({ timer: { openSeconds: 45, bidSeconds: 12 } });
    expect(screen.getByText("45s open · 12s per bid")).toBeTruthy();
    unmount();
    lobby();
    expect(screen.queryByText("TIMER")).toBeNull();
  });

  it("shows one tile per restricted category with the pack's label and the room's cap (Room `lobby`)", () => {
    lobby();
    const tile = screen.getByText("FOREIGN PLAYERS").parentElement as HTMLElement;
    expect(tile.textContent).toBe("FOREIGN PLAYERS≤ 4");
  });

  it("shows the room's own cap, not the pack's, and no tile for a category the room does not cap", () => {
    const { unmount } = lobby({ room: room({ config: { ...config, categoryQuotas: { "foreign-cap": 2 } } }) });
    expect((screen.getByText("FOREIGN PLAYERS").parentElement as HTMLElement).textContent).toContain("≤ 2");
    unmount();
    lobby({ room: room({ config: { ...config, categoryQuotas: {} } }) });
    expect(screen.queryByText("FOREIGN PLAYERS")).toBeNull();
  });

  it("shows the countdown, a status per franchise and an I'm ready button that only asks", async () => {
    const onToggleReady = vi.fn();
    lobby({ onToggleReady, ready: ["fb"] });
    expect(screen.getByTestId("lobby-countdown").textContent).toBe("02:14:36");
    const rows = screen.getAllByTestId("lobby-row");
    expect(rows.map((r) => r.getAttribute("data-status"))).toEqual(["IN LOBBY", "READY"]);
    expect(screen.getByTestId("lobby-count").textContent).toBe("1/2 ready");
    const button = screen.getByTestId("ready-button");
    expect(button.textContent).toContain("I'm ready");
    await userEvent.click(button);
    expect(onToggleReady).toHaveBeenCalledTimes(1);
    // Nothing changed locally: still not ready until the server says so.
    expect(button.getAttribute("aria-pressed")).toBe("false");
  });

  it("renders my ready state from the server's list, and the pack's role minimums", () => {
    lobby({ ready: ["fa"] });
    expect(screen.getByTestId("ready-button").textContent).toContain("You're ready. Paddle up.");
    expect(screen.getByText("1 Goalkeeper · 3 Defender")).toBeTruthy();
    expect(screen.getByText("€100 M")).toBeTruthy();
  });

  it("counts past the start as +mm:ss with the past-start banner (lobbyLate)", () => {
    lobby({ untilStartMs: -(6 * 60 + 12) * 1000 });
    expect(screen.getByTestId("lobby-countdown").textContent).toBe("+06:12");
    expect(screen.getByTestId("lobby-late").textContent).toContain("Past the start time");
    expect(screen.getByText("PAST START TIME")).toBeTruthy();
  });

  it("locks ready, freezes the countdown and says so when offline (lobbyOffline)", async () => {
    const onToggleReady = vi.fn();
    lobby({ offline: true, onToggleReady, lastSeen: new Date("2030-03-01T09:41:00") });
    expect(screen.getByTestId("lobby-offline").textContent).toContain("Ready is locked until you're back");
    const button = screen.getByTestId("ready-button") as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain("Reconnecting…");
    expect(screen.getByText("WAITING")).toBeTruthy();
    expect(screen.getByTestId("lobby-count").textContent).toMatch(/^last seen /);
    await userEvent.click(button);
    expect(onToggleReady).not.toHaveBeenCalled();
  });

  it("gives a Commissioner without a franchise no ready button and a note (lobbyAdmin)", () => {
    lobby({ room: room({ myRole: "commissioner", myFranchiseId: null }) });
    expect(screen.queryByTestId("ready-button")).toBeNull();
    expect(screen.getByTestId("lobby-admin").textContent).toContain("You run this one, you don't play it");
    expect(screen.getAllByTestId("lobby-row")).toHaveLength(2);
  });

  it("words the header chip with the start (AUCTION SAT 8 PM) and names the Commissioner without a franchise (Room `lobby`)", () => {
    const { unmount } = lobby({ startsAt: "2030-03-02T20:00:00" });
    expect(screen.getByTestId("room-state").textContent).toBe("AUCTION SAT 8 PM");
    expect(screen.getByText("Auction lobby")).toBeTruthy();
    unmount();
    lobby({ startsAt: "2030-03-02T20:05:00", room: room({ myRole: "commissioner", myFranchiseId: null }) });
    expect(screen.getByTestId("room-state").textContent).toBe("AUCTION SAT 8:05 PM");
    expect(screen.getByText("Auction lobby · Commissioner, no franchise")).toBeTruthy();
  });
});
