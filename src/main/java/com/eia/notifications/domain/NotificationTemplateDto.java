package com.eia.notifications.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a notification template. Mirrors the expert's
 * template-management projection so persistence entities never leak through
 * the API (SPEC 7.6).
 */
public record NotificationTemplateDto(
        UUID id,
        String code,
        int version,
        String channel,
        String locale,
        String bodyTemplate,
        String status,
        UUID approvedBy,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
