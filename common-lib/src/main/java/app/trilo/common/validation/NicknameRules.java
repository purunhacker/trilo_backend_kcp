package app.trilo.common.validation;

/**
 * 닉네임 공통 규칙. auth-service(회원가입/프로필 수정/소셜 로그인)와 admin-service(회원 정보 수정)에서 동일하게 사용한다.
 *
 * <p>정책: 영문자/숫자/밑줄(_)/마침표(.)만 허용, 2~20자, 중복 불가(대소문자 무시).
 */
public final class NicknameRules {

  private NicknameRules() {}

  /** 허용 문자: 영문자, 숫자, 밑줄(_), 마침표(.) */
  public static final String PATTERN = "^[a-zA-Z0-9_.]+$";

  public static final int MIN_LENGTH = 2;
  public static final int MAX_LENGTH = 20;

  public static final String MESSAGE = "닉네임은 영문자, 숫자, 밑줄(_), 마침표(.)만 사용해 2~20자로 입력해주세요.";

  /** 길이(2~20)와 허용 문자 패턴을 모두 만족하는지 검사한다. */
  public static boolean isValid(String nickname) {
    if (nickname == null) {
      return false;
    }
    int len = nickname.length();
    return len >= MIN_LENGTH && len <= MAX_LENGTH && nickname.matches(PATTERN);
  }
}
