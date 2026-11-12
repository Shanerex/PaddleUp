tags: [testing, playwright, e2e]
Playwright clears `outputDir` (`web/test-results`) at the start of every run, so shots saved there by one spec are gone after the next run; write comparison shots outside it.
`seedRoom` in `web/e2e/support/seed.mjs` has no `squadMax` or `categoryQuotas` options, so a squad-full or quota lock must be set up by hand; add them when the next spec needs a lock in the browser.
