---
feature: 2026-11-02-22-llm-gateway
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-22-llm-gateway main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-22-llm-gateway

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-130, FR-131, NFR-11; module: advisor
- 2026-11-02 intake: depends on: 2026-11-02-04-rooms
- 2026-11-02 intake: why: every AI feature needs one safe, provider-agnostic LLM entry point.
- 2026-11-02 intake: who: advisor features (report card, lifelines).
- 2026-11-02 intake: done-when: Spring AI OpenAI-compatible client with base_url and model per feature (Ollama locally); per-room call cap; input sanitised, length-capped and passed as delimited JSON; output schema-validated; any failure returns a typed result and never affects the auction.
