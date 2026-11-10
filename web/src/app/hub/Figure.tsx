"use client";

import { useEffect, useState } from "react";
import { formatUntil, type HeroFigure } from "./hero";
import type { ServerClock } from "../../auction/clock";

/** Ticks once a second while mounted; the countdown reads the server clock, never the browser's. */
function useTick(active: boolean) {
  const [, setTick] = useState(0);
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => setTick((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, [active]);
}

/** The one DM Mono figure of a hero or tile: plain text, or a countdown to the auction start. */
export function FigureValue({ figure, clock }: { figure: HeroFigure; clock: ServerClock }) {
  useTick(figure.kind === "countdown");
  if (figure.kind === "text") return <>{figure.value}</>;
  const left = clock.timeLeft(Date.parse(figure.startsAt));
  return <>{left === null ? "--:--" : formatUntil(left)}</>;
}

/** Hero figures longer than six characters drop to a smaller size (Home `wall`). */
export function figureLength(figure: HeroFigure, clock: ServerClock): number {
  if (figure.kind === "text") return figure.value.length;
  const left = clock.timeLeft(Date.parse(figure.startsAt));
  return left === null ? 5 : formatUntil(left).length;
}
