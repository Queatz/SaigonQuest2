const CACHE = "saigon-town-v2";
const PRECACHE = [
    "/index.html",
    "/styles.css",
    "/sq-config.js",
    "/webApp.js",
    "/manifest.json",
    "/icon.png",
    "/icon-192.png",
    "/icon-512.png",
    "/icon-maskable-512.png",
    "/apple-touch-icon.png",
];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE)
            .then((cache) => cache.addAll(PRECACHE))
            .then(() => self.skipWaiting()),
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(
                keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)),
            ))
            .then(() => self.clients.claim()),
    );
});

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.method !== "GET") return;

    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;
    if (url.pathname.startsWith("/api/") || url.pathname.startsWith("/media/")) return;

    event.respondWith(
        fetch(request)
            .then((response) => {
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE).then((cache) => cache.put(request, copy));
                }
                return response;
            })
            .catch(() => caches.match(request).then((cached) => {
                if (cached) return cached;
                    if (request.mode === "navigate") return caches.match("/index.html");
                    return undefined;
            })),
    );
});

self.addEventListener("push", (event) => {
    let data = { title: "Saigon.Town", body: "", url: "/" };
    try {
        if (event.data) {
            data = Object.assign(data, event.data.json());
        }
    } catch (_) {
    }
    const url = data.url || "/";
    event.waitUntil(
        self.registration.showNotification(data.title || "Saigon.Town", {
            body: data.body || "",
            data: { url },
            icon: "/icon-192.png",
            badge: "/icon-192.png",
            tag: data.id || url,
            renotify: true,
        }),
    );
});

self.addEventListener("notificationclick", (event) => {
    event.notification.close();
    const target = (event.notification.data && event.notification.data.url) || "/";
    const dest = new URL(target, self.location.origin).href;
    event.waitUntil(
        self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
            for (const client of windows) {
                if ("focus" in client) {
                    const focused = client.focus();
                    if ("navigate" in client) return client.navigate(dest);
                    return focused;
                }
            }
            if (self.clients.openWindow) return self.clients.openWindow(dest);
            return undefined;
        }),
    );
});
