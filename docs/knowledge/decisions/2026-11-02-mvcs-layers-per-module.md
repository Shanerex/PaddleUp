tags: [architecture, modules, server, layering]
# MVCS layers inside each module
Decision: every module package has controller, service, repository, model and dto sub-packages; other modules use only its service and dto.
Why: feature modules keep domain boundaries, and the familiar Spring MVCS layering inside them keeps code quick to read and review.
Also: sign-in and users get their own `accounts` module instead of living in rooms, since auction and scoring need identity too.
