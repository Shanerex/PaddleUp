// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("../../../../api/client", async (orig) => ({
  ...(await orig<typeof import("../../../../api/client")>()),
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  getAudit: vi.fn(),
}));

import { ApiError, getAudit, getMembers, getRoom, type AuditEntry, type Member, type Room } from "../../../../api/client";
import { AuditView } from "./audit-view";

afterEach(cleanup);

const config = { name: "Friends", currencyCode: "EUR", displayUnit: { label: "M", divisor: 100 } };
const room = { id: "r1", state: "auction_live", myRole: "manager", myFranchiseId: "fa", config } as unknown as Room;
const members = [
  { userId: "a", displayName: "Ann", role: "manager", franchise: { id: "fa", name: "Red FC", monogram: "RF", colour: "rose" } },
  { userId: "b", displayName: "Ben", role: "manager", franchise: { id: "fb", name: "Blue FC", monogram: "BF", colour: "sky" } },
] as unknown as Member[];
const entry = (seq: number, over: Partial<AuditEntry>): AuditEntry => ({ seq, at: `2030-03-01T10:00:0${seq}Z`, category: "bids", kind: "bid_accepted", detail: {}, ...over });

beforeEach(() => {
  vi.mocked(getRoom).mockResolvedValue(room);
  vi.mocked(getMembers).mockResolvedValue(members);
});

describe("AuditView", () => {
  it("lists entries newest first with the tag, and narrows them with a filter", async () => {
    vi.mocked(getAudit).mockResolvedValue({
      entries: [
        entry(1, { franchiseId: "fb", lot: 3, detail: { amount: 200 } }),
        entry(2, { category: "passes", kind: "passed", franchiseId: "fa", lot: 3 }),
        entry(3, { category: "admin", kind: "lot_reopened", lot: 2, player: "Pat Reed", detail: { reason: "Wrong paddle" } }),
      ],
      lastSeq: 3,
      more: false,
      serverNow: "",
    });
    render(<AuditView roomId="r1" />);

    const rows = await screen.findAllByTestId("audit-row");
    expect(rows.map((r) => r.getAttribute("data-kind"))).toEqual(["lot_reopened", "passed", "bid_accepted"]);
    expect(rows[0].textContent).toContain('Commissioner re-opened Lot 2 · Pat Reed · "Wrong paddle"');
    expect(rows[0].textContent).toContain("ADMIN");
    expect(rows[1].textContent).toContain("You passed · Lot 3");
    expect(rows[2].textContent).toContain("Blue FC bid €2 M · Lot 3");

    await userEvent.click(screen.getByTestId("filter-passes"));
    expect(screen.getAllByTestId("audit-row")).toHaveLength(1);
    await userEvent.click(screen.getByTestId("filter-scoring"));
    expect(screen.getByTestId("audit-empty").textContent).toContain("Nothing logged yet");
  });

  it("shows the empty state for a log with nothing in it", async () => {
    vi.mocked(getAudit).mockResolvedValue({ entries: [], lastSeq: 0, more: false, serverNow: "" });
    render(<AuditView roomId="r1" />);
    expect((await screen.findByTestId("audit-empty")).textContent).toContain("New entries appear here live, newest first.");
  });

  it("reads every page of a long log", async () => {
    vi.mocked(getAudit)
      .mockResolvedValueOnce({ entries: [entry(1, { franchiseId: "fa", lot: 1 })], lastSeq: 1, more: true, serverNow: "" })
      .mockResolvedValueOnce({ entries: [entry(2, { franchiseId: "fb", lot: 1 })], lastSeq: 2, more: false, serverNow: "" });
    render(<AuditView roomId="r1" />);
    expect(await screen.findAllByTestId("audit-row")).toHaveLength(2);
    expect(getAudit).toHaveBeenNthCalledWith(2, "r1", "all", 1);
  });

  it("says so for a room the viewer is not in", async () => {
    vi.mocked(getRoom).mockRejectedValue(new ApiError(404, null));
    render(<AuditView roomId="r1" />);
    expect(within(await screen.findByRole("alert")).getByText("Can't find that room")).toBeTruthy();
  });
});
