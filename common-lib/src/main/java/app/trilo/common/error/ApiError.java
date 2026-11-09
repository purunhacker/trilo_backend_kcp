package app.trilo.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fields) {
  public ApiError(ErrorCode errorCode) {
    this(errorCode.name(), errorCode.getDefaultMessage(), null);
  }

  public ApiError(ErrorCode errorCode, String message) {
    this(errorCode.name(), message, null);
  }

  public ApiError(ErrorCode errorCode, Map<String, String> fields) {
    this(errorCode.name(), errorCode.getDefaultMessage(), fields);
  }
}
