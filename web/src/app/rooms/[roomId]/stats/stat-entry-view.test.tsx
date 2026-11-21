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
  getStatMatches: vi.fn(),
  getStatMatchPlayers: vi.fn(),
  getStatSheet: vi.fn(),
  saveStatSheet: vi.fn(),
  clearStatFields: vi.fn(),
}));

import {
  ApiError,
  clearStatFields,
  getMembers,
  getRoom,
  getStatMatchPlayers,
  getStatMatches,
  getStatSheet,
  saveStatSheet,
  type MatchPlayers,
  type Member,
  type Room,
  type SheetResult,
  type StatMatch,
  type StatSheet,
} from "../../../../api/client";
import { StatEntryView } from "./stat-entry-view";

const room = (over: Record<string, unknown> = {}) =>
  ({
    id: "room-1",
    state: "auction_live",
    myRole: "commissioner",
    myFranchiseId: null,
    commissioner: { id: "u-ann", displayName: "Ann" },
    config: { name: "Friends" },
    packLabels: { roles: [{ id: "A", label: "Attacker", short: "ATT" }], stats: [], tags: [], restrictedCategories: [], terminology: {} },
    ...over,
  }) as unknown as Room;

const members = [{ userId: "u-ann", displayName: "Ann", role: "commissioner", franchise: null }] as unknown as Member[];

const matches: StatMatch[] = [
  { matchId: "m1", n: 1, label: null, startsAt: "2031-04-01T10:00:00Z", chip: "PROVIDER", manualPlayers: 0 },
  { matchId: "m2", n: 2, label: "Eliminator", startsAt: "2031-04-02T10:00:00Z", chip: "PROVIDER_MANUAL", manualPlayers: 1 },
  { matchId: "m3", n: 3, label: null, startsAt: "2031-04-09T10:00:00Z", chip: "UPCOMING", manualPlayers: 0 },
];

const kites = { id: "f-kites", name: "Kochi Kites", shortName: "KK", colour: "sky" };

const players = (over: Partial<MatchPlayers> = {}): MatchPlayers => ({
  match: matches[1],
  withValues: 1,
  poolSize: 3,
  players: [
    { poolPlayerId: "p1", name: "Sid Rao", role: "A", owner: kites, chip: "PROVIDER_MANUAL", total: 52 },
    { poolPlayerId: "p2", name: "Kiran Desai", role: "A", chip: null, total: null },
    { poolPlayerId: "p3", name: "Yash Gupta", role: "A", chip: null, total: null },
  ],
  ...over,
});

const field = (over: Record<string, unknown>) => ({
  stat: "score",
  eventType: "",
  label: "Score",
  kind: "integer",
  min: 0,
  max: 120,
  input: "number",
  provider: null,
  manual: null,
  ...over,
});

const sheet = (over: Partial<StatSheet> = {}): StatSheet =>
  ({
    match: matches[1],
    poolPlayerId: "p1",
    name: "Sid Rao",
    role: "A",
    owner: kites,
    points: 52,
    providerOnly: 46,
    fields: [
      field({ provider: 46, manual: 52 }),
      field({ stat: "extra", label: "Extra", provider: 6 }),
      field({ stat: "starter", label: "Starter", input: "yes_no", max: 1, provider: 1 }),
      field({ stat: "typed", eventType: "kind-a", label: "Typed", max: 10 }),
    ],
    ...over,
  }) as unknown as StatSheet;

const result = (over: Partial<SheetResult> = {}): SheetResult =>
  ({ before: 52, after: 58, providerOnly: 46, franchise: { id: "f-kites", delta: 6 }, changedFields: 1, sheet: sheet(), ...over }) as unknown as SheetResult;

