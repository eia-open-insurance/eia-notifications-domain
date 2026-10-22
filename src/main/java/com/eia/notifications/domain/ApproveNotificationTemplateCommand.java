package com.eia.notifications.domain;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Approval command for a notification template. The approver is recorded as
 * evidence on the template and is required before it can become ACTIVE
 * (NTF-14).
 */
public record ApproveNotificationTemplateCommand(
        @NotNull UUID approvedBy
) {
}
