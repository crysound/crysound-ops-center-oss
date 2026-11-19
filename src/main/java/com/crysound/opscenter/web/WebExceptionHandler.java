package com.crysound.opscenter.web;

import com.crysound.opscenter.execution.ExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class WebExceptionHandler {
    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<?> execution(ExecutionException failure) {
        return ResponseEntity.status(failure.status()).body(Map.of(
                "code", failure.code(), "message", failure.getMessage(), "fields", failure.fieldErrors()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT", "message", "Check the request format"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> unavailable() {
        return ResponseEntity.internalServerError().body(Map.of(
                "code", "UNAVAILABLE", "message", "The operation could not be completed"));
    }
}
