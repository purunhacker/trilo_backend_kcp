package app.trilo.auth.domain;

import app.trilo.common.dto.UserDto;
import app.trilo.common.dto.UserPrefs;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(nullable = false)
  private String nickname;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Column @Builder.Default private String bio = "";

  /** 비공개 계정 여부. true이면 내가 팔로우한 사용자에게만 피드가 노출된다. */
  @Column(name = "is_private", nullable = false)
  @Builder.Default
  private boolean isPrivate = false;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private String prefs = "{}";

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** 이메일 인증 완료 여부 (이메일/비밀번호 회원 대상). */
  @Column(name = "email_verified", nullable = false)
  @Builder.Default
  private boolean emailVerified = false;

  @Column(
      name = "search_vector",
      columnDefinition = "tsvector",
      insertable = false,
      updatable = false)
  private String searchVector;

  @OneToMany(
      mappedBy = "user",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private List<SocialLink> socialLinks = new ArrayList<>();

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = Instant.now();
  }

  public UserDto toDto(ObjectMapper objectMapper) {
    List<String> linkedSocial = socialLinks.stream().map(SocialLink::getProvider).toList();

    UserPrefs userPrefs;
    try {
      userPrefs = objectMapper.readValue(prefs, UserPrefs.class);
    } catch (JsonProcessingException e) {
      userPrefs = UserPrefs.defaults();
    }

    return new UserDto(
        id.toString(),
        email,
        nickname,
        avatarUrl,
        bio,
        // 가입 시각은 절대 시각(ISO-8601 UTC)으로 내려보내고, 사용자 현지 날짜로의 변환은 클라이언트가
        // 맡는다. 서버 기본 타임존으로 날짜만 잘라 보내면 자정 전후 가입자의 가입일이 하루 어긋난다.
        createdAt.toString(),
        linkedSocial.isEmpty() ? List.of() : linkedSocial,
        userPrefs,
        isPrivate,
        // 이메일 회원(비밀번호 보유)만 인증 여부 노출, 소셜 전용 계정은 null → 응답에서 생략
        hasPassword() ? emailVerified : null);
  }

  public boolean hasPassword() {
    return passwordHash != null && !passwordHash.isEmpty();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
