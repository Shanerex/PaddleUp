// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
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
  getMe: vi.fn().mockResolvedValue(null),
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  getPool: vi.fn(),
  addPoolPlayer: vi.fn(),
  editPoolPlayer: vi.fn(),
  removePoolPlayer: vi.fn(),
  resetPool: vi.fn(),
}));

import {
  addPoolPlayer,
  ApiError,
  editPoolPlayer,
  getMembers,
  getPool,
  getRoom,
  removePoolPlayer,
  resetPool,
  type Member,
  type Pool,
  type Room,
  type RoomState,
} from "../../../../api/client";
import { PoolView } from "./pool-view";

const config = {
  name: "Friends",
  competition: "Season 1",
  minManagers: 2,
  maxManagers: 10,
  purse: 10000,
  currencyCode: "EUR",
  displayUnit: { label: "M", divisor: 100 },
  incrementLadder: [{ from: 0, step: 5 }],
  squadMin: 11,
  squadMax: 18,
  roleMinimums: {},
  categoryQuotas: {},
  retention: {},
  scoringRules: { rules: [] },
  ai: { enabled: false, lifelines: {}, reportCardVisibility: "room" },
};

function room(state: RoomState = "setup", myRole = "commissioner"): Room {
  return {
    id: "room-1",
    state,
    myRole,
    commissioner: { id: "u-ann", displayName: "Ann" },
    configVersion: 1,
    franchiseCount: 2,
    myFranchiseId: null,
    advanceTo: [],
    createdAt: "2031-01-01T00:00:00Z",
    packLabels: {
      packId: "p",
      packName: "Pack Name",
      packVersion: "1",
      roles: [
        { id: "R1", label: "First" },
        { id: "R2", label: "Second" },
      ],
      tags: [
        { id: "a", label: "Alpha" },
        { id: "b", label: "Beta" },
      ],
      restrictedCategories: [],
      stats: [],
      terminology: {},
    },
    config,
  } as unknown as Room;
}

const members = [{ userId: "u-ann", displayName: "Ann", avatar: "preset-01", role: "commissioner", franchise: null }] as unknown as Member[];

function pool(over: Partial<Pool> = {}): Pool {
  return {
    hasPool: true,
    version: 4,
    datasetVersion: 3,
    latestDatasetVersion: 3,
    editCount: 2,
    canEdit: true,
    players: [
      { id: "p1", name: "Kiran Desai", country: "India", role: "R1", tags: ["a"], basePrice: 150, set: "Marquee", origin: "edited" },
      { id: "p2", name: "Sid Rao", country: "Chile", role: "R2", tags: ["a", "b"], basePrice: 30, set: "Uncapped", origin: "source" },
      { id: "p3", name: "Aarav Menon", country: "India", role: "R2", tags: [], basePrice: 20, set: "Uncapped", origin: "added", providerId: "x1" },
    ],
    sets: [
      { name: "Marquee", players: 1 },
      { name: "Uncapped", players: 2 },
    ],
    ...over,
  } as Pool;
}

const api = (status: number, body: unknown) => new ApiError(status, body);

function setup(opts: { state?: RoomState; role?: string; pool?: Pool } = {}) {
  vi.mocked(getRoom).mockResolvedValue(room(opts.state, opts.role));
  vi.mocked(getMembers).mockResolvedValue(members);
  vi.mocked(getPool).mockResolvedValue(opts.pool ?? pool());
  return render(<PoolView roomId="room-1" />);
}

beforeEach(() => {
  vi.mocked(addPoolPlayer).mockReset();
  vi.mocked(editPoolPlayer).mockReset();
  vi.mocked(removePoolPlayer).mockReset();
  vi.mocked(resetPool).mockReset();
});
afterEach(cleanup);

