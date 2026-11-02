tags: [architecture, modules, server, layering]
# exception, config and util packages per module
Decision: each module adds exception, config and util sub-packages beside the MVCS five; supersedes the package list in 2026-11-02-mvcs-layers-per-module.
Why: accounts put security config, properties, error types and helpers inside controller and service, which blurred the layers.
Access: other modules use service, dto and exception (callers must catch what a service throws); config and util stay module-private.
