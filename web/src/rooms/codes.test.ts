import { describe, expect, it } from "vitest";
import { formatCode, normaliseCode } from "./codes";

describe("invite codes", () => {
  it("groups a 12-character code in fours for reading", () => {
    expect(formatCode("7K2M9QXD4HPA")).toBe("7K2M-9QXD-4HPA");
    expect(formatCode("ABC")).toBe("ABC");
  });

  it("normalises typed codes like the server: case, spaces and dashes do not matter", () => {
    expect(normaliseCode(" 7k2m-9qxd 4hpa ")).toBe("7K2M9QXD4HPA");
  });

  it("drops characters that can never be in a code so a path cannot be smuggled in", () => {
    expect(normaliseCode("../../api%2F")).toBe("API2F");
  });
});
