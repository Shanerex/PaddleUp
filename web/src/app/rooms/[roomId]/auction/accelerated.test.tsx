// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuctionLot } from "../../../../api/client";
import { MainDoneCard, NominatingPanel, SendPicksBar } from "./accelerated";

afterEach(cleanup);

const labels = {
  packId: "stub",
  packName: "Stub",
  roles: [
    { id: "gk", label: "Goalkeeper" },
    { id: "st", label: "Striker" },
  ],
  tags: [{ id: "os", label: "Overseas" }],
  stats: [],
  terminology: {},
} as never;
const config = { currencyCode: "EUR", displayUnit: { label: "M", divisor: 1_000_000 } } as never;
const card = (no: number, name: string, role = "gk", tags: string[] = []): AuctionLot => ({ no, playerId: `p${no}`, name, country: "Spain", role, tags, set: "S", position: no, basePrice: 300_000 });
const pool = [card(4, "Zed Zero"), card(7, "Mo Most", "st", ["os"]), card(9, "Ned Nine")];
const counts = new Map([
  [7, 3],
  [9, 1],
]);
const who = [
  { id: "fa", name: "Ace FC", monogram: "AC", colour: "crimson", done: true },
  { id: "fb", name: "Blue FC", monogram: "BL", colour: "sky", done: false },
];

describe("MainDoneCard", () => {
  it("shows the totals read-only to a manager", () => {
    render(<MainDoneCard sold={11} unsold={23} franchises={8} />);
    expect(screen.getByText("Main pool done")).toBeTruthy();
    expect(screen.getByText(/23 players went unsold/)).toBeTruthy();
    expect(screen.getByTestId("main-done-sold").textContent).toBe("11");
    expect(screen.getByText(/Waiting for the Commissioner/)).toBeTruthy();
    expect(screen.queryByTestId("open-round")).toBeNull();
    expect(screen.queryByTestId("end-auction")).toBeNull();
  });

  it("gives the Commissioner the two choices and nothing else", () => {
    const onOpenRound = vi.fn();
    const onEnd = vi.fn();
    render(<MainDoneCard sold={11} unsold={23} franchises={8} commish={{ busy: false, onOpenRound, onEnd }} />);
    expect(screen.queryByText(/Waiting for the Commissioner/)).toBeNull();
    fireEvent.click(screen.getByTestId("open-round"));
    fireEvent.click(screen.getByTestId("end-auction"));
    expect(onOpenRound).toHaveBeenCalledTimes(1);
    expect(onEnd).toHaveBeenCalledTimes(1);
  });

  it("locks both buttons while a choice is on its way", () => {
    render(<MainDoneCard sold={1} unsold={1} franchises={2} commish={{ busy: true, onOpenRound: vi.fn(), onEnd: vi.fn() }} />);
    expect((screen.getByTestId("open-round") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByTestId("end-auction") as HTMLButtonElement).disabled).toBe(true);
  });
});

