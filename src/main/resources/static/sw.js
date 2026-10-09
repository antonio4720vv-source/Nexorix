// Nexorix - service worker: recibe los avisos push y los muestra como notificacion del dispositivo.

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

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
