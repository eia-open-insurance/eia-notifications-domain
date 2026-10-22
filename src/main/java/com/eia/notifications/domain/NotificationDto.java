package com.eia.notifications.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a notification. Mirrors the expert response so the
 * domain does not leak persistence entities (SPEC 7.6). {@code deliveredAt} and
 * {@code providerReference} carry the provider-confirmed delivery evidence of
 * the send recorded on the notification.
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
        Instant deliveredAt,
        String providerReference,
        int version,
        Instant createdAt,
        Instant updatedAt,
        String sourceType,
        UUID sourceId,
        String sourceReference
) {
}
