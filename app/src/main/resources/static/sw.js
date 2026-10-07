// app/src/main/resources/static/sw.js — caches the app shell only. API responses are never cached (personal data).
const SHELL = "weekend-shell-v6";
const FILES = ["/", "/index.html", "/styles.css", "/app.js", "/theme.js", "/manifest.webmanifest", "/icon.svg", "/logo.svg", "/companion.js", "/companion.svg"];
self.addEventListener("install", (e) => e.waitUntil(caches.open(SHELL).then((c) => c.addAll(FILES))));
self.addEventListener("activate", (e) =>
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== SHELL).map((k) => caches.delete(k))))));
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.pathname.startsWith("/api/") || url.pathname.startsWith("/jobs/") || url.pathname.startsWith("/media/")) return;
  e.respondWith(caches.match(e.request).then((hit) => hit || fetch(e.request)));
});
