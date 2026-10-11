// Nexorix: permite instalar la app en el celular y abrir mas rapido.
// Nunca guarda respuestas de /api (son datos financieros de la persona).
const CACHE = "nexorix-v1";

self.addEventListener("install", () => self.skipWaiting());

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

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
            .catch(() => caches.match(request))
    );
});
