package com.weekend.assistant.web;

import com.weekend.assistant.agent.CostCapExceededException;
import java.util.Map;
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

    @ExceptionHandler(CostCapExceededException.class)
    public ResponseEntity<Map<String, String>> costCap(CostCapExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
    }
}
