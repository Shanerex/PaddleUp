// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const push = vi.fn();
const replace = vi.fn();
const router = { push, replace };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../api/client")>()),
  listPacks: vi.fn(),
  createRoom: vi.fn(),
  getRoom: vi.fn(),
  getMembers: vi.fn(),
  getInvite: vi.fn(),
  saveConfig: vi.fn(),
  issueInvite: vi.fn(),
}));

import { ApiError, createRoom, getInvite, getMembers, getRoom, issueInvite, listPacks, saveConfig, type Room } from "../../api/client";
import { RoomWizard } from "./room-wizard";

const config = {
  name: "Friends",
  competition: "Spring",
  minManagers: 2,
  maxManagers: 10,
  purse: 10000,
  currencyCode: "EUR",
  displayUnit: { label: "M", divisor: 100 },
  incrementLadder: [{ from: 0, step: 5 }],
  squadMin: 11,
  squadMax: 18,
  roleMinimums: { one: 4, two: 4 },
  categoryQuotas: {},
  retention: {},
  scoringRules: { rules: [{ type: "per-unit", id: "g", stat: "goals", points: 5 }] },
  ai: { enabled: true, lifelines: { "fit-check": 1 }, reportCardVisibility: "room" },
};

function room(overrides: Record<string, unknown> = {}): Room {
  return {
    id: "room-1",
    state: "setup",
    myRole: "commissioner",
    commissioner: { id: "u1", displayName: "Ann" },
    configVersion: 1,
    franchiseCount: 0,
    myFranchiseId: null,
    advanceTo: [],
    createdAt: "2031-01-01T00:00:00Z",
    packLabels: {
      packId: "p",
      packVersion: "1",
      roles: [
        { id: "one", label: "Role one" },
        { id: "two", label: "Role two" },
      ],
      tags: [],
      restrictedCategories: [],
      stats: [{ id: "goals", label: "Goals" }],
      terminology: {},
    },
    config,
    ...overrides,
  } as unknown as Room;
}

