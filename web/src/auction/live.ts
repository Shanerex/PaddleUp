import { Client } from "@stomp/stompjs";
import { ApiError, type AuctionEvent, type AuctionState } from "../api/client";
import { ServerClock } from "./clock";

/** Heartbeat each way, in ms; the server sends the same. Two missed beats close the socket. Keep equal to the server's. */
export const HEARTBEAT_MS = 10_000;

export type Connection = "connecting" | "live" | "reconnecting";

export type LiveSnapshot = {
  /** The server's state with bid events patched in; null until the first fetch. */
  state: AuctionState | null;
  connection: Connection;
  /** True only after a full state fetch was applied on this connection and nothing was missed since. */
  synced: boolean;
  /** Franchises with the auction open and those ready, from the presence topic. */
  connected: string[];
  ready: string[];
  /** The room has no auction (404 no_auction). */
  missing: boolean;
  /** Wall-clock ms when the connection was last known good; for "last seen" text only. */
  lastSeen: number | null;
  /** Ms left on the lot (or to the start, negative once past it) when the connection last dropped; null while live (R-10, the frozen timer). */
  frozenLeft: number | null;
};

/** One open socket: subscribe to a destination, close. */
export interface Socket {
  subscribe(destination: string, onBody: (body: string) => void): void;
  close(): void;
}

/** Opens a socket with a ticket. `onClose` fires on any close, error or silent drop. */
export type Connect = (
  ticket: string,
  handlers: { onConnect(socket: Socket): void; onClose(): void },
) => { close(): void };

/** The browser events the live feed reacts to. */
export interface Environment {
  on(event: "online" | "offline" | "visible", cb: () => void): () => void;
}

export type LiveDeps = {
  roomId: string;
  getAuction: (roomId: string) => Promise<AuctionState>;
  getTicket: (roomId: string) => Promise<{ ticket?: string }>;
  connect: Connect;
  clock: ServerClock;
  env?: Environment;
  /** Monotonic ms, for the clock samples. */
  mono?: () => number;
};

const RETRY_MIN_MS = 500;
const RETRY_MAX_MS = 4000;

/** Events that carry everything the screen needs to patch; every other kind refetches the state. */
const PATCHED = new Set(["bid_accepted", "passed"]);

export const topicOf = (roomId: string) => `/topic/auctions/${roomId}`;

/**
 * The live feed of one auction (R-6, R-10, AC-13 to AC-15). It connects with a ticket, subscribes to
 * the room topic and its presence topic, buffers what arrives while a state fetch is in flight and
 * applies events in order: a seq at or below the last is dropped, a bid patches the bid, leader,
 * deadline and next bid, a gap or any other event refetches. `synced` is true only after a fetch,
 * and any doubt (offline, close, a hidden tab coming back) clears it before the fetch starts.
 */
