// @vitest-environment jsdom
import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiError, type AuctionEvent, type EventsPage } from "../api/client";
import { useFeed } from "./useFeed";

const ev = (seq: number, lot = 7, kind = "bid_accepted"): AuctionEvent => ({ seq, at: "2030-03-01T10:00:00Z", kind, franchiseId: "fa", lot, payload: { amount: seq * 100 } });
const page = (events: AuctionEvent[], more = false): EventsPage => ({ events, lastSeq: events.length ? events[events.length - 1].seq : 0, more, serverNow: "2030-03-01T10:00:00Z" });

describe("useFeed", () => {
  it("rebuilds from the events page when the feed first syncs, across pages, keeping the open lot's bids", async () => {
    const read = vi.fn(async (_room: string, after: number) => (after === 0 ? page([ev(1, 6), ev(2, 7, "lot_opened")], true) : page([ev(3), ev(4, 7, "lot_sold")])));
    const { result } = renderHook(() => useFeed("r1", { synced: true, bids: [] }, 7, read));
    await waitFor(() => expect(result.current.map((e) => e.seq)).toEqual([3]));
    expect(read.mock.calls.map((c) => c[1])).toEqual([0, 2]);
  });

  it("cuts a re-opened lot at its own opening, by position in the log (CR-16)", async () => {
    // Lot 3 drew a bid, was re-opened as lot 12 (opening at seq 10); lot 12 is on the block.
    const read = vi.fn(async () => page([ev(1, 3, "lot_opened"), ev(2, 3), ev(10, 12, "lot_opened"), ev(11, 12)]));
    const { result } = renderHook(() => useFeed("r1", { synced: true, bids: [] }, 12, read));
    await waitFor(() => expect(result.current.map((e) => e.seq)).toEqual([11]));
  });

  it("waits until the feed is synced, then reads only what is new after a resync", async () => {
    const read = vi.fn(async (_room: string, after: number) => (after === 0 ? page([ev(2), ev(3)]) : page([ev(5)])));
    const { result, rerender } = renderHook(({ synced }) => useFeed("r1", { synced, bids: [] }, 7, read), { initialProps: { synced: false } });
    expect(read).not.toHaveBeenCalled();
    rerender({ synced: true });
    await waitFor(() => expect(result.current.map((e) => e.seq)).toEqual([2, 3]));
    rerender({ synced: false });
    rerender({ synced: true });
    await waitFor(() => expect(result.current.map((e) => e.seq)).toEqual([2, 3, 5]));
    expect(read.mock.calls.map((c) => c[1])).toEqual([0, 3]);
  });

  it("shows live bids at once, deduped against the page, with nothing read for them", async () => {
    const read = vi.fn(async () => page([ev(2), ev(3)]));
    const { result, rerender } = renderHook(({ bids }) => useFeed("r1", { synced: true, bids }, 7, read), { initialProps: { bids: [] as AuctionEvent[] } });
    await waitFor(() => expect(result.current).toHaveLength(2));
    rerender({ bids: [ev(3), ev(4)] });
    expect(result.current.map((e) => e.seq)).toEqual([2, 3, 4]);
    expect(read).toHaveBeenCalledTimes(1);
  });

  it("keeps what it has when a read fails and reads again from the same place next time", async () => {
    const read = vi.fn<(room: string, after: number) => Promise<EventsPage>>().mockRejectedValueOnce(new Error("down")).mockResolvedValue(page([ev(2)]));
    const { result, rerender } = renderHook(({ synced }) => useFeed("r1", { synced, bids: [ev(9)] }, 7, read), { initialProps: { synced: true } });
    await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
    expect(result.current.map((e) => e.seq)).toEqual([9]);
    await act(async () => {
      rerender({ synced: false });
    });
    rerender({ synced: true });
    await waitFor(() => expect(result.current.map((e) => e.seq)).toEqual([2, 9]));
    expect(read.mock.calls.map((c) => c[1])).toEqual([0, 0]);
  });

  it("retries a failed read with a growing wait while the feed stays synced, from where it left off", async () => {
    vi.useFakeTimers();
    try {
      const read = vi
        .fn<(room: string, after: number) => Promise<EventsPage>>()
        .mockRejectedValueOnce(new Error("down"))
        .mockRejectedValueOnce(new Error("down"))
        .mockResolvedValue(page([ev(2), ev(3)]));
      const { result } = renderHook(() => useFeed("r1", { synced: true, bids: [] }, 7, read));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(read).toHaveBeenCalledTimes(1);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000);
      });
      expect(read).toHaveBeenCalledTimes(2);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1999);
      });
      expect(read).toHaveBeenCalledTimes(2);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1);
      });
      expect(read).toHaveBeenCalledTimes(3);
      expect(result.current.map((e) => e.seq)).toEqual([2, 3]);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(60_000);
      });
      expect(read).toHaveBeenCalledTimes(3);
    } finally {
      vi.useRealTimers();
    }
  });

  it("stops retrying on a permanent error such as 403 but keeps reading after a 5xx (CR-30)", async () => {
    vi.useFakeTimers();
    try {
      const gone = vi.fn<(room: string, after: number) => Promise<EventsPage>>().mockRejectedValue(new ApiError(403, null));
      renderHook(() => useFeed("r1", { synced: true, bids: [] }, 7, gone));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(60_000);
      });
      expect(gone).toHaveBeenCalledTimes(1);
      const down = vi.fn<(room: string, after: number) => Promise<EventsPage>>().mockRejectedValueOnce(new ApiError(503, null)).mockResolvedValue(page([ev(2)]));
      renderHook(() => useFeed("r1", { synced: true, bids: [] }, 7, down));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000);
      });
      expect(down).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it("stops retrying once the feed is no longer synced", async () => {
    vi.useFakeTimers();
    try {
      const read = vi.fn<(room: string, after: number) => Promise<EventsPage>>().mockRejectedValue(new Error("down"));
      const { rerender } = renderHook(({ synced }) => useFeed("r1", { synced, bids: [] }, 7, read), { initialProps: { synced: true } });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      rerender({ synced: false });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(60_000);
      });
      expect(read).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });
  it("is not blocked by a read that never answers: a resync reads on its own (CR-19)", async () => {
    const read = vi
      .fn<(room: string, after: number) => Promise<EventsPage>>()
      .mockImplementationOnce(() => new Promise<EventsPage>(() => {}))
      .mockResolvedValue(page([ev(2), ev(3)]));
    const { result, rerender } = renderHook(({ synced }) => useFeed("r1", { synced, bids: [] }, 7, read), { initialProps: { synced: true } });
    await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
    rerender({ synced: false });
    rerender({ synced: true });
    await waitFor(() => expect(result.current.map((e) => e.seq)).toEqual([2, 3]));
    expect(read).toHaveBeenCalledTimes(2);
  });
});
