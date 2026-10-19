package com.eia.notifications.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a notification. Mirrors the expert response so the
 * domain does not leak persistence entities (SPEC 7.6).
 */
public record NotificationDto(
        UUID id,
        UUID tenantId,
        String notificationNumber,
        String notificationType,
        String channel,
        String status,
        UUID recipientPartyId,
        String subject,
        String body,
        String templateCode,
        Integer templateVersion,
        Instant scheduledAt,
        Instant sentAt,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