describe("PoolView reading (Room pool, poolMember, poolLive)", () => {
  it("shows the Commissioner the pool with edit controls, pack labels and edit flags", async () => {
    setup();

    expect(await screen.findByText("Pack Name · season data v3 · 3 players · 2 edits")).toBeTruthy();
    expect(screen.getByRole("button", { name: /Add player/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /Reset to latest/ })).toBeTruthy();
    expect(screen.getAllByRole("button", { name: /^Edit / })).toHaveLength(3);
    expect(screen.getByText("EDITED", { selector: "span.flagTop, span[class*=flagTop]" })).toBeTruthy();
    const chips = screen.getByRole("group", { name: "Filter by role" });
    expect(within(chips).getByText("First")).toBeTruthy();
    expect(within(chips).getByText("Second")).toBeTruthy();
    expect(screen.getByText("Showing 3 of 3.")).toBeTruthy();
  });

  it("shows a member the same pool with no edit controls and the member note", async () => {
    setup({ role: "manager", pool: pool({ canEdit: false, editCount: 2 }) });

    expect(await screen.findByText("Kiran Desai")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Add player/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Reset to latest/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /^Edit / })).toBeNull();
    expect(screen.queryByText("EDITED")).toBeNull();
    expect(screen.getByText("Ann can change the pool until the auction goes live. You can look, not touch.")).toBeTruthy();
    expect(screen.getByText("Pack Name · season data v3 · 3 players")).toBeTruthy();
    expect(screen.getByRole("link", { name: /See set order/ }).getAttribute("href")).toBe("/rooms/room-1/sets");
  });

  it("removes the controls and says the pool is locked once the auction is live", async () => {
    setup({ state: "auction_live", pool: pool({ canEdit: false }) });

    expect(await screen.findByText("Locked. The auction is live, so nobody can change the pool now.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Add player/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /^Edit / })).toBeNull();
    expect(screen.getByText(/locked$/)).toBeTruthy();
  });

  it("tells the Commissioner when newer season data is out", async () => {
    setup({ pool: pool({ latestDatasetVersion: 4 }) });

    expect(await screen.findByText("Season data v4 is out.")).toBeTruthy();
    expect(screen.getByText(/Your pool is a copy of v3 with 2 edits\. Reset to switch to v4\./)).toBeTruthy();
  });

  it("filters by search text and by role chip", async () => {
    setup();
    const user = userEvent.setup();
    await screen.findByText("Kiran Desai");

    await user.type(screen.getByLabelText("Search players or countries"), "chile");
    expect(screen.queryByText("Kiran Desai")).toBeNull();
    expect(screen.getByText("Sid Rao")).toBeTruthy();

    await user.clear(screen.getByLabelText("Search players or countries"));
    await user.click(within(screen.getByRole("group", { name: "Filter by role" })).getByRole("button", { name: /First/ }));
    expect(screen.getByText("Kiran Desai")).toBeTruthy();
    expect(screen.queryByText("Sid Rao")).toBeNull();
  });

  it("shows a stranger the not-found screen", async () => {
    vi.mocked(getRoom).mockRejectedValue(api(404, { error: "room_not_found" }));
    vi.mocked(getMembers).mockRejectedValue(api(404, { error: "room_not_found" }));
    vi.mocked(getPool).mockRejectedValue(api(404, { error: "room_not_found" }));
    render(<PoolView roomId="room-1" />);

    expect(await screen.findByText("Can't find that room")).toBeTruthy();
  });
});

describe("PoolView empty (poolEmpty)", () => {
  const empty = pool({ hasPool: false, version: 0, datasetVersion: null, latestDatasetVersion: null, editCount: 0, players: [], sets: [] });

  it("explains there is no pool and disables Load season data while there is no dataset", async () => {
    setup({ pool: empty });

    expect(await screen.findByText("No player pool yet")).toBeTruthy();
    expect(screen.getByText(/Nobody has loaded season data for Pack Name yet/)).toBeTruthy();
    expect((screen.getByRole("button", { name: /Load season data/ }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("Turns on as soon as there's season data to load.")).toBeTruthy();
  });

  it("loads the season data with version 0 once a dataset exists", async () => {
    const user = userEvent.setup();
    setup({ pool: { ...empty, latestDatasetVersion: 2 } });
    vi.mocked(resetPool).mockResolvedValue(pool());

    await user.click(await screen.findByRole("button", { name: /Load season data/ }));

    expect(resetPool).toHaveBeenCalledWith("room-1", 0);
    expect(await screen.findByText("Kiran Desai")).toBeTruthy();
  });

  it("gives a member the empty state with no button", async () => {
    setup({ role: "manager", pool: { ...empty, canEdit: false } });

    expect(await screen.findByText("No player pool yet")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Load season data/ })).toBeNull();
  });
});

