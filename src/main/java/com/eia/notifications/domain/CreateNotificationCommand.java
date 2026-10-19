package com.eia.notifications.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Create command for a notification draft. Tenant identity comes from the
 * required X-EIA-Tenant header, never from the body.
 */
public record CreateNotificationCommand(
        @NotBlank @Size(max = 64) String notificationType,
        @NotBlank @Size(max = 32) String channel,
        @NotNull UUID recipientPartyId,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank String body,
        @Size(max = 64) String templateCode,
        Integer templateVersion,
        Instant scheduledAt
) {
}
