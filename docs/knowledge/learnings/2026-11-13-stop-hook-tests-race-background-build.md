tags: [rx, build, testing]
The rx stop hook ran make test while rx:dev was still rebuilding server/target in the background: 291 ITs errored with "class path resource ... .class does not exist". It was a race, not a bug; a clean rerun after the agent finished was green. Check the first Caused by before fixing anything.
