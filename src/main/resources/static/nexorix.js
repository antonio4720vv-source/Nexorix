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
            ["dividir", "/dividir.html", "nav.dividir"],
            ["seguridad", "/seguridad.html", "nav.seguridad"], ["ajustes", "/ajustes.html", "nav.ajustes"]];
        return '<nav class="main-nav" aria-label="Secciones">' + links.map(([id, href, key]) =>
            `<a href="${href}"${id === active ? ' aria-current="page"' : ""}>${Nexorix.t(key)}</a>`).join("") + "</nav>";
    },

    /* ---------- Idioma ---------- */

    /** Textos de la interfaz comun (navegacion, barra Demo, ajustes). Lo demas de cada pagina sigue en espanol. */
    i18n: {
        es: {
            "nav.panel": "Panel", "nav.contador": "Contador", "nav.compras": "Compras",
            "nav.dividir": "Compras y gastos", "nav.seguridad": "Seguridad", "nav.ajustes": "Ajustes",
            "demo.label": "Demo", "demo.on": "Demo activo", "demo.title": "Modo Demo: datos de ejemplo para probar cada función.",
            "demo.load": "Cargar datos de ejemplo", "demo.exit": "Salir del demo", "demo.simulate": "Simular:",
            "demo.COMPRA": "Compra", "demo.COMPRA_INUSUAL": "Compra inusual", "demo.TRANSFER_PROPIA": "Transferencia propia",
            "demo.TRANSFER_TERCERO": "Transferencia a tercero", "demo.VIAJE_IMPOSIBLE": "Viaje imposible", "demo.PAGO_DIVISION": "Un amigo te paga su parte", "demo.TARJETA_AJENA": "Tarjeta no registrada",
            "demo.tools": "Herramientas de demo", "demo.toolsOpen": "Dinero y movimientos…", "demo.account": "Cuenta", "demo.amount": "Monto a agregar", "demo.password": "Clave de demo", "demo.addMoney": "Agregar dinero", "demo.resetMoney": "Reiniciar dinero", "demo.resetTx": "Reiniciar movimientos de la cuenta",
            "demo.bank": "Banco", "demo.split": "Dividir gastos", "demo.statements": "Extractos y reportes",
            "demo.splitRun": "Dividir una cena", "demo.importRun": "Subir extracto de ejemplo", "demo.goReports": "Ver reportes",
            "demo.goTrace": "Ver Trace", "demo.goPurchases": "Mis compras", "demo.expense": "Registrar un gasto",
            "profile.title": "Mi cuenta", "profile.name": "Nombre", "profile.username": "Usuario", "profile.account": "N.º de cuenta",
            "profile.personal": "Información personal", "profile.cedula": "Cédula", "profile.phone": "Teléfono", "profile.email": "Correo",
            "profile.kyc": "Identidad", "profile.note": "Por tu seguridad, los datos personales se muestran censurados.",
            "profile.close": "Cerrar", "profile.changePhoto": "Cambiar foto", "profile.removePhoto": "Quitar foto", "profile.photoSaved": "Foto actualizada.", "profile.none": "Sin registrar",
            "settings.title": "Ajustes", "settings.language": "Idioma", "settings.languageHelp":
                "Cambia los menús, los botones generales y esta página. El resto del contenido sigue en español por ahora.",
            "settings.demo": "Modo Demo", "settings.demoHelp": "Muestra la barra Demo para probar cada función con datos de ejemplo.",
            "settings.saved": "Idioma guardado.", "settings.es": "Español", "settings.en": "English"
        },
        en: {
            "nav.panel": "Dashboard", "nav.contador": "Accounting & reports", "nav.compras": "Purchases",
            "nav.dividir": "Purchases & splits", "nav.seguridad": "Security", "nav.ajustes": "Settings",
            "demo.label": "Demo", "demo.on": "Demo on", "demo.title": "Demo mode: sample data to try every feature.",
            "demo.load": "Load sample data", "demo.exit": "Exit demo", "demo.simulate": "Simulate:",
            "demo.COMPRA": "Purchase", "demo.COMPRA_INUSUAL": "Unusual purchase", "demo.TRANSFER_PROPIA": "Own transfer",
            "demo.TRANSFER_TERCERO": "Transfer to third party", "demo.VIAJE_IMPOSIBLE": "Impossible trip", "demo.PAGO_DIVISION": "A friend pays their share", "demo.TARJETA_AJENA": "Unregistered card",
            "demo.tools": "Demo tools", "demo.toolsOpen": "Money and transactions…", "demo.account": "Account", "demo.amount": "Amount to add", "demo.password": "Demo password", "demo.addMoney": "Add money", "demo.resetMoney": "Reset money", "demo.resetTx": "Reset account transactions",
            "demo.bank": "Bank", "demo.split": "Split expenses", "demo.statements": "Statements & reports",
            "demo.splitRun": "Split a dinner", "demo.importRun": "Upload sample statement", "demo.goReports": "View reports",
            "demo.goTrace": "View Trace", "demo.goPurchases": "My purchases", "demo.expense": "Log an expense",
            "profile.title": "My account", "profile.name": "Name", "profile.username": "Username", "profile.account": "Account no.",
            "profile.personal": "Personal information", "profile.cedula": "ID number", "profile.phone": "Phone", "profile.email": "Email",
            "profile.kyc": "Identity", "profile.note": "For your security, personal data is shown masked.",
            "profile.close": "Close", "profile.changePhoto": "Change photo", "profile.removePhoto": "Remove photo", "profile.photoSaved": "Photo updated.", "profile.none": "Not set",
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
        scenarios: ["COMPRA", "COMPRA_INUSUAL", "TRANSFER_PROPIA", "TRANSFER_TERCERO", "VIAJE_IMPOSIBLE", "TARJETA_AJENA", "PAGO_DIVISION"],

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
            const debit = await create("/api/accounts",
                { name: "Visa débito (ejemplo)", bank: "Bancolombia", type: "DEBITO", balance: 800000, cardNumber: "4111 1111 1111 1111", cardExpiry: "12/30" });
            await create("/api/accounts",
                { name: "Mastercard crédito (ejemplo)", bank: "Davivienda", type: "CREDITO", balance: 1500000,
                  creditLimit: 2000000, cardNumber: "5555 5555 5555 4444", cardExpiry: "09/29" });
            await create("/api/transactions",
                { accountId: nequi.id, type: "INGRESO", amount: 3000000, description: "Salario" });

            const linked = await Nexorix.api("/api/bank/links", { method: "POST", json: { accountId: debit.id } });
            if (!linked.ok) throw new Error(Nexorix.errorOf(linked, "No fue posible vincular el banco de ejemplo."));
            await Nexorix.demo.run("SEMILLA", true);
            Nexorix.toast("Datos de ejemplo cargados. Prueba los demás escenarios desde la barra Demo.");
        },

        /** Demo de Dividir gastos: amigos de ejemplo y una cena dividida entre tres. */
        async split() {
            const result = await Nexorix.api("/api/split/demo", { method: "POST" });
            if (!result.ok) throw new Error(Nexorix.errorOf(result, "No se pudo crear la división de ejemplo."));
            Nexorix.toast("Cena dividida con Camila y Andrés. Mira \"Me deben\".");
            if (location.pathname === "/dividir.html") location.hash = "#dividir", location.reload();
            else location.href = "/dividir.html";
        },

        /** Sube un extracto de ejemplo a la primera cuenta y abre Contador para verlo procesar. */
        async importSample() {
            await Nexorix.demo.seed();
            const accounts = await Nexorix.api("/api/accounts");
            if (!accounts.ok || !accounts.data.length) throw new Error("Crea una cuenta primero.");
            const file = await fetch("/demo-extracto.csv").then(r => r.blob());
            const form = new FormData();
            form.append("files", new File([file], "extracto-demo.csv", { type: "text/csv" }));
            form.append("accountId", accounts.data[0].id);
            const result = await Nexorix.api("/api/imports", { method: "POST", body: form });
            if (!result.ok) throw new Error(Nexorix.errorOf(result, "No se pudo subir el extracto de ejemplo."));
            try {
                sessionStorage.setItem("nexorix.imports", JSON.stringify(result.data.map(b => b.batchId)));
            } catch (error) { /* sin almacenamiento */ }
            location.href = "/contador.html";
        },

        /** Registra un gasto de ejemplo (en la primera cuenta). */
        async expense() {
            await Nexorix.demo.seed();
            const accounts = await Nexorix.api("/api/accounts");
            if (!accounts.ok || !accounts.data.length) throw new Error("Crea una cuenta primero.");
            const params = new URLSearchParams({ accountId: accounts.data[0].id, type: "EGRESO", amount: 36500,
                description: "Almuerzo de ejemplo" });
            const result = await Nexorix.api("/api/transactions?" + params, { method: "POST" });
            if (!result.ok) throw new Error(Nexorix.errorOf(result, "No se pudo registrar el gasto."));
            Nexorix.toast("Gasto registrado.");
            document.dispatchEvent(new Event("nexorix:changed"));
        },

        /** Herramientas de demo con clave: agregar dinero, reiniciar dinero y reiniciar movimientos de una cuenta. */
        async admin() {
            const accounts = await Nexorix.api("/api/accounts");
            if (!accounts.ok || !accounts.data.length) throw new Error("Crea una cuenta primero.");
            let dialog = document.getElementById("demoAdminDialog");
            if (dialog) dialog.remove();
            dialog = document.createElement("dialog");
            dialog.id = "demoAdminDialog";
            dialog.className = "sheet";
            dialog.innerHTML = `<div class="sheet-body"><div class="sheet-head"><h2>${Nexorix.esc(Nexorix.t("demo.tools"))}</h2>`
                + `<button class="text-button" type="button" data-x>${Nexorix.esc(Nexorix.t("profile.close"))}</button></div>`
                + `<div class="field"><label for="daAccount">${Nexorix.esc(Nexorix.t("demo.account"))}</label><select id="daAccount">`
                + accounts.data.map(a => `<option value="${a.id}">${Nexorix.esc(a.name)}</option>`).join("") + `</select></div>`
                + `<div class="field"><label for="daAmount">${Nexorix.esc(Nexorix.t("demo.amount"))}</label>`
                + `<input id="daAmount" inputmode="numeric" placeholder="$ 0" autocomplete="off"></div>`
                + `<div class="field"><label for="daPass">${Nexorix.esc(Nexorix.t("demo.password"))}</label>`
                + `<input id="daPass" type="password" autocomplete="off"></div>`
                + `<div class="demo-group"><button class="button small" type="button" data-act="add">${Nexorix.esc(Nexorix.t("demo.addMoney"))}</button>`
                + `<button class="button secondary small" type="button" data-act="money">${Nexorix.esc(Nexorix.t("demo.resetMoney"))}</button>`
                + `<button class="button secondary small" type="button" data-act="tx">${Nexorix.esc(Nexorix.t("demo.resetTx"))}</button></div>`
                + `<p class="notice" id="daMsg" role="status" aria-live="polite"></p></div>`;
            document.body.appendChild(dialog);
            dialog.addEventListener("click", async (e) => {
                if (e.target === dialog || e.target.dataset.x !== undefined) { dialog.close(); return; }
                const act = e.target.dataset.act;
                if (!act) return;
                const body = { password: dialog.querySelector("#daPass").value,
                    accountId: Number(dialog.querySelector("#daAccount").value),
                    amount: Nexorix.parseMoney(dialog.querySelector("#daAmount").value || "0") };
                const path = { add: "add-money", money: "reset-money", tx: "reset-transactions" }[act];
                const r = await Nexorix.api("/api/bank/demo-admin/" + path, { method: "POST", json: body });
                Nexorix.say(dialog.querySelector("#daMsg"), r.ok ? "Listo ✔" : Nexorix.errorOf(r, "No se pudo."), r.ok ? "ok" : "error");
                if (r.ok) document.dispatchEvent(new Event("nexorix:changed"));
            });
            dialog.showModal();
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
                Nexorix.toast(result.data.map(x => x.status).join(", ") + ". Revisa Panel y Contador.");
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
                const link = event.target.closest("a[data-go]");
                if (link) {
                    Nexorix.demo.seed().catch(() => {}).finally(() => { location.href = link.dataset.go; });
                    event.preventDefault();
                    return;
                }
                const button = event.target.closest("button[data-demo]");
                if (!button) return;
                button.disabled = true;
                try {
                    if (button.dataset.demo === "load") await Nexorix.demo.enable();
                    else if (button.dataset.demo === "exit") Nexorix.demo.set(false);
                    else if (button.dataset.demo === "split") await Nexorix.demo.split();
                    else if (button.dataset.demo === "import") await Nexorix.demo.importSample();
                    else if (button.dataset.demo === "admin") await Nexorix.demo.admin();
                    else if (button.dataset.demo === "expense") await Nexorix.demo.expense();
                    else await Nexorix.demo.run(button.dataset.demo);
                } catch (error) {
                    Nexorix.toast(error.message, "error");
                } finally {
                    button.disabled = false;
                }
            });
        }
        const t = (k) => Nexorix.esc(Nexorix.t(k));
        const btn = (id, label, primary) => `<button class="button ${primary ? "" : "secondary "}small" type="button" data-demo="${id}">${label}</button>`;
        const go = (url, label) => `<a class="button secondary small" href="${url}" data-go="${url}">${label}</a>`;
        strip.innerHTML = `<p>${t("demo.title")}</p>`
            + `<div class="demo-group"><span class="demo-label">${t("demo.bank")}</span>${btn("load", t("demo.load"), true)}`
            + Nexorix.demo.scenarios.map(id => btn(id, t("demo." + id))).join("") + `</div>`
            + `<div class="demo-group"><span class="demo-label">${t("demo.split")}</span>${btn("split", t("demo.splitRun"))}`
            + `${btn("expense", t("demo.expense"))}${go("/dividir.html#compras", t("demo.goPurchases"))}</div>`
            + `<div class="demo-group"><span class="demo-label">${t("demo.statements")}</span>${btn("import", t("demo.importRun"))}`
            + `${go("/contador.html#trace", t("demo.goTrace"))}${go("/contador.html#reportes", t("demo.goReports"))}`
            + `<div class="demo-group"><span class="demo-label">${t("demo.tools")}</span>${btn("admin", t("demo.toolsOpen"))}</div>`
            + `<button class="text-button" type="button" data-demo="exit">${t("demo.exit")}</button></div>`;
    },

    /** Pone la foto como fondo del avatar (o la quita y vuelven las iniciales). */
    paintPhoto(el, image) {
        if (!el) return;
        if (image) {
            el.style.backgroundImage = `url("${image}")`;
            el.style.backgroundSize = "cover";
            el.style.backgroundPosition = "center";
            el.style.color = "transparent";
            el.style.fontSize = "0";
        } else {
            ["backgroundImage", "backgroundSize", "backgroundPosition", "color", "fontSize"].forEach(k => el.style[k] = "");
        }
    },

    /** Reduce una imagen a un cuadrado de `size` px (JPEG) para guardarla liviana. */
    shrinkImage(file, size) {
        // Se lee como data URL (no blob:): la politica de seguridad solo deja cargar imagenes 'self' y data:.
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onerror = () => reject(new Error("No se pudo leer la imagen."));
            reader.onload = () => {
                const img = new Image();
                img.onload = () => {
                    const canvas = document.createElement("canvas");
                    canvas.width = canvas.height = size;
                    const side = Math.min(img.width, img.height);
                    canvas.getContext("2d").drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, size, size);
                    resolve(canvas.toDataURL("image/jpeg", 0.85));
                };
                img.onerror = () => reject(new Error("No se pudo leer la imagen."));
                img.src = reader.result;
            };
            reader.readAsDataURL(file);
        });
    },

    /** El avatar de la barra abre la ventana "Mi cuenta" con los datos personales censurados. */
    /**
     * Celular: la navegacion (Panel, Contador, Seguridad, Ajustes...), el modo Demo y Salir van en un menu
     * de tres lineas. En computador el boton no se ve y la barra queda como siempre.
     */
    mountMenu() {
        const who = document.querySelector(".topbar .who");
        if (!who || document.getElementById("menuButton")) return;
        const button = document.createElement("button");
        button.id = "menuButton";
        button.type = "button";
        button.className = "menu-button";
        button.setAttribute("aria-label", "Menú");
        button.setAttribute("aria-expanded", "false");
        button.innerHTML = Nexorix.icon("menu");
        who.appendChild(button);

        const close = () => {
            document.getElementById("menuPanel")?.remove();
            button.setAttribute("aria-expanded", "false");
        };
        button.addEventListener("click", (event) => {
            event.stopPropagation();
            if (document.getElementById("menuPanel")) { close(); return; }
            const links = [...document.querySelectorAll(".main-nav a")].map(a =>
                `<a href="${a.getAttribute("href")}"${a.getAttribute("aria-current") ? ' aria-current="page"' : ""}>${a.textContent}</a>`).join("");
            const demo = document.getElementById("demoToggle");
            const panel = document.createElement("div");
            panel.id = "menuPanel";
            panel.className = "menu-panel";
            panel.innerHTML = `<nav aria-label="Secciones">${links}</nav>`
                + (demo ? `<button type="button" data-act="demo">${demo.textContent}</button>` : "")
                + '<button type="button" data-act="logout" class="danger">Salir</button>';
            panel.addEventListener("click", (e) => {
                const act = e.target.closest("button")?.dataset.act;
                if (act === "demo") { demo.click(); close(); }
                if (act === "logout") document.getElementById("logoutButton")?.click();
            });
            document.querySelector(".topbar").appendChild(panel);
            button.setAttribute("aria-expanded", "true");
        });
        document.addEventListener("click", (e) => { if (!e.target.closest("#menuPanel")) close(); });
        document.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); });
    },

    mountProfile() {
        const avatar = document.getElementById("avatar");
        if (!avatar || avatar.dataset.profile) return;
        avatar.dataset.profile = "1";
        avatar.removeAttribute("aria-hidden");
        avatar.setAttribute("role", "button");
        avatar.setAttribute("tabindex", "0");
        avatar.setAttribute("aria-label", Nexorix.t("profile.title"));
        avatar.style.cursor = "pointer";
        const open = async () => {
            let dialog = document.getElementById("profileDialog");
            if (!dialog) {
                dialog = document.createElement("dialog");
                dialog.id = "profileDialog";
                dialog.className = "sheet";
                dialog.addEventListener("click", (e) => { if (e.target === dialog) dialog.close(); });
                document.body.appendChild(dialog);
            }
            const result = await Nexorix.api("/api/users/me/profile");
            if (!result.ok) { Nexorix.toast(Nexorix.errorOf(result, "No se pudo cargar tu cuenta."), "error"); return; }
            const p = result.data;
            const row = (label, value) => `<div class="profile-row"><span>${Nexorix.esc(Nexorix.t(label))}</span>`
                + `<strong>${Nexorix.esc(value || Nexorix.t("profile.none"))}</strong></div>`;
            dialog.innerHTML = `<div class="sheet-body"><div class="sheet-head"><h2>${Nexorix.esc(Nexorix.t("profile.title"))}</h2>`
                + `<button class="text-button" type="button" id="profileClose">${Nexorix.esc(Nexorix.t("profile.close"))}</button></div>`
                + `<div class="profile-top"><span class="avatar big" id="profilePhoto">${Nexorix.esc(Nexorix.initials(p.name))}</span>`
                + `<div><strong>${Nexorix.esc(p.name)}</strong><br><span class="muted">@${Nexorix.esc(p.username)}</span><br>`
                + `<label class="text-button" style="cursor:pointer">${Nexorix.esc(Nexorix.t("profile.changePhoto"))}`
                + `<input type="file" id="photoInput" accept="image/png,image/jpeg,image/webp" hidden></label>`
                + (p.photo ? ` · <button class="text-button" type="button" id="photoRemove">${Nexorix.esc(Nexorix.t("profile.removePhoto"))}</button>` : "")
                + `</div></div>`
                + row("profile.username", "@" + p.username) + row("profile.account", p.accountNumber)
                + `<h3 class="profile-h">${Nexorix.esc(Nexorix.t("profile.personal"))}</h3>`
                + row("profile.name", p.name) + row("profile.cedula", p.cedula) + row("profile.phone", p.phone)
                + row("profile.email", p.email) + row("profile.kyc", p.kycStatus)
                + `<p class="muted" style="font-size:.8rem;margin-top:14px">${Nexorix.esc(Nexorix.t("profile.note"))}</p></div>`;
            dialog.querySelector("#profileClose").addEventListener("click", () => dialog.close());
            Nexorix.paintPhoto(dialog.querySelector("#profilePhoto"), p.photo);
            dialog.querySelector("#photoInput").addEventListener("change", async (e) => {
                const file = e.target.files[0];
                if (!file) return;
                try {
                    const image = await Nexorix.shrinkImage(file, 256);
                    const r = await Nexorix.api("/api/users/me/photo", { method: "POST", json: { image } });
                    if (!r.ok) throw new Error(Nexorix.errorOf(r, "No se pudo guardar la foto."));
                    Nexorix.paintPhoto(avatar, image);
                    Nexorix.paintPhoto(dialog.querySelector("#profilePhoto"), image);
                    Nexorix.toast(Nexorix.t("profile.photoSaved"));
                } catch (error) { Nexorix.toast(error.message, "error"); }
            });
            const remove = dialog.querySelector("#photoRemove");
            if (remove) remove.addEventListener("click", async () => {
                await Nexorix.api("/api/users/me/photo", { method: "DELETE" });
                Nexorix.paintPhoto(avatar, null);
                dialog.close();
            });
            dialog.showModal();
        };
        avatar.addEventListener("click", open);
        Nexorix.api("/api/users/me/profile").then(r => { if (r.ok) Nexorix.paintPhoto(avatar, r.data.photo); });
        avatar.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(); } });
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
            eye: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
            eyeoff: '<path d="M3 3l18 18M10.6 6.2A9.6 9.6 0 0 1 12 6c6.4 0 10 6 10 6a17 17 0 0 1-3.2 3.9M6.6 7.6A17 17 0 0 0 2 12s3.6 6 10 6a9.5 9.5 0 0 0 4-.9"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
            menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
            card: '<rect x="2.5" y="5" width="19" height="14" rx="3"/><path d="M2.5 10h19M6 15h4"/>',
            bell: '<path d="M6 17V11a6 6 0 0 1 12 0v6l1.5 2h-15z"/><path d="M10 21a2 2 0 0 0 4 0"/>',
            lock: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
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
            // Campo trampa: vacio para una persona; un bot que llena todos los campos lo delata.
            const trap = document.getElementById("nx_website");
            const guarded = ["/api/auth/login", "/api/users", "/api/recovery/start"].includes(path);
            if (trap && guarded && options.json && typeof options.json === "object" && !Array.isArray(options.json)) {
                options.json = { ...options.json, nx_website: trap.value };
            }
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

    // ============================================================
    // NOTIFICACIONES DEL DISPOSITIVO (Web Push)
    // ============================================================

    push: {
        supported() {
            return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
        },

        keyBytes(base64url) {
            const padded = (base64url + "=".repeat((4 - base64url.length % 4) % 4)).replace(/-/g, "+").replace(/_/g, "/");
            return Uint8Array.from(atob(padded), char => char.charCodeAt(0));
        },

        async registration() {
            return navigator.serviceWorker.register("/sw.js");
        },

        /** Crea (o reutiliza) la suscripcion de este navegador y se la entrega al servidor. */
        async subscribe() {
            const registration = await this.registration();
            await navigator.serviceWorker.ready;
            const key = await Nexorix.api("/api/push/key");
            if (!key.ok || !key.data) {
                throw new Error("No fue posible preparar las notificaciones.");
            }
            let subscription = await registration.pushManager.getSubscription();
            if (!subscription) {
                subscription = await registration.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: this.keyBytes(key.data.publicKey)
                });
            }
            const json = subscription.toJSON();
            const saved = await Nexorix.api("/api/push/subscribe", { method: "POST", json });
            if (!saved.ok) {
                throw new Error(Nexorix.errorOf(saved, "No fue posible activar las notificaciones."));
            }
            return subscription;
        },

        /** Pide permiso (hay que llamarlo desde un clic) y activa los avisos. */
        async enable() {
            if (!this.supported()) {
                throw new Error("Este navegador no permite notificaciones. En iPhone, instala Nexorix en la pantalla de inicio.");
            }
            const permission = await Notification.requestPermission();
            if (permission !== "granted") {
                throw new Error("Las notificaciones están bloqueadas. Actívalas en los ajustes del navegador.");
            }
            await this.subscribe();
        },

        async disable() {
            if (!this.supported()) return;
            const registration = await navigator.serviceWorker.getRegistration("/sw.js");
            const subscription = registration && await registration.pushManager.getSubscription();
            if (subscription) {
                await Nexorix.api("/api/push/unsubscribe", { method: "POST", json: { endpoint: subscription.endpoint } });
                await subscription.unsubscribe();
            }
        },

        /** "granted" con suscripcion activa en este navegador. */
        async active() {
            if (!this.supported() || Notification.permission !== "granted") return false;
            const registration = await navigator.serviceWorker.getRegistration("/sw.js");
            return !!(registration && await registration.pushManager.getSubscription());
        },

        /** En cada pagina con sesion: mantiene la suscripcion al dia o invita a activarla. */
        async init() {
            if (!this.supported()) return;
            try {
                if (Notification.permission === "granted") {
                    await this.subscribe();
                } else if (Notification.permission === "default") {
                    this.offer();
                }
            } catch (error) {
                console.warn(error.message);
            }
        },

        offer() {
            let dismissed = false;
            try { dismissed = Date.now() < Number(localStorage.getItem("nexorix.pushLater") || 0); } catch (e) { /* sin almacenamiento */ }
            if (dismissed || document.getElementById("pushOffer")) return;

            const bar = document.createElement("div");
            bar.id = "pushOffer";
            bar.className = "push-offer";
            bar.innerHTML = `<span class="push-icon">${Nexorix.icon("bell")}</span>
                <span class="push-text"><strong>Recibe tus alertas en el celular.</strong>
                Compras bloqueadas o inusuales te llegan como notificación, aunque no tengas Nexorix abierto.</span>
                <button type="button" class="button small" data-act="yes">Activar</button>
                <button type="button" class="text-button" data-act="no">Ahora no</button>`;
            const topbar = document.querySelector(".topbar");
            topbar.insertAdjacentElement("afterend", bar);
            bar.addEventListener("click", async (event) => {
                const act = event.target.closest("button")?.dataset.act;
                if (act === "no") {
                    try { localStorage.setItem("nexorix.pushLater", String(Date.now() + 7 * 864e5)); } catch (e) { /* sin almacenamiento */ }
                    bar.remove();
                } else if (act === "yes") {
                    try {
                        await Nexorix.push.enable();
                        bar.remove();
                        Nexorix.toast("Listo: te avisaremos en este dispositivo.");
                    } catch (error) {
                        Nexorix.toast(error.message, "error");
                    }
                }
            });
        }
    },

    // ============================================================
    // CAMPANA DE AVISOS (arriba, en todas las paginas con sesion)
    // ============================================================

    bell: {
        lastUnread: null,
        lastTopId: null,

        mount() {
            const who = document.querySelector(".topbar .who");
            if (!who || document.getElementById("bellLink")) return;
            const link = document.createElement("a");
            link.id = "bellLink";
            link.className = "bell";
            link.href = "/seguridad.html#avisos";
            link.setAttribute("aria-label", "Avisos");
            link.innerHTML = Nexorix.icon("bell") + '<span class="bell-count" id="bellCount" hidden></span>';
            who.insertBefore(link, who.querySelector(".who-name") || who.firstChild);
        },

        async refresh() {
            const result = await Nexorix.api("/api/security/notifications");
            if (!result.ok || !result.data) return false;
            const unread = Number(result.data.unread || 0);
            const count = document.getElementById("bellCount");
            if (count) {
                count.hidden = unread === 0;
                count.textContent = unread > 9 ? "9+" : String(unread);
            }
            const top = (result.data.items || [])[0];
            // Aviso nuevo mientras la persona tiene la app abierta: tambien se ve en pantalla.
            if (this.lastTopId !== null && top && top.id !== this.lastTopId && !top.read) {
                Nexorix.toast((top.severity === "CRITICAL" ? "🚨 " : "") + top.title + ": " + top.body,
                    top.severity === "CRITICAL" ? "error" : "");
            }
            this.lastTopId = top ? top.id : 0;
            this.lastUnread = unread;
            document.dispatchEvent(new CustomEvent("nexorix:notifications", { detail: result.data }));
            return true;
        },

        /** Solo si hay sesion (algunas paginas con barra, como el cobro, son publicas). */
        async start() {
            if (!await this.refresh()) return;
            this.mount();
            await this.refresh();
            setInterval(() => { if (!document.hidden) this.refresh(); }, 15000);
            Nexorix.push.init();
        }
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
    Nexorix.mountProfile();

    // Dibuja el logo en cualquier elemento con la clase "brand-mark".
    document.querySelectorAll(".brand-mark").forEach(mark => {
        mark.innerHTML = Nexorix.icon("logo");
        mark.style.color = "#04101f";
    });

    // Paginas con sesion (tienen barra superior): campana de avisos y notificaciones del dispositivo.
    if (document.querySelector(".topbar")) {
        Nexorix.bell.start();
        Nexorix.mountMenu();
    }

    // Seguridad: en las paginas con sesion iniciada (las que tienen barra superior),
    // si pasan 10 minutos sin tocar nada se cierra la sesion, como en una app de banco.
    if (document.querySelector(".topbar")) {
        const IDLE_MS = 10 * 60 * 1000;
        let timer;
        const expire = async () => {
            try {
                await Nexorix.api("/api/users/logout", { method: "POST" });
            } catch (error) {
                // igual se sale de la pagina
            }
            location.replace("/login.html?expirada=1");
        };
        const rearm = () => {
            clearTimeout(timer);
            timer = setTimeout(expire, IDLE_MS);
        };
        ["pointerdown", "keydown", "touchstart", "scroll"].forEach(name =>
            document.addEventListener(name, rearm, { passive: true }));
        rearm();
    }

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
