package com.eia.notifications.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Domain business rules for notifications. The public API never writes data
 * directly; every operation is delegated to the expert after validation.
 */
@Service
public class NotificationService {

    private static final Set<String> SENDABLE_STATUSES = Set.of("CREATED", "QUEUED");
    private static final int MAX_SEARCH_LIMIT = 25;
    private static final String SEARCH_TYPE = "NOTIFICATION";
    private static final String FIELD_SEPARATOR = " · ";

    private final NotificationExpertClient client;

    public NotificationService(NotificationExpertClient client) {
        this.client = client;
    }

    public List<NotificationDto> list(UUID tenantId) {
        return client.findAll(tenantId);
    }

    /**
     * Common global search. The tenant-scoped expert list is the only read path
     * available to the domain, so matching, ordering, and limiting happen here
     * and produce the shared search shape used by the shell.
     */
    public NotificationSearchResponse search(UUID tenantId, String q, int limit) {
        String query = q == null ? "" : q.trim();
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        List<NotificationDto> notifications = client.findAll(tenantId);
        if (notifications == null) {
            notifications = List.of();
        }
        List<NotificationSearchItem> items = notifications.stream()
                .filter(notification -> notification.tenantId() == null
                        || tenantId.equals(notification.tenantId()))
                .filter(notification -> matches(notification, query))
                .sorted(Comparator.comparing(NotificationDto::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(effectiveLimit)
                .map(NotificationService::toSearchItem)
                .toList();
        return new NotificationSearchResponse(query, effectiveLimit, items);
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

    private static boolean matches(NotificationDto notification, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return contains(notification.notificationNumber(), needle)
                || contains(notification.subject(), needle)
                || contains(notification.recipientPartyId() == null
                        ? null : notification.recipientPartyId().toString(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static NotificationSearchItem toSearchItem(NotificationDto notification) {
        String primary = isBlank(notification.subject())
                ? notification.notificationType() : notification.subject();
        return new NotificationSearchItem(
                notification.id(),
                SEARCH_TYPE,
                notification.notificationNumber(),
                secondary(primary, notification.channel()),
                notification.status(),
                "/notifications?record=" + notification.id());
    }

    private static String secondary(String primary, String channel) {
        if (isBlank(primary)) {
            return channel;
        }
        if (isBlank(channel)) {
            return primary;
        }
        return primary + FIELD_SEPARATOR + channel;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
