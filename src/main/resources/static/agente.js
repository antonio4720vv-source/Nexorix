// ============================================================
// NEXORIX - "Nexo", el agente financiero (chat flotante)
// Se incluye en las paginas con sesion: <script src="/agente.js"></script>
// ============================================================

const NexoAgent = (() => {

    const STORE = "nexorix.agent.messages";
    const SUGGESTIONS = [
        "¿Cuánto dinero me entró de verdad este mes?",
        "¿En qué se me va más la plata?",
        "¿Tengo transferencias por confirmar?",
        "¿Qué gastos se repiten cada mes?",
        "Dame el reporte PDF de este año para mi contador"
    ];

    let root, list, input, sendButton, enabled = false, busy = false, messages = [];

    const esc = (v) => Nexorix.esc(v);

    /** Texto del modelo -> HTML seguro (solo **negrita**, listas con guion y saltos de linea). */
    function format(text) {
        const lines = esc(text).split(/\n/);
        let html = "", inList = false;
        for (const raw of lines) {
            const line = raw.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
            const item = line.match(/^\s*[-•*]\s+(.*)$/);
            if (item) {
                if (!inList) { html += "<ul>"; inList = true; }
                html += `<li>${item[1]}</li>`;
            } else {
                if (inList) { html += "</ul>"; inList = false; }
                if (line.trim()) html += `<p>${line}</p>`;
            }
        }
        return html + (inList ? "</ul>" : "");
    }

    const TOOL_NAMES = {
        resumen_financiero: "resumen financiero", cuentas: "tus cuentas", buscar_movimientos: "movimientos",
        gastos_por_categoria: "categorías", resumen_mensual: "meses", gastos_recurrentes: "gastos repetidos",
        transferencias_sugeridas: "Trace", historial_transferencias: "historial de Trace",
        proponer_confirmar_transferencia: "Trace", preparar_reporte: "reportes"
    };

    function save() {
        try { sessionStorage.setItem(STORE, JSON.stringify(messages.slice(-30))); } catch (e) { /* sin almacenamiento */ }
    }

    function render() {
        if (!messages.length) {
            list.innerHTML = `
                <div class="nexo-hello">
                    <div class="nexo-avatar big">${Nexorix.icon("ai")}</div>
                    <p><strong>Hola, soy Nexo.</strong> Analizo tus cuentas y movimientos para responderte con cifras reales.
                    No muevo dinero: si hay algo que confirmar o descargar, te dejo un botón.</p>
                    <div class="nexo-suggestions">${SUGGESTIONS.map(q => `<button type="button" data-q="${esc(q)}">${esc(q)}</button>`).join("")}</div>
                    ${enabled ? "" : `<p class="nexo-off">El agente no está activo: falta configurar <code>GEMINI_API_KEY</code> en el servidor.</p>`}
                </div>`;
            list.querySelectorAll("[data-q]").forEach(b => b.addEventListener("click", () => send(b.dataset.q)));
            return;
        }
        list.innerHTML = messages.map((m, i) => {
            if (m.role === "user") return `<div class="nexo-msg user"><p>${esc(m.text)}</p></div>`;
            const tools = (m.tools || []).map(t => TOOL_NAMES[t] || t).filter((v, j, a) => a.indexOf(v) === j);
            return `<div class="nexo-msg bot ${m.error ? "error" : ""}">
                <span class="nexo-avatar">${Nexorix.icon("ai")}</span>
                <div class="nexo-bubble">
                    ${format(m.text)}
                    ${(m.actions || []).map((a, k) => actionHTML(a, i, k)).join("")}
                    ${tools.length ? `<div class="nexo-tools">Consultó: ${tools.map(esc).join(", ")}</div>` : ""}
                </div></div>`;
        }).join("");
        list.querySelectorAll("[data-action]").forEach(b => b.addEventListener("click", () => runAction(b)));
        list.scrollTop = list.scrollHeight;
    }

    function actionHTML(action, i, k) {
        if (action.type === "download" || action.type === "link") {
            const isDownload = action.type === "download";
            // Solo rutas internas de Nexorix.
            const url = String(action.url || "").startsWith("/") ? action.url : "#";
            return `<a class="button small nexo-action" href="${esc(url)}" ${isDownload ? "download" : ""}>
                ${Nexorix.icon(isDownload ? "download" : "arrow")} ${esc(action.label)}</a>`;
        }
        if (action.type === "confirm_trace") {
            const done = action.done ? "disabled" : "";
            return `<button class="button small nexo-action" type="button" data-action="${i}:${k}" ${done}>
                ${Nexorix.icon("check")} ${action.done ? "Confirmada" : esc(action.label)}</button>`;
        }
        return "";
    }

    async function runAction(button) {
        const [i, k] = button.dataset.action.split(":").map(Number);
        const action = messages[i].actions[k];
        button.disabled = true;
        const result = await Nexorix.api("/api/trace/groups", {
            method: "POST", json: { originIds: action.originIds, destinationIds: action.destinationIds }
        });
        if (result.ok) {
            action.done = true;
            save();
            render();
            Nexorix.toast("Transferencia confirmada.");
            document.dispatchEvent(new CustomEvent("nexorix:changed"));
        } else {
            Nexorix.toast(Nexorix.errorOf(result, "No fue posible confirmarla."), "error");
            button.disabled = false;
        }
    }

    async function send(text) {
        const message = String(text || "").trim();
        if (!message || busy) return;
        if (!enabled) {
            Nexorix.toast("El agente no está activo en este servidor.", "error");
            return;
        }
        busy = true;
        sendButton.disabled = true;
        input.value = "";
        messages.push({ role: "user", text: message });
        render();
        const thinking = document.createElement("div");
        thinking.className = "nexo-msg bot";
        thinking.innerHTML = `<span class="nexo-avatar">${Nexorix.icon("ai")}</span>
            <div class="nexo-bubble nexo-thinking"><span></span><span></span><span></span> Analizando tus movimientos…</div>`;
        list.appendChild(thinking);
        list.scrollTop = list.scrollHeight;

        try {
            const result = await Nexorix.api("/api/agent/chat", { method: "POST", json: { message } });
            if (result.status === 401) { window.location.href = "/login.html?expirada=1"; return; }
            if (result.ok && result.data) {
                messages.push({ role: "bot", text: result.data.reply, actions: result.data.actions || [], tools: result.data.toolsUsed || [] });
            } else {
                messages.push({ role: "bot", error: true, text: Nexorix.errorOf(result, "No pude responder en este momento.") });
            }
        } catch (e) {
            messages.push({ role: "bot", error: true, text: "No hay conexión con Nexorix." });
        } finally {
            busy = false;
            sendButton.disabled = false;
            save();
            render();
            input.focus();
        }
    }

    function open(question) {
        root.classList.add("open");
        document.getElementById("nexoLauncher").setAttribute("aria-expanded", "true");
        input.focus();
        if (question) send(question);
    }

    function close() {
        root.classList.remove("open");
        document.getElementById("nexoLauncher").setAttribute("aria-expanded", "false");
    }

    async function init() {
        const launcher = document.createElement("button");
        launcher.id = "nexoLauncher";
        launcher.className = "nexo-launcher";
        launcher.type = "button";
        launcher.setAttribute("aria-expanded", "false");
        launcher.setAttribute("aria-controls", "nexoPanel");
        launcher.innerHTML = `${Nexorix.icon("ai")}<span>Pregúntale a Nexo</span>`;
        document.body.appendChild(launcher);

        root = document.createElement("section");
        root.id = "nexoPanel";
        root.className = "nexo";
        root.setAttribute("aria-label", "Asistente financiero Nexo");
        root.innerHTML = `
            <header class="nexo-head">
                <span class="nexo-avatar">${Nexorix.icon("ai")}</span>
                <div><strong>Nexo</strong><small>Asistente financiero con IA</small></div>
                <button type="button" class="nexo-icon-btn" id="nexoReset" title="Nueva conversación" aria-label="Nueva conversación">${Nexorix.icon("undo")}</button>
                <button type="button" class="nexo-icon-btn" id="nexoClose" aria-label="Cerrar">${Nexorix.icon("close")}</button>
            </header>
            <div class="nexo-list" id="nexoList" aria-live="polite"></div>
            <form class="nexo-form" id="nexoForm">
                <input id="nexoInput" maxlength="1000" autocomplete="off" placeholder="Escribe tu pregunta…" aria-label="Tu pregunta">
                <button class="button" type="submit" id="nexoSend" aria-label="Enviar">${Nexorix.icon("send")}</button>
            </form>
            <p class="nexo-legal">Nexo usa Gemini (Google) y puede equivocarse. Revisa las cifras importantes.</p>`;
        document.body.appendChild(root);

        list = root.querySelector("#nexoList");
        input = root.querySelector("#nexoInput");
        sendButton = root.querySelector("#nexoSend");

        launcher.addEventListener("click", () => root.classList.contains("open") ? close() : open());
        root.querySelector("#nexoClose").addEventListener("click", close);
        root.querySelector("#nexoForm").addEventListener("submit", (e) => { e.preventDefault(); send(input.value); });
        root.querySelector("#nexoReset").addEventListener("click", async () => {
            messages = [];
            save();
            render();
            await Nexorix.api("/api/agent/reset", { method: "POST" });
        });
        document.addEventListener("keydown", (e) => { if (e.key === "Escape" && root.classList.contains("open")) close(); });

        try { messages = JSON.parse(sessionStorage.getItem(STORE) || "[]"); } catch (e) { messages = []; }

        const status = await Nexorix.api("/api/ai/status");
        enabled = !!(status.ok && status.data && status.data.enabled);
        render();
    }

    document.addEventListener("DOMContentLoaded", init);
    return { open, send };
})();
