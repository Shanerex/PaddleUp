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

vi.mock("../../../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../api/client")>()),
  listPacks: vi.fn(),
  createRoom: vi.fn(),
  getRoom: vi.fn(),
  saveConfig: vi.fn(),
  issueInvite: vi.fn(),
}));

import {
  ApiError,
  createRoom,
  getRoom,
  issueInvite,
  listPacks,
  saveConfig,
  type Room,
} from "../../../api/client";
import { RoomWizard } from "./room-wizard";

function room(version: number, overrides: Record<string, unknown> = {}): Room {
  return {
    id: "room-1",
    state: "setup",
    myRole: "commissioner",
    commissioner: { id: "u1", displayName: "Ann" },
    configVersion: version,
    franchiseCount: 0,
    advanceTo: ["retention", "auction_scheduled"],
    createdAt: "2031-01-01T00:00:00Z",
    packLabels: {
      packId: "pack-a",
      packVersion: "1.0.0",
      roles: [
        { id: "R1", label: "Role one" },
        { id: "R2", label: "Role two" },
      ],
      tags: [],
      restrictedCategories: [{ id: "cap-1", label: "Capped group", tag: "t", max: 4 }],
      stats: [],
      terminology: {},
    },
    config: {
      name: "Friends",
      competition: "",
      minManagers: 2,
      maxManagers: 10,
      purse: 12000,
      currencyCode: "XXX",
      displayUnit: { label: "Big units", divisor: 100 },
      incrementLadder: [{ from: 0, step: 5 }],
      squadMin: 18,
      squadMax: 25,
      roleMinimums: { R1: 2, R2: 3 },
      categoryQuotas: { "cap-1": 4 },
      retention: { maxRetentions: 2, rtmCards: 1 },
      scoringRules: { rules: [{ type: "PerUnit", id: "unit-points", stat: "s", points: 1 }] },
      ai: {
        enabled: true,
        lifelines: { "fit-check": 1, "price-ceiling": 1, "rival-scan": 1 },
        reportCardVisibility: "room",
      },
    },
    ...overrides,
  } as unknown as Room;
}

