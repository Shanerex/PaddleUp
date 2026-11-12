tags: [rx, process, roadmap, build]
After 11 days 7 of 27 specs were merged (about 1 a working day). The cost was not the spec files: every spec ran the full lane in series (about 7 human turns each), each PR was reviewed twice, scope grew after G2, and closes needed waivers (pom.xml as version_file, plan/tasks caps, architecture.md tasks).
Decision: full lane, one at a time, for 09, 11-14, 17, 22-26; quick lane in a second worktree, in parallel, for 08, 10, 15, 16, 19, 20, 27; 18 and 21 deferred. Lanes and order are in docs/roadmap.md.
Rules added to CLAUDE.md: scope freezes at G2, one review pass before the PR, oversize plans are split, architecture.md patches are batched on main. `version_file` is dropped from .rx/config.yml: pom.xml is the dependency manifest, so protecting it blocked every close that added a dependency.
Machine time was a minor cost: make test took 99 s, and 86 s once npm ci runs only on a lockfile change (it cost 9 to 32 s on every make test and make lint call).
Rejected: two parallel full-lane tracks (the owner wants one full spec open at a time) and changing the rx plugin now (gaps kept for v0.2).