beforeEach(() => {
  vi.mocked(listPacks).mockResolvedValue([
    { id: "p", version: "1.0.0", name: "Pack One", summary: "A test pack.", icon: "sports", presets: [{ id: "std", label: "Standard" }] },
  ]);
  vi.mocked(createRoom).mockResolvedValue(room());
  vi.mocked(getRoom).mockResolvedValue(room());
  vi.mocked(getMembers).mockResolvedValue([]);
  vi.mocked(getInvite).mockResolvedValue(null);
  vi.mocked(saveConfig).mockImplementation(async (_id, version, cfg) => room({ configVersion: version + 1, config: cfg }));
  vi.mocked(issueInvite).mockResolvedValue({ code: "ABCDEFGHJKMN", link: "http://x/join/ABCDEFGHJKMN", expiresAt: "2031-01-08T00:00:00Z" });
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const steps = () => within(screen.getByRole("list", { name: "Steps" }));
const sections = () => within(screen.getByRole("list", { name: "Sections" }));

describe("RoomWizard (Home `wizard:1-6`)", () => {
  it("starts on the Sport step with the pack's display name and presets", async () => {
    render(<RoomWizard />);

    expect(await screen.findByText("Pack One")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Choose a sport" })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "Standard" }).getAttribute("aria-checked")).toBe("true");
    expect(steps().getByRole("button", { current: "step" }).textContent).toContain("1. Sport");
  });

  it("creates the room on Basics and moves the room id into the URL", async () => {
    const user = userEvent.setup();
    render(<RoomWizard />);
    await screen.findByText("Pack One");

    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(screen.getByLabelText("Room name"), "Friends");
    await user.type(screen.getByLabelText("Season"), "Spring");
    await user.click(screen.getByRole("button", { name: "Create room" }));

    await waitFor(() =>
      expect(createRoom).toHaveBeenCalledWith({ packId: "p", presetId: "std", name: "Friends", competition: "Spring" }),
    );
    expect(replace).toHaveBeenCalledWith("/rooms/room-1/setup?step=3");
    expect(await screen.findByRole("heading", { name: "Squad rules" })).toBeTruthy();
  });

  it("refuses a one-letter room name before asking the server", async () => {
    const user = userEvent.setup();
    render(<RoomWizard />);
    await screen.findByText("Pack One");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(screen.getByLabelText("Room name"), "F");

    await user.click(screen.getByRole("button", { name: "Create room" }));

    expect(screen.getByText("Use 2–40 characters.")).toBeTruthy();
    expect(createRoom).not.toHaveBeenCalled();
  });

  it("resumes an existing room from the URL and never creates another", async () => {
    const user = userEvent.setup();
    render(<RoomWizard roomId="room-1" initialStep={3} />);

    expect(await screen.findByRole("heading", { name: "Squad rules" })).toBeTruthy();
    expect(screen.getByText("€100 M")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => expect(saveConfig).toHaveBeenCalledWith("room-1", 1, config));
    expect(createRoom).not.toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith("/rooms/room-1/setup?step=4", { scroll: false });
  });

  it("blocks Continue while role minimums exceed the squad minimum", async () => {
    vi.mocked(getRoom).mockResolvedValue(room({ config: { ...config, roleMinimums: { one: 8, two: 8 } } }));
    render(<RoomWizard roomId="room-1" initialStep={3} />);

    expect(await screen.findByText(/Role minimums add up to 16, but the squad minimum is 11/)).toBeTruthy();
    expect((screen.getByRole("button", { name: "Continue" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("loads the other save instead of overwriting it when the version is stale", async () => {
    vi.mocked(saveConfig).mockRejectedValueOnce(new ApiError(409, { error: "stale_version" }));
    vi.mocked(getRoom)
      .mockResolvedValueOnce(room())
      .mockResolvedValueOnce(room({ configVersion: 2, config: { ...config, name: "Theirs" } }));
    const user = userEvent.setup();
    render(<RoomWizard roomId="room-1" initialStep={2} />);
    const name = await screen.findByLabelText("Room name");
    await user.clear(name);
    await user.type(name, "Mine");

    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText(/Someone else saved changes/)).toBeTruthy();
    expect((screen.getByLabelText("Room name") as HTMLInputElement).value).toBe("Theirs");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await waitFor(() =>
      expect(saveConfig).toHaveBeenLastCalledWith("room-1", 2, expect.objectContaining({ name: "Theirs" })),
    );
  });

  it("saves chosen manager limits before leaving for the setup page", async () => {
    const user = userEvent.setup();
    render(<RoomWizard />);
    await screen.findByText("Pack One");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(screen.getByLabelText("Room name"), "Friends");
    await user.click(screen.getByRole("button", { name: "Increase Minimum managers" }));
    await user.click(screen.getByRole("button", { name: "Create room" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/rooms/room-1/setup?step=3"));
    expect(saveConfig).toHaveBeenCalledWith("room-1", 1, expect.objectContaining({ minManagers: 3 }));
    expect(vi.mocked(saveConfig).mock.invocationCallOrder[0]).toBeLessThan(replace.mock.invocationCallOrder[0]);
  });

  it("keeps the created room when saving the manager limits fails, and never creates a second", async () => {
    vi.mocked(saveConfig).mockRejectedValueOnce(new ApiError(500, null));
    const user = userEvent.setup();
    render(<RoomWizard />);
    await screen.findByText("Pack One");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(screen.getByLabelText("Room name"), "Friends");
    await user.click(screen.getByRole("button", { name: "Increase Minimum managers" }));
    await user.click(screen.getByRole("button", { name: "Create room" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/rooms/room-1/setup?step=2&min=3&max=10"));
    expect(await screen.findByText(/manager limits were not saved/)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() =>
      expect(saveConfig).toHaveBeenLastCalledWith("room-1", 1, expect.objectContaining({ minManagers: 3, maxManagers: 10 })),
    );
    expect(createRoom).toHaveBeenCalledTimes(1);
  });

  it("offers unsaved limits again on the setup page and retries them on Continue", async () => {
    const user = userEvent.setup();
    // Strict Mode loads the room twice, as `make dev` does; the later load must not drop the limits.
    vi.mocked(getRoom)
      .mockResolvedValueOnce(room())
      .mockImplementationOnce(() => new Promise((done) => setTimeout(() => done(room()), 30)));
    render(
      <StrictMode>
        <RoomWizard roomId="room-1" initialStep={2} unsavedLimits={{ min: 3, max: 8 }} />
      </StrictMode>,
    );

    expect(await screen.findByText(/manager limits were not saved/)).toBeTruthy();
    await waitFor(() => expect(getRoom).toHaveBeenCalledTimes(2));
    await new Promise((done) => setTimeout(done, 60));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() =>
      expect(saveConfig).toHaveBeenCalledWith("room-1", 1, expect.objectContaining({ minManagers: 3, maxManagers: 8 })),
    );
    expect(await screen.findByRole("heading", { name: "Squad rules" })).toBeTruthy();
    expect(screen.queryByText(/manager limits were not saved/)).toBeNull();
    expect(createRoom).not.toHaveBeenCalled();
  });

  it("issues an invite on reaching the last step and opens the room", async () => {
    const user = userEvent.setup();
    render(<RoomWizard roomId="room-1" initialStep={5} />);
    await screen.findByRole("heading", { name: "AI settings" });

    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect((await screen.findByTestId("invite-code")).textContent).toBe("ABCD-EFGH-JKMN");
    expect(document.querySelector("time")?.getAttribute("datetime")).toBe("2031-01-08T00:00:00Z");
    expect(screen.getByText("Playing too?")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Open room" }));
    expect(push).toHaveBeenCalledWith("/rooms/room-1");
  });
});

describe("RoomWizard create redirects (AC-6)", () => {
  it("writes ?step= on every URL change in create mode", async () => {
    const user = userEvent.setup();
    render(<RoomWizard />);
    await screen.findByText("Pack One");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(screen.getByLabelText("Room name"), "Friends");
    await user.click(screen.getByRole("button", { name: "Create room" }));
    await screen.findByRole("heading", { name: "Squad rules" });
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await screen.findByRole("heading", { name: "Scoring" });

    expect(replace).toHaveBeenCalled();
    for (const [url] of replace.mock.calls) expect(url).toMatch(/\/setup\?step=\d/);
  });
});

describe("RoomWizard settings mode (Home `settings:*`)", () => {
  const settings = (section = 2, over: Record<string, unknown> = {}) => {
    vi.mocked(getRoom).mockResolvedValue(room({ config: { ...config, name: "College Gang" }, packLabels: { ...room().packLabels, packName: "Pack One" }, ...over }));
    return render(<RoomWizard mode="settings" roomId="room-1" initialStep={section as 2} />);
  };

  it("shows the room-name rail, five sections, the locked sport and no create chrome", async () => {
    settings();

    expect(await screen.findByRole("heading", { name: "Basics" })).toBeTruthy();
    const rail = within(screen.getByRole("navigation", { name: "Room settings" }));
    expect(rail.getByText("College Gang")).toBeTruthy();
    expect(rail.getByText("Room settings")).toBeTruthy();
    expect(rail.getByText("SPORT")).toBeTruthy();
    expect(rail.getByText("Pack One")).toBeTruthy();
    expect(sections().getAllByRole("button").map((b) => b.textContent)).toEqual([
      expect.stringContaining("Basics"),
      expect.stringContaining("Squad rules"),
      expect.stringContaining("Scoring"),
      expect.stringContaining("AI"),
      expect.stringContaining("Invite"),
    ]);
    expect(screen.queryByText("NEW ROOM")).toBeNull();
    expect(screen.queryByText(/\d of 6/)).toBeNull();
    expect(screen.getByRole("button", { name: "Back to room" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: /^Save/ })).toBeNull();
    expect(screen.queryByRole("button", { name: "Prev" })).toBeNull();
    expect(screen.getByText("Next saves this section.")).toBeTruthy();
    expect(screen.getByText("Changes save per section.")).toBeTruthy();
  });

  it("Next saves the section with the config version, then opens the next section", async () => {
    const user = userEvent.setup();
    settings();
    const name = await screen.findByLabelText("Room name");
    await user.clear(name);
    await user.type(name, "Mates");

    await user.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => expect(saveConfig).toHaveBeenCalledWith("room-1", 1, expect.objectContaining({ name: "Mates" })));
    expect(replace).toHaveBeenCalledWith("/rooms/room-1/setup?section=squad", { scroll: false });
    expect(await screen.findByRole("heading", { name: "Squad rules" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Prev" })).toBeTruthy();
  });

  it("draws the admin tile for a Commissioner without a franchise (AC-12)", async () => {
    settings();
    await screen.findByRole("heading", { name: "Basics" });
    const rail = screen.getByRole("navigation", { name: "Room settings" });
    expect(rail.querySelector("[class*='adminTile']")).toBeTruthy();
  });

  it("does not save a section that was not edited", async () => {
    const user = userEvent.setup();
    settings();
    await screen.findByLabelText("Room name");

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByRole("heading", { name: "Squad rules" })).toBeTruthy();
    expect(saveConfig).not.toHaveBeenCalled();
  });

  it("Scoring has nothing to save, and the last section says Done and returns to the room", async () => {
    const user = userEvent.setup();
    settings(4);
    await screen.findByText("Points come from the Sport Pack, so there is nothing to save here.");
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByRole("heading", { name: "AI" })).toBeTruthy();
    expect(saveConfig).not.toHaveBeenCalled();
    cleanup();

    settings(6);
    await screen.findByText("The invite code and managers update as soon as you tap them.");
    await user.click(screen.getByRole("button", { name: "Done" }));
    expect(push).toHaveBeenCalledWith("/rooms/room-1");
    expect(saveConfig).not.toHaveBeenCalled();
  });

  it("keeps the server value and stays put on a stale version (AC-7)", async () => {
    vi.mocked(saveConfig).mockRejectedValueOnce(new ApiError(409, { error: "stale_version" }));
    const user = userEvent.setup();
    settings();
    vi.mocked(getRoom).mockResolvedValue(room({ configVersion: 2, config: { ...config, name: "Theirs" } }));
    const name = await screen.findByLabelText("Room name");
    await user.clear(name);
    await user.type(name, "Mine");

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Someone saved changes while you were editing.")).toBeTruthy();
    expect((screen.getByLabelText("Room name") as HTMLInputElement).value).toBe("Theirs");
    expect(screen.getByRole("heading", { name: "Basics" })).toBeTruthy();
    expect(replace).not.toHaveBeenCalled();
    expect(saveConfig).toHaveBeenCalledTimes(1);
  });

  it("is read-only once the auction is live: reason shown, fields disabled, Next only navigates", async () => {
    const user = userEvent.setup();
    settings(2, { state: "auction_live" });

    expect(await screen.findByText("Settings are locked.")).toBeTruthy();
    expect(screen.getByLabelText("Room name").matches(":disabled")).toBe(true);
    expect(screen.queryByText("Next saves this section.")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByRole("heading", { name: "Squad rules" })).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Prev" }));
    expect(await screen.findByRole("heading", { name: "Basics" })).toBeTruthy();
    expect(saveConfig).not.toHaveBeenCalled();
  });

  it("turns read-only when a save is refused because the auction went live", async () => {
    vi.mocked(saveConfig).mockRejectedValueOnce(new ApiError(409, { error: "room_locked" }));
    const user = userEvent.setup();
    settings();
    vi.mocked(getRoom).mockResolvedValue(room({ state: "auction_live", config: { ...config, name: "Theirs" } }));
    const name = await screen.findByLabelText("Room name");
    await user.type(name, "!");

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Settings are locked.")).toBeTruthy();
    expect(screen.getByLabelText("Room name").matches(":disabled")).toBe(true);
  });

  it("warns before Prev drops unsaved edits, and discards them when confirmed", async () => {
    const user = userEvent.setup();
    settings(3);
    const purse = await screen.findByRole("button", { name: "Increase Purse" });
    await user.click(purse);

    await user.click(screen.getByRole("button", { name: "Prev" }));

    expect(screen.getByRole("dialog")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Squad rules", level: 1 })).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Discard" }));
    expect(await screen.findByRole("heading", { name: "Basics", level: 1 })).toBeTruthy();
    expect(saveConfig).not.toHaveBeenCalled();
  });

  it("sends a manager to the room with the Note and draws no settings", async () => {
    settings(2, { myRole: "manager" });

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/rooms/room-1?note=settings"));
    expect(screen.queryByText("ROOM SETTINGS")).toBeNull();
    expect(screen.queryByLabelText("Room name")).toBeNull();
  });
});

describe("RoomWizard pack-driven labels (AC-11)", () => {
  const football = {
    ...config,
    currencyCode: "EUR",
    displayUnit: { label: "Million", divisor: 1_000_000, short: "M", minor: { label: "Thousand", short: "K", divisor: 1_000 } },
    purse: 100_000_000,
    incrementLadder: [{ from: 0, step: 500_000 }, { from: 5_000_000, step: 1_000_000 }],
    roleMinimums: { gk: 1, def: 4, mid: 4, fwd: 2 },
    categoryQuotas: { loan: 2, youth: 3 },
    ai: { enabled: true, lifelines: { "rival-scan": 1, "fit-check": 2, "price-ceiling": 0 }, reportCardVisibility: "room" },
    squadMin: 11,
    scoringRules: { rules: [{ type: "per-unit", id: "g", stat: "goals", points: 5 }] },
  };
  const labels = {
    packId: "fb",
    packVersion: "1",
    roles: [
      { id: "fwd", label: "Forward", short: "FWD" },
      { id: "mid", label: "Midfielder", short: "MID" },
      { id: "def", label: "Defender", short: "DEF" },
      { id: "gk", label: "Goalkeeper", short: "GK" },
    ],
    tags: [],
    restrictedCategories: [
      { id: "youth", label: "Youth" },
      { id: "loan", label: "Loan" },
    ],
    stats: [{ id: "goals", label: "Goals", group: "Attack" }],
    terminology: {},
  };
  const CRICKET = /Crore|Lakh|\bBAT\b|\bBOWL\b|\bWK\b|Overseas|Batting|Bowling|₹|\bCr\b/;

  it("shows the football pack's roles, quotas, units and groups with no cricket string", async () => {
    vi.mocked(getRoom).mockResolvedValue(room({ config: football, packLabels: labels }));
    const { container } = render(<RoomWizard roomId="room-1" initialStep={3} />);

    await screen.findByRole("heading", { name: "Squad rules" });
    const text = container.textContent ?? "";
    expect(text.indexOf("FWD")).toBeLessThan(text.indexOf("MID"));
    expect(text.indexOf("MID")).toBeLessThan(text.indexOf("DEF"));
    expect(text.indexOf("DEF")).toBeLessThan(text.indexOf("GK"));
    expect(text.indexOf("Youth")).toBeLessThan(text.indexOf("Loan"));
    expect(screen.getByText("€100 M")).toBeTruthy();
    expect(screen.getByText("+€500 K")).toBeTruthy();
    expect(text).not.toMatch(CRICKET);
  });

  it("orders lifelines by the core order and groups scoring by the pack's group", async () => {
    vi.mocked(getRoom).mockResolvedValue(room({ config: football, packLabels: labels }));
    const { container } = render(<RoomWizard roomId="room-1" initialStep={5} />);
    await screen.findByRole("heading", { name: "AI settings" });
    const text = container.textContent ?? "";
    expect(text.indexOf("Fit Check")).toBeLessThan(text.indexOf("Price Ceiling"));
    expect(text.indexOf("Price Ceiling")).toBeLessThan(text.indexOf("Rival Scan"));
    cleanup();

    render(<RoomWizard roomId="room-1" initialStep={4} />);
    expect(await screen.findByText("Attack")).toBeTruthy();
    expect(document.body.textContent).not.toMatch(CRICKET);
  });

  it("falls back to labels when a pack has no short keys", async () => {
    render(<RoomWizard roomId="room-1" initialStep={3} />);
    expect(await screen.findByText("Role one")).toBeTruthy();
    expect(screen.getByText("€100 M")).toBeTruthy();
  });
});
