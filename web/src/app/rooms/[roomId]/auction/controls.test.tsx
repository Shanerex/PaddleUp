// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("../../../../api/client", async (orig) => ({ ...(await orig<typeof import("../../../../api/client")>()), sendControl: vi.fn() }));

import { sendControl, type AuctionState } from "../../../../api/client";
import { Controls, type ControlsContext } from "./controls";
import { AdjournedCard, Notices } from "./notices";

afterEach(cleanup);
beforeEach(() => vi.mocked(sendControl).mockReset());

const lotCard = { no: 7, playerId: "p7", name: "Pat Reed", country: "C", role: "def", tags: [], set: "S", position: 1, basePrice: 200_000 };
function state(over: Partial<AuctionState> = {}): AuctionState {
  return {
    phase: "live",
    lotOpen: true,
    currentBid: 0,
    lot: lotCard,
    unsold: [2, 3],
    lastResult: { lot: 6, sold: true, franchiseId: "fa", amount: 400_000, card: { ...lotCard, no: 6, name: "Sam Cole" } },
    ...over,
  } as unknown as AuctionState;
}
function ctx(over: Partial<ControlsContext> = {}): ControlsContext {
  return {
    roomId: "r1",
    state: state(),
    money: (n) => `€${n / 1_000_000} M`,
    nameOf: (id) => (id === "fa" ? "Red FC" : null),
    timeLeftMs: 12_000,
    serverNow: () => Date.now(),
    onNotice: vi.fn(),
    onResync: vi.fn(),
    ...over,
  };
}
const open = async (c: ControlsContext) => {
  render(<Controls ctx={c} />);
  await userEvent.click(screen.getByTestId("controls-chip"));
};

