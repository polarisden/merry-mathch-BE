package com.fsd10.merry_match_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(RegisterFailedException.class)
  public ResponseEntity<Map<String, Object>> handleRegisterFailed(RegisterFailedException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bodyWithMessage("Bad Request", ex.getMessage()));
  }

  @ExceptionHandler(LoginFailedException.class)
  public ResponseEntity<Map<String, Object>> handleLoginFailed(LoginFailedException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(bodyWithMessage("Unauthorized", ex.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleUnreadableJson(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bodyWithMessage("Bad Request", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String msg = ex.getBindingResult().getFieldErrors().stream()
        .map(this::formatFieldError)
        .collect(Collectors.joining("; "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bodyWithMessage("Bad Request", msg));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    String error = status.getReasonPhrase();
    String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
    return ResponseEntity.status(status).body(bodyWithMessage(error, message));
  }

  private String formatFieldError(FieldError e) {
    return e.getField() + ": " + e.getDefaultMessage();
  }

  private Map<String, Object> bodyWithMessage(String error, String message) {
    Map<String, Object> body = new HashMap<>();
    body.put("error", error);
    body.put("message", message);
    return body;
  }
}