export function createLive(deps: LiveDeps) {
  const { roomId, clock } = deps;
  const mono = deps.mono ?? (() => (typeof performance !== "undefined" ? performance.now() : Date.now()));
  const env = deps.env ?? browserEnvironment();

  let snap: LiveSnapshot = {
    state: null,
    connection: "connecting",
    synced: false,
    connected: [],
    ready: [],
    missing: false,
    lastSeen: null,
    frozenLeft: null,
  };
  const listeners = new Set<() => void>();
  let stopped = true;
  let attempt = 0;
  let retryTimer: ReturnType<typeof setTimeout> | undefined;
  let conn: { close(): void } | null = null;
  let generation = 0; // a socket from an older attempt is ignored
  let socketUp = false; // true only between a socket's CONNECTED and its close; a fetch alone never makes the feed live
  let presenceStamp = 0; // counts presence messages, so a fetch can tell it raced one
  let lastPresence: { connected: string[]; ready: string[] } | null = null;
  let buffer: AuctionEvent[] = [];
  let fetching = false;
  let again = false;
  let offs: (() => void)[] = [];

  const emit = (patch: Partial<LiveSnapshot>) => {
    snap = { ...snap, ...patch };
    listeners.forEach((l) => l());
  };

  /** Leaves live: lock bidding, remember how much of the lot clock was left. */
  const drop = () => {
    const s = snap.state;
    // A lot has its deadline; a lobby counts down to the start, and past it keeps counting up.
    const left = s?.deadline
      ? clock.timeLeft(Date.parse(s.deadline))
      : s?.phase === "scheduled" && clock.ready
        ? Date.parse(s.startsAt) - clock.now()
        : null;
    emit({ connection: "reconnecting", synced: false, frozenLeft: snap.frozenLeft ?? left });
  };

  const fetchState = async () => {
    if (fetching) {
      again = true;
      return;
    }
    fetching = true;
    try {
      do {
        again = false;
        const sent = mono();
        const atGeneration = generation;
        const atPresence = presenceStamp;
        let state: AuctionState;
        try {
          state = await deps.getAuction(roomId);
        } catch (e) {
          if (stopped) return;
          if (e instanceof ApiError && e.status === 404) {
            // An event that arrived while this read was in flight means the auction now exists.
            // Drop the buffer and read once more; a second 404 with nothing new buffered ends it.
            const arrived = buffer.length > 0;
            buffer = [];
            if (socketUp) attempt = 0;
            emit({
              missing: true,
              state: null,
              synced: socketUp,
              connection: socketUp ? "live" : snap.connection,
              frozenLeft: socketUp ? null : snap.frozenLeft,
            });
            if (arrived) {
              again = true;
              continue;
            }
            return;
          }
          fail();
          return;
        }
        if (stopped) return;
        clock.observe(Date.parse(state.serverNow), sent, mono());
        // The socket was replaced or dropped while this was in flight: its answer may predate the
        // new socket's subscriptions, so ask again on the current one.
        if (atGeneration !== generation) {
          again = true;
          continue;
        }
        applyFetched(state, atPresence);
      } while (again && !stopped);
    } finally {
      fetching = false;
    }
  };

  const applyFetched = (state: AuctionState, atPresence: number) => {
    let next = state;
    const pending = buffer.filter((e) => e.seq > next.lastSeq).sort((a, b) => a.seq - b.seq);
    buffer = [];
    let gap = false;
    for (const e of pending) {
      if (e.seq !== next.lastSeq + 1) {
        gap = true;
        break;
      }
      const patched = patch(next, e);
      if (!patched) {
        gap = true; // a structural event: the fetch we are about to make covers it
        break;
      }
      next = patched;
    }
    // Presence that arrived while the fetch was in flight is newer than the fetched snapshot.
    const newer = presenceStamp !== atPresence ? lastPresence : null;
    // Only an open socket makes the feed live; a fetch during the backoff just refreshes the state.
    if (socketUp) attempt = 0;
    emit({
      state: next,
      missing: false,
      connected: newer ? newer.connected : next.connected,
      ready: newer ? (next.phase === "scheduled" ? newer.ready : []) : next.ready,
      connection: socketUp ? "live" : snap.connection,
      synced: socketUp && !gap,
      lastSeen: socketUp ? Date.now() : snap.lastSeen,
      frozenLeft: socketUp ? null : snap.frozenLeft,
    });
    if (gap) again = true;
  };

  /** The state after one event, or null when the event needs a refetch. */
  const patch = (s: AuctionState, e: AuctionEvent): AuctionState | null => {
    if (!PATCHED.has(e.kind)) return null;
    if (e.kind === "passed") {
      return e.franchiseId && !s.passedBy.includes(e.franchiseId)
        ? { ...s, passedBy: [...s.passedBy, e.franchiseId], lastSeq: e.seq }
        : { ...s, lastSeq: e.seq };
    }
    const amount = Number(e.payload.amount);
    const deadline = String(e.payload.deadline ?? "");
    const nextBid = Number(e.payload.nextBid);
    if (!Number.isFinite(amount) || !Number.isFinite(nextBid) || !deadline) return null;
    return { ...s, currentBid: amount, leaderFranchiseId: e.franchiseId, deadline, nextBid, lastSeq: e.seq };
  };

  const onEvent = (e: AuctionEvent) => {
    const s = snap.state;
    if (!s) {
      if (snap.missing) {
        // "No auction yet" and an event arrives: the auction now exists, so read it.
        emit({ synced: false });
        void fetchState();
      } else {
        buffer.push(e);
      }
      return;
    }
    if (!snap.synced) {
      buffer.push(e);
      return;
    }
    if (e.seq <= s.lastSeq) return;
    const next = e.seq === s.lastSeq + 1 ? patch(s, e) : null;
    if (next) {
      emit({ state: next, lastSeen: Date.now() });
      return;
    }
    emit({ synced: false });
    void fetchState();
  };

  const onPresence = (body: { connected?: string[]; ready?: string[] }) => {
    lastPresence = { connected: body.connected ?? [], ready: body.ready ?? [] };
    presenceStamp++;
    emit({ connected: lastPresence.connected, ready: snap.state?.phase === "scheduled" ? lastPresence.ready : [] });
  };

  const parse = (body: string) => {
    try {
      return JSON.parse(body);
    } catch {
      return null;
    }
  };

  const open = async () => {
    if (stopped) return;
    const mine = ++generation;
    socketUp = false;
    conn?.close();
    conn = null;
    let ticket: string;
    try {
      const issued = (await deps.getTicket(roomId)).ticket;
      if (!issued) throw new Error("no ticket");
      ticket = issued;
    } catch {
      if (!stopped && mine === generation) fail();
      return;
    }
    if (stopped || mine !== generation) return;
    conn = deps.connect(ticket, {
      onConnect(socket) {
        if (mine !== generation || stopped) return;
        socketUp = true;
        buffer = [];
        socket.subscribe(topicOf(roomId), (b) => {
          const e = parse(b);
          if (mine === generation && e && typeof e.seq === "number") onEvent(e as AuctionEvent);
        });
        socket.subscribe(`${topicOf(roomId)}/presence`, (b) => {
          const p = parse(b);
          if (mine === generation && p) onPresence(p);
        });
        void fetchState();
      },
      onClose() {
        if (mine === generation && !stopped) fail();
      },
    });
  };

  /** A failed attempt or a closed socket: lock, then try again with a growing pause. */
  const fail = () => {
    drop();
    generation++;
    socketUp = false;
    conn?.close();
    conn = null;
    clearTimeout(retryTimer);
    const wait = Math.min(RETRY_MAX_MS, RETRY_MIN_MS * 2 ** attempt++);
    retryTimer = setTimeout(() => void open(), wait);
  };

  const reconnectNow = () => {
    clearTimeout(retryTimer);
    attempt = 0;
    drop();
    void open();
  };

  return {
    snapshot: () => snap,
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    start() {
      if (!stopped) return;
      stopped = false;
      offs = [
        env.on("offline", () => {
          if (stopped) return;
          clearTimeout(retryTimer);
          generation++;
          socketUp = false;
          conn?.close();
          conn = null;
          drop();
        }),
        env.on("online", () => {
          if (!stopped) reconnectNow();
        }),
        // A tab that was hidden may have slept through bids: lock, then trust only a fresh fetch.
        env.on("visible", () => {
          if (stopped) return;
          emit({ synced: false });
          void fetchState();
        }),
      ];
      void open();
    },
    /** The Retry button. */
    retry() {
      if (!stopped) reconnectNow();
    },
    /** The server's own answer to a ready toggle: it is the lobby truth, like a presence message. */
    applyPresence(body: { connected?: string[]; ready?: string[] }) {
      if (!stopped) onPresence(body);
    },
    /** Asks for a state fetch now (after my own bid, for instance). */
    resync() {
      if (stopped) return;
      emit({ synced: false });
      void fetchState();
    },
    stop() {
      stopped = true;
      generation++;
      socketUp = false;
      clearTimeout(retryTimer);
      offs.forEach((off) => off());
      offs = [];
      conn?.close();
      conn = null;
      buffer = [];
    },
  };
}

