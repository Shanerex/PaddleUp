// @vitest-environment jsdom
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Monogram } from "./kit";

afterEach(cleanup);

describe("Monogram (Design System 08)", () => {
  it("paints the admin tone with its tile, not an unstyled square", () => {
    const { container } = render(<Monogram tone="admin" size={32} />);
    const tile = container.firstElementChild as HTMLElement;
    expect(tile.className).toMatch(/adminTile/);
    expect(tile.textContent).toBe("shield_person");
  });

  it("keeps the admin team tone and a rival's own hue", () => {
    const { container } = render(
      <>
        <Monogram tone="adminTeam" />
        <Monogram text="KI" colour="sky" />
      </>,
    );
    expect((container.children[0] as HTMLElement).className).toMatch(/adminTeam/);
    expect((container.children[1] as HTMLElement).className).toMatch(/rival/);
  });
});
