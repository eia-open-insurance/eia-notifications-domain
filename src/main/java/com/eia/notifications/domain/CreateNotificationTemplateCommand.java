package com.eia.notifications.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create command for a notification template. Tenant identity comes from the
 * required X-EIA-Tenant header, never from the body. A new template always
 * starts in DRAFT; activation is a separate approval command (NTF-14).
 */
public record CreateNotificationTemplateCommand(
        @NotBlank @Size(max = 64) String templateCode,
        @NotNull @Min(1) Integer templateVersion,
        @Size(max = 32) String channel,
        @NotBlank @Size(max = 16) String locale,
        @JsonAlias("body") @NotBlank String bodyTemplate
) {
}
