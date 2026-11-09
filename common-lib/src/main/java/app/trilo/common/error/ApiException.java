package app.trilo.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final ErrorCode errorCode;
  private final Map<String, String> fields;

  public ApiException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
    this.fields = null;
  }

  public ApiException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.fields = null;
  }

  public ApiException(ErrorCode errorCode, Map<String, String> fields) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
    this.fields = fields;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return errorCode.getHttpStatus();
  }

  public Map<String, String> getFields() {
    return fields;
  }

  public ApiError toApiError() {
    return new ApiError(errorCode.name(), getMessage(), fields);
  }
}
