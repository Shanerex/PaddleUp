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
vi.mock("../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/client")>()),
  listMyRooms: vi.fn(),
  getMe: vi.fn(),
}));

import { getMe, listMyRooms, type RoomSummary } from "../api/client";
import { greeting, HomeHub } from "./home-hub";

const franchise = (colour: string, monogram: string, name = "Lee XI") => ({ id: "f", ownerUserId: "u1", name, colour, monogram });

function room(over: Partial<RoomSummary> & { id: string }): RoomSummary {
  return {
    name: `Room ${over.id}`,
    competition: "Spring",
    state: "setup",
    packId: "p",
    packName: "Pack One",
    commissioner: false,
    franchiseCount: 3,
    minManagers: 4,
    maxManagers: 6,
    myFranchise: franchise("sky", "LX"),
    updatedAt: "2031-01-01T00:00:00Z",
    auctionStartsAt: null,
    serverNow: "2031-01-02T00:00:00Z",
    ...over,
  } as RoomSummary;
}

beforeEach(() => {
  vi.mocked(getMe).mockResolvedValue({ id: "u1", email: "a@b.c", displayName: "Ann Lee", avatar: "preset-01" });
});
afterEach(() => {
  cleanup();
  vi.mocked(listMyRooms).mockReset();
  vi.clearAllMocks();
  vi.useRealTimers();
});

const hero = async () => within(await screen.findByTestId("hero"));

describe("HomeHub empty wall (Home `empty`)", () => {
  it("shows the blank wall with both ways in and no hero", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([]);
    render(<HomeHub />);

    expect(await screen.findByRole("heading", { name: "No rooms yet" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "Create a room" }).getAttribute("href")).toBe("/rooms/new");
    expect(screen.getByRole("link", { name: "Join with code" }).getAttribute("href")).toBe("/join");
    expect(screen.queryByTestId("hero")).toBeNull();
    expect(screen.queryByText("PAST SEASONS")).toBeNull();
  });

  it("shows only the Past seasons strip plus the empty wall for a lone completed room", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([room({ id: "old", name: "Family Cup 2026", state: "completed" })]);
    render(<HomeHub />);

    expect(await screen.findByText("PAST SEASONS")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Family Cup 2026" }).getAttribute("href")).toBe("/rooms/old");
    expect(screen.getByText("COMPLETED")).toBeTruthy();
    expect(screen.getByTestId("empty-wall")).toBeTruthy();
    expect(screen.queryByTestId("hero")).toBeNull();
  });
});

