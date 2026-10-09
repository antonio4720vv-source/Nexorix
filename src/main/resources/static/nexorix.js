// ============================================================
// NEXORIX - utilidades compartidas por las paginas
// ============================================================

const Nexorix = {

    /** Formato de pesos colombianos sin decimales. */
    money(value) {
        return new Intl.NumberFormat("es-CO", {
            style: "currency",
            currency: "COP",
            maximumFractionDigits: 0
        }).format(Number(value || 0));
    },

    /** Fecha y hora legibles. */
    date(value) {
        if (!value) {
            return "";
        }
        return new Date(value).toLocaleString("es-CO", {
            day: "numeric",
            month: "short",
            hour: "numeric",
            minute: "2-digit"
        });
    },

    /**
     * Escapa texto antes de meterlo en HTML.
     * Evita que una descripcion como "<script>..." se ejecute.
     */
    esc(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    },

    /** Primera letra en mayuscula y el resto en minuscula. */
    sentence(text) {
        const lower = String(text || "").toLowerCase();
        return lower.charAt(0).toUpperCase() + lower.slice(1);
    },

    /** Iniciales para el avatar: "Ana Prueba Dos" -> "AP". */
    initials(name) {
        return String(name || "?")
            .trim()
            .split(/\s+/)
            .slice(0, 2)
            .map(part => part.charAt(0).toUpperCase())
            .join("");
    },

    /** Color estable para cada banco (el mismo nombre, el mismo color). */
    bankColor(bank) {
        const palette = ["#0e8a7a", "#6a5ae0", "#d0384f", "#c98a1b", "#2f6fdb", "#16393e"];
        let hash = 0;
        for (const char of String(bank || "")) {
            hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
        }
        return palette[hash % palette.length];
    },

    /** Barra de navegacion comun: marca la pagina actual. */
    nav(active) {
        const links = [["dashboard", "/dashboard.html", "nav.panel"], ["contador", "/contador.html", "nav.contador"],
            ["compras", "/compras.html", "nav.compras"], ["dividir", "/dividir.html", "nav.dividir"],
            ["seguridad", "/seguridad.html", "nav.seguridad"], ["ajustes", "/ajustes.html", "nav.ajustes"]];
        return '<nav class="main-nav" aria-label="Secciones">' + links.map(([id, href, key]) =>
            `<a href="${href}"${id === active ? ' aria-current="page"' : ""}>${Nexorix.t(key)}</a>`).join("") + "</nav>";
    },

    /* ---------- Idioma ---------- */

    /** Textos de la interfaz comun (navegacion, barra Demo, ajustes). Lo demas de cada pagina sigue en espanol. */
    i18n: {
        es: {
            "nav.panel": "Panel", "nav.contador": "Contador y reportes", "nav.compras": "Compras",
            "nav.dividir": "Dividir gastos", "nav.seguridad": "Seguridad", "nav.ajustes": "Ajustes",
            "demo.label": "Demo", "demo.on": "Demo activo", "demo.title": "Modo Demo: datos de ejemplo para probar cada función.",
            "demo.load": "Cargar datos de ejemplo", "demo.exit": "Salir del demo", "demo.simulate": "Simular:",
            "demo.COMPRA": "Compra", "demo.COMPRA_INUSUAL": "Compra inusual", "demo.TRANSFER_PROPIA": "Transferencia propia",
            "demo.TRANSFER_TERCERO": "Transferencia a tercero", "demo.VIAJE_IMPOSIBLE": "Viaje imposible",
            "settings.title": "Ajustes", "settings.language": "Idioma", "settings.languageHelp":
                "Cambia los menús, los botones generales y esta página. El resto del contenido sigue en español por ahora.",
            "settings.demo": "Modo Demo", "settings.demoHelp": "Muestra la barra Demo para probar cada función con datos de ejemplo.",
            "settings.saved": "Idioma guardado.", "settings.es": "Español", "settings.en": "English"
        },
        en: {
            "nav.panel": "Dashboard", "nav.contador": "Accounting & reports", "nav.compras": "Purchases",
            "nav.dividir": "Split expenses", "nav.seguridad": "Security", "nav.ajustes": "Settings",
            "demo.label": "Demo", "demo.on": "Demo on", "demo.title": "Demo mode: sample data to try every feature.",
            "demo.load": "Load sample data", "demo.exit": "Exit demo", "demo.simulate": "Simulate:",
            "demo.COMPRA": "Purchase", "demo.COMPRA_INUSUAL": "Unusual purchase", "demo.TRANSFER_PROPIA": "Own transfer",
            "demo.TRANSFER_TERCERO": "Transfer to third party", "demo.VIAJE_IMPOSIBLE": "Impossible trip",
            "settings.title": "Settings", "settings.language": "Language", "settings.languageHelp":
                "Changes menus, general buttons and this page. The rest of the content stays in Spanish for now.",
            "settings.demo": "Demo mode", "settings.demoHelp": "Shows the Demo bar to try each feature with sample data.",
            "settings.saved": "Language saved.", "settings.es": "Español", "settings.en": "English"
        }
    },

    /** Idioma elegido en este navegador: "es" (por defecto) o "en". */
    lang() {
        try {
            return localStorage.getItem("nexorix.lang") === "en" ? "en" : "es";
        } catch (error) {
            return "es";
        }
    },

    setLang(code) {
        try {
            localStorage.setItem("nexorix.lang", code === "en" ? "en" : "es");
        } catch (error) {
            // Sin almacenamiento (navegador privado): el cambio dura solo esta vista.
        }
    },

    /** Texto traducido; si falta en ingles, cae al espanol. */
    t(key) {
        const table = Nexorix.i18n[Nexorix.lang()] || {};
        return table[key] || Nexorix.i18n.es[key] || key;
    },

    /** Aplica el idioma a los elementos con data-i18n (cambia su texto). */
    applyLang() {
        document.documentElement.lang = Nexorix.lang();
        document.querySelectorAll("[data-i18n]").forEach(el => el.textContent = Nexorix.t(el.dataset.i18n));
    },

    /* ---------- Modo Demo ---------- */

    demo: {
        /** Escenarios de la barra Demo: el servidor los arma como webhooks del banco. */
        scenarios: ["COMPRA", "COMPRA_INUSUAL", "TRANSFER_PROPIA", "TRANSFER_TERCERO", "VIAJE_IMPOSIBLE"],

        isOn() {
            try {
                return localStorage.getItem("nexorix.demo") === "on";
            } catch (error) {
                return false;
            }
        },

        /** Enciende o apaga la barra Demo en este navegador. */
        set(on) {
            try {
                localStorage.setItem("nexorix.demo", on ? "on" : "off");
            } catch (error) {
                // Sin almacenamiento: la barra solo dura en esta vista.
            }
            Nexorix.mountDemo();
        },

        /** Enciende el modo Demo y, si aun no hay datos de ejemplo, los crea. */
        async enable() {
            Nexorix.demo.set(true);
            try {
                await Nexorix.demo.seed();
            } catch (error) {
                Nexorix.toast(error.message, "error");
            }
            document.dispatchEvent(new Event("nexorix:changed"));
        },

        /** Crea cuentas de ejemplo, vincula una al banco y carga compras habituales. */
        async seed() {
            const existing = await Nexorix.api("/api/accounts");
            if (!existing.ok) throw new Error(Nexorix.errorOf(existing, "No fue posible leer tus cuentas."));
            if (existing.data.some(a => String(a.name).endsWith("(ejemplo)"))) return;

            const create = async (path, params) => {
                const result = await Nexorix.api(path + "?" + new URLSearchParams(params), { method: "POST" });
                if (!result.ok) throw new Error(Nexorix.errorOf(result, "No fue posible crear el ejemplo."));
                return result.data;
            };

            const nequi = await create("/api/accounts",
                { name: "Nequi (ejemplo)", bank: "Nequi", type: "BILLETERA", balance: 3000000 });
            await create("/api/accounts",
                { name: "Davivienda (ejemplo)", bank: "Davivienda", type: "AHORROS", balance: 0 });
            await create("/api/transactions",
                { accountId: nequi.id, type: "INGRESO", amount: 3000000, description: "Salario" });

            const linked = await Nexorix.api("/api/bank/links", { method: "POST", json: { accountId: nequi.id } });
            if (!linked.ok) throw new Error(Nexorix.errorOf(linked, "No fue posible vincular el banco de ejemplo."));
            await Nexorix.demo.run("SEMILLA", true);
            Nexorix.toast("Datos de ejemplo cargados. Prueba los demás escenarios desde la barra Demo.");
        },

        /** Ejecuta un escenario del simulador de banco. */
        async run(scenario, quiet) {
            const result = await Nexorix.api("/api/bank/demo/" + scenario, { method: "POST" });
            if (!result.ok) {
                const message = Nexorix.errorOf(result, "No se pudo simular.");
                if (!quiet) Nexorix.toast(message, "error");
                throw new Error(message);
            }
            if (!quiet) {
                Nexorix.toast(result.data.map(x => x.status).join(", ") + ". Revisa Panel y Contador y reportes.");
                document.dispatchEvent(new Event("nexorix:changed"));
            }
            return result.data;
        }
    },

    /**
     * Dibuja lo comun en todas las paginas: boton Demo en la barra superior,
     * barra Demo (si esta encendida) e idioma. Se llama al cargar la pagina.
     */
    mountDemo() {
        const topbar = document.querySelector(".topbar");
        if (!topbar) return;
        let who = topbar.querySelector(".who");
        if (!who) {
            who = document.createElement("div");
            who.className = "who";
            topbar.appendChild(who);
        }

        let toggle = document.getElementById("demoToggle");
        if (!toggle) {
            toggle = document.createElement("button");
            toggle.id = "demoToggle";
            toggle.type = "button";
            toggle.className = "demo-toggle";
            toggle.addEventListener("click", () => Nexorix.demo.set(!Nexorix.demo.isOn()));
            who.prepend(toggle);
        }
        const on = Nexorix.demo.isOn();
        toggle.textContent = on ? Nexorix.t("demo.on") : Nexorix.t("demo.label");
        toggle.setAttribute("aria-pressed", String(on));
        toggle.title = Nexorix.t("demo.title");

        let strip = document.getElementById("demoStrip");
        if (!on) {
            if (strip) strip.remove();
            return;
        }
        if (!strip) {
            strip = document.createElement("div");
            strip.id = "demoStrip";
            strip.className = "demo-strip";
            strip.setAttribute("role", "region");
            strip.setAttribute("aria-label", Nexorix.t("demo.title"));
            topbar.after(strip);
            strip.addEventListener("click", async (event) => {
                const button = event.target.closest("button[data-demo]");
                if (!button) return;
                button.disabled = true;
                try {
                    if (button.dataset.demo === "load") await Nexorix.demo.enable();
                    else if (button.dataset.demo === "exit") Nexorix.demo.set(false);
                    else await Nexorix.demo.run(button.dataset.demo);
                } catch (error) {
                    Nexorix.toast(error.message, "error");
                } finally {
                    button.disabled = false;
                }
            });
        }
        strip.innerHTML = `<p>${Nexorix.esc(Nexorix.t("demo.title"))}</p><div class="demo-actions">`
            + `<button class="button small" type="button" data-demo="load">${Nexorix.esc(Nexorix.t("demo.load"))}</button>`
            + `<span class="demo-label">${Nexorix.esc(Nexorix.t("demo.simulate"))}</span>`
            + Nexorix.demo.scenarios.map(id => `<button class="button secondary small" type="button" data-demo="${id}">`
                + `${Nexorix.esc(Nexorix.t("demo." + id))}</button>`).join("")
            + `<button class="text-button" type="button" data-demo="exit">${Nexorix.esc(Nexorix.t("demo.exit"))}</button></div>`;
    },

    /** Iconos SVG (trazo simple, heredan el color del texto). */
    icon(name) {
        const paths = {
            in: '<path d="M12 5v14M5 12l7 7 7-7"/>',
            out: '<path d="M12 19V5M5 12l7-7 7 7"/>',
            moved: '<path d="M7 7h11l-3-3M17 17H6l3 3"/>',
            arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
            shield: '<path d="M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6l8-3z"/><path d="M9 12l2 2 4-4"/>',
            check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
            close: '<path d="M6 6l12 12M18 6L6 18"/>',
            clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
            info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
            spinner: '<path d="M12 3a9 9 0 1 0 9 9" />',
            face: '<circle cx="12" cy="10" r="4"/><path d="M5 20c1.5-3.5 4-5 7-5s5.5 1.5 7 5"/><path d="M3 7V4h3M21 7V4h-3M3 17v3h3M21 17v3h-3"/>',
            key: '<circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3"/>',
            plus: '<path d="M12 5v14M5 12h14"/>',
            ai: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z"/>',
            upload: '<path d="M12 16V4M7 9l5-5 5 5"/><path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/>',
            file: '<path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"/><path d="M14 3v5h5"/>',
            wallet: '<rect x="3" y="6" width="18" height="13" rx="3"/><path d="M3 10h18M16 15h2"/>',
            sparkle: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/>',
            chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
            download: '<path d="M12 4v12M7 11l5 5 5-5"/><path d="M4 20h16"/>',
            chat: '<path d="M4 5h16v11H9l-5 4z"/>',
            mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>',
            trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
            send: '<path d="M4 12l16-8-6 16-2.5-6.5z"/>',
            undo: '<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/>',
            split: '<path d="M4 12h6M10 12l8-6M10 12l8 6M18 6h2M18 18h2"/>',
            merge: '<path d="M4 6l8 6-8 6M12 12h8"/>',
            logo: '<path d="M4 17l5-5 4 4 7-8"/><circle cx="20" cy="8" r="1.6" fill="currentColor"/>'
        };
        return '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
            + 'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
            + (paths[name] || "") + '</svg>';
    },

    /**
     * Llama a la API de Nexorix con la sesion del navegador.
     * Devuelve { ok, status, data, text } sin lanzar error por 4xx/5xx.
     */
    async api(path, options = {}) {
        const settings = {
            credentials: "same-origin",
            ...options
        };

        if (options.json !== undefined) {
            settings.headers = {
                "Content-Type": "application/json",
                ...(options.headers || {})
            };
            settings.body = JSON.stringify(options.json);
            delete settings.json;
        }

        const response = await fetch(path, settings);
        const text = await response.text();

        let data = null;
        try {
            data = text ? JSON.parse(text) : null;
        } catch (error) {
            data = null;
        }

        return {
            ok: response.ok,
            status: response.status,
            data,
            text
        };
    },

    /** Mensaje de error de una respuesta de la API (JSON o texto). */
    errorOf(result, fallback) {
        if (result && result.data && result.data.error) {
            return result.data.error;
        }
        if (result && result.status === 400 && result.text && !result.data) {
            return result.text;
        }
        return fallback;
    },

    /** Convierte "1.500.000" o "$ 1.500.000" en 1500000 (sin centavos). */
    parseMoney(text) {
        const digits = String(text || "").replace(/\D/g, "");
        return digits ? Number(digits) : NaN;
    },

    /**
     * Campo de dinero: mientras se escribe pone los puntos de miles
     * y muestra debajo el valor en pesos.
     */
    moneyInput(input, preview) {
        input.addEventListener("input", () => {
            const value = Nexorix.parseMoney(input.value);
            input.value = isNaN(value) ? "" : value.toLocaleString("es-CO");
            if (preview) {
                preview.textContent = isNaN(value) ? "" : Nexorix.money(value);
            }
        });
    },

    /** Aviso flotante que desaparece solo. */
    toast(text, type) {
        let box = document.getElementById("nexorixToast");
        if (!box) {
            box = document.createElement("div");
            box.id = "nexorixToast";
            box.setAttribute("role", "status");
            box.setAttribute("aria-live", "polite");
            document.body.appendChild(box);
        }
        box.className = "toast show " + (type || "");
        box.textContent = text;
        clearTimeout(box._timer);
        box._timer = setTimeout(() => box.className = "toast " + (type || ""), 3200);
    },

    /** Muestra un mensaje en un contenedor (tipo: ok, error, wait). */
    say(element, message, type) {
        element.className = "notice " + (type || "");
        element.textContent = message || "";
    }
};

document.addEventListener("DOMContentLoaded", () => {

    // Idioma, boton Demo y barra Demo (comunes a todas las paginas).
    Nexorix.applyLang();
    Nexorix.mountDemo();

    // Dibuja el logo en cualquier elemento con la clase "brand-mark".
    document.querySelectorAll(".brand-mark").forEach(mark => {
        mark.innerHTML = Nexorix.icon("logo");
        mark.style.color = "#5ee0c5";
    });

    // Casillas "Mostrar contraseña" / "Mostrar PIN".
    // Uso: <input type="checkbox" data-reveal="campo1,campo2">
    // Al marcarla, los campos se ven como texto; al desmarcarla, se ocultan.
    document.querySelectorAll("input[type=checkbox][data-reveal]").forEach(box => {
        const fields = box.dataset.reveal
            .split(",")
            .map(id => document.getElementById(id.trim()))
            .filter(Boolean);

        box.checked = false;
        box.addEventListener("change", () => {
            fields.forEach(field => field.type = box.checked ? "text" : "password");
        });
    });
});
