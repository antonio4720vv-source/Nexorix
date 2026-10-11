// Nexorix - service worker: recibe los avisos push y los muestra como notificacion del dispositivo.

self.addEventListener("install", () => self.skipWaiting());
// Cache para que la app abra rapido e instale en el celular. Nunca guarda respuestas de /api
// (son datos financieros de la persona).
const CACHE = "nexorix-v2";
const OFFLINE = "/offline.html";
const SHELL = [OFFLINE, "/icon-192.png", "/nexorix.css", "/nexorix.js"];

self.addEventListener("install", (event) => {
    event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).catch(() => {}));
});

self.addEventListener("activate", (event) => event.waitUntil(
    caches.keys()
        .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
        .then(() => self.clients.claim())
));

self.addEventListener("fetch", (event) => {
    const request = event.request;
    const url = new URL(request.url);
    if (request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) {
        return;
    }
    // Primero la red (siempre lo ultimo); sin conexion, lo ultimo que se vio.
    event.respondWith(
        fetch(request)
            .then(response => {
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE).then(cache => cache.put(request, copy));
                }
                return response;
            })
            .catch(() => request.mode === "navigate"
                // Una pagina sin sus datos (que nunca se guardan) no sirve: se muestra el aviso.
                ? caches.match(OFFLINE)
                : caches.match(request).then(hit => hit || Response.error()))
    );
});

self.addEventListener("push", (event) => {
    let data = {};
    try {
        data = event.data ? event.data.json() : {};
    } catch (error) {
        data = { body: event.data ? event.data.text() : "" };
    }
    const title = data.title || "Nexorix";
    event.waitUntil(self.registration.showNotification(title, {
        body: data.body || "",
        icon: "/icon-192.png",
        badge: "/icon-192.png",
        tag: data.tag || "nexorix",
        renotify: true,
        data: { url: data.url || "/seguridad.html" }
    }));
});

self.addEventListener("notificationclick", (event) => {
    event.notification.close();
    const target = (event.notification.data && event.notification.data.url) || "/seguridad.html";
    event.waitUntil((async () => {
        const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
        for (const client of windows) {
            if ("focus" in client) {
                await client.focus();
                if ("navigate" in client) {
                    try { await client.navigate(target); } catch (error) { /* otra pestana */ }
                }
                return;
            }
        }
        await self.clients.openWindow(target);
    })());
});
