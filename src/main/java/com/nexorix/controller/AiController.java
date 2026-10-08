package com.nexorix.controller;

import com.nexorix.agent.AgentReply;
import com.nexorix.agent.NexorixAgent;
import com.nexorix.ai.AiException;
import com.nexorix.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IA de Nexorix.
 *   GET  /api/ai/status        ¿esta activa?
 *   POST /api/agent/chat       {message} -> {reply, actions, toolsUsed}
 *   POST /api/agent/reset      borrar la conversacion
 *   POST /api/ai/ask           (anterior) {question} -> {answer}
 */
@RestController
@RequestMapping("/api")
public class AiController {

    private static final String HISTORY = "agent.history";

    private final NexorixAgent agent;
    private final UserRepository userRepository;

    public AiController(NexorixAgent agent, UserRepository userRepository) {
        this.agent = agent;
        this.userRepository = userRepository;
    }

    public record ChatRequest(String message) {
    }

    public record Question(String question) {
    }

    @GetMapping("/ai/status")
    public Map<String, Object> status() {
        return Map.of("enabled", agent.isEnabled(), "provider", "Gemini");
    }

    @PostMapping("/agent/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(run(body.message(), request));
        } catch (AiException exception) {
            return ResponseEntity.status(503).body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/agent/reset")
    public Map<String, Object> reset(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(HISTORY);
        return Map.of("ok", true);
    }

    @PostMapping("/ai/ask")
    public ResponseEntity<?> ask(@RequestBody Question body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(Map.of("answer", run(body.question(), request).reply()));
        } catch (AiException exception) {
            return ResponseEntity.status(503).body(Map.of("error", exception.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private AgentReply run(String message, HttpServletRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String name = userRepository.findByUsername(username).map(u -> u.getName().split(" ")[0]).orElse(null);

        HttpSession session = request.getSession();
        Object stored = session.getAttribute(HISTORY);
        List<String> history = stored instanceof List<?> ? new ArrayList<>((List<String>) stored) : new ArrayList<>();
        AgentReply reply = agent.chat(username, name, message, history);
        session.setAttribute(HISTORY, history);
        return reply;
    }
}