describe("PoolView add and edit sheet (poolAdd, poolEdit)", () => {
  it("adds a player with the pack's roles, tags and the typed price, then shows the new pool", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(addPoolPlayer).mockResolvedValue(
      pool({ version: 5, players: [...pool().players, { id: "p4", name: "New One", country: "Peru", role: "R2", tags: ["b"], basePrice: 40, set: "Marquee", origin: "added" } as never] }),
    );

    await user.click(await screen.findByRole("button", { name: /Add player/ }));
    const sheet = screen.getByRole("dialog", { name: "Add player" });
    await user.type(within(sheet).getByLabelText("Name"), "New One");
    await user.type(within(sheet).getByLabelText("Country"), "Peru");
    await user.click(within(sheet).getByRole("radio", { name: /Second/ }));
    await user.click(within(sheet).getByRole("button", { name: /Beta/ }));
    await user.type(within(sheet).getByLabelText("Base price"), "€0.4 M");
    await user.selectOptions(within(sheet).getByLabelText("Set"), "Marquee");
    await user.click(within(sheet).getByRole("button", { name: "Add to pool" }));

    expect(addPoolPlayer).toHaveBeenCalledWith("room-1", {
      version: 4,
      name: "New One",
      country: "Peru",
      role: "R2",
      tags: ["b"],
      basePrice: 40,
      set: "Marquee",
      providerId: undefined,
    });
    expect(await screen.findByText("New One")).toBeTruthy();
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("prefills an edit, saves it by player id and closes", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(editPoolPlayer).mockResolvedValue(pool({ version: 5 }));

    await user.click((await screen.findAllByRole("button", { name: "Edit Kiran Desai" }))[0]);
    const sheet = screen.getByRole("dialog", { name: "Edit player" });
    expect((within(sheet).getByLabelText("Name") as HTMLInputElement).value).toBe("Kiran Desai");
    expect((within(sheet).getByLabelText("Base price") as HTMLInputElement).value).toBe("€1.5 M");
    await user.clear(within(sheet).getByLabelText("Name"));
    await user.type(within(sheet).getByLabelText("Name"), "Kiran D");
    await user.click(within(sheet).getByRole("button", { name: "Save changes" }));

    expect(editPoolPlayer).toHaveBeenCalledWith("room-1", "p1", expect.objectContaining({ version: 4, name: "Kiran D", basePrice: 150, role: "R1", tags: ["a"], set: "Marquee" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("keeps the sheet open and shows the server's field error when a field is rejected", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(editPoolPlayer).mockRejectedValue(
      api(422, { error: "invalid_player", violations: [{ field: "base_price", message: "That base price isn't allowed in Pack Name." }] }),
    );

    await user.click((await screen.findAllByRole("button", { name: "Edit Kiran Desai" }))[0]);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("That base price isn't allowed in Pack Name. Nothing was saved.")).toBeTruthy();
    expect(screen.getByRole("dialog", { name: "Edit player" })).toBeTruthy();
    expect(getPool).toHaveBeenCalledTimes(1);
  });

  it("does not call the server when the typed price is not a number", async () => {
    const user = userEvent.setup();
    setup();

    await user.click((await screen.findAllByRole("button", { name: "Edit Kiran Desai" }))[0]);
    const sheet = screen.getByRole("dialog");
    await user.clear(within(sheet).getByLabelText("Base price"));
    await user.type(within(sheet).getByLabelText("Base price"), "lots");
    await user.click(within(sheet).getByRole("button", { name: "Save changes" }));

    expect(editPoolPlayer).not.toHaveBeenCalled();
    expect(await screen.findByText(/Enter a base price like/)).toBeTruthy();
  });
});

describe("PoolView remove and reset (poolRemove, poolReset)", () => {
  it("confirms a removal, calls the endpoint with the pool version and shows the reloaded pool", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(removePoolPlayer).mockResolvedValue(pool({ version: 5, players: pool().players.slice(1) }));

    await user.click((await screen.findAllByRole("button", { name: "Edit Kiran Desai" }))[0]);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /Remove/ }));
    expect(screen.getByText("Remove Kiran Desai from the pool?")).toBeTruthy();
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remove" }));

    expect(removePoolPlayer).toHaveBeenCalledWith("room-1", "p1", 4);
    await waitFor(() => expect(screen.queryByText("Kiran Desai")).toBeNull());
  });

  it("confirms a reset to the latest version and replaces the pool", async () => {
    const user = userEvent.setup();
    setup({ pool: pool({ latestDatasetVersion: 4 }) });
    vi.mocked(resetPool).mockResolvedValue(pool({ version: 5, datasetVersion: 4, latestDatasetVersion: 4, editCount: 0 }));

    await user.click(await screen.findByRole("button", { name: /Reset to latest/ }));
    expect(screen.getByText("Reset the pool to v4?")).toBeTruthy();
    expect(screen.getByText(/Your 2 edits are thrown away, and you can't undo this\./)).toBeTruthy();
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Reset pool" }));

    expect(resetPool).toHaveBeenCalledWith("room-1", 4);
    expect(await screen.findByText("Pack Name · season data v4 · 3 players · 0 edits")).toBeTruthy();
  });

  it("cancels without calling anything", async () => {
    const user = userEvent.setup();
    setup();

    await user.click(await screen.findByRole("button", { name: /Reset to latest/ }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }));

    expect(resetPool).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

describe("PoolView 409 handling (poolStale, poolLive)", () => {
  it("reloads the pool and shows the stale banner naming the lost edit", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(editPoolPlayer).mockRejectedValue(api(409, { error: "stale_version" }));
    await user.click((await screen.findAllByRole("button", { name: "Edit Kiran Desai" }))[0]);
    vi.mocked(getPool).mockResolvedValue(pool({ version: 6, players: pool().players.slice(1) }));

    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("The pool changed in another tab.")).toBeTruthy();
    expect(screen.getByText("We loaded the latest version. Your edit to Kiran Desai wasn't saved, so make it again.")).toBeTruthy();
    await waitFor(() => expect(screen.queryByText("Kiran Desai")).toBeNull());
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(getPool).toHaveBeenCalledTimes(2);
  });

  it("reloads, shows the locked banner and removes the controls when the auction has started", async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(addPoolPlayer).mockRejectedValue(api(409, { error: "room_locked" }));
    await user.click(await screen.findByRole("button", { name: /Add player/ }));
    vi.mocked(getRoom).mockResolvedValue(room("auction_live"));
    vi.mocked(getPool).mockResolvedValue(pool({ canEdit: false }));
    const sheet = screen.getByRole("dialog");
    await user.type(within(sheet).getByLabelText("Base price"), "0.2");
    await user.click(within(sheet).getByRole("button", { name: "Add to pool" }));

    expect(await screen.findByText("Too late for that.")).toBeTruthy();
    expect(screen.getByText("The auction has started, so the pool is locked. Your change wasn't saved.")).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole("button", { name: /Add player/ })).toBeNull());
    expect(screen.queryByRole("button", { name: /^Edit / })).toBeNull();
    expect(screen.getByText("Locked. The auction is live, so nobody can change the pool now.")).toBeTruthy();
  });
});
