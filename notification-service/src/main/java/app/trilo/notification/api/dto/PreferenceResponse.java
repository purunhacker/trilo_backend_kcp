package app.trilo.notification.api.dto;

/** Per-user notification delivery preferences. */
public record PreferenceResponse(boolean allowNightPush, boolean allowNewPost, boolean allowLike) {}
