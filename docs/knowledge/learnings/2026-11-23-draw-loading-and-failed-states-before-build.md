tags: [design, rx, loading-states, review]
The auction-panels mocks drew only the loaded state, so loading and failed UI was invented in code and code review raised it again in rounds 2 to 4 (CR-11, CR-12, CR-15, CR-17) until the states were drawn and approved.
For any panel backed by a REST read, draw its loading and failed frames in docs/design/source before G2.
