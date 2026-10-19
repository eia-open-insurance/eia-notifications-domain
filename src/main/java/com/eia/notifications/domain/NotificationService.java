package com.eia.notifications.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Domain business rules for notifications. The public API never writes data
 * directly; every operation is delegated to the expert after validation.
 */
@Service
public class NotificationService {

    private static final Set<String> SENDABLE_STATUSES = Set.of("CREATED", "QUEUED");

    private final NotificationExpertClient client;

    public NotificationService(NotificationExpertClient client) {
        this.client = client;
    }

    public List<NotificationDto> list(UUID tenantId) {
        return client.findAll(tenantId);
    }

    public NotificationDto get(UUID tenantId, UUID id) {
        return client.findById(tenantId, id);
    }

    public NotificationDto create(UUID tenantId, CreateNotificationCommand command) {
        NotificationType.from(command.notificationType());
        NotificationChannel.from(command.channel());
        validateTemplateVersion(command);
        return client.create(tenantId, command);
    }

    /**
     * Issues a notification. Mirrors the SPEC 11.8.7 invariants at the domain
     * boundary; the expert re-checks them transactionally.
     */
    public NotificationDto send(UUID tenantId, UUID id) {
        NotificationDto current = client.findById(tenantId, id);

        if (!SENDABLE_STATUSES.contains(current.status())) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Notification '" + id + "' is in status '" + current.status()
                            + "'; only CREATED or QUEUED notifications can be sent.");
        }
        if (current.templateCode() == null || current.templateCode().isBlank()) {
            throw new ApiException(ErrorCode.TEMPLATE_REQUIRED,
                    "Notification '" + id + "' has no template; an approved template is required before sending (NTF-01, NTF-14).");
        }
        Instant now = Instant.now();
        if (current.scheduledAt() != null && now.isBefore(current.scheduledAt())) {
            throw new ApiException(ErrorCode.SEND_BEFORE_SCHEDULED,
                    "Notification '" + id + "' is scheduled for " + current.scheduledAt()
                            + " and cannot be sent at " + now + "; sent_at must not precede scheduled_at.");
        }
        return client.send(tenantId, id);
    }

    private void validateTemplateVersion(CreateNotificationCommand command) {
        String templateCode = command.templateCode() == null ? null : command.templateCode().trim();
        if (templateCode == null || templateCode.isEmpty()) {
            if (command.templateVersion() != null) {
                throw new ApiException(ErrorCode.TEMPLATE_REQUIRED,
                        "templateVersion was supplied without templateCode.");
            }
            return;
        }
        if (command.templateVersion() != null && command.templateVersion() < 1) {
            throw new ApiException(ErrorCode.INVALID_TEMPLATE_VERSION,
                    "templateVersion must be a positive integer.");
        }
    }
}
