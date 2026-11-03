// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

import { JoinCode } from "./join-code";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("JoinCode (Home `joinCode`)", () => {
  it("goes to the join link for a typed code, ignoring case, spaces and dashes", async () => {
    const user = userEvent.setup();
    render(<JoinCode />);

    await user.type(screen.getByLabelText("Invite code"), "7k2m-9qxd 4hpa");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(push).toHaveBeenCalledWith("/join/7K2M9QXD4HPA");
  });

  it("says when a code is too short instead of asking the server", async () => {
    const user = userEvent.setup();
    render(<JoinCode />);

    await user.type(screen.getByLabelText("Invite code"), "ABC");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(screen.getByText(/too short/)).toBeTruthy();
    expect(push).not.toHaveBeenCalled();
  });
});
