package com.eia.notifications.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of one delivery attempt for a notification. The expert owns
 * the attempt rows, their ordering, and the provider evidence; the domain
 * forwards the tenant-scoped projection unchanged. {@code providerReference} is
 * the provider-issued reference/idempotency key and the error fields are
 * populated only for a failed attempt (SPEC 11.8.4, NTF-11).
 */
public record NotificationDeliveryAttemptDto(
        UUID id,
        UUID tenantId,
        UUID notificationId,
        int attemptNumber,
        String channel,
        String provider,
        String providerReference,
        String status,
        String errorCode,
        String errorMessage,
        Instant attemptedAt
) {

    /**
     * Compatibility projection for callers that already hold the tenant and do
     * not need it echoed on the attempt row.
     */
    public NotificationDeliveryAttemptDto(UUID id, UUID notificationId, int attemptNumber, String channel,
                                          String provider, String providerReference, String status,
                                          String errorCode, String errorMessage, Instant attemptedAt) {
        this(id, null, notificationId, attemptNumber, channel, provider, providerReference, status,
                errorCode, errorMessage, attemptedAt);
    }
}
