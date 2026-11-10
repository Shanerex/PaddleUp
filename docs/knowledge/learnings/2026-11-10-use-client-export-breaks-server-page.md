tags: [web, testing, nextjs]
`stepOfSection` was exported from a `"use client"` file and called by a server page; jsdom unit tests passed, only the Playwright run on a real stack failed. Keep helpers shared with server pages in a plain module (`web/src/rooms/sections.ts`).
Run the changed flows in a real browser, not just vitest.