describe("NominatingPanel", () => {
  const base = { pool, counts, labels, config, franchises: who, doneCount: 1, onToggle: vi.fn() };

  it("lists the pool most-picked first with the pick count of every lot", () => {
    render(<NominatingPanel {...base} openSlots={3} selected={[]} sent={false} />);
    const rows = screen.getAllByTestId("pick-row");
    expect(rows.map((r) => within(r).getByTestId("pick-name").textContent)).toEqual(["Mo Most", "Ned Nine", "Zed Zero"]);
    expect(within(rows[0]).getByTestId("pick-count").textContent).toBe("3 picks");
    expect(within(rows[1]).getByTestId("pick-count").textContent).toBe("1 pick");
    expect(within(rows[2]).getByTestId("pick-count").textContent).toBe("0 picks");
    expect(within(rows[0]).getByText("Striker")).toBeTruthy();
    expect(within(rows[0]).getByText("Overseas")).toBeTruthy();
    expect(screen.getByText(/You have 3 open squad slots, so pick up to 3/)).toBeTruthy();
    expect(screen.getByTestId("nom-done").textContent).toBe("1/2");
    expect(screen.getByTestId("nom-mine").textContent).toBe("0 of 3");
  });

  it("toggles a lot by number, and stops adding at the open slots", () => {
    const onToggle = vi.fn();
    const { rerender } = render(<NominatingPanel {...base} onToggle={onToggle} openSlots={1} selected={[]} sent={false} />);
    fireEvent.click(screen.getAllByTestId("pick-row")[0]);
    expect(onToggle).toHaveBeenCalledWith(7);
    rerender(<NominatingPanel {...base} onToggle={onToggle} openSlots={1} selected={[7]} sent={false} />);
    expect(screen.getAllByTestId("pick-row")[0].getAttribute("aria-pressed")).toBe("true");
    onToggle.mockClear();
    fireEvent.click(screen.getAllByTestId("pick-row")[1]);
    expect(onToggle).not.toHaveBeenCalled();
    fireEvent.click(screen.getAllByTestId("pick-row")[0]);
    expect(onToggle).toHaveBeenCalledWith(7);
  });

  it("is read-only once the picks are in", () => {
    const onToggle = vi.fn();
    render(<NominatingPanel {...base} onToggle={onToggle} openSlots={3} selected={[9]} sent />);
    fireEvent.click(screen.getAllByTestId("pick-row")[1]);
    expect(onToggle).not.toHaveBeenCalled();
    expect(screen.getAllByTestId("pick-row")[1].getAttribute("aria-pressed")).toBe("true");
  });

  it("shows the Commissioner the franchises still picking and End round with the lot count", () => {
    const onEndRound = vi.fn();
    render(<NominatingPanel {...base} openSlots={undefined} selected={[]} sent={false} commish={{ queued: 2, waiting: 1, busy: false, onEndRound }} />);
    expect(screen.queryByTestId("nom-mine")).toBeNull();
    expect(screen.getAllByTestId("pick-row").every((r) => (r as HTMLButtonElement).disabled)).toBe(true);
    expect(screen.getByTitle("Picked")).toBeTruthy();
    expect(screen.getByTitle("Picking")).toBeTruthy();
    fireEvent.click(screen.getByTestId("end-round"));
    expect(screen.getByTestId("end-round").textContent).toContain("End round · play 2 lots");
    expect(screen.getByText(/closes picking for the 1 still choosing/)).toBeTruthy();
    expect(onEndRound).toHaveBeenCalledTimes(1);
  });

  it("says so when the viewer has no open slots", () => {
    render(<NominatingPanel {...base} openSlots={0} selected={[]} sent />);
    expect(screen.getByText(/Your squad is full/)).toBeTruthy();
    expect(screen.queryByText(/pick up to 0/)).toBeNull();
  });

  it("does not show Commissioner controls to a manager", () => {
    render(<NominatingPanel {...base} openSlots={3} selected={[]} sent={false} />);
    expect(screen.queryByTestId("end-round")).toBeNull();
  });
});

describe("SendPicksBar", () => {
  it("is disabled with no picks and sends the count otherwise", () => {
    const onSend = vi.fn();
    const { rerender } = render(<SendPicksBar picked={0} sent={false} waiting={1} busy={false} onSend={onSend} />);
    const button = screen.getByTestId("send-picks") as HTMLButtonElement;
    expect(button.textContent).toBe("Pick at least one");
    expect(button.disabled).toBe(true);
    rerender(<SendPicksBar picked={2} sent={false} waiting={1} busy={false} onSend={onSend} />);
    fireEvent.click(screen.getByTestId("send-picks"));
    expect(screen.getByTestId("send-picks").textContent).toBe("Send my 2 picks");
    expect(onSend).toHaveBeenCalledTimes(1);
  });

  it("says the picks are in and who is still to go", () => {
    render(<SendPicksBar picked={2} sent waiting={3} busy={false} onSend={vi.fn()} />);
    expect(screen.queryByTestId("send-picks")).toBeNull();
    expect(screen.getByTestId("picks-in").textContent).toContain("Picks in. Waiting on 3 franchises.");
  });

  it("tells a franchise with no open slots there is nothing to pick, instead of saying picks are in", () => {
    render(<SendPicksBar picked={0} sent noSlots waiting={1} busy={false} onSend={vi.fn()} />);
    expect(screen.queryByTestId("picks-in")).toBeNull();
    expect(screen.getByTestId("no-slots").textContent).toContain("Your squad is full, so there is nothing to pick. Waiting on 1 franchise.");
  });
});
