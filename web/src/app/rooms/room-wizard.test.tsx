// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

  it("keeps the person's edits when someone else saved first", async () => {
    vi.mocked(saveConfig).mockRejectedValueOnce(new ApiError(409, { error: "stale_version" }));
    vi.mocked(getRoom).mockResolvedValueOnce(room()).mockResolvedValueOnce(room({ configVersion: 2 }));
    const user = userEvent.setup();
    render(<RoomWizard roomId="room-1" initialStep={2} />);
    const name = await screen.findByLabelText("Room name");
    await user.clear(name);
    await user.type(name, "Mine");

    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText(/Someone else saved changes/)).toBeTruthy();
    expect((screen.getByLabelText("Room name") as HTMLInputElement).value).toBe("Mine");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await waitFor(() => expect(saveConfig).toHaveBeenLastCalledWith("room-1", 2, expect.objectContaining({ name: "Mine" })));
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
