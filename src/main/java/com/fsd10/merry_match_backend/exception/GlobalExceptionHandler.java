package com.fsd10.merry_match_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import co.omise.models.OmiseException;
import jakarta.persistence.EntityNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(PlanNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handlePlanNotFound(PlanNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(bodyWithMessage("Not Found", ex.getMessage()));
  }

  @ExceptionHandler(SubscriptionNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleSubscriptionNotFound(SubscriptionNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(bodyWithMessage("Not Found", ex.getMessage()));
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(bodyWithMessage("Not Found", ex.getMessage()));
  }

  @ExceptionHandler(SubscriptionAlreadyActiveException.class)
  public ResponseEntity<Map<String, Object>> handleSubscriptionConflict(SubscriptionAlreadyActiveException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(bodyWithMessage("Conflict", ex.getMessage()));
  }

  @ExceptionHandler(SubscriptionPaymentException.class)
  public ResponseEntity<Map<String, Object>> handleSubscriptionPayment(SubscriptionPaymentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bodyWithMessage("Bad Request", ex.getMessage()));
  }

  @ExceptionHandler(InvalidPlanChangeException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidPlanChange(InvalidPlanChangeException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bodyWithMessage("Bad Request", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bodyWithMessage("Bad Request", ex.getMessage()));
  }

  @ExceptionHandler(OmiseException.class)
  public ResponseEntity<Map<String, Object>> handleOmise(OmiseException ex) {
    int code = ex.getHttpStatusCode();
    HttpStatus status = (code >= 400 && code < 600) ? HttpStatus.valueOf(code) : HttpStatus.BAD_REQUEST;
    String msg = ex.getMessage() != null ? ex.getMessage() : "Omise error";
    return ResponseEntity.status(status).body(bodyWithMessage(status.getReasonPhrase(), msg));
  }

  @ExceptionHandler(RegisterFailedException.class)
  public ResponseEntity<Map<String, Object>> handleRegisterFailed(RegisterFailedException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bodyWithMessage("Bad Request", ex.getMessage()));
  }

  @ExceptionHandler(LoginFailedException.class)
  public ResponseEntity<Map<String, Object>> handleLoginFailed(LoginFailedException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(bodyWithMessage("Unauthorized", ex.getMessage()));
  }

  @ExceptionHandler(EmailAlreadyUsedException.class)
  public ResponseEntity<Map<String, Object>> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(bodyWithMessageAndField("Conflict", ex.getMessage(), "email"));
  }

  @ExceptionHandler(UsernameAlreadyUsedException.class)
  public ResponseEntity<Map<String, Object>> handleUsernameAlreadyUsed(UsernameAlreadyUsedException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(bodyWithMessageAndField("Conflict", ex.getMessage(), "username"));
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

  private Map<String, Object> bodyWithMessageAndField(String error, String message, String field) {
    Map<String, Object> body = bodyWithMessage(error, message);
    body.put("field", field);
    return body;
  }
}
