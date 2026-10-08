package com.nexorix.agent;

import java.util.List;

/** Respuesta del agente para la pagina. */
public record AgentReply(String reply, List<AgentAction> actions, List<String> toolsUsed) {
}
