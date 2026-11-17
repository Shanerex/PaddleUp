// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Sheet } from "./Sheet";
import s from "./Sheet.module.css";

afterEach(cleanup);

describe("Sheet", () => {
  it("keeps the drag handle and no close button without a title", () => {
    render(
      <Sheet label="Plain" onClose={() => {}}>
        <button type="button">Inside</button>
      </Sheet>,
    );
    expect(screen.getByRole("dialog").querySelector(`.${s.handle}`)).not.toBeNull();
    expect(screen.queryByRole("button", { name: "Close" })).toBeNull();
  });

  it("with a title draws the title row and a named close button instead of the handle", async () => {
    const onClose = vi.fn();
    render(
      <Sheet label="Titled" title="Titled" onClose={onClose}>
        <button type="button">Inside</button>
      </Sheet>,
    );
    const dialog = screen.getByRole("dialog", { name: "Titled" });
    expect(dialog.querySelector(`.${s.handle}`)).toBeNull();
    expect(dialog.querySelector("h2")?.textContent).toBe("Titled");
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("still closes on Escape and keeps Tab inside", async () => {
    const onClose = vi.fn();
    render(
      <Sheet label="Titled" title="Titled" onClose={onClose}>
        <button type="button">Inside</button>
      </Sheet>,
    );
    await userEvent.tab();
    await userEvent.tab();
    await userEvent.tab();
    expect(screen.getByRole("dialog").contains(document.activeElement)).toBe(true);
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });
});
