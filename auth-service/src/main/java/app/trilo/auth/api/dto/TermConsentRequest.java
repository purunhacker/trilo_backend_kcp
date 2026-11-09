package app.trilo.auth.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * 회원가입 시 약관 동의 요청
 *
 * @param consents 각 약관 버전에 대한 동의 여부 목록
 */
public record TermConsentRequest(@NotEmpty List<ConsentItem> consents) {

  /**
   * 개별 약관 동의 항목
   *
   * @param termVersionId 약관 버전 ID (null이면 termCode로 현재 활성 버전 사용)
   * @param termCode 약관 코드 (termVersionId가 null일 때 사용)
   * @param consented 동의 여부
   */
  public record ConsentItem(UUID termVersionId, String termCode, Boolean consented) {}
}
