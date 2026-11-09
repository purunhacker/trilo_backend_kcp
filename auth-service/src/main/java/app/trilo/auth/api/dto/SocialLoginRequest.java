package app.trilo.auth.api.dto;

public record SocialLoginRequest(
    String idToken,
    String accessToken,
    String code,
    String codeVerifier,
    String redirectUri, // Required for Google authorization code exchange
    String name // Apple Sign In: name is provided separately on first login
    ) {}
