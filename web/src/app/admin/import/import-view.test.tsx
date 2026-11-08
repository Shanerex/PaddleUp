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
  getMe: vi.fn().mockResolvedValue(null),
  listDatasetPacks: vi.fn(),
  listDatasets: vi.fn(),
  importDataset: vi.fn(),
}));

import { ApiError, importDataset, listDatasetPacks, listDatasets, type DatasetPack, type DatasetSummary, type ImportReport } from "../../../api/client";
import { ImportView } from "./import-view";

const packs = [
  {
    id: "pack-a",
    version: "1.0.0",
    name: "Pack A",
    icon: "sports_cricket",
    roles: [
      { id: "R1", label: "One" },
      { id: "R2", label: "Two" },
    ],
    tags: [{ id: "t1", label: "Tag" }],
  },
  { id: "pack-b", version: "2.0.0", name: "Pack B", icon: null, roles: [{ id: "X", label: "X" }], tags: [] },
] as unknown as DatasetPack[];

const versions = [
  { id: "d3", version: 3, packVersion: "1.0.0", sourceFormat: "csv", importedAt: "2026-11-02T09:00:00Z", playerCount: 214, rejectedCount: 0 },
  { id: "d2", version: 2, packVersion: "1.0.0", sourceFormat: "csv", importedAt: "2026-10-29T09:00:00Z", playerCount: 208, rejectedCount: 6 },
] as DatasetSummary[];

const csv = () => new File(["name,country\nA,B\nC,D\n"], "ipl-2026-pool.csv", { type: "text/csv" });

function report(over: Partial<ImportReport> = {}): ImportReport {
  return {
    version: 4,
    packId: "pack-a",
    packVersion: "1.0.0",
    totalRows: 218,
    imported: 215,
    skipped: 3,
    rows: [
      { row: 17, field: "role", value: "BATTER", message: "Not a Pack A role. Use R1 or R2." },
      { row: 88, field: "tags", value: "overseas;local", message: '"local" is not a Pack A tag.' },
      { row: 141, field: "base_price", value: "-20", message: "Base price must be above zero." },
    ],
    moreProblems: 0,
    ...over,
  };
}

beforeEach(() => {
  vi.mocked(listDatasetPacks).mockResolvedValue(packs);
  vi.mocked(listDatasets).mockResolvedValue(versions);
  vi.mocked(importDataset).mockReset();
});
afterEach(cleanup);

async function pickFile(file = csv()) {
  const user = userEvent.setup();
  await user.upload(screen.getByTestId("season-file"), file);
  return user;
}

