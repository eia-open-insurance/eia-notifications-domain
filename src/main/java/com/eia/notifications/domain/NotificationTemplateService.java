package com.eia.notifications.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Domain facade for the notification template registry. The domain owns request
 * shape and lifecycle invariants; the expert owns persistence, uniqueness, and
 * the transactional state transition (NTF-14). Expert 400/404/409 problems are
 * propagated unchanged by {@link NotificationExpertClient}.
 */
@Service
public class NotificationTemplateService {

    private final NotificationExpertClient client;

    public NotificationTemplateService(NotificationExpertClient client) {
        this.client = client;
    }

    public List<NotificationTemplateDto> list(UUID tenantId, String statusFilter, String codeFilter) {
        NotificationTemplateStatus status = NotificationTemplateStatus.from(statusFilter);
        String code = blankToNull(codeFilter);
        return client.findAllTemplates(tenantId, status, code);
    }

    public NotificationTemplateDto create(UUID tenantId, CreateNotificationTemplateCommand command) {
        if (command.templateVersion() == null || command.templateVersion() < 1) {
            throw new ApiException(ErrorCode.INVALID_TEMPLATE_VERSION,
                    "templateVersion must be a positive integer.");
        }
        String channel = blankToNull(command.channel());
        if (channel != null) {
            NotificationChannel.from(channel);
        }
        return client.createTemplate(tenantId, command);
    }

    public NotificationTemplateDto approve(UUID tenantId, UUID templateId, String approvedBy) {
        if (approvedBy == null) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT, "approvedBy is required.");
        }
        return client.approveTemplate(tenantId, templateId, approvedBy);
    }

    public NotificationTemplateDto retire(UUID tenantId, UUID templateId) {
        return client.retireTemplate(tenantId, templateId);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
