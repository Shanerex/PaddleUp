// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/client")>()),
  listMyRooms: vi.fn(),
  getMe: vi.fn(),
}));

import { getMe, listMyRooms, type RoomSummary } from "../api/client";
import { cardFoot, greeting, HomeHub } from "./home-hub";

const base = {
  id: "r1",
  name: "Friends",
  competition: "Spring",
  state: "setup",
  packId: "p",
  packName: "Pack One",
  commissioner: true,
  franchiseCount: 1,
  minManagers: 3,
  maxManagers: 8,
  myFranchise: null,
  updatedAt: "2031-01-01T00:00:00Z",
} as unknown as RoomSummary;

beforeEach(() => {
  vi.mocked(getMe).mockResolvedValue({ id: "u1", email: "a@b.c", displayName: "Ann Lee", avatar: "preset-01" });
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("HomeHub (Home `hubNow`, `empty`)", () => {
  it("shows the empty state with both ways in", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([]);
    render(<HomeHub />);

    expect(await screen.findByRole("heading", { name: "No rooms yet" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "Create a room" }).getAttribute("href")).toBe("/rooms/new");
    expect(screen.getByRole("link", { name: "Join with code" }).getAttribute("href")).toBe("/join");
  });

  it("lists each room with its state, managers and a link in", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([
      base,
      {
        ...base,
        id: "r2",
        name: "Office",
        state: "auction_scheduled",
        commissioner: false,
        franchiseCount: 6,
        myFranchise: { id: "f", ownerUserId: "u1", name: "Lee XI", colour: "sky", monogram: "LX" },
      },
    ]);
    render(<HomeHub />);

    expect(await screen.findByRole("heading", { name: "Your rooms" })).toBeTruthy();
    expect(screen.getByRole("link", { name: /Friends/ }).getAttribute("href")).toBe("/rooms/r1");
    expect(screen.getByText("You're the commissioner · no franchise")).toBeTruthy();
    expect(screen.getByText("1 / 3 min")).toBeTruthy();
    expect(screen.getByText("Lee XI · Spring")).toBeTruthy();
    expect(screen.getByText("6 / 8 max")).toBeTruthy();
    expect(screen.getByText("AUCTION NEXT")).toBeTruthy();
    expect(await screen.findByText(/, Ann\./)).toBeTruthy();
  });
});

describe("hub copy", () => {
  it("greets by the viewer's local time", () => {
    expect(greeting(new Date(2031, 0, 1, 8))).toBe("Morning");
    expect(greeting(new Date(2031, 0, 1, 14))).toBe("Afternoon");
    expect(greeting(new Date(2031, 0, 1, 20))).toBe("Evening");
  });

  it("says how many managers are still missing, facts first", () => {
    expect(cardFoot(base)).toBe("Waiting on 2 more. Nudge the group chat.");
    expect(cardFoot({ ...base, franchiseCount: 3 })).toBe("Enough managers. Move the room on when you're ready.");
    expect(cardFoot({ ...base, commissioner: false })).toBe("The Commissioner moves the room on once everyone's in.");
  });
});
