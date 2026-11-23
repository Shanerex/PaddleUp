tags: [web, fetch, websocket, resync]
useFeed chained every events read on one promise; a single fetch that never settled (mobile drop) blocked every later resync, so the feed froze until reload (CR-19).
Let each effect run drop a superseded result instead of queueing behind the last read, and keep hook-level useMemo off a 100 ms ticker (CR-26).