describe("Controls sheets", () => {
  it("draw a title row with a close button instead of the drag handle, and the X closes", async () => {
    await open(ctx());
    const dialog = screen.getByRole("dialog", { name: "Commissioner controls" });
    expect(dialog.querySelector("h2")?.textContent).toBe("Commissioner controls");
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("skip, re-open and adjourn sheets use the same title row", async () => {
    await open(ctx());
    await userEvent.click(screen.getByTestId("control-skip"));
    expect(screen.getByRole("dialog", { name: "Skip Lot 7?" }).querySelector("h2")?.textContent).toBe("Skip Lot 7?");
    expect(screen.getByRole("button", { name: "Close" })).toBeTruthy();
  });
});

describe("Controls", () => {
  it("lists the controls with Skip available only on a lot with no bid", async () => {
    await open(ctx());
    expect((screen.getByTestId("control-skip") as HTMLButtonElement).disabled).toBe(false);
    expect((screen.getByTestId("control-accel") as HTMLButtonElement).disabled).toBe(true);
    cleanup();
    await open(ctx({ state: state({ currentBid: 300_000 }) }));
    expect((screen.getByTestId("control-skip") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByTestId("control-skip").textContent).toContain("bids stand");
  });

  it("pauses straight from the list and reads the state again", async () => {
    vi.mocked(sendControl).mockResolvedValue({ outcome: "accepted", lot: 7, serverNow: "" });
    const c = ctx();
    await open(c);
    await userEvent.click(screen.getByTestId("control-pause"));
    expect(sendControl).toHaveBeenCalledWith("r1", "pause", {});
    await waitFor(() => expect(c.onResync).toHaveBeenCalled());
  });

  it("offers Resume while paused", async () => {
    await open(ctx({ state: state({ phase: "paused" }) }));
    expect(screen.getByTestId("control-pause").textContent).toContain("Resume");
  });

  it("confirms a skip, naming the lot, and shows the server's refusal when a bid landed first", async () => {
    vi.mocked(sendControl).mockResolvedValue({ outcome: "rejected", reason: "has_bid", lot: 7, serverNow: "" });
    const c = ctx({ state: state({ leaderFranchiseId: undefined }) });
    await open(c);
    await userEvent.click(screen.getByTestId("control-skip"));
    expect(screen.getByTestId("skip-sheet").textContent).toContain("Pat Reed leaves the block");
    await userEvent.click(screen.getByRole("button", { name: "Skip Lot 7" }));
    expect(sendControl).toHaveBeenCalledWith("r1", "skip", { lot: 7 });
    await waitFor(() => expect(c.onNotice).toHaveBeenLastCalledWith(expect.objectContaining({ title: "Couldn't skip Lot 7" })));
  });

  it("will not re-open without a reason, then sends the trimmed reason with the lot", async () => {
    vi.mocked(sendControl).mockResolvedValue({ outcome: "accepted", lot: 6, serverNow: "" });
    await open(ctx());
    await userEvent.click(screen.getByTestId("control-reopen"));
    const sheet = screen.getByTestId("reopen-sheet");
    expect(sheet.textContent).toContain("Sam Cole leaves Red FC");
    expect(sheet.textContent).toContain("€0.4 M goes back to their purse");
    const button = screen.getByRole("button", { name: "Re-open Lot 6" }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    await userEvent.type(screen.getByTestId("reopen-reason"), "  Wrong paddle ");
    expect(button.disabled).toBe(false);
    await userEvent.click(button);
    expect(sendControl).toHaveBeenCalledWith("r1", "reopen", { lot: 6, reason: "Wrong paddle" });
  });
});

describe("Notices for the control phases", () => {
  const base = { reconnecting: false, paused: false, pausedLeftMs: null, toast: null, onRetry: () => {}, onDismiss: () => {} };

  it("shows the manager's pause notice with the frozen time and no Resume", () => {
    render(<Notices {...base} paused pausedLeftMs={9000} />);
    expect(screen.getByTestId("notice-paused").textContent).toContain("Commish hit pause");
    expect(screen.getByTestId("notice-paused").textContent).toContain("0:09");
    expect(screen.queryByTestId("notice-resume")).toBeNull();
  });

  it("gives the Commissioner's own pause notice a Resume", async () => {
    const onResume = vi.fn();
    render(<Notices {...base} paused pausedLeftMs={9000} onResume={onResume} />);
    expect(screen.getByTestId("notice-paused").textContent).toContain("You paused the auction");
    await userEvent.click(screen.getByTestId("notice-resume"));
    expect(onResume).toHaveBeenCalled();
  });

  it("counts down the resume lobby to the lot going live", () => {
    render(<Notices {...base} resumeLobby={{ lot: 87, leftMs: 102_000 }} />);
    const text = screen.getByTestId("notice-resume-lobby").textContent ?? "";
    expect(text).toContain("Lot 87 goes live in");
    expect(text).toContain("1:42");
  });

  it("shows the Commissioner's skip or re-open to every manager", () => {
    render(<Notices {...base} toast={{ kind: "admin", notice: { title: "Lot 41 is back", body: 'The Commissioner re-opened it: "timer bug".' } }} />);
    expect(screen.getByTestId("notice-admin").textContent).toContain("Lot 41 is back");
  });
});

describe("AdjournedCard", () => {
  it("says when bidding resumes in the viewer's time and gives managers no actions", () => {
    render(<AdjournedCard lotsDone={86} squad={4} purse="€1 M" resumes="Sat 2 Nov, 8:00 PM" />);
    expect(screen.getByTestId("adjourned-card").textContent).toContain("Bidding resumes Sat 2 Nov, 8:00 PM your time.");
    expect(screen.queryByTestId("resume-now")).toBeNull();
    expect(screen.getByTestId("adjourned-card").textContent).toContain("Adjourned after Lot 86.");
  });

  it("does not claim a Lot 0 when adjourned before any lot closed", () => {
    render(<AdjournedCard lotsDone={0} squad={null} purse={null} resumes={null} />);
    const text = screen.getByTestId("adjourned-card").textContent ?? "";
    expect(text).toContain("Adjourned before the first lot closed.");
    expect(text).not.toContain("Lot 0");
  });

  it("gives the Commissioner Resume now and Change time, and names the 2-minute lobby", async () => {
    const onResumeNow = vi.fn();
    const onChangeTime = vi.fn();
    render(<AdjournedCard lotsDone={86} squad={null} purse={null} resumes="Sat" commish={{ onResumeNow, onChangeTime, busy: false, nextLot: 87, lobbyMinutes: 2 }} />);
    await userEvent.click(screen.getByTestId("resume-now"));
    await userEvent.click(screen.getByTestId("change-resume-time"));
    expect(onResumeNow).toHaveBeenCalled();
    expect(onChangeTime).toHaveBeenCalled();
    expect(screen.getByTestId("adjourned-card").textContent).toContain("2-minute countdown, then Lot 87.");
  });
});
