// ============================================================
// NEXORIX - tarjetas de Trace (panel y pagina de Trace)
// ============================================================

const NexorixTrace = {

    /** Texto corto para la clasificacion. */
    classLabel(classification) {
        return {
            "TRANSFERENCIA PROPIA MUY PROBABLE": "Muy probable",
            "TRANSFERENCIA PROPIA PROBABLE": "Probable",
            "REVISAR": "Revisar"
        }[classification] || Nexorix.sentence(classification);
    },

    classKind(classification) {
        return {
            "TRANSFERENCIA PROPIA MUY PROBABLE": "ok",
            "TRANSFERENCIA PROPIA PROBABLE": "warn",
            "REVISAR": "bad"
        }[classification] || "warn";
    },

    banks(list) {
        return list.map(m => Nexorix.esc(m.bank)).join(" + ");
    },

    /** Tarjeta de una sugerencia o de una transferencia del historial. */
    card(item, options = {}) {
        const esc = Nexorix.esc;
        const money = (v) => `<span class="amt">${Nexorix.money(v)}</span>`;
        const kindIcon = item.kind === "ONE_TO_MANY" ? "split" : item.kind === "MANY_TO_ONE" ? "merge" : "moved";
        const side = (list, label) => `
            <div class="trace-side">
                <span class="trace-side-label">${label}</span>
                ${list.map(m => `
                    <div class="trace-move">
                        <span class="chip small" style="background:${Nexorix.bankColor(m.bank)}">${esc(String(m.bank || "?").charAt(0).toUpperCase())}</span>
                        <div><div class="title">${esc(m.bank)}</div>
                            <div class="meta">${esc(m.description || "")} · ${Nexorix.date(m.date)}</div></div>
                        <span class="amount">${money(m.amount)}</span>
                    </div>`).join("")}
            </div>`;

        const status = options.history
            ? `<span class="pill ${item.active ? "done" : "bad"}">${item.active ? "Confirmada" : "Deshecha"}</span>`
            : `<span class="pill ${NexorixTrace.classKind(item.classification)}">${NexorixTrace.classLabel(item.classification)} · ${item.score}/100</span>`;

        const fee = Number(item.fee) > 0
            ? `<div class="trace-fee">Comisión de transferencia: <strong>${money(item.fee)}</strong> (cuenta como gasto real)</div>` : "";

        const actions = options.history
            ? (item.active ? `<button class="link-button" type="button" data-undo="${esc(item.groupId)}">Deshacer</button>` : "")
            : `<button class="button small" type="button" data-confirm="${esc(item.key)}">Confirmar</button>`;

        return `
            <article class="trace-card${options.history && !item.active ? " undone" : ""}">
                <header class="trace-head">
                    <span class="dot moved">${Nexorix.icon(kindIcon)}</span>
                    <div class="trace-title">
                        <strong>${NexorixTrace.banks(item.origins)} → ${NexorixTrace.banks(item.destinations)}</strong>
                        <span class="kind-badge">${esc(item.kindLabel)}</span>
                    </div>
                    <span class="amount moved">${money(item.amount)}</span>
                </header>
                <div class="trace-flow">${side(item.origins, "Salió de")}${side(item.destinations, "Llegó a")}</div>
                ${fee}
                <footer class="trace-foot">${status}${actions}</footer>
            </article>`;
    },

    /** Confirma una sugerencia (todos los ids van al servidor, que vuelve a validar). */
    async confirm(suggestion) {
        const result = await Nexorix.api("/api/trace/groups", {
            method: "POST",
            json: { originIds: suggestion.origins.map(m => m.id), destinationIds: suggestion.destinations.map(m => m.id) }
        });
        if (result.status === 401) window.location.href = "/login.html?expirada=1";
        return result;
    },

    async undo(groupId) {
        const result = await Nexorix.api(`/api/trace/groups/${encodeURIComponent(groupId)}/undo`, { method: "POST" });
        if (result.status === 401) window.location.href = "/login.html?expirada=1";
        return result;
    },

    /** Conecta los botones Confirmar / Deshacer de un contenedor. */
    bind(container, suggestions, onChange) {
        container.querySelectorAll("[data-confirm]").forEach(button => button.addEventListener("click", async () => {
            const suggestion = suggestions.find(s => s.key === button.dataset.confirm);
            if (!suggestion) return;
            button.disabled = true;
            const result = await NexorixTrace.confirm(suggestion);
            if (result.ok) {
                Nexorix.toast("Transferencia confirmada. Tus números se actualizaron.");
                onChange();
            } else {
                Nexorix.toast(Nexorix.errorOf(result, "No fue posible confirmar."), "error");
                button.disabled = false;
            }
        }));
        container.querySelectorAll("[data-undo]").forEach(button => button.addEventListener("click", async () => {
            if (!confirm("¿Deshacer esta transferencia? Volverá a contar como ingreso y gasto real.")) return;
            button.disabled = true;
            const result = await NexorixTrace.undo(button.dataset.undo);
            if (result.ok) {
                Nexorix.toast("Listo, la deshicimos. Quedó en el historial.");
                onChange();
            } else {
                Nexorix.toast(Nexorix.errorOf(result, "No fue posible deshacerla."), "error");
                button.disabled = false;
            }
        }));
    }
};
