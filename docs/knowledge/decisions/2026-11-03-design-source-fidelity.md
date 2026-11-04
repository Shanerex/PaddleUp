tags: [web, design, ui, rx, verify]
docs/design/source/*.dc.html is the only UI layout source. Each UI task cites its screen ("Design refs" in tasks.md); a missing screen is drawn there and approved before code. Spec sets rules and limits, the mock sets the look; mock copy is fixed when they disagree.
Why: sign-in (spec 01) and rooms (spec 04) both drifted. The rx architect reads only spawn paths + source_globs (docs/design is in neither), dev reads only what plan names, qa checks code and tests only.
Residual risk: rx prompts are unpatched, so the orchestrator must put docs/design/README.md and the cited .dc.html files in architect/dev spawn prompts for UI work.
Verify: screenshot every changed screen beside its mock frame at 390 and 1440 (serve docs/design/source with python3 -m http.server); data may differ, structure/spacing/colour/type may not.
web/src/styles/tokens.css mirrors source/paddle.js and a parity test fails on any token drift.
