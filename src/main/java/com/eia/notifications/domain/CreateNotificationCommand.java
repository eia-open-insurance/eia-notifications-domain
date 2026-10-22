package com.eia.notifications.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Create command for a notification draft. Tenant identity comes from the
 * required X-EIA-Tenant header, never from the body.
 *
 * <p>The optional flat source link identifies the business object the
 * notification is about. It is forwarded unchanged; the expert owns the
 * linkage rules and rejects an invalid link with its own 400 problem.</p>
 */
public record CreateNotificationCommand(
        @NotBlank @Size(max = 64) String notificationType,
        @NotBlank @Size(max = 32) String channel,
        @NotNull UUID recipientPartyId,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank String body,
        @Size(max = 64) String templateCode,
        Integer templateVersion,
        Instant scheduledAt,
        String sourceType,
        UUID sourceId,
        String sourceReference
) {
}
