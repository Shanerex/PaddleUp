import { describe, expect, it } from "vitest";
import type { DatasetSummary } from "../../../api/client";
import { countRows, fileMeta, fileSize, versionSub } from "./describe";

describe("import file descriptions", () => {
  it("sizes a file in bytes, KB or MB", () => {
    expect(fileSize(300)).toBe("300 B");
    expect(fileSize(41 * 1024)).toBe("41 KB");
    expect(fileSize(1.5 * 1024 * 1024)).toBe("1.5 MB");
  });

  it("counts CSV rows without the header and JSON players", () => {
    expect(countRows("a.csv", "name,x\nA,1\n\nB,2\n")).toBe(2);
    expect(countRows("a.json", '{"players":[{},{},{}]}')).toBe(3);
    expect(countRows("a.json", "{not json")).toBeNull();
    expect(countRows("a.json", '{"people":[]}')).toBeNull();
  });

  it("describes a file by rows and size, or size alone", () => {
    expect(fileMeta("a.csv", 2048, "name\nA\n")).toBe("1 row · 2 KB");
    expect(fileMeta("a.csv", 2048, null)).toBe("2 KB");
  });

  it("describes a version by day and skipped rows", () => {
    const v = (importedAt: string, rejectedCount: number) => ({ importedAt, rejectedCount }) as DatasetSummary;
    const now = new Date(2026, 10, 4, 22, 0);
    expect(versionSub(v(new Date(2026, 10, 4, 21, 40).toISOString(), 3), now)).toBe("Today 21:40 · 3 skipped");
    expect(versionSub(v(new Date(2026, 10, 2, 9, 0).toISOString(), 0), now)).toBe("Mon 2 Nov · 0 skipped");
  });
});
