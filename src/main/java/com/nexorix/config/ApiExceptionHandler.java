package com.nexorix.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Convierte los errores en respuestas claras para la pagina.
 *
 * - Validaciones (IllegalArgumentException): 400 con el mensaje.
 * - Falta un dato o tiene un formato equivocado (por ejemplo "abc" en un
 *   monto): 400 con un mensaje entendible, sin detalles internos.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissing(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body("Falta el dato: " + exception.getParameterName() + ".");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleMismatch(MethodArgumentTypeMismatchException exception) {
        log.debug("Dato con formato invalido: {}", exception.getName());
        return ResponseEntity.badRequest().body("El dato \"" + exception.getName() + "\" no tiene un formato válido.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body("El archivo pesa más de 5 MB.");
    }

    /** 403, 404, 409...: el mensaje va en "error" para que la pagina lo muestre. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException exception) {
        String reason = exception.getReason() == null ? "No fue posible completar la acción." : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", reason));
    }
}
