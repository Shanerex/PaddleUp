"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { getMe } from "../api/client";
import { HOME } from "../auth/redirect";
import s from "./AppShell.module.css";
import { Icon } from "./Icon";
import { PaddleMark } from "./PaddleMark";

/** Up to two initials from a display name, for the top-bar avatar. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "";
  const first = parts[0][0] ?? "";
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? "") : (parts[0][1] ?? "");
  return (first + last).toUpperCase();
}

/** Home.dc.html top bar: wordmark home link and the avatar that opens the profile. No bell in v1. */
export function AppTopBar() {
  const [name, setName] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    getMe()
      .then((me) => active && setName(me?.displayName ?? ""))
      .catch(() => active && setName(""));
    return () => {
      active = false;
    };
  }, []);
  return (
    <header className={s.bar}>
      <Link href={HOME} className={s.home} aria-label="PaddleUp home">
        <PaddleMark size={28} className={s.mark} />
        <span className={s.word}>
          Paddle<span className={s.up}>Up</span>
        </span>
      </Link>
      <span className={s.spacer} />
      <Link href="/profile" className={s.avatar} aria-label="Your profile">
        {name ? initials(name) : ""}
      </Link>
    </header>
  );
}

/** Home `join` back link: "← Rooms". */
export function BackLink({ href = HOME, children = "Rooms" }: { href?: string; children?: ReactNode }) {
  return (
    <Link href={href} className={s.back}>
      <Icon name="arrow_back" />
      {children}
    </Link>
  );
}

/** The page frame outside a room: top bar plus a main column. */
export function AppShell({
  children,
  centered = false,
  bleed = false,
}: {
  children: ReactNode;
  centered?: boolean;
  /** Full-bleed content (the wizard draws its own rail and footer). */
  bleed?: boolean;
}) {
  const cls = [s.main, centered && s.centered, bleed && s.bleed].filter(Boolean).join(" ");
  return (
    <div className={s.app}>
      <AppTopBar />
      <main data-shell className={cls}>
        {children}
      </main>
    </div>
  );
}

export { s as shell };