export type Live = ReturnType<typeof createLive>;

function browserEnvironment(): Environment {
  return {
    on(event, cb) {
      if (typeof window === "undefined") return () => {};
      if (event === "visible") {
        const handler = () => {
          if (document.visibilityState === "visible") cb();
        };
        document.addEventListener("visibilitychange", handler);
        return () => document.removeEventListener("visibilitychange", handler);
      }
      window.addEventListener(event, cb);
      return () => window.removeEventListener(event, cb);
    },
  };
}

/** Where the socket lives: straight to the server (a Next rewrite does not carry WebSockets). */
export function socketUrl(): string {
  const configured = process.env.NEXT_PUBLIC_WS_URL;
  if (configured) return configured;
  if (typeof window !== "undefined") {
    const { protocol, hostname } = window.location;
    return `${protocol === "https:" ? "wss" : "ws"}://${hostname}:8080/api/ws`;
  }
  return "ws://localhost:8080/api/ws";
}

/** The real STOMP socket: a ticket on CONNECT, 10 s heartbeats, no automatic reconnect (a ticket is single-use). */
export const stompConnect: Connect = (ticket, handlers) => {
  const client = new Client({
    brokerURL: socketUrl(),
    connectHeaders: { ticket },
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    reconnectDelay: 0,
  });
  client.onConnect = () =>
    handlers.onConnect({
      subscribe: (destination, onBody) => {
        client.subscribe(destination, (frame) => onBody(frame.body));
      },
      close: () => void client.deactivate(),
    });
  client.onWebSocketClose = () => handlers.onClose();
  client.onWebSocketError = () => handlers.onClose();
  client.onStompError = () => handlers.onClose();
  client.activate();
  return { close: () => void client.deactivate() };
};
