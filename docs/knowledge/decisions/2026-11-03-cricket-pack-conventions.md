tags: [packs, cricket, scoring, money]
Cricket pack conventions (spec 2026-11-02-03): money is integer lakh (1 Cr = 100), shown via displayUnit Crore.
Rate bands are half-open [from, to); a strict ">x" edge is x + 0.01, so rates are entered at 2 dp. The most extreme band replaces the milder one.
Band rules read the supplied rate stat divided by in-playing-xi (always 1 when playing), scale 1; rates are not derived.
Stacked milestones (50 then 100 runs, 3 then 5 wickets) are separate threshold rules so they add up.
Optional preset retention (tagLimits, slabs, rtmFromSeason, rtmCounterRaise), poolSize and stat unit; packs without them load unchanged.