beforeEach(() => {
  vi.mocked(listPacks).mockResolvedValue([
    {
      id: "pack-a",
      version: "1.0.0",
      presets: [
        { id: "standard", label: "Standard rules" },
        { id: "short", label: "Short rules" },
      ],
    },
  ]);
  vi.mocked(createRoom).mockResolvedValue(room(1));
  vi.mocked(saveConfig).mockImplementation(async (_id, version, config) =>
    room(version + 1, { config }),
  );
  vi.mocked(issueInvite).mockResolvedValue({
    code: "ABCDEFGHJKMN",
    link: "http://localhost:3000/join/ABCDEFGHJKMN",
    expiresAt: "2031-01-08T00:00:00Z",
  });
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

async function toSquadStep(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByText("Pick a sport");
  await user.click(screen.getByLabelText("Short rules"));
  await user.click(screen.getByRole("button", { name: "Next" }));
  await user.type(screen.getByLabelText("Room name"), "Friends");
  await user.click(screen.getByRole("button", { name: "Create room" }));
  await screen.findByText("Squad rules");
}

describe("RoomWizard", () => {
  it("creates the room at Basics, saves each later step with its version, and shows the invite", async () => {
    const user = userEvent.setup();
    render(<RoomWizard />);

    await toSquadStep(user);
    expect(createRoom).toHaveBeenCalledWith({
      packId: "pack-a",
      presetId: "short",
      name: "Friends",
      competition: "",
    });

    const purse = screen.getByLabelText(/Purse/);
    expect((purse as HTMLInputElement).value).toBe("120");
    await user.clear(purse);
    await user.type(purse, "150");
    await user.click(screen.getByRole("button", { name: "Next" }));

    await screen.findByText("Scoring");
    expect(saveConfig).toHaveBeenCalledTimes(1);
    const [id, version, config] = vi.mocked(saveConfig).mock.calls[0];
    expect(id).toBe("room-1");
    expect(version).toBe(1);
    expect(config.purse).toBe(15000);
    expect(screen.getByText("Unit points")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.clear(screen.getByLabelText("Fit check lifelines"));
    await user.type(screen.getByLabelText("Fit check lifelines"), "0");
    await user.click(screen.getByRole("button", { name: "Save and invite" }));

    expect(await screen.findByTestId("invite-code")).toHaveProperty("textContent", "ABCDEFGHJKMN");
    expect(vi.mocked(saveConfig).mock.calls[1][1]).toBe(2);
    expect(vi.mocked(saveConfig).mock.calls[1][2].ai.lifelines["fit-check"]).toBe(0);
    expect(screen.getByText("http://localhost:3000/join/ABCDEFGHJKMN")).toBeTruthy();
    expect(document.querySelector("time")?.getAttribute("datetime")).toBe("2031-01-08T00:00:00Z");
    expect(screen.getByRole("link", { name: "Open room" }).getAttribute("href")).toBe("/rooms/room-1");
  });

  it("labels roles and categories from the room's pack, not from the UI", async () => {
    const user = userEvent.setup();
    render(<RoomWizard />);

    await toSquadStep(user);

    expect(screen.getByLabelText("Role one")).toBeTruthy();
    expect(screen.getByLabelText("Role two")).toBeTruthy();
    expect(screen.getByLabelText("Capped group")).toBeTruthy();
    expect(screen.getByLabelText(/Purse \(Big units\)/)).toBeTruthy();
  });

  it("shows every violation and keeps the step when a save is refused", async () => {
    vi.mocked(saveConfig).mockRejectedValue(
      new ApiError(400, {
        error: "invalid_config",
        violations: [{ field: "roleMinimums", message: "Minimums add up to 20, above squadMin" }],
      }),
    );
    const user = userEvent.setup();
    render(<RoomWizard />);
    await toSquadStep(user);

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText(/Minimums add up to 20/)).toBeTruthy();
    expect(screen.getByText("Squad rules")).toBeTruthy();
    expect(getRoom).not.toHaveBeenCalled();
  });

  it("reloads the room and says why when the version is stale", async () => {
    vi.mocked(saveConfig).mockRejectedValue(new ApiError(409, { error: "stale_version" }));
    vi.mocked(getRoom).mockResolvedValue(room(5));
    const user = userEvent.setup();
    render(<RoomWizard />);
    await toSquadStep(user);

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText(/Someone else changed these settings/)).toBeTruthy();
    expect(getRoom).toHaveBeenCalledWith("room-1");
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(saveConfig).toHaveBeenCalledTimes(2));
    expect(vi.mocked(saveConfig).mock.calls[1][1]).toBe(5);
  });

  it("tells a stale tab that the auction is live instead of pretending the save worked", async () => {
    vi.mocked(saveConfig).mockRejectedValue(new ApiError(409, { error: "room_locked" }));
    vi.mocked(getRoom).mockResolvedValue(room(4, { state: "auction_live", advanceTo: [] }));
    const user = userEvent.setup();
    render(<RoomWizard />);
    await toSquadStep(user);

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText(/The auction is live, so this can no longer be changed/)).toBeTruthy();
    expect(getRoom).toHaveBeenCalledWith("room-1");
    expect(screen.getByText("Squad rules")).toBeTruthy();
    expect(screen.queryByTestId("invite-code")).toBeNull();
  });

  it("explains a refused room creation", async () => {
    vi.mocked(createRoom).mockRejectedValue(
      new ApiError(400, { error: "invalid_request", message: "Unknown preset" }),
    );
    const user = userEvent.setup();
    render(<RoomWizard />);
    await screen.findByText("Pick a sport");
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.type(screen.getByLabelText("Room name"), "Friends");

    await user.click(screen.getByRole("button", { name: "Create room" }));

    expect(await screen.findByText("Unknown preset")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Create room" })).toBeTruthy();
  });

  it("can issue a replacement invite code on the last step", async () => {
    vi.mocked(issueInvite)
      .mockResolvedValueOnce({ code: "FIRSTCODE123", link: "l1", expiresAt: "2031-01-08T00:00:00Z" })
      .mockResolvedValueOnce({ code: "SECONDCODE12", link: "l2", expiresAt: "2031-01-09T00:00:00Z" });
    const user = userEvent.setup();
    render(<RoomWizard />);
    await toSquadStep(user);
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Save and invite" }));
    expect((await screen.findByTestId("invite-code")).textContent).toBe("FIRSTCODE123");

    await user.click(screen.getByRole("button", { name: "New code" }));

    await waitFor(() => expect(screen.getByTestId("invite-code").textContent).toBe("SECONDCODE12"));
  });

  it("lists the six steps and marks the current one", async () => {
    render(<RoomWizard />);
    await screen.findByText("Pick a sport");

    const steps = within(screen.getByRole("list", { name: "Steps" })).getAllByRole("listitem");
    expect(steps.map((s) => s.textContent)).toEqual([
      "1. Sport",
      "2. Basics",
      "3. Squad rules",
      "4. Scoring",
      "5. AI",
      "6. Invite",
    ]);
    expect(steps[0].getAttribute("aria-current")).toBe("step");
  });
});
