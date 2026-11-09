import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, type AuctionEvent, type AuctionState } from "../api/client";
import { ServerClock } from "./clock";
import { createLive, topicOf, type Connect, type Environment, type Socket } from "./live";

const ROOM = "room-1";
const A = "fr-a";
const B = "fr-b";

function state(over: Partial<AuctionState> = {}): AuctionState {
  return {
    phase: "live",
    startsAt: "2030-03-01T10:00:00Z",
    serverNow: "2030-03-01T10:00:03Z",
    lastSeq: 3,
    lotCount: 6,
    lot: { no: 1, playerId: "p1", name: "Player 1", country: "Chile", role: "BAT", tags: [], set: "S", position: 0, basePrice: 20 },
    lotOpen: true,
    currentBid: 0,
    passedBy: [],
    standings: [],
    unsold: [],
    connected: [],
    ready: [],
    nextBid: 20,
    deadline: "2030-03-01T10:00:20Z",
    ...over,
  };
}

function bidEvent(seq: number, amount: number, franchiseId = B): AuctionEvent {
  return { seq, at: "x", kind: "bid_accepted", franchiseId, lot: 1, payload: { amount, deadline: "2030-03-01T10:00:30Z", nextBid: amount + 5 } };
}

/** A fake STOMP socket the test drives. */
class FakeSocket implements Socket {
  subs = new Map<string, (body: string) => void>();
  closed = false;
  constructor(readonly ticket: string) {}
  subscribe(destination: string, onBody: (body: string) => void) {
    this.subs.set(destination, onBody);
  }
  close() {
    this.closed = true;
  }
  send(destination: string, body: unknown) {
    this.subs.get(destination)?.(JSON.stringify(body));
  }
}

function setup(first: AuctionState = state()) {
  const sockets: { socket: FakeSocket; handlers: Parameters<Connect>[1] }[] = [];
  const connect: Connect = (ticket, handlers) => {
    const socket = new FakeSocket(ticket);
    sockets.push({ socket, handlers });
    return { close: () => socket.close() };
  };
  const listeners: Record<string, Set<() => void>> = { online: new Set(), offline: new Set(), visible: new Set() };
  const env: Environment = {
    on(event, cb) {
      listeners[event].add(cb);
      return () => listeners[event].delete(cb);
    },
  };
  let tickets = 0;
  let fetches: AuctionState[] = [first];
  const getAuction = vi.fn(async () => {
    const next = fetches.length > 1 ? fetches.shift()! : fetches[0];
    return next;
  });
  const getTicket = vi.fn(async () => ({ ticket: `t${++tickets}` }));
  const clock = new ServerClock(() => Date.now());
  const live = createLive({ roomId: ROOM, getAuction, getTicket, connect, clock, env, mono: () => Date.now() });
  return {
    live,
    sockets,
    getAuction,
    getTicket,
    setFetches: (...s: AuctionState[]) => (fetches = s),
    fire: (e: "online" | "offline" | "visible") => listeners[e].forEach((cb) => cb()),
    /** Opens the socket of the latest attempt, as the server would, and waits for the first fetch. */
    async connectLatest() {
      await vi.advanceTimersByTimeAsync(0);
      const last = sockets[sockets.length - 1];
      last.handlers.onConnect(last.socket);
      await vi.advanceTimersByTimeAsync(0);
      return last;
    },
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(Date.parse("2030-03-01T10:00:03Z"));
});
afterEach(() => vi.useRealTimers());