describe("ImportView (AdminImport importEmpty, import)", () => {
  it("starts with no file: the drop target, the pack's columns and keys, and an Import button that does nothing", async () => {
    render(<ImportView />);

    expect(await screen.findByText("Drop a CSV or JSON file")).toBeTruthy();
    expect(screen.getByText("APP ADMIN")).toBeTruthy();
    expect(screen.getByText("name, country, role, tags, base_price, set, provider_id")).toBeTruthy();
    expect(screen.getByText("R1 · R2")).toBeTruthy();
    expect(screen.getByText("t1")).toBeTruthy();
    expect((screen.getByRole("button", { name: /Import/ }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("NEW IMPORT")).toBeTruthy();
  });

  it("lists the pack's versions newest first with LATEST on the top one", async () => {
    render(<ImportView />);

    await screen.findByText("VERSIONS · PACK A");
    const rows = screen.getAllByTestId("version-row");
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText("v3")).toBeTruthy();
    expect(within(rows[0]).getByText("LATEST")).toBeTruthy();
    expect(within(rows[0]).getByText("214")).toBeTruthy();
    expect(within(rows[1]).queryByText("LATEST")).toBeNull();
    expect(within(rows[1]).getByText(/6 skipped/)).toBeTruthy();
    expect(listDatasets).toHaveBeenCalledWith("pack-a");
  });

  it("shows the picked file with its rows and size, and clears it", async () => {
    render(<ImportView />);
    await screen.findByText("Drop a CSV or JSON file");

    const user = await pickFile();
    expect(await screen.findByText("ipl-2026-pool.csv")).toBeTruthy();
    expect(screen.getByText(/2 rows · /)).toBeTruthy();
    expect((screen.getByRole("button", { name: /Import/ }) as HTMLButtonElement).disabled).toBe(false);

    await user.click(screen.getByRole("button", { name: "Remove file" }));
    expect(screen.getByText("Drop a CSV or JSON file")).toBeTruthy();
  });

  it("reloads the versions and keys when another pack is chosen", async () => {
    const user = userEvent.setup();
    render(<ImportView />);
    await screen.findByText("VERSIONS · PACK A");

    await user.selectOptions(screen.getByRole("combobox"), "pack-b");

    expect(await screen.findByText("VERSIONS · PACK B")).toBeTruthy();
    expect(listDatasets).toHaveBeenLastCalledWith("pack-b");
    expect(screen.getByText("Pack B roles:")).toBeTruthy();
  });
});

describe("ImportView results (AdminImport importReport, importFailed)", () => {
  it("shows a mixed import: the new version, the counts and each rejected row with row, field, value and problem", async () => {
    render(<ImportView />);
    await screen.findByText("Drop a CSV or JSON file");
    vi.mocked(importDataset).mockResolvedValue(report());
    vi.mocked(listDatasets).mockResolvedValue([{ ...versions[0], id: "d4", version: 4, playerCount: 215, rejectedCount: 3 }, ...versions]);

    const user = await pickFile();
    await user.click(await screen.findByRole("button", { name: /Import/ }));

    expect(await screen.findByText("v4 is live with 215 players.")).toBeTruthy();
    expect(screen.getByText(/3 rows were skipped\. New rooms get v4\./)).toBeTruthy();
    expect(importDataset).toHaveBeenCalledWith("pack-a", expect.any(File));
    const rows = screen.getAllByTestId("rejected-row");
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByText("17")).toBeTruthy();
    expect(within(rows[0]).getByText("role")).toBeTruthy();
    expect(within(rows[0]).getByText("BATTER")).toBeTruthy();
    expect(within(rows[0]).getByText("Not a Pack A role. Use R1 or R2.")).toBeTruthy();
    expect(within(rows[2]).getByText("base_price")).toBeTruthy();
    expect(screen.getByText("IMPORT ANOTHER FILE")).toBeTruthy();
    await waitFor(() => expect(within(screen.getAllByTestId("version-row")[0]).getByText("v4")).toBeTruthy());
  });

  it("says nothing was imported when every row is rejected, and counts the rest as 'and N more'", async () => {
    render(<ImportView />);
    await screen.findByText("Drop a CSV or JSON file");
    vi.mocked(importDataset).mockImplementation(() =>
      Promise.reject(
        new ApiError(422, {
          error: "no_valid_rows",
          report: report({ version: null, totalRows: 24, imported: 0, skipped: 24, moreProblems: 20 }),
        }),
      ),
    );

    const user = await pickFile();
    await user.click(screen.getByRole("button", { name: /Import/ }));

    expect(await screen.findByText("Nothing imported.")).toBeTruthy();
    expect(screen.getByText(/All 24 rows were skipped, so no new version was made\. v3 is still the latest\./)).toBeTruthy();
    expect(screen.getByText("and 20 more like these")).toBeTruthy();
    expect(screen.getAllByTestId("rejected-row")).toHaveLength(3);
    expect(listDatasets).toHaveBeenCalledTimes(1);
  });

  it("explains a file that cannot be read at all", async () => {
    render(<ImportView />);
    await screen.findByText("Drop a CSV or JSON file");
    vi.mocked(importDataset).mockImplementation(() =>
      Promise.reject(new ApiError(400, { error: "invalid_file", message: "The CSV could not be read" })),
    );

    const user = await pickFile();
    await user.click(screen.getByRole("button", { name: /Import/ }));

    expect(await screen.findByText("That file couldn't be imported.")).toBeTruthy();
    expect(screen.getByText(/The CSV could not be read/)).toBeTruthy();
  });
});

describe("ImportView importDenied", () => {
  it("shows the denied screen and never the form on a 403", async () => {
    vi.mocked(listDatasetPacks).mockImplementation(() => Promise.reject(new ApiError(403, { error: "forbidden" })));
    render(<ImportView />);

    expect(await screen.findByText("This page is for app admins")).toBeTruthy();
    expect(screen.getByText(/Your account isn't on the admin list/)).toBeTruthy();
    expect(screen.queryByText("Drop a CSV or JSON file")).toBeNull();
    expect(screen.queryByText("APP ADMIN")).toBeNull();
    expect(screen.getByRole("link", { name: "Back to your rooms" })).toBeTruthy();
  });
});
