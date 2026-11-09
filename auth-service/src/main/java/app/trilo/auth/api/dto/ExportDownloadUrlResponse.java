package app.trilo.auth.api.dto;

/** A short-lived, token-authenticated URL for downloading an export zip natively. */
public record ExportDownloadUrlResponse(String url, String expiresAt) {}