describe("HomeHub rooms (matrix rows)", () => {
  it("shows a single room as the hero only, with no wall", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([room({ id: "a", commissioner: true })]);
    render(<HomeHub />);

    expect(await screen.findByRole("heading", { name: "Your rooms" })).toBeTruthy();
    expect(screen.queryByTestId("tile")).toBeNull();
    expect(screen.queryByText("PAST SEASONS")).toBeNull();
  });

  it("setup, Commissioner: chip, Invite managers, joined figure", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([room({ id: "a", commissioner: true })]);
    render(<HomeHub />);

    const h = await hero();
    expect(h.getByTestId("room-state").textContent).toBe("SETUP");
    expect(h.getByRole("link", { name: "Invite managers" }).getAttribute("href")).toBe("/rooms/a");
    expect(h.getByTestId("hero-figure").textContent).toBe("3/6");
    expect(h.getByText("1 more to reach the minimum of 4.")).toBeTruthy();
  });

  it("setup, no franchise: Take your seat on the inverse surface with the admin tile", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([room({ id: "a", myFranchise: undefined })]);
    render(<HomeHub />);

    const h = await hero();
    expect(h.getByRole("link", { name: "Take your seat" }).getAttribute("href")).toBe("/rooms/a");
    const band = screen.getByTestId("hero");
    expect(band.className).toMatch(/inverse/);
    expect(band.className).not.toMatch(/\bteam\b/);
    expect(band.querySelector("[data-admin='true']")).toBeTruthy();
  });

  it("setup, manager with a franchise: waiting for the Commissioner", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([room({ id: "a" })]);
    render(<HomeHub />);

    const h = await hero();
    expect(h.getByText("Waiting for the Commissioner")).toBeTruthy();
    expect(h.getByRole("link", { name: "Open room" }).getAttribute("href")).toBe("/rooms/a");
  });

  it("retention, season_live and auction_live rows", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([
      room({ id: "live", state: "auction_live" }),
      room({ id: "ret", state: "retention", franchiseCount: 6 }),
      room({ id: "sea", state: "season_live", franchiseCount: 8 }),
    ]);
    render(<HomeHub />);

    const h = await hero();
    expect(h.getByText("AUCTION LIVE")).toBeTruthy();
    expect(h.getByRole("link", { name: "Join the auction" }).getAttribute("href")).toBe("/rooms/live/auction");
    expect(h.getByTestId("hero-figure").textContent).toBe("LIVE");
    const tiles = screen.getAllByTestId("tile").map((t) => within(t));
    expect(tiles[0].getByText("RETENTION")).toBeTruthy();
    expect(tiles[0].getByRole("link", { name: "Room ret" }).getAttribute("href")).toBe("/rooms/ret");
    expect(tiles[1].getByText("SEASON LIVE")).toBeTruthy();
  });

  it("paints every tile in its franchise colour and ends the wall with Create a room", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([
      room({ id: "a", state: "auction_live", myFranchise: franchise("crimson", "CR") }),
      room({ id: "b", state: "retention", myFranchise: franchise("cobalt", "CO") }),
      room({ id: "c", state: "season_live", myFranchise: undefined, commissioner: true }),
    ]);
    render(<HomeHub />);
    await hero();

    const [b, c] = screen.getAllByTestId("tile");
    expect(screen.getByTestId("hero").style.getPropertyValue("--h")).toBe("15");
    expect(b.style.getPropertyValue("--h")).toBe("255");
    expect(b.className).toMatch(/\bteam\b/);
    expect(c.className).toMatch(/inverse/);
    expect(c.style.getPropertyValue("--h")).toBe("");
    const last = screen.getByRole("list", { name: "Your other rooms" }).lastElementChild as HTMLElement;
    expect(within(last).getByRole("link", { name: "Create a room" }).getAttribute("href")).toBe("/rooms/new");
  });

  it("keeps completed rooms out of the hero and the wall", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([
      room({ id: "done", name: "Old Cup", state: "completed" }),
      room({ id: "a", state: "retention" }),
    ]);
    render(<HomeHub />);

    await hero();
    expect(screen.queryByTestId("tile")).toBeNull();
    const strip = within(screen.getByRole("region", { name: "Past seasons" }));
    expect(strip.getByRole("link", { name: "Old Cup" })).toBeTruthy();
  });
});

describe("hero countdown (AC-4)", () => {
  const startsAt = "2031-01-02T00:05:00Z";
  const scheduled = (over: Partial<RoomSummary> = {}) => room({ id: "s", state: "auction_scheduled", auctionStartsAt: startsAt, ...over });

  it.each([-5, 0, 5])("stays on the server deadline with the browser clock %i minutes off", async (skewMin) => {
    vi.useFakeTimers({ toFake: ["Date", "performance", "setInterval", "clearInterval"] });
    vi.setSystemTime(new Date(Date.parse("2031-01-02T00:00:00Z") + skewMin * 60_000));
    vi.mocked(listMyRooms).mockResolvedValue([scheduled()]);
    render(<HomeHub />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    // serverNow 00:00:00, start 00:05:00: five minutes left, whatever the browser says.
    expect(screen.getByTestId("hero-figure").textContent).toBe("00:05:00");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(61_000);
    });
    expect(screen.getByTestId("hero-figure").textContent).toBe("00:03:59");
  });

  it("shows days and the local start with the Open lobby CTA", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([scheduled({ auctionStartsAt: "2031-01-04T04:12:00Z" })]);
    render(<HomeHub />);

    const h = await hero();
    expect(h.getByTestId("hero-figure").textContent).toBe("2d 04:12");
    expect(h.getByText("AUCTION NEXT")).toBeTruthy();
    expect(h.getByText(/your time\. Rules are set\./)).toBeTruthy();
    expect(h.getByRole("link", { name: "Open lobby" }).getAttribute("href")).toBe("/rooms/s/auction");
  });

  it("says Time not set with no start, and ranks it after a dated one", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([scheduled({ id: "undated", auctionStartsAt: null }), scheduled({ id: "dated" })]);
    render(<HomeHub />);

    const h = await hero();
    expect(h.getByRole("link", { name: "Open lobby" }).getAttribute("href")).toBe("/rooms/dated/auction");
    expect(within(screen.getByTestId("tile")).getByText("Time not set")).toBeTruthy();
  });
});

