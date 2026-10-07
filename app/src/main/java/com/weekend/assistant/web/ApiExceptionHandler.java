package com.weekend.assistant.web;

import com.weekend.assistant.agent.CostCapExceededException;
import java.util.Map;
import com.weekend.assistant.agent.ChatGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Uniform JSON errors. Never echoes request content back (it may hold personal data). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "malformed JSON"));
    }

    /** Our validators throw these with field-level messages that never contain request content. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badInput(IllegalArgumentException e) {
        String msg = e.getMessage() == null ? "invalid request" : e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", msg.length() > 200 ? "invalid request" : msg));
    }

    @ExceptionHandler(ChatGuard.BusyException.class)
    public ResponseEntity<Map<String, String>> busy(ChatGuard.BusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(e.retryAfter().toSeconds()))
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CostCapExceededException.class)
    public ResponseEntity<Map<String, String>> costCap(CostCapExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
    }
}
