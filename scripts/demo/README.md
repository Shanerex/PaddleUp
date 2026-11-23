# Demo world

Local playground for trying PaddleUp end to end. It uses its own database (`paddleup_demo` in the
compose Postgres), so the normal `paddleup` dev database is never touched.

    scripts/demo/up.sh                 # fresh DB, (re)start server + web, seed every scenario (~4 min)
    node scripts/demo/signin.mjs       # sign-in code + link for cara@paddleup.test (or pass an email)
    node scripts/demo/bots.mjs         # start the Demo Cup auction in 45s; 5 bots play the other seats
    scripts/demo/start.sh              # start server + web again on the existing demo data
    scripts/demo/down.sh               # stop server + web

You play as `cara@paddleup.test` (also an app admin, so `/admin/import` works).

| Room | State | What to look at |
|---|---|---|
| Seed League | setup, no pool | "Load season data" on the player pool |
| Weekend Warriors | setup, pool loaded, invite open | config, pool editor (one edited player), set order, schedule sheet |
| Office Premier League | scheduled in 3 days | lobby as a Manager (Alex is Commissioner), 2 managers ready |
| Sunday Smashers | auction ended | squads, stat entry, points for 3 matches (one manual override) |
| Demo Cup | scheduled | the live simulation: you vs Alex, Bea, Chen, Dev and Esha |

Bot styles: Alex splurger, Bea shrewd, Chen balanced, Dev star hunter, Esha depth. Options:
`--start-in <sec>`, `--no-start`, `--human <email>` (leave a seat to a second browser window, for
example `--human alex@paddleup.test` and sign Alex in from a private window).

Demo Cup rules: 90 Cr purse, squad 9 to 12, minimums WK 1 / BAT 3 / BWL 3 / AR 2, at most 4
overseas, timers 15 s open / 10 s per bid / 4 s gap, 71 players in 7 sets. Lots nobody buys go to the
unsold pool; at main-done you choose the accelerated round or end, and the bots send their picks.