describe("load error and refetch (AC-5)", () => {
  it("shows a Note with Retry and no stale rooms, then recovers", async () => {
    vi.mocked(listMyRooms).mockRejectedValueOnce(new Error("down")).mockResolvedValue([room({ id: "a" })]);
    const user = userEvent.setup();
    render(<HomeHub />);

    expect(await screen.findByText("Couldn't load your rooms.")).toBeTruthy();
    expect(screen.getByText("Check your connection and try again.")).toBeTruthy();
    expect(screen.queryByTestId("hero")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByTestId("hero")).toBeTruthy();
    expect(screen.queryByText("Couldn't load your rooms.")).toBeNull();
  });

  it("clears the rooms when a refetch on focus fails", async () => {
    vi.mocked(listMyRooms).mockResolvedValueOnce([room({ id: "a" })]).mockRejectedValueOnce(new Error("down"));
    render(<HomeHub />);
    await screen.findByTestId("hero");

    fireEvent.focus(window);

    expect(await screen.findByText("Couldn't load your rooms.")).toBeTruthy();
    expect(screen.queryByTestId("hero")).toBeNull();
  });

  it("refetches on focus and on becoming visible, so a stale tab shows the server's hero", async () => {
    vi.mocked(listMyRooms)
      .mockResolvedValueOnce([room({ id: "a", state: "retention" })])
      .mockResolvedValueOnce([room({ id: "a", state: "auction_live" })])
      .mockResolvedValue([room({ id: "a", state: "season_live" })]);
    render(<HomeHub />);
    expect((await screen.findByTestId("hero")).textContent).toMatch(/RETENTION/);

    fireEvent.focus(window);
    await waitFor(() => expect(screen.getByTestId("hero").textContent).toMatch(/AUCTION LIVE/));
    expect(screen.getByRole("link", { name: "Join the auction" }).getAttribute("href")).toBe("/rooms/a/auction");

    fireEvent(document, new Event("visibilitychange"));
    await waitFor(() => expect(screen.getByTestId("hero").textContent).toMatch(/SEASON LIVE/));
    expect(listMyRooms).toHaveBeenCalledTimes(3);
  });

  it("shows the same hero in a second tab as in the first", async () => {
    const list = [room({ id: "x", state: "retention" }), room({ id: "y", state: "season_live" })];
    vi.mocked(listMyRooms).mockResolvedValue(list);
    const first = render(<HomeHub />);
    const a = (await screen.findByTestId("hero")).textContent;
    first.unmount();
    render(<HomeHub />);
    expect((await screen.findByTestId("hero")).textContent).toBe(a);
  });
});

describe("hub copy", () => {
  it("greets by the viewer's local time", () => {
    expect(greeting(new Date(2031, 0, 1, 8))).toBe("Morning");
    expect(greeting(new Date(2031, 0, 1, 14))).toBe("Afternoon");
    expect(greeting(new Date(2031, 0, 1, 20))).toBe("Evening");
  });

  it("greets the viewer by first name", async () => {
    vi.mocked(listMyRooms).mockResolvedValue([room({ id: "a" })]);
    render(<HomeHub />);
    expect(await screen.findByText(/, Ann\./)).toBeTruthy();
  });
});
