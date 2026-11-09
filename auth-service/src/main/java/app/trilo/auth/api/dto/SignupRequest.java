package app.trilo.auth.api.dto;

import app.trilo.common.validation.NicknameRules;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 회원가입 요청
 *
 * @param email 이메일
 * @param password 비밀번호 (최소 8자)
 * @param nickname 닉네임 (2-20자)
 * @param avatar 프로필 이미지 URL (선택)
 * @param agreedTerms 필수 약관 동의 여부 (하위 호환성 유지)
 * @param agreedMarketing 마케팅 동의 여부 (하위 호환성 유지)
 * @param termConsents 개별 약관 동의 목록 (새로운 방식)
 */
public record SignupRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotBlank
        @Size(min = NicknameRules.MIN_LENGTH, max = NicknameRules.MAX_LENGTH)
        @Pattern(regexp = NicknameRules.PATTERN, message = NicknameRules.MESSAGE)
        String nickname,
    String avatar,
    @NotNull Boolean agreedTerms,
    Boolean agreedMarketing,
    List<TermConsentItem> termConsents) {

  /**
   * 개별 약관 동의 항목
   *
   * @param termCode 약관 코드 (tos, privacy, location, location_personal, age, marketing_email,
   *     marketing_push, night_ads)
   * @param consented 동의 여부
   */
  public record TermConsentItem(String termCode, Boolean consented) {}
}
