// @vitest-environment jsdom
import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, getPool } from "../api/client";
import { usePool } from "./usePool";

vi.mock("../api/client", async (orig) => ({ ...(await orig<typeof import("../api/client")>()), getPool: vi.fn() }));

const pool = { players: [] } as never;

describe("usePool", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(getPool).mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it("reads again with a growing wait after a failure, until it gets the pool", async () => {
    vi.mocked(getPool).mockRejectedValueOnce(new Error("down")).mockRejectedValueOnce(new Error("down")).mockResolvedValue(pool);
    const { result } = renderHook(() => usePool("r1"));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current).toMatchObject({ pool: null, failed: true });
    expect(getPool).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(getPool).toHaveBeenCalledTimes(2);
    expect(result.current.pool).toBeNull();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1999);
    });
    expect(getPool).toHaveBeenCalledTimes(2); // the second wait is longer than the first
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(getPool).toHaveBeenCalledTimes(3);
    expect(result.current).toMatchObject({ pool, failed: false });
  });

  it("stops reading again on a permanent error and keeps the failed state, but still retries on 5xx, 408 and 429 and on a manual retry (CR-30)", async () => {
    vi.mocked(getPool).mockRejectedValue(new ApiError(404, null));
    const { result } = renderHook(() => usePool("r1"));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(getPool).toHaveBeenCalledTimes(1);
    expect(result.current).toMatchObject({ pool: null, failed: true });
    vi.mocked(getPool).mockResolvedValue(pool);
    await act(async () => {
      result.current.retry();
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current).toMatchObject({ pool, failed: false });

    for (const status of [500, 503, 408, 429]) {
      vi.mocked(getPool).mockReset();
      vi.mocked(getPool).mockRejectedValueOnce(new ApiError(status, null)).mockResolvedValue(pool);
      const h = renderHook(() => usePool("r1"));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000);
      });
      expect(getPool).toHaveBeenCalledTimes(2);
      h.unmount();
    }
  });

  it("reads nothing while disabled and stops retrying when it unmounts", async () => {
    vi.mocked(getPool).mockRejectedValue(new Error("down"));
    const off = renderHook(() => usePool("r1", false));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(getPool).not.toHaveBeenCalled();
    off.unmount();
    const on = renderHook(() => usePool("r1"));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    on.unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(getPool).toHaveBeenCalledTimes(1);
  });
  it("does not read again once it has the pool, on a re-enable or a retry, and never fails beside a valid pool (CR-20)", async () => {
    vi.mocked(getPool).mockResolvedValueOnce(pool).mockRejectedValue(new Error("down"));
    const { result, rerender } = renderHook(({ on }) => usePool("r1", on), { initialProps: { on: true } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current).toMatchObject({ pool, failed: false });
    rerender({ on: false });
    rerender({ on: true });
    act(() => result.current.retry());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(getPool).toHaveBeenCalledTimes(1);
    expect(result.current).toMatchObject({ pool, failed: false });
  });
});
