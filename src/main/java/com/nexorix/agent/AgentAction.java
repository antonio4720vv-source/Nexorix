package com.nexorix.agent;

import java.util.List;

/**
 * Algo que el agente propone y la PERSONA debe aprobar con un boton.
 * El agente nunca cambia datos por su cuenta.
 *
 *   confirm_trace: confirmar una transferencia entre cuentas propias.
 *   download:      descargar un reporte.
 *   link:          ir a una pagina de Nexorix.
 */
public record AgentAction(
        String type,
        String label,
        String url,
        String key,
        List<Long> originIds,
        List<Long> destinationIds
) {

    public static AgentAction confirmTrace(String label, String key, List<Long> originIds, List<Long> destinationIds) {
        return new AgentAction("confirm_trace", label, null, key, originIds, destinationIds);
    }

    public static AgentAction download(String label, String url) {
        return new AgentAction("download", label, url, null, null, null);
    }

    public static AgentAction link(String label, String url) {
        return new AgentAction("link", label, url, null, null, null);
    }
}
