package com.eia.notifications.domain;

import jakarta.validation.constraints.NotNull;

/**
 * Approval command for a notification template. The approver is recorded as
 * evidence on the template and is required before it can become ACTIVE
 * (NTF-14). The approver is a stable directory identifier (for example
 * {@code a-adjuster}), not necessarily a UUID.
 */
public record ApproveNotificationTemplateCommand(
        @NotNull String approvedBy
) {
}