beforeEach(() => {
  vi.mocked(getRoom).mockResolvedValue(room());
  vi.mocked(getMembers).mockResolvedValue(members);
  vi.mocked(getStatMatches).mockResolvedValue(matches);
  vi.mocked(getStatMatchPlayers).mockResolvedValue(players());
  vi.mocked(getStatSheet).mockResolvedValue(sheet());
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const open = async () => {
  render(<StatEntryView roomId="room-1" />);
  await screen.findByTestId("stat-sheet");
};

describe("StatEntryView lists (App Screens 9f, 9h)", () => {
  it("opens the newest started match, lists matches newest first and cannot open an upcoming one", async () => {
    await open();

    expect(getStatMatchPlayers).toHaveBeenCalledWith("room-1", "m2");
    const rows = screen.getAllByTestId("stat-match");
    expect(rows.map((r) => r.getAttribute("data-chip"))).toEqual(["UPCOMING", "PROVIDER_MANUAL", "PROVIDER"]);
    expect(rows[0].hasAttribute("disabled")).toBe(true);
    expect(within(rows[1]).getByText("Match 2 · Eliminator")).toBeTruthy();
    expect(within(rows[1]).getByText("PROVIDER + 1 MANUAL")).toBeTruthy();
    expect(within(rows[2]).getByText("Match 1")).toBeTruthy();
    fireEvent.click(rows[0]);
    expect(getStatMatchPlayers).toHaveBeenCalledTimes(1);

    fireEvent.click(rows[2]);
    await waitFor(() => expect(getStatMatchPlayers).toHaveBeenCalledWith("room-1", "m1"));
  });

  it("lists the whole pool with the owner chip, the source chip, the points and the n of N count", async () => {
    await open();

    expect(screen.getByTestId("stat-player-count").textContent).toBe("1 of 3 players");
    const rows = screen.getAllByTestId("stat-player");
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByText("MANUAL")).toBeTruthy();
    expect(within(rows[0]).getByText("52")).toBeTruthy();
    expect(within(rows[0]).getByText("ATT")).toBeTruthy();
    expect(within(rows[1]).getByText("NOT ENTERED")).toBeTruthy();
    expect(rows[0].getAttribute("aria-current")).toBe("true");
  });

  it("says who can enter stats when the caller is not the Commissioner, without reading any stat route", async () => {
    vi.mocked(getRoom).mockResolvedValue(room({ myRole: "manager", myFranchiseId: "f-x" }));
    render(<StatEntryView roomId="room-1" />);

    expect(await screen.findByText("Only the Commissioner enters stats")).toBeTruthy();
    expect(getStatMatches).not.toHaveBeenCalled();
  });
});

describe("StatEntryView sheet form (App Screens 9f, 9g)", () => {
  it("draws a field per stat, per event type and a yes/no from the schema, with manual values bordered", async () => {
    await open();

    const fields = screen.getAllByTestId("stat-field");
    expect(fields).toHaveLength(4);
    expect((screen.getByLabelText("Score") as HTMLInputElement).value).toBe("52");
    expect((screen.getByLabelText("Extra") as HTMLInputElement).value).toBe("6");
    expect(screen.getByLabelText("Typed · Kind a")).toBeTruthy();
    expect(screen.getByRole("switch", { name: "Starter" }).getAttribute("aria-checked")).toBe("true");
    expect(screen.getByText("MANUAL · BEATS PROVIDER")).toBeTruthy();
    expect(screen.getByText("Kochi Kites · ATT · provider said 46 pts")).toBeTruthy();
  });

  it("saves only the fields that were typed into and shows before, after and the owner's change", async () => {
    vi.mocked(saveStatSheet).mockResolvedValue(result());
    const user = userEvent.setup();
    await open();

    await user.clear(screen.getByLabelText("Score"));
    await user.type(screen.getByLabelText("Score"), "58");
    await user.type(screen.getByLabelText("Typed · Kind a"), "2");
    await user.click(screen.getByRole("switch", { name: "Starter" }));
    await user.click(screen.getByTestId("stat-save"));

    await waitFor(() => expect(saveStatSheet).toHaveBeenCalledTimes(1));
    expect(saveStatSheet).toHaveBeenCalledWith("room-1", "m2", "p1", [
      { stat: "score", eventType: "", value: 58 },
      { stat: "starter", eventType: "", value: 0 },
      { stat: "typed", eventType: "kind-a", value: 2 },
    ]);
    const toast = await screen.findByRole("status");
    expect(toast.textContent).toContain("Saved · Match 2 recomputed");
    expect(toast.textContent).toContain("Sid Rao 52 to 58 pts. Kochi Kites +6.");
  });

  it("does not send a provider value that was only shown, and points an empty save to Clear override", async () => {
    const user = userEvent.setup();
    await open();

    await user.click(screen.getByTestId("stat-save"));

    expect(saveStatSheet).not.toHaveBeenCalled();
    expect((await screen.findByTestId("stat-banner")).textContent).toContain("Clear override");
  });

  it("keeps what was typed and names the field when the server refuses the sheet", async () => {
    vi.mocked(saveStatSheet).mockRejectedValue(
      new ApiError(422, { error: "invalid_stat_sheet", fields: [{ stat: "extra", eventType: "", min: 0, max: 120, reason: "above_max" }] }),
    );
    const user = userEvent.setup();
    await open();

    await user.clear(screen.getByLabelText("Extra"));
    await user.type(screen.getByLabelText("Extra"), "412");
    await user.type(screen.getByLabelText("Typed · Kind a"), "3");
    await user.click(screen.getByTestId("stat-save"));

    expect((await screen.findByTestId("stat-banner")).textContent).toContain("1 field needs fixing. Nothing was saved.");
    expect(screen.getByText("Whole number, 0 to 120.")).toBeTruthy();
    expect((screen.getByLabelText("Extra") as HTMLInputElement).value).toBe("412");
    expect((screen.getByLabelText("Typed · Kind a") as HTMLInputElement).value).toBe("3");
    expect(screen.getByLabelText("Extra").getAttribute("aria-invalid")).toBe("true");
    await user.clear(screen.getByLabelText("Extra"));
    await user.type(screen.getByLabelText("Extra"), "41");
    expect(screen.queryByText("Whole number, 0 to 120.")).toBeNull();
  });

  it("names a value that is not a number without sending anything", async () => {
    const user = userEvent.setup();
    await open();

    await user.type(screen.getByLabelText("Typed · Kind a"), "x");
    await user.click(screen.getByTestId("stat-save"));

    expect(saveStatSheet).not.toHaveBeenCalled();
    expect((await screen.findByTestId("stat-banner")).textContent).toContain("1 field needs fixing. Nothing was saved.");
  });
});

describe("StatEntryView clear (App Screens 9j)", () => {
  it("confirms with the points and the owner's drop, then clears every overridden field", async () => {
    vi.mocked(clearStatFields).mockResolvedValue(result({ before: 52, after: 46, franchise: { id: "f-kites", delta: -6 } as never }));
    const user = userEvent.setup();
    await open();

    await user.click(screen.getByTestId("stat-clear"));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Clear Sid Rao's override?")).toBeTruthy();
    expect(within(dialog).getByText(/Match 2 goes back to provider data for Sid Rao: 52 to 46 pts, and Kochi Kites drop 6\./)).toBeTruthy();
    await user.click(within(dialog).getByRole("button", { name: "Clear override" }));

    await waitFor(() => expect(clearStatFields).toHaveBeenCalledWith("room-1", "m2", "p1", [{ stat: "score", eventType: "" }]));
    expect((await screen.findByRole("status")).textContent).toContain("Kochi Kites −6");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("offers Clear override only when the player has a manual value", async () => {
    vi.mocked(getStatSheet).mockResolvedValue(sheet({ fields: [field({ provider: 46 })] } as never));
    await open();

    expect(screen.queryByTestId("stat-clear")).toBeNull();
    expect(within(screen.getByTestId("stat-sheet")).getByText("PROVIDER")).toBeTruthy();
  });
});

describe("StatEntryView stale tab (AC-19)", () => {
  it("reads again when the tab comes back and shows the provider's new values; a save still works", async () => {
    await open();
    vi.mocked(getStatSheet).mockResolvedValue(sheet({ fields: [field({ provider: 99, manual: 52 }), field({ stat: "extra", label: "Extra", provider: 9 })] } as never));
    vi.mocked(saveStatSheet).mockResolvedValue(result({ sheet: sheet({ fields: [field({ provider: 99, manual: 52 }), field({ stat: "extra", label: "Extra", provider: 9, manual: 8 })] } as never) }));

    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await waitFor(() => expect((screen.getByLabelText("Extra") as HTMLInputElement).value).toBe("9"));

    const user = userEvent.setup();
    await user.clear(screen.getByLabelText("Extra"));
    await user.type(screen.getByLabelText("Extra"), "8");
    await user.click(screen.getByTestId("stat-save"));
    await waitFor(() => expect(saveStatSheet).toHaveBeenCalled());
    expect(await screen.findByRole("status")).toBeTruthy();
  });

  it("tells the person when the match is no longer open for entry", async () => {
    vi.mocked(saveStatSheet).mockRejectedValue(new ApiError(409, { error: "match_not_started" }));
    const user = userEvent.setup();
    await open();

    await user.type(screen.getByLabelText("Typed · Kind a"), "2");
    await user.click(screen.getByTestId("stat-save"));

    expect((await screen.findByTestId("stat-banner")).textContent).toContain("has not started");
    expect(getStatMatches).toHaveBeenCalledTimes(2);
  });
});
