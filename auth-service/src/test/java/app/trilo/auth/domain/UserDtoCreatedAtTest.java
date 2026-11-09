package app.trilo.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.common.dto.UserDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가입 시각을 서버 기본 타임존으로 날짜만 잘라 내보내면, 자정 전후 가입자의 가입일이 하루 어긋나고 해외 사용자는 자기 현지 날짜로 되돌릴 방법이 없다. 절대
 * 시각(UTC)으로 내보내는지 확인한다.
 */
class UserDtoCreatedAtTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TimeZone originalDefault = TimeZone.getDefault();

  @AfterEach
  void restoreDefaultTimeZone() {
    TimeZone.setDefault(originalDefault);
  }

  private static User userCreatedAt(Instant createdAt) {
    return User.builder()
        .id(UUID.randomUUID())
        .email("traveller@example.com")
        .nickname("traveller")
        .passwordHash("hash")
        .createdAt(createdAt)
        .build();
  }

  @Test
  @DisplayName("가입 시각을 ISO-8601 UTC 절대 시각으로 내보낸다")
  void exposesCreatedAtAsAbsoluteInstant() {
    Instant createdAt = Instant.parse("2026-01-15T23:30:00Z");

    UserDto dto = userCreatedAt(createdAt).toDto(objectMapper);

    assertThat(dto.createdAt()).isEqualTo("2026-01-15T23:30:00Z");
    assertThat(Instant.parse(dto.createdAt())).isEqualTo(createdAt);
  }

  @Test
  @DisplayName("서버 기본 타임존이 바뀌어도 값이 달라지지 않는다")
  void isIndependentOfServerTimeZone() {
    // UTC 23:30은 서울에서 다음 날 08:30이다. 서버 타임존에 따라 날짜가 흔들리면 안 된다.
    User user = userCreatedAt(Instant.parse("2026-01-15T23:30:00Z"));

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    String onUtcHost = user.toDto(objectMapper).createdAt();

    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    String onSeoulHost = user.toDto(objectMapper).createdAt();

    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
    String onLosAngelesHost = user.toDto(objectMapper).createdAt();

    assertThat(onSeoulHost).isEqualTo(onUtcHost);
    assertThat(onLosAngelesHost).isEqualTo(onUtcHost);
  }
}
