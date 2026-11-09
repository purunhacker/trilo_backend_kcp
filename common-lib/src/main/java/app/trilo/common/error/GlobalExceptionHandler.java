package app.trilo.common.error;

import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApiException(ApiException ex) {
    log.warn("API error: {} - {}", ex.getErrorCode(), ex.getMessage());
    return ResponseEntity.status(ex.getHttpStatus()).body(ex.toApiError());
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ApiError> handleEntityNotFoundException(EntityNotFoundException ex) {
    log.warn("Entity not found: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
        .body(new ApiError(ErrorCode.NOT_FOUND));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fields.put(error.getField(), error.getDefaultMessage());
    }
    ApiError apiError = new ApiError(ErrorCode.VALIDATION_FAILED, fields);
    return ResponseEntity.badRequest().body(apiError);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleException(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
        .body(new ApiError(ErrorCode.INTERNAL_ERROR));
  }
}
