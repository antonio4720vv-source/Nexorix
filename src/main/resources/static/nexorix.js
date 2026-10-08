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
        const links = [["dashboard", "/dashboard.html", "Panel"], ["traza", "/traza.html", "Trace"],
            ["importar", "/importar.html", "Importar"], ["reportes", "/reportes.html", "Reportes"]];
        return '<nav class="main-nav" aria-label="Secciones">' + links.map(([id, href, label]) =>
            `<a href="${href}"${id === active ? ' aria-current="page"' : ""}>${label}</a>`).join("") + "</nav>";
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