describe("createLive", () => {
  it("connects with a ticket, subscribes to the topic and presence, and is synced only after a fetch", async () => {
    const t = setup();
    t.live.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(t.sockets[0].socket.ticket).toBe("t1");
    expect(t.live.snapshot().synced).toBe(false);

    t.sockets[0].handlers.onConnect(t.sockets[0].socket);
    expect([...t.sockets[0].socket.subs.keys()]).toEqual([topicOf(ROOM), `${topicOf(ROOM)}/presence`]);
    expect(t.live.snapshot().synced).toBe(false); // subscribed, fetch not applied yet
    await vi.advanceTimersByTimeAsync(0);

    expect(t.live.snapshot()).toMatchObject({ connection: "live", synced: true });
    expect(t.live.snapshot().state?.lastSeq).toBe(3);
  });

  it("patches a bid into the state and drops a seq it already has", async () => {
    const t = setup();
    t.live.start();
    const { socket } = await t.connectLatest();

    socket.send(topicOf(ROOM), bidEvent(4, 20));
    expect(t.live.snapshot().state).toMatchObject({ currentBid: 20, leaderFranchiseId: B, nextBid: 25, lastSeq: 4, deadline: "2030-03-01T10:00:30Z" });

    socket.send(topicOf(ROOM), bidEvent(4, 99, A));
    socket.send(topicOf(ROOM), bidEvent(3, 99, A));
    expect(t.live.snapshot().state).toMatchObject({ currentBid: 20, leaderFranchiseId: B, lastSeq: 4 });
    expect(t.getAuction).toHaveBeenCalledTimes(1);
  });

  it("refetches on a seq gap and stays unsynced until the fetch lands", async () => {
    const t = setup();
    t.live.start();
    const { socket } = await t.connectLatest();
    t.setFetches(state({ lastSeq: 6, currentBid: 30, leaderFranchiseId: A, nextBid: 35 }));

    socket.send(topicOf(ROOM), bidEvent(6, 30, A)); // 4 and 5 were missed
    expect(t.live.snapshot().synced).toBe(false);
    await vi.advanceTimersByTimeAsync(0);

    expect(t.getAuction).toHaveBeenCalledTimes(2);
    expect(t.live.snapshot()).toMatchObject({ synced: true });
    expect(t.live.snapshot().state).toMatchObject({ lastSeq: 6, currentBid: 30 });
  });

  it.each(["lot_sold", "lot_unsold", "lot_opened", "lot_resumed", "auction_started", "auction_ended"])(
    "refetches on %s",
    async (kind) => {
      const t = setup();
      t.live.start();
      const { socket } = await t.connectLatest();
      t.setFetches(state({ lastSeq: 4 }));

      socket.send(topicOf(ROOM), { seq: 4, at: "x", kind, lot: 1, payload: {} });
      expect(t.live.snapshot().synced).toBe(false);
      await vi.advanceTimersByTimeAsync(0);

      expect(t.getAuction).toHaveBeenCalledTimes(2);
      expect(t.live.snapshot()).toMatchObject({ synced: true });
      expect(t.live.snapshot().state?.lastSeq).toBe(4);
    },
  );

  it("applies events that arrived while the first fetch was in flight, in order", async () => {
    const t = setup();
    let release: (s: AuctionState) => void = () => {};
    t.getAuction.mockImplementationOnce(() => new Promise<AuctionState>((r) => (release = r)));
    t.live.start();
    await vi.advanceTimersByTimeAsync(0);
    const { socket, handlers } = t.sockets[0];
    handlers.onConnect(socket);

    socket.send(topicOf(ROOM), bidEvent(5, 30, A)); // arrives before 4
    socket.send(topicOf(ROOM), bidEvent(4, 25, B));
    socket.send(topicOf(ROOM), bidEvent(3, 1, B)); // already in the fetched state
    release(state({ lastSeq: 3 }));
    await vi.advanceTimersByTimeAsync(0);

    expect(t.live.snapshot().synced).toBe(true);
    expect(t.live.snapshot().state).toMatchObject({ lastSeq: 5, currentBid: 30, leaderFranchiseId: A });
  });

  it("reports reconnecting within 3 s of going offline, freezes the clock and locks until a fetch", async () => {
    const t = setup();
    t.live.start();
    await t.connectLatest();
    expect(t.live.snapshot().frozenLeft).toBeNull();

    t.fire("offline");

    expect(t.live.snapshot()).toMatchObject({ connection: "reconnecting", synced: false });
    expect(t.live.snapshot().frozenLeft).toBe(17_000); // deadline 10:00:20, server now 10:00:03
    await vi.advanceTimersByTimeAsync(5000);
    expect(t.live.snapshot().frozenLeft).toBe(17_000); // still frozen while time passes

    t.setFetches(state({ lastSeq: 9, currentBid: 55 }));
    t.fire("online");
    await t.connectLatest();

    expect(t.live.snapshot()).toMatchObject({ connection: "live", synced: true, frozenLeft: null });
    expect(t.live.snapshot().state?.lastSeq).toBe(9);
  });

  it("reports reconnecting when the socket closes (a missed heartbeat) and reconnects with a new ticket", async () => {
    const t = setup();
    t.live.start();
    const first = await t.connectLatest();

    first.handlers.onClose();
    expect(t.live.snapshot()).toMatchObject({ connection: "reconnecting", synced: false });

    await vi.advanceTimersByTimeAsync(600);
    expect(t.getTicket).toHaveBeenCalledTimes(2);
    const second = await t.connectLatest();
    expect(second.socket.ticket).toBe("t2");
    expect(t.live.snapshot()).toMatchObject({ connection: "live", synced: true });
  });

  it("ignores a late message from a socket it has already replaced", async () => {
    const t = setup();
    t.live.start();
    const first = await t.connectLatest();
    first.handlers.onClose();
    await vi.advanceTimersByTimeAsync(600);
    await t.connectLatest();

    first.socket.send(topicOf(ROOM), bidEvent(4, 77, A));

    expect(t.live.snapshot().state?.currentBid).toBe(0);
  });

  it("backs off between failed attempts and Retry tries at once", async () => {
    const t = setup();
    t.getTicket.mockRejectedValue(new Error("down"));
    t.live.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(t.getTicket).toHaveBeenCalledTimes(1);
    expect(t.live.snapshot().connection).toBe("reconnecting");

    await vi.advanceTimersByTimeAsync(499);
    expect(t.getTicket).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(2);
    expect(t.getTicket).toHaveBeenCalledTimes(2);

    t.getTicket.mockResolvedValue({ ticket: "ok" });
    t.live.retry();
    await t.connectLatest();
    expect(t.live.snapshot()).toMatchObject({ connection: "live", synced: true });
  });

  it("locks when a hidden tab comes back and unlocks only after a fresh fetch", async () => {
    const t = setup();
    t.live.start();
    await t.connectLatest();
    let release: (s: AuctionState) => void = () => {};
    t.getAuction.mockImplementationOnce(() => new Promise<AuctionState>((r) => (release = r)));

    t.fire("visible");
    expect(t.live.snapshot().synced).toBe(false);
    expect(t.live.snapshot().state?.lastSeq).toBe(3);

    release(state({ lastSeq: 8, currentBid: 70 }));
    await vi.advanceTimersByTimeAsync(0);
    expect(t.live.snapshot()).toMatchObject({ synced: true });
    expect(t.live.snapshot().state).toMatchObject({ lastSeq: 8, currentBid: 70 });
  });

  it("applies presence from the topic while the auction is scheduled", async () => {
    const t = setup(state({ phase: "scheduled", lotOpen: false, lot: undefined, nextBid: undefined, lastSeq: 1 }));
    t.live.start();
    const { socket } = await t.connectLatest();

    socket.send(`${topicOf(ROOM)}/presence`, { connected: [A, B], ready: [A] });

    expect(t.live.snapshot()).toMatchObject({ connected: [A, B], ready: [A] });
  });

  it("marks a room with no auction as missing instead of retrying", async () => {
    const t = setup();
    t.getAuction.mockRejectedValue(new ApiError(404, { error: "no_auction" }));
    t.live.start();
    await t.connectLatest();

    expect(t.live.snapshot()).toMatchObject({ missing: true, state: null });
  });

  it("stop closes the socket and ignores everything after", async () => {
    const t = setup();
    t.live.start();
    const { socket } = await t.connectLatest();

    t.live.stop();
    socket.send(topicOf(ROOM), bidEvent(4, 20));
    t.fire("offline");

    expect(socket.closed).toBe(true);
    expect(t.live.snapshot().state?.currentBid).toBe(0);
  });

  it("takes the server's answer to a ready toggle as presence", async () => {
    const t = setup(state({ phase: "scheduled", lotOpen: false, lot: undefined, nextBid: undefined, lastSeq: 1 }));
    t.live.start();
    await t.connectLatest();

    t.live.applyPresence({ connected: [A], ready: [A] });

    expect(t.live.snapshot()).toMatchObject({ connected: [A], ready: [A] });
  });

  it("freezes the lobby countdown when it drops, including once past the start", async () => {
    const t = setup(state({ phase: "scheduled", lotOpen: false, lot: undefined, nextBid: undefined, deadline: undefined, startsAt: "2030-03-01T10:00:13Z", lastSeq: 1 }));
    t.live.start();
    await t.connectLatest();

    t.fire("offline");
    expect(t.live.snapshot().frozenLeft).toBe(10_000);

    t.fire("online");
    t.setFetches(state({ phase: "scheduled", lotOpen: false, lot: undefined, nextBid: undefined, deadline: undefined, startsAt: "2030-03-01T10:00:13Z", lastSeq: 1 }));
    await t.connectLatest();
    await vi.advanceTimersByTimeAsync(20_000);
    t.fire("offline");
    expect(t.live.snapshot().frozenLeft).toBeLessThan(0);
  });
});
