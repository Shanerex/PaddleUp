// Minimal service worker: enables installability only.
// No fetch handler, no Cache API: every request goes to the network.
self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});
