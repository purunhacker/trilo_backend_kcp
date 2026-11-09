package app.trilo.notification.api.dto;

/**
 * Partial update of notification preferences. Every field is optional; only non-null fields are
 * applied so clients can toggle a single preference at a time.
 */
public record UpdatePreferenceRequest(
    Boolean allowNightPush, Boolean allowNewPost, Boolean allowLike) {}
