package app.trilo.admin.dto;

import java.time.Instant;

public record UpdateTermVersionRequest(String title, String content, Instant effectiveAt) {}
